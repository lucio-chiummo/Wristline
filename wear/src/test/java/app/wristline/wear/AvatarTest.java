package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class AvatarTest {
    @Test
    public void firstAndLastWord() {
        assertEquals("AB", Avatar.initials("Annaly Biavon"));
        assertEquals("AR", Avatar.initials("annaly quill roskin"));
    }

    @Test
    public void singleWord() {
        assertEquals("C", Avatar.initials("Corvan"));
    }

    @Test
    public void skipsSymbolsAndEmoji() {
        assertEquals("M", Avatar.initials("Mirelle ❤️"));
        assertEquals("LU", Avatar.initials("(Lumen) 😀 Uplink"));
    }

    @Test
    public void emptyOrNoLetters() {
        assertEquals("?", Avatar.initials(""));
        assertEquals("?", Avatar.initials("   "));
        assertEquals("?", Avatar.initials("☎️"));
        assertEquals("?", Avatar.initials(null));
    }

    @Test
    public void nonLatinLetters() {
        assertEquals("ИП", Avatar.initials("Ивар Полозов"));
        assertEquals("林", Avatar.initials("林"));
    }

    @Test
    public void shortNameIsTheFirstWordUpToSeven() {
        assertEquals("Annaly", Avatar.shortName("Annaly Biavon"));
        assertEquals("Marivan", Avatar.shortName("Marivanderos"));
        assertEquals("Mirelle", Avatar.shortName("  Mirelle "));
        assertEquals("", Avatar.shortName(null));
        // Never cuts an emoji in half.
        assertEquals("Ciao\uD83C\uDF38ab", Avatar.shortName("Ciao\uD83C\uDF38abcdef"));
    }

    @Test
    public void colourIsStableAndCaseInsensitive() {
        assertEquals(Avatar.colorIndex("Corvan"), Avatar.colorIndex("Corvan"));
        assertEquals(Avatar.colorIndex("corvan"), Avatar.colorIndex("CORVAN"));
    }

    @Test
    public void coloursSpreadAndStayInRange() {
        Set<Integer> seen = new HashSet<>();
        for (String name : new String[] {"Annaly", "Corvan", "Galvane", "Lumen", "Mirelle", "Perì", "Merrick", "Sorrel", "Elowyn", "Pellam"}) {
            int index = Avatar.colorIndex(name);
            assertTrue(index >= 0 && index < Avatar.COLORS);
            seen.add(index);
        }
        assertTrue(seen.size() >= 4);
    }
}
