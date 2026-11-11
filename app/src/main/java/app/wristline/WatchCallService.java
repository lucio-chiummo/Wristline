package app.wristline;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/** Receives "call this contact with this app" from the watch app. */
public class WatchCallService extends WearableListenerService {
    @Override
    public void onMessageReceived(MessageEvent event) {
        ListenerRebind.ifNeeded(this, ListenerRebind.WATCH, null);
        if (WatchProtocol.PATH_HANGUP.equals(event.getPath())) {
            RelayListener.hangUpFromWatch();
            return;
        }
        if (WatchProtocol.PATH_MUTE.equals(event.getPath())) {
            RelayListener.muteFromWatch();
            return;
        }
        if (WatchProtocol.PATH_FAVOURITES.equals(event.getPath())) {
            // Backup only: not republished, the watch already has them.
            Prefs.setWatchFavourites(this, WatchProtocol.parseIds(event.getData()));
            return;
        }
        if (WatchProtocol.PATH_SET_RELAY.equals(event.getPath())) {
            Relaying.set(this, "1".equals(new String(event.getData(), java.nio.charset.StandardCharsets.UTF_8)));
            return;
        }
        if (!WatchProtocol.PATH_CALL.equals(event.getPath())) {
            return;
        }
        WatchProtocol.CallRequest request = WatchProtocol.parseCall(event.getData());
        if (request == null || !Prefs.watchCalling(this)) {
            Log.w(RelayListener.TAG, "ignoring call request from the watch");
            return;
        }
        OutgoingCalls.start(this, request.contactId, request.app);
    }
}
