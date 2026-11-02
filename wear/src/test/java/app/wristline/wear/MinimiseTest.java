package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Minimise must reach the watch face. Moving the task back reveals MainActivity, whose onResume
 * brings a running call straight back, so on a real watch the button seemed to do nothing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class MinimiseTest {
    private InCallActivity openCallScreen() {
        Intent intent = InCallActivity.intent(RuntimeEnvironment.getApplication(), "Corvan", Protocol.WHATSAPP);
        return Robolectric.buildActivity(InCallActivity.class, intent).setup().get();
    }

    @Test
    public void minimiseGoesToTheWatchFace() {
        InCallActivity screen = openCallScreen();

        screen.findViewById(R.id.minimise).performClick();

        Intent started = shadowOf(screen).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_MAIN, started.getAction());
        assertTrue(started.hasCategory(Intent.CATEGORY_HOME));
    }

    /** The call carries on: opening Wristline has to find this screen to bring it back. */
    @Test
    public void minimiseKeepsTheCallScreen() {
        InCallActivity screen = openCallScreen();

        screen.findViewById(R.id.minimise).performClick();

        assertFalse(screen.isFinishing());
        assertSame(screen, InCallActivity.current);
    }
}
