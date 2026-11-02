package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Pausing and resuming from Home, the Quick Settings tile or the watch: one switch. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class RelayingTest {
    private final Application context = RuntimeEnvironment.getApplication();

    @After
    public void tearDown() {
        Relaying.onChange = null;
    }

    @Test
    public void pauseIsSavedRecordedAndShownOnHome() {
        int[] homeRedrawn = {0};
        Relaying.onChange = () -> homeRedrawn[0]++;

        Relaying.set(context, false);
        ShadowLooper.idleMainLooper();

        assertFalse(Prefs.relayOn(context));
        assertEquals(EventLog.RELAY_PAUSED, Prefs.events(context).get(0).code);
        assertEquals(1, homeRedrawn[0]);
    }

    @Test
    public void settingTheSameStateDoesNothing() {
        Relaying.set(context, true);

        assertTrue(Prefs.relayOn(context));
        assertTrue(Prefs.events(context).isEmpty());
    }

    @Test
    public void quickSettingsTileToggles() {
        RelayTileService tile = Robolectric.buildService(RelayTileService.class).create().get();

        tile.onClick();
        assertFalse(Prefs.relayOn(context));

        tile.onClick();
        assertTrue(Prefs.relayOn(context));
        assertEquals(EventLog.RELAY_RESUMED, Prefs.events(context).get(0).code);
    }
}
