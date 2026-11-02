package app.wristline;

/**
 * Shell commands for full mode, which the user runs on a computer: stop an app from reading the
 * phone's call state (READ_PHONE_STATE app-op "ignore") so it doesn't hang up when Wristline's
 * Telecom call takes the audio.
 */
final class AppOpsCommands {
    private AppOpsCommands() {
    }

    static String set(String pkg) {
        return "appops set --uid " + pkg + " READ_PHONE_STATE ignore";
    }

    /**
     * "allow", not "default": on Android 16 "default" turns back into "ignore" (seen on a real
     * phone, permission flag REVOKED_COMPAT comes back with it), while "allow" clears both.
     */
    static String reset(String pkg) {
        return "appops set --uid " + pkg + " READ_PHONE_STATE allow";
    }

    static String adbLine(String pkg) {
        return "adb shell " + set(pkg);
    }
}
