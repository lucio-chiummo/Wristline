package app.wristline;

import java.util.Locale;

/** Phone brand, for where "Build number" (Developer options) lives in its Settings. */
enum Brand {
    VIVO, SAMSUNG, GOOGLE, XIAOMI, ONEPLUS_OPPO, MOTOROLA, OTHER;

    /** From {@code Build.MANUFACTURER} (or the brand name); null or unknown gives OTHER. */
    static Brand of(String manufacturer) {
        if (manufacturer == null) {
            return OTHER;
        }
        switch (manufacturer.toLowerCase(Locale.ROOT)) {
            case "vivo":
            case "iqoo":
                return VIVO;
            case "samsung":
                return SAMSUNG;
            case "google":
                return GOOGLE;
            case "xiaomi":
            case "redmi":
            case "poco":
                return XIAOMI;
            case "oneplus":
            case "oppo":
            case "realme":
                return ONEPLUS_OPPO;
            case "motorola":
                return MOTOROLA;
            default:
                return OTHER;
        }
    }
}
