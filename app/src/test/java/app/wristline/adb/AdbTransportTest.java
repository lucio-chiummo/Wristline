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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The adb handshake (CNXN, STLS, TLS 1.3, CNXN) and shell streams against a scripted adbd on loopback. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AdbTransportTest {
    private static final String V2_BANNER = "device::ro.product.name=x;features=shell_v2,cmd";
    private static final String LEGACY_BANNER = "device::features=cmd";
    private static final int REMOTE = 1;

    private static final KeyPair SERVER = TlsTestSupport.rsa();

    /** One adbd-side session script; assertion failures on the fake's side fail the test. */
    private interface Script {
        void run(Fake f) throws Exception;
    }

    // ---- handshake ----

    @Test
    public void connectsOverStlsAndRunsShellV2() throws Exception {
        runAgainst(f -> {
            f.upgrade(V2_BANNER);
            AdbMessage open = f.expect(A_OPEN);
            f.send(A_OKAY, REMOTE, open.arg0);
            f.send(A_WRTE, REMOTE, open.arg0, concat(v2(1, "ok\n"), v2(3, new byte[] {0})));
            f.expectAck(open.arg0);
            f.closeStream(open.arg0);
        }, t -> {
            assertEquals("ok\n", t.shell("echo ok"));
            assertTrue(t.banner().contains("shell_v2"));
            assertFalse(t.banner().endsWith("\0"));
        });
    }

    @Test
    public void clientCnxnAndStlsCarryTheExpectedFields() throws Exception {
        runAgainst(f -> {
            AdbMessage cnxn = f.expect(A_CNXN);
            assertEquals(VERSION, cnxn.arg0);
            assertEquals(MAX_PAYLOAD, cnxn.arg1);
            assertEquals("host::features=shell_v2,cmd\0", new String(cnxn.payload, StandardCharsets.UTF_8));
            f.send(A_STLS, STLS_VERSION, 0);
            AdbMessage stls = f.expect(A_STLS);
            assertEquals(STLS_VERSION, stls.arg0);
            assertEquals(0, stls.arg1);
            f.tls();
            f.send(A_CNXN, VERSION, MAX_PAYLOAD, (V2_BANNER + "\0").getBytes(StandardCharsets.UTF_8));
            f.awaitClose();
        }, t -> assertEquals(V2_BANNER, t.banner()));
    }

    @Test
    public void clientCertificateIsPresented() throws Exception {
        AtomicReference<Certificate> seen = new AtomicReference<>();
        runAgainst(f -> {
            f.upgrade(V2_BANNER);
            seen.set(f.tls.getSession().getPeerCertificates()[0]);
            f.awaitClose();
        }, t -> {
        });
        assertEquals(key().certificate(), seen.get());
    }

    @Test
    public void cnxnWithoutStlsThrows() {
        IOException e = assertThrows(IOException.class, () -> runAgainst(f -> {
            f.expect(A_CNXN);
            f.send(A_CNXN, VERSION, MAX_PAYLOAD, (V2_BANNER + "\0").getBytes(StandardCharsets.UTF_8));
            f.awaitClose();
        }, t -> {
        }));
        assertTrue(e.getMessage(), e.getMessage().contains("expected STLS"));
    }

    @Test
    public void somethingOtherThanCnxnAfterTlsThrows() {
        assertThrows(IOException.class, () -> runAgainst(f -> {
            f.expect(A_CNXN);
            f.send(A_STLS, STLS_VERSION, 0);
            f.expect(A_STLS);
            f.tls();
            f.send(A_OKAY, 1, 1);
            f.awaitClose();
        }, t -> {
        }));
    }

    // ---- shell ----

    @Test
    public void openPayloadIsNulTerminated() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        runAgainst(f -> {
            f.upgrade(V2_BANNER);
            AdbMessage open = f.expect(A_OPEN);
            payload.set(new String(open.payload, StandardCharsets.UTF_8));
            f.send(A_OKAY, REMOTE, open.arg0);
            f.send(A_WRTE, REMOTE, open.arg0, v2(3, new byte[] {0}));
            f.expectAck(open.arg0);
            f.closeStream(open.arg0);
        }, t -> assertEquals("", t.shell("echo ok")));
        assertEquals("shell,v2,raw:echo ok\0", payload.get());
    }

    @Test
    public void fallsBackToLegacyShellWithoutShellV2() throws Exception {
        AtomicReference<String> payload = new AtomicReference<>();
        runAgainst(f -> {
            f.upgrade(LEGACY_BANNER);
            AdbMessage open = f.expect(A_OPEN);
            payload.set(new String(open.payload, StandardCharsets.UTF_8));
            f.send(A_OKAY, REMOTE, open.arg0);
            f.send(A_WRTE, REMOTE, open.arg0, "o".getBytes(StandardCharsets.UTF_8));
            f.expectAck(open.arg0);
            f.send(A_WRTE, REMOTE, open.arg0, "k\n".getBytes(StandardCharsets.UTF_8));
            f.expectAck(open.arg0);
            f.closeStream(open.arg0);
        }, t -> assertEquals("ok\n", t.shell("echo ok")));
        assertEquals("shell:echo ok\0", payload.get());
    }

    @Test
    public void v2PacketsSplitAcrossWritesWithStderr() throws Exception {
        runAgainst(f -> {
            f.upgrade(V2_BANNER);
            AdbMessage open = f.expect(A_OPEN);
            f.send(A_OKAY, REMOTE, open.arg0);
            byte[] all = concat(v2(1, "a"), v2(2, "b"), v2(1, "c"), v2(3, new byte[] {0}));
            for (int i = 0; i < all.length; i += 3) {
                byte[] chunk = java.util.Arrays.copyOfRange(all, i, Math.min(all.length, i + 3));
                f.send(A_WRTE, REMOTE, open.arg0, chunk);
                f.expectAck(open.arg0);
            }
            f.closeStream(open.arg0);
        }, t -> assertEquals("abc", t.shell("x")));
    }

    @Test
    public void nonZeroExitThrows() {
        IOException e = assertThrows(IOException.class, () -> runAgainst(f -> {
            f.upgrade(V2_BANNER);
            AdbMessage open = f.expect(A_OPEN);
            f.send(A_OKAY, REMOTE, open.arg0);
            f.send(A_WRTE, REMOTE, open.arg0, concat(v2(1, "secret"), v2(3, new byte[] {1})));
            f.expectAck(open.arg0);
            f.closeStream(open.arg0);
        }, t -> t.shell("false")));
        assertTrue(e.getMessage(), e.getMessage().contains("1"));
        assertFalse(e.getMessage(), e.getMessage().contains("secret"));
    }

    @Test
    public void v2WithoutExitThrows() {
        assertThrows(IOException.class, () -> runAgainst(f -> {
            f.upgrade(V2_BANNER);
            AdbMessage open = f.expect(A_OPEN);
            f.send(A_OKAY, REMOTE, open.arg0);
            f.send(A_WRTE, REMOTE, open.arg0, v2(1, "ok\n"));
            f.expectAck(open.arg0);
            f.closeStream(open.arg0);
        }, t -> t.shell("echo ok")));
    }

    @Test
    public void clseBeforeOkayThrows() {
        assertThrows(IOException.class, () -> runAgainst(f -> {
            f.upgrade(V2_BANNER);
            AdbMessage open = f.expect(A_OPEN);
            f.send(A_CLSE, 0, open.arg0);
            f.awaitClose();
        }, t -> t.shell("echo ok")));
    }

    @Test
    public void localIdsIncrementPerShell() throws Exception {
        AtomicReference<String> ids = new AtomicReference<>("");
        runAgainst(f -> {
            f.upgrade(V2_BANNER);
            for (int i = 0; i < 2; i++) {
                AdbMessage open = f.expect(A_OPEN);
                ids.set(ids.get() + open.arg0 + "," + open.arg1 + ";");
                f.send(A_OKAY, REMOTE + i, open.arg0);
                f.send(A_WRTE, REMOTE + i, open.arg0, concat(v2(1, "r" + i), v2(3, new byte[] {0})));
                AdbMessage ack = f.expect(A_OKAY);
                assertEquals(open.arg0, ack.arg0);
                assertEquals(REMOTE + i, ack.arg1);
                f.send(A_CLSE, REMOTE + i, open.arg0);
                f.expect(A_CLSE);
            }
            f.awaitClose();
        }, t -> {
            assertEquals("r0", t.shell("a"));
            assertEquals("r1", t.shell("b"));
        });
        assertEquals("1,0;2,0;", ids.get());
    }

    @Test
    public void connectFailsWhenNothingListens() {
        assertThrows(IOException.class, () -> AdbTransport.connect(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 1), key(), 500, 500));
    }

    // ---- fake adbd ----

    private interface Client {
        void run(AdbTransport t) throws Exception;
    }

    private static AdbKey key() {
        return AdbKey.load(RuntimeEnvironment.getApplication());
    }

    private static void runAgainst(Script script, Client client) throws Exception {
        AdbKey key = key();
        SSLContext serverContext = SSLContext.getInstance("TLSv1.3");
        serverContext.init(TlsTestSupport.keyManagers(SERVER, "adbd"), new TrustManager[] {new TrustAny()}, null);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        CountDownLatch serverDone = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(10000);
            Thread thread = new Thread(() -> {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(10000);
                    script.run(new Fake(s, serverContext));
                } catch (Throwable t) {
                    serverFailure.set(t);
                } finally {
                    serverDone.countDown();
                }
            }, "fake-adbd");
            thread.start();

            SSLContext clientContext = SSLContext.getInstance("TLSv1.3");
            clientContext.init(new KeyManager[] {key.keyManager()}, new TrustManager[] {new TrustAny()}, null);
            Socket raw = new Socket();
            raw.setSoTimeout(10000);
            raw.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()), 4000);
            try (AdbTransport t = AdbTransport.connectOn(raw, key, clientContext)) {
                client.run(t);
            } finally {
                raw.close();
                assertTrue(serverDone.await(15, TimeUnit.SECONDS));
                Throwable t = serverFailure.get();
                // The fake's own assertions must hold; socket errors after the client closed are expected.
                if (t instanceof AssertionError) {
                    throw new AssertionError("fake adbd failed", t);
                }
            }
        }
    }

    /** adbd's side of one connection. */
    private static final class Fake {
        final Socket raw;
        final SSLContext context;
        SSLSocket tls;
        DataInputStream in;
        OutputStream out;

        Fake(Socket raw, SSLContext context) throws IOException {
            this.raw = raw;
            this.context = context;
            in = new DataInputStream(raw.getInputStream());
            out = raw.getOutputStream();
        }

        /** CNXN, STLS both ways, TLS, then adbd's CNXN carrying {@code banner}. */
        void upgrade(String banner) throws IOException {
            expect(A_CNXN);
            send(A_STLS, STLS_VERSION, 0);
            expect(A_STLS);
            tls();
            send(A_CNXN, VERSION, MAX_PAYLOAD, (banner + "\0").getBytes(StandardCharsets.UTF_8));
        }

        void tls() throws IOException {
            tls = (SSLSocket) context.getSocketFactory().createSocket(raw,
                    raw.getInetAddress().getHostAddress(), raw.getPort(), true);
            tls.setUseClientMode(false);
            tls.setWantClientAuth(true);
            tls.setEnabledProtocols(new String[] {"TLSv1.3"});
            tls.startHandshake();
            InputStream tin = new BufferedInputStream(tls.getInputStream());
            in = new DataInputStream(tin);
            out = tls.getOutputStream();
        }

        void send(int command, int arg0, int arg1) throws IOException {
            send(command, arg0, arg1, new byte[0]);
        }

        void send(int command, int arg0, int arg1, byte[] payload) throws IOException {
            out.write(new AdbMessage(command, arg0, arg1, payload).encode());
            out.flush();
        }

        AdbMessage expect(int command) throws IOException {
            AdbMessage m = AdbMessage.read(in);
            assertEquals(Integer.toHexString(command), Integer.toHexString(m.command));
            return m;
        }

        /** The client's OKAY(local, remote) for a WRTE. */
        void expectAck(int localId) throws IOException {
            AdbMessage ok = expect(A_OKAY);
            assertEquals(localId, ok.arg0);
            assertEquals(REMOTE, ok.arg1);
        }

        /** CLSE from the device, then the client's single CLSE back. */
        void closeStream(int localId) throws IOException {
            send(A_CLSE, REMOTE, localId);
            AdbMessage clse = expect(A_CLSE);
            assertEquals(localId, clse.arg0);
            assertEquals(REMOTE, clse.arg1);
            awaitClose();
        }

        /** Holds the connection open until the client closes it; nothing else may arrive. */
        void awaitClose() {
            try {
                int b = in.read();
                assertEquals("unexpected bytes after the script", -1, b);
            } catch (IOException e) {
                // closed
            }
        }
    }

    // ---- shell protocol v2 packets ----

    private static byte[] v2(int id, String data) {
        return v2(id, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] v2(int id, byte[] data) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(id);
        b.write(data.length);
        b.write(data.length >>> 8);
        b.write(data.length >>> 16);
        b.write(data.length >>> 24);
        b.write(data, 0, data.length);
        return b.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            b.write(p, 0, p.length);
        }
        return b.toByteArray();
    }
}
