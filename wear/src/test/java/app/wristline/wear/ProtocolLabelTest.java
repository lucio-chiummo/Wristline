package app.wristline.wear;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProtocolLabelTest {
    @Test
    public void namesEveryCallingApp() {
        assertEquals("WhatsApp", Protocol.label(Protocol.WHATSAPP));
        assertEquals("Telegram", Protocol.label(Protocol.TELEGRAM));
        assertEquals("Instagram", Protocol.label("com.instagram.android"));
        assertEquals("Signal", Protocol.label("org.thoughtcrime.securesms"));
    }
}
