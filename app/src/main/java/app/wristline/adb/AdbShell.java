package app.wristline.adb;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Talks to this phone's own adbd over Wireless debugging: pairs once with a pairing code, then runs
 * shell commands with adb-shell privileges. The only class of this package the app uses. Every
 * call blocks; failures are logged by class and message only, never shell output or key material.
 */
public final class AdbShell {
    static final String TAG = AdbDiscovery.TAG;
    static final int CONNECT_TIMEOUT_MS = 4000;
    static final int SOCKET_TIMEOUT_MS = 15000;
    static final long PAIR_TIMEOUT_MS = 20000;

    private AdbShell() {
    }

    /** True when Wireless debugging is on and adbd lets Wristline's key in; runs no command. */
    public static boolean isReachable(Context context) {
        try (AdbTransport transport = connect(context)) {
            return transport != null;
        } catch (IOException | RuntimeException e) {
            log("adb not reachable", e);
            return false;
        }
    }

    /** Each command's output in order, over one connection; null when adbd can't be reached or any command fails. */
    public static List<String> run(Context context, List<String> commands) {
        try (AdbTransport transport = connect(context)) {
            if (transport == null) {
                return null;
            }
            List<String> outputs = new ArrayList<>();
            for (String command : commands) {
                outputs.add(transport.shell(command));
            }
            return outputs;
        } catch (IOException | RuntimeException e) {
            log("adb shell failed", e);
            return null;
        }
    }

    /**
     * Pairs with the "Pair device with pairing code" service. Waits on a side thread so a stuck
     * peer cannot hold the caller past {@link #PAIR_TIMEOUT_MS}.
     */
    public static boolean pair(Context context, InetSocketAddress pairing, String code) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            PairingClient.pair(pairing, code, AdbKey.load(context), CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS);
            return null;
        });
        Thread thread = new Thread(task, "adb-pair");
        thread.setDaemon(true);
        thread.start();
        try {
            task.get(PAIR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException e) {
            log("adb pairing failed", e.getCause() == null ? e : e.getCause());
        } catch (Exception e) {
            log("adb pairing failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        task.cancel(true);
        return false;
    }

    /** Deletes the adb key: reaching adbd again takes a new pairing. */
    public static void forgetKey(Context context) {
        AdbKey.forget(context);
    }

    /** The handshaken transport, or null when not on Wi-Fi or no connect service is advertised. */
    private static AdbTransport connect(Context context) throws IOException {
        if (!AdbDiscovery.onWifi(context)) {
            Log.i(TAG, "adb: not on Wi-Fi");
            return null;
        }
        InetSocketAddress address = AdbDiscovery.find(context, AdbDiscovery.CONNECT, CONNECT_TIMEOUT_MS);
        if (address == null) {
            Log.i(TAG, "adb: no Wireless debugging service found");
            return null;
        }
        return AdbTransport.connect(address, AdbKey.load(context), CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS);
    }

    private static void log(String what, Throwable t) {
        Log.w(TAG, what + ": " + t.getClass().getSimpleName() + ": " + t.getMessage());
    }
}
