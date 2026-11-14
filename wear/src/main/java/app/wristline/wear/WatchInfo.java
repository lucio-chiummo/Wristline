package app.wristline.wear;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Tells the phone which watch this is (maker, model, Wear OS SDK, Wristline version), for the
 * phone's diagnostic report. Build facts only: no names, no ids.
 */
final class WatchInfo {
    private WatchInfo() {
    }

    static DataMap dataMap(String manufacturer, String model, int sdk, String version) {
        DataMap map = new DataMap();
        map.putString(Protocol.KEY_MANUFACTURER, manufacturer);
        map.putString(Protocol.KEY_MODEL, model);
        map.putInt(Protocol.KEY_SDK, sdk);
        map.putString(Protocol.KEY_VERSION, version);
        return map;
    }

    /** Off the main thread; a data item that hasn't changed isn't sent again. */
    static void publish(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                String version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
                PutDataMapRequest request = PutDataMapRequest.create(Protocol.PATH_WATCH_INFO);
                request.getDataMap().putAll(dataMap(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT,
                        version == null ? "?" : version));
                Wearable.getDataClient(app).putDataItem(request.asPutDataRequest().setUrgent());
            } catch (Exception e) {
                Log.w(PhoneLink.TAG, "could not tell the phone about this watch", e);
            }
        }, "watch-info").start();
    }
}
