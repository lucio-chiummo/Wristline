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
        /** The watch is rung with Wristline's own notification instead of a phone call. */
        boolean ringByNotification;
        boolean watchCalling;
        boolean listenerConnected;
        boolean keyPresent;
        List<String> missingPermissions = new ArrayList<>();
        List<AppState> apps = new ArrayList<>();
        List<EventLog.Entry> events = new ArrayList<>();
        /** Null when the watch app hasn't published its facts. */
        WatchSync.WatchFacts watch;
        /** Null when it can't be told (no Play services). */
        volatile Integer connectedWatches;
        String defaultDialer = "unknown";
        List<CallBridges.Bridge> bridges = new ArrayList<>();
        List<String> companions = new ArrayList<>();
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
        out.append("Watch: ");
        if (f.watch == null) {
            out.append("watch app not reporting");
        } else {
            out.append(f.watch.manufacturer).append(' ').append(f.watch.model).append(", SDK ").append(f.watch.sdk)
                    .append(", Wristline watch app ").append(f.watch.version);
        }
        out.append(" · connected watches: ").append(f.connectedWatches == null ? "unknown" : f.connectedWatches).append('\n');
        out.append("Relaying: ").append(f.relayOn ? "on" : "paused")
                .append(" · Notification listener: ").append(f.listenerConnected ? "connected" : "NOT connected")
                .append(" · Calling from the watch: ").append(f.watchCalling ? "on" : "off").append('\n');
        out.append("Ring by notification: ").append(f.ringByNotification ? "on" : "off").append('\n');
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
        out.append('\n').append("Default dialer: ").append(f.defaultDialer).append('\n');
        out.append("Call bridges (InCallService):");
        if (f.bridges.isEmpty()) {
            out.append(" none");
        }
        out.append('\n');
        for (int i = 0; i < f.bridges.size() && i < CallBridges.MAX_LISTED; i++) {
            CallBridges.Bridge b = f.bridges.get(i);
            out.append("- ").append(b.packageName).append(' ').append(b.version)
                    .append(", self-managed ").append(b.selfManaged ? "yes" : "no")
                    .append(", ui ").append(b.ui ? "yes" : "no").append('\n');
        }
        if (f.bridges.size() > CallBridges.MAX_LISTED) {
            out.append("- … and ").append(f.bridges.size() - CallBridges.MAX_LISTED).append(" more").append('\n');
        }
        out.append("Watch companion apps:");
        if (f.companions.isEmpty()) {
            out.append(" none");
        }
        out.append('\n');
        for (String companion : f.companions) {
            out.append("- ").append(companion).append('\n');
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
