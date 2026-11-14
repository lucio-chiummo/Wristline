package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowTelecomManager;

import java.util.concurrent.TimeUnit;

/** "Ring my watch": starting it, and how its stand-in call behaves on the watch's buttons. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class TestCallFlowTest {
    private Context context;
    private ShadowTelecomManager telecom;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        telecom = shadowOf(context.getSystemService(TelecomManager.class));
    }

    @After
    public void tearDown() {
        RelayListener.currentApp = null;
        RelayConnection.current = null;
        Prefs.setRingByNotification(context, false);
        TestCall.endNotificationTest(context);
    }

    private RelayConnection ringingTestCall() {
        RelayConnection connection = new RelayConnection(context);
        connection.setRinging();
        connection.markTestCall();
        RelayConnection.current = connection;
        return connection;
    }

    @Test
    public void startsATelecomCallMarkedAsATest() {
        assertTrue(RelayListener.startTestCall(context));

        ShadowTelecomManager.CallRecord call = telecom.getOnlyIncomingCall();
        assertTrue(call.extras.getBoolean(TestCall.EXTRA));
        assertEquals(context.getString(R.string.test_call_caller), call.extras.getString(RelayListener.EXTRA_CALLER));
    }

    @Test
    public void refusedWhileARealCallIsRelayed() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;

        assertFalse(RelayListener.startTestCall(context));
        assertEquals(0, telecom.getAllIncomingCalls().size());
    }

    @Test
    public void declineEndsIt() {
        RelayConnection connection = ringingTestCall();

        connection.onReject();

        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertEquals(DisconnectCause.REJECTED, connection.getDisconnectCause().getCode());
        assertNull(RelayConnection.current);
        // Declining on the watch proves the call reached it.
        assertEquals(TestCall.Result.REACHED, Prefs.testCallResult(context));
    }

    @Test
    public void answerGoesActiveThenHangsUpByItself() {
        RelayConnection connection = ringingTestCall();

        connection.onAnswer();
        assertEquals(Connection.STATE_ACTIVE, connection.getState());
        assertEquals(TestCall.Result.REACHED, Prefs.testCallResult(context));

        ShadowLooper.idleMainLooper(TestCall.ANSWERED_MS, TimeUnit.MILLISECONDS);
        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertEquals(DisconnectCause.LOCAL, connection.getDisconnectCause().getCode());
    }

    @Test
    public void unansweredStopsRinging() {
        RelayConnection connection = ringingTestCall();

        ShadowLooper.idleMainLooper(TestCall.RING_MS, TimeUnit.MILLISECONDS);

        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertEquals(DisconnectCause.MISSED, connection.getDisconnectCause().getCode());
        assertEquals(TestCall.Result.MISSED, Prefs.testCallResult(context));
    }

    @Test
    public void homeIsToldWhenTheResultIsKnown() {
        int[] told = {0};
        TestCall.onResult = () -> told[0]++;
        try {
            ringingTestCall().onReject();
            ShadowLooper.idleMainLooper();
        } finally {
            TestCall.onResult = null;
        }
        assertEquals(1, told[0]);
    }

    @Test
    public void aRealCallReplacesARingingTest() {
        RelayConnection connection = ringingTestCall();

        RelayConnection.endTestCall();

        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertNull(RelayConnection.current);
    }

    private EventLog.Entry latest(String code) {
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new AssertionError("no " + code + " recorded");
    }

    private void tap(String action) {
        new ActionReceiver().onReceive(context, new android.content.Intent(action));
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void notificationTestRingsWithoutTelecom() {
        Prefs.setRingByNotification(context, true);

        assertTrue(RelayListener.startTestCall(context));

        assertEquals(0, telecom.getAllIncomingCalls().size());
        assertTrue(WatchRing.ringing());
        assertTrue(TestCall.notificationRinging);
        assertEquals("started notification", latest(EventLog.TEST_CALL).detail);
        android.app.Notification n = shadowOf(context.getSystemService(android.app.NotificationManager.class))
                .getNotification(StatusNotifications.ID_INCOMING_FALLBACK);
        assertEquals(context.getString(R.string.test_call_caller),
                n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString());
    }

    @Test
    public void answeringTheNotificationTestReachesTheWatch() {
        Prefs.setRingByNotification(context, true);
        RelayListener.startTestCall(context);

        tap(ActionReceiver.ANSWER);

        assertEquals(TestCall.Result.REACHED, Prefs.testCallResult(context));
        assertEquals("test call", latest(EventLog.ANSWERED_ON_WATCH).detail);
        assertEquals("reached", latest(EventLog.TEST_CALL).detail);
        assertFalse(WatchRing.ringing());
        assertFalse(TestCall.notificationRinging);
    }

    @Test
    public void decliningTheNotificationTestReachesTheWatch() {
        Prefs.setRingByNotification(context, true);
        RelayListener.startTestCall(context);

        tap(ActionReceiver.DECLINE);

        assertEquals(TestCall.Result.REACHED, Prefs.testCallResult(context));
        assertEquals("test call", latest(EventLog.DECLINED_ON_WATCH).detail);
        assertFalse(WatchRing.ringing());
    }

    @Test
    public void unansweredNotificationTestIsMissed() {
        Prefs.setRingByNotification(context, true);
        RelayListener.startTestCall(context);

        ShadowLooper.idleMainLooper(TestCall.RING_MS + 1, TimeUnit.MILLISECONDS);

        assertEquals(TestCall.Result.MISSED, Prefs.testCallResult(context));
        assertEquals("missed", latest(EventLog.TEST_CALL).detail);
        assertFalse(WatchRing.ringing());
        assertFalse(TestCall.notificationRinging);
    }

    @Test
    public void answeredNotificationTestIsNotMissedLater() {
        Prefs.setRingByNotification(context, true);
        RelayListener.startTestCall(context);
        tap(ActionReceiver.ANSWER);

        ShadowLooper.idleMainLooper(TestCall.RING_MS + 1, TimeUnit.MILLISECONDS);

        assertEquals(TestCall.Result.REACHED, Prefs.testCallResult(context));
    }

    @Test
    public void noSecondTestWhileOneRings() {
        Prefs.setRingByNotification(context, true);
        assertTrue(RelayListener.startTestCall(context));

        assertFalse(RelayListener.startTestCall(context));
    }
}
