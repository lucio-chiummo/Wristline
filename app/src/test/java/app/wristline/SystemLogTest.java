package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class SystemLogTest {
    @Test
    public void commandsOnlyRead() {
        for (String command : SystemLog.commands(0)) {
            assertTrue(command, command.startsWith("getprop ") || command.startsWith("logcat -d ")
                    || command.startsWith("dumpsys audio ") || command.startsWith("dumpsys bluetooth_manager "));
            assertFalse(command, command.contains(">") && !command.contains("2>/dev/null"));
            assertFalse(command, command.contains(";") || command.contains("&&") || command.contains("`"));
        }
    }

    @Test
    public void startsJustBeforeTheCall() {
        String logcat = SystemLog.commands(1_800_000_000_000L).get(1);

        assertTrue(logcat, logcat.matches("logcat -d -v time -t '\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}' .*"));
        assertTrue(SystemLog.commands(0).get(1).contains("-t " + SystemLog.FALLBACK_SEARCHED + " "));
    }

    @Test
    public void masksAddressesAndLongNumbers() {
        assertEquals("device=XX:XX:XX:XX:XX:9F connected",
                SystemLog.clean("  device=AC:12:0b:44:E1:9F connected"));
        assertEquals("call from # uid 10234", SystemLog.clean("call from +393331234567 uid 10234"));
        assertEquals("11-16 18:23:43.933 route=earpiece", SystemLog.clean("11-16 18:23:43.933 route=earpiece"));
    }

    @Test
    public void shortensLongLines() {
        String cleaned = SystemLog.clean("x".repeat(SystemLog.MAX_LINE + 50));

        assertEquals(SystemLog.MAX_LINE + 1, cleaned.length());
    }

    @Test
    public void formatsEachSection() {
        String report = SystemLog.format(List.of("true\n",
                "11-16 18:23:43.9 W/HeadsetService: isScoAcceptable: rejected SCO for AC:12:0B:44:E1:9F\n",
                "", "mActiveDevice=null\n"), 0);

        assertTrue(report, report.contains("Voice link managed by audio: true\n"));
        assertTrue(report, report.contains("-- Log --\n11-16 18:23:43.9 W/HeadsetService: isScoAcceptable: rejected SCO for XX:XX:XX:XX:XX:9F\n"));
        assertTrue(report, report.contains("-- Audio service --\nnothing\n"));
        assertTrue(report, report.contains("-- Bluetooth hands-free --\nmActiveDevice=null\n"));
    }

    @Test
    public void keepsOnlyTheNewestLines() {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < SystemLog.LOGCAT_LINES + 10; i++) {
            many.append("line ").append(i).append('\n');
        }

        String report = SystemLog.format(List.of("", many.toString(), "", ""), 0);

        assertFalse(report.contains("line 9\n"));
        assertTrue(report.contains("line 10\n"));
        assertTrue(report.contains("Voice link managed by audio: not set\n"));
    }
}
