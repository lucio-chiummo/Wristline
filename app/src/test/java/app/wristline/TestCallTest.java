package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestCallTest {
    @Test
    public void startsWhenNothingElseIsOn() {
        assertEquals(TestCall.Start.OK, TestCall.check(false, false));
    }

    @Test
    public void refusedDuringARealCall() {
        assertEquals(TestCall.Start.BUSY, TestCall.check(true, false));
        assertEquals(TestCall.Start.BUSY, TestCall.check(false, true));
    }

    @Test
    public void beepsEndBeforeTheCallHangsUp() {
        long last = TestCall.BEEPS_AT[TestCall.BEEPS_AT.length - 1] + TestCall.BEEP_MS;
        assertTrue(last < TestCall.ANSWERED_MS);
        for (int i = 1; i < TestCall.BEEPS_AT.length; i++) {
            assertTrue(TestCall.BEEPS_AT[i] >= TestCall.BEEPS_AT[i - 1] + TestCall.BEEP_MS);
        }
    }
}
