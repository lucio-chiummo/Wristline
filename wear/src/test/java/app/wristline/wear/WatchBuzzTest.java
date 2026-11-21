package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.VibrationAttributes;
import android.os.Vibrator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowVibrator;

/** The watch app vibrating by itself for a ringing call. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WatchBuzzTest {
    private Context context;
    private ShadowVibrator vibrator;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        vibrator = shadowOf(context.getSystemService(Vibrator.class));
        vibrator.setHasVibrator(true);
    }

    @Test
    public void aRingVibratesWithRingtoneUsageForAtMostSixtySeconds() {
        WatchBuzz.start(context, 120_000);

        long[] pattern = vibrator.getPattern();
        assertNotNull(pattern);
        long total = 0;
        for (long ms : pattern) {
            total += ms;
        }
        assertTrue("ran " + total + " ms", total <= 60_000 && total > 50_000);
        assertEquals("a lost stop must not leave it buzzing", -1, vibrator.getRepeat());
        assertEquals("starts on straight away", 0, pattern[0]);
        assertEquals(700, pattern[1]);
        assertEquals(500, pattern[2]);
        assertEquals(700, pattern[3]);
        assertEquals(1600, pattern[4]);
        assertEquals(VibrationAttributes.USAGE_RINGTONE,
                ((VibrationAttributes) vibrator.getVibrationAttributesFromLastVibration()).getUsage());
    }

    @Test
    public void aShorterRingIsHonoured() {
        WatchBuzz.start(context, 5_000);

        long total = 0;
        for (long ms : vibrator.getPattern()) {
            total += ms;
        }
        assertEquals(5_000, total);
    }

    @Test
    public void aStopCancelsTheVibration() {
        WatchBuzz.start(context, 30_000);

        WatchBuzz.stop(context);

        assertTrue(vibrator.isCancelled());
    }

    @Test
    public void aStopReplacesTheBuzzWithASilentRingtoneVibration() {
        WatchBuzz.start(context, 30_000);

        WatchBuzz.stop(context);

        long[] pattern = vibrator.getPattern();
        assertNotNull(pattern);
        long total = 0;
        for (long ms : pattern) {
            total += ms;
        }
        assertTrue("ran " + total + " ms", total <= 1);
        assertEquals("not repeating", -1, vibrator.getRepeat());
        assertEquals(VibrationAttributes.USAGE_RINGTONE,
                ((VibrationAttributes) vibrator.getVibrationAttributesFromLastVibration()).getUsage());
    }

    @Test
    public void noVibratorDoesNothing() {
        vibrator.setHasVibrator(false);

        WatchBuzz.start(context, 30_000);

        assertFalse(vibrator.isVibrating());
        assertEquals(null, vibrator.getPattern());
    }
}
