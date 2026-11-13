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
            Diagnostics.record(this, EventLog.WATCH_MESSAGE, RelayListener.currentApp,
                    "hangup" + (RelayConnection.current == null ? ", no call on the phone" : ""));
            RelayListener.hangUpFromWatch();
            return;
        }
        if (WatchProtocol.PATH_MUTE.equals(event.getPath())) {
            Diagnostics.record(this, EventLog.WATCH_MESSAGE, RelayListener.currentApp, "mute");
            RelayListener.muteFromWatch();
            return;
        }
        if (WatchProtocol.PATH_FAVOURITES.equals(event.getPath())) {
            // Backup only: not republished, the watch already has them.
            Prefs.setWatchFavourites(this, WatchProtocol.parseIds(event.getData()));
            return;
        }
        if (WatchProtocol.PATH_SET_RELAY.equals(event.getPath())) {
            boolean on = "1".equals(new String(event.getData(), java.nio.charset.StandardCharsets.UTF_8));
            Diagnostics.record(this, EventLog.WATCH_MESSAGE, "", on ? "relay on" : "relay off");
            Relaying.set(this, on);
            return;
        }
        if (!WatchProtocol.PATH_CALL.equals(event.getPath())) {
            return;
        }
        WatchProtocol.CallRequest request = WatchProtocol.parseCall(event.getData());
        if (request == null || !Prefs.watchCalling(this)) {
            Log.w(RelayListener.TAG, "ignoring call request from the watch");
            Diagnostics.record(this, EventLog.WATCH_MESSAGE, "",
                    request == null ? "call ignored, unreadable" : "call ignored, calling from the watch is off");
            return;
        }
        OutgoingCalls.start(this, request.contactId, request.app);
    }
}
