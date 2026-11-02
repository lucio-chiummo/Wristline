package app.wristline;

/** Whether calling from the watch can work right now: a watch connected, with Wristline on it. */
final class WatchStatus {
    enum Kind {
        /** Play services couldn't tell (no Wear OS app on the phone, or it timed out): say nothing. */
        UNKNOWN,
        NO_WATCH,
        /** A watch is connected but Wristline's watch app isn't installed on it. */
        APP_MISSING,
        READY
    }

    private WatchStatus() {
    }

    /**
     * @param connectedWatches connected Wear OS nodes, or -1 when they couldn't be read
     * @param watchesWithApp of those, the ones that have Wristline's watch app, or -1
     */
    static Kind of(int connectedWatches, int watchesWithApp) {
        if (connectedWatches < 0) {
            return Kind.UNKNOWN;
        }
        if (connectedWatches == 0) {
            return Kind.NO_WATCH;
        }
        if (watchesWithApp < 0) {
            return Kind.UNKNOWN;
        }
        return watchesWithApp == 0 ? Kind.APP_MISSING : Kind.READY;
    }
}
