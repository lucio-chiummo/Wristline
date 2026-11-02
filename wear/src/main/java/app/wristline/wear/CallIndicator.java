package app.wristline.wear;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

/**
 * While a call started from the watch is on: an icon on the watch face (and in the recent apps)
 * that brings the call screen back after Minimise. Needs the notifications permission; without it
 * there's simply no icon.
 */
final class CallIndicator {
    private static final String CHANNEL = "call_in_progress";
    private static final int ID = 1;

    private CallIndicator() {
    }

    static void show(Context context, String name, String app) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    context.getString(R.string.channel_call_in_progress), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
            PendingIntent back = PendingIntent.getActivity(context, 0,
                    new Intent(context, InCallActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String text = context.getString(R.string.call_in_progress, name, Protocol.label(app));
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_call)
                    .setContentTitle(name)
                    .setContentText(text)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setOngoing(true)
                    .setSilent(true)
                    .setContentIntent(back);
            new OngoingActivity.Builder(context, ID, builder)
                    .setStaticIcon(R.drawable.ic_call)
                    .setTouchIntent(back)
                    .setStatus(new Status.Builder().addTemplate(text).build())
                    .build()
                    .apply(context);
            manager.notify(ID, builder.build());
        } catch (RuntimeException e) {
            // No notifications permission: the call works, there's just no icon.
            Log.w(PhoneLink.TAG, "could not show the call indicator", e);
        }
    }

    static void hide(Context context) {
        context.getSystemService(NotificationManager.class).cancel(ID);
    }
}
