package app.wristline.adb;

import static app.wristline.adb.AdbMessage.A_CLSE;
import static app.wristline.adb.AdbMessage.A_CNXN;
import static app.wristline.adb.AdbMessage.A_OKAY;
import static app.wristline.adb.AdbMessage.A_OPEN;
import static app.wristline.adb.AdbMessage.A_STLS;
import static app.wristline.adb.AdbMessage.A_WRTE;
import static app.wristline.adb.AdbMessage.MAX_PAYLOAD;
import static app.wristline.adb.AdbMessage.STLS_VERSION;
import static app.wristline.adb.AdbMessage.VERSION;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/**
 * A connection to adbd (AOSP adb/protocol.txt): CNXN, STLS both ways, TLS 1.3 presenting
 * {@link AdbKey}'s certificate, then adbd's CNXN. A paired key is what lets the TLS handshake
 * through; there is no AUTH step on this path. Then one shell stream at a time.
 *
 * <p>adbd authenticates Wristline; Wristline does not authenticate adbd. The server certificate
 * is accepted unchecked ({@link TrustAny}), so an app on the same phone could pose as adbd on the
 * phone's own address and fake command output. It gains no secret or privilege: adbd trusts
 * Wristline because of the pairing code, and Wristline sends only fixed commands.
 */
final class AdbTransport implements Closeable {
    static final String HOST_BANNER = "host::features=shell_v2,cmd";
    /** Total output kept per command; Wristline's commands print a few lines. */
    static final int MAX_OUTPUT = 4 * 1024 * 1024;

    private static final int SHELL_STDOUT = 1;
    private static final int SHELL_STDERR = 2;
    private static final int SHELL_EXIT = 3;
    private static final int SHELL_HEADER_BYTES = 5;

    private final Socket raw;
    private final SSLSocket tls;
    private final DataInputStream in;
    private final OutputStream out;
    private final String banner;
    private final boolean shellV2;
    private int nextLocalId = 1;

    private AdbTransport(Socket raw, SSLSocket tls, DataInputStream in, OutputStream out, String banner) {
        this.raw = raw;
        this.tls = tls;
        this.in = in;
        this.out = out;
        this.banner = banner;
        this.shellV2 = hasFeature(banner, "shell_v2");
    }

    /** Connects (connect timeout, SO_TIMEOUT on every read) and completes the handshake. */
    static AdbTransport connect(InetSocketAddress address, AdbKey key, int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        SSLContext context;
        try {
            context = SSLContext.getInstance("TLSv1.3");
            context.init(new KeyManager[] {key.keyManager()}, new TrustManager[] {new TrustAny()}, null);
        } catch (GeneralSecurityException e) {
            throw new IOException("TLS 1.3 unavailable", e);
        }
        Socket raw = new Socket();
        boolean ok = false;
        try {
            raw.setSoTimeout(readTimeoutMs);
            raw.connect(address, connectTimeoutMs);
            AdbTransport transport = connectOn(raw, key, context);
            ok = true;
            return transport;
        } finally {
            if (!ok) {
                closeQuietly(raw);
            }
        }
    }

    /**
     * The handshake on a connected socket, keeping its SO_TIMEOUT for the TLS socket.
     * {@code tlsContext} must present {@code key}'s certificate. Closes the socket on failure.
     */
    static AdbTransport connectOn(Socket socket, AdbKey key, SSLContext tlsContext) throws IOException {
        SSLSocket tls = null;
        boolean ok = false;
        try {
            DataInputStream rawIn = new DataInputStream(socket.getInputStream()); // unbuffered: TLS follows
            OutputStream rawOut = socket.getOutputStream();
            write(rawOut, new AdbMessage(A_CNXN, VERSION, MAX_PAYLOAD, nulTerminated(HOST_BANNER)));
            AdbMessage reply = AdbMessage.read(rawIn);
            if (reply.command != A_STLS) {
                throw new IOException("expected STLS, got " + name(reply.command));
            }
            write(rawOut, new AdbMessage(A_STLS, STLS_VERSION, 0, new byte[0]));

            tls = (SSLSocket) tlsContext.getSocketFactory().createSocket(socket,
                    socket.getInetAddress().getHostAddress(), socket.getPort(), true);
            tls.setSoTimeout(socket.getSoTimeout());
            tls.setEnabledProtocols(new String[] {"TLSv1.3"});
            tls.setUseClientMode(true);
            tls.startHandshake();
            DataInputStream in = new DataInputStream(new BufferedInputStream(tls.getInputStream()));
            OutputStream out = new BufferedOutputStream(tls.getOutputStream());
            AdbMessage cnxn = AdbMessage.read(in);
            if (cnxn.command != A_CNXN) {
                throw new IOException("expected CNXN after TLS, got " + name(cnxn.command));
            }
            AdbTransport transport = new AdbTransport(socket, tls, in, out, stripNul(cnxn.payload));
            ok = true;
            return transport;
        } finally {
            if (!ok) {
                if (tls != null) {
                    closeQuietly(tls);
                }
                closeQuietly(socket);
            }
        }
    }

    /** adbd's banner, e.g. {@code device::ro.product.name=x;features=shell_v2,cmd}. */
    String banner() {
        return banner;
    }

    /**
     * Runs {@code command}; stdout then stderr interleaved as sent, as UTF-8. Throws on a non-zero
     * exit (code in the message, output never), or when adbd refuses or drops the stream.
     */
    String shell(String command) throws IOException {
        int localId = nextLocalId++;
        String service = (shellV2 ? "shell,v2,raw:" : "shell:") + command;
        write(out, new AdbMessage(A_OPEN, localId, 0, nulTerminated(service)));

        int remoteId = 0;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while (true) {
            AdbMessage m = AdbMessage.read(in);
            if (m.arg1 != localId) {
                continue; // a late message for an earlier stream
            }
            if (m.command == A_OKAY) {
                if (remoteId == 0) {
                    remoteId = m.arg0;
                }
            } else if (m.command == A_WRTE) {
                if (remoteId == 0) {
                    throw new IOException("adb WRTE before OKAY");
                }
                if (data.size() + m.payload.length > MAX_OUTPUT) {
                    throw new IOException("adb shell output too large");
                }
                data.write(m.payload, 0, m.payload.length);
                write(out, new AdbMessage(A_OKAY, localId, remoteId, new byte[0]));
            } else if (m.command == A_CLSE) {
                if (remoteId == 0) {
                    throw new IOException("adb refused the shell service");
                }
                write(out, new AdbMessage(A_CLSE, localId, remoteId, new byte[0]));
                break;
            } else {
                throw new IOException("unexpected adb message " + name(m.command));
            }
        }
        byte[] bytes = data.toByteArray();
        return shellV2 ? decodeShellV2(bytes) : new String(bytes, StandardCharsets.UTF_8);
    }

    /** Shell protocol v2 packets: id (1 byte), length (uint32 LE), data. Keeps stdout and stderr. */
    static String decodeShellV2(byte[] bytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Integer exit = null;
        while (b.hasRemaining()) {
            if (b.remaining() < SHELL_HEADER_BYTES) {
                throw new IOException("truncated shell packet");
            }
            int id = b.get() & 0xff;
            int length = b.getInt();
            if (length < 0 || length > b.remaining()) {
                throw new IOException("truncated shell packet");
            }
            if (id == SHELL_STDOUT || id == SHELL_STDERR) {
                output.write(bytes, b.position(), length);
            } else if (id == SHELL_EXIT) {
                if (length < 1) {
                    throw new IOException("empty shell exit packet");
                }
                exit = bytes[b.position()] & 0xff;
            }
            b.position(b.position() + length);
        }
        if (exit == null) {
            throw new IOException("shell ended without an exit status");
        }
        if (exit != 0) {
            throw new IOException("shell command exited with status " + exit);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Whether the banner's {@code features=} list (after {@code ::}, {@code ;}-separated) has {@code feature}. */
    static boolean hasFeature(String banner, String feature) {
        int sep = banner.indexOf("::");
        String props = sep < 0 ? "" : banner.substring(sep + 2);
        for (String prop : props.split(";")) {
            if (prop.startsWith("features=")) {
                for (String f : prop.substring("features=".length()).split(",")) {
                    if (f.equals(feature)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void close() {
        closeQuietly(tls);
        closeQuietly(raw);
    }

    private static void write(OutputStream out, AdbMessage m) throws IOException {
        out.write(m.encode());
        out.flush();
    }

    private static byte[] nulTerminated(String s) {
        return (s + "\0").getBytes(StandardCharsets.UTF_8);
    }

    private static String stripNul(byte[] payload) {
        int end = payload.length;
        while (end > 0 && payload[end - 1] == 0) {
            end--;
        }
        return new String(payload, 0, end, StandardCharsets.UTF_8);
    }

    /** The four ASCII letters of a command, for error messages. */
    private static String name(int command) {
        byte[] b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array();
        for (byte c : b) {
            if (c < 0x20 || c > 0x7e) {
                return "0x" + Integer.toHexString(command);
            }
        }
        return new String(b, StandardCharsets.US_ASCII);
    }

    private static void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            // Nothing to do; the connection is over either way.
        }
    }
}
