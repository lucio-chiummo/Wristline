package app.wristline.wear;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

public class ContactOrderTest {
    private static final WatchContact ANNALY = new WatchContact(3, "Annaly", List.of("com.whatsapp", "org.telegram.messenger"));
    private static final WatchContact CORVAN = new WatchContact(5, "Corvan", List.of("com.whatsapp"));
    private static final WatchContact LUMEN = new WatchContact(7, "Lumen", List.of("org.telegram.messenger"));

    private static List<String> names(List<WatchContact> contacts) {
        return contacts.stream().map(c -> c.name).collect(Collectors.toList());
    }

    @Test
    public void recentPeopleInCallOrderForTheTile() {
        assertEquals(List.of("Lumen", "Annaly"),
                names(ContactOrder.recent(List.of(ANNALY, CORVAN, LUMEN), List.of(7L, 3L), 5)));
    }

    @Test
    public void tileSkipsContactsThatAreGoneAndCapsTheCount() {
        assertEquals(List.of("Corvan"), names(ContactOrder.recent(List.of(ANNALY, CORVAN), List.of(99L, 5L, 3L), 1)));
    }

    @Test
    public void listShowsRecentFirstThenEveryoneElseWithoutDuplicates() {
        assertEquals(List.of("Lumen", "Annaly", "Corvan"),
                names(ContactOrder.list(List.of(ANNALY, CORVAN, LUMEN), List.of(7L))));
    }

    @Test
    public void quickPutsTheLastCallerFirstThenFavouritesThenRecentWithoutRepeats() {
        assertEquals(List.of("Lumen", "Corvan", "Annaly"),
                names(ContactOrder.quick(List.of(CORVAN), List.of(LUMEN, CORVAN, ANNALY), 5)));
    }

    @Test
    public void quickKeepsTheLastCallerEvenWhenFavouritesFillTheTile() {
        assertEquals(List.of("Lumen", "Annaly"),
                names(ContactOrder.quick(List.of(ANNALY, CORVAN), List.of(LUMEN), 2)));
    }

    @Test
    public void quickCapsTheCount() {
        assertEquals(List.of("Annaly", "Corvan"),
                names(ContactOrder.quick(List.of(ANNALY, CORVAN, LUMEN), List.of(), 2)));
    }

    @Test
    public void quickWithNothingIsEmpty() {
        assertEquals(List.of(), names(ContactOrder.quick(List.of(), List.of(), 5)));
    }
}
