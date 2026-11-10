package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AppBadgeTest {
    @Test
    public void letters() {
        assertEquals("W", AppBadge.letter(Protocol.WHATSAPP));
        assertEquals("T", AppBadge.letter(Protocol.TELEGRAM));
        assertEquals("S", AppBadge.letter(Protocol.SIGNAL));
        assertEquals("I", AppBadge.letter("com.instagram.android"));
        assertNull(AppBadge.letter("com.example"));
    }
}
