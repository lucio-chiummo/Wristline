package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

/** Ringing the watch with Wristline's own notification, for watches whose call screen loses Answer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WatchRingTest {
    private Context context;
    private NotificationManager nm;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        nm = context.getSystemService(NotificationManager.class);
        StatusNotifications.createChannels(context);
    }

    @After
    public void tearDown() {
        WatchRing.stop(context);
    }

    private Notification shown() {
        return shadowOf(nm).getNotification(StatusNotifications.ID_INCOMING_FALLBACK);
    }

    private void pass(long ms) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS);
    }

    @Test
    public void settingIsOffByDefault() {
        assertFalse(Prefs.ringByNotification(context));
        Prefs.setRingByNotification(context, true);
        assertTrue(Prefs.ringByNotification(context));
    }

    @Test
    public void postsAnswerAndDecline() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", WatchRing.MAX_MS);

        Notification n = shown();
        assertNotNull(n);
        assertEquals(Notification.CATEGORY_CALL, n.category);
        assertEquals("watch_ring", n.getChannelId());
        assertEquals(2, n.actions.length);
        assertEquals("Answer", n.actions[0].title.toString());
        assertEquals("Decline", n.actions[1].title.toString());
        assertEquals("the watch doesn't show ongoing notifications", 0, n.flags & Notification.FLAG_ONGOING_EVENT);
        assertEquals("every repeat must alert", 0, n.flags & Notification.FLAG_ONLY_ALERT_ONCE);
        assertEquals("Corvan", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertTrue(WatchRing.ringing());
    }

    @Test
    public void alertsAgainEveryFourSeconds() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", WatchRing.MAX_MS);
        Notification first = shown();

        pass(WatchRing.REPEAT_MS);

        assertTrue("not posted again", shown() != first);
        assertTrue(WatchRing.ringing());
    }

    @Test
    public void stopsAfterItsLimit() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", 10_000);

        pass(12_000);

        assertFalse(WatchRing.ringing());
        assertNull(shown());
    }

    @Test
    public void stopCancelsTheNotificationAndTheRepeats() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", WatchRing.MAX_MS);

        WatchRing.stop(context);
        pass(2 * WatchRing.REPEAT_MS);

        assertNull(shown());
        assertFalse(WatchRing.ringing());
    }

    @Test
    public void startingAgainRestartsWithTheNewCaller() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", WatchRing.MAX_MS);
        WatchRing.start(context, "Annaly", "Incoming Telegram call", WatchRing.MAX_MS);
        pass(WatchRing.REPEAT_MS);

        assertEquals("Annaly", shown().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
    }

    @Test
    public void ringChannelIsSilentButVibrates() {
        NotificationChannel channel = nm.getNotificationChannel(StatusNotifications.CHANNEL_RING);
        assertNotNull(channel);
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.getImportance());
        assertNull("the calling app already plays its ringtone", channel.getSound());
        assertTrue(channel.shouldVibrate());
    }
}
