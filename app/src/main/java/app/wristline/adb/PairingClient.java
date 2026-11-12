package app.wristline.adb;

import app.wristline.adb.Spake2.Role;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

/**
 * Pairs with this phone's Wireless debugging the way {@code adb pair} does
 * (adb/pairing_connection): TLS 1.3, SPAKE2 over the six-digit code bound to the TLS session by
 * exported keying material, then each side's PeerInfo sealed with the SPAKE2 key. After this
 * adbd accepts {@link AdbKey}'s certificate on its connect port.
 *
 * <p>Nothing here logs: the code, SPAKE2 messages, exported material and PeerInfo must never
 * reach a log. Errors are thrown as {@link IOException}s with fixed messages; callers log only
 * the exception class and message.
 */
final class PairingClient {
    /** Exported keying material from the TLS session; split out because the JVM has no SSLSockets. */
    interface KeyExporter {
        byte[] export(SSLSocket socket, String label, int length) throws IOException;
    }

    static final int TYPE_SPAKE2_MSG = 0;
    static final int TYPE_PEER_INFO = 1;
    static final int PEER_INFO_BYTES = 8192;
    /** adb's kExportedKeyLabel is {@code "adb-label\0"}, passed with its NUL (sizeof). */
    static final String EXPORT_LABEL = "adb-label\0";
    static final int EXPORT_BYTES = 64;

    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 6;
    private static final int MAX_PAYLOAD = 16384;
    private static final int SPAKE2_MSG_BYTES = 32;

    private PairingClient() {
    }

    /**
     * Connects (connect timeout, then SO_TIMEOUT on every read), does TLS 1.3 presenting
     * {@code key}'s certificate, and pairs. Throws on any failure, including a wrong code.
     */
    static void pair(InetSocketAddress address, String code, AdbKey key, int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        Socket raw = new Socket();
        SSLSocket tls = null;
        try {
            raw.setSoTimeout(readTimeoutMs);
            raw.connect(address, connectTimeoutMs);
            SSLContext context;
            try {
                context = SSLContext.getInstance("TLSv1.3");
                context.init(new KeyManager[] {key.keyManager()}, new TrustManager[] {new TrustAny()}, null);
            } catch (GeneralSecurityException e) {
                throw new IOException("TLS 1.3 unavailable", e);
            }
            tls = (SSLSocket) context.getSocketFactory()
                    .createSocket(raw, address.getHostString(), address.getPort(), true);
            tls.setSoTimeout(readTimeoutMs);
            tls.setEnabledProtocols(new String[] {"TLSv1.3"});
            tls.setUseClientMode(true);
            tls.startHandshake();
            pairOn(tls, code, key.peerInfo(), new AndroidKeyExporter());
        } finally {
            if (tls != null) {
                closeQuietly(tls);
            }
            closeQuietly(raw);
        }
    }

    /** The exchange on an already handshaken socket; {@code peerInfo} is {@link AdbKey#peerInfo()}. */
    static void pairOn(SSLSocket tls, String code, byte[] peerInfo, KeyExporter exporter) throws IOException {
        if (peerInfo.length != PEER_INFO_BYTES) {
            throw new IllegalArgumentException("peer info must be " + PEER_INFO_BYTES + " bytes");
        }
        byte[] exported = exporter.export(tls, EXPORT_LABEL, EXPORT_BYTES);
        byte[] codeBytes = code.getBytes(StandardCharsets.US_ASCII);
        byte[] password = Arrays.copyOf(codeBytes, codeBytes.length + exported.length);
        System.arraycopy(exported, 0, password, codeBytes.length, exported.length);
        Arrays.fill(exported, (byte) 0);

        DataInputStream in = new DataInputStream(tls.getInputStream());
        DataOutputStream out = new DataOutputStream(tls.getOutputStream());
        Spake2 spake = new Spake2(Role.ALICE, name("adb pair client"), name("adb pair server"), new SecureRandom());
        byte[] spakeKey = null;
        try {
            byte[] mine = spake.generateMessage(password);
            writeFrame(out, TYPE_SPAKE2_MSG, mine);
            byte[] theirs = readFrame(in, TYPE_SPAKE2_MSG);
            if (theirs.length != SPAKE2_MSG_BYTES) {
                throw new IOException("bad SPAKE2 message length");
            }
            try {
                spakeKey = spake.processMessage(theirs);
            } catch (IllegalArgumentException e) {
                throw new IOException("bad SPAKE2 message");
            }
            PairingCipher cipher = new PairingCipher(spakeKey);
            try {
                writeFrame(out, TYPE_PEER_INFO, cipher.encrypt(peerInfo));
                byte[] plain = cipher.decrypt(readFrame(in, TYPE_PEER_INFO));
                int length = plain.length;
                Arrays.fill(plain, (byte) 0);
                if (length != PEER_INFO_BYTES) {
                    throw new IOException("bad peer info length");
                }
            } catch (GeneralSecurityException e) {
                // A wrong code shows up here: the keys differ, so the tag does not verify.
                throw new IOException("pairing failed: wrong code or corrupt message");
            }
        } finally {
            Arrays.fill(password, (byte) 0);
            if (spakeKey != null) {
                Arrays.fill(spakeKey, (byte) 0);
            }
        }
    }

    /**
     * A SPAKE2 name as adb passes it: US-ASCII with the trailing NUL, because adb hands
     * {@code sizeof(kClientName)} to BoringSSL. Leaving the NUL out changes the derived key.
     */
    static byte[] name(String s) {
        byte[] ascii = s.getBytes(StandardCharsets.US_ASCII);
        return Arrays.copyOf(ascii, ascii.length + 1);
    }

    /** Version 1, type, big-endian uint32 payload length. */
    static byte[] header(int type, int payloadLength) {
        return new byte[] {
            VERSION, (byte) type,
            (byte) (payloadLength >>> 24), (byte) (payloadLength >>> 16),
            (byte) (payloadLength >>> 8), (byte) payloadLength
        };
    }

    /** {@code {version, type, length}}; throws on anything adbd would never send. */
    static int[] readHeader(DataInputStream in) throws IOException {
        byte[] h = new byte[HEADER_BYTES];
        in.readFully(h);
        int version = h[0] & 0xff;
        int type = h[1] & 0xff;
        long length = ((h[2] & 0xffL) << 24) | ((h[3] & 0xff) << 16) | ((h[4] & 0xff) << 8) | (h[5] & 0xff);
        if (version != VERSION) {
            throw new IOException("unsupported pairing version " + version);
        }
        if (type != TYPE_SPAKE2_MSG && type != TYPE_PEER_INFO) {
            throw new IOException("unknown pairing message type " + type);
        }
        if (length == 0 || length > MAX_PAYLOAD) {
            throw new IOException("bad pairing payload length " + length);
        }
        return new int[] {version, type, (int) length};
    }

    private static void writeFrame(DataOutputStream out, int type, byte[] payload) throws IOException {
        out.write(header(type, payload.length));
        out.write(payload);
        out.flush();
    }

    private static byte[] readFrame(DataInputStream in, int expectedType) throws IOException {
        int[] h = readHeader(in);
        if (h[1] != expectedType) {
            throw new IOException("unexpected pairing message type " + h[1]);
        }
        byte[] payload = new byte[h[2]];
        try {
            in.readFully(payload);
        } catch (EOFException e) {
            throw new IOException("pairing connection closed mid-message", e);
        }
        return payload;
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException e) {
            // Nothing to do; the attempt is over either way.
        }
    }
}
