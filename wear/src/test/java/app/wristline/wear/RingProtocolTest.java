package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    @Test
    public void parsesAFullRing() {
        Protocol.Ring ring = Protocol.parseRing("start|60000|com.whatsapp|Ann | Bob");

        assertEquals(60_000, ring.maxMs);
        assertEquals("com.whatsapp", ring.app);
        assertEquals("the name is last and may hold a bar", "Ann | Bob", ring.name);
    }

    /** An older phone app sends no caller: the watch only buzzes. */
    @Test
    public void parsesAnOldRing() {
        Protocol.Ring ring = Protocol.parseRing("start|60000");

        assertEquals(60_000, ring.maxMs);
        assertNull(ring.app);
        assertNull(ring.name);
        Protocol.Ring empty = Protocol.parseRing("start|60000||");
        assertNull(empty.app);
        assertNull(empty.name);
    }

    @Test
    public void stopAndGarbageAreNull() {
        assertNull(Protocol.parseRing("stop"));
        assertNull(Protocol.parseRing("start|soon|com.whatsapp|Ann"));
        assertNull(Protocol.parseRing("start"));
        assertNull(Protocol.parseRing(""));
        assertNull(Protocol.parseRing(null));
        assertNull(Protocol.parseRing("start|-5"));
    }

    @Test
    public void answerAndDeclinePathsMatchThePhone() {
        assertEquals("/wristline/answer", Protocol.PATH_ANSWER);
        assertEquals("/wristline/decline", Protocol.PATH_DECLINE);
    }
}
