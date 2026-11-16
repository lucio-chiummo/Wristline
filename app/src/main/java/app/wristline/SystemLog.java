package app.wristline;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * For the report: what Android itself logged about a call's audio. Why a Bluetooth voice link
 * closed is written only in the system log, which an app cannot read; Wristline's Wireless
 * debugging connection can. Read-only commands, filtered on the phone to audio, Bluetooth-call and
 * call-routing lines; addresses and long numbers are masked before anything is kept.
 */
final class SystemLog {
    /** Lines kept per command. */
    static final int LOGCAT_LINES = 260;
    static final int AUDIO_LINES = 160;
    static final int BLUETOOTH_LINES = 60;
    static final int MAX_LINE = 220;
    /** Without a call's start time: this many of the newest system-log lines are searched. */
    static final int FALLBACK_SEARCHED = 40000;
    /** Starts this long before the call. */
    static final long LEAD_MS = 3000;

    private static final String LOGCAT_FILTER = String.join("|",
            // Audio service: who asks for which call device, who owns the mode, link dropped.
            "AudioDeviceBroker", "BtHelper", "AS\\.AudioService", "AudioModeSession", "RouteSelection",
            "CommunicationRoute", "setCommunicationDevice", "HfpAudio", "ScoManaged",
            // Bluetooth hands-free: accepting, opening and closing the voice link.
            "HeadsetService", "HeadsetStateMachine", "HeadsetNative", "BluetoothInCall", "isScoAcceptable",
            "(^|[^a-z])sco([^a-z]|$)", "bta_ag", "btm_sco",
            // Telecom: where it routes the call.
            "CallAudioRoute", "CallAudioMode", "BluetoothRouteManager", "BluetoothDeviceManager", "CARC\\.",
            "PendingAudioRoute");

    private static final String AUDIO_FILTER = String.join("|",
            "CommunicationRoute", "CommunicationDevice", "ModeOwner", "Mode owner", "HfpAudio",
            "BluetoothSco", "ScoManaged", "RouteSelectionStack", "RouteClient", "setMode\\(",
            "Requester #", "- Mode:", "- Package:", "- Privileged:", "- Active:", "update time",
            "Playback active", "Recording active", "mBluetoothScoOn", "mScoAudioState");

    private static final String BLUETOOTH_FILTER = String.join("|",
            "isScoAcceptable", "mActiveDevice", "mVirtualCallStarted", "mVoiceRecognitionStarted",
            "mAudioRouteAllowed", "isScoManagedByAudio", "AudioOn", "AudioConnecting", "AudioDisconnecting",
            "mAudioState", "isInbandRinging", "inCall", "mNumActive", "HFP_AUDIO");

    private static final Pattern MAC = Pattern.compile("(?i)(?:[0-9a-f]{2}:){5}([0-9a-f]{2})");
    /** A partly hidden address as Android prints it ("XX:XX:XX:XX:12:34"): kept, it is already masked. */
    private static final Pattern LONG_NUMBER = Pattern.compile("\\+?\\d{7,}");

    private SystemLog() {
    }

    /**
     * The commands, in the order {@link #format} expects their outputs. {@code sinceMs} is when the
     * call started, or 0 for "the newest lines".
     */
    static List<String> commands(long sinceMs) {
        String window = sinceMs > 0
                ? "-t '" + new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(sinceMs - LEAD_MS)) + "'"
                : "-t " + FALLBACK_SEARCHED;
        List<String> commands = new ArrayList<>();
        commands.add("getprop bluetooth.sco.managed_by_audio");
        commands.add("logcat -d -v time " + window + " 2>/dev/null | grep -iE '" + LOGCAT_FILTER + "' | tail -n " + LOGCAT_LINES);
        commands.add("dumpsys audio 2>/dev/null | grep -iE '" + AUDIO_FILTER + "' | tail -n " + AUDIO_LINES);
        commands.add("dumpsys bluetooth_manager 2>/dev/null | grep -iE '" + BLUETOOTH_FILTER + "' | tail -n " + BLUETOOTH_LINES);
        return commands;
    }

    /** The section of the report, from the outputs of {@link #commands}; masked and shortened. */
    static String format(List<String> outputs, long capturedAtMs) {
        StringBuilder out = new StringBuilder();
        out.append("System log captured ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(capturedAtMs))).append('\n');
        String managed = outputs.size() > 0 ? outputs.get(0).trim() : "";
        out.append("Voice link managed by audio: ").append(managed.isEmpty() ? "not set" : clean(managed)).append('\n');
        section(out, "Log", outputs, 1, LOGCAT_LINES);
        section(out, "Audio service", outputs, 2, AUDIO_LINES);
        section(out, "Bluetooth hands-free", outputs, 3, BLUETOOTH_LINES);
        return out.toString();
    }

    private static void section(StringBuilder out, String title, List<String> outputs, int index, int maxLines) {
        out.append("-- ").append(title).append(" --").append('\n');
        String output = outputs.size() > index ? outputs.get(index) : "";
        String[] lines = output.split("\n");
        int kept = 0;
        for (int i = Math.max(0, lines.length - maxLines); i < lines.length; i++) {
            String line = clean(lines[i]);
            if (!line.isEmpty()) {
                out.append(line).append('\n');
                kept++;
            }
        }
        if (kept == 0) {
            out.append("nothing").append('\n');
        }
    }

    /** One line without a full Bluetooth address or a long number (a phone number, an account id). */
    static String clean(String line) {
        String cleaned = MAC.matcher(line.trim()).replaceAll("XX:XX:XX:XX:XX:$1");
        cleaned = LONG_NUMBER.matcher(cleaned).replaceAll("#");
        return cleaned.length() > MAX_LINE ? cleaned.substring(0, MAX_LINE) + "…" : cleaned;
    }
}
