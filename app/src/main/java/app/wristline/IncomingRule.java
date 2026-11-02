package app.wristline;

/** Whether a new incoming call may ring the watch. */
final class IncomingRule {
    enum Decision {
        RELAY,
        /** Relaying paused, or this app switched off. */
        SWITCHED_OFF,
        /** The phone's Do Not Disturb silences this call (and Wristline follows it). */
        DO_NOT_DISTURB,
        /** The phone is unlocked and in use, and the user asked for no watch ringing then. */
        PHONE_IN_USE
    }

    private IncomingRule() {
    }

    /**
     * @param passesDnd the phone would let this call notification through Do Not Disturb (always
     *     true when Do Not Disturb is off; its allowed contacts, repeat callers and schedules count)
     * @param notWhileInUse the user's "not while I'm using the phone"
     * @param phoneInUse the phone's screen is on and unlocked
     */
    static Decision decide(boolean relayOn, boolean appOn, boolean followDnd, boolean passesDnd,
            boolean notWhileInUse, boolean phoneInUse) {
        if (!relayOn || !appOn) {
            return Decision.SWITCHED_OFF;
        }
        if (followDnd && !passesDnd) {
            return Decision.DO_NOT_DISTURB;
        }
        return notWhileInUse && phoneInUse ? Decision.PHONE_IN_USE : Decision.RELAY;
    }
}
