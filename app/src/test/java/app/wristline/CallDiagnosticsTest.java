package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.CallAudioState;
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

    private ConnectionRequest answeredRequest() {
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_ANSWERED, true);
        return new ConnectionRequest(null, null, extras);
    }

    private android.app.PendingIntent appAnswer() {
        return android.app.PendingIntent.getBroadcast(context, 7, new android.content.Intent("test.answer"),
                android.app.PendingIntent.FLAG_IMMUTABLE);
    }

    private CallActions ringingCall() {
        android.app.PendingIntent other = android.app.PendingIntent.getBroadcast(context, 8,
                new android.content.Intent("test.other"), android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification n = new android.app.Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Annaly")
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .setFullScreenIntent(other, true)
                .addAction(new android.app.Notification.Action.Builder(null, "Decline", other).build())
                .addAction(new android.app.Notification.Action.Builder(null, "Answer", appAnswer()).build())
                .build();
        return CallActions.from(n, false);
    }

    private boolean appAnswered() {
        for (android.content.Intent sent : org.robolectric.Shadows.shadowOf((android.app.Application) context).getBroadcastIntents()) {
            if ("test.answer".equals(sent.getAction())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void anAnsweredCallGoesActiveThenAnswersTheApp() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        RelayListener.incomingCall = ringingCall();
        RelayConnectionService service = Robolectric.buildService(RelayConnectionService.class).create().get();

        android.telecom.Connection connection = service.onCreateIncomingConnection(null, answeredRequest());
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertEquals(android.telecom.Connection.STATE_ACTIVE, connection.getState());
        assertTrue(RelayConnection.isAwaitingAppAnswer());
        assertTrue("app answered before the watch's audio link", !appAnswered());

        org.robolectric.shadows.ShadowLooper.idleMainLooper(RelayConnection.ANSWER_DELAY_MS, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertTrue("app never answered", appAnswered());
        assertTrue(!RelayConnection.isAwaitingAppAnswer());
        RelayConnection.current.onAbort();
    }

    @Test
    public void anAnsweredCallTelecomCannotCreateStillAnswersTheApp() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        RelayListener.incomingCall = ringingCall();
        RelayConnectionService service = Robolectric.buildService(RelayConnectionService.class).create().get();

        service.onCreateIncomingConnectionFailed(null, answeredRequest());

        assertTrue("call lost", appAnswered());
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

    @Test
    public void audioStateIsRecordedOncePerChange() {
        RelayConnection connection = new RelayConnection(context);
        CallAudioState earpiece = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);

        connection.onCallAudioStateChanged(earpiece);
        connection.onCallAudioStateChanged(earpiece);
        connection.onCallAudioStateChanged(new CallAudioState(false, CallAudioState.ROUTE_SPEAKER,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER));

        List<EventLog.Entry> routes = withCode(EventLog.AUDIO_ROUTE);
        assertEquals(2, routes.size());
        assertEquals("route=speaker supported=earpiece,speaker", routes.get(0).detail);
        assertEquals("route=earpiece supported=earpiece,speaker", routes.get(1).detail);
    }

    @Test
    public void missingBluetoothRouteWhileActiveIsRecordedOnce() {
        RelayConnection connection = new RelayConnection(context);
        connection.setActive();
        CallAudioState noBluetooth = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);

        connection.onCallAudioStateChanged(noBluetooth);
        connection.onCallAudioStateChanged(noBluetooth);

        List<EventLog.Entry> missing = withCode(EventLog.AUDIO_NO_WATCH);
        assertEquals(1, missing.size());
        assertEquals("supported=earpiece,speaker", missing.get(0).detail);
        connection.onAbort();
    }

    @Test
    public void bluetoothRouteAvailableIsNotReportedMissing() {
        RelayConnection connection = new RelayConnection(context);
        connection.setActive();

        connection.onCallAudioStateChanged(new CallAudioState(false, CallAudioState.ROUTE_BLUETOOTH,
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH));

        assertEquals(0, withCode(EventLog.AUDIO_NO_WATCH).size());
        connection.onAbort();
    }

    @Test
    public void otherTelecomSignalsAreRecorded() {
        RelayConnection connection = new RelayConnection(context);

        connection.onSilence();
        connection.onHold();
        connection.onUnhold();
        connection.onCallEvent("com.example.ANSWERED_ON_WATCH", new Bundle());

        List<EventLog.Entry> signals = withCode(EventLog.CALL_SIGNAL);
        assertEquals(4, signals.size());
        assertEquals("event com.example.ANSWERED_ON_WATCH", signals.get(0).detail);
        assertEquals("unhold", signals.get(1).detail);
        assertEquals("hold", signals.get(2).detail);
        assertEquals("silence", signals.get(3).detail);
    }

    @Test
    public void holdIsOnlyObserved() {
        RelayConnection connection = new RelayConnection(context);
        connection.setActive();

        connection.onHold();

        assertTrue("observing must not put the call on hold", connection.isActive());
        connection.onAbort();
    }

    @Test
    public void dtmfIsRecordedWithoutItsDigit() {
        RelayConnection connection = new RelayConnection(context);

        connection.onPlayDtmfTone('7');

        assertEquals("dtmf", withCode(EventLog.CALL_SIGNAL).get(0).detail);
    }

    @Test
    public void changedExtrasAreRecordedByKeyOnly() {
        RelayConnection connection = new RelayConnection(context);
        Bundle extras = new Bundle();
        extras.putString("zeta", "+39 333 123 4567");
        extras.putString("alpha", "Corvan");

        connection.onExtrasChanged(extras);
        connection.onExtrasChanged(null);

        assertEquals("extras none", withCode(EventLog.CALL_SIGNAL).get(0).detail);
        assertEquals("extras alpha,zeta", withCode(EventLog.CALL_SIGNAL).get(1).detail);
    }

    @Test
    public void answerWithVideoStateIsRecordedAsItArrives() {
        RelayConnection connection = new RelayConnection(context);
        connection.setRinging();
        RelayConnection.current = connection;

        connection.onAnswer(0);   // no incoming call known: Wristline drops it, the report still shows it came

        assertEquals("answer video=0", withCode(EventLog.CALL_SIGNAL).get(0).detail);
    }

    private List<android.media.AudioRecordingConfiguration> oneVoiceCommunication(android.media.AudioManager am) {
        return List.of(org.robolectric.Shadows.shadowOf(am).createActiveRecordingConfiguration(
                1, android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION, "com.whatsapp"));
    }

    private void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void recordingChangesAreRecordedOncePerChangeWhileActive() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        RelayConnection connection = new RelayConnection(context);
        connection.setActive();
        android.media.AudioManager am = context.getSystemService(android.media.AudioManager.class);

        org.robolectric.Shadows.shadowOf(am).setActiveRecordingConfigurations(oneVoiceCommunication(am), true);
        idle();
        org.robolectric.Shadows.shadowOf(am).setActiveRecordingConfigurations(oneVoiceCommunication(am), true);
        idle();
        org.robolectric.Shadows.shadowOf(am).setActiveRecordingConfigurations(List.of(), true);
        idle();

        List<EventLog.Entry> recordings = withCode(EventLog.RECORDING);
        assertEquals(2, recordings.size());
        assertEquals("none", recordings.get(0).detail);
        assertEquals("source=voice_communication silenced=false", recordings.get(1).detail);
        assertEquals("com.whatsapp", recordings.get(1).app);
        connection.onAbort();
    }

    @Test
    public void recordingsAreNotWatchedBeforeTheCallIsActive() {
        RelayConnection connection = new RelayConnection(context);
        connection.setRinging();
        android.media.AudioManager am = context.getSystemService(android.media.AudioManager.class);

        org.robolectric.Shadows.shadowOf(am).setActiveRecordingConfigurations(oneVoiceCommunication(am), true);
        idle();

        assertEquals(0, withCode(EventLog.RECORDING).size());
        connection.onAbort();
    }

    @Test
    public void recordingsAreNotWatchedAfterTheCallEnds() {
        RelayConnection connection = new RelayConnection(context);
        connection.setActive();
        connection.onAbort();
        android.media.AudioManager am = context.getSystemService(android.media.AudioManager.class);

        org.robolectric.Shadows.shadowOf(am).setActiveRecordingConfigurations(oneVoiceCommunication(am), true);
        idle();

        assertEquals(0, withCode(EventLog.RECORDING).size());
    }

    @Test
    public void testCallsDoNotWatchRecordings() {
        RelayConnection connection = new RelayConnection(context);
        connection.setRinging();
        connection.markTestCall();
        RelayConnection.current = connection;
        connection.onAnswer();
        android.media.AudioManager am = context.getSystemService(android.media.AudioManager.class);

        org.robolectric.Shadows.shadowOf(am).setActiveRecordingConfigurations(oneVoiceCommunication(am), true);
        idle();

        assertEquals(0, withCode(EventLog.RECORDING).size());
        connection.onAbort();
    }

    /** The watch can still show the buttons of a call that is over. */
    @Test
    public void staleAnswerAfterTheCallEndedDoesNothing() {
        RelayListener.currentApp = null;
        RelayListener.incomingCall = null;
        int before = Prefs.events(context).size();

        new ActionReceiver().onReceive(context, new Intent(ActionReceiver.ANSWER));

        assertEquals(before, Prefs.events(context).size());
    }
}
