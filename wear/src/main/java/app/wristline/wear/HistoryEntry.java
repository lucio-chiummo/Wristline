package app.wristline.wear;

/** A recent call relayed by the phone (in or out). */
final class HistoryEntry {
    final long time;
    final boolean outgoing;
    final String app;
    /** Callable contact id, or -1. */
    final long contactId;
    final String name;
    /** Nobody answered it. */
    final boolean missed;

    HistoryEntry(long time, boolean outgoing, String app, long contactId, String name) {
        this(time, outgoing, app, contactId, name, false);
    }

    HistoryEntry(long time, boolean outgoing, String app, long contactId, String name, boolean missed) {
        this.time = time;
        this.outgoing = outgoing;
        this.app = app;
        this.contactId = contactId;
        this.name = name;
        this.missed = missed;
    }
}
