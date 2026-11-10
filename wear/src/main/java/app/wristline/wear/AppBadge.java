package app.wristline.wear;

/** The letter on a call's app badge: W WhatsApp, T Telegram, S Signal, I Instagram. */
final class AppBadge {
    private AppBadge() {
    }

    /** Null for an app Wristline doesn't know: no badge then. */
    static String letter(String app) {
        if (Protocol.WHATSAPP.equals(app)) {
            return "W";
        }
        if (Protocol.TELEGRAM.equals(app)) {
            return "T";
        }
        if (Protocol.SIGNAL.equals(app)) {
            return "S";
        }
        return "com.instagram.android".equals(app) ? "I" : null;
    }
}
