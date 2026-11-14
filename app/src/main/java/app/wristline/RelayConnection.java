package app.wristline;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.util.Log;

/** Telecom-side stand-in for an app's call; forwards Answer/Decline/Hang up to that app. */
public class RelayConnection extends Connection {
    static volatile RelayConnection current;
    /** Starts the app's call once the outgoing mirror is set up (calls from the watch). */
    static volatile Runnable pendingAppStart;
    static final long ANSWER_DELAY_MS = 1000;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean awaitingAppAnswer;
    /** "Ring my watch": no app behind this call; answer beeps, then it hangs up by itself. */
    private boolean testCall;
    /** The user moved the voice to the phone mid-call: stop pulling it back to the watch. */
    private volatile boolean voiceOnPhone;
    /** Last audio state written to the report, so repeats aren't logged again. */
    private String lastRouteDetail;
    /** "No Bluetooth route" is logged once per call. */
    private boolean noWatchLogged;
    /** Last recordings summary written to the report, so repeats aren't logged again. */
    private String lastRecordings;
    /**
     * While the call is active: is Android muting a recording (the calling app's microphone)?
     * Reads the kind of input and the muted flag only.
     */
    private android.media.AudioManager.AudioRecordingCallback recordingCallback;

    RelayConnection(Context context) {
        this.context = context.getApplicationContext();
        setConnectionProperties(PROPERTY_SELF_MANAGED);
        setConnectionCapabilities(CAPABILITY_MUTE | CAPABILITY_SUPPORT_HOLD);
        setAudioModeIsVoip(true);
    }

    static void endFromApp() {
        RelayConnection connection = current;
        if (connection != null) {
            connection.end(new DisconnectCause(DisconnectCause.REMOTE));
        }
    }

    /** True between going active and answering the app; a hang-up by the app then means full mode broke. */
    static boolean isAwaitingAppAnswer() {
        RelayConnection connection = current;
        return connection != null && connection.awaitingAppAnswer;
    }

    /** Answered on the phone itself: drop the mirror without taking the call audio from WhatsApp. */
    static void answeredOnPhone() {
        RelayConnection connection = current;
        if (connection != null && connection.getState() == STATE_RINGING) {
            connection.end(new DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE));
        }
    }

    /** Whether the voice of the call in progress is on the watch (null: no call with voice on the watch). */
    static Boolean voiceOnWatch() {
        RelayConnection connection = current;
        if (connection == null || connection.testCall || !connection.isActive()) {
            return null;
        }
        return !connection.voiceOnPhone;
    }

    /**
     * Moves the voice of the call in progress to the phone (earpiece) or back to the watch. The
     * call itself carries on; the watch keeps showing it. Returns false when there's no such call.
     */
    @SuppressWarnings("deprecation") // setAudioRoute: CallEndpoint API needs 34
    static boolean moveVoice(boolean toPhone) {
        RelayConnection connection = current;
        if (connection == null || connection.testCall || !connection.isActive()) {
            return false;
        }
        connection.voiceOnPhone = toPhone;
        Log.i(RelayListener.TAG, toPhone ? "voice moved to the phone" : "voice moved back to the watch");
        Diagnostics.record(connection.context, EventLog.VOICE_MOVED, RelayListener.currentApp, toPhone ? "phone" : "watch");
        if (toPhone) {
            connection.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
        } else {
            connection.routeToWatch(null);
        }
        VoiceControls.update(connection.context);
        return true;
    }

    /** Ends a test call so a real one can take its place. */
    static void endTestCall() {
        RelayConnection connection = current;
        if (connection != null && connection.testCall) {
            connection.end(new DisconnectCause(DisconnectCause.CANCELED));
        }
    }

    /** Ringing a test call: give up if nobody answers. */
    void markTestCall() {
        testCall = true;
        handler.postDelayed(() -> {
            testResult(TestCall.Result.MISSED);
            end(new DisconnectCause(DisconnectCause.MISSED));
        }, TestCall.RING_MS);
    }

    /** Remembers how the test call went and tells Home (posted apart: end() clears our handler). */
    private void testResult(TestCall.Result result) {
        TestCall.report(context, result);
    }

    /** Answered test call: audio to the watch, a few beeps there, then hang up. */
    private void answerTestCall() {
        Log.i(RelayListener.TAG, "test call answered on the watch");
        Diagnostics.record(context, EventLog.ANSWERED_ON_WATCH, "", "test call");
        testResult(TestCall.Result.REACHED);
        handler.removeCallbacksAndMessages(null);
        setActive();
        routeToWatch(null);
        for (long at : TestCall.BEEPS_AT) {
            handler.postDelayed(this::beep, at);
        }
        handler.postDelayed(() -> end(new DisconnectCause(DisconnectCause.LOCAL)), TestCall.ANSWERED_MS);
    }

    private void beep() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, TestCall.BEEP_MS);
            handler.postDelayed(tone::release, TestCall.BEEP_MS + 100);
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not play the test beep", e);
        }
    }

    boolean isActive() {
        return getState() == STATE_ACTIVE;
    }

    /** The watch shows its own call screen for this call (answered, or calling out). */
    boolean isShownOnWatch() {
        return getState() == STATE_ACTIVE || getState() == STATE_DIALING;
    }

    /**
     * A call started from the watch. In full mode it goes active at once so Telecom opens the
     * watch's audio link and the app's call (ringback included) plays there; the app's call-state
     * setting keeps it from hanging up. In basic mode it stays "dialing": the watch shows the call
     * and can hang it up, the voice stays on the phone.
     */
    void startOutgoing(boolean fullMode) {
        setDialing();
        Runnable startApp = pendingAppStart;
        pendingAppStart = null;
        if (!fullMode) {
            if (startApp != null) {
                startApp.run();
            }
            return;
        }
        // Go active first so Telecom opens the watch's audio link, then start the app's call into it.
        handler.postDelayed(() -> {
            setActive();
            routeToWatch(null);
            VoiceControls.update(context);
            if (startApp != null) {
                handler.postDelayed(startApp, ANSWER_DELAY_MS);
            }
            // The app grabs the audio when its call starts; move it back to the watch after that.
            handler.postDelayed(() -> routeToWatch(null), 3 * ANSWER_DELAY_MS);
        }, ANSWER_DELAY_MS);
    }

    /**
     * Already answered on our ringing notification: no Answer to wait for from the watch. Goes
     * active so Telecom opens the watch's audio link, then answers the app into it.
     */
    void startAnswered() {
        awaitingAppAnswer = true;
        // Once Telecom has the connection: it is still being created here.
        handler.post(() -> {
            CallActions call = RelayListener.incomingCall;
            if (call == null) {
                end(new DisconnectCause(DisconnectCause.CANCELED));
                return;
            }
            Log.i(RelayListener.TAG, "answered on the notification (full mode)");
            setActive();
            routeToWatch(null);
            VoiceControls.update(context);
            handler.postDelayed(() -> {
                awaitingAppAnswer = false;
                RelayListener.send(context, call.answer);
            }, ANSWER_DELAY_MS);
        });
    }

    @Override
    public void onAnswer() {
        if (testCall) {
            answerTestCall();
            return;
        }
        CallActions call = RelayListener.incomingCall;
        if (call == null) {
            end(new DisconnectCause(DisconnectCause.CANCELED));
            return;
        }
        RelayListener.incomingHandledOnWatch();
        if (RelayListener.useFullModeForCall) {
            Log.i(RelayListener.TAG, "answered from the watch (full mode)");
            Diagnostics.record(context, EventLog.ANSWERED_ON_WATCH, RelayListener.currentApp, "full mode");
            // Going active makes Telecom open the Bluetooth audio link to the watch; the app's
            // call audio then uses it. Answer the app only once the link is up.
            awaitingAppAnswer = true;
            setActive();
            VoiceControls.update(context);
            handler.postDelayed(() -> {
                awaitingAppAnswer = false;
                RelayListener.send(context, call.answer);
            }, ANSWER_DELAY_MS);
        } else {
            Log.i(RelayListener.TAG, "answered from the watch (basic mode)");
            Diagnostics.record(context, EventLog.ANSWERED_ON_WATCH, RelayListener.currentApp, "basic mode");
            // Hand over to the app without going active, or it would hang up.
            RelayListener.send(context, call.answer);
            end(new DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE));
        }
    }

    @Override
    public void onAnswer(int videoState) {
        signal("answer video=" + videoState);
        onAnswer();
    }

    @Override
    public void onReject() {
        if (testCall) {
            Log.i(RelayListener.TAG, "test call declined on the watch");
            Diagnostics.record(context, EventLog.DECLINED_ON_WATCH, "", "test call");
            testResult(TestCall.Result.REACHED);
            end(new DisconnectCause(DisconnectCause.REJECTED));
            return;
        }
        Log.i(RelayListener.TAG, "declined from the watch");
        RelayListener.incomingHandledOnWatch();
        Diagnostics.record(context, EventLog.DECLINED_ON_WATCH, RelayListener.currentApp, "");
        CallActions call = RelayListener.incomingCall;
        RelayListener.send(context, call == null ? null : call.decline);
        end(new DisconnectCause(DisconnectCause.REJECTED));
    }

    @Override
    public void onDisconnect() {
        if (testCall) {
            end(new DisconnectCause(DisconnectCause.LOCAL));
            return;
        }
        Log.i(RelayListener.TAG, "hung up from the watch");
        Diagnostics.record(context, EventLog.HUNG_UP_ON_WATCH, RelayListener.currentApp, "");
        CallActions ongoing = RelayListener.ongoingCall;
        CallActions incoming = RelayListener.incomingCall;
        if (ongoing != null && ongoing.hangUp != null) {
            RelayListener.send(context, ongoing.hangUp);
        } else if (incoming != null) {
            RelayListener.send(context, incoming.decline);
        }
        end(new DisconnectCause(DisconnectCause.LOCAL));
    }

    @Override
    public void onAbort() {
        end(new DisconnectCause(DisconnectCause.CANCELED));
    }

    @Override
    public void onShowIncomingCallUi() {
        // The app already shows its own incoming-call screen on the phone.
    }

    private void watchRecordings() {
        if (recordingCallback != null) {
            return;
        }
        android.media.AudioManager.AudioRecordingCallback callback = new android.media.AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(java.util.List<android.media.AudioRecordingConfiguration> configs) {
                java.util.List<String> described = new java.util.ArrayList<>();
                for (android.media.AudioRecordingConfiguration config : configs) {
                    described.add(Recordings.describe(config.getClientAudioSource(), config.isClientSilenced()));
                }
                String summary = Recordings.summary(described);
                if (recordingCallback == this && !summary.equals(lastRecordings)) {
                    lastRecordings = summary;
                    Diagnostics.record(context, EventLog.RECORDING, RelayListener.currentApp, summary);
                }
            }
        };
        try {
            context.getSystemService(android.media.AudioManager.class).registerAudioRecordingCallback(callback, handler);
            recordingCallback = callback;
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not watch recordings", e);
        }
    }

    private void stopWatchingRecordings() {
        android.media.AudioManager.AudioRecordingCallback callback = recordingCallback;
        recordingCallback = null;
        if (callback != null) {
            try {
                context.getSystemService(android.media.AudioManager.class).unregisterAudioRecordingCallback(callback);
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "could not stop watching recordings", e);
            }
        }
    }

    /** For the report only: what else Telecom (so, a watch's companion app) sends this call. Changes nothing. */
    private void signal(String detail) {
        Diagnostics.record(context, EventLog.CALL_SIGNAL, testCall ? null : RelayListener.currentApp, detail);
    }

    @Override
    public void onSilence() {
        signal("silence");
    }

    @Override
    public void onHold() {
        signal("hold");
    }

    @Override
    public void onUnhold() {
        signal("unhold");
    }

    @Override
    public void onCallEvent(String event, android.os.Bundle extras) {
        signal("event " + event);
    }

    /** The digit stays out: one typed into a call can be a PIN. */
    @Override
    public void onPlayDtmfTone(char c) {
        signal("dtmf");
    }

    /** Key names only: the values can hold a caller's name or number. */
    @Override
    public void onExtrasChanged(android.os.Bundle extras) {
        if (extras == null || extras.isEmpty()) {
            signal("extras none");
            return;
        }
        signal("extras " + String.join(",", new java.util.TreeSet<>(extras.keySet())));
    }

    /** For the report: whether Telecom ever took the watch's Answer shows up as ringing → active. */
    @Override
    public void onStateChanged(int state) {
        Diagnostics.record(context, EventLog.CALL_STATE, testCall ? null : RelayListener.currentApp,
                Connection.stateToString(state).toLowerCase(java.util.Locale.ROOT));
        if (state == STATE_ACTIVE && !testCall) {
            watchRecordings();
        }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        if (state != null) {
            String detail = AudioRoute.describe(state.getRoute(), state.getSupportedRouteMask());
            if (!detail.equals(lastRouteDetail)) {
                lastRouteDetail = detail;
                Diagnostics.record(context, EventLog.AUDIO_ROUTE, testCall ? null : RelayListener.currentApp, detail);
            }
        }
        routeToWatch(state);
    }

    /**
     * Moves the call audio to the watch (Bluetooth) when it's available and not there yet. Telecom
     * reports a new audio state after answering an incoming call, but not after an outgoing call
     * goes active, so that path calls this itself.
     */
    @SuppressWarnings("deprecation") // getCallAudioState/setAudioRoute: CallEndpoint API needs 34
    private void routeToWatch(CallAudioState state) {
        if (voiceOnPhone) {
            return;
        }
        if (state == null) {
            state = getCallAudioState();
        }
        if (isActive() && state != null && (state.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) == 0
                && !noWatchLogged) {
            noWatchLogged = true;
            Log.w(RelayListener.TAG, "no Bluetooth route for the call: the watch isn't connected for calls");
            Diagnostics.record(context, EventLog.AUDIO_NO_WATCH, testCall ? null : RelayListener.currentApp,
                    "supported=" + AudioRoute.names(state.getSupportedRouteMask()));
        }
        if (isActive() && state != null
                && (state.getSupportedRouteMask() & CallAudioState.ROUTE_BLUETOOTH) != 0
                && state.getRoute() != CallAudioState.ROUTE_BLUETOOTH) {
            Log.i(RelayListener.TAG, "routing call audio to the watch");
            setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
        }
    }

    private void end(DisconnectCause cause) {
        if (current == this) {
            current = null;
        }
        awaitingAppAnswer = false;
        stopWatchingRecordings();
        handler.removeCallbacksAndMessages(null);
        setDisconnected(cause);
        destroy();
        VoiceControls.update(context);
    }
}
