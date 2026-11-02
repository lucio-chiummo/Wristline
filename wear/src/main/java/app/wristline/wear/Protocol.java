package app.wristline.wear;

/**
 * What the phone and watch apps send each other over the Wear OS Data Layer. Keep in sync with
 * {@code app.wristline.WatchProtocol} in the phone app.
 */
final class Protocol {
    /** Data item the phone publishes: the callable contacts and the recently called ones. */
    static final String PATH_CONTACTS = "/wristline/contacts";
    /** Data item from the phone: whether relaying is on (missing with older phone apps). */
    static final String PATH_STATUS = "/wristline/status";
    static final String KEY_RELAY_ON = "relay_on";
    /** Message to the phone: pause ("0") or resume ("1") relaying. */
    static final String PATH_SET_RELAY = "/wristline/relay";
    /** Message the watch sends to start a call: {@link #callPayload}. */
    static final String PATH_CALL = "/wristline/call";
    /** Message the watch sends to hang up the call it started. */
    static final String PATH_HANGUP = "/wristline/hangup";
    /** Message the watch sends to toggle mute on the call it started. */
    static final String PATH_MUTE = "/wristline/mute";
    /**
     * Message the phone sends about a call started from the watch: "<state>|<app>|<name>", where
     * {@code <state>} is "<kind>;<ms since connected>;<mute>" ({@link CallState#parse}).
     */
    static final String PATH_CALL_STATE = "/wristline/callstate";
    static final String STATE_CALLING = "calling";
    static final String STATE_RINGING = "ringing";
    static final String STATE_ACTIVE = "active";
    static final String STATE_ENDED = "ended";
    static final String KEY_HISTORY = "history";
    static final String KEY_TIME = "time";
    static final String KEY_OUTGOING = "outgoing";
    /** Absent from older phone apps: reads as false. */
    static final String KEY_MISSED = "missed";
    static final String KEY_APP = "app";
    static final String KEY_CONTACTS = "contacts";
    static final String KEY_RECENT = "recent";
    /** The favourites the phone keeps as a backup (missing with older phone apps). */
    static final String KEY_FAVOURITES = "favourites";
    /** Message to the phone whenever favourites change: "<id>,<id>,…". */
    static final String PATH_FAVOURITES = "/wristline/favourites";
    static final String KEY_ID = "id";
    static final String KEY_NAME = "name";
    static final String KEY_APPS = "apps";

    static final String WHATSAPP = "com.whatsapp";
    static final String TELEGRAM = "org.telegram.messenger";

    static final String MUTE_OFF = "off";
    static final String MUTE_ON = "on";

    /** The state field of a call-state message. */
    static final class CallState {
        final String kind;
        /** How long ago the call connected when the phone sent this; -1 when unknown. */
        final long connectedMs;
        /** Whether the app has a mute button the watch can press. */
        final boolean canMute;
        final boolean muted;

        CallState(String kind, long connectedMs, boolean canMute, boolean muted) {
            this.kind = kind;
            this.connectedMs = connectedMs;
            this.canMute = canMute;
            this.muted = muted;
        }

        /** "<kind>;<ms>;<mute>"; a bare "<kind>" (older phone app) reads as no timer, no mute. */
        static CallState parse(String field) {
            String[] parts = (field == null ? "" : field).split(";", -1);
            long ms = -1;
            if (parts.length > 1 && !parts[1].isEmpty()) {
                try {
                    ms = Math.max(0, Long.parseLong(parts[1]));
                } catch (NumberFormatException e) {
                    ms = -1;
                }
            }
            String mute = parts.length > 2 ? parts[2] : "";
            boolean canMute = MUTE_OFF.equals(mute) || MUTE_ON.equals(mute);
            return new CallState(parts[0], ms, canMute, MUTE_ON.equals(mute));
        }
    }

    private Protocol() {
    }

    /** "<contact id>|<app package>" */
    static byte[] callPayload(long contactId, String app) {
        return (contactId + "|" + app).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    static String label(String app) {
        return WHATSAPP.equals(app) ? "WhatsApp" : TELEGRAM.equals(app) ? "Telegram"
                : "com.instagram.android".equals(app) ? "Instagram" : app;
    }
}
