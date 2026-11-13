package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TelecomNamesTest {
    @Test
    public void statesAndCauses() {
        assertEquals("ringing", TelecomNames.state(2));
        assertEquals("active", TelecomNames.state(4));
        assertEquals("state(42)", TelecomNames.state(42));
        assertEquals("rejected", TelecomNames.cause(6));
        assertEquals("answered_elsewhere", TelecomNames.cause(11));
        assertEquals("cause(-1)", TelecomNames.cause(-1));
    }

    @Test
    public void companionsNamedWhenKnown() {
        assertEquals("OHealth (com.heytap.health.international 4.29)",
                Companions.describe("com.heytap.health.international", "4.29"));
        assertEquals("com.example.watch 1.0", Companions.describe("com.example.watch", "1.0"));
    }
}
