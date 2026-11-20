package app.wristline;

/** What the status card on the Home screen shows; the first match wins. */
final class SetupStatus {
    enum Kind {
        PAUSED, STEPS_LEFT,
        /** Notification access is on, but Android isn't connecting the listener. */
        NOT_RECEIVING,
        NEEDS_REPAIR,
        /** This phone lost the mode-owner war, but Telecom's carrier account is still disabled. */
        CARRIER_OFF,
        /** Everything is set up, but no call has reached the watch yet: offer "Ring my watch". */
        TRY_IT,
        READY
    }

    private SetupStatus() {
    }

    /**
     * @param reachedWatch a call (test or real) has rung on the watch since setup
     * @param carrierNeeded this phone lost the mode-owner war, so relayed calls ring as a managed
     *     call on the carrier account instead of a self-managed one
     * @param carrierEnabled the user has enabled the carrier account in Calling accounts
     */
    static Kind decide(boolean relayOn, int missingSteps, boolean listenerUnbound, int brokenApps,
            boolean reachedWatch, boolean carrierNeeded, boolean carrierEnabled) {
        if (!relayOn) {
            return Kind.PAUSED;
        }
        if (missingSteps > 0) {
            return Kind.STEPS_LEFT;
        }
        if (listenerUnbound) {
            return Kind.NOT_RECEIVING;
        }
        if (brokenApps > 0) {
            return Kind.NEEDS_REPAIR;
        }
        if (carrierNeeded && !carrierEnabled) {
            return Kind.CARRIER_OFF;
        }
        return reachedWatch ? Kind.READY : Kind.TRY_IT;
    }
}
