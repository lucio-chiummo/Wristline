package app.wristline.wear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Order of contacts on the watch: recently called people first. */
final class ContactOrder {
    private ContactOrder() {
    }

    /** The recently called people still in the list, most recent first, at most {@code limit}. */
    static List<WatchContact> recent(List<WatchContact> all, List<Long> recentIds, int limit) {
        Map<Long, WatchContact> byId = new LinkedHashMap<>();
        for (WatchContact contact : all) {
            byId.put(contact.id, contact);
        }
        List<WatchContact> recent = new ArrayList<>();
        for (Long id : recentIds) {
            WatchContact contact = byId.get(id);
            if (contact != null && recent.size() < limit) {
                recent.add(contact);
            }
        }
        return recent;
    }

    /**
     * For the tile: the last person called first (one tap calls them back), then favourites, then
     * the other recent people, at most {@code limit}.
     */
    static List<WatchContact> quick(List<WatchContact> favourites, List<WatchContact> recent, int limit) {
        List<WatchContact> out = new ArrayList<>();
        if (!recent.isEmpty() && limit > 0) {
            out.add(recent.get(0));
        }
        for (WatchContact contact : favourites) {
            if (out.size() < limit && !out.contains(contact)) {
                out.add(contact);
            }
        }
        for (WatchContact contact : recent) {
            if (out.size() < limit && !out.contains(contact)) {
                out.add(contact);
            }
        }
        return out;
    }

    /** Starred people, in the phone's (A–Z) order. */
    static List<WatchContact> favourites(List<WatchContact> all, java.util.Set<Long> starred) {
        List<WatchContact> out = new ArrayList<>();
        for (WatchContact contact : all) {
            if (starred.contains(contact.id)) {
                out.add(contact);
            }
        }
        return out;
    }

    /** Recent people first, then everyone else in the phone's (A–Z) order. */
    static List<WatchContact> list(List<WatchContact> all, List<Long> recentIds) {
        List<WatchContact> list = new ArrayList<>(recent(all, recentIds, Integer.MAX_VALUE));
        for (WatchContact contact : all) {
            if (!list.contains(contact)) {
                list.add(contact);
            }
        }
        return list;
    }
}
