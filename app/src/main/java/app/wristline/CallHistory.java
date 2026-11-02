package app.wristline;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** The last calls relayed by Wristline (in and out), for the watch's "Recent" list. */
final class CallHistory {
    static final int MAX = 20;

    static final class Entry {
        final long time;
        final boolean outgoing;
        final String app;
        /** Callable contact id, or -1 when the caller isn't one (e.g. Instagram). */
        final long contactId;
        final String name;
        /** An incoming call nobody answered (and that wasn't declined on the watch). */
        final boolean missed;

        Entry(long time, boolean outgoing, String app, long contactId, String name) {
            this(time, outgoing, app, contactId, name, false);
        }

        Entry(long time, boolean outgoing, String app, long contactId, String name, boolean missed) {
            this.time = time;
            this.outgoing = outgoing;
            this.app = app;
            this.contactId = contactId;
            this.name = name;
            this.missed = missed;
        }
    }

    /** Marks the incoming call that started at {@code time} as missed; other entries unchanged. */
    static List<Entry> markMissed(List<Entry> history, long time) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : history) {
            out.add(e.time == time && !e.outgoing
                    ? new Entry(e.time, false, e.app, e.contactId, e.name, true) : e);
        }
        return out;
    }

    /** The entry that started at {@code time}, or null. */
    static Entry at(List<Entry> history, long time) {
        for (Entry e : history) {
            if (e.time == time) {
                return e;
            }
        }
        return null;
    }

    private CallHistory() {
    }

    /** How many of {@code history} were started from the watch. */
    static int fromWatch(List<Entry> history) {
        int n = 0;
        for (Entry e : history) {
            if (e.outgoing) {
                n++;
            }
        }
        return n;
    }

    static List<Entry> push(List<Entry> history, Entry entry, int max) {
        List<Entry> out = new ArrayList<>();
        out.add(entry);
        for (Entry old : history) {
            if (out.size() < max) {
                out.add(old);
            }
        }
        return out;
    }

    /** One line per entry: time|out|app|contactId|base64(name)|missed (older lines lack |missed). */
    static String encode(List<Entry> history) {
        StringBuilder out = new StringBuilder();
        for (Entry e : history) {
            out.append(e.time).append('|').append(e.outgoing ? 1 : 0).append('|').append(e.app).append('|')
                    .append(e.contactId).append('|')
                    .append(Base64.getEncoder().encodeToString(e.name.getBytes(StandardCharsets.UTF_8)))
                    .append('|').append(e.missed ? 1 : 0)
                    .append('\n');
        }
        return out.toString();
    }

    static List<Entry> decode(String stored) {
        List<Entry> out = new ArrayList<>();
        for (String line : stored.split("\n")) {
            String[] f = line.split("\\|");
            if (f.length != 5 && f.length != 6) {
                continue;
            }
            try {
                out.add(new Entry(Long.parseLong(f[0]), "1".equals(f[1]), f[2], Long.parseLong(f[3]),
                        new String(Base64.getDecoder().decode(f[4]), StandardCharsets.UTF_8),
                        f.length == 6 && "1".equals(f[5])));
            } catch (IllegalArgumentException e) {
                // Skip a damaged line.
            }
        }
        return out;
    }
}
