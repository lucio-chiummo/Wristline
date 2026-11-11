package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Looper;

import com.google.android.gms.wearable.MessageEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Any message from the watch asks for a listener rebind when the listener is unbound. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WatchRebindTest {
    private Context context;
    private RelayListener listener;
    private WatchCallService service;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        listener = Robolectric.buildService(RelayListener.class).create().get();
        service = Robolectric.buildService(WatchCallService.class).get();
        ListenerRebind.onChange = null;
        shadowOf(context.getSystemService(NotificationManager.class))
                .setNotificationListenerAccessGranted(new ComponentName(context, RelayListener.class), true);
    }

    @After
    public void tearDown() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ListenerRebind.CHECK_AFTER_MS));
        listener.onListenerDisconnected();
        ListenerRebind.onChange = null;
    }

    private static MessageEvent message(String path, String data) {
        return new MessageEvent() {
            @Override public int getRequestId() { return 0; }
            @Override public String getPath() { return path; }
            @Override public byte[] getData() { return data.getBytes(StandardCharsets.UTF_8); }
            @Override public String getSourceNodeId() { return "watch"; }
        };
    }

    private List<EventLog.Entry> rebinds() {
        List<EventLog.Entry> out = new ArrayList<>();
        for (EventLog.Entry e : Prefs.events(context)) {
            if (EventLog.LISTENER_REBIND.equals(e.code)) {
                out.add(e);
            }
        }
        return out;
    }

    @Test
    public void watchTileChipRebinds() {
        service.onMessageReceived(message(WatchProtocol.PATH_SET_RELAY, "0"));

        assertFalse(Prefs.relayOn(context));
        assertEquals(1, rebinds().size());
        assertEquals(ListenerRebind.WATCH, rebinds().get(0).detail);
    }

    @Test
    public void watchMessageWithListenerUpDoesNothingExtra() {
        listener.onListenerConnected();

        service.onMessageReceived(message(WatchProtocol.PATH_SET_RELAY, "0"));

        assertFalse(Prefs.relayOn(context));
        assertTrue(rebinds().isEmpty());
    }
}
