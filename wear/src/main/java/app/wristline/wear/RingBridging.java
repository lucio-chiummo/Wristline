package app.wristline.wear;

import android.content.Context;
import android.util.Log;

import androidx.wear.phone.interactions.notifications.BridgingConfig;
import androidx.wear.phone.interactions.notifications.BridgingManager;

/**
 * The phone's ring notification is the backup for watches without this app. Here the incoming
 * screen already rings, so the Wear OS bridge must not show that notification as well. Every other
 * Wristline notification keeps bridging.
 */
final class RingBridging {
    private RingBridging() {}

    /** Excludes the ring notification's tag from bridging; the setting persists, so repeating is cheap. */
    static void exclude(Context context) {
        try {
            BridgingManager.fromContext(context).setConfig(
                    new BridgingConfig.Builder(context, true).addExcludedTag(Protocol.RING_BRIDGE_TAG).build());
        } catch (RuntimeException e) {
            // No Wear OS bridge service on this watch: the ring notification can't arrive anyway.
            Log.w(PhoneLink.TAG, "could not exclude the ring notification from bridging", e);
        }
    }
}
