package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppOpsCommandsTest {
    @Test
    public void commands() {
        assertEquals("appops set --uid com.whatsapp READ_PHONE_STATE ignore", AppOpsCommands.set("com.whatsapp"));
        assertEquals("appops set --uid com.whatsapp READ_PHONE_STATE allow", AppOpsCommands.reset("com.whatsapp"));
        assertEquals("adb shell appops set --uid com.whatsapp READ_PHONE_STATE ignore",
                AppOpsCommands.adbLine("com.whatsapp"));
    }
}
