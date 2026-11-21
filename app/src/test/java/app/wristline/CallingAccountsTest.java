package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The carrier card's button opens the screen that can enable the carrier, skin by skin. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class CallingAccountsTest {
    private static final ComponentName HYPEROS = new ComponentName("com.android.server.telecomui",
            "com.android.server.telecomui.settings.EnableAccountPreferenceActivity");
    private static final ComponentName VIVO = new ComponentName("com.android.server.telecom",
            "com.android.server.telecomui.settings.EnableAccountPreferenceActivity");

    /** A skin whose first screen tried is there but guarded (not exported, or a permission). */
    public static class GuardedFirstScreen extends MainActivity {
        final List<Intent> tried = new ArrayList<>();
        boolean opening;

        @Override
        public void startActivity(Intent intent) {
            if (opening) {
                tried.add(intent);
                if (tried.size() == 1) {
                    throw new SecurityException("Permission Denial");
                }
            }
            super.startActivity(intent);
        }
    }

    @Test
    public void hyperOsTelecomUiScreenIsTriedFirst() {
        MainActivity home = Robolectric.buildActivity(MainActivity.class).setup().get();
        while (shadowOf(home).getNextStartedActivity() != null) {
            // Drop whatever the home screen opened by itself.
        }

        home.openCallingAccounts();

        assertEquals(HYPEROS, shadowOf(home).getNextStartedActivity().getComponent());
    }

    @Test
    public void aGuardedScreenFallsThroughToTheNextWayIn() {
        GuardedFirstScreen home = Robolectric.buildActivity(GuardedFirstScreen.class).setup().get();
        home.opening = true;

        home.openCallingAccounts();

        assertEquals(HYPEROS, home.tried.get(0).getComponent());
        assertEquals(VIVO, home.tried.get(1).getComponent());
        assertEquals(2, home.tried.size());
    }
}
