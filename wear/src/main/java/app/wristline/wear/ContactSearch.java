package app.wristline.wear;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search box on the watch: matches the start of any word, ignoring case and accents. */
final class ContactSearch {
    private ContactSearch() {
    }

    static boolean matches(String name, String query) {
        String q = fold(query).trim();
        if (q.isEmpty()) {
            return true;
        }
        for (String word : fold(name).split("\\s+")) {
            if (word.startsWith(q)) {
                return true;
            }
        }
        return fold(name).startsWith(q);
    }

    static List<WatchContact> filter(List<WatchContact> contacts, String query) {
        List<WatchContact> out = new ArrayList<>();
        for (WatchContact contact : contacts) {
            if (matches(contact.name, query)) {
                out.add(contact);
            }
        }
        return out;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
