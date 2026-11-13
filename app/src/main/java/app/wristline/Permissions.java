package app.wristline;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/** The four things the user grants once so calls can be relayed. */
final class Permissions {
    static final int REQUEST_NOTIFICATIONS = 1;

    enum Step {
        NOTIFICATION_ACCESS(R.string.step_listener_title, R.string.step_listener_desc),
        POST_NOTIFICATIONS(R.string.step_post_title, R.string.step_post_desc),
        OVERLAY(R.string.step_overlay_title, R.string.step_overlay_desc),
        BATTERY(R.string.step_battery_title, R.string.step_battery_desc);

        final int title;
        final int description;

        Step(int title, int description) {
            this.title = title;
            this.description = description;
        }
    }

    private Permissions() {
    }

    /** The pairing code is typed into a notification: without one, pairing can't be finished. */
    static boolean canShowCodePopUp(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(StatusNotifications.CHANNEL_SETUP);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static boolean isDone(Context context, Step step) {
        switch (step) {
            case NOTIFICATION_ACCESS:
                return context.getSystemService(NotificationManager.class)
                        .isNotificationListenerAccessGranted(new ComponentName(context, RelayListener.class));
            case POST_NOTIFICATIONS:
                return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
            case OVERLAY:
                return Settings.canDrawOverlays(context);
            case BATTERY:
                return context.getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(context.getPackageName());
            default:
                throw new IllegalArgumentException(step.name());
        }
    }

    static List<Step> missing(Context context) {
        List<Step> missing = new ArrayList<>();
        for (Step step : Step.values()) {
            if (!isDone(context, step)) {
                missing.add(step);
            }
        }
        return missing;
    }

    /** Notification access may be greyed out ("restricted setting") for an app installed from a file. */
    static boolean showRestrictedHint(Context context, Step step) {
        if (step != Step.NOTIFICATION_ACCESS || isDone(context, step)) {
            return false;
        }
        String installer = null;
        try {
            installer = context.getPackageManager().getInstallSourceInfo(context.getPackageName())
                    .getInstallingPackageName();
        } catch (PackageManager.NameNotFoundException e) {
            // Our own package: can't happen.
        }
        return InstallSource.restrictedSettingsHint(installer, android.os.Build.VERSION.SDK_INT);
    }

    static Intent appInfo(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
    }

    /**
     * After the user denied the notification prompt twice, Android stops showing it (no rationale
     * either): only the app's notification settings can turn notifications on then.
     */
    static boolean notificationSettingsInstead(boolean askedBefore, boolean showRationale) {
        return askedBefore && !showRationale;
    }

    /** Opens the settings page (or permission prompt) for the step; the caller re-checks in onResume. */
    static void open(Activity activity, Step step) {
        switch (step) {
            case NOTIFICATION_ACCESS:
                Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                                new ComponentName(activity, RelayListener.class).flattenToString());
                try {
                    activity.startActivity(detail);
                } catch (RuntimeException e) {
                    activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
                break;
            case POST_NOTIFICATIONS:
                if (notificationSettingsInstead(Prefs.askedNotifications(activity),
                        activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                    activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
                } else {
                    Prefs.setAskedNotifications(activity);
                    activity.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
                }
                break;
            case OVERLAY:
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName())));
                break;
            case BATTERY:
                activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                break;
            default:
                throw new IllegalArgumentException(step.name());
        }
    }
}
