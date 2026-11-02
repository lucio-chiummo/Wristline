package app.wristline;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Apps that can't be called through the contacts (Instagram) put a "Call back" button on their own
 * missed-call notification. Wristline keeps that button's intent and offers it on its own
 * missed-call notice, which the watch shows.
 */
final class OwnCallBack {
    private static final String[] WORDS = {
            "call back", "callback", "richiama", "rappeler", "zurückrufen", "zuruckrufen", "devolver",
            "llamar", "ligar", "terugbellen", "oddzwoń", "oddzwon", "перезвонить", "geri ara"};

    private static final class Entry {
        final Object action;
        final long time;

        Entry(Object action, long time) {
            this.action = action;
            this.time = time;
        }
    }

    private static final Map<String, Entry> CAPTURED = new HashMap<>();

    private OwnCallBack() {
    }

    /** Which of a missed-call notification's buttons is "Call back": by label, else the last of two; -1 if none. */
    static int index(List<? extends CharSequence> titles) {
        for (int i = 0; i < titles.size(); i++) {
            String title = titles.get(i).toString().toLowerCase(Locale.ROOT);
            for (String word : WORDS) {
                if (title.contains(word)) {
                    return i;
                }
            }
        }
        return titles.size() == 2 ? 1 : -1;
    }

    /** Keeps the "Call back" button of {@code app}'s missed-call notification, posted at {@code time}. */
    static void capture(String app, Notification notification, long time) {
        // Instagram's DMs have two buttons too: only the call category is a missed call.
        if (notification.actions == null || !Notification.CATEGORY_CALL.equals(notification.category)
                || (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return;
        }
        List<CharSequence> titles = new java.util.ArrayList<>();
        for (Notification.Action action : notification.actions) {
            titles.add(action.title);
        }
        int at = index(titles);
        if (at >= 0 && notification.actions[at].actionIntent != null) {
            put(app, notification.actions[at].actionIntent, time);
        }
    }

    static synchronized void put(String app, Object action, long time) {
        CAPTURED.put(app, new Entry(action, time));
    }

    static synchronized void clear() {
        CAPTURED.clear();
    }

    /** Whether the app's missed-call "Call back" was captured at or after {@code callStartedAt}. */
    static synchronized boolean has(String app, long callStartedAt) {
        Entry entry = CAPTURED.get(app);
        return entry != null && entry.time >= callStartedAt;
    }

    /** Presses the app's own "Call back". */
    static void press(Context context, String app, long callStartedAt) {
        Entry entry;
        synchronized (OwnCallBack.class) {
            entry = CAPTURED.get(app);
        }
        if (entry != null && entry.time >= callStartedAt && entry.action instanceof PendingIntent) {
            RelayListener.send(context, (PendingIntent) entry.action);
        }
    }
}
