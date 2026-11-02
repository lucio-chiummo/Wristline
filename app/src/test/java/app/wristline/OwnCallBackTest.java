package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class OwnCallBackTest {
    @Test
    public void findsTheButtonLabelledCallBack() {
        assertEquals(1, OwnCallBack.index(List.of("Message", "Call back")));
        assertEquals(0, OwnCallBack.index(List.of("Richiama", "Messaggio")));
    }

    @Test
    public void withTwoUnknownLabelsTheSecondIsCallBack() {
        assertEquals(1, OwnCallBack.index(List.of("Nachricht", "Zurückrufen")));
    }

    @Test
    public void noCallBackWhenTheButtonsAreNotTwoOrNotRecognised() {
        assertEquals(-1, OwnCallBack.index(List.of("Message")));
        assertEquals(-1, OwnCallBack.index(List.of("A", "B", "C")));
        assertEquals(-1, OwnCallBack.index(List.of()));
    }

    @Test
    public void aCapturedActionCountsOnlyForTheSameAppAndACallBeforeIt() {
        OwnCallBack.clear();
        OwnCallBack.put("com.instagram.android", new Object(), 5000);
        assertTrue(OwnCallBack.has("com.instagram.android", 1000));
        assertFalse(OwnCallBack.has("com.instagram.android", 6000));
        assertFalse(OwnCallBack.has("com.whatsapp", 1000));
    }

    @Test
    public void nothingCapturedMeansNoCallBack() {
        OwnCallBack.clear();
        assertFalse(OwnCallBack.has("com.instagram.android", 0));
    }
}
