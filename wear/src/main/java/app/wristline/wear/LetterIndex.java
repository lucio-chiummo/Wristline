package app.wristline.wear;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** First letters of a sorted name list and where each starts: the A–Z fast scroll. */
final class LetterIndex {
    private final Map<String, Integer> firstAt = new LinkedHashMap<>();

    private LetterIndex() {
    }

    /** @param names in list order; positions are indexes into this list */
    static LetterIndex of(List<String> names) {
        LetterIndex index = new LetterIndex();
        for (int i = 0; i < names.size(); i++) {
            index.firstAt.putIfAbsent(letter(names.get(i)), i);
        }
        return index;
    }

    /** "É" and "é" count as "E"; anything that isn't a letter goes under "#". */
    static String letter(String name) {
        String plain = Normalizer.normalize(name.trim(), Normalizer.Form.NFD);
        if (plain.isEmpty() || !Character.isLetter(plain.charAt(0))) {
            return "#";
        }
        return String.valueOf(plain.charAt(0)).toUpperCase(Locale.ROOT);
    }

    List<String> letters() {
        return new ArrayList<>(firstAt.keySet());
    }

    int position(String letter) {
        Integer at = firstAt.get(letter);
        return at == null ? 0 : at;
    }

    /** The letter under a finger {@code fraction} (0 = top, 1 = bottom) of the way down the strip. */
    String atFraction(float fraction) {
        List<String> letters = letters();
        int last = letters.size() - 1;
        int at = Math.round(Math.max(0f, Math.min(1f, fraction)) * last);
        return letters.get(Math.max(0, at));
    }
}
