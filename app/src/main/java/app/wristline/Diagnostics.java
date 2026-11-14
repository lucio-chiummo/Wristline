package app.wristline;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

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

    static DiagnosticReport.Facts collect(Context context) {
        DiagnosticReport.Facts f = new DiagnosticReport.Facts();
        f.appVersion = version(context, context.getPackageName());
        f.device = Build.MANUFACTURER + " " + Build.MODEL;
        f.sdk = Build.VERSION.SDK_INT;
        f.securityPatch = Build.VERSION.SECURITY_PATCH;
        f.brand = Build.BRAND;
        f.release = Build.VERSION.RELEASE;
        f.display = Build.DISPLAY;
        f.locale = java.util.Locale.getDefault().toLanguageTag();
        f.bluetoothCallAudio = bluetoothCallAudio(context.getSystemService(AudioManager.class));
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
        f.watch = WatchSync.watchFacts(context);
        f.connectedWatches = WatchSync.connectedNodeCount(context);
        PackageManager pm = context.getPackageManager();
        f.defaultDialer = CallBridges.defaultDialer(context);
        f.bridges = CallBridges.inCallServices(pm);
        f.companions = CallBridges.installedCompanions(pm);
        f.events = Prefs.events(context);
        return f;
    }

    /**
     * Whether the phone has a Bluetooth output that can carry call audio (a watch connected for
     * calls shows up as one). Types only, never device names. Null when it can't be told.
     */
    static Boolean bluetoothCallAudio(AudioManager audio) {
        try {
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return null;
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

    /** The share sheet with the report as plain text. Blocking (reads the watch): call off the main thread. */
    static Intent shareIntent(Context context) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_subject))
                .putExtra(Intent.EXTRA_TEXT, DiagnosticReport.build(collect(context)));
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
