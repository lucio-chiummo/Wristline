package app.wristline;

import android.content.Context;
import android.content.SharedPreferences;

/** The user's switches and each app's full-mode state. */
final class Prefs {
    private static final String FILE = "wristline";
    private static final String KEY_RELAY_ON = "relay_on";
    private static final String KEY_MIGRATED = "migrated_1_1";
    private static final String LEGACY_FULL = "full_mode_enabled";
    private static final String LEGACY_BROKEN = "full_mode_broken";

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean relayOn(Context context) {
        return prefs(context).getBoolean(KEY_RELAY_ON, true);
    }

    static void setRelayOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_RELAY_ON, on).apply();
    }

    static boolean appOn(Context context, SupportedApp app) {
        return prefs(context).getBoolean("app_on_" + app.packageName, app.defaultOn);
    }

    static void setAppOn(Context context, SupportedApp app, boolean on) {
        prefs(context).edit().putBoolean("app_on_" + app.packageName, on).apply();
    }

    static boolean fullModeOn(Context context, SupportedApp app) {
        return prefs(context).getBoolean("full_" + app.packageName, false);
    }

    /** Turning full mode on or off also clears the broken flag. */
    static void setFullModeOn(Context context, SupportedApp app, boolean on) {
        prefs(context).edit()
                .putBoolean("full_" + app.packageName, on)
                .putBoolean("broken_" + app.packageName, false)
                .apply();
    }

    /** The app's first-install time when voice through the watch was set up; 0 when unknown. */
    static long installTime(Context context, SupportedApp app) {
        return prefs(context).getLong("installed_" + app.packageName, 0);
    }

    static void setInstallTime(Context context, SupportedApp app, long time) {
        prefs(context).edit().putLong("installed_" + app.packageName, time).apply();
    }

    static boolean broken(Context context, SupportedApp app) {
        return prefs(context).getBoolean("broken_" + app.packageName, false);
    }

    static void setBroken(Context context, SupportedApp app, boolean broken) {
        prefs(context).edit().putBoolean("broken_" + app.packageName, broken).apply();
    }

    /** The user agreed that Wristline may change this app's setting again by itself (repair). */
    static boolean consent(Context context, SupportedApp app) {
        return prefs(context).getBoolean("consent_" + app.packageName, false);
    }

    static void setConsent(Context context, SupportedApp app, boolean consent) {
        prefs(context).edit().putBoolean("consent_" + app.packageName, consent).apply();
    }

    /** Calls the phone's Do Not Disturb silences don't ring the watch either (on unless turned off). */
    static boolean followDnd(Context context) {
        return prefs(context).getBoolean("follow_dnd", true);
    }

    static void setFollowDnd(Context context, boolean on) {
        prefs(context).edit().putBoolean("follow_dnd", on).apply();
    }

    /** Off unless asked: don't ring the watch while the phone is unlocked and in use. */
    static boolean notWhileInUse(Context context) {
        return prefs(context).getBoolean("not_while_in_use", false);
    }

    static void setNotWhileInUse(Context context, boolean on) {
        prefs(context).edit().putBoolean("not_while_in_use", on).apply();
    }

    /** The watch's favourites (contact ids), backed up from the watch. */
    static long[] watchFavourites(Context context) {
        return WatchProtocol.parseIds(prefs(context).getString("watch_favourites", "")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static void setWatchFavourites(Context context, long[] ids) {
        StringBuilder out = new StringBuilder();
        for (long id : ids) {
            out.append(out.length() > 0 ? "," : "").append(id);
        }
        prefs(context).edit().putString("watch_favourites", out.toString()).apply();
    }

    static boolean watchCalling(Context context) {
        return prefs(context).getBoolean("watch_calling", false);
    }

    static void setWatchCalling(Context context, boolean on) {
        prefs(context).edit().putBoolean("watch_calling", on).apply();
    }

    /** When the listener was last seen unbound, 0 when it is not known to be. */
    static long listenerUnboundSince(Context context) {
        return prefs(context).getLong("listener_unbound_since", 0);
    }

    static void setListenerUnboundSince(Context context, long time) {
        prefs(context).edit().putLong("listener_unbound_since", time).apply();
    }

    /** The last time the listener was known to be connected: the unbound gap starts here when nothing says otherwise. */
    static long listenerSeenAt(Context context) {
        return prefs(context).getLong("listener_seen_at", 0);
    }

    static void setListenerSeenAt(Context context, long time) {
        prefs(context).edit().putLong("listener_seen_at", time).apply();
    }

    /** When a rebind check was last written to the event log, so the periodic one doesn't flood it. */
    static long lastRebindRecorded(Context context) {
        return prefs(context).getLong("last_rebind_recorded", 0);
    }

    static void setLastRebindRecorded(Context context, long time) {
        prefs(context).edit().putLong("last_rebind_recorded", time).apply();
    }

    static long[] recentCalls(Context context) {
        String stored = prefs(context).getString("recent_calls", "");
        if (stored.isEmpty()) {
            return new long[0];
        }
        String[] parts = stored.split(",");
        long[] ids = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ids[i] = Long.parseLong(parts[i]);
        }
        return ids;
    }

    static void addRecentCall(Context context, long contactId) {
        long[] recent = WatchProtocol.pushRecent(recentCalls(context), contactId, WatchProtocol.RECENT_MAX);
        StringBuilder out = new StringBuilder();
        for (long id : recent) {
            out.append(out.length() > 0 ? "," : "").append(id);
        }
        prefs(context).edit().putString("recent_calls", out.toString()).apply();
    }

    static java.util.List<CallHistory.Entry> callHistory(Context context) {
        return CallHistory.decode(prefs(context).getString("call_history", ""));
    }

    static synchronized void clearCallHistory(Context context) {
        prefs(context).edit().remove("call_history").apply();
    }

    static synchronized void markMissed(Context context, long time) {
        prefs(context).edit().putString("call_history",
                CallHistory.encode(CallHistory.markMissed(callHistory(context), time))).apply();
    }

    static synchronized void addToCallHistory(Context context, CallHistory.Entry entry) {
        prefs(context).edit().putString("call_history",
                CallHistory.encode(CallHistory.push(callHistory(context), entry, CallHistory.MAX))).apply();
    }

    static TestCall.Result testCallResult(Context context) {
        try {
            return TestCall.Result.valueOf(prefs(context).getString("test_call_result", "NONE"));
        } catch (IllegalArgumentException e) {
            return TestCall.Result.NONE;
        }
    }

    static void setTestCallResult(Context context, TestCall.Result result) {
        prefs(context).edit().putString("test_call_result", result.name()).apply();
    }

    static java.util.List<EventLog.Entry> events(Context context) {
        return EventLog.decode(prefs(context).getString("events", ""));
    }

    static synchronized void addEvent(Context context, EventLog.Entry entry) {
        prefs(context).edit().putString("events",
                EventLog.encode(EventLog.push(events(context), entry, EventLog.MAX))).apply();
    }

    /** True the first time for this app version: tell the user once that its calls can't be read. */
    static synchronized boolean markUnreadableNotified(Context context, SupportedApp app, String version) {
        String key = "unreadable_" + app.packageName;
        if (version.equals(prefs(context).getString(key, null))) {
            return false;
        }
        prefs(context).edit().putString(key, version).apply();
        return true;
    }

    static boolean askedNotifications(Context context) {
        return prefs(context).getBoolean("asked_notifications", false);
    }

    static void setAskedNotifications(Context context) {
        prefs(context).edit().putBoolean("asked_notifications", true).apply();
    }

    /** 1.0 kept one full-mode state, which was WhatsApp's. Copies it once so full mode survives the update. */
    static void migrate(Context context) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(KEY_MIGRATED, false)) {
            return;
        }
        String pkg = SupportedApp.WHATSAPP.packageName;
        prefs.edit()
                .putBoolean("full_" + pkg, prefs.getBoolean(LEGACY_FULL, false))
                .putBoolean("broken_" + pkg, prefs.getBoolean(LEGACY_BROKEN, false))
                .remove(LEGACY_FULL)
                .remove(LEGACY_BROKEN)
                .putBoolean(KEY_MIGRATED, true)
                .apply();
    }
}
