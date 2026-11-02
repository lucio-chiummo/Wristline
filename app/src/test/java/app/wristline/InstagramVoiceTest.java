package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Instagram asks for READ_PHONE_STATE but doesn't hold it and makes no Telecom
 * call of its own: answered on the watch, its voice can go to the watch with no setting.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class InstagramVoiceTest {
    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        PackageInfo instagram = new PackageInfo();
        instagram.packageName = SupportedApp.INSTAGRAM.packageName;
        instagram.firstInstallTime = 1000;
        instagram.requestedPermissions = new String[] {Manifest.permission.READ_PHONE_STATE};
        instagram.requestedPermissionsFlags = new int[] {0}; // requested, not granted
        shadowOf(context.getPackageManager()).installPackage(instagram);
    }

    @Test
    public void voiceGoesToTheWatchWithoutSetup() {
        assertEquals(VoiceState.ON, FullMode.state(context, SupportedApp.INSTAGRAM));
        assertTrue(FullMode.shouldUse(context, SupportedApp.INSTAGRAM));
    }
}
