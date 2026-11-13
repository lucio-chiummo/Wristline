package app.wristline;

import java.util.ArrayList;
import java.util.List;

/** The plain-text report behind "Share diagnostic report": setup state plus recent events. */
final class DiagnosticReport {
    /** One calling app's state. */
    static final class AppState {
        final String packageName;
        /** Installed version, or null when the app isn't installed. */
        final String version;
        final boolean switchOn;
        final String voice;

        AppState(String packageName, String version, boolean switchOn, String voice) {
            this.packageName = packageName;
            this.version = version;
            this.switchOn = switchOn;
            this.voice = voice;
        }
    }

    /** Everything the report says, gathered by {@link Diagnostics#collect}. */
    static final class Facts {
        String appVersion = "";
        String device = "";
        int sdk;
        String securityPatch = "";
        boolean relayOn;
        boolean watchCalling;
        boolean listenerConnected;
        boolean keyPresent;
        List<String> missingPermissions = new ArrayList<>();
        List<AppState> apps = new ArrayList<>();
        List<EventLog.Entry> events = new ArrayList<>();
    }

    private DiagnosticReport() {
    }

    static String build(Facts f) {
        StringBuilder out = new StringBuilder();
        out.append("Wristline ").append(f.appVersion).append('\n');
        out.append("Check before posting publicly: this report shows when recent calls happened.").append('\n');
        out.append("Device: ").append(f.device).append(", Android SDK ").append(f.sdk)
                .append(", patch ").append(f.securityPatch).append('\n');
        out.append("Relaying: ").append(f.relayOn ? "on" : "paused")
                .append(" · Notification listener: ").append(f.listenerConnected ? "connected" : "NOT connected")
                .append(" · Calling from the watch: ").append(f.watchCalling ? "on" : "off").append('\n');
        out.append("adb key present: ").append(f.keyPresent ? "yes" : "no").append('\n');
        out.append("Missing permissions: ")
                .append(f.missingPermissions.isEmpty() ? "none" : String.join(", ", f.missingPermissions)).append('\n');
        out.append('\n').append("Apps:").append('\n');
        for (AppState app : f.apps) {
            out.append("- ").append(app.packageName).append(": ");
            if (app.version == null) {
                out.append("not installed");
            } else {
                out.append(app.version).append(", switch ").append(app.switchOn ? "on" : "off")
                        .append(", voice ").append(app.voice);
            }
            out.append('\n');
        }
        out.append('\n').append("Recent events (newest first):").append('\n');
        if (f.events.isEmpty()) {
            out.append("none").append('\n');
        }
        for (EventLog.Entry e : f.events) {
            out.append(EventLog.format(e)).append('\n');
        }
        return out.toString();
    }
}
