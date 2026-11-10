package app.wristline;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.util.Log;

/**
 * Some phones kill the process and never bind the notification listener again, with Notification
 * access still granted. Ask Android to bind it, and when that doesn't connect, disable and
 * re-enable the component, which makes it look the listener up afresh.
 */
final class ListenerRebind {
    static final String HOME = "home";
    static final String QS_TILE = "qs_tile";
    static final String WATCH = "watch";
    static final String JOB = "job";
    static final String FIX = "fix";

    static final long CHECK_AFTER_MS = 5_000;

    /** Home, while it's shown: redraw once a check has finished. */
    static volatile Runnable onChange;

    private static boolean pending;

    private ListenerRebind() {
    }

    /**
     * Asks for a rebind if the listener isn't connected, toggles the component if that fails.
     * Callers may be on any thread: the work always runs on the main looper, so the pending flag
     * is only touched there and {@code done} runs there too.
     */
    static void ifNeeded(Context context, String trigger, Runnable done) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        if (Looper.myLooper() == Looper.getMainLooper()) {
            start(app, main, trigger, done);
        } else {
            main.post(() -> start(app, main, trigger, done));
        }
    }

    private static void start(Context app, Handler main, String trigger, Runnable done) {
        if (RelayListener.isConnected() || !accessGranted(app) || pending) {
            if (done != null) {
                done.run();
            }
            return;
        }
        pending = true;
        Diagnostics.record(app, EventLog.LISTENER_REBIND, "", trigger);
        requestRebind(app);
        main.postDelayed(() -> {
            try {
                if (!RelayListener.isConnected() && accessGranted(app)) {
                    toggle(app, trigger);
                    if (Prefs.listenerUnboundSince(app) == 0) {
                        Prefs.setListenerUnboundSince(app, System.currentTimeMillis());
                    }
                }
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "listener check failed", e);
            } finally {
                pending = false;
            }
            Runnable home = onChange;
            if (home != null) {
                home.run();
            }
            if (done != null) {
                done.run();
            }
        }, CHECK_AFTER_MS);
    }

    /** Disables and re-enables the listener component, then asks for a rebind. */
    static void toggle(Context context, String trigger) {
        ComponentName component = new ComponentName(context, RelayListener.class);
        PackageManager pm = context.getPackageManager();
        setState(pm, component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        setState(pm, component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        requestRebind(context);
        Diagnostics.record(context, EventLog.LISTENER_TOGGLED, "", trigger);
    }

    /** Access granted, listener not connected, and known to be unbound since a failed check or disconnect. */
    static boolean unbound(Context context) {
        return accessGranted(context)
                && !RelayListener.isConnected()
                && Prefs.listenerUnboundSince(context) > 0;
    }

    private static boolean accessGranted(Context context) {
        return Permissions.isDone(context, Permissions.Step.NOTIFICATION_ACCESS);
    }

    private static void setState(PackageManager pm, ComponentName component, int state) {
        try {
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not change the listener component state", e);
        }
    }

    private static void requestRebind(Context context) {
        try {
            NotificationListenerService.requestRebind(new ComponentName(context, RelayListener.class));
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not ask for the listener to be bound again", e);
        }
    }
}
