package app.wristline.wear;

import static org.junit.Assert.assertEquals;

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
}
