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
