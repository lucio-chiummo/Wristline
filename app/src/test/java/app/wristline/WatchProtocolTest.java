package app.wristline;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class WatchProtocolTest {
    @Test
    public void parsesTheWatchCallMessage() {
        WatchProtocol.CallRequest call = WatchProtocol.parseCall("42|com.whatsapp".getBytes(StandardCharsets.UTF_8));
        assertEquals(42, call.contactId);
        assertEquals(SupportedApp.WHATSAPP, call.app);
    }

    @Test
    public void rejectsGarbageAndAppsThatCantCall() {
        assertNull(WatchProtocol.parseCall("nope".getBytes(StandardCharsets.UTF_8)));
        assertNull(WatchProtocol.parseCall("x|com.whatsapp".getBytes(StandardCharsets.UTF_8)));
        assertNull(WatchProtocol.parseCall("3|com.instagram.android".getBytes(StandardCharsets.UTF_8)));
        assertNull(WatchProtocol.parseCall(null));
    }

    @Test
    public void recentListNewestFirstNoDuplicatesCapped() {
        long[] recent = WatchProtocol.pushRecent(new long[] {5, 3, 7}, 3, 3);
        assertArrayEquals(new long[] {3, 5, 7}, recent);
        assertArrayEquals(new long[] {9, 3, 5}, WatchProtocol.pushRecent(recent, 9, 3));
    }

    @Test
    public void callStateCarriesTheTimeOnlyWhenActive() {
        assertEquals("active;4200;off", WatchProtocol.callState(WatchProtocol.STATE_ACTIVE, 4200, WatchProtocol.MUTE_OFF));
        assertEquals("ringing;;on", WatchProtocol.callState(WatchProtocol.STATE_RINGING, 4200, WatchProtocol.MUTE_ON));
        assertEquals("ended;;", WatchProtocol.callState(WatchProtocol.STATE_ENDED, -1, null));
        assertEquals("active;;", WatchProtocol.callState(WatchProtocol.STATE_ACTIVE, -1, WatchProtocol.MUTE_NONE));
    }

    @Test
    public void callStatePayloadKeepsBarsInTheName() {
        assertEquals("calling;;|com.whatsapp|A | B", new String(WatchProtocol.callStatePayload(
                WatchProtocol.callState(WatchProtocol.STATE_CALLING, -1, null), "com.whatsapp", "A | B"),
                java.nio.charset.StandardCharsets.UTF_8));
    }
}
