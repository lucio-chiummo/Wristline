package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class CallHistoryTest {
    @Test
    public void newestFirstCapped() {
        List<CallHistory.Entry> list = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            list = CallHistory.push(list, new CallHistory.Entry(i, true, "com.whatsapp", 7, "Annaly"), 20);
        }
        assertEquals(20, list.size());
        assertEquals(24, list.get(0).time);
    }

    @Test
    public void storesAndReadsBackIncludingAwkwardNames() {
        List<CallHistory.Entry> list = List.of(
                new CallHistory.Entry(1000, false, "com.instagram.android", -1, "Annaly | Work"),
                new CallHistory.Entry(900, true, "com.whatsapp", 317, "Galvane🌸 Orbit"));
        List<CallHistory.Entry> back = CallHistory.decode(CallHistory.encode(list));
        assertEquals(2, back.size());
        assertEquals("Annaly | Work", back.get(0).name);
        assertFalse(back.get(0).outgoing);
        assertEquals(-1, back.get(0).contactId);
        assertEquals("Galvane🌸 Orbit", back.get(1).name);
        assertTrue(back.get(1).outgoing);
        assertEquals(317, back.get(1).contactId);
    }

    @Test
    public void emptyOrBrokenStorageIsAnEmptyList() {
        assertTrue(CallHistory.decode("").isEmpty());
        assertTrue(CallHistory.decode("garbage").isEmpty());
    }

    @Test
    public void countsCallsStartedFromTheWatch() {
        java.util.List<CallHistory.Entry> history = java.util.List.of(
                new CallHistory.Entry(3, true, "com.whatsapp", 1, "A"),
                new CallHistory.Entry(2, false, "com.whatsapp", -1, "B"),
                new CallHistory.Entry(1, true, "org.telegram.messenger", 2, "C"));
        org.junit.Assert.assertEquals(2, CallHistory.fromWatch(history));
        org.junit.Assert.assertEquals(0, CallHistory.fromWatch(java.util.List.of()));
    }

    @Test
    public void missedSurvivesStorageAndOldLinesStillRead() {
        java.util.List<CallHistory.Entry> history = new java.util.ArrayList<>();
        history.add(new CallHistory.Entry(200, false, "com.whatsapp", 7, "Corvan"));
        history.add(new CallHistory.Entry(100, true, "com.whatsapp", 7, "Corvan"));

        java.util.List<CallHistory.Entry> marked = CallHistory.markMissed(history, 200);
        java.util.List<CallHistory.Entry> back = CallHistory.decode(CallHistory.encode(marked));

        org.junit.Assert.assertTrue(back.get(0).missed);
        org.junit.Assert.assertFalse(back.get(1).missed);
        // A line written before "missed" existed.
        org.junit.Assert.assertFalse(CallHistory.decode("5|0|com.whatsapp|-1|Q29ydmFu\n").get(0).missed);
        org.junit.Assert.assertEquals("Corvan", CallHistory.at(back, 200).name);
        org.junit.Assert.assertNull(CallHistory.at(back, 300));
    }

    @Test
    public void onlyIncomingCallsCanBeMissed() {
        java.util.List<CallHistory.Entry> history = new java.util.ArrayList<>();
        history.add(new CallHistory.Entry(100, true, "com.whatsapp", 7, "Corvan"));
        org.junit.Assert.assertFalse(CallHistory.markMissed(history, 100).get(0).missed);
    }
}
