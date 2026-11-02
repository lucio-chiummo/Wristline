package app.wristline.wear;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Talks to Wristline on the phone over the Wear OS Data Layer. Blocking: call off the main thread. */
final class PhoneLink {
    static final String TAG = "WristlineWatch";
    private static final long TIMEOUT_S = 10;

    /** What the phone published. */
    static final class Contacts {
        final List<WatchContact> all;
        final List<Long> recent;
        final List<HistoryEntry> history;
        /** The favourites backed up on the phone. */
        final List<Long> favourites;

        Contacts(List<WatchContact> all, List<Long> recent, List<HistoryEntry> history) {
            this(all, recent, history, new ArrayList<>());
        }

        Contacts(List<WatchContact> all, List<Long> recent, List<HistoryEntry> history, List<Long> favourites) {
            this.all = all;
            this.recent = recent;
            this.history = history;
            this.favourites = favourites;
        }
    }

    /** Sample contacts for the screenshot tests (no phone there); null in the app. */
    static volatile Contacts preview;

    private PhoneLink() {
    }

    /** The contacts the phone published, or an empty list when there are none (yet). */
    static Contacts contacts(Context context) {
        Contacts sample = preview;
        if (sample != null) {
            return sample;
        }
        List<WatchContact> all = new ArrayList<>();
        List<Long> recent = new ArrayList<>();
        List<HistoryEntry> history = new ArrayList<>();
        List<Long> favourites = new ArrayList<>();
        Uri uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(Protocol.PATH_CONTACTS).build();
        try {
            DataItemBuffer items = Tasks.await(Wearable.getDataClient(context).getDataItems(uri), TIMEOUT_S, TimeUnit.SECONDS);
            try {
                for (DataItem item : items) {
                    DataMap map = DataMapItem.fromDataItem(item).getDataMap();
                    ArrayList<DataMap> contacts = map.getDataMapArrayList(Protocol.KEY_CONTACTS);
                    if (contacts != null) {
                        for (DataMap c : contacts) {
                            ArrayList<String> apps = c.getStringArrayList(Protocol.KEY_APPS);
                            all.add(new WatchContact(c.getLong(Protocol.KEY_ID), c.getString(Protocol.KEY_NAME, ""),
                                    apps == null ? new ArrayList<>() : apps));
                        }
                    }
                    ArrayList<DataMap> calls = map.getDataMapArrayList(Protocol.KEY_HISTORY);
                    if (calls != null) {
                        for (DataMap c : calls) {
                            history.add(new HistoryEntry(c.getLong(Protocol.KEY_TIME), c.getBoolean(Protocol.KEY_OUTGOING),
                                    c.getString(Protocol.KEY_APP, ""), c.getLong(Protocol.KEY_ID, -1),
                                    c.getString(Protocol.KEY_NAME, ""), c.getBoolean(Protocol.KEY_MISSED, false)));
                        }
                    }
                    long[] starred = map.getLongArray(Protocol.KEY_FAVOURITES);
                    if (starred != null) {
                        for (long id : starred) {
                            favourites.add(id);
                        }
                    }
                    long[] ids = map.getLongArray(Protocol.KEY_RECENT);
                    if (ids != null) {
                        for (long id : ids) {
                            recent.add(id);
                        }
                    }
                }
            } finally {
                items.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "could not read contacts from the phone", e);
        }
        return new Contacts(all, recent, history, favourites);
    }

    /** Backs up the favourites on the phone (off the main thread). */
    static void backUpFavourites(Context context, java.util.Set<Long> ids) {
        StringBuilder payload = new StringBuilder();
        for (long id : ids) {
            payload.append(payload.length() > 0 ? "," : "").append(id);
        }
        new Thread(() -> send(context, Protocol.PATH_FAVOURITES,
                payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), "favourites-backup").start();
    }

    /** Asks the phone to hang up the call started from the watch. */
    /** Whether the phone relays calls to the watch; null when it hasn't said (older phone app). */
    static Boolean relayOn(Context context) {
        Uri uri = new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(Protocol.PATH_STATUS).build();
        try {
            DataItemBuffer items = Tasks.await(Wearable.getDataClient(context).getDataItems(uri), TIMEOUT_S, TimeUnit.SECONDS);
            try {
                for (DataItem item : items) {
                    DataMap map = DataMapItem.fromDataItem(item).getDataMap();
                    if (map.containsKey(Protocol.KEY_RELAY_ON)) {
                        return map.getBoolean(Protocol.KEY_RELAY_ON);
                    }
                }
            } finally {
                items.release();
            }
        } catch (Exception e) {
            Log.w(TAG, "could not read whether relaying is on", e);
        }
        return null;
    }

    /** Pauses or resumes relaying on the phone; false when the phone can't be reached. */
    static boolean setRelay(Context context, boolean on) {
        return send(context, Protocol.PATH_SET_RELAY, (on ? "1" : "0").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static void hangUp(Context context) {
        send(context, Protocol.PATH_HANGUP, new byte[0]);
    }

    /** Asks the phone to press the app's mute button on the call the watch started. */
    static void toggleMute(Context context) {
        send(context, Protocol.PATH_MUTE, new byte[0]);
    }

    private static boolean send(Context context, String path, byte[] payload) {
        try {
            List<Node> nodes = Tasks.await(Wearable.getNodeClient(context).getConnectedNodes(), TIMEOUT_S, TimeUnit.SECONDS);
            for (Node node : nodes) {
                Tasks.await(Wearable.getMessageClient(context).sendMessage(node.getId(), path, payload),
                        TIMEOUT_S, TimeUnit.SECONDS);
            }
            return !nodes.isEmpty();
        } catch (Exception e) {
            Log.w(TAG, "could not reach the phone", e);
            return false;
        }
    }

    /** Asks the phone to call; false when no phone is connected or the message couldn't be sent. */
    static boolean call(Context context, long contactId, String app) {
        return send(context, Protocol.PATH_CALL, Protocol.callPayload(contactId, app));
    }
}
