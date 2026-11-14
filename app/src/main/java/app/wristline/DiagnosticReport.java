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
        String brand = "";
        /** Android version name, e.g. "17". */
        String release = "";
        /** The OEM's build name (HyperOS, OriginOS, ...). */
        String display = "";
        /** Language tag: button-label matching depends on it. */
        String locale = "";
        /** A Bluetooth call-audio output (SCO or LE headset) exists; null when it can't be told. */
        Boolean bluetoothCallAudio;
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
        out.append("Device: ").append(f.device).append(" (").append(f.brand).append("), Android ")
                .append(f.release).append(" (").append(f.display).append("), SDK ").append(f.sdk)
                .append(", patch ").append(f.securityPatch).append(", locale ").append(f.locale).append('\n');
        out.append("Relaying: ").append(f.relayOn ? "on" : "paused")
                .append(" · Notification listener: ").append(f.listenerConnected ? "connected" : "NOT connected")
                .append(" · Calling from the watch: ").append(f.watchCalling ? "on" : "off").append('\n');
        out.append("adb key present: ").append(f.keyPresent ? "yes" : "no").append('\n');
        out.append("Bluetooth call audio device: ").append(f.bluetoothCallAudio == null ? "unknown"
                : f.bluetoothCallAudio ? "yes" : "no").append('\n');
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
