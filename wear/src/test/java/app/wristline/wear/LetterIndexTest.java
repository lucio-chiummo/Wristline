package app.wristline.wear;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class LetterIndexTest {
    @Test
    public void lettersInOrderWithTheirFirstPosition() {
        LetterIndex index = LetterIndex.of(List.of("Annaly", "Axton", "Corvan", "Cirelle", "Zephra"));
        assertEquals(List.of("A", "C", "Z"), index.letters());
        assertEquals(0, index.position("A"));
        assertEquals(2, index.position("C"));
        assertEquals(4, index.position("Z"));
    }

    @Test
    public void accentsAndCaseCountAsThePlainLetter() {
        LetterIndex index = LetterIndex.of(List.of("Brindle", "éldrin", "Émbric"));
        assertEquals(List.of("B", "E"), index.letters());
        assertEquals(1, index.position("E"));
    }

    @Test
    public void namesThatDontStartWithALetterGoUnderHash() {
        LetterIndex index = LetterIndex.of(List.of("1Password", "Annaly"));
        assertEquals(List.of("#", "A"), index.letters());
    }

    @Test
    public void atFractionPicksTheLetterUnderTheFinger() {
        LetterIndex index = LetterIndex.of(List.of("Annaly", "Corvan", "Zephra"));
        assertEquals("A", index.atFraction(0f));
        assertEquals("C", index.atFraction(0.5f));
        assertEquals("Z", index.atFraction(1f));
        assertEquals("A", index.atFraction(-3f));
        assertEquals("Z", index.atFraction(9f));
    }

    @Test
    public void emptyHasNoLetters() {
        assertEquals(List.of(), LetterIndex.of(List.of()).letters());
    }
}
