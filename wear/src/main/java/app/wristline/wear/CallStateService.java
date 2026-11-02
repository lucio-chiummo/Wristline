package app.wristline.wear;

import androidx.wear.tiles.TileService;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;

/**
 * The phone's updates about a call started from the watch ("<state>|<app>|<name>"), and new
 * contacts/recent calls (refreshes the tile).
 */
public class CallStateService extends WearableListenerService {
    @Override
    public void onDataChanged(DataEventBuffer events) {
        for (DataEvent event : events) {
            String path = event.getDataItem().getUri().getPath();
            if (Protocol.PATH_CONTACTS.equals(path) || Protocol.PATH_STATUS.equals(path)) {
                refreshTile(this);
                return;
            }
        }
    }

    /** Asks the system to redraw Wristline's tile and complication (new favourites, contacts or recent calls). */
    static void refreshTile(android.content.Context context) {
        try {
            TileService.getUpdater(context).requestUpdate(RecentTileService.class);
        } catch (RuntimeException e) {
            android.util.Log.w(PhoneLink.TAG, "could not refresh the tile", e);
        }
        QuickCallComplicationService.refresh(context);
    }

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (!Protocol.PATH_CALL_STATE.equals(event.getPath())) {
            return;
        }
        String[] parts = new String(event.getData(), StandardCharsets.UTF_8).split("\\|", 3);
        if (parts.length != 3) {
            return;
        }
        InCallActivity screen = InCallActivity.current;
        if (screen != null) {
            screen.onState(Protocol.CallState.parse(parts[0]));
        }
    }
}
