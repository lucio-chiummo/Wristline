package app.wristline;

/**
 * Android resets an app's settings (our READ_PHONE_STATE app-op among them) when it's uninstalled
 * and installed again, but not on updates. Comparing the app's first-install time with the one
 * seen when voice through the watch was set up catches a reinstall before a call drops.
 */
final class InstallCheck {
    enum Result {
        /** Nothing remembered yet (set up before this check existed): remember the current time. */
        REMEMBER,
        SAME,
        REINSTALLED
    }

    private InstallCheck() {
    }

    /** Times are {@code PackageInfo.firstInstallTime}; 0 or less means unknown. */
    static Result compare(long remembered, long current) {
        if (current <= 0) {
            return Result.SAME;
        }
        if (remembered <= 0) {
            return Result.REMEMBER;
        }
        return remembered == current ? Result.SAME : Result.REINSTALLED;
    }
}
