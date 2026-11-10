package app.wristline.wear;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The home screen's buttons and the lists behind them: one per app, plus favourites. */
final class AppLists {
    /** List keys besides the app packages. */
    static final String FAVOURITES = "favourites";
    static final String RECENT = "recent";

    /** Button order on the home screen. */
    private static final List<String> ORDER = List.of(FAVOURITES, Protocol.WHATSAPP, Protocol.TELEGRAM, Protocol.SIGNAL);

    private AppLists() {
    }

    /** The people reachable on {@code app}, in the order given (A–Z from the phone). */
    static List<WatchContact> forApp(List<WatchContact> all, String app) {
        List<WatchContact> out = new ArrayList<>();
        for (WatchContact contact : all) {
            if (contact.apps.contains(app)) {
                out.add(contact);
            }
        }
        return out;
    }

    /** The buttons to show: the ones with at least one person behind them. */
    static List<String> buttons(List<WatchContact> all, Set<Long> favouriteIds) {
        List<String> out = new ArrayList<>();
        for (String key : ORDER) {
            if (count(all, favouriteIds, key) > 0) {
                out.add(key);
            }
        }
        return out;
    }

    /** People behind a button; favourites count only those still among the contacts. */
    static int count(List<WatchContact> all, Set<Long> favouriteIds, String key) {
        int n = 0;
        for (WatchContact contact : all) {
            if (FAVOURITES.equals(key) ? favouriteIds.contains(contact.id) : contact.apps.contains(key)) {
                n++;
            }
        }
        return n;
    }
}
