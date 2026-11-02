package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** An older Wristline could leave an ADB key behind: updating must not keep it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class LegacyAdbKeyTest {
    private Application context;
    private File key;
    private File temp;

    @Before
    public void setUp() throws IOException {
        context = RuntimeEnvironment.getApplication();
        File dir = new File(context.getFilesDir(), "adb");
        dir.mkdirs();
        key = new File(dir, "adbkey.pem");
        temp = new File(dir, "adbkey.pem.tmp");
        Files.write(key.toPath(), new byte[] {1, 2, 3});
        Files.write(temp.toPath(), new byte[] {1, 2, 3});
    }

    @Test
    public void deleteRemovesTheKeyAndAHalfWrittenOne() {
        LegacyAdbKey.delete(context);

        assertFalse(key.exists());
        assertFalse(temp.exists());
        assertFalse(key.getParentFile().exists());
    }

    @Test
    public void deleteWithNoKeyIsFine() {
        LegacyAdbKey.delete(context);
        LegacyAdbKey.delete(context);

        assertFalse(key.exists());
    }

    @Test
    public void openingTheAppDeletesTheKey() {
        Robolectric.buildActivity(MainActivity.class).setup();

        assertFalse(key.exists());
    }

    /** The call listener starts by itself after an update, without the app being opened. */
    @Test
    public void theCallListenerStartingDeletesTheKey() {
        Robolectric.buildService(RelayListener.class).create();

        assertFalse(key.exists());
    }

    @Test
    public void theOldSetupChannelGoesToo() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("adb_setup", "old", NotificationManager.IMPORTANCE_HIGH));

        StatusNotifications.createChannels(context);

        assertNull(manager.getNotificationChannel("adb_setup"));
    }
}
