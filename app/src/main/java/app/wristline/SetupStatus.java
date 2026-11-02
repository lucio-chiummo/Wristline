package app.wristline;

/** What the status card on the Home screen shows; the first match wins. */
final class SetupStatus {
    enum Kind {
        PAUSED, STEPS_LEFT, NEEDS_REPAIR,
        /** Everything is set up, but no call has reached the watch yet: offer "Ring my watch". */
        TRY_IT,
        READY
    }

    private SetupStatus() {
    }

    /** @param reachedWatch a call (test or real) has rung on the watch since setup */
    static Kind decide(boolean relayOn, int missingSteps, int brokenApps, boolean reachedWatch) {
        if (!relayOn) {
            return Kind.PAUSED;
        }
        if (missingSteps > 0) {
            return Kind.STEPS_LEFT;
        }
        if (brokenApps > 0) {
            return Kind.NEEDS_REPAIR;
        }
        return reachedWatch ? Kind.READY : Kind.TRY_IT;
    }
}
