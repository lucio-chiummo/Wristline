package app.wristline;

/**
 * Where the on-phone voice setup is, shared between {@link AdbSetupService} (which moves it on)
 * and the guide screen (which shows it). Lives only as long as the app's process.
 */
final class SetupProgress {
    enum Phase { IDLE, WAITING_FOR_PAIRING_SCREEN, WAITING_FOR_CODE, PAIRING, APPLYING, DONE, FAILED }

    private static volatile Phase phase = Phase.IDLE;
    private static volatile String message;
    private static volatile long codeDeadlineWallMs;
    /** The guide screen while visible; called on the thread that changed the progress. */
    static volatile Runnable listener;

    private SetupProgress() {
    }

    static Phase phase() {
        return phase;
    }

    /** What went wrong (FAILED) or what was set up (DONE), in words for the user. */
    static String message() {
        return message;
    }

    /** Wall-clock time the pairing code is waited for until, while WAITING_FOR_CODE. */
    static long codeDeadlineWallMs() {
        return codeDeadlineWallMs;
    }

    static void set(Phase newPhase, String newMessage, long newCodeDeadlineWallMs) {
        phase = newPhase;
        message = newMessage;
        codeDeadlineWallMs = newCodeDeadlineWallMs;
        Runnable current = listener;
        if (current != null) {
            current.run();
        }
    }

    static void set(Phase newPhase) {
        set(newPhase, null, 0);
    }

    static boolean running() {
        Phase current = phase;
        return current != Phase.IDLE && current != Phase.DONE && current != Phase.FAILED;
    }
}
