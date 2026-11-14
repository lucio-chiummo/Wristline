package app.wristline;

import static org.junit.Assert.assertEquals;

import android.telecom.CallAudioState;

import org.junit.Test;

public class AudioRouteTest {
    @Test
    public void describesRouteAndSupportedMask() {
        assertEquals("route=earpiece supported=earpiece,bluetooth,speaker",
                AudioRoute.describe(CallAudioState.ROUTE_EARPIECE,
                        CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_SPEAKER));
    }

    @Test
    public void emptyMaskSaysNone() {
        assertEquals("route=speaker supported=none", AudioRoute.describe(CallAudioState.ROUTE_SPEAKER, 0));
    }

    @Test
    public void namesEveryRoute() {
        assertEquals("earpiece,bluetooth,wired_headset,speaker,streaming", AudioRoute.names(
                CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_WIRED_HEADSET
                        | CallAudioState.ROUTE_SPEAKER | CallAudioState.ROUTE_STREAMING));
    }
}
