package app.wristline;

import static app.wristline.SetupStatus.Kind.NEEDS_REPAIR;
import static app.wristline.SetupStatus.Kind.PAUSED;
import static app.wristline.SetupStatus.Kind.READY;
import static app.wristline.SetupStatus.Kind.STEPS_LEFT;
import static app.wristline.SetupStatus.Kind.TRY_IT;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SetupStatusTest {
    @Test
    public void pausedWins() {
        assertEquals(PAUSED, SetupStatus.decide(false, 3, 1, false));
    }

    @Test
    public void stepsBeforeRepair() {
        assertEquals(STEPS_LEFT, SetupStatus.decide(true, 1, 1, false));
    }

    @Test
    public void repair() {
        assertEquals(NEEDS_REPAIR, SetupStatus.decide(true, 0, 1, true));
    }

    @Test
    public void allStepsDoneButNothingReachedTheWatchYet() {
        assertEquals(TRY_IT, SetupStatus.decide(true, 0, 0, false));
    }

    @Test
    public void ready() {
        assertEquals(READY, SetupStatus.decide(true, 0, 0, true));
    }
}
