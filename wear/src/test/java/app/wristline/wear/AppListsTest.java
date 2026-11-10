package app.wristline.wear;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.Test;

public class AppListsTest {
    private final WatchContact a = new WatchContact(1, "Annaly", List.of(Protocol.WHATSAPP, Protocol.SIGNAL));
    private final WatchContact b = new WatchContact(2, "Corvan", List.of(Protocol.WHATSAPP));
    private final WatchContact c = new WatchContact(3, "Dessa", List.of(Protocol.SIGNAL));

    @Test
    public void forAppKeepsOrderAndOverlap() {
        assertEquals(List.of(a, c), AppLists.forApp(List.of(a, b, c), Protocol.SIGNAL));
        assertEquals(List.of(a, b), AppLists.forApp(List.of(a, b, c), Protocol.WHATSAPP));
    }

    @Test
    public void buttonsSkipEmpty() {
        assertEquals(List.of(Protocol.WHATSAPP, Protocol.SIGNAL), AppLists.buttons(List.of(a, b, c), Set.of()));
        assertEquals(List.of(AppLists.FAVOURITES, Protocol.WHATSAPP, Protocol.SIGNAL),
                AppLists.buttons(List.of(a, b, c), Set.of(b.id)));
    }

    @Test
    public void favouriteCountIgnoresGoneContacts() {
        assertEquals(1, AppLists.count(List.of(a, b), Set.of(a.id, 99L), AppLists.FAVOURITES));
    }

    @Test
    public void appCount() {
        assertEquals(2, AppLists.count(List.of(a, b, c), Set.of(), Protocol.SIGNAL));
        assertEquals(0, AppLists.count(List.of(a, b, c), Set.of(), Protocol.TELEGRAM));
    }
}
