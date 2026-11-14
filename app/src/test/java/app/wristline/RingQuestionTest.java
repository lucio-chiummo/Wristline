package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

/** After a test call nobody answered, Home asks whether the watch rang and offers ringing by notification. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class RingQuestionTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        ShadowAlertDialog.reset();
    }

    @After
    public void tearDown() {
        Prefs.setRingByNotification(context, false);
        TestCall.endNotificationTest(context);
        WatchRing.stop(context);
        RelayConnection.current = null;
    }

    private MainActivity home() {
        return Robolectric.buildActivity(MainActivity.class).setup().get();
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void missedTestAsksWhetherTheWatchRang() {
        MainActivity activity = home();

        TestCall.report(context, TestCall.Result.MISSED);
        idle();

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("no question asked", dialog);
        assertEquals("Did the watch ring?", shadowOf(dialog).getTitle().toString());
        assertTrue(shadowOf(dialog).getMessage().toString().contains("ring your watch with a notification"));
        assertNotNull(activity);
    }

    @Test
    public void switchingTurnsTheSettingOnAndRingsAgain() {
        home();
        TestCall.report(context, TestCall.Result.MISSED);
        idle();

        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();

        assertTrue(Prefs.ringByNotification(context));
        assertTrue("no new test call", TestCall.notificationRinging);
    }

    @Test
    public void ringAgainKeepsTheSetting() {
        home();
        TestCall.report(context, TestCall.Result.MISSED);
        idle();

        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
        idle();

        assertFalse(Prefs.ringByNotification(context));
        assertFalse(TestCall.notificationRinging);
    }

    @Test
    public void noQuestionWhenAlreadyRingingByNotification() {
        Prefs.setRingByNotification(context, true);
        home();

        TestCall.report(context, TestCall.Result.MISSED);
        idle();

        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test
    public void noQuestionWhenTheTestReachedTheWatch() {
        home();

        TestCall.report(context, TestCall.Result.REACHED);
        idle();

        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }
}
