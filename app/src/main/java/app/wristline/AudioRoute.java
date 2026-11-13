package app.wristline;

/**
 * Names a call audio route for the diagnostic report, e.g. "bluetooth of earpiece+bluetooth+speaker".
 * Values are android.telecom.CallAudioState's ROUTE_* bits, repeated here so tests run without Android.
 */
final class AudioRoute {
    static final int EARPIECE = 0x1;
    static final int BLUETOOTH = 0x2;
    static final int WIRED_HEADSET = 0x4;
    static final int SPEAKER = 0x8;
    static final int STREAMING = 0x10;

    private static final int[] BITS = {EARPIECE, BLUETOOTH, WIRED_HEADSET, SPEAKER, STREAMING};
    private static final String[] NAMES = {"earpiece", "bluetooth", "wired", "speaker", "streaming"};

    private AudioRoute() {
    }

    static String describe(int route, int supportedMask) {
        StringBuilder supported = new StringBuilder();
        for (int i = 0; i < BITS.length; i++) {
            if ((supportedMask & BITS[i]) != 0) {
                supported.append(supported.length() == 0 ? "" : "+").append(NAMES[i]);
            }
        }
        return name(route) + " of " + (supported.length() == 0 ? "none" : supported);
    }

    private static String name(int route) {
        for (int i = 0; i < BITS.length; i++) {
            if (route == BITS[i]) {
                return NAMES[i];
            }
        }
        return "unknown(" + route + ")";
    }
}
