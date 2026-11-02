package app.wristline;

/** The parts of a notification that tell a ringing call from an ongoing one (or from neither). */
final class CallShape {
    enum Kind { NONE, INCOMING, ONGOING }

    private static final int CALL_TYPE_INCOMING = 1; // Notification.CallStyle.CALL_TYPE_INCOMING
    private static final int CALL_TYPE_ONGOING = 2; // Notification.CallStyle.CALL_TYPE_ONGOING

    private final boolean callCategory;
    private final boolean ongoingFlag;
    private final Integer callType;
    private final boolean fullScreen;
    private final boolean hasAnswer;
    private final int actionCount;
    private final boolean hasHangUp;

    /**
     * {@code callType} is null when the notification has no EXTRA_CALL_TYPE (not a CallStyle);
     * {@code hasHangUp}: a hang-up intent or a button labelled like one.
     */
    CallShape(boolean callCategory, boolean ongoingFlag, Integer callType, boolean fullScreen,
            boolean hasAnswer, int actionCount, boolean hasHangUp) {
        this.callCategory = callCategory;
        this.ongoingFlag = ongoingFlag;
        this.callType = callType;
        this.fullScreen = fullScreen;
        this.hasAnswer = hasAnswer;
        this.actionCount = actionCount;
        this.hasHangUp = hasHangUp;
    }

    /**
     * @param allowUncategorizedOngoing the app's in-call notification has no call category (Telegram):
     *     accept an ongoing notification whose single action is a hang-up as the ongoing call (not,
     *     say, a live-location notification with a single "Stop").
     */
    Kind classify(boolean allowUncategorizedOngoing) {
        // A CallStyle notification says what it is, with or without the call category: Instagram's
        // in-call notification has none.
        if (callType != null && callType == CALL_TYPE_INCOMING) {
            return Kind.INCOMING;
        }
        if (callType != null && callType == CALL_TYPE_ONGOING) {
            return Kind.ONGOING;
        }
        if (!callCategory) {
            return allowUncategorizedOngoing && ongoingFlag && actionCount == 1 && hasHangUp
                    ? Kind.ONGOING : Kind.NONE;
        }
        if (fullScreen && (hasAnswer || actionCount == 2)) {
            return Kind.INCOMING;
        }
        // Missed-call notifications share the call category but aren't ongoing (Instagram reuses
        // the ringing notification's key for them).
        return ongoingFlag ? Kind.ONGOING : Kind.NONE;
    }
}
