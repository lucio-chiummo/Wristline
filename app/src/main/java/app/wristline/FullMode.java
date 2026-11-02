package app.wristline;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * "Full mode" = call audio through the watch. An app that can read the phone's call state hangs up
 * when our Telecom call goes active, so its READ_PHONE_STATE app-op is set to "ignore". Only a
 * shell-level process can do that, which Wristline isn't: the user runs the command on a computer.
 */
final class FullMode {
    private FullMode() {
    }

    /** Whether the next answered call from this app should route audio to the watch. */
    static boolean shouldUse(Context context, SupportedApp app) {
        return state(context, app) == VoiceState.ON;
    }

    static VoiceState state(Context context, SupportedApp app) {
        if (Prefs.fullModeOn(context, app) && !Prefs.broken(context, app)) {
            checkReinstall(context, app);
        }
        return VoiceState.of(app.fullModeVerified, needsAppOp(context, app),
                Prefs.fullModeOn(context, app), Prefs.broken(context, app));
    }

    /** The user ran the set-up command for this app on a computer. */
    static void markSetUp(Context context, SupportedApp app) {
        Prefs.setFullModeOn(context, app, true);
        Prefs.setInstallTime(context, app, installTime(context, app));
    }

    private static long installTime(Context context, SupportedApp app) {
        try {
            return context.getPackageManager().getPackageInfo(app.packageName, 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /** A reinstalled app lost its setting: ask for a repair now rather than drop the next answered call. */
    private static void checkReinstall(Context context, SupportedApp app) {
        long now = installTime(context, app);
        switch (InstallCheck.compare(Prefs.installTime(context, app), now)) {
            case REMEMBER:
                Prefs.setInstallTime(context, app, now);
                break;
            case REINSTALLED:
                Log.w(RelayListener.TAG, app.packageName + " was reinstalled; its setting is gone");
                Diagnostics.record(context, EventLog.REINSTALLED, app, "");
                needsRepair(context, app);
                break;
            default:
                break;
        }
    }

    /** Apps without READ_PHONE_STATE can't see our call go active, so they need no setting (Instagram). */
    static boolean needsAppOp(Context context, SupportedApp app) {
        return context.getPackageManager().checkPermission(Manifest.permission.READ_PHONE_STATE, app.packageName)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Every app with the setting applied, whether or not it's being relayed right now. */
    static List<SupportedApp> appsWithVoiceOn(Context context) {
        List<SupportedApp> apps = new ArrayList<>();
        for (SupportedApp app : SupportedApp.ALL) {
            if (Prefs.fullModeOn(context, app)) {
                apps.add(app);
            }
        }
        return apps;
    }

    /** The apps have their normal setting back (the user ran the commands), or never had another one. */
    static void markTurnedOff(Context context, List<SupportedApp> apps) {
        for (SupportedApp app : apps) {
            Prefs.setFullModeOn(context, app, false);
        }
        StatusNotifications.cancel(context, StatusNotifications.ID_REPAIR);
    }

    /**
     * Called when the app ended a call right after our Telecom call went active: its setting was
     * reset (app reinstalled or its Phone permission toggled). Falls back to basic mode until the
     * user repairs it.
     */
    static void onCallDropped(Context context, SupportedApp app) {
        Log.w(RelayListener.TAG, app.packageName + " hung up when the watch took the audio; full mode needs repair");
        needsRepair(context, app);
    }

    /** Back to basic mode for this app until the user repairs it. */
    private static void needsRepair(Context context, SupportedApp app) {
        Prefs.setBroken(context, app, true);
        StatusNotifications.showRepairNeeded(context, app);
    }
}
