package app.wristline;

import java.util.List;

/** The calling apps Wristline can relay to the watch, and how each one behaves. */
final class SupportedApp {
    static final SupportedApp WHATSAPP = new SupportedApp("com.whatsapp", "WhatsApp", true, true, false, false, false,
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call", false);
    // Built from Telegram's source (VoIPService): its in-call notification has no call category.
    // Voice through the watch after the same one-time setup as WhatsApp (Telegram holds
    // READ_PHONE_STATE and hangs up on OFFHOOK).
    static final SupportedApp TELEGRAM = new SupportedApp("org.telegram.messenger", "Telegram", false, true, true, false, true,
            "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call", false);
    // Voice through the watch with no setting: Instagram doesn't hold READ_PHONE_STATE (it can't see
    // our call go active) and, answered, makes no Telecom call of its own (Telecom history, 2026-11-01).
    static final SupportedApp INSTAGRAM = new SupportedApp("com.instagram.android", "Instagram", false, true, false, false, true, null, true);

    // Captured 2026-11-10: ringing and in-call are separate call-category CallStyle notifications
    // (Decline/Answer, then "Hang up"); no missed-call notification of its own. A call started from
    // the watch shows "Establishing…" with only "Cancel call" and no call category, hence
    // ongoingLacksCallCategory (as Telegram) so hanging up from the watch presses it. Signal registers a
    // self-managed Telecom account, yet voice through the watch works with the same READ_PHONE_STATE
    // appop as WhatsApp: tested 2026-11-10 (answer, call from the watch, hang up from the watch).
    static final SupportedApp SIGNAL = new SupportedApp("org.thoughtcrime.securesms", "Signal", false, true, true, false, true,
            "vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call", false);

    static final List<SupportedApp> ALL = List.of(WHATSAPP, TELEGRAM, INSTAGRAM, SIGNAL);

    final String packageName;
    final String label;
    /** Relayed until the user switches it off. */
    final boolean defaultOn;
    /** Voice through the watch has been proven on a real call; until then calls stay in basic mode. */
    final boolean fullModeVerified;
    /** The app's in-call notification lacks CATEGORY_CALL; accept an ongoing one-action notification instead. */
    final boolean ongoingLacksCallCategory;
    /** Not tested on a real call yet; shown with a "Beta" label. */
    final boolean beta;
    /** In a call the app shows a different notification from the ringing one (WhatsApp keeps one). */
    final boolean separateInCallNotification;
    /** Contacts data-row mimetype the app writes for "Voice call"; null when it writes none. */
    final String callMimeType;
    /** Call back by pressing the "Call back" button of the app's own missed-call notification. */
    final boolean ownCallBack;

    private SupportedApp(String packageName, String label, boolean defaultOn, boolean fullModeVerified,
            boolean ongoingLacksCallCategory, boolean beta, boolean separateInCallNotification,
            String callMimeType, boolean ownCallBack) {
        this.packageName = packageName;
        this.label = label;
        this.defaultOn = defaultOn;
        this.fullModeVerified = fullModeVerified;
        this.ongoingLacksCallCategory = ongoingLacksCallCategory;
        this.beta = beta;
        this.separateInCallNotification = separateInCallNotification;
        this.callMimeType = callMimeType;
        this.ownCallBack = ownCallBack;
    }

    /** The app whose "Voice call" contact rows use this mimetype, or null. */
    static SupportedApp forCallMimeType(String mimeType) {
        for (SupportedApp app : ALL) {
            if (app.callMimeType != null && app.callMimeType.equals(mimeType)) {
                return app;
            }
        }
        return null;
    }

    /** Returns null when the package isn't supported. */
    static SupportedApp forPackage(String packageName) {
        for (SupportedApp app : ALL) {
            if (app.packageName.equals(packageName)) {
                return app;
            }
        }
        return null;
    }
}
