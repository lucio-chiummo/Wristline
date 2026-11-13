package app.wristline;

/**
 * The steps of the voice setup guide: 1 Notifications (the pairing code is typed into one), 2 Wi-Fi,
 * 3 Developer options, 4 Wireless debugging and pairing. Step 4 is one trip to Settings: Wristline
 * notices Wireless debugging going on by itself.
 */
final class GuideSteps {
    enum Status { DONE, CURRENT, TODO, FAILED }

    private GuideSteps() {
    }

    /**
     * USB debugging and Wireless debugging only exist inside Developer options, so either being on proves
     * Developer options are on. Some brands (vivo) never set the standard flag, hence the user can also confirm.
     */
    static boolean developerOptionsOn(int developmentSettingsEnabled, int adbEnabled, int adbWifiEnabled,
            boolean userConfirmed) {
        return developmentSettingsEnabled == 1 || adbEnabled == 1 || adbWifiEnabled == 1 || userConfirmed;
    }

    static Status[] of(boolean notifications, boolean onWifi, boolean developerOptions, SetupProgress.Phase phase) {
        if (phase == SetupProgress.Phase.DONE) {
            return new Status[] {Status.DONE, Status.DONE, Status.DONE, Status.DONE};
        }
        Status notify = notifications ? Status.DONE : Status.CURRENT;
        Status wifi = onWifi ? Status.DONE : notifications ? Status.CURRENT : Status.TODO;
        Status dev = developerOptions ? Status.DONE : notifications && onWifi ? Status.CURRENT : Status.TODO;
        Status pair = !(notifications && onWifi && developerOptions) ? Status.TODO
                : phase == SetupProgress.Phase.FAILED ? Status.FAILED : Status.CURRENT;
        return new Status[] {notify, wifi, dev, pair};
    }
}
