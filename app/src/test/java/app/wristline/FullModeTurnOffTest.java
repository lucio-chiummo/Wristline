package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.NotificationManager;
import android.content.pm.PackageInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class FullModeTurnOffTest {
    private static final SupportedApp WHATSAPP = SupportedApp.WHATSAPP;
    private static final SupportedApp TELEGRAM = SupportedApp.TELEGRAM;

    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
        install(WHATSAPP);
        install(TELEGRAM);
    }

    private void install(SupportedApp app) {
        PackageInfo info = new PackageInfo();
        info.packageName = app.packageName;
        info.firstInstallTime = 1000;
        info.requestedPermissions = new String[] {Manifest.permission.READ_PHONE_STATE};
        info.requestedPermissionsFlags = new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
        shadowOf(context.getPackageManager()).installPackage(info);
    }

    private NotificationManager notifications() {
        return context.getSystemService(NotificationManager.class);
    }

    @Test
    public void appsWithVoiceOnIncludeOnesWhoseRelaySwitchIsOff() {
        FullMode.markSetUp(context, TELEGRAM);
        Prefs.setAppOn(context, TELEGRAM, false);

        assertEquals(List.of(TELEGRAM), FullMode.appsWithVoiceOn(context));
    }

    @Test
    public void turningOffOnlyTouchesTheAppsGiven() {
        FullMode.markSetUp(context, WHATSAPP);
        FullMode.markSetUp(context, TELEGRAM);

        FullMode.markTurnedOff(context, List.of(WHATSAPP));

        assertFalse(Prefs.fullModeOn(context, WHATSAPP));
        assertTrue(Prefs.fullModeOn(context, TELEGRAM));
    }

    @Test
    public void droppedCallAsksForARepair() {
        FullMode.markSetUp(context, WHATSAPP);

        FullMode.onCallDropped(context, WHATSAPP);

        assertEquals(VoiceState.NEEDS_REPAIR, FullMode.state(context, WHATSAPP));
        assertNotNull(shadowOf(notifications()).getNotification(StatusNotifications.ID_REPAIR));
    }

    @Test
    public void turningOffClearsTheRepairNotice() {
        FullMode.markSetUp(context, WHATSAPP);
        FullMode.onCallDropped(context, WHATSAPP);

        FullMode.markTurnedOff(context, List.of(WHATSAPP));

        assertNull(shadowOf(notifications()).getNotification(StatusNotifications.ID_REPAIR));
    }
}
