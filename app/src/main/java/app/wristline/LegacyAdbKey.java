package app.wristline;

import android.content.Context;

import java.io.File;

/**
 * Older versions paired with Wireless debugging from the phone and could leave an ADB key in
 * app-private storage. Wristline has no use for one any more, so an update must not keep it.
 */
final class LegacyAdbKey {
    private static final String[] FILES = {"adbkey.pem", "adbkey.pem.tmp"};

    private LegacyAdbKey() {
    }

    static void delete(Context context) {
        File dir = new File(context.getFilesDir(), "adb");
        for (String name : FILES) {
            new File(dir, name).delete();
        }
        dir.delete();
    }
}
