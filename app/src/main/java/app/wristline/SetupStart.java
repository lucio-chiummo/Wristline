package app.wristline;

/** The setup service only gets ~3 minutes, so it starts once the user can go straight to pairing. */
final class SetupStart {
    private SetupStart() {
    }

    static boolean ready(boolean notifications, boolean onWifi, boolean developerOptions, boolean wirelessDebugging) {
        return notifications && onWifi && developerOptions && wirelessDebugging;
    }
}
