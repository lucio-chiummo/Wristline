package app.wristline;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Publishes the callable contacts to the watch app (names and apps only, never numbers). */
final class WatchSync {
    private WatchSync() {
    }

    /** Off the main thread. Tells the watch whether relaying is on (for its tile's Pause/Resume). */
    static void publishStatus(Context context) {
        new Thread(() -> {
            try {
                PutDataMapRequest request = PutDataMapRequest.create(WatchProtocol.PATH_STATUS);
                request.getDataMap().putBoolean(WatchProtocol.KEY_RELAY_ON, Prefs.relayOn(context));
                Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent());
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "could not tell the watch whether relaying is on", e);
            }
        }, "watch-status").start();
    }

    /** Off the main thread. Publishes, or clears the watch's list when watch calling is off. */
    static void publish(Context context) {
        new Thread(() -> {
            try {
                if (!Prefs.watchCalling(context)) {
                    Wearable.getDataClient(context).deleteDataItems(new android.net.Uri.Builder()
                            .scheme("wear").path(WatchProtocol.PATH_CONTACTS).build());
                    return;
                }
                ArrayList<DataMap> contacts = new ArrayList<>();
                for (CallableContacts.Contact contact : ContactsReader.callable(context)) {
                    DataMap map = new DataMap();
                    map.putLong(WatchProtocol.KEY_ID, contact.id);
                    map.putString(WatchProtocol.KEY_NAME, contact.name);
                    map.putStringArrayList(WatchProtocol.KEY_APPS, new ArrayList<>(contact.callRows.keySet()));
                    contacts.add(map);
                }
                PutDataMapRequest request = PutDataMapRequest.create(WatchProtocol.PATH_CONTACTS);
                request.getDataMap().putDataMapArrayList(WatchProtocol.KEY_CONTACTS, contacts);
                request.getDataMap().putLongArray(WatchProtocol.KEY_RECENT, Prefs.recentCalls(context));
                request.getDataMap().putLongArray(WatchProtocol.KEY_FAVOURITES, Prefs.watchFavourites(context));
                ArrayList<DataMap> history = new ArrayList<>();
                for (CallHistory.Entry entry : Prefs.callHistory(context)) {
                    DataMap map = new DataMap();
                    map.putLong(WatchProtocol.KEY_TIME, entry.time);
                    map.putBoolean(WatchProtocol.KEY_OUTGOING, entry.outgoing);
                    map.putBoolean(WatchProtocol.KEY_MISSED, entry.missed);
                    map.putString(WatchProtocol.KEY_APP, entry.app);
                    map.putLong(WatchProtocol.KEY_ID, entry.contactId);
                    map.putString(WatchProtocol.KEY_NAME, entry.name);
                    history.add(map);
                }
                request.getDataMap().putDataMapArrayList(WatchProtocol.KEY_HISTORY, history);
                Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent());
                Log.i(RelayListener.TAG, "published " + contacts.size() + " contacts to the watch");
            } catch (RuntimeException e) {
                // No Wear OS / Play services: nothing to sync to.
                Log.w(RelayListener.TAG, "could not publish contacts to the watch", e);
            }
        }, "watch-sync").start();
    }

    /** Tells the watch app how a call it started is going. Off the main thread; fire and forget. */
    static void callState(Context context, String state, SupportedApp app, String name) {
        callState(context, state, -1, WatchProtocol.MUTE_NONE, app, name);
    }

    /** As above, with how long ago the call connected (ms, -1 unknown) and the mute state. */
    static void callState(Context context, String kind, long connectedMs, String mute, SupportedApp app, String name) {
        new Thread(() -> {
            try {
                byte[] payload = WatchProtocol.callStatePayload(WatchProtocol.callState(kind, connectedMs, mute),
                        app.packageName, name);
                for (com.google.android.gms.wearable.Node node : com.google.android.gms.tasks.Tasks.await(
                        Wearable.getNodeClient(context).getConnectedNodes())) {
                    Wearable.getMessageClient(context).sendMessage(node.getId(), WatchProtocol.PATH_CALL_STATE, payload);
                }
            } catch (Exception e) {
                Log.w(RelayListener.TAG, "could not tell the watch about the call", e);
            }
        }, "watch-call-state").start();
    }

    /** Records a relayed call for the watch's "Recent" list and republishes. Off the main thread. */
    static void recordCall(Context context, boolean outgoing, SupportedApp app, long contactId, String name, long time) {
        new Thread(() -> {
            long id = contactId;
            if (id < 0 && Prefs.watchCalling(context)) {
                // An incoming caller: callable back from the watch if they're a contact.
                for (CallableContacts.Contact contact : ContactsReader.callable(context)) {
                    if (contact.name.equals(name)) {
                        id = contact.id;
                        break;
                    }
                }
            }
            Prefs.addToCallHistory(context, new CallHistory.Entry(time, outgoing,
                    app.packageName, id, name));
            publish(context);
        }, "watch-call-history").start();
    }

    /** Blocking (Play services, up to a few seconds): is a watch connected, with Wristline on it? */
    static WatchStatus.Kind status(Context context) {
        int connected = -1;
        int withApp = -1;
        try {
            connected = Tasks.await(Wearable.getNodeClient(context).getConnectedNodes(), 5, TimeUnit.SECONDS).size();
            CapabilityInfo info = Tasks.await(Wearable.getCapabilityClient(context).getCapability(
                    WatchProtocol.CAPABILITY_WATCH_APP, CapabilityClient.FILTER_REACHABLE), 5, TimeUnit.SECONDS);
            withApp = info.getNodes().size();
        } catch (Exception e) {
            Log.w(RelayListener.TAG, "could not check the watch", e);
        }
        return WatchStatus.of(connected, withApp);
    }

    /** The diagnostic report waits this long for the watch, no longer. */
    static final int REPORT_TIMEOUT_S = 3;

    /** What the watch app published about its watch (Build facts only). */
    static final class WatchFacts {
        final String manufacturer;
        final String model;
        final int sdk;
        final String version;

        WatchFacts(String manufacturer, String model, int sdk, String version) {
            this.manufacturer = manufacturer;
            this.model = model;
            this.sdk = sdk;
            this.version = version;
        }
    }

    /** Blocking (Play services, up to {@link #REPORT_TIMEOUT_S} s): the watch app's facts, or null when it hasn't published any. */
    static WatchFacts watchFacts(Context context) {
        try {
            android.net.Uri uri = new android.net.Uri.Builder().scheme("wear").path(WatchProtocol.PATH_WATCH_INFO).build();
            com.google.android.gms.wearable.DataItemBuffer items = Tasks.await(
                    Wearable.getDataClient(context).getDataItems(uri), REPORT_TIMEOUT_S, TimeUnit.SECONDS);
            try {
                for (com.google.android.gms.wearable.DataItem item : items) {
                    DataMap map = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).getDataMap();
                    return new WatchFacts(map.getString(WatchProtocol.KEY_MANUFACTURER, "?"),
                            map.getString(WatchProtocol.KEY_MODEL, "?"), map.getInt(WatchProtocol.KEY_SDK),
                            map.getString(WatchProtocol.KEY_VERSION, "?"));
                }
            } finally {
                items.release();
            }
        } catch (Exception e) {
            Log.w(RelayListener.TAG, "could not read the watch's facts", e);
        }
        return null;
    }

    /** Blocking (up to {@link #REPORT_TIMEOUT_S} s): how many Wear devices are connected; never their names. Null when it can't be told. */
    static Integer connectedNodeCount(Context context) {
        try {
            return Tasks.await(Wearable.getNodeClient(context).getConnectedNodes(), REPORT_TIMEOUT_S, TimeUnit.SECONDS).size();
        } catch (Exception e) {
            return null;
        }
    }

    static int count(Context context) {
        List<CallableContacts.Contact> contacts = ContactsReader.callable(context);
        return contacts.size();
    }
}
