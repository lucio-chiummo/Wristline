package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InstallCheckTest {
    @Test
    public void sameInstall() {
        assertEquals(InstallCheck.Result.SAME, InstallCheck.compare(1000, 1000));
    }

    @Test
    public void reinstalled() {
        assertEquals(InstallCheck.Result.REINSTALLED, InstallCheck.compare(1000, 2000));
    }

    @Test
    public void nothingRememberedYet() {
        assertEquals(InstallCheck.Result.REMEMBER, InstallCheck.compare(0, 2000));
    }

    @Test
    public void unknownCurrentTimeChangesNothing() {
        assertEquals(InstallCheck.Result.SAME, InstallCheck.compare(1000, 0));
        assertEquals(InstallCheck.Result.SAME, InstallCheck.compare(0, -1));
    }
}
