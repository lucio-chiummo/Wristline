package app.wristline.wear;

import android.app.NotificationManager;
import android.content.Context;
import android.util.Log;

import androidx.wear.phone.interactions.notifications.BridgingConfig;
import androidx.wear.phone.interactions.notifications.BridgingManager;

/**
 * The phone's ring notification is the backup for watches without this app. Here the incoming
 * screen already rings, so the Wear OS bridge must not show that notification as well. Every other
 * Wristline notification keeps bridging. With this app's notifications turned off the incoming
 * screen can't show, so then the phone's ring notification bridges again.
 */
final class RingBridging {
    private RingBridging() {}

    /** Sets {@link #config} on the bridge; the setting persists, so repeating is cheap. */
    static void exclude(Context context) {
        try {
            BridgingManager.fromContext(context).setConfig(config(context));
        } catch (RuntimeException e) {
            // No Wear OS bridge service on this watch: the ring notification can't arrive anyway.
            Log.w(PhoneLink.TAG, "could not exclude the ring notification from bridging", e);
        }
    }

    /** Bridging on, without the ring notification's tag only while the incoming screen can show. */
    static BridgingConfig config(Context context) {
        BridgingConfig.Builder builder = new BridgingConfig.Builder(context, true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null && manager.areNotificationsEnabled()) {
            builder.addExcludedTag(Protocol.RING_BRIDGE_TAG);
        }
        return builder.build();
    }
}
