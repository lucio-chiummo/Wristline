package app.wristline.adb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import java.net.InetAddress;
import java.net.InetSocketAddress;
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
}
