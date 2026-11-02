package app.wristline;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * A relayed call nobody answered: marked in the history and told with Wristline's own
 * notification, which the Pixel Watch app shows on the watch (WhatsApp's own missed-call
 * notifications never get there: its watch app turns bridging off). "Call back" starts the call
 * the way the watch does, when the caller is a callable contact.
 */
final class MissedCalls {
    private static final String EXTRA_CONTACT = "contact";
    private static final String EXTRA_APP = "app";
    private static final String EXTRA_TIME = "time";

    private MissedCalls() {
    }

    static void onMissed(Context context, SupportedApp app, String caller, long startedAt) {
        Diagnostics.record(context, EventLog.MISSED, app, "");
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            // Give the history entry (written off the main thread at ring time) a moment to land.
            CallHistory.Entry entry = null;
            for (int i = 0; i < 10 && entry == null; i++) {
                entry = CallHistory.at(Prefs.callHistory(appContext), startedAt);
                if (entry == null) {
                    sleep(100);
                }
            }
            Prefs.markMissed(appContext, startedAt);
            WatchSync.publish(appContext);
            // The app's own missed-call notification (with its "Call back") can land a moment later.
            for (int i = 0; i < 20 && app.ownCallBack && !OwnCallBack.has(app.packageName, startedAt); i++) {
                sleep(100);
            }
            notify(appContext, app, caller, entry == null ? -1 : entry.contactId, startedAt);
        }, "missed-call").start();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Call back needs calling from the watch on (contacts + "make calls") and a callable contact. */
    static boolean canCallBack(Context context, SupportedApp app, long contactId) {
        return contactId >= 0 && app.callMimeType != null && Prefs.watchCalling(context)
                && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    private static void notify(Context context, SupportedApp app, String caller, long contactId, long startedAt) {
        Notification.Builder builder = new Notification.Builder(context, StatusNotifications.CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.missed_title, app.label))
                .setContentText(caller)
                .setCategory(Notification.CATEGORY_MISSED_CALL)
                .setWhen(startedAt)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE));
        int id = notificationId(startedAt);
        boolean own = app.ownCallBack && OwnCallBack.has(app.packageName, startedAt);
        if (own || canCallBack(context, app, contactId)) {
            Intent callBack = new Intent(context, CallBackReceiver.class)
                    .putExtra(EXTRA_CONTACT, contactId)
                    .putExtra(EXTRA_APP, app.packageName)
                    .putExtra(EXTRA_TIME, startedAt);
            builder.addAction(new Notification.Action.Builder(null, context.getString(R.string.call_back),
                    PendingIntent.getBroadcast(context, id, callBack,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        context.getSystemService(NotificationManager.class).notify(StatusNotifications.TAG_MISSED, id, builder.build());
    }

    /** One notification per missed call, so two missed calls don't replace each other. */
    static int notificationId(long startedAt) {
        return (int) (startedAt / 1000);
    }

    /** "Call back" on the missed-call notification (also from the watch, where it's shown). */
    public static class CallBackReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long contactId = intent.getLongExtra(EXTRA_CONTACT, -1);
            SupportedApp app = SupportedApp.forPackage(intent.getStringExtra(EXTRA_APP));
            long startedAt = intent.getLongExtra(EXTRA_TIME, 0);
            context.getSystemService(NotificationManager.class).cancel(StatusNotifications.TAG_MISSED,
                    notificationId(startedAt));
            if (app != null && app.ownCallBack && OwnCallBack.has(app.packageName, startedAt)) {
                OwnCallBack.press(context, app.packageName, startedAt);
                return;
            }
            if (app == null || !canCallBack(context, app, contactId)) {
                Log.w(RelayListener.TAG, "can't call back");
                return;
            }
            PendingResult pending = goAsync();
            Context appContext = context.getApplicationContext();
            new Thread(() -> {
                try {
                    OutgoingCalls.start(appContext, contactId, app);
                } finally {
                    pending.finish();
                }
            }, "call-back").start();
        }
    }
}
