package app.wristline.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import app.wristline.adb.Spake2.Role;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.junit.Test;

/** The pairing exchange against a scripted adbd over real TLS 1.3 on loopback. */
public class PairingClientTest {
    private static final String CODE = "482913";
    private static final byte[] EXPORTED = new byte[64];
    private static final PairingClient.KeyExporter FIXED = (s, label, length) -> {
        assertEquals("adb-label\0", label);
        assertEquals(64, length);
        return EXPORTED.clone();
    };

    static {
        for (int i = 0; i < EXPORTED.length; i++) {
            EXPORTED[i] = (byte) (0xA0 ^ i);
        }
    }

    private static final KeyPair CLIENT = rsa();
    private static final KeyPair SERVER = rsa();

    /** One server-side session script; throws to record a failure on the fake's side. */
    private interface Script {
        void run(SSLSocket s, DataInputStream in, DataOutputStream out) throws Exception;
    }

    // ---- frame helpers ----

    @Test
    public void headerIsVersionTypeBigEndianLength() {
        assertArrayEquals(new byte[] {1, 1, 0, 0, 0x20, 0x10}, PairingClient.header(1, 0x2010));
    }

    @Test
    public void readHeaderParses() throws IOException {
        int[] h = PairingClient.readHeader(stream(1, 0, 0, 0, 0, 32));
        assertArrayEquals(new int[] {1, 0, 32}, h);
        assertArrayEquals(new int[] {1, 1, 16384}, PairingClient.readHeader(stream(1, 1, 0, 0, 0x40, 0)));
    }

    @Test
    public void readHeaderRejectsBadFields() {
        assertThrows(IOException.class, () -> PairingClient.readHeader(stream(2, 0, 0, 0, 0, 32)));
        assertThrows(IOException.class, () -> PairingClient.readHeader(stream(1, 2, 0, 0, 0, 32)));
        assertThrows(IOException.class, () -> PairingClient.readHeader(stream(1, 0, 0, 0, 0, 0)));
        assertThrows(IOException.class, () -> PairingClient.readHeader(stream(1, 0, 0, 0, 0x40, 1)));
        assertThrows(IOException.class, () -> PairingClient.readHeader(stream(1, 0, (byte) 0x80, 0, 0, 1)));
        assertThrows(IOException.class, () -> PairingClient.readHeader(stream(1, 0, 0)));
    }

    @Test
    public void namesCarryTrailingNul() {
        assertArrayEquals("adb pair client\0".getBytes(StandardCharsets.US_ASCII), PairingClient.name("adb pair client"));
        assertEquals(16, PairingClient.name("adb pair server").length);
    }

    // ---- full exchange ----

    @Test
    public void pairsWithFakeAdbd() throws Exception {
        runAgainst(adbd(CODE, true, 8192), 15000);
    }

    @Test
    public void wrongCodeFails() throws Exception {
        assertThrows(IOException.class, () -> runAgainst(adbd("000000", false, 8192), 15000));
    }

    @Test
    public void peerInfoOfWrongLengthFails() throws Exception {
        IOException e = assertThrows(IOException.class, () -> runAgainst(adbd(CODE, true, 100), 15000));
        assertTrue(e.getMessage(), e.getMessage().contains("peer info"));
    }

    @Test
    public void headerVersion2Rejected() {
        assertThrows(IOException.class, () -> runAgainst((s, in, out) -> {
            readFrame(in, 0);
            out.write(new byte[] {2, 0, 0, 0, 0, 32});
            out.write(new byte[32]);
            out.flush();
            awaitClose(in);
        }, 15000));
    }

    @Test
    public void zeroPayloadRejected() {
        assertThrows(IOException.class, () -> runAgainst((s, in, out) -> {
            readFrame(in, 0);
            out.write(new byte[] {1, 0, 0, 0, 0, 0});
            out.flush();
            awaitClose(in);
        }, 15000));
    }

    @Test
    public void socketReadTimeoutIsSet() {
        long start = System.nanoTime();
        assertThrows(SocketTimeoutException.class, () -> runAgainst((s, in, out) -> {
            readFrame(in, 0);
            awaitClose(in);
        }, 500));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("took " + ms + " ms", ms < 5000);
    }

    @Test
    public void pairFailsWhenNothingListens() throws Exception {
        // pair() needs SSLSockets (Android) past the handshake; here only the connect failure is checked.
        assertThrows(IOException.class, () -> PairingClient.pair(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 1), CODE, null, 500, 500));
    }

    // ---- fake adbd ----

    /** adbd's side: real SPAKE2 as BOB, then decrypt and check our PeerInfo, then send its own. */
    private static Script adbd(String code, boolean expectDecryptOk, int replyLength) {
        return (s, in, out) -> {
            byte[] password = concat(code.getBytes(StandardCharsets.US_ASCII), EXPORTED);
            Spake2 bob = new Spake2(Role.BOB, PairingClient.name("adb pair server"),
                    PairingClient.name("adb pair client"), new SecureRandom());
            byte[] theirs = readFrame(in, 0);
            assertEquals(32, theirs.length);
            byte[] mine = bob.generateMessage(password);
            out.write(PairingClient.header(0, mine.length));
            out.write(mine);
            out.flush();
            PairingCipher cipher = new PairingCipher(bob.processMessage(theirs));

            byte[] sealed = readFrame(in, 1);
            byte[] plain;
            try {
                plain = cipher.decrypt(sealed);
            } catch (GeneralSecurityException e) {
                if (expectDecryptOk) {
                    throw e;
                }
                return; // adbd drops the connection on a wrong code
            }
            if (!expectDecryptOk) {
                fail("decrypt with a wrong code succeeded");
            }
            assertEquals(8192, plain.length);
            assertEquals(0, plain[0]);
            int end = 1;
            while (plain[end] != 0) {
                end++;
            }
            String line = new String(plain, 1, end - 1, StandardCharsets.US_ASCII);
            String[] parts = line.split(" ", 2);
            assertArrayEquals(AndroidPubkey.encode((RSAPublicKey) CLIENT.getPublic()),
                    Base64.getDecoder().decode(parts[0]));
            assertEquals("wristline@test", parts[1]);

            byte[] reply = new byte[replyLength];
            reply[0] = 1; // ADB_DEVICE_GUID
            byte[] guid = "adb-FAKE1234-abcdef".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(guid, 0, reply, 1, Math.min(guid.length, replyLength - 1));
            byte[] enc = cipher.encrypt(reply);
            out.write(PairingClient.header(1, enc.length));
            out.write(enc);
            out.flush();
            awaitClose(in);
        };
    }

    private static void runAgainst(Script script, int clientSoTimeoutMs) throws Exception {
        SSLContext serverContext = SSLContext.getInstance("TLSv1.3");
        serverContext.init(keyManagers(SERVER, "adbd"), new TrustManager[] {new TrustAny()}, null);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        CountDownLatch serverDone = new CountDownLatch(1);
        try (SSLServerSocket server = (SSLServerSocket) serverContext.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setEnabledProtocols(new String[] {"TLSv1.3"});
            server.setNeedClientAuth(true);
            server.setSoTimeout(10000);
            Thread thread = new Thread(() -> {
                try (SSLSocket s = (SSLSocket) server.accept()) {
                    s.setSoTimeout(10000);
                    s.startHandshake();
                    script.run(s, new DataInputStream(s.getInputStream()), new DataOutputStream(s.getOutputStream()));
                } catch (Throwable t) {
                    serverFailure.set(t);
                } finally {
                    serverDone.countDown();
                }
            }, "fake-adbd");
            thread.start();

            SSLContext clientContext = SSLContext.getInstance("TLSv1.3");
            clientContext.init(keyManagers(CLIENT, "wristline"), new TrustManager[] {new TrustAny()}, null);
            SSLSocket client = (SSLSocket) clientContext.getSocketFactory()
                    .createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort());
            try {
                client.setEnabledProtocols(new String[] {"TLSv1.3"});
                client.setUseClientMode(true);
                client.setSoTimeout(clientSoTimeoutMs);
                client.startHandshake();
                PairingClient.pairOn(client, CODE, peerInfo(), FIXED);
            } finally {
                client.close();
                assertTrue(serverDone.await(15, TimeUnit.SECONDS));
                Throwable t = serverFailure.get();
                // Fake's own assertions must hold; socket errors after the client closed are expected.
                if (t instanceof AssertionError || t instanceof GeneralSecurityException) {
                    throw new AssertionError("fake adbd failed", t);
                }
            }
        }
    }

    // ---- helpers ----

    private static byte[] readFrame(DataInputStream in, int type) throws IOException {
        int[] h = PairingClient.readHeader(in);
        assertEquals(type, h[1]);
        byte[] payload = new byte[h[2]];
        in.readFully(payload);
        return payload;
    }

    /** Holds the connection open until the client closes it (EOF or error). */
    private static void awaitClose(DataInputStream in) {
        try {
            while (in.read() >= 0) {
                // drain
            }
        } catch (IOException e) {
            // closed
        }
    }

    private static byte[] peerInfo() {
        byte[] line = AndroidPubkey.line((RSAPublicKey) CLIENT.getPublic(), "wristline@test")
                .getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[8192];
        System.arraycopy(line, 0, out, 1, line.length);
        return out;
    }

    private static KeyManager[] keyManagers(KeyPair pair, String cn) throws Exception {
        long now = System.currentTimeMillis();
        X509Certificate cert = SelfSigned.certificate(pair, cn, new Date(now - 86_400_000L), new Date(now + 86_400_000L));
        char[] pass = "test".toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, pass);
        store.setKeyEntry("k", pair.getPrivate(), pass, new Certificate[] {cert});
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, pass);
        return factory.getKeyManagers();
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DataInputStream stream(int... bytes) {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            b[i] = (byte) bytes[i];
        }
        return new DataInputStream(new ByteArrayInputStream(b));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
