package app.wristline;

import static org.junit.Assert.assertNotNull;
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
 * Signal's outgoing-call notification, captured 2026-11-10 (id 70, channel call_status): ongoing,
 * no call category, not CallStyle, one button "Cancel call", text "Establishing…". Hanging up from
 * the watch has to press that button.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 36)
public class SignalOutgoingTest {
    private static Notification establishing(String button) {
        Context context = RuntimeEnvironment.getApplication();
        PendingIntent cancel = PendingIntent.getBroadcast(context, 0, new Intent("cancel"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context, "call_status")
                .setSmallIcon(android.R.drawable.sym_call_outgoing)
                .setContentTitle("Chris")
                .setContentText("Establishing…")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, button, cancel).build())
                .build();
    }

    @Test
    public void outgoingEstablishingIsTheCallAndCancelHangsUp() {
        Notification n = establishing("Cancel call");
        CallActions call = CallActions.from(n, SupportedApp.SIGNAL.ongoingLacksCallCategory);
        assertNotNull(call);
        assertSame(n.actions[0].actionIntent, call.hangUp);
    }

    @Test
    public void italianCancelCallHangsUp() {
        Notification n = establishing("Annulla chiamata");
        CallActions call = CallActions.from(n, SupportedApp.SIGNAL.ongoingLacksCallCategory);
        assertNotNull(call);
        assertSame(n.actions[0].actionIntent, call.hangUp);
    }
}
