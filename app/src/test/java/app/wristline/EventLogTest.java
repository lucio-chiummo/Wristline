package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EventLogTest {
    @Test
    public void newestFirstAndCapped() {
        List<EventLog.Entry> log = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            log = EventLog.push(log, new EventLog.Entry(i, EventLog.INCOMING, "com.whatsapp", ""), 3);
        }
        assertEquals(3, log.size());
        assertEquals(4, log.get(0).time);
        assertEquals(2, log.get(2).time);
    }

    @Test
    public void roundTripKeepsEverythingIncludingSeparators() {
        List<EventLog.Entry> log = new ArrayList<>();
        log.add(new EventLog.Entry(1700000000000L, EventLog.UNREADABLE_CALL, "com.whatsapp",
                "actions=[Rispondi|Rifiuta]\nsecond line"));
        log.add(new EventLog.Entry(1700000001000L, EventLog.LISTENER_REBIND, "", ""));

        List<EventLog.Entry> back = EventLog.decode(EventLog.encode(log));

        assertEquals(2, back.size());
        assertEquals("actions=[Rispondi|Rifiuta]\nsecond line", back.get(0).detail);
        assertEquals(EventLog.UNREADABLE_CALL, back.get(0).code);
        assertEquals("", back.get(1).app);
        assertEquals("", back.get(1).detail);
    }

    @Test
    public void damagedOrEmptyStorageIsSkipped() {
        assertTrue(EventLog.decode("").isEmpty());
        assertTrue(EventLog.decode(null).isEmpty());
        assertEquals(1, EventLog.decode("garbage\n12|incoming|com.whatsapp|\nx|y|z|w\n").size());
    }

    @Test
    public void formatLeavesOutEmptyParts() {
        String line = EventLog.format(new EventLog.Entry(0, EventLog.LISTENER_CONNECTED, "", ""));
        assertTrue(line.endsWith(" listener_connected"));
        String withApp = EventLog.format(new EventLog.Entry(0, EventLog.INCOMING, "com.whatsapp", "full mode"));
        assertTrue(withApp.endsWith(" incoming com.whatsapp full mode"));
    }
}
