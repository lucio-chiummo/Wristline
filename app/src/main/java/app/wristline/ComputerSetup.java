package app.wristline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;

import java.util.List;

/** Fallback: the adb command(s) to run once from a computer, and "I've run it". */
final class ComputerSetup {
    private ComputerSetup() {
    }

    static void show(Activity activity, List<SupportedApp> apps, Runnable onDone) {
        StringBuilder lines = new StringBuilder();
        for (SupportedApp app : apps) {
            lines.append(lines.length() > 0 ? "\n" : "").append(AppOpsCommands.adbLine(app.packageName));
        }
        String commands = lines.toString();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.adb_title)
                .setMessage(activity.getString(R.string.adb_message_apps, commands, AppUi.labels(activity, apps)))
                .setNeutralButton(R.string.copy, (d, w) -> copy(activity, commands))
                .setPositiveButton(R.string.adb_done, (d, w) -> {
                    for (SupportedApp app : apps) {
                        FullMode.markSetUp(activity, app);
                    }
                    StatusNotifications.cancel(activity, StatusNotifications.ID_REPAIR);
                    onDone.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    static void copy(Activity activity, String text) {
        activity.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("adb", text));
        Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show();
    }
}
