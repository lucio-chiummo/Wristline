package app.wristline;

/**
 * Follows one relayed call per app across that app's notifications. WhatsApp keeps one notification
 * from ringing to hang-up; Telegram and Instagram post a separate in-call notification, and Telegram
 * removes the ringing one as soon as the call is answered. Not thread-safe: use from one thread.
 */
final class CallTracker {
    /** How long to wait for an in-call notification after the ringing one goes. */
    static final long GRACE_MS = 2000;

    enum Result { NONE, START, IGNORED, ONGOING, END, CHECK_LATER }

    private String pkg;
    private String incomingKey;
    private String ongoingKey;
    private int callId;

    Result incoming(String pkg, String key, boolean allowedToStart) {
        if (this.pkg == null) {
            if (!allowedToStart) {
                return Result.IGNORED;
            }
            this.pkg = pkg;
            incomingKey = key;
            callId++;
            return Result.START;
        }
        // One relayed call at a time; the tracked app's reposts change nothing.
        return this.pkg.equals(pkg) ? Result.NONE : Result.IGNORED;
    }

    /**
     * A call Wristline itself started (from the watch): followed through the app's in-call
     * notification. Check {@link #graceExpired(int)} after a while: if the app never showed one,
     * the call didn't start.
     */
    Result outgoing(String pkg) {
        if (this.pkg != null) {
            return Result.IGNORED;
        }
        this.pkg = pkg;
        incomingKey = null;
        callId++;
        return Result.START;
    }

    Result ongoing(String pkg, String key) {
        if (this.pkg == null || !this.pkg.equals(pkg)) {
            return Result.NONE;
        }
        ongoingKey = key;
        return Result.ONGOING;
    }

    Result removed(String pkg, String key) {
        return removed(pkg, key, false);
    }

    /**
     * @param takingAudio our Telecom call just went active to take the audio: the app removing its
     *     ringing notification now means it hung up on us, not that an in-call notification follows.
     */
    Result removed(String pkg, String key, boolean takingAudio) {
        return removed(pkg, key, takingAudio, true);
    }

    /**
     * @param separateInCall the app posts its in-call notification separately (Telegram, Instagram);
     *     otherwise (WhatsApp) the ringing notification going without becoming ongoing ends the call.
     */
    Result removed(String pkg, String key, boolean takingAudio, boolean separateInCall) {
        if (this.pkg == null || !this.pkg.equals(pkg)) {
            return Result.NONE;
        }
        if (key.equals(ongoingKey)) {
            reset();
            return Result.END;
        }
        if (key.equals(incomingKey)) {
            incomingKey = null;
            if (takingAudio || (!separateInCall && ongoingKey == null)) {
                reset();
                return Result.END;
            }
            return ongoingKey == null ? Result.CHECK_LATER : Result.NONE;
        }
        return Result.NONE;
    }

    /** A tracked notification was updated in place into something that isn't a call (a missed call). */
    Result notACall(String pkg, String key) {
        return removed(pkg, key, false);
    }

    Result graceExpired() {
        return graceExpired(callId);
    }

    /**
     * Call after {@link #GRACE_MS} once {@link #removed} returned CHECK_LATER, with the
     * {@link #callId()} read then; a late check for an earlier call changes nothing.
     */
    Result graceExpired(int forCall) {
        if (forCall == callId && pkg != null && incomingKey == null && ongoingKey == null) {
            reset();
            return Result.END;
        }
        return Result.NONE;
    }

    /** Identifies the current call; changes with every call that starts. */
    int callId() {
        return callId;
    }

    /** The notification is the ringing notification of the call being relayed. */
    boolean isIncomingKey(String pkg, String key) {
        return pkg.equals(this.pkg) && key.equals(incomingKey);
    }

    String trackedPackage() {
        return pkg;
    }

    boolean isTracking() {
        return pkg != null;
    }

    void reset() {
        pkg = null;
        incomingKey = null;
        ongoingKey = null;
    }
}
