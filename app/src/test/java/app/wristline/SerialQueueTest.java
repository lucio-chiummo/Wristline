package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Android 12/13 allow one mDNS resolve at a time; a second one fails with FAILURE_ALREADY_ACTIVE. */
public class SerialQueueTest {
    @Test
    public void firstStartsAtOnce() {
        SerialQueue<String> q = new SerialQueue<>();
        assertEquals("other-phone", q.offer("other-phone"));
    }

    @Test
    public void secondWaitsUntilFirstIsDone() {
        SerialQueue<String> q = new SerialQueue<>();
        q.offer("other-phone");
        assertNull(q.offer("this-phone"));
        assertEquals("this-phone", q.done());
        assertNull(q.done());
        assertEquals("third", q.offer("third"));
    }
}
