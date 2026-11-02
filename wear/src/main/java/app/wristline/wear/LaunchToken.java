package app.wristline.wear;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Tiles can only launch exported activities, so any app on the watch can start MainActivity and
 * ToggleRelayActivity too. A secret per install, known only to Wristline's own tile and
 * complication, tells their taps from those: without it, the activities don't call or pause anything.
 */
final class LaunchToken {
    static final String EXTRA = "launch_token";
    private static final String FILE = "watch";
    private static final String KEY = "launch_token";
    private static final int BYTES = 16;

    private LaunchToken() {
    }

    static synchronized String get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY, null);
        if (token == null) {
            byte[] random = new byte[BYTES];
            new SecureRandom().nextBytes(random);
            token = Base64.encodeToString(random, Base64.NO_WRAP);
            prefs.edit().putString(KEY, token).apply();
        }
        return token;
    }

    static boolean matches(Context context, Intent intent) {
        String given = intent.getStringExtra(EXTRA);
        return given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8),
                get(context).getBytes(StandardCharsets.UTF_8));
    }
}
