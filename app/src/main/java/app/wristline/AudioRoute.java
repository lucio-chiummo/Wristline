package app.wristline;

import android.telecom.CallAudioState;

/** Telecom audio routes as the diagnostic report writes them: route names only, never device names. */
final class AudioRoute {
    private static final int[] ROUTES = {
            CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_BLUETOOTH, CallAudioState.ROUTE_WIRED_HEADSET,
            CallAudioState.ROUTE_SPEAKER, CallAudioState.ROUTE_STREAMING};
    private static final String[] NAMES = {"earpiece", "bluetooth", "wired_headset", "speaker", "streaming"};

    private AudioRoute() {
    }

    /** "route=earpiece supported=earpiece,bluetooth". */
    static String describe(int route, int supportedMask) {
        return "route=" + names(route) + " supported=" + names(supportedMask);
    }

    /** The routes in {@code mask}, in Telecom's bit order; "none" when it's empty. */
    static String names(int mask) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ROUTES.length; i++) {
            if ((mask & ROUTES[i]) != 0) {
                out.append(out.length() > 0 ? "," : "").append(NAMES[i]);
            }
        }
        return out.length() == 0 ? "none" : out.toString();
    }
}
