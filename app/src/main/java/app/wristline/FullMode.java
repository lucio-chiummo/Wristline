package app.wristline;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import app.wristline.adb.AdbShell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Full mode" = call audio through the watch. An app that can read the phone's call state hangs up
 * when our Telecom call goes active, so its READ_PHONE_STATE app-op is set to "ignore". Only a
 * shell-level process can do that, which Wristline isn't: it uses Wristline's built-in adb client, or a computer.
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

    /** Voice through the watch was just set up for this app (on the phone or with a computer). */
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

    /** A reinstalled app lost its setting: repair now rather than drop the next answered call. */
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
     * Blocking. Applies and verifies the setting for each app over ADB, then restarts the apps that
     * took it (they read the call state at startup). Returns the apps that are now set up, or null
     * when ADB can't be reached.
     */
    static List<SupportedApp> apply(Context context, List<SupportedApp> apps) {
        List<String> commands = new ArrayList<>();
        for (SupportedApp app : apps) {
            commands.add(AppOpsCommands.set(app.packageName));
            commands.add(AppOpsCommands.get(app.packageName));
        }
        List<String> out = AdbShell.run(context, commands);
        if (out == null) {
            return null;
        }
        List<SupportedApp> ok = new ArrayList<>();
        List<String> restarts = new ArrayList<>();
        for (int i = 0; i < apps.size(); i++) {
            SupportedApp app = apps.get(i);
            if (AppOpsCommands.isIgnored(out.get(i * 2 + 1))) {
                ok.add(app);
                restarts.add(AppOpsCommands.restart(app.packageName));
                markSetUp(context, app);
            }
            Log.i(RelayListener.TAG, "full mode setting for " + app.packageName + ": " + ok.contains(app));
        }
        if (!restarts.isEmpty()) {
            AdbShell.run(context, restarts);
        }
        return ok;
    }

    /**
     * Blocking. Turns full mode off, gives the apps their normal setting back when ADB can be reached,
     * and checks that it took. Once every app is back, forgets the ADB key: nothing needs it until the
     * next setup. Returns false when a setting couldn't be reset (the user can do it with a computer).
     */
    static boolean disable(Context context, List<SupportedApp> apps) {
        List<SupportedApp> toReset = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        for (SupportedApp app : apps) {
            Prefs.setFullModeOn(context, app, false);
            // Without the permission there's no setting to undo, and "allow" isn't what it had.
            if (needsAppOp(context, app)) {
                toReset.add(app);
                commands.add(AppOpsCommands.reset(app.packageName));
                commands.add(AppOpsCommands.get(app.packageName));
            }
        }
        boolean allBack = true;
        if (!toReset.isEmpty()) {
            List<String> out = AdbShell.run(context, commands);
            if (out == null) {
                return false;
            }
            List<String> restarts = new ArrayList<>();
            for (int i = 0; i < toReset.size(); i++) {
                SupportedApp app = toReset.get(i);
                boolean back = AppOpsCommands.isAllowed(out.get(i * 2 + 1));
                Log.i(RelayListener.TAG, "normal setting back for " + app.packageName + ": " + back);
                if (back) {
                    restarts.add(AppOpsCommands.restart(app.packageName));
                } else {
                    allBack = false;
                }
            }
            if (!restarts.isEmpty()) {
                AdbShell.run(context, restarts);
            }
        }
        if (allBack) {
            AdbShell.forgetKey(context);
        }
        return allBack;
    }

    /**
     * Called when the app ended a call right after our Telecom call went active: its setting was
     * reset (app reinstalled or its Phone permission toggled). Repairs it when ADB is reachable;
     * otherwise falls back to basic mode until the user repairs it.
     */
    static void onCallDropped(Context context, SupportedApp app) {
        Log.w(RelayListener.TAG, app.packageName + " hung up when the watch took the audio; full mode needs repair");
        needsRepair(context, app);
    }

    /** Back to basic mode for this app until repaired; repairs by itself when allowed and possible. */
    private static void needsRepair(Context context, SupportedApp app) {
        Prefs.setBroken(context, app, true);
        if (!Prefs.consent(context, app)) {
            // Set up with a computer: changing the app's setting again needs the user's go-ahead.
            StatusNotifications.showRepairNeeded(context, app);
            return;
        }
        new Thread(() -> {
            List<SupportedApp> repaired = null;
            try {
                if (AdbShell.isReachable(context)) {
                    repaired = apply(context, Collections.singletonList(app));
                }
            } catch (Throwable t) {
                // Also Errors from the ADB/crypto libraries: this runs in the call listener's process.
                Log.w(RelayListener.TAG, "automatic repair failed", t);
            }
            if (repaired != null && repaired.contains(app)) {
                Log.i(RelayListener.TAG, "full mode repaired automatically");
                Prefs.setBroken(context, app, false);
                Diagnostics.record(context, EventLog.REPAIR_SILENT, app, "");
                StatusNotifications.cancel(context, StatusNotifications.ID_REPAIR);
            } else {
                StatusNotifications.showRepairNeeded(context, app);
            }
        }).start();
    }
}
