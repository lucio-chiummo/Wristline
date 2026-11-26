package app.wristline;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Once the other side hangs up or doesn't answer, WhatsApp keeps its in-call notification
 * (call category, ongoing) with "Call again" first. Hanging up from the watch then pressed that
 * button and called the person again (field report, Xiaomi, Wristline 1.4). A redial button is
 * never taken as the hang-up.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 36)
public class CallActionsRedialTest {
    private static Notification inCall(String... buttons) {
        Context context = RuntimeEnvironment.getApplication();
        Notification.Builder builder = new Notification.Builder(context, "voip")
                .setSmallIcon(android.R.drawable.sym_call_outgoing)
                .setContentTitle("Chris")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true);
        for (int i = 0; i < buttons.length; i++) {
            PendingIntent intent = PendingIntent.getBroadcast(context, i, new Intent(buttons[i]), PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null, buttons[i], intent).build());
        }
        return builder.build();
    }

    private static CallActions parse(Notification n) {
        CallActions call = CallActions.from(n, SupportedApp.WHATSAPP.ongoingLacksCallCategory);
        assertNotNull(call);
        return call;
    }

    @Test
    public void callAgainFirstIsNeverTheHangUp() {
        assertNull(parse(inCall("Call again", "Message")).hangUp);
    }

    @Test
    public void italianRichiamaFirstIsNeverTheHangUp() {
        assertNull(parse(inCall("Richiama", "Messaggio")).hangUp);
    }

    @Test
    public void cancelNextToCallAgainIsTheHangUp() {
        Notification n = inCall("Call again", "Cancel");
        assertSame(n.actions[1].actionIntent, parse(n).hangUp);
    }

    @Test
    public void italianAnnullaNextToRichiamaIsTheHangUp() {
        Notification n = inCall("Richiama", "Annulla");
        assertSame(n.actions[1].actionIntent, parse(n).hangUp);
    }

    @Test
    public void aLiveCallStillHangsUpWithItsOwnButton() {
        Notification n = inCall("Hang up", "Speaker");
        assertSame(n.actions[0].actionIntent, parse(n).hangUp);
    }

    @Test
    public void anUnknownFirstButtonIsStillTheHangUpFallback() {
        Notification n = inCall("Отбой");
        assertSame(n.actions[0].actionIntent, parse(n).hangUp);
    }
}
