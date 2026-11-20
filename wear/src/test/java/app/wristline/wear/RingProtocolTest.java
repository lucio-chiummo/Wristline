package app.wristline.wear;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The phone's ring message. */
public class RingProtocolTest {
    @Test
    public void pathMatchesThePhone() {
        assertEquals("/wristline/ring", Protocol.PATH_RING);
    }

    @Test
    public void startCarriesTheLongestRing() {
        assertEquals(60_000, Protocol.ringMaxMs("start|60000"));
    }

    @Test
    public void stopAndGarbageAreNotARing() {
        assertEquals(-1, Protocol.ringMaxMs("stop"));
        assertEquals(-1, Protocol.ringMaxMs("start|soon"));
        assertEquals(-1, Protocol.ringMaxMs("start"));
        assertEquals(-1, Protocol.ringMaxMs(""));
        assertEquals(-1, Protocol.ringMaxMs(null));
        assertEquals(-1, Protocol.ringMaxMs("start|-5"));
    }
}
