package app.wristline;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * "Ring my watch": a call that only exists in Wristline, to check that calls reach the watch
 * (and, answered, that its sound plays there) without anyone calling. It never touches a calling
 * app and isn't added to the call history.
 */
final class TestCall {
    /** Set on the Telecom call's extras. */
    static final String EXTRA = "app.wristline.TEST_CALL";
    /** Unanswered, it stops ringing after this long. */
    static final long RING_MS = 30_000;
    /** Answered, it beeps and hangs up after this long. */
    static final long ANSWERED_MS = 5_000;
    /** Beeps after answering: when each starts (ms after answering); each lasts {@link #BEEP_MS}. */
    static final long[] BEEPS_AT = {800, 1800, 2800};
    static final int BEEP_MS = 300;

    enum Start { OK, BUSY }

    /** How the last test call went: answered or declined on the watch means it reached it. */
    enum Result { NONE, REACHED, MISSED }

    /** Home, while it's shown: run on the main thread when a test call's result is known. */
    static volatile Runnable onResult;

    /** A test call ringing by notification is on (see {@link WatchRing}). */
    static volatile boolean notificationRinging;

    private TestCall() {
    }

    /** Gives up on a notification test nobody answered; main thread only. */
    private static Runnable timeout;

    /** Remembers how a test call went and tells Home. */
    static void report(Context context, Result result) {
        Prefs.setTestCallResult(context, result);
        Diagnostics.record(context, EventLog.TEST_CALL, "", result.name().toLowerCase(java.util.Locale.ROOT));
        Runnable listener = onResult;
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(listener);
        }
    }

    /**
     * The test call for watches rung by notification: the same buzzing Answer/Decline notification
     * a real call gets. A tap on either button there means it reached the watch.
     */
    static void startNotificationTest(Context context) {
        Context app = context.getApplicationContext();
        notificationRinging = true;
        WatchRing.start(app, app.getString(R.string.test_call_caller), app.getString(R.string.test_call_text), RING_MS);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            cancelTimeout(handler);
            timeout = () -> {
                timeout = null;
                if (notificationRinging) {
                    notificationRinging = false;
                    WatchRing.stop(app);
                    report(app, Result.MISSED);
                }
            };
            handler.postDelayed(timeout, RING_MS);
        });
    }

    /** Answer or Decline was tapped on the test notification. */
    static void notificationTestTapped(Context context, boolean answer) {
        endNotificationTest(context);
        Diagnostics.record(context, answer ? EventLog.ANSWERED_ON_WATCH : EventLog.DECLINED_ON_WATCH, "", "test call");
        report(context, Result.REACHED);
    }

    /** Ends a notification test call without a verdict: a real call took its place. */
    static void endNotificationTest(Context context) {
        if (!notificationRinging) {
            return;
        }
        notificationRinging = false;
        WatchRing.stop(context);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> cancelTimeout(handler));
    }

    private static void cancelTimeout(Handler handler) {
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
    }

    /** A real call (relayed or mirrored) always wins: no test while one is on. */
    static Start check(boolean relayingCall, boolean mirroredCallExists) {
        return relayingCall || mirroredCallExists ? Start.BUSY : Start.OK;
    }
}
