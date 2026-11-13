package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppOpsCommandsTest {
    @Test
    public void commands() {
        assertEquals("appops set --uid com.whatsapp READ_PHONE_STATE ignore", AppOpsCommands.set("com.whatsapp"));
        assertEquals("appops set --uid com.whatsapp READ_PHONE_STATE allow", AppOpsCommands.reset("com.whatsapp"));
        assertEquals("adb shell appops set --uid com.whatsapp READ_PHONE_STATE ignore",
                AppOpsCommands.adbLine("com.whatsapp"));
    }

    @Test
    public void restartUsesAmKillNotForceStop() {
        assertEquals("am kill com.whatsapp", AppOpsCommands.restart("com.whatsapp"));
    }

    @Test
    public void getReadsTheUidOp() {
        assertEquals("appops get --uid com.whatsapp READ_PHONE_STATE", AppOpsCommands.get("com.whatsapp"));
    }

    @Test
    public void isIgnoredReadsUidMode() {
        assertTrue(AppOpsCommands.isIgnored("Uid mode: READ_PHONE_STATE: ignore\nREAD_PHONE_STATE: allow; time=1"));
        assertFalse(AppOpsCommands.isIgnored("READ_PHONE_STATE: allow"));
    }

    @Test
    public void isAllowedAcceptsAllowAndNoOperations() {
        assertTrue(AppOpsCommands.isAllowed("No operations."));
        assertTrue(AppOpsCommands.isAllowed("READ_PHONE_STATE: allow"));
        assertFalse(AppOpsCommands.isAllowed("Uid mode: READ_PHONE_STATE: ignore"));
    }
}
