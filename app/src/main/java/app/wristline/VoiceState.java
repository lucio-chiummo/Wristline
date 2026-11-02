package app.wristline;

/** Where an app's call audio goes, as shown on the Voice screen. */
enum VoiceState {
    ON, SET_UP, NEEDS_REPAIR, NOT_VERIFIED;

    /**
     * @param needsAppOp the app can read the phone's call state (holds READ_PHONE_STATE), so it would
     *     hang up when the watch takes the audio unless its app-op is set
     */
    static VoiceState of(boolean fullModeVerified, boolean needsAppOp, boolean fullModeOn, boolean broken) {
        if (!fullModeVerified) {
            return NOT_VERIFIED;
        }
        if (broken) {
            return NEEDS_REPAIR;
        }
        return fullModeOn || !needsAppOp ? ON : SET_UP;
    }
}
