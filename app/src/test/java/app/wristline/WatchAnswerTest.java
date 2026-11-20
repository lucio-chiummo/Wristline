package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.service.notification.StatusBarNotification;

import com.google.android.gms.wearable.MessageEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Answer and Decline on Wristline's own incoming screen on the watch, for a call rung by notification. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WatchAnswerTest {
    private Context context;
    private RelayListener listener;
    private WatchCallService service;
    private final List<String> callStates = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void setUp() {
        WatchSync.resetRingQueue();
        context = RuntimeEnvironment.getApplication();
        listener = Robolectric.buildService(RelayListener.class).create().get();
        service = Robolectric.buildService(WatchCallService.class).create().get();
        WatchSync.sender = (c, path, payload) -> {
            if (WatchProtocol.PATH_CALL_STATE.equals(path)) {
                callStates.add(new String(payload, StandardCharsets.UTF_8));
            }
        };
        Prefs.setRingByNotification(context, true);
    }

    @After
    public void tearDown() {
        RelayListener.clearCall(context);
        RelayConnection.current = null;
        RelayListener.useFullModeForCall = false;
        Prefs.setRingByNotification(context, false);
        TestCall.endNotificationTest(context);
        WatchRing.stop(context);
        WatchSync.sender = WatchSync.WATCH_SENDER;
        listener.onListenerDisconnected();
    }

    private PendingIntent intent(int requestCode) {
        return PendingIntent.getBroadcast(context, requestCode, new Intent("test." + requestCode), PendingIntent.FLAG_IMMUTABLE);
    }

    /** WhatsApp's ringing call, on a basic-mode call: Answer sends the app's own Answer. */
    private void ringing() {
        Notification n = new Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Corvan")
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(intent(1), true)
                .addAction(new Notification.Action.Builder(null, "Decline", intent(2)).build())
                .addAction(new Notification.Action.Builder(null, "Answer", intent(3)).build())
                .build();
        listener.onNotificationPosted(post("k1", n));
        RelayListener.useFullModeForCall = false;
    }

    /** The app's in-call notification once the call is connected (it shows a call timer). */
    private StatusBarNotification ongoing() {
        Notification n = new Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Corvan")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - 1000)
                .addAction(new Notification.Action.Builder(null, "Hang up", intent(4)).build())
                .build();
        return post("k1", n);
    }

    private StatusBarNotification post(String key, Notification n) {
        try {
            return StatusBarNotification.class
                    .getConstructor(String.class, String.class, int.class, String.class, int.class, int.class,
                            Notification.class, android.os.UserHandle.class, String.class, long.class)
                    .newInstance("com.whatsapp", "com.whatsapp", key.hashCode(), key, 1000, 1, n,
                            Process.myUserHandle(), null, 0L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void fromWatch(String path) {
        service.onMessageReceived(new MessageEvent() {
            @Override
            public int getRequestId() {
                return 0;
            }

            @Override
            public String getPath() {
                return path;
            }

            @Override
            public byte[] getData() {
                return new byte[0];
            }

            @Override
            public String getSourceNodeId() {
                return "watch";
            }
        });
        ShadowLooper.idleMainLooper();
    }

    private boolean appGot(String action) {
        for (Intent sent : shadowOf((android.app.Application) context).getBroadcastIntents()) {
            if (action.equals(sent.getAction())) {
                return true;
            }
        }
        return false;
    }

    private boolean anyAppActionSent() {
        for (Intent sent : shadowOf((android.app.Application) context).getBroadcastIntents()) {
            if (sent.getAction() != null && sent.getAction().startsWith("test.")) {
                return true;
            }
        }
        return false;
    }

    private String detail(String code) {
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code)) {
                return e.detail;
            }
        }
        return null;
    }

    /** The call states sent so far, waiting a little for one starting with {@code prefix} (they go off the main thread). */
    private String callState(String prefix) throws InterruptedException {
        for (int i = 0; i < 80; i++) {
            synchronized (callStates) {
                for (String state : callStates) {
                    if (state.startsWith(prefix)) {
                        return state;
                    }
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("no " + prefix + " call state in " + callStates);
    }

    @Test
    public void anAnswerFromTheWatchRunsTheNotificationAnswer() {
        ringing();

        fromWatch(WatchProtocol.PATH_ANSWER);

        assertFalse(WatchRing.ringing());
        assertTrue(RelayListener.isIncomingHandled());
        assertEquals("notification", detail(EventLog.ANSWERED_ON_WATCH));
        assertTrue("the app's Answer wasn't sent", appGot("test.3"));
        assertFalse(appGot("test.2"));
    }

    @Test
    public void aDeclineFromTheWatchRunsTheNotificationDecline() {
        ringing();

        fromWatch(WatchProtocol.PATH_DECLINE);

        assertFalse(WatchRing.ringing());
        assertTrue(RelayListener.isIncomingHandled());
        assertEquals("notification", detail(EventLog.DECLINED_ON_WATCH));
        assertTrue("the app's Decline wasn't sent", appGot("test.2"));
        assertFalse(appGot("test.3"));
    }

    @Test
    public void anAnswerFromTheWatchWhenNothingRingsDoesNothing() {
        fromWatch(WatchProtocol.PATH_ANSWER);
        fromWatch(WatchProtocol.PATH_DECLINE);

        assertFalse(anyAppActionSent());
        assertEquals(null, detail(EventLog.ANSWERED_ON_WATCH));
    }

    @Test
    public void anAnsweredCallKeepsTheWatchCallScreenInformed() throws InterruptedException {
        ringing();
        fromWatch(WatchProtocol.PATH_ANSWER);

        listener.onNotificationPosted(ongoing());
        String active = callState(WatchProtocol.STATE_ACTIVE + ";");
        assertTrue(active, active.endsWith("|com.whatsapp|Corvan"));

        listener.onNotificationRemoved(ongoing());
        ShadowLooper.idleMainLooper(CallTracker.GRACE_MS + 1, TimeUnit.MILLISECONDS);
        assertEquals("ended;;|com.whatsapp|Corvan", callState(WatchProtocol.STATE_ENDED + ";"));
    }
}
