package app.wristline;

/**
 * Short names for android.telecom.Connection states and DisconnectCause codes in the diagnostic
 * report. Values repeated here so tests run without Android.
 */
final class TelecomNames {
    private static final String[] STATES = {
            "initializing", "new", "ringing", "dialing", "active", "holding", "disconnected", "pulling"};
    private static final String[] CAUSES = {
            "unknown", "error", "local", "remote", "canceled", "missed", "rejected", "busy",
            "restricted", "other", "connection_manager_not_supported", "answered_elsewhere", "call_pulled"};

    private TelecomNames() {
    }

    static String state(int state) {
        return state >= 0 && state < STATES.length ? STATES[state] : "state(" + state + ")";
    }

    static String cause(int code) {
        return code >= 0 && code < CAUSES.length ? CAUSES[code] : "cause(" + code + ")";
    }
}
