package app.wristline;

import java.util.Locale;

/**
 * Recognises a "Call again" / "Call back" button, in common languages. Once the other side hangs
 * up or doesn't answer, WhatsApp keeps its in-call notification with "Call again" in it: pressing
 * that as "Hang up" from the watch starts a new call (field report, 2026-11-13).
 */
final class RedialAction {
    private static final String[] WORDS = {
            "call again", "call back", "callback", "redial", "richiama", "chiama di nuovo",
            "chiama ancora", "ricomponi", "volver a llamar", "rellamar", "devolver llamada",
            "ligar novamente", "ligar de novo", "retornar", "erneut anrufen", "nochmal anrufen",
            "zurückrufen", "zuruckrufen", "rappeler", "opnieuw bellen", "terugbellen",
            "zadzwoń ponownie", "zadzwon ponownie", "oddzwoń", "oddzwon", "позвонить снова",
            "перезвонить", "tekrar ara", "geri ara", "再次呼叫", "重拨", "回拨", "再撥", "回撥",
            "もう一度かける", "折り返し", "다시 걸기"};

    private RedialAction() {
    }

    static boolean is(CharSequence label) {
        if (label == null) {
            return false;
        }
        String title = label.toString().toLowerCase(Locale.ROOT).trim();
        for (String word : WORDS) {
            if (title.startsWith(word) || title.contains(" " + word)) {
                return true;
            }
        }
        return false;
    }
}
