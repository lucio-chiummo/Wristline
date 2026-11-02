package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/** Button labels go in a report people may post publicly, so they must not carry who was calling. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class NotificationShapeTest {
    private static final List<CharSequence> ANNALY = List.of("Annaly Biavon");

    @Test
    public void callersNameIsMasked() {
        assertEquals("Call [name] back", NotificationShape.redact("Call Annaly back", ANNALY));
    }

    @Test
    public void everyPartOfTheNameIsMaskedWhateverItsCase() {
        assertEquals("[name] [name]", NotificationShape.redact("BIAVON annaly", ANNALY));
    }

    @Test
    public void wordsThatOnlyContainTheNameStay() {
        assertEquals("Annalyse", NotificationShape.redact("Annalyse", ANNALY));
    }

    @Test
    public void labelsWithoutPersonalDataStay() {
        assertEquals("Rispondi", NotificationShape.redact("Rispondi", ANNALY));
    }

    @Test
    public void numbersAreMasked() {
        assertEquals("Richiama #", NotificationShape.redact("Richiama +39 333 123-4567", List.of()));
    }

    @Test
    public void nonLatinNamesAreMasked() {
        assertEquals("Позвонить [name]", NotificationShape.redact("Позвонить Анна", List.of("Анна")));
    }

    @Test
    public void reportedShapeLeavesOutTheNameInTheTitle() {
        Application context = RuntimeEnvironment.getApplication();
        PendingIntent intent = PendingIntent.getBroadcast(context, 0, new Intent("test"), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Annaly Biavon")
                .addAction(new Notification.Action.Builder(null, "Call Annaly back", intent).build())
                .build();

        String shape = NotificationShape.of(notification);

        assertTrue(shape, shape.contains("actions=[Call [name] back]"));
        assertFalse(shape, shape.contains("Annaly"));
    }
}
