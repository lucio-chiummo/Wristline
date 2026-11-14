package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.ConnectionRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * What the diagnostic report says about a ringing call: whether Telecom made Wristline's call,
 * and which button on the watch was pressed. Field reports on other companion apps (OPPO's
 * OHealth) couldn't tell "never rang" from "rang, but the tap never came back".
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class CallDiagnosticsTest {
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @After
    public void tearDown() {
        RelayListener.currentApp = null;
        RelayListener.incomingCall = null;
        RelayConnection.current = null;
    }

    private EventLog.Entry newest() {
        List<EventLog.Entry> events = Prefs.events(context);
        assertTrue("no event recorded", !events.isEmpty());
        return events.get(0);
    }

    /** Most recent event with this code (a test call logs its result just after). */
    private EventLog.Entry latest(String code) {
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new AssertionError("no " + code + " recorded");
    }

    private ConnectionRequest request(boolean testCall) {
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(TestCall.EXTRA, testCall);
        return new ConnectionRequest(null, null, extras);
    }

    @Test
    public void recordsTheMirroredCallBeingCreated() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        RelayConnectionService service = Robolectric.buildService(RelayConnectionService.class).create().get();

        service.onCreateIncomingConnection(null, request(false));

        EventLog.Entry e = newest();
        assertEquals(EventLog.CALL_CREATED, e.code);
        assertEquals("com.whatsapp", e.app);
    }

    @Test
    public void recordsATestCallBeingCreated() {
        RelayConnectionService service = Robolectric.buildService(RelayConnectionService.class).create().get();

        service.onCreateIncomingConnection(null, request(true));

        EventLog.Entry e = newest();
        assertEquals(EventLog.CALL_CREATED, e.code);
        assertEquals("test call", e.detail);
        RelayConnection.current.onAbort();
    }

    @Test
    public void recordsTelecomFailingToCreateTheCall() {
        RelayListener.currentApp = SupportedApp.TELEGRAM;
        RelayConnectionService service = Robolectric.buildService(RelayConnectionService.class).create().get();

        service.onCreateIncomingConnectionFailed(null, request(false));

        EventLog.Entry e = newest();
        assertEquals(EventLog.CALL_NOT_CREATED, e.code);
        assertEquals("org.telegram.messenger", e.app);
    }

    @Test
    public void testCallAnswerIsRecordedApartFromDecline() {
        RelayConnection connection = new RelayConnection(context);
        connection.setRinging();
        connection.markTestCall();
        RelayConnection.current = connection;

        connection.onAnswer();

        EventLog.Entry e = latest(EventLog.ANSWERED_ON_WATCH);
        assertEquals("test call", e.detail);
        connection.onAbort();
    }

    @Test
    public void testCallDeclineIsRecorded() {
        RelayConnection connection = new RelayConnection(context);
        connection.setRinging();
        connection.markTestCall();
        RelayConnection.current = connection;

        connection.onReject();

        EventLog.Entry e = latest(EventLog.DECLINED_ON_WATCH);
        assertEquals("test call", e.detail);
    }

    @Test
    public void answerFromTheFallbackNotificationCountsAsHandled() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        RelayListener.incomingCall = null;

        new ActionReceiver().onReceive(context, new Intent(ActionReceiver.ANSWER));

        EventLog.Entry e = newest();
        assertEquals(EventLog.ANSWERED_ON_WATCH, e.code);
        assertEquals("notification", e.detail);
        assertTrue("a call answered from the notification must not be reported missed",
                RelayListener.isIncomingHandled());
    }

    @Test
    public void declineFromTheFallbackNotificationCountsAsHandled() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;

        new ActionReceiver().onReceive(context, new Intent(ActionReceiver.DECLINE));

        EventLog.Entry e = newest();
        assertEquals(EventLog.DECLINED_ON_WATCH, e.code);
        assertEquals("notification", e.detail);
        assertTrue(RelayListener.isIncomingHandled());
    }

    /** Codes in the log, newest first, with only this code. */
    private List<EventLog.Entry> withCode(String code) {
        List<EventLog.Entry> out = new java.util.ArrayList<>();
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code)) {
                out.add(e);
            }
        }
        return out;
    }

    @Test
    public void telecomStateChangesAreRecorded() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        RelayConnection connection = new RelayConnection(context);

        connection.setRinging();
        connection.setActive();

        List<EventLog.Entry> states = withCode(EventLog.CALL_STATE);
        assertEquals(2, states.size());
        assertEquals("active", states.get(0).detail);
        assertEquals("ringing", states.get(1).detail);
        assertEquals("com.whatsapp", states.get(0).app);
        connection.onAbort();
    }
}
