package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedialActionTest {
    @Test
    public void callAgainInSeveralLanguages() {
        assertTrue(RedialAction.is("Call again"));
        assertTrue(RedialAction.is("Richiama"));
        assertTrue(RedialAction.is("Chiama di nuovo"));
        assertTrue(RedialAction.is("Volver a llamar"));
        assertTrue(RedialAction.is("Erneut anrufen"));
        assertTrue(RedialAction.is("Rappeler"));
    }

    @Test
    public void hangUpAndOtherButtonsAreNotRedial() {
        assertFalse(RedialAction.is("Hang up"));
        assertFalse(RedialAction.is("Riaggancia"));
        assertFalse(RedialAction.is("Termina chiamata"));
        assertFalse(RedialAction.is("Annulla"));
        assertFalse(RedialAction.is("Altoparlante"));
        assertFalse(RedialAction.is("Disattiva microfono"));
        assertFalse(RedialAction.is(null));
    }
}
