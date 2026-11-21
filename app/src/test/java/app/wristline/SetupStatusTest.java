package app.wristline;

import static app.wristline.SetupStatus.Kind.CARRIER_OFF;
import static app.wristline.SetupStatus.Kind.NEEDS_REPAIR;
import static app.wristline.SetupStatus.Kind.NOT_RECEIVING;
import static app.wristline.SetupStatus.Kind.PAUSED;
import static app.wristline.SetupStatus.Kind.READY;
import static app.wristline.SetupStatus.Kind.STEPS_LEFT;
import static app.wristline.SetupStatus.Kind.TRY_IT;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SetupStatusTest {
    @Test
    public void pausedWins() {
        assertEquals(PAUSED, SetupStatus.decide(false, 3, false, 1, false, false, true));
    }

    @Test
    public void stepsBeforeRepair() {
        assertEquals(STEPS_LEFT, SetupStatus.decide(true, 1, false, 1, false, false, true));
    }

    @Test
    public void repair() {
        assertEquals(NEEDS_REPAIR, SetupStatus.decide(true, 0, false, 1, true, false, true));
    }

    @Test
    public void allStepsDoneButNothingReachedTheWatchYet() {
        assertEquals(TRY_IT, SetupStatus.decide(true, 0, false, 0, false, false, true));
    }

    @Test
    public void ready() {
        assertEquals(READY, SetupStatus.decide(true, 0, false, 0, true, false, true));
    }

    @Test public void unboundListenerIsNotReady() { assertEquals(NOT_RECEIVING, SetupStatus.decide(true, 0, true, 0, true, false, true)); }
    @Test public void unboundBeforeRepair() { assertEquals(NOT_RECEIVING, SetupStatus.decide(true, 0, true, 1, true, false, true)); }
    @Test public void stepsBeforeUnbound() { assertEquals(STEPS_LEFT, SetupStatus.decide(true, 1, true, 0, true, false, true)); }
    @Test public void pausedWinsOverUnbound() { assertEquals(PAUSED, SetupStatus.decide(false, 0, true, 0, true, false, true)); }

    @Test
    public void aWarPhoneWithTheCarrierOffSaysSo() {
        assertEquals(CARRIER_OFF, SetupStatus.decide(true, 0, false, 0, true, true, false));
    }

    @Test
    public void aWarPhoneWithTheCarrierOnIsReady() {
        assertEquals(READY, SetupStatus.decide(true, 0, false, 0, true, true, true));
    }

    @Test
    public void aPhoneThatNeverWarredDoesNotNeedTheCarrier() {
        assertEquals(READY, SetupStatus.decide(true, 0, false, 0, true, false, false));
    }

    @Test
    public void setupProblemsComeBeforeTheCarrier() {
        assertEquals(STEPS_LEFT, SetupStatus.decide(true, 1, false, 0, true, true, false));
        assertEquals(NEEDS_REPAIR, SetupStatus.decide(true, 0, false, 1, true, true, false));
    }
}
