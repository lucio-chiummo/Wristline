package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationManager;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Keeping the phone's ring notification off the watch that shows Wristline's own incoming screen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class RingBridgingTest {
    @Test
    public void theBridgeTagMatchesThePhone() {
        assertEquals("wristline_ring", Protocol.RING_BRIDGE_TAG);
    }

    @Test
    public void aWatchWithoutTheBridgeServiceCarriesOn() {
        Context context = RuntimeEnvironment.getApplication();
        RingBridging.exclude(context);
        RingBridging.exclude(context);
    }

    @Test
    public void theRingNotificationIsKeptOffWhileTheIncomingScreenCanShow() {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(context.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);

        assertEquals(java.util.Set.of(Protocol.RING_BRIDGE_TAG), RingBridging.config(context).getExcludedTags());
    }

    /** With this app's notifications off its incoming screen can't show: the phone's ring must arrive. */
    @Test
    public void theRingNotificationBridgesWhileThisAppsNotificationsAreOff() {
        Context context = RuntimeEnvironment.getApplication();
        shadowOf(context.getSystemService(NotificationManager.class)).setNotificationsEnabled(false);

        assertTrue(RingBridging.config(context).getExcludedTags().isEmpty());
        assertTrue(RingBridging.config(context).isBridgingEnabled());
    }
}
