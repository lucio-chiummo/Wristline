package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

/** People who feel lost tap Help: it must open the step-by-step guide, which must exist. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class HelpButtonTest {
    private static final String REPO_PAGE = "https://github.com/lucio-chiummo/Wristline/blob/main/";

    @Test
    public void helpOpensTheGuideInTheRepository() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();

        activity.findViewById(R.id.help).performClick();

        Intent opened = shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(Intent.ACTION_VIEW, opened.getAction());
        String url = opened.getDataString();
        assertTrue(url, url.startsWith(REPO_PAGE));
        String path = url.substring(REPO_PAGE.length());
        assertTrue("guide missing: " + path, new File("../" + path).isFile());
    }
}
