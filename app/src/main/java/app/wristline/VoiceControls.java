package app.wristline;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * During a call with its voice on the watch, a small notification (also shown on the watch) to move
 * the voice to the phone and back, e.g. when the watch battery runs low. The call carries on.
 */
final class VoiceControls {
    private static final String EXTRA_TO_PHONE = "to_phone";

    private VoiceControls() {
    }

    /** Shows, updates or removes the notification to match the call in progress. */
    static void update(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Boolean onWatch = RelayConnection.voiceOnWatch();
        if (onWatch == null) {
            manager.cancel(StatusNotifications.ID_VOICE);
            return;
        }
        Intent move = new Intent(context, MoveReceiver.class).putExtra(EXTRA_TO_PHONE, onWatch);
        Notification notification = new Notification.Builder(context, StatusNotifications.CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(onWatch ? R.string.voice_on_watch_now : R.string.voice_on_phone_now))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null,
                        context.getString(onWatch ? R.string.move_voice_to_phone : R.string.move_voice_to_watch),
                        PendingIntent.getBroadcast(context, 20, move,
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build())
                .build();
        manager.notify(StatusNotifications.ID_VOICE, notification);
    }

    /** "Move voice to phone / to watch" on the notification (from the phone or the watch). */
    public static class MoveReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RelayConnection.moveVoice(intent.getBooleanExtra(EXTRA_TO_PHONE, true))) {
                update(context);
            }
        }
    }
}
