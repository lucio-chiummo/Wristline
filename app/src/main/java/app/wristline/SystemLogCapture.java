package app.wristline;

import android.content.Context;
import android.util.Log;

import app.wristline.adb.AdbKey;
import app.wristline.adb.AdbShell;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads {@link SystemLog} over Wristline's own Wireless debugging connection and keeps the last
 * capture for the report. Only when the phone is paired (voice set up on the phone) and Wireless
 * debugging is on; otherwise nothing is run.
 */
final class SystemLogCapture {
    private static final String FILE = "system_log.txt";
    /** Lets the system finish writing about the call that just ended. */
    private static final long SETTLE_MS = 1500;
    private static final AtomicBoolean running = new AtomicBoolean();

    private SystemLogCapture() {
    }

    /** After a call that had its voice on the watch: capture what Android logged about it. Returns at once. */
    static void afterCall(Context context, long callStartedMs) {
        Context app = context.getApplicationContext();
        if (!AdbKey.exists(app) || !running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(SETTLE_MS);
                boolean captured = capture(app, callStartedMs);
                if (!captured) {
                    // An older call's capture would pass for this one's.
                    forget(app);
                }
                Diagnostics.record(app, EventLog.SYSTEM_LOG, "", captured ? "captured" : "wireless debugging off");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                // Also Errors from the adb/crypto code: a report detail must never break a call.
                Log.w(RelayListener.TAG, "could not capture the system log", t);
            } finally {
                running.set(false);
            }
        }, "system-log");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * The section for the report: the capture kept after the last call, else one made now (the
     * newest lines), else null. Blocking: call off the main thread.
     */
    static String forReport(Context context) {
        Context app = context.getApplicationContext();
        String stored = stored(app);
        if (stored != null) {
            return stored;
        }
        if (!AdbKey.exists(app) || !running.compareAndSet(false, true)) {
            return null;
        }
        try {
            return capture(app, 0) ? stored(app) : null;
        } catch (Throwable t) {
            Log.w(RelayListener.TAG, "could not capture the system log", t);
            return null;
        } finally {
            running.set(false);
        }
    }

    private static boolean capture(Context context, long sinceMs) {
        List<String> outputs = AdbShell.run(context, SystemLog.commands(sinceMs));
        if (outputs == null) {
            return false;
        }
        try {
            Files.write(file(context).toPath(),
                    SystemLog.format(outputs, System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.w(RelayListener.TAG, "could not keep the system log", e);
            return false;
        }
    }

    private static String stored(Context context) {
        File file = file(context);
        if (!file.isFile()) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Deletes the kept capture (with the adb key: nothing of that connection stays behind). */
    static void forget(Context context) {
        file(context.getApplicationContext()).delete();
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE);
    }
}
