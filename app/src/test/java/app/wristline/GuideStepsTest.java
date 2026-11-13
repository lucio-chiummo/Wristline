package app.wristline;

import static app.wristline.GuideSteps.Status.CURRENT;
import static app.wristline.GuideSteps.Status.DONE;
import static app.wristline.GuideSteps.Status.FAILED;
import static app.wristline.GuideSteps.Status.TODO;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Steps: 1 Notifications (the pairing code is typed into one), 2 Wi-Fi, 3 Developer options,
 * 4 Wireless debugging and pairing (one trip to Settings: Wristline notices Wireless debugging going
 * on by itself).
 */
public class GuideStepsTest {
    @Test
    public void startsAtNotifications() {
        assertArrayEquals(new GuideSteps.Status[] {CURRENT, TODO, TODO, TODO},
                GuideSteps.of(false, false, false, SetupProgress.Phase.IDLE));
    }

    @Test
    public void notificationsOffHoldsBackPairingEvenWithTheRestOn() {
        assertArrayEquals(new GuideSteps.Status[] {CURRENT, DONE, DONE, TODO},
                GuideSteps.of(false, true, true, SetupProgress.Phase.IDLE));
    }

    @Test
    public void wifiNext() {
        assertArrayEquals(new GuideSteps.Status[] {DONE, CURRENT, TODO, TODO},
                GuideSteps.of(true, false, false, SetupProgress.Phase.IDLE));
    }

    @Test
    public void developerOptionsNext() {
        assertArrayEquals(new GuideSteps.Status[] {DONE, DONE, CURRENT, TODO},
                GuideSteps.of(true, true, false, SetupProgress.Phase.IDLE));
    }

    @Test
    public void wirelessDebuggingAndPairingIsOneStep() {
        assertArrayEquals(new GuideSteps.Status[] {DONE, DONE, DONE, CURRENT},
                GuideSteps.of(true, true, true, SetupProgress.Phase.IDLE));
        assertArrayEquals(new GuideSteps.Status[] {DONE, DONE, DONE, CURRENT},
                GuideSteps.of(true, true, true, SetupProgress.Phase.WAITING_FOR_CODE));
    }

    @Test
    public void failureShowsOnTheLastStepAndKeepsTheOthersDone() {
        assertArrayEquals(new GuideSteps.Status[] {DONE, DONE, DONE, FAILED},
                GuideSteps.of(true, true, true, SetupProgress.Phase.FAILED));
    }

    @Test
    public void doneIsAllTicked() {
        assertArrayEquals(new GuideSteps.Status[] {DONE, DONE, DONE, DONE},
                GuideSteps.of(true, true, true, SetupProgress.Phase.DONE));
    }

    @Test
    public void wifiLostGoesBackToItsStep() { // Wireless debugging switches itself off too
        assertArrayEquals(new GuideSteps.Status[] {DONE, CURRENT, DONE, TODO},
                GuideSteps.of(true, false, true, SetupProgress.Phase.FAILED));
    }

    @Test
    public void developerOptionsOnWhenOnlyAdbEnabled() {
        assertTrue(GuideSteps.developerOptionsOn(0, 1, 0, false));
    }

    @Test
    public void developerOptionsOnWhenOnlyWirelessDebugging() {
        assertTrue(GuideSteps.developerOptionsOn(0, 0, 1, false));
    }

    @Test
    public void developerOptionsOnWhenUserConfirmed() {
        assertTrue(GuideSteps.developerOptionsOn(0, 0, 0, true));
    }

    @Test
    public void developerOptionsOffWhenNothingSet() {
        assertFalse(GuideSteps.developerOptionsOn(0, 0, 0, false));
    }

    @Test
    public void developerOptionsOnWithStandardFlag() {
        assertTrue(GuideSteps.developerOptionsOn(1, 0, 0, false));
    }
}
