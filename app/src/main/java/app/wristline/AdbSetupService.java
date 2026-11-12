package app.wristline;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import app.wristline.adb.AdbDiscovery;
import app.wristline.adb.AdbShell;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Sets up voice through the watch on the phone alone: waits for Wireless debugging's pairing
 * service, takes the pairing code from a notification's reply field (so the user never leaves
 * Settings), pairs, then applies each app's setting over ADB. Runs as a short foreground service.
 */
public class AdbSetupService extends Service {
    static final String ACTION_START = "app.wristline.adb.START";
    static final String ACTION_CODE = "app.wristline.adb.CODE";
    static final String EXTRA_PACKAGES = "packages";
    private static final String KEY_CODE = "code";
    /** shortService gets about 3 minutes; finish well before the system's limit. */
    private static final long TIMEOUT_MS = 165_000;
    private static final long DISCOVERY_STEP_MS = 3_000;
    private static final long REALERT_MS = 15_000;
    private static final String GROUP_CODE = "pairing_code";
    private static final String GROUP_PROGRESS = "setup_progress";

    private final LinkedBlockingQueue<String> codes = new LinkedBlockingQueue<>();
    private volatile boolean running;
    /** A fixed code for the event log: never the pairing code or any user text. */
    private String reason = "done";
    private volatile int runStartId;
    private ArrayList<String> runPackages = new ArrayList<>();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CODE.equals(action)) {
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            String code = PairingCode.normalize(results == null ? null : results.getCharSequence(KEY_CODE));
            codes.offer(code == null ? "" : code);
            if (running) {
                // stopSelf(id) only works with the latest start, and this is now the latest.
                runStartId = startId;
            } else {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action) && !running) {
            StatusNotifications.cancel(this, StatusNotifications.ID_SETUP_RESULT);
            running = true;
            runStartId = startId;
            codes.clear();
            SetupProgress.set(SetupProgress.Phase.WAITING_FOR_PAIRING_SCREEN);
            List<SupportedApp> apps = new ArrayList<>();
            ArrayList<String> packages = intent.getStringArrayListExtra(EXTRA_PACKAGES);
            runPackages = packages == null ? new ArrayList<>() : packages;
            if (packages != null) {
                for (String pkg : packages) {
                    SupportedApp app = SupportedApp.forPackage(pkg);
                    if (app != null) {
                        apps.add(app);
                    }
                }
            }
            Notification progress = progress(getString(R.string.setup_looking), getString(R.string.setup_looking_text));
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(StatusNotifications.ID_PAIRING, progress, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
            } else {
                startForeground(StatusNotifications.ID_PAIRING, progress);
            }
            new Thread(() -> work(apps), "adb-setup").start();
        }
        return START_NOT_STICKY;
    }

    private void work(List<SupportedApp> apps) {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        String failure;
        List<SupportedApp> done = null;
        try {
            failure = connect(deadline);
            if (failure == null) {
                notifyProgress(getString(R.string.setup_applying), null);
                SetupProgress.set(SetupProgress.Phase.APPLYING);
                done = FullMode.apply(this, apps);
                if (done == null) {
                    reason = "no_connection";
                    failure = getString(R.string.setup_no_connection);
                }
            }
        } catch (Throwable t) {
            // Also Errors from the ADB/crypto libraries: never take the call listener down with us.
            Log.w(RelayListener.TAG, "ADB setup failed", t);
            reason = "no_connection";
            failure = getString(R.string.setup_no_connection);
        }
        finish(apps, done, failure);
    }

    /** Returns null once Wristline can talk to Wireless debugging, else the reason it can't. */
    private String connect(long deadline) {
        InetSocketAddress pairing = null;
        boolean waiting = true;
        while (waiting) {
            boolean wifi = AdbDiscovery.onWifi(this);
            // Already paired (e.g. a repair after Wireless debugging was turned back on): no code needed.
            boolean reachable = wifi && AdbShell.isReachable(this);
            if (wifi && !reachable) {
                pairing = AdbDiscovery.find(this, AdbDiscovery.PAIRING, DISCOVERY_STEP_MS);
            }
            switch (SetupStep.whileLooking(wifi, reachable, pairing != null, remaining(deadline))) {
                case FAIL_NO_WIFI:
                    reason = "no_wifi";
                    return getString(R.string.wifi_needed);
                case APPLY:
                    return null;
                case FAIL_NOT_FOUND:
                    reason = "not_found";
                    return getString(R.string.setup_not_found);
                case ASK_CODE:
                    waiting = false;
                    break;
                default:
                    break;
            }
        }
        long codeDeadline = deadline - SetupStep.FINISH_MARGIN_MS;
        notifyCodeEntry(getString(R.string.setup_enter_code_text), codeDeadline);
        notifyProgress(getString(R.string.setup_waiting_code), null);
        waitingForCode(null, codeDeadline);
        while (remaining(codeDeadline) > 0) {
            String code;
            try {
                code = codes.poll(Math.min(remaining(codeDeadline), REALERT_MS), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (code == null) {
                if (remaining(codeDeadline) <= 0) {
                    break;
                }
                // The pop-up only stays a few seconds: drop it down again until the code arrives.
                notifyCodeEntry(getString(R.string.setup_enter_code_text), codeDeadline);
                continue;
            }
            if (code.isEmpty()) {
                notifyCodeEntry(getString(R.string.setup_bad_code), codeDeadline);
                waitingForCode(getString(R.string.setup_bad_code), codeDeadline);
                continue;
            }
            StatusNotifications.cancel(this, StatusNotifications.ID_PAIRING_CODE);
            notifyProgress(getString(R.string.setup_pairing), null);
            SetupProgress.set(SetupProgress.Phase.PAIRING);
            // The pairing dialog gets a new port each time it opens (or after the screen sleeps).
            InetSocketAddress current = AdbDiscovery.find(this, AdbDiscovery.PAIRING, DISCOVERY_STEP_MS);
            if (current != null) {
                pairing = current;
            }
            if (AdbShell.pair(this, pairing, code)) {
                return null;
            }
            // The user may have reopened the pairing dialog, which moves it to a new port.
            InetSocketAddress again = AdbDiscovery.find(this, AdbDiscovery.PAIRING, DISCOVERY_STEP_MS);
            if (again != null) {
                pairing = again;
            }
            notifyCodeEntry(getString(R.string.setup_wrong_code), codeDeadline);
            waitingForCode(getString(R.string.setup_wrong_code), codeDeadline);
        }
        reason = "not_paired";
        return getString(R.string.setup_not_paired);
    }

    private static void waitingForCode(String problem, long codeDeadline) {
        SetupProgress.set(SetupProgress.Phase.WAITING_FOR_CODE, problem,
                System.currentTimeMillis() + Math.max(0, remaining(codeDeadline)));
    }

    private static long remaining(long deadline) {
        return deadline - SystemClock.elapsedRealtime();
    }

    private void finish(List<SupportedApp> apps, List<SupportedApp> done, String failure) {
        String title;
        String text;
        if (failure != null) {
            title = getString(R.string.setup_failed_title);
            text = failure;
        } else {
            List<SupportedApp> missed = new ArrayList<>(apps);
            missed.removeAll(done);
            if (done.isEmpty()) {
                reason = "no_connection";
                title = getString(R.string.setup_failed_title);
                text = getString(R.string.setup_no_connection);
            } else {
                title = getString(R.string.setup_done_title);
                reason = missed.isEmpty() ? "done" : "partly";
                text = missed.isEmpty()
                        ? getString(R.string.setup_done_text, AppUi.labels(this, done))
                        : getString(R.string.setup_partly_text, AppUi.labels(this, done), AppUi.labels(this, missed));
                StatusNotifications.cancel(this, StatusNotifications.ID_REPAIR);
            }
        }
        Notification.Builder result = new Notification.Builder(this, StatusNotifications.CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, VoiceSetupActivity.class), PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true);
        if (failure != null) {
            // Started from the notification, so the user can retry without leaving Settings.
            result.addAction(new Notification.Action.Builder(null, getString(R.string.try_again),
                    PendingIntent.getForegroundService(this, 3, startIntent(this, runPackages),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)).build());
        }
        if (failure == null) {
            result.addAction(new Notification.Action.Builder(null, getString(R.string.turn_off_wireless_debugging),
                    PendingIntent.getActivity(this, 1, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)).build());
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        StatusNotifications.cancel(this, StatusNotifications.ID_PAIRING_CODE);
        getSystemService(NotificationManager.class).notify(StatusNotifications.ID_SETUP_RESULT, result.build());
        running = false;
        boolean failed = failure != null || done.isEmpty();
        SetupProgress.set(failed ? SetupProgress.Phase.FAILED : SetupProgress.Phase.DONE, text, 0);
        Diagnostics.record(this, failed ? EventLog.SETUP_FAILED : EventLog.SETUP_DONE, "", reason);
        stopSelf(runStartId);
    }

    /** shortService's time is up (API 34+): stop now or the system treats the app as not responding. */
    @Override
    public void onTimeout(int startId) {
        Log.w(RelayListener.TAG, "ADB setup ran out of time");
        SetupProgress.set(SetupProgress.Phase.FAILED, getString(R.string.setup_not_paired), 0);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (SetupProgress.running()) {
            // Stopped before it finished (e.g. by the system): the guide must offer Try again.
            SetupProgress.set(SetupProgress.Phase.FAILED, getString(R.string.setup_not_paired), 0);
        }
        super.onDestroy();
    }

    static Intent startIntent(android.content.Context context, ArrayList<String> packages) {
        return new Intent(context, AdbSetupService.class)
                .setAction(ACTION_START)
                .putStringArrayListExtra(EXTRA_PACKAGES, packages);
    }

    private Notification progress(String title, String text) {
        Notification.Builder builder = new Notification.Builder(this, StatusNotifications.CHANNEL_SETUP)
                .setSmallIcon(R.drawable.ic_notification)
                .setGroup(GROUP_PROGRESS)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        if (text != null) {
            builder.setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text));
        }
        return builder.build();
    }

    private void notifyProgress(String title, String text) {
        getSystemService(NotificationManager.class).notify(StatusNotifications.ID_PAIRING, progress(title, text));
    }

    /** Pops up over Wireless debugging's pairing screen; counts down to {@code deadline}. */
    private void notifyCodeEntry(String text, long deadline) {
        RemoteInput input = new RemoteInput.Builder(KEY_CODE)
                .setLabel(getString(R.string.setup_code_hint))
                .build();
        PendingIntent reply = PendingIntent.getService(this, 2,
                new Intent(this, AdbSetupService.class).setAction(ACTION_CODE),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long left = Math.max(0, remaining(deadline));
        Notification notification = new Notification.Builder(this, StatusNotifications.CHANNEL_SETUP)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.setup_enter_code))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                // Own group: Android would otherwise fold it together with the progress
                // notification, hiding the Pair button.
                .setGroup(GROUP_CODE)
                // Live countdown so the user knows how long the code is waited for.
                .setWhen(System.currentTimeMillis() + left)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(left)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, getString(R.string.setup_send_code), reply)
                        .addRemoteInput(input)
                        .build())
                .build();
        getSystemService(NotificationManager.class).notify(StatusNotifications.ID_PAIRING_CODE, notification);
    }
}
