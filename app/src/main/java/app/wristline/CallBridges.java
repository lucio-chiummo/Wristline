package app.wristline;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Which apps on the phone receive calls from Telecom (InCallServices: the dialer, Bluetooth, a
 * watch companion), and which watch companion apps are installed. Wristline's calls reach a watch
 * only through one of these, so the diagnostic report lists them. Package names and versions only.
 */
final class CallBridges {
    static final String ACTION_IN_CALL_SERVICE = "android.telecom.InCallService";
    /** Declared by an InCallService that also wants self-managed calls, as Wristline's are. */
    static final String META_SELF_MANAGED = "android.telecom.INCLUDE_SELF_MANAGED_CALLS";
    /** Declared by the InCallService that draws the phone's call screen. */
    static final String META_UI = "android.telecom.IN_CALL_SERVICE_UI";
    /** The report lists at most this many bridges, then says how many more there are. */
    static final int MAX_LISTED = 12;

    /** Watch companion apps; keep in sync with the manifest's queries. */
    static final String[] COMPANIONS = {
            "com.google.android.apps.wear.companion", // Pixel Watch
            "com.google.android.wearable.app", // Wear OS by Google
            "com.heytap.health.international", // OHealth (OPPO, OnePlus)
            "com.heytap.health",
            "com.samsung.android.app.watchmanager", // Galaxy Wearable
            "com.xiaomi.wearable", // Mi Fitness
            "com.huawei.health",
            "com.mobvoi.companion.aw", // Mobvoi
    };

    static final class Bridge {
        final String packageName;
        final String version;
        final boolean selfManaged;
        final boolean ui;

        Bridge(String packageName, String version, boolean selfManaged, boolean ui) {
            this.packageName = packageName;
            this.version = version;
            this.selfManaged = selfManaged;
            this.ui = ui;
        }
    }

    private CallBridges() {
    }

    /** One entry per package, by package name; a flag is set when any of its services declares it true. */
    static List<Bridge> inCallServices(PackageManager pm) {
        Map<String, boolean[]> flags = new TreeMap<>();
        try {
            for (ResolveInfo info : pm.queryIntentServices(new Intent(ACTION_IN_CALL_SERVICE), PackageManager.GET_META_DATA)) {
                if (info.serviceInfo == null) {
                    continue;
                }
                Bundle meta = info.serviceInfo.metaData;
                boolean[] f = flags.computeIfAbsent(info.serviceInfo.packageName, k -> new boolean[2]);
                f[0] |= meta != null && meta.getBoolean(META_SELF_MANAGED, false);
                f[1] |= meta != null && meta.getBoolean(META_UI, false);
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        List<Bridge> out = new ArrayList<>();
        for (Map.Entry<String, boolean[]> e : flags.entrySet()) {
            String version = version(pm, e.getKey());
            out.add(new Bridge(e.getKey(), version == null ? "?" : version, e.getValue()[0], e.getValue()[1]));
        }
        return out;
    }

    /** "<package> <version>" for each installed companion app. */
    static List<String> installedCompanions(PackageManager pm) {
        List<String> out = new ArrayList<>();
        for (String pkg : COMPANIONS) {
            String version = version(pm, pkg);
            if (version != null) {
                out.add(pkg + " " + version);
            }
        }
        return out;
    }

    static String defaultDialer(Context context) {
        try {
            String dialer = context.getSystemService(TelecomManager.class).getDefaultDialerPackage();
            return dialer == null ? "unknown" : dialer;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    /** Version name, "?" when it has none, null when the package isn't installed. */
    private static String version(PackageManager pm, String pkg) {
        try {
            String name = pm.getPackageInfo(pkg, 0).versionName;
            return name == null ? "?" : name;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
        }
    }
}
