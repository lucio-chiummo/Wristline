package app.wristline;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.database.ContentObserver;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.Ranking;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.Collections;

/**
 * Watches the call notifications of the supported apps and mirrors each incoming call as a
 * self-managed Telecom call. The Pixel Watch companion bridges self-managed Telecom calls to the
 * watch as real calls (ringing screen, Answer/Decline), which these apps don't do themselves.
 */
public class RelayListener extends NotificationListenerService {
    static final String TAG = "Wristline";
    static final String EXTRA_CALLER = "app.wristline.CALLER";
    static final String EXTRA_FULL_MODE = "app.wristline.FULL_MODE";

    static volatile CallActions incomingCall;
    static volatile CallActions ongoingCall;
    /** App of the call being relayed, null when there is none. */
    static volatile SupportedApp currentApp;
    /** Decided when the call starts; full mode routes the call audio to the watch. */
    static volatile boolean useFullModeForCall;
    /** The incoming call being relayed: when it started (its history entry) and who's calling. */
    private static volatile long incomingStartedAt;
    private static volatile String incomingCaller;
    /** Answered (anywhere) or declined on the watch: not a missed call when it ends. */
    private static volatile boolean incomingHandled;
    private static String shownOngoingLabels;

    private final CallTracker tracker = new CallTracker();
    /** The running listener, for calls started from the watch. */
    private static volatile RelayListener instance;
    /** If the app hasn't shown its in-call notification by then, the call didn't start. */
    private static final long OUTGOING_START_MS = 20_000;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.migrate(this);
        LegacyAdbKey.delete(this);
        StatusNotifications.createChannels(this);
        registerAccount(this);
    }

    static PhoneAccountHandle accountHandle(Context context) {
        return new PhoneAccountHandle(new ComponentName(context, RelayConnectionService.class), "relay");
    }

    private static void registerAccount(Context context) {
        PhoneAccount account = PhoneAccount.builder(accountHandle(context), context.getString(R.string.phone_account_label))
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .setSupportedUriSchemes(Collections.singletonList(PhoneAccount.SCHEME_SIP))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_notification))
                .build();
        context.getSystemService(TelecomManager.class).registerPhoneAccount(account);
    }

    /** Contacts changed: refresh the watch's list (debounced; a sync touches many rows). */
    private final ContentObserver contactsObserver = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            handler.removeCallbacks(publishContacts);
            handler.postDelayed(publishContacts, 5000);
        }
    };
    private final Runnable publishContacts = () -> {
        if (Prefs.watchCalling(this)) {
            WatchSync.publish(this);
        }
        WatchSync.publishStatus(this);
    };

    private boolean watchingContacts;

    /** Follows contact changes once watch calling is on and contacts may be read. */
    private void watchContacts() {
        if (watchingContacts || !Prefs.watchCalling(this)
                || checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver);
            watchingContacts = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "could not watch contacts", e);
        }
    }

    /** Watch calling was just turned on (contacts permission granted meanwhile). */
    static void watchCallingTurnedOn() {
        RelayListener listener = instance;
        if (listener != null) {
            listener.handler.post(listener::watchContacts);
        }
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        Diagnostics.record(this, EventLog.LISTENER_DISCONNECTED, "", "");
        if (Prefs.listenerUnboundSince(this) == 0) {
            Prefs.setListenerUnboundSince(this, System.currentTimeMillis());
        }
    }

    /** Android has bound the listener: call notifications reach Wristline. */
    static boolean isConnected() {
        return instance != null;
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        long unboundSince = Prefs.listenerUnboundSince(this);
        String gap = unboundSince > 0
                ? "after " + EventLog.duration(System.currentTimeMillis() - unboundSince) + " unbound" : "";
        Diagnostics.record(this, EventLog.LISTENER_CONNECTED, "", gap);
        Prefs.setListenerUnboundSince(this, 0);
        ListenerCheckJob.sync(this);
        Runnable home = ListenerRebind.onChange;
        if (home != null) {
            new Handler(Looper.getMainLooper()).post(home);
        }
        watchContacts();
        publishContacts.run();
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                onNotificationPosted(sbn);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not read active notifications", e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        SupportedApp app = SupportedApp.forPackage(sbn.getPackageName());
        if (app == null) {
            return;
        }
        CallActions call = CallActions.from(sbn.getNotification(), app.ongoingLacksCallCategory);
        if (call == null) {
            if (app.ownCallBack) {
                OwnCallBack.capture(app.packageName, sbn.getNotification(), System.currentTimeMillis());
            }
            // A ringing notification can be updated in place into a missed-call one.
            onCallNotificationGone(app, tracker.notACall(app.packageName, sbn.getKey()), false);
            return;
        }
        if (!call.incoming) {
            if (tracker.ongoing(app.packageName, sbn.getKey()) == CallTracker.Result.ONGOING) {
                onOngoingCall(call);
            }
            return;
        }
        if (call.answer == null || call.decline == null) {
            Diagnostics.unreadableCall(this, app, sbn.getNotification());
            return;
        }
        // Switches and Do Not Disturb only decide whether a new call starts; one already ringing carries on.
        IncomingRule.Decision rule = IncomingRule.decide(Prefs.relayOn(this), Prefs.appOn(this, app),
                Prefs.followDnd(this), passesDnd(sbn), Prefs.notWhileInUse(this), phoneInUse());
        boolean allowed = rule == IncomingRule.Decision.RELAY;
        switch (tracker.incoming(app.packageName, sbn.getKey(), allowed)) {
            case START:
                incomingCall = call;
                startCall(app, call);
                break;
            case NONE:
                // Reposts of the ringing notification refresh its buttons; another call from the
                // same app mustn't replace them, or Answer on the watch would pick up that call.
                if (tracker.isIncomingKey(app.packageName, sbn.getKey())) {
                    incomingCall = call;
                }
                break;
            default:
                Log.i(TAG, "not relaying incoming call from " + app.packageName + " (" + (allowed
                        ? "another call is being relayed" : rule.name().toLowerCase(java.util.Locale.ROOT)) + ")");
                Diagnostics.record(this, allowed ? EventLog.BUSY
                        : rule == IncomingRule.Decision.DO_NOT_DISTURB ? EventLog.DO_NOT_DISTURB
                        : rule == IncomingRule.Decision.PHONE_IN_USE ? EventLog.PHONE_IN_USE
                        : EventLog.SWITCHED_OFF, app, "");
                break;
        }
    }

    /** The screen is on and unlocked: the phone is in the user's hand. */
    boolean phoneInUse() {
        return getSystemService(android.os.PowerManager.class).isInteractive()
                && !getSystemService(android.app.KeyguardManager.class).isKeyguardLocked();
    }

    /**
     * Whether the phone's Do Not Disturb lets this notification through, by its own rules (allowed
     * contacts, repeat callers, schedules). True when it can't be told.
     */
    boolean passesDnd(StatusBarNotification sbn) {
        try {
            RankingMap rankings = getCurrentRanking();
            Ranking ranking = new Ranking();
            return rankings == null || !rankings.getRanking(sbn.getKey(), ranking) || ranking.matchesInterruptionFilter();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Rings the watch with a test call (see {@link TestCall}). Returns false when a real call is
     * on or Telecom refuses; works without notification access.
     */
    static boolean startTestCall(Context context) {
        if (TestCall.check(currentApp != null, RelayConnection.current != null) != TestCall.Start.OK) {
            Log.i(TAG, "not starting a test call: a call is on");
            return false;
        }
        Bundle extras = new Bundle();
        extras.putString(EXTRA_CALLER, context.getString(R.string.test_call_caller));
        extras.putBoolean(TestCall.EXTRA, true);
        try {
            registerAccount(context);
            context.getSystemService(TelecomManager.class).addNewIncomingCall(accountHandle(context), extras);
            Log.i(TAG, "test call started");
            Diagnostics.record(context, EventLog.TEST_CALL, "", "started");
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Telecom refused the test call", e);
            return false;
        }
    }

    private void startCall(SupportedApp app, CallActions call) {
        // A real call replaces a test call that is still ringing.
        RelayConnection.endTestCall();
        currentApp = app;
        ongoingCall = null;
        shownOngoingLabels = null;
        useFullModeForCall = FullMode.shouldUse(this, app);

        String caller = call.caller == null ? getString(R.string.unknown_caller, app.label) : call.caller.toString();
        outgoingName = null;
        incomingStartedAt = System.currentTimeMillis();
        incomingCaller = caller;
        incomingHandled = false;
        WatchSync.recordCall(this, false, app, -1, caller, incomingStartedAt);
        Log.i(TAG, "incoming call (" + app.packageName
                + (useFullModeForCall ? ", full mode)" : ", basic mode)"));
        Diagnostics.record(this, EventLog.INCOMING, app, useFullModeForCall ? "full mode" : "basic mode");
        Bundle extras = new Bundle();
        extras.putString(EXTRA_CALLER, call.caller == null ? caller  // "WhatsApp call", not "WhatsApp · WhatsApp call"
                : getString(R.string.caller_with_app, app.label, caller));
        try {
            registerAccount(this);
            getSystemService(TelecomManager.class).addNewIncomingCall(accountHandle(this), extras);
        } catch (RuntimeException e) {
            Log.w(TAG, "Telecom refused the mirrored call; falling back to a notification", e);
            Diagnostics.record(this, EventLog.TELECOM_REFUSED, app, e.getClass().getSimpleName());
            postFallbackNotification(app, caller);
        }
    }

    /**
     * The watch started a call in {@code app}: show it on the watch as an outgoing call and follow
     * the app's in-call notification. Returns false when another call is being relayed.
     */
    static boolean startOutgoing(Context context, SupportedApp app, String name, Runnable startApp) {
        RelayListener listener = instance;
        if (listener == null) {
            Log.w(TAG, "notification access is off: can't follow the call");
            return false;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return listener.outgoing(app, name, startApp);
        }
        java.util.concurrent.FutureTask<Boolean> task = new java.util.concurrent.FutureTask<>(() -> listener.outgoing(app, name, startApp));
        listener.handler.post(task);
        try {
            return task.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /** The watch answered or declined the ringing call: it isn't a missed call. */
    static void incomingHandledOnWatch() {
        incomingHandled = true;
    }

    /** The app couldn't start the call the watch asked for: forget it. */
    static void outgoingFailed(SupportedApp app) {
        RelayListener listener = instance;
        if (listener != null) {
            listener.handler.post(() -> {
                listener.tracker.reset();
                clearCall(listener);
                String name = outgoingName;
                if (name != null) {
                    WatchSync.callState(listener, WatchProtocol.STATE_ENDED, app, name);
                    outgoingName = null;
                }
            });
        }
    }

    /** What the watch's call screen was last told: "<kind>;<mute>" (the timer runs on the watch). */
    private static volatile String toldWatch;

    /**
     * "Ringing" until the app's in-call notification shows a call timer (the other side answered),
     * then "active" with the real start time; resent when the mute button changes.
     */
    private void tellWatchAboutOutgoing(CallActions call) {
        boolean connected = call.connectedAt > 0;
        String kind = connected ? WatchProtocol.STATE_ACTIVE : WatchProtocol.STATE_RINGING;
        String told = kind + ";" + call.muteState();
        if (told.equals(toldWatch)) {
            return;
        }
        toldWatch = told;
        long connectedMs = connected ? Math.max(0, System.currentTimeMillis() - call.connectedAt) : -1;
        WatchSync.callState(this, kind, connectedMs, call.muteState(), currentApp, outgoingName);
    }

    /** Mute on Wristline's own call screen on the watch: the app's own mute button. */
    static void muteFromWatch() {
        RelayListener listener = instance;
        if (listener == null) {
            return;
        }
        listener.handler.post(() -> {
            CallActions call = ongoingCall;
            if (call != null && call.mute != null) {
                send(listener, call.mute);
            } else {
                Log.w(TAG, "mute asked from the watch, but the app has no mute button");
            }
        });
    }

    /** Hang-up on Wristline's own call screen on the watch. */
    static void hangUpFromWatch() {
        RelayListener listener = instance;
        if (listener == null) {
            return;
        }
        listener.handler.post(() -> {
            RelayConnection connection = RelayConnection.current;
            if (connection != null) {
                connection.onDisconnect();
            }
        });
    }

    /** The call in progress was started from the watch: keep the watch's call screen informed. */
    private static volatile String outgoingName;

    /** Telecom refusing the call, for a missing permission too, is caught below and leaves the call on the phone. */
    @SuppressLint("MissingPermission")
    private boolean outgoing(SupportedApp app, String name, Runnable startApp) {
        if (tracker.outgoing(app.packageName) != CallTracker.Result.START) {
            Log.i(TAG, "not starting a watch call: another call is being relayed");
            return false;
        }
        currentApp = app;
        incomingCall = null;
        ongoingCall = null;
        shownOngoingLabels = null;
        outgoingName = name;
        toldWatch = null;
        WatchSync.callState(this, WatchProtocol.STATE_CALLING, app, name);
        useFullModeForCall = FullMode.shouldUse(this, app);
        Log.i(TAG, "outgoing call (" + app.packageName
                + (useFullModeForCall ? ", full mode)" : ", basic mode)"));
        Diagnostics.record(this, EventLog.OUTGOING, app, useFullModeForCall ? "full mode" : "basic mode");
        Bundle callExtras = new Bundle();
        callExtras.putString(EXTRA_CALLER, getString(R.string.caller_with_app, app.label, name));
        callExtras.putBoolean(EXTRA_FULL_MODE, useFullModeForCall);
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle(this));
        extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callExtras);
        RelayConnection.pendingAppStart = startApp;
        try {
            registerAccount(this);
            getSystemService(TelecomManager.class).placeCall(
                    android.net.Uri.fromParts(PhoneAccount.SCHEME_SIP, name, null), extras);
        } catch (RuntimeException e) {
            Log.w(TAG, "Telecom refused the outgoing mirror; the call stays on the phone", e);
            Diagnostics.record(this, EventLog.TELECOM_REFUSED, app, "outgoing " + e.getClass().getSimpleName());
            RelayConnection.pendingAppStart = null;
            tracker.reset();
            return false;
        }
        int callId = tracker.callId();
        handler.postDelayed(() -> {
            if (tracker.graceExpired(callId) == CallTracker.Result.END) {
                Log.w(TAG, app.packageName + " didn't start the call");
                Diagnostics.record(this, EventLog.OUTGOING_NOT_STARTED, app, "");
                endCall(app, false);
            }
        }, OUTGOING_START_MS);
        return true;
    }

    private void onOngoingCall(CallActions call) {
        if (outgoingName != null && currentApp != null) {
            tellWatchAboutOutgoing(call);
        }
        incomingHandled = true;
        ongoingCall = call;
        RelayConnection.answeredOnPhone();
        RelayConnection connection = RelayConnection.current;
        boolean watchShowsCall = connection != null && connection.isShownOnWatch();
        StringBuilder labels = new StringBuilder();
        for (Notification.Action action : call.actions) {
            labels.append(action.title).append('|');
        }
        // Apps refresh this notification constantly (call timer); repost only when buttons change.
        if (!watchShowsCall && call.actions.length > 0 && !labels.toString().equals(shownOngoingLabels)) {
            shownOngoingLabels = labels.toString();
            postOngoingControls(call);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        SupportedApp app = SupportedApp.forPackage(sbn.getPackageName());
        if (app == null) {
            return;
        }
        // Read now: by the end of the grace period the watch has finished taking the audio.
        boolean takingAudio = RelayConnection.isAwaitingAppAnswer();
        onCallNotificationGone(app, tracker.removed(app.packageName, sbn.getKey(), takingAudio,
                app.separateInCallNotification), takingAudio);
    }

    private void onCallNotificationGone(SupportedApp app, CallTracker.Result result, boolean takingAudio) {
        switch (result) {
            case END:
                endCall(app, takingAudio);
                break;
            case CHECK_LATER:
                // The ringing notification went: answered (an in-call one follows) or declined.
                int callId = tracker.callId();
                handler.postDelayed(() -> {
                    if (tracker.graceExpired(callId) == CallTracker.Result.END) {
                        endCall(app, false);
                    }
                }, CallTracker.GRACE_MS);
                break;
            default:
                break;
        }
    }

    private void endCall(SupportedApp app, boolean droppedWhileTakingAudio) {
        Log.i(TAG, app.packageName + " call ended");
        Diagnostics.record(this, droppedWhileTakingAudio ? EventLog.DROPPED_TAKING_AUDIO : EventLog.ENDED, app, "");
        if (outgoingName != null) {
            WatchSync.callState(this, WatchProtocol.STATE_ENDED, app, outgoingName);
            outgoingName = null;
        } else if (!incomingHandled && incomingCaller != null) {
            MissedCalls.onMissed(this, app, incomingCaller, incomingStartedAt);
        }
        incomingCaller = null;
        clearCall(this);
        RelayConnection.endFromApp();
        if (droppedWhileTakingAudio) {
            FullMode.onCallDropped(this, app);
        }
    }

    static void clearCall(Context context) {
        currentApp = null;
        incomingCall = null;
        ongoingCall = null;
        shownOngoingLabels = null;
        StatusNotifications.cancel(context, StatusNotifications.ID_INCOMING_FALLBACK);
        StatusNotifications.cancel(context, StatusNotifications.ID_ONGOING);
    }

    /** Fires one of the calling app's own notification actions, allowing it to open its call screen. */
    static void send(Context context, PendingIntent intent) {
        if (intent == null) {
            Log.w(TAG, "no app action to send");
            return;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        }
        try {
            intent.send(context, 0, null, null, null, null, options.toBundle());
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "app action no longer valid", e);
        }
    }

    private void postFallbackNotification(SupportedApp app, String caller) {
        Notification relay = new Notification.Builder(this, StatusNotifications.CHANNEL_INCOMING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(caller)
                .setContentText(getString(R.string.incoming_call, app.label))
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(60000)
                .addAction(action(getString(R.string.answer), ActionReceiver.ANSWER, 0, 1))
                .addAction(action(getString(R.string.decline), ActionReceiver.DECLINE, 0, 2))
                .build();
        getSystemService(NotificationManager.class).notify(StatusNotifications.ID_INCOMING_FALLBACK, relay);
    }

    /** Watch-visible card with the app's own in-call buttons (Hang up, Mute, ...). */
    private void postOngoingControls(CallActions call) {
        SupportedApp app = currentApp;
        String label = app == null ? "" : app.label;
        // In-call notifications don't always name the caller (Instagram: "Audio call").
        CallActions incoming = incomingCall;
        CharSequence caller = incoming != null && incoming.caller != null ? incoming.caller : call.caller;
        Notification.Builder builder = new Notification.Builder(this, StatusNotifications.CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(caller == null ? getString(R.string.unknown_caller, label) : caller)
                .setContentText(getString(R.string.on_call, label))
                // Call category keeps the phone-to-watch sync immediate instead of batched.
                .setCategory(Notification.CATEGORY_CALL)
                .setOnlyAlertOnce(true);
        for (int i = 0; i < call.actions.length; i++) {
            builder.addAction(action(call.actions[i].title, ActionReceiver.ONGOING, i, 10 + i));
        }
        getSystemService(NotificationManager.class).notify(StatusNotifications.ID_ONGOING, builder.build());
    }

    private Notification.Action action(CharSequence label, String which, int index, int requestCode) {
        Intent intent = new Intent(this, ActionReceiver.class).setAction(which).putExtra(ActionReceiver.EXTRA_INDEX, index);
        PendingIntent pi = PendingIntent.getBroadcast(
                this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(null, label, pi).build();
    }
}
