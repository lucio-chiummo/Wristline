package app.wristline;

import static app.wristline.SetupStep.Next.APPLY;
import static app.wristline.SetupStep.Next.ASK_CODE;
import static app.wristline.SetupStep.Next.FAIL_NOT_FOUND;
import static app.wristline.SetupStep.Next.FAIL_NO_WIFI;
import static app.wristline.SetupStep.Next.KEEP_LOOKING;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SetupStepTest {
    @Test
    public void wifiLostFailsAtOnce() { // no busy loop while discovery can't run
        assertEquals(FAIL_NO_WIFI, SetupStep.whileLooking(false, false, false, 100_000));
    }

    @Test
    public void alreadyPairedSkipsPairing() { // repair after Wireless debugging was turned back on
        assertEquals(APPLY, SetupStep.whileLooking(true, true, false, 100_000));
    }

    @Test
    public void pairingServiceFoundAsksForCode() {
        assertEquals(ASK_CODE, SetupStep.whileLooking(true, false, true, 100_000));
    }

    @Test
    public void keepsLookingWhileTimeLeft() {
        assertEquals(KEEP_LOOKING, SetupStep.whileLooking(true, false, false, 100_000));
    }

    @Test
    public void givesUpWhileThereIsStillTimeToReport() { // shortService must stop before its limit
        assertEquals(FAIL_NOT_FOUND, SetupStep.whileLooking(true, false, false, SetupStep.FINISH_MARGIN_MS));
    }
}
