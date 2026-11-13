package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Pairing can't be finished without the pop-up the code is typed into. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class CodePopUpTest {
    private Application context;
    private NotificationManager manager;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = context.getSystemService(NotificationManager.class);
    }

    @Test
    public void allowedWhenNotificationsAreOn() {
        StatusNotifications.createChannels(context);
        assertTrue(Permissions.canShowCodePopUp(context));
    }

    @Test
    public void blockedWhenTheAppsNotificationsAreOff() {
        shadowOf(manager).setNotificationsEnabled(false);
        assertFalse(Permissions.canShowCodePopUp(context));
    }

    @Test
    public void blockedWhenOnlyThePairingChannelIsOff() {
        manager.createNotificationChannel(new NotificationChannel(StatusNotifications.CHANNEL_SETUP,
                "Setup", NotificationManager.IMPORTANCE_NONE));
        assertFalse(Permissions.canShowCodePopUp(context));
    }
}
