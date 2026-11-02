package app.wristline;

import java.nio.charset.StandardCharsets;

/**
 * What the phone and watch apps send each other over the Wear OS Data Layer. Keep in sync with
 * {@code app.wristline.wear.Protocol} in the watch app.
 */
final class WatchProtocol {
    /** Data item the phone publishes: the callable contacts and the recently called ones. */
    static final String PATH_CONTACTS = "/wristline/contacts";
    /** Data item the phone publishes: whether relaying is on (always published, unlike contacts). */
    static final String PATH_STATUS = "/wristline/status";
    static final String KEY_RELAY_ON = "relay_on";
    /** Message the watch sends to pause ("0") or resume ("1") relaying. */
    static final String PATH_SET_RELAY = "/wristline/relay";
    /** Declared by the watch app (wear.xml): tells the phone it's installed on a connected watch. */
    static final String CAPABILITY_WATCH_APP = "wristline_watch_app";
    /** Message the watch sends to start a call: "<contact id>|<app package>". */
    static final String PATH_CALL = "/wristline/call";
    /** Message the watch sends to hang up the call it started. */
    static final String PATH_HANGUP = "/wristline/hangup";
    /** Message the watch sends to toggle mute on the call it started (the app's own mute action). */
    static final String PATH_MUTE = "/wristline/mute";
    /**
     * Message the phone sends about a call started from the watch: "<state>|<app>|<name>", where
     * {@code <state>} is {@link #callState}'s "<kind>;<ms since connected>;<mute>".
     */
    static final String PATH_CALL_STATE = "/wristline/callstate";
    static final String STATE_CALLING = "calling";
    /** The app shows its in-call notification but the other side hasn't answered yet. */
    static final String STATE_RINGING = "ringing";
    static final String STATE_ACTIVE = "active";
    static final String STATE_ENDED = "ended";
    static final String KEY_HISTORY = "history";
    static final String KEY_TIME = "time";
    static final String KEY_OUTGOING = "outgoing";
    static final String KEY_MISSED = "missed";
    static final String KEY_APP = "app";
    static final String KEY_CONTACTS = "contacts";
    static final String KEY_RECENT = "recent";
    /** The watch's favourites, kept on the phone too so a reinstalled watch app gets them back. */
    static final String KEY_FAVOURITES = "favourites";
    /** Message the watch sends whenever its favourites change: "<id>,<id>,…" (empty: none). */
    static final String PATH_FAVOURITES = "/wristline/favourites";
    static final String KEY_ID = "id";
    static final String KEY_NAME = "name";
    static final String KEY_APPS = "apps";
    static final int RECENT_MAX = 5;

    /** No mute action on the app's in-call notification. */
    static final String MUTE_NONE = "";
    static final String MUTE_OFF = "off";
    static final String MUTE_ON = "on";

    /**
     * The state field of a call-state message: "<kind>;<ms since the call connected, or empty>;<mute>".
     * {@code connectedMs} is ignored unless {@code kind} is {@link #STATE_ACTIVE}.
     */
    static String callState(String kind, long connectedMs, String mute) {
        String since = STATE_ACTIVE.equals(kind) && connectedMs >= 0 ? String.valueOf(connectedMs) : "";
        return kind + ";" + since + ";" + (mute == null ? MUTE_NONE : mute);
    }

    /** The full call-state message payload. */
    static byte[] callStatePayload(String state, String app, String name) {
        return (state + "|" + app + "|" + name).getBytes(StandardCharsets.UTF_8);
    }

    /** A call the watch asked for. */
    static final class CallRequest {
        final long contactId;
        final SupportedApp app;

        CallRequest(long contactId, SupportedApp app) {
            this.contactId = contactId;
            this.app = app;
        }
    }

    private WatchProtocol() {
    }

    /** Null for anything that isn't "<id>|<package of an app that can start calls>". */
    static CallRequest parseCall(byte[] payload) {
        if (payload == null) {
            return null;
        }
        String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", 2);
        if (parts.length != 2) {
            return null;
        }
        SupportedApp app = SupportedApp.forPackage(parts[1]);
        if (app == null || app.callMimeType == null) {
            return null;
        }
        try {
            return new CallRequest(Long.parseLong(parts[0]), app);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code id} first, then the others in order without it, at most {@code max}. */
    static long[] pushRecent(long[] recent, long id, int max) {
        long[] out = new long[Math.min(max, recent.length + 1)];
        int n = 0;
        out[n++] = id;
        for (long other : recent) {
            if (other != id && n < out.length) {
                out[n++] = other;
            }
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    /** "3,7,12" → {3, 7, 12}; anything that isn't a number is skipped. */
    static long[] parseIds(byte[] payload) {
        String text = payload == null ? "" : new String(payload, StandardCharsets.UTF_8).trim();
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (String part : text.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException e) {
                // Skip.
            }
        }
        long[] out = new long[ids.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = ids.get(i);
        }
        return out;
    }
}
