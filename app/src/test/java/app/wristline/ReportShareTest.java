package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

/** Sharing the diagnostic report from Home: it takes a moment (it asks the watch), and says so. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ReportShareTest {
    private void waitForReport(MainActivity activity) throws InterruptedException {
        Thread thread = activity.reportThread;
        assertNotNull("no report being built", thread);
        thread.join(15_000);
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void saysItIsPreparing() throws InterruptedException {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        activity.findViewById(R.id.row_report).performClick();

        assertEquals("Preparing report…", ShadowToast.getTextOfLatestToast());
        waitForReport(activity);
    }

    @Test
    public void twoTapsOpenOneShareSheet() throws InterruptedException {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        activity.findViewById(R.id.row_report).performClick();
        activity.findViewById(R.id.row_report).performClick();
        waitForReport(activity);

        Intent chooser = shadowOf(activity).getNextStartedActivity();
        assertNotNull("share sheet not opened", chooser);
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        assertNull("a second share sheet opened", shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void canShareAgainAfterTheSheetOpened() throws InterruptedException {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        activity.findViewById(R.id.row_report).performClick();
        waitForReport(activity);
        shadowOf(activity).getNextStartedActivity();

        activity.findViewById(R.id.row_report).performClick();
        waitForReport(activity);

        assertNotNull(shadowOf(activity).getNextStartedActivity());
    }
}
