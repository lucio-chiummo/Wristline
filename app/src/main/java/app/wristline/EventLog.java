package app.wristline;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * What Wristline did recently (calls relayed, answered, dropped, setup results), for the
 * diagnostic report people can share when something doesn't work. Never holds caller names or
 * numbers: only event codes, app packages and short technical details.
 */
final class EventLog {
    static final int MAX = 100;

    // Event codes, as they appear in the report.
    static final String LISTENER_CONNECTED = "listener_connected";
    static final String LISTENER_DISCONNECTED = "listener_disconnected";
    static final String LISTENER_REBIND = "listener_rebind";
    static final String LISTENER_TOGGLED = "listener_toggled";
    static final String INCOMING = "incoming";
    static final String SWITCHED_OFF = "switched_off";
    static final String RELAY_PAUSED = "relay_paused";
    static final String RELAY_RESUMED = "relay_resumed";
    static final String PHONE_IN_USE = "phone_in_use";
    static final String VOICE_MOVED = "voice_moved";
    static final String DO_NOT_DISTURB = "do_not_disturb";
    static final String BUSY = "busy";
    static final String UNREADABLE_CALL = "unreadable_call";
    static final String TELECOM_REFUSED = "telecom_refused";
    /** Telecom made Wristline's ringing call (detail "test call" for Ring my watch). */
    static final String CALL_CREATED = "call_created";
    /** Telecom accepted the request but then failed to make the ringing call. */
    static final String CALL_NOT_CREATED = "call_not_created";
    /** Telecom moved Wristline's call to a new state (detail: ringing, dialing, active, disconnected...). */
    static final String CALL_STATE = "call_state";
    /** The call's audio route changed (detail "route=<x> supported=<a,b>"). */
    static final String AUDIO_ROUTE = "audio_route";
    /** Wristline's call went active with no Bluetooth route to move the voice to: no watch connected for calls. */
    static final String AUDIO_NO_WATCH = "audio_no_watch";
    static final String OUTGOING = "outgoing";
    static final String OUTGOING_NOT_STARTED = "outgoing_not_started";
    static final String ANSWERED_ON_WATCH = "answered_on_watch";
    static final String DECLINED_ON_WATCH = "declined_on_watch";
    static final String HUNG_UP_ON_WATCH = "hung_up_on_watch";
    static final String ENDED = "ended";
    static final String DROPPED_TAKING_AUDIO = "dropped_taking_audio";
    static final String REINSTALLED = "reinstalled";
    static final String SETUP_STARTED = "setup_started";
    static final String SETUP_DONE = "setup_done";
    static final String SETUP_FAILED = "setup_failed";
    static final String REPAIR_SILENT = "repair_silent";
    static final String MISSED = "missed";
    static final String TEST_CALL = "test_call";

    static final class Entry {
        final long time;
        final String code;
        /** The calling app's package, or "" when the event isn't about one. */
        final String app;
        final String detail;

        Entry(long time, String code, String app, String detail) {
            this.time = time;
            this.code = code;
            this.app = app == null ? "" : app;
            this.detail = detail == null ? "" : detail;
        }
    }

    private EventLog() {
    }

    /** Two largest units, rounded down: "<1m", "12m", "2h 14m", "3d 4h". */
    static String duration(long ms) {
        long minutes = ms / 60_000;
        if (minutes < 1) {
            return "<1m";
        }
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h " + (minutes % 60) + "m";
        }
        return (hours / 24) + "d " + (hours % 24) + "h";
    }

    /** Newest first, at most {@code max}. */
    static List<Entry> push(List<Entry> log, Entry entry, int max) {
        List<Entry> out = new ArrayList<>();
        out.add(entry);
        for (Entry old : log) {
            if (out.size() >= max) {
                break;
            }
            out.add(old);
        }
        return out;
    }

    /** One line per entry: time|code|app|base64(detail). */
    static String encode(List<Entry> log) {
        StringBuilder out = new StringBuilder();
        for (Entry e : log) {
            out.append(e.time).append('|').append(e.code).append('|').append(e.app).append('|')
                    .append(Base64.getEncoder().encodeToString(e.detail.getBytes(StandardCharsets.UTF_8)))
                    .append('\n');
        }
        return out.toString();
    }

    static List<Entry> decode(String stored) {
        List<Entry> out = new ArrayList<>();
        if (stored == null) {
            return out;
        }
        for (String line : stored.split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length != 4) {
                continue;
            }
            try {
                out.add(new Entry(Long.parseLong(f[0]), f[1], f[2],
                        new String(Base64.getDecoder().decode(f[3]), StandardCharsets.UTF_8)));
            } catch (IllegalArgumentException e) {
                // Skip a damaged line.
            }
        }
        return out;
    }

    /** "2026-11-02 14:03:07 incoming com.whatsapp full mode". */
    static String format(Entry e) {
        SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        StringBuilder line = new StringBuilder(time.format(new Date(e.time))).append(' ').append(e.code);
        if (!e.app.isEmpty()) {
            line.append(' ').append(e.app);
        }
        if (!e.detail.isEmpty()) {
            line.append(' ').append(e.detail);
        }
        return line.toString();
    }
}
