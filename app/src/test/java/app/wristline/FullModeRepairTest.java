package app.wristline;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Robolectric has no Wireless debugging service, so the built-in adb client is never reachable here;
 * discovery gives up after its 4 s timeout, hence the long wait.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class FullModeRepairTest {
    private static final SupportedApp WHATSAPP = SupportedApp.WHATSAPP;

    private Application context;
    private NotificationManager nm;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
        nm = context.getSystemService(NotificationManager.class);
    }

    private void awaitRepairNotification() throws InterruptedException {
        long end = System.currentTimeMillis() + 10000;
        while (shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR) == null
                && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
    }

    @Test
    public void repairWithoutConsentPostsNotification() {
        Prefs.setConsent(context, WHATSAPP, false);

        FullMode.onCallDropped(context, WHATSAPP);

        assertNotNull(shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR));
        assertTrue(Prefs.broken(context, WHATSAPP));
    }

    @Test
    public void repairWithConsentButUnreachableFallsBackToNotification() throws Exception {
        Prefs.setConsent(context, WHATSAPP, true);

        FullMode.onCallDropped(context, WHATSAPP);
        awaitRepairNotification();

        assertNotNull(shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR));
        assertTrue(Prefs.broken(context, WHATSAPP));
    }
}
