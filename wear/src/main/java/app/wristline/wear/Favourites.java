package app.wristline.wear;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Starred contacts, kept on the watch. */
final class Favourites {
    private static final String FILE = "favourites";
    private static final String KEY = "ids";

    private Favourites() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Set<Long> ids(Context context) {
        Set<Long> ids = new HashSet<>();
        for (String id : prefs(context).getStringSet(KEY, new HashSet<>())) {
            ids.add(Long.parseLong(id));
        }
        return ids;
    }

    /** Stars or unstars; returns whether it's now starred. */
    static boolean toggle(Context context, long id) {
        Set<String> ids = new HashSet<>(prefs(context).getStringSet(KEY, new HashSet<>()));
        boolean starred = !ids.remove(String.valueOf(id));
        if (starred) {
            ids.add(String.valueOf(id));
        }
        prefs(context).edit().putStringSet(KEY, ids).apply();
        CallStateService.refreshTile(context);
        PhoneLink.backUpFavourites(context, ids(context));
        return starred;
    }

    /**
     * A fresh watch app (reinstalled, or a new watch) gets the favourites the phone backed up. Only
     * when it has none of its own; unstarring everyone also empties the phone's copy.
     */
    static void restoreIfEmpty(Context context, java.util.List<Long> backedUp) {
        if (backedUp.isEmpty() || !prefs(context).getStringSet(KEY, new HashSet<>()).isEmpty()) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (long id : backedUp) {
            ids.add(String.valueOf(id));
        }
        prefs(context).edit().putStringSet(KEY, ids).apply();
        CallStateService.refreshTile(context);
    }
}
