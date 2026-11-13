package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AudioRouteTest {
    @Test
    public void namesTheRouteAndWhatWasAvailable() {
        assertEquals("bluetooth of earpiece+bluetooth+speaker",
                AudioRoute.describe(AudioRoute.BLUETOOTH, AudioRoute.EARPIECE | AudioRoute.BLUETOOTH | AudioRoute.SPEAKER));
        assertEquals("earpiece of earpiece+speaker",
                AudioRoute.describe(AudioRoute.EARPIECE, AudioRoute.EARPIECE | AudioRoute.SPEAKER));
    }

    @Test
    public void oddValuesStillReadable() {
        assertEquals("unknown(0) of none", AudioRoute.describe(0, 0));
    }
}
