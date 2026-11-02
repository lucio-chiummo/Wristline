package app.wristline;

import android.content.ComponentName;
import android.content.Context;
import android.service.quicksettings.TileService;
import android.util.Log;

/**
 * Pausing and resuming relaying, from wherever: Home's main switch, the Quick Settings tile or the
 * watch. Keeps all three showing the same state.
 */
final class Relaying {
    private Relaying() {
    }

    static void set(Context context, boolean on) {
        if (Prefs.relayOn(context) == on) {
            return;
        }
        Prefs.setRelayOn(context, on);
        Log.i(RelayListener.TAG, on ? "relaying resumed" : "relaying paused");
        Diagnostics.record(context, on ? EventLog.RELAY_RESUMED : EventLog.RELAY_PAUSED, "", "");
        changed(context);
    }

    /** Redraws the Quick Settings tile and tells the watch. */
    static void changed(Context context) {
        try {
            TileService.requestListeningState(context, new ComponentName(context, RelayTileService.class));
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not refresh the Quick Settings tile", e);
        }
        WatchSync.publishStatus(context);
        Runnable home = onChange;
        if (home != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(home);
        }
    }

    /** Home, while it's shown: redraw when relaying is paused or resumed from elsewhere. */
    static volatile Runnable onChange;
}
