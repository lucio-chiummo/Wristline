package app.wristline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Watch companion apps the diagnostic report looks for. Only the Pixel Watch app is tested; the
 * others bridge calls their own way, so a report must say which one the watch uses.
 * Each package is also listed under {@code <queries>} in the manifest, or it reads as not installed.
 */
final class Companions {
    static final Map<String, String> KNOWN = new LinkedHashMap<>();

    static {
        KNOWN.put("com.google.android.apps.wear.companion", "Pixel Watch");
        KNOWN.put("com.google.android.wearable.app", "Wear OS by Google");
        KNOWN.put("com.samsung.android.app.watchmanager", "Galaxy Wearable");
        KNOWN.put("com.heytap.health.international", "OHealth");
        KNOWN.put("com.heytap.health", "HeyTap Health");
        KNOWN.put("com.mobvoi.companion", "Mobvoi Health");
        KNOWN.put("com.xiaomi.wearable", "Mi Fitness");
        KNOWN.put("com.huami.watch.hmwatchmanager", "Zepp");
        KNOWN.put("com.huawei.health", "Huawei Health");
    }

    private Companions() {
    }

    /** "OHealth (com.heytap.health.international 4.29.1)". */
    static String describe(String packageName, String version) {
        String label = KNOWN.get(packageName);
        return (label == null ? "" : label + " (") + packageName + " " + version + (label == null ? "" : ")");
    }
}
