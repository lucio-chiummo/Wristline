package app.wristline;

import android.app.Notification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A caller-free summary of a calling app's notification (category, flags, CallStyle type, button
 * labels), recorded when Wristline can't make sense of it, so a changed app can be supported
 * from a shared report. Titles and texts, which hold the caller's name, are left out, and masked
 * where a button label repeats them.
 */
final class NotificationShape {
    private static final Pattern NUMBER = Pattern.compile("\\+?\\d(?:[\\s().-]*\\d)*");
    private static final int MIN_NAME_LENGTH = 2;

    private NotificationShape() {
    }

    static String describe(String category, boolean ongoing, Integer callType, boolean fullScreen,
            List<String> actionLabels) {
        return "category=" + (category == null ? "none" : category)
                + " ongoing=" + ongoing
                + " callType=" + (callType == null ? "none" : callType)
                + " fullScreen=" + fullScreen
                + " actions=" + actionLabels;
    }

    /**
     * A button label with the words of {@code personal} (the notification's own texts, which hold the
     * caller's name) and any numbers masked. Too eager is fine: "End call" may lose "call", while
     * labels in an unknown language, which is what the report is for, stay readable.
     */
    static String redact(String label, List<? extends CharSequence> personal) {
        String out = label;
        for (CharSequence text : personal) {
            if (text == null) {
                continue;
            }
            for (String word : text.toString().split("[^\\p{L}\\p{N}]+")) {
                if (word.codePointCount(0, word.length()) >= MIN_NAME_LENGTH) {
                    out = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(out).replaceAll("[name]");
                }
            }
        }
        return NUMBER.matcher(out).replaceAll("#");
    }

    static String of(Notification n) {
        List<CharSequence> personal = Arrays.asList(
                n.extras.getCharSequence(Notification.EXTRA_TITLE),
                n.extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
                n.extras.getCharSequence(Notification.EXTRA_TEXT),
                n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
                n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        List<String> labels = new ArrayList<>();
        if (n.actions != null) {
            for (Notification.Action action : n.actions) {
                labels.add(action.title == null ? "" : redact(action.title.toString(), personal));
            }
        }
        Integer callType = n.extras.containsKey(Notification.EXTRA_CALL_TYPE)
                ? n.extras.getInt(Notification.EXTRA_CALL_TYPE) : null;
        return describe(n.category, (n.flags & Notification.FLAG_ONGOING_EVENT) != 0, callType,
                n.fullScreenIntent != null, labels);
    }
}
