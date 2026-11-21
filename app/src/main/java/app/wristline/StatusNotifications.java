package app.wristline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Notification channels and the app's own status notifications. */
final class StatusNotifications {
    static final String CHANNEL_INCOMING = "incoming_calls";
    /** Calls ringing the watch by notification: see {@link WatchRing}. */
    static final String CHANNEL_RING = "watch_ring";
    static final String CHANNEL_ONGOING = "ongoing_call_controls";
    static final String CHANNEL_SETUP = "voice_setup";
    static final String CHANNEL_STATUS = "status";
    static final String CHANNEL_MISSED = "missed_calls";
    /** Missed-call notifications: this tag, one id per call. */
    static final String TAG_MISSED = "missed";

    static final int ID_INCOMING_FALLBACK = 1;
    static final int ID_ONGOING = 2;
    static final int ID_REPAIR = 3;
    static final int ID_PAIRING = 4;
    static final int ID_SETUP_RESULT = 5;
    /** Its own id, not the foreground one, so it pops up over Settings with the code field showing. */
    static final int ID_PAIRING_CODE = 6;
    static final int ID_UNREADABLE = 7;
    static final int ID_VOICE = 8;

    private StatusNotifications() {
    }

    static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);

        NotificationChannel incoming = new NotificationChannel(CHANNEL_INCOMING,
                context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH);
        incoming.enableVibration(true);
        manager.createNotificationChannel(incoming);

        // No sound: the calling app is already playing its ringtone on the phone. The watch buzzes.
        NotificationChannel ring = new NotificationChannel(CHANNEL_RING,
                context.getString(R.string.channel_ring), NotificationManager.IMPORTANCE_HIGH);
        ring.setSound(null, null);
        ring.enableVibration(true);
        manager.createNotificationChannel(ring);

        NotificationChannel ongoing = new NotificationChannel(CHANNEL_ONGOING,
                context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_DEFAULT);
        ongoing.setSound(null, null);
        ongoing.enableVibration(false);
        manager.createNotificationChannel(ongoing);

        NotificationChannel status = new NotificationChannel(CHANNEL_STATUS,
                context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(status);

        // Default importance: the watch buzzes once, like for a message.
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_MISSED,
                context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT));

        // High importance so the pairing-code field pops up over the Wireless debugging screen.
        NotificationChannel setup = new NotificationChannel(CHANNEL_SETUP,
                context.getString(R.string.channel_setup), NotificationManager.IMPORTANCE_HIGH);
        setup.setSound(null, null);
        setup.enableVibration(false);
        manager.createNotificationChannel(setup);

        // Older versions asked for the pairing code through this channel.
        manager.deleteNotificationChannel("adb_setup");
    }

    static void showRepairNeeded(Context context, SupportedApp app) {
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, VoiceActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.repair_title))
                .setContentText(context.getString(R.string.repair_text, app.label))
                .setStyle(new Notification.BigTextStyle().bigText(context.getString(R.string.repair_text, app.label)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(ID_REPAIR, notification);
    }

    static void cancel(Context context, int id) {
        context.getSystemService(NotificationManager.class).cancel(id);
    }
}
