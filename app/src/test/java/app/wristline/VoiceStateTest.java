package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VoiceStateTest {
    @Test
    public void unverifiedAppStaysOnPhone() {
        assertEquals(VoiceState.NOT_VERIFIED, VoiceState.of(false, true, true, false));
    }

    @Test
    public void brokenNeedsRepair() {
        assertEquals(VoiceState.NEEDS_REPAIR, VoiceState.of(true, true, true, true));
    }

    @Test
    public void on() {
        assertEquals(VoiceState.ON, VoiceState.of(true, true, true, false));
    }

    @Test
    public void notSetUpYet() {
        assertEquals(VoiceState.SET_UP, VoiceState.of(true, true, false, false));
    }

    @Test
    public void appThatCantSeeCallStateNeedsNoSetUp() { // e.g. Instagram: no READ_PHONE_STATE
        assertEquals(VoiceState.ON, VoiceState.of(true, false, false, false));
    }
}
