package app.wristline.wear;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * While the phone rings the watch by notification: a call notification with a full-screen intent,
 * the supported way to open {@link IncomingActivity} over everything (a plain activity start from
 * the background is blocked). Silent: {@link WatchBuzz} does the vibrating.
 */
final class IncomingRing {
    static final int ID = 2;
    private static final String CHANNEL = "incoming_call";

    /** When the ring in progress ends at the latest ({@code elapsedRealtime}); 0 when none rings. */
    private static volatile long ringingUntil;

    private IncomingRing() {
    }

    /** Shows the incoming call screen for {@code ring}, which has a caller. */
    static void start(Context context, Protocol.Ring ring) {
        ringingUntil = SystemClock.elapsedRealtime() + ring.maxMs;
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    context.getString(R.string.channel_incoming_call), NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null);
            channel.enableVibration(false);
            manager.createNotificationChannel(channel);
            if (Build.VERSION.SDK_INT >= 34 && !manager.canUseFullScreenIntent()) {
                Log.w(PhoneLink.TAG, "full-screen intents not allowed: the incoming screen may stay a notification");
            }
            Intent screen = IncomingActivity.intent(context, ring);
            PendingIntent open = PendingIntent.getActivity(context, 0, screen,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            // Different request codes: extras alone don't tell PendingIntents apart.
            PendingIntent answer = PendingIntent.getActivity(context, 1,
                    new Intent(screen).putExtra(IncomingActivity.EXTRA_ACTION, IncomingActivity.ACTION_ANSWER),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent decline = PendingIntent.getActivity(context, 2,
                    new Intent(screen).putExtra(IncomingActivity.EXTRA_ACTION, IncomingActivity.ACTION_DECLINE),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_call)
                    .setContentTitle(ring.name)
                    .setContentText(Protocol.label(ring.app))
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOngoing(true)
                    // Not setSilent: its silent group makes the system drop the full-screen intent.
                    // The channel is silent already.
                    .setOnlyAlertOnce(true)
                    .setTimeoutAfter(ring.maxMs)
                    .setContentIntent(open)
                    .setFullScreenIntent(open, true)
                    .addAction(R.drawable.ic_call, context.getString(R.string.answer), answer)
                    .addAction(R.drawable.ic_call_end, context.getString(R.string.decline), decline);
            manager.notify(ID, builder.build());
        } catch (RuntimeException e) {
            // No notifications permission: the watch still buzzes.
            Log.w(PhoneLink.TAG, "could not show the incoming call", e);
        }
        try {
            // Wear may open it only through the full-screen intent; a blocked start is harmless.
            context.startActivity(IncomingActivity.intent(context, ring));
        } catch (RuntimeException e) {
            Log.w(PhoneLink.TAG, "could not open the incoming screen", e);
        }
    }

    /** The ring is over (answered, declined, stopped on the phone): no notification, no screen. */
    static void stop(Context context) {
        ringingUntil = 0;
        context.getSystemService(NotificationManager.class).cancel(ID);
        IncomingActivity screen = IncomingActivity.current;
        if (screen != null) {
            screen.runOnUiThread(screen::finish);
        }
    }

    /** How long the ring in progress may still go on; 0 when it's over (stopped or past its longest). */
    static long remainingMs() {
        return Math.max(0, ringingUntil - SystemClock.elapsedRealtime());
    }
}
