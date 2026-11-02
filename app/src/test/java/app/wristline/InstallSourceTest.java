package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Android 13+ greys out notification access for apps installed from a file ("restricted settings"). */
public class InstallSourceTest {
    @Test
    public void sideloadedOnAndroid13PlusGetsTheHint() {
        assertTrue(InstallSource.restrictedSettingsHint(null, 33));
        assertTrue(InstallSource.restrictedSettingsHint("com.google.android.packageinstaller", 36));
    }

    @Test
    public void playStoreInstallsDont() {
        assertFalse(InstallSource.restrictedSettingsHint("com.android.vending", 36));
    }

    @Test
    public void olderAndroidDoesnt() {
        assertFalse(InstallSource.restrictedSettingsHint(null, 32));
    }
}
