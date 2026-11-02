package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** The call indicator lives exactly as long as the call screen. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class CallIndicatorTest {
    @Test
    public void shownWithTheCallScreenAndGoneAfter() {
        Application context = RuntimeEnvironment.getApplication();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        ActivityController<InCallActivity> screen = Robolectric.buildActivity(InCallActivity.class,
                InCallActivity.intent(context, "Corvan", Protocol.WHATSAPP)).setup();

        assertNotNull(shadowOf(manager).getNotification(1));
        assertEquals("Corvan", shadowOf(manager).getNotification(1).extras.getString("android.title"));

        screen.destroy();
        assertEquals(0, shadowOf(manager).size());
    }
}
