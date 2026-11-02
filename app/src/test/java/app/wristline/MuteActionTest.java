package app.wristline;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MuteActionTest {
    @Test
    public void muteInSeveralLanguages() {
        assertEquals(MuteAction.Kind.MUTE, MuteAction.classify("Mute"));
        assertEquals(MuteAction.Kind.MUTE, MuteAction.classify("Disattiva microfono"));
        assertEquals(MuteAction.Kind.MUTE, MuteAction.classify("Silenciar"));
        assertEquals(MuteAction.Kind.MUTE, MuteAction.classify("Stummschalten"));
    }

    @Test
    public void unmuteIsNotMute() {
        assertEquals(MuteAction.Kind.UNMUTE, MuteAction.classify("Unmute"));
        assertEquals(MuteAction.Kind.UNMUTE, MuteAction.classify("Riattiva microfono"));
        assertEquals(MuteAction.Kind.UNMUTE, MuteAction.classify("ミュート解除"));
        assertEquals(MuteAction.Kind.UNMUTE, MuteAction.classify("取消静音"));
    }

    @Test
    public void otherButtonsAreNotMute() {
        assertEquals(MuteAction.Kind.NONE, MuteAction.classify("Hang up"));
        assertEquals(MuteAction.Kind.NONE, MuteAction.classify("Speaker"));
        assertEquals(MuteAction.Kind.NONE, MuteAction.classify("Commute"));
        assertEquals(MuteAction.Kind.NONE, MuteAction.classify(null));
    }
}
