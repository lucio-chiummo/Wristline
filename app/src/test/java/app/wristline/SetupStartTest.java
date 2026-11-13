package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The setup service has ~3 minutes, so it starts once Wireless debugging is on (seen from Settings too). */
public class SetupStartTest {
    @Test
    public void startsOnceEverythingIsOn() {
        assertTrue(SetupStart.ready(true, true, true, true));
    }

    @Test
    public void waitsForWirelessDebugging() {
        assertFalse(SetupStart.ready(true, true, true, false));
    }

    @Test
    public void waitsForWifiAndDeveloperOptions() {
        assertFalse(SetupStart.ready(true, false, true, true));
        assertFalse(SetupStart.ready(true, true, false, true));
    }

    @Test
    public void waitsForNotifications() { // the pairing code is typed into a notification
        assertFalse(SetupStart.ready(false, true, true, true));
    }
}
