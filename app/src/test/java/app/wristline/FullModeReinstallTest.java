package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

/** A reinstalled WhatsApp loses its setting: caught before a call, back to basic mode. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class FullModeReinstallTest {
    private Application context;
    private PackageInfo whatsapp;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
        whatsapp = new PackageInfo();
        whatsapp.packageName = SupportedApp.WHATSAPP.packageName;
        whatsapp.firstInstallTime = 1000;
        whatsapp.requestedPermissions = new String[] {Manifest.permission.READ_PHONE_STATE};
        whatsapp.requestedPermissionsFlags = new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
        install();
        // Set up with a computer (no consent for automatic repair over ADB).
        FullMode.markSetUp(context, SupportedApp.WHATSAPP);
    }

    private void install() {
        shadowOf(context.getPackageManager()).installPackage(whatsapp);
    }

    private NotificationManager notifications() {
        return context.getSystemService(NotificationManager.class);
    }

    @Test
    public void sameInstallStaysOn() {
        assertEquals(VoiceState.ON, FullMode.state(context, SupportedApp.WHATSAPP));
        assertNull(shadowOf(notifications()).getNotification(StatusNotifications.ID_REPAIR));
    }

    @Test
    public void reinstallNeedsRepairBeforeTheNextCall() {
        whatsapp.firstInstallTime = 2000;
        install();

        assertEquals(VoiceState.NEEDS_REPAIR, FullMode.state(context, SupportedApp.WHATSAPP));
        assertEquals(false, FullMode.shouldUse(context, SupportedApp.WHATSAPP));
        assertNotNull(shadowOf(notifications()).getNotification(StatusNotifications.ID_REPAIR));
        assertEquals(EventLog.REINSTALLED, Prefs.events(context).get(0).code);
    }

    @Test
    public void setUpBeforeThisCheckRemembersTheCurrentInstall() {
        Prefs.setInstallTime(context, SupportedApp.WHATSAPP, 0);

        assertEquals(VoiceState.ON, FullMode.state(context, SupportedApp.WHATSAPP));
        assertEquals(1000, Prefs.installTime(context, SupportedApp.WHATSAPP));
    }
}
