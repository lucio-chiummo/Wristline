package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;
import org.robolectric.shadows.ShadowSettings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ListenerStatusCardTest {
    private Context context;
    private RelayListener listener;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        listener = Robolectric.buildService(RelayListener.class).create().get();
        ListenerRebind.onChange = null;
        grantAccess(true);
        shadowOf(context.getSystemService(PowerManager.class))
                .setIgnoringBatteryOptimizations("app.wristline", true);
        shadowOf((android.app.Application) context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ShadowSettings.setCanDrawOverlays(true);
        Prefs.addToCallHistory(context, new CallHistory.Entry(1, false, "app", -1, "x"));
    }

    @After
    public void tearDown() {
        idle();
        listener.onListenerDisconnected();
        ListenerRebind.onChange = null;
    }

    private void grantAccess(boolean granted) {
        shadowOf(context.getSystemService(NotificationManager.class))
                .setNotificationListenerAccessGranted(new ComponentName(context, RelayListener.class), granted);
    }

    private void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ListenerRebind.CHECK_AFTER_MS));
    }

    private String text(MainActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    private MainActivity home() {
        return Robolectric.buildActivity(MainActivity.class).setup().get();
    }

    private List<EventLog.Entry> toggles() {
        List<EventLog.Entry> out = new ArrayList<>();
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(EventLog.LISTENER_TOGGLED)) {
                out.add(e);
            }
        }
        return out;
    }

    @Test
    public void unboundShowsNotReceiving() {
        Prefs.setListenerUnboundSince(context, 1);
        MainActivity a = home();
        assertEquals(context.getString(R.string.status_unbound_title), text(a, R.id.status_title));
        assertEquals("Fix", ((Button) a.findViewById(R.id.attention_button)).getText().toString());
    }

    @Test
    public void xiaomiGetsItsHint() {
        ShadowBuild.setManufacturer("Xiaomi");
        Prefs.setListenerUnboundSince(context, 1);
        MainActivity a = home();
        assertTrue(text(a, R.id.attention_desc).contains(context.getString(R.string.battery_hint_xiaomi)));
    }

    @Test
    public void fixTogglesNow() {
        Prefs.setListenerUnboundSince(context, 1);
        MainActivity a = home();
        a.findViewById(R.id.attention_button).performClick();
        List<EventLog.Entry> t = toggles();
        assertFalse(t.isEmpty());
        assertEquals(ListenerRebind.FIX, t.get(0).detail);
    }

    @Test
    public void stillUnboundAfterFixOffersSettings() {
        Prefs.setListenerUnboundSince(context, 1);
        MainActivity a = home();
        a.findViewById(R.id.attention_button).performClick();
        idle();
        assertEquals("Open Wristline settings", ((Button) a.findViewById(R.id.attention_button)).getText().toString());
        a.findViewById(R.id.attention_button).performClick();
        Intent started = shadowOf(a).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.getAction());
        assertEquals("package:app.wristline", started.getDataString());
    }

    @Test
    public void reconnectTurnsTheCardReady() {
        Prefs.setListenerUnboundSince(context, 1);
        MainActivity a = home();
        idle(); // the HOME check has finished and the card is still unbound
        assertEquals(context.getString(R.string.status_unbound_title), text(a, R.id.status_title));
        listener.onListenerConnected();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(context.getString(R.string.status_ready_title), text(a, R.id.status_title));
    }

    @Test
    public void fixOnAConnectedListenerDoesNotToggle() {
        Prefs.setListenerUnboundSince(context, 1);
        MainActivity a = home();
        listener.onListenerConnected();
        int before = toggles().size();
        a.findViewById(R.id.attention_button).performClick();
        assertEquals(before, toggles().size());
    }

    @Test
    public void bindsInTimeStaysReady() {
        Prefs.setListenerUnboundSince(context, 0);
        MainActivity a = home();
        assertEquals(context.getString(R.string.status_ready_title), text(a, R.id.status_title));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        listener.onListenerConnected();
        idle();
        assertEquals(context.getString(R.string.status_ready_title), text(a, R.id.status_title));
        assertTrue(toggles().isEmpty());
    }

    @Test
    public void missingAccessIsAStepNotUnbound() {
        grantAccess(false);
        Prefs.setListenerUnboundSince(context, 1);
        MainActivity a = home();
        assertFalse(context.getString(R.string.status_unbound_title).equals(text(a, R.id.status_title)));
        assertEquals(context.getResources().getQuantityString(R.plurals.status_steps_title, 1, 1),
                text(a, R.id.status_title));
    }
}
