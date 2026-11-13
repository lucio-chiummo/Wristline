package app.wristline.adb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The facade's own logic; the wire protocol is covered by AdbTransportTest and PairingClientTest. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AdbShellTest {
    @Test
    public void pairReturnsFalseWhenNothingListens() {
        Application ctx = RuntimeEnvironment.getApplication();
        long start = System.nanoTime();
        assertFalse(AdbShell.pair(ctx, new InetSocketAddress(InetAddress.getLoopbackAddress(), 1), "123456"));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < AdbShell.PAIR_TIMEOUT_MS);
    }

    @Test
    public void forgetKeyDeletesTheKey() {
        Application ctx = RuntimeEnvironment.getApplication();
        AdbKey.load(ctx);
        assertTrue(AdbKey.exists(ctx));
        AdbShell.forgetKey(ctx);
        assertFalse(AdbKey.exists(ctx));
    }

    @Test
    public void forgottenKeyStaysForgottenWhenAdbIsChecked() {
        Application ctx = RuntimeEnvironment.getApplication();
        AdbKey.load(ctx);
        assertNotNull(AdbShell.existingKey(ctx));
        AdbShell.forgetKey(ctx);

        assertNull(AdbShell.existingKey(ctx));
        assertFalse(AdbKey.exists(ctx));
        assertFalse(AdbShell.isReachable(ctx));
        assertFalse(AdbKey.exists(ctx));
        assertNull(AdbShell.run(ctx, List.of("true")));
        assertFalse(AdbKey.exists(ctx));
    }

    @Test
    public void stuckPeerTimesOutAndThePairingThreadEnds() throws Exception {
        Application ctx = RuntimeEnvironment.getApplication();
        AdbKey key = AdbKey.load(ctx);
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            // Accepts the connection and never answers, like a pairing dialog that went away.
            Thread acceptor = new Thread(() -> {
                try (Socket peer = server.accept()) {
                    while (peer.getInputStream().read() >= 0) {
                        // Swallow the TLS hello; never reply.
                    }
                } catch (Exception e) {
                    // The client closed: done.
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            long start = System.nanoTime();
            assertFalse(AdbShell.pair(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()),
                    "123456", () -> key, 500));
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("took " + ms + " ms", ms < 3000);

            long end = System.currentTimeMillis() + 3000;
            while (pairThreadAlive() && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
            assertFalse("adb-pair thread still running", pairThreadAlive());
            acceptor.join(3000);
            assertFalse("peer never saw the socket close", acceptor.isAlive());
        }
    }

    private static boolean pairThreadAlive() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("adb-pair".equals(thread.getName()) && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }
}
