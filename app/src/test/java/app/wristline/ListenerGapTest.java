package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The event log says how long the listener was unbound when it reconnects. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class ListenerGapTest {
    private Context context;
    private RelayListener listener;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        listener = Robolectric.buildService(RelayListener.class).create().get();
    }

    @After
    public void tearDown() {
        listener.onListenerDisconnected();
    }

    @Test
    public void reconnectRecordsHowLongItWasUnbound() {
        Prefs.setListenerUnboundSince(context, System.currentTimeMillis() - (2 * 60 + 14) * 60_000L);

        listener.onListenerConnected();

        EventLog.Entry latest = Prefs.events(context).get(0);
        assertEquals(EventLog.LISTENER_CONNECTED, latest.code);
        assertEquals("after 2h 14m unbound", latest.detail);
        assertEquals(0, Prefs.listenerUnboundSince(context));
    }

    @Test
    public void normalConnectHasNoGap() {
        Prefs.setListenerUnboundSince(context, 0);

        listener.onListenerConnected();

        EventLog.Entry latest = Prefs.events(context).get(0);
        assertEquals(EventLog.LISTENER_CONNECTED, latest.code);
        assertEquals("", latest.detail);
    }

    @Test
    public void disconnectStartsTheGap() {
        listener.onListenerConnected();
        listener.onListenerDisconnected();
        long first = Prefs.listenerUnboundSince(context);
        assertTrue(first > 0);

        Prefs.setListenerUnboundSince(context, first - 5_000);
        listener.onListenerDisconnected();

        assertEquals(first - 5_000, Prefs.listenerUnboundSince(context));
    }
}
