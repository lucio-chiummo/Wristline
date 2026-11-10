package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A rebind request that doesn't connect is followed by toggling the listener component. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ListenerRebindTest {
    private Context context;
    private RelayListener listener;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        listener = Robolectric.buildService(RelayListener.class).create().get();
        ListenerRebind.onChange = null;
    }

    @After
    public void tearDown() {
        wait5s();
        listener.onListenerDisconnected();
        ListenerRebind.onChange = null;
    }

    private void grantAccess() {
        shadowOf(context.getSystemService(NotificationManager.class))
                .setNotificationListenerAccessGranted(new ComponentName(context, RelayListener.class), true);
    }

    private void wait5s() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ListenerRebind.CHECK_AFTER_MS));
    }

    /** Event codes, newest first. */
    private List<String> codes() {
        List<String> codes = new ArrayList<>();
        for (EventLog.Entry e : Prefs.events(context)) {
            codes.add(e.code);
        }
        return codes;
    }

    @Test
    public void unboundListenerIsToggledAfterFiveSeconds() {
        grantAccess();
        context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, RelayListener.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        ListenerRebind.ifNeeded(context, ListenerRebind.HOME, null);
        assertEquals(Arrays.asList(EventLog.LISTENER_REBIND), codes());

        wait5s();

        assertEquals(Arrays.asList(EventLog.LISTENER_TOGGLED, EventLog.LISTENER_REBIND), codes());
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                context.getPackageManager().getComponentEnabledSetting(new ComponentName(context, RelayListener.class)));
        assertTrue(ListenerRebind.unbound(context));
    }

    @Test
    public void bindingInTimeNeedsNoToggle() {
        grantAccess();
        ListenerRebind.ifNeeded(context, ListenerRebind.HOME, null);
        listener.onListenerConnected();

        wait5s();

        assertFalse(codes().contains(EventLog.LISTENER_TOGGLED));
        assertFalse(ListenerRebind.unbound(context));
    }

    @Test
    public void neverTogglesAConnectedListener() {
        grantAccess();
        listener.onListenerConnected();
        int before = codes().size();

        ListenerRebind.ifNeeded(context, ListenerRebind.HOME, null);
        assertEquals(before, codes().size());
        wait5s();

        assertEquals(before, codes().size());
    }

    @Test
    public void noAccessNoRebind() {
        ComponentName component = new ComponentName(context, RelayListener.class);
        int stateBefore = context.getPackageManager().getComponentEnabledSetting(component);
        ListenerRebind.ifNeeded(context, ListenerRebind.HOME, null);
        wait5s();

        assertTrue(codes().isEmpty());
        assertEquals(stateBefore, context.getPackageManager().getComponentEnabledSetting(component));
    }

    @Test
    public void oneCheckAtATime() {
        grantAccess();
        ListenerRebind.ifNeeded(context, ListenerRebind.HOME, null);
        ListenerRebind.ifNeeded(context, ListenerRebind.QS_TILE, null);
        wait5s();

        assertEquals(Arrays.asList(EventLog.LISTENER_TOGGLED, EventLog.LISTENER_REBIND), codes());
    }

    @Test
    public void doneRunsEitherWay() {
        grantAccess();
        AtomicInteger done = new AtomicInteger();
        listener.onListenerConnected();
        ListenerRebind.ifNeeded(context, ListenerRebind.JOB, done::incrementAndGet);
        assertEquals(1, done.get());

        listener.onListenerDisconnected();
        ListenerRebind.ifNeeded(context, ListenerRebind.JOB, done::incrementAndGet);
        assertEquals(1, done.get());
        wait5s();
        assertEquals(2, done.get());
    }

    @Test
    public void callsFromABackgroundThreadStillCheckOnce() throws InterruptedException {
        grantAccess();
        Thread[] threads = new Thread[2];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> ListenerRebind.ifNeeded(context, ListenerRebind.WATCH, null));
        }
        for (Thread t : threads) {
            t.start();
            t.join();
        }

        wait5s();

        assertEquals(Arrays.asList(EventLog.LISTENER_TOGGLED, EventLog.LISTENER_REBIND), codes());
    }

    @Test
    public void homeRedrawsAfterTheCheck() {
        grantAccess();
        AtomicInteger redraws = new AtomicInteger();
        ListenerRebind.onChange = redraws::incrementAndGet;
        ListenerRebind.ifNeeded(context, ListenerRebind.HOME, null);
        wait5s();

        assertEquals(1, redraws.get());
    }
}
