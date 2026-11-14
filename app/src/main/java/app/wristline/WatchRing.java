package app.wristline;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Rings the watch with Wristline's own Answer/Decline notification, alerting again every few
 * seconds so it feels like a ringing call. Every watch companion passes notification buttons on,
 * which isn't true of the Answer on a call screen. The buttons go to {@link ActionReceiver}.
 */
final class WatchRing {
    /** The watch buzzes again this often while the call rings. */
    static final long REPEAT_MS = 4000;
    /** A call nobody picks up stops ringing after this long. */
    static final long MAX_MS = 60_000;

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    /** The repeat in progress; null when nothing rings. Main thread only, read from anywhere. */
    private static volatile Runnable repeat;

    private WatchRing() {
    }

    /** Rings until {@link #stop} or for {@code maxMs}; a ring already going is replaced. */
    static void start(Context context, String title, String text, long maxMs) {
        Context app = context.getApplicationContext();
        onMain(() -> {
            cancelRepeat();
            long until = SystemClock.uptimeMillis() + maxMs;
            Runnable ring = new Runnable() {
                @Override
                public void run() {
                    if (repeat != this) {
                        return;
                    }
                    long left = until - SystemClock.uptimeMillis();
                    if (left <= 0) {
                        stopNow(app);
                        return;
                    }
                    post(app, title, text, left);
                    HANDLER.postDelayed(this, REPEAT_MS);
                }
            };
            repeat = ring;
            ring.run();
        });
    }

    private static void onMain(Runnable work) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            work.run();
        } else {
            HANDLER.post(work);
        }
    }

    static void stop(Context context) {
        Context app = context.getApplicationContext();
        onMain(() -> stopNow(app));
    }

    static boolean ringing() {
        return repeat != null;
    }

    private static void cancelRepeat() {
        Runnable ring = repeat;
        repeat = null;
        if (ring != null) {
            HANDLER.removeCallbacks(ring);
        }
    }

    private static void stopNow(Context context) {
        cancelRepeat();
        context.getSystemService(NotificationManager.class).cancel(StatusNotifications.ID_INCOMING_FALLBACK);
    }

    private static void post(Context context, String title, String text, long timeoutMs) {
        Notification ring = new Notification.Builder(context, StatusNotifications.CHANNEL_RING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setTimeoutAfter(timeoutMs)
                .addAction(action(context, context.getString(R.string.answer), ActionReceiver.ANSWER, 1))
                .addAction(action(context, context.getString(R.string.decline), ActionReceiver.DECLINE, 2))
                .build();
        context.getSystemService(NotificationManager.class).notify(StatusNotifications.ID_INCOMING_FALLBACK, ring);
    }

    private static Notification.Action action(Context context, CharSequence label, String which, int requestCode) {
        Intent intent = new Intent(context, ActionReceiver.class).setAction(which);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(null, label, pi).build();
    }
}
