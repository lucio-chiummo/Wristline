package app.wristline;

/**
 * Shell commands for full mode, run by Wristline's built-in adb client, or a computer: stop an app from reading the phone's call state (READ_PHONE_STATE
 * app-op "ignore") so it doesn't hang up when Wristline's Telecom call takes the audio.
 */
final class AppOpsCommands {
    private AppOpsCommands() {
    }

    static String set(String pkg) {
        return "appops set --uid " + pkg + " READ_PHONE_STATE ignore";
    }

    static String get(String pkg) {
        return "appops get --uid " + pkg + " READ_PHONE_STATE";
    }

    /**
     * "allow", not "default": on Android 16 "default" turns back into "ignore" (seen on a real
     * phone, permission flag REVOKED_COMPAT comes back with it), while "allow" clears both.
     */
    static String reset(String pkg) {
        return "appops set --uid " + pkg + " READ_PHONE_STATE allow";
    }

    /** "am kill", never force-stop: a force-stopped app stops receiving pushes, so calls stop arriving. */
    static String restart(String pkg) {
        return "am kill " + pkg;
    }

    static String adbLine(String pkg) {
        return "adb shell " + set(pkg);
    }

    static boolean isIgnored(String getOutput) {
        return getOutput != null && getOutput.contains("Uid mode: READ_PHONE_STATE: ignore");
    }

    /** The app has its normal setting back. "No operations." means nothing is overriding it. */
    static boolean isAllowed(String getOutput) {
        if (getOutput == null || isIgnored(getOutput)) {
            return false;
        }
        return getOutput.contains("READ_PHONE_STATE: allow") || getOutput.contains("No operations");
    }
}
