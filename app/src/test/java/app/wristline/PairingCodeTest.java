package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PairingCodeTest {
    @Test
    public void plainSixDigits() {
        assertEquals("123456", PairingCode.normalize("123456"));
    }

    @Test
    public void spacesAndDashesIgnored() {
        assertEquals("123456", PairingCode.normalize(" 123 456 "));
        assertEquals("123456", PairingCode.normalize("123-456"));
    }

    @Test
    public void wrongLengthRejected() {
        assertNull(PairingCode.normalize("12345"));
        assertNull(PairingCode.normalize("1234567"));
    }

    @Test
    public void lettersRejected() {
        assertNull(PairingCode.normalize("12a456"));
    }

    @Test
    public void nullRejected() {
        assertNull(PairingCode.normalize(null));
    }
}
