package app.wristline;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;

import java.util.Locale;

/**
 * Finds the answer, decline and hang-up actions on a call notification, independent
 * of the phone's language: first from the standard CallStyle extras, then from well-known
 * button labels in common languages, finally from the conventional button order.
 */
final class CallActions {
    private static final String[] ANSWER_WORDS = {
            "answer", "accept", "pick up", "rispondi", "accetta", "responder", "contestar", "aceptar",
            "atender", "aceitar", "annehmen", "antworten", "répondre", "repondre", "accepter",
            "opnemen", "beantwoorden", "odbierz", "ответить", "принять", "cevapla", "kabul",
            "اجابة", "رد", "जवाब", "接听", "接聽", "応答", "받기"};
    private static final String[] DECLINE_WORDS = {
            "decline", "reject", "dismiss", "rifiuta", "rechazar", "recusar", "rejeitar", "ablehnen",
            "refuser", "weigeren", "afwijzen", "odrzuć", "odrzuc", "отклонить", "reddet",
            "رفض", "अस्वीकार", "拒绝", "拒絕", "拒否", "거절"};
    private static final String[] HANG_UP_WORDS = {
            "hang up", "end call", "end", "cancel call", "cancel", "annulla chiamata", "annulla", "termina", "riaggancia", "chiudi", "colgar", "finalizar",
            "encerrar", "desligar", "auflegen", "beenden", "raccrocher", "terminer", "ophangen",
            "beëindigen", "rozłącz", "rozlacz", "завершить", "kapat", "إنهاء", "समाप्त", "挂断",
            "掛斷", "終了", "종료"};

    final boolean incoming;
    final PendingIntent answer;
    final PendingIntent decline;
    final PendingIntent hangUp;
    final CharSequence caller;
    final Notification.Action[] actions;
    /** The label of {@link #hangUp}'s button, for the diagnostic report; null when it has none. */
    final CharSequence hangUpLabel;
    /** The app's mute toggle, or null when its notification has none. */
    final PendingIntent mute;
    /** Whether {@link #mute} currently reads "Unmute". */
    final boolean muted;
    /**
     * When the call connected (wall clock ms), or 0 while it hasn't: apps switch their in-call
     * notification to a chronometer counting from {@code when} once the other side answers.
     */
    final long connectedAt;

    private CallActions(boolean incoming, PendingIntent answer, PendingIntent decline,
            PendingIntent hangUp, CharSequence hangUpLabel, CharSequence caller, Notification.Action[] actions,
            PendingIntent mute, boolean muted, long connectedAt) {
        this.incoming = incoming;
        this.answer = answer;
        this.decline = decline;
        this.hangUp = hangUp;
        this.hangUpLabel = hangUpLabel;
        this.caller = caller;
        this.actions = actions;
        this.mute = mute;
        this.muted = muted;
        this.connectedAt = connectedAt;
    }

    /** The mute field of a call-state message for the watch. */
    String muteState() {
        return mute == null ? WatchProtocol.MUTE_NONE : muted ? WatchProtocol.MUTE_ON : WatchProtocol.MUTE_OFF;
    }

    /**
     * Returns null when the notification is neither a ringing nor an ongoing call.
     *
     * @param allowUncategorizedOngoing see {@link CallShape#classify(boolean)}
     */
    @SuppressWarnings("deprecation") // Bundle.getParcelable(String): typed overload needs API 33
    static CallActions from(Notification n, boolean allowUncategorizedOngoing) {
        Bundle extras = n.extras;
        Notification.Action[] actions = n.actions == null ? new Notification.Action[0] : n.actions;
        PendingIntent answer = extras.getParcelable(Notification.EXTRA_ANSWER_INTENT);
        PendingIntent decline = extras.getParcelable(Notification.EXTRA_DECLINE_INTENT);
        PendingIntent hangUp = extras.getParcelable(Notification.EXTRA_HANG_UP_INTENT);
        PendingIntent mute = null;
        boolean muted = false;
        CharSequence hangUpLabel = null;

        for (Notification.Action action : actions) {
            String title = action.title == null ? "" : action.title.toString().toLowerCase(Locale.ROOT);
            MuteAction.Kind muteKind = MuteAction.classify(title);
            if (RedialAction.is(title)) {
                // "Call again" after the other side hung up: never pressed as anything else.
                continue;
            }
            if (muteKind != MuteAction.Kind.NONE) {
                if (mute == null) {
                    mute = action.actionIntent;
                    muted = muteKind == MuteAction.Kind.UNMUTE;
                }
            } else if (answer == null && matches(title, ANSWER_WORDS)) {
                answer = action.actionIntent;
            } else if (decline == null && matches(title, DECLINE_WORDS)) {
                decline = action.actionIntent;
            } else if (hangUp == null && matches(title, HANG_UP_WORDS)) {
                hangUp = action.actionIntent;
                hangUpLabel = action.title;
            }
        }

        CallShape shape = new CallShape(
                Notification.CATEGORY_CALL.equals(n.category),
                (n.flags & Notification.FLAG_ONGOING_EVENT) != 0,
                extras.containsKey(Notification.EXTRA_CALL_TYPE) ? extras.getInt(Notification.EXTRA_CALL_TYPE) : null,
                n.fullScreenIntent != null,
                answer != null,
                actions.length,
                hangUp != null);
        CallShape.Kind kind = shape.classify(allowUncategorizedOngoing);
        if (kind == CallShape.Kind.NONE) {
            return null;
        }
        boolean incoming = kind == CallShape.Kind.INCOMING;

        // Unknown language: Android call notifications put the negative button first.
        if (incoming && answer == null && decline == null && actions.length == 2) {
            decline = actions[0].actionIntent;
            answer = actions[1].actionIntent;
        }
        if (!incoming && hangUp == null && actions.length > 0 && actions[0].actionIntent != mute
                && !RedialAction.is(actions[0].title)) {
            hangUp = actions[0].actionIntent;
            hangUpLabel = actions[0].title;
        }

        CharSequence caller = extras.getCharSequence(Notification.EXTRA_TITLE);
        long connectedAt = !incoming && extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) && n.when > 0 ? n.when : 0;
        return new CallActions(incoming, answer, decline, hangUp, hangUpLabel, caller, actions, mute, muted, connectedAt);
    }

    private static boolean matches(String title, String[] words) {
        // Match whole words only, so e.g. "end" does not match "send message".
        for (String word : words) {
            if (title.startsWith(word) || title.contains(" " + word)) {
                return true;
            }
        }
        return false;
    }
}
