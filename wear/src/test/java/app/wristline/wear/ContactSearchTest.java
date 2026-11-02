package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

public class ContactSearchTest {
    private static WatchContact c(long id, String name) {
        return new WatchContact(id, name, List.of(Protocol.WHATSAPP));
    }

    @Test
    public void matchesAnyWordStartIgnoringCaseAndAccents() {
        assertTrue(ContactSearch.matches("Annaly Biavon", "bia"));
        assertTrue(ContactSearch.matches("Annaly Biavon", "AN"));
        assertTrue(ContactSearch.matches("Nivolò", "nivolo"));
        assertFalse(ContactSearch.matches("Annaly Biavon", "nna"));
    }

    @Test
    public void emptyQueryMatchesEveryone() {
        assertTrue(ContactSearch.matches("Annaly", "  "));
    }

    @Test
    public void filtersAList() {
        List<WatchContact> all = List.of(c(1, "Annaly Biavon"), c(2, "Corvan"), c(3, "Biarra"));
        assertEquals(List.of("Annaly Biavon", "Biarra"),
                ContactSearch.filter(all, "bi").stream().map(x -> x.name).collect(Collectors.toList()));
    }

    @Test
    public void favouritesInListOrder() {
        List<WatchContact> all = List.of(c(1, "Annaly"), c(2, "Corvan"), c(3, "Lumen"));
        assertEquals(List.of("Annaly", "Lumen"),
                ContactOrder.favourites(all, java.util.Set.of(3L, 1L, 99L)).stream().map(x -> x.name).collect(Collectors.toList()));
    }
}
