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
        /** Build.DISPLAY: the maker's OS build (HyperOS, ColorOS, One UI…). */
        String osBuild = "";
        /** Wear OS check: ready, no_watch, app_missing or unknown. */
        String watch = "unknown";
        /** Installed watch companion apps, already described by {@link Companions#describe}. */
        List<String> companions = new ArrayList<>();
        /** A Bluetooth call-audio (SCO) device is available right now; null when it couldn't be read. */
        Boolean bluetoothCallAudio;
        /** Phone settings that decide whether calls reach Wristline, name → state. Shown in order. */
        java.util.Map<String, String> phoneSettings = new java.util.LinkedHashMap<>();
        List<AppState> apps = new ArrayList<>();
        List<EventLog.Entry> events = new ArrayList<>();
    }

    static final String EVENTS_DOC = "https://github.com/lucio-chiummo/Wristline/blob/main/docs/DIAGNOSTICS.md";

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
        out.append("Phone OS build: ").append(f.osBuild).append('\n');
        out.append("Watch: ").append(f.watch)
                .append(" · Companion apps: ").append(f.companions.isEmpty() ? "none found" : String.join(", ", f.companions))
                .append('\n');
        out.append("Bluetooth call audio: ")
                .append(f.bluetoothCallAudio == null ? "unknown" : f.bluetoothCallAudio ? "available now" : "NOT available now")
                .append('\n');
        if (!f.phoneSettings.isEmpty()) {
            List<String> settings = new ArrayList<>();
            for (java.util.Map.Entry<String, String> e : f.phoneSettings.entrySet()) {
                settings.add(e.getKey() + " " + e.getValue());
            }
            out.append("Phone settings: ").append(String.join(" · ", settings)).append('\n');
        }
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
        out.append('\n').append("Recent events (newest first; codes explained in ")
                .append(EVENTS_DOC).append("):").append('\n');
        if (f.events.isEmpty()) {
            out.append("none").append('\n');
        }
        for (EventLog.Entry e : f.events) {
            out.append(EventLog.format(e)).append('\n');
        }
        return out.toString();
    }
}
