package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PermissionsTest {
    @Test
    public void firstTimeAsksWithThePrompt() {
        assertFalse(Permissions.notificationSettingsInstead(false, false));
    }

    @Test
    public void afterOneDenialStillAsks() {
        assertFalse(Permissions.notificationSettingsInstead(true, true));
    }

    @Test
    public void permanentlyDeniedOpensSettings() { // the prompt no longer appears
        assertTrue(Permissions.notificationSettingsInstead(true, false));
    }
}
