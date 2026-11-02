package app.wristline.wear;

import java.util.Locale;

/**
 * The circle shown for a person instead of a photo (the watch never gets photos): up to two
 * initials and a colour that stays the same for the same name.
 */
final class Avatar {
    /** How many avatar colours there are ({@code R.array.avatar_colors}). */
    static final int COLORS = 8;

    private Avatar() {
    }

    /** "Annaly Biavon" → "AB", "Corvan" → "C", "  " → "?"; letters and digits only, not emoji or "(". */
    static String initials(String name) {
        if (name == null) {
            return "?";
        }
        String first = null;
        String last = null;
        for (String word : name.trim().split("\\s+")) {
            String letter = firstLetter(word);
            if (letter == null) {
                continue;
            }
            if (first == null) {
                first = letter;
            } else {
                last = letter;
            }
        }
        if (first == null) {
            return "?";
        }
        return (last == null ? first : first + last).toUpperCase(Locale.ROOT);
    }

    private static String firstLetter(String word) {
        for (int i = 0; i < word.length(); ) {
            int cp = word.codePointAt(i);
            if (Character.isLetterOrDigit(cp)) {
                return new String(Character.toChars(cp));
            }
            i += Character.charCount(cp);
        }
        return null;
    }

    /** The first word, at most 7 characters: what fits under an avatar on the tile or in a complication. */
    static String shortName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        String word = space > 0 ? trimmed.substring(0, space) : trimmed;
        if (word.codePointCount(0, word.length()) <= 7) {
            return word;
        }
        return word.substring(0, word.offsetByCodePoints(0, 7));
    }

    /** Index into the avatar colours: the same for the same name, spread across names. */
    static int colorIndex(String name) {
        if (name == null) {
            return 0;
        }
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = hash * 31 + Character.toLowerCase(name.charAt(i));
        }
        return Math.floorMod(hash, COLORS);
    }
}
