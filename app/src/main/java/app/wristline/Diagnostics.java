package app.wristline;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.util.Map;

/**
 * Records events for the diagnostic report, and builds and shares it. Nothing leaves the phone
 * unless the user shares the report.
 */
final class Diagnostics {
    private Diagnostics() {
    }

    static void record(Context context, String code, SupportedApp app, String detail) {
        record(context, code, app == null ? "" : app.packageName, detail);
    }

    static void record(Context context, String code, String app, String detail) {
        try {
            Prefs.addEvent(context, new EventLog.Entry(System.currentTimeMillis(), code, app, detail));
        } catch (RuntimeException e) {
            // Diagnostics must never get in the way of a call.
            Log.w(RelayListener.TAG, "could not record " + code, e);
        }
    }

    static DiagnosticReport.Facts collectWithWatch(Context context) {
        DiagnosticReport.Facts f = collect(context);
        f.watch = WatchSync.status(context).name().toLowerCase(java.util.Locale.ROOT);
        return f;
    }

    static DiagnosticReport.Facts collect(Context context) {
        DiagnosticReport.Facts f = new DiagnosticReport.Facts();
        f.appVersion = version(context, context.getPackageName());
        f.device = Build.MANUFACTURER + " " + Build.MODEL;
        f.sdk = Build.VERSION.SDK_INT;
        f.securityPatch = Build.VERSION.SECURITY_PATCH;
        f.relayOn = Prefs.relayOn(context);
        f.watchCalling = Prefs.watchCalling(context);
        f.listenerConnected = RelayListener.isConnected();
        f.keyPresent = app.wristline.adb.AdbKey.exists(context);
        for (Permissions.Step step : Permissions.missing(context)) {
            f.missingPermissions.add(step.name());
        }
        for (SupportedApp app : SupportedApp.ALL) {
            String version = version(context, app.packageName);
            f.apps.add(new DiagnosticReport.AppState(app.packageName, version, Prefs.appOn(context, app),
                    version == null ? "" : FullMode.state(context, app).name()));
        }
        f.events = Prefs.events(context);
        f.osBuild = Build.DISPLAY;
        for (String pkg : Companions.KNOWN.keySet()) {
            String version = version(context, pkg);
            if (version != null) {
                f.companions.add(Companions.describe(pkg, version));
            }
        }
        f.bluetoothCallAudio = bluetoothCallAudio(context);
        phoneSettings(context, f.phoneSettings);
        return f;
    }

    /** Whether a Bluetooth headset (the watch) can carry call audio right now; needs no permission. */
    private static Boolean bluetoothCallAudio(Context context) {
        try {
            for (AudioDeviceInfo device : context.getSystemService(AudioManager.class).getAvailableCommunicationDevices()) {
                if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The phone settings that most often stop calls on makers' Android builds (HyperOS, ColorOS,
     * One UI): battery limits, background limits, pop-ups over other apps, and so on.
     */
    private static void phoneSettings(Context context, Map<String, String> out) {
        try {
            out.put("battery unrestricted", yes(context.getSystemService(PowerManager.class)
                    .isIgnoringBatteryOptimizations(context.getPackageName())));
            out.put("background restricted", yes(context.getSystemService(ActivityManager.class).isBackgroundRestricted()));
            out.put("display over other apps", yes(Settings.canDrawOverlays(context)));
            if (Build.VERSION.SDK_INT >= 34) {
                out.put("full-screen calls", yes(context.getSystemService(NotificationManager.class).canUseFullScreenIntent()));
            }
            out.put("notifications", yes(context.getSystemService(NotificationManager.class).areNotificationsEnabled()));
            out.put("contacts", granted(context, Manifest.permission.READ_CONTACTS));
            out.put("phone (calls)", granted(context, Manifest.permission.CALL_PHONE));
            out.put("do not disturb", dnd(context.getSystemService(NotificationManager.class).getCurrentInterruptionFilter()));
        } catch (RuntimeException e) {
            out.put("unreadable", e.getClass().getSimpleName());
        }
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }

    private static String granted(Context context, String permission) {
        return yes(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
    }

    private static String dnd(int filter) {
        switch (filter) {
            case NotificationManager.INTERRUPTION_FILTER_ALL:
                return "off";
            case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
                return "priority only";
            case NotificationManager.INTERRUPTION_FILTER_ALARMS:
                return "alarms only";
            case NotificationManager.INTERRUPTION_FILTER_NONE:
                return "total silence";
            default:
                return "unknown";
        }
    }

    /** Installed version name, or null when the package isn't installed. */
    private static String version(Context context, String packageName) {
        try {
            String name = context.getPackageManager().getPackageInfo(packageName, 0).versionName;
            return name == null ? "?" : name;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** The share sheet with the report as plain text. Off the main thread: it asks Wear OS about the watch. */
    static Intent shareIntent(Context context) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_subject))
                .putExtra(Intent.EXTRA_TEXT, DiagnosticReport.build(collectWithWatch(context)));
        return Intent.createChooser(send, context.getString(R.string.row_report));
    }

    /**
     * A calling app's call notification Wristline couldn't read (no Answer/Decline found): the
     * call doesn't reach the watch. Recorded with its shape, and the user is told once per app
     * version, since an app update is the usual cause.
     */
    static void unreadableCall(Context context, SupportedApp app, Notification n) {
        String shape = NotificationShape.of(n);
        record(context, EventLog.UNREADABLE_CALL, app, shape);
        Log.w(RelayListener.TAG, "unreadable call notification from " + app.packageName + ": " + shape);
        String version = version(context, app.packageName);
        if (!Prefs.markUnreadableNotified(context, app, version == null ? "" : version)) {
            return;
        }
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String text = context.getString(R.string.unreadable_text, app.label);
        Notification notification = new Notification.Builder(context, StatusNotifications.CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.unreadable_title, app.label))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        context.getSystemService(NotificationManager.class).notify(StatusNotifications.ID_UNREADABLE, notification);
    }
}
