package app.wristline;

import android.content.Context;
import android.os.Build;

/** The extra background setting some brands add on top of Android's battery exemption. */
final class BatteryHint {
    private BatteryHint() {
    }

    /** String resource of the brand's hint, or 0 when the brand needs nothing extra. */
    static int of(Brand brand) {
        switch (brand) {
            case VIVO:
                return R.string.battery_hint_vivo;
            case XIAOMI:
                return R.string.battery_hint_xiaomi;
            case SAMSUNG:
                return R.string.battery_hint_samsung;
            case ONEPLUS_OPPO:
                return R.string.battery_hint_oneplus;
            default:
                return 0;
        }
    }

    /** The step's description; the battery step also carries this phone brand's hint. */
    static String description(Context context, Permissions.Step step) {
        String text = context.getString(step.description);
        if (step == Permissions.Step.BATTERY) {
            text = withBrandHint(context, text);
        }
        return text;
    }

    /** The text plus this phone brand's hint, when the brand has one. */
    static String withBrandHint(Context context, String text) {
        int hint = of(Brand.of(Build.MANUFACTURER));
        return hint == 0 ? text : text + "\n" + context.getString(hint);
    }
}
