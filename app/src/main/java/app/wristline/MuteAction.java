package app.wristline;

import java.util.Locale;

/** Recognises an in-call notification's mute button by its label, in common languages. */
final class MuteAction {
    enum Kind { NONE, MUTE, UNMUTE }

    // Checked first: "unmute" must not read as "mute".
    private static final String[] UNMUTE_WORDS = {
            "unmute", "turn on microphone", "riattiva", "attiva microfono", "activar micrófono",
            "activar sonido", "reactivar", "ativar som", "ativar microfone", "stummschaltung aufheben",
            "mikrofon an", "réactiver", "reactiver", "activer le micro", "dempen opheffen",
            "wyłącz wyciszenie", "включить микрофон", "取消静音", "取消靜音", "ミュート解除", "음소거 해제"};
    private static final String[] MUTE_WORDS = {
            "mute", "turn off microphone", "muto", "silenzia", "disattiva microfono", "silenciar",
            "desactivar micrófono", "stumm", "mikrofon aus", "couper", "désactiver le micro",
            "desactiver le micro", "dempen", "wycisz", "выключить микрофон", "без звука", "sessize al",
            "静音", "ミュート", "음소거"};

    private MuteAction() {
    }

    static Kind classify(CharSequence label) {
        if (label == null) {
            return Kind.NONE;
        }
        String title = label.toString().toLowerCase(Locale.ROOT).trim();
        if (matches(title, UNMUTE_WORDS)) {
            return Kind.UNMUTE;
        }
        return matches(title, MUTE_WORDS) ? Kind.MUTE : Kind.NONE;
    }

    private static boolean matches(String title, String[] words) {
        for (String word : words) {
            if (title.startsWith(word) || title.contains(" " + word)) {
                return true;
            }
        }
        return false;
    }
}
