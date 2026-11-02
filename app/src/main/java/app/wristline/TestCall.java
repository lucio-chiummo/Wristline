package app.wristline;

/**
 * "Ring my watch": a call that only exists in Wristline, to check that calls reach the watch
 * (and, answered, that its sound plays there) without anyone calling. It never touches a calling
 * app and isn't added to the call history.
 */
final class TestCall {
    /** Set on the Telecom call's extras. */
    static final String EXTRA = "app.wristline.TEST_CALL";
    /** Unanswered, it stops ringing after this long. */
    static final long RING_MS = 30_000;
    /** Answered, it beeps and hangs up after this long. */
    static final long ANSWERED_MS = 5_000;
    /** Beeps after answering: when each starts (ms after answering); each lasts {@link #BEEP_MS}. */
    static final long[] BEEPS_AT = {800, 1800, 2800};
    static final int BEEP_MS = 300;

    enum Start { OK, BUSY }

    /** How the last test call went: answered or declined on the watch means it reached it. */
    enum Result { NONE, REACHED, MISSED }

    /** Home, while it's shown: run on the main thread when a test call's result is known. */
    static volatile Runnable onResult;

    private TestCall() {
    }

    /** A real call (relayed or mirrored) always wins: no test while one is on. */
    static Start check(boolean relayingCall, boolean mirroredCallExists) {
        return relayingCall || mirroredCallExists ? Start.BUSY : Start.OK;
    }
}
