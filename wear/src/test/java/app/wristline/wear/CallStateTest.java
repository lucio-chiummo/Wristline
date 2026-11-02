package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallStateTest {
    @Test
    public void activeWithTimeAndMuteOff() {
        Protocol.CallState state = Protocol.CallState.parse("active;4200;off");
        assertEquals(Protocol.STATE_ACTIVE, state.kind);
        assertEquals(4200, state.connectedMs);
        assertTrue(state.canMute);
        assertFalse(state.muted);
    }

    @Test
    public void ringingWithoutTimeAndMuted() {
        Protocol.CallState state = Protocol.CallState.parse("ringing;;on");
        assertEquals(Protocol.STATE_RINGING, state.kind);
        assertEquals(-1, state.connectedMs);
        assertTrue(state.canMute);
        assertTrue(state.muted);
    }

    @Test
    public void noMuteButton() {
        assertFalse(Protocol.CallState.parse("active;10;").canMute);
    }

    @Test
    public void bareStateFromAnOlderPhoneApp() {
        Protocol.CallState state = Protocol.CallState.parse("ended");
        assertEquals(Protocol.STATE_ENDED, state.kind);
        assertEquals(-1, state.connectedMs);
        assertFalse(state.canMute);
    }

    @Test
    public void garbageTimeIsUnknownAndNegativeIsZero() {
        assertEquals(-1, Protocol.CallState.parse("active;abc;off").connectedMs);
        assertEquals(0, Protocol.CallState.parse("active;-5;off").connectedMs);
    }
}
