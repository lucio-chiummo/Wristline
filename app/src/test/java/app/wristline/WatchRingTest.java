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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Ringing the watch with Wristline's own notification, for watches whose call screen loses Answer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class WatchRingTest {
    private Context context;
    private NotificationManager nm;

    @Before
    public void setUp() {
        WatchSync.resetRingQueue();
        context = RuntimeEnvironment.getApplication();
        nm = context.getSystemService(NotificationManager.class);
        StatusNotifications.createChannels(context);
        WatchSync.sender = (c, path, payload) ->
                sent.add(path + " " + new String(payload, StandardCharsets.UTF_8));
    }

    private final List<String> sent = new ArrayList<>();

    @After
    public void tearDown() {
        WatchRing.stop(context);
        WatchSync.sender = WatchSync.WATCH_SENDER;
    }

    private Notification shown() {
        return shadowOf(nm).getNotification(StatusNotifications.ID_INCOMING_FALLBACK);
    }

    private void flush() throws Exception {
        WatchSync.flushRing();
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
    public void theRingNotificationCarriesTheBridgeTag() {
        WatchRing.start(context, "Anna", "Incoming call", "com.whatsapp", WatchRing.MAX_MS);
        assertEquals("wristline_ring", new Notification.WearableExtender(shown()).getBridgeTag());
    }

    @Test
    public void postsAnswerAndDecline() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);

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
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);
        Notification first = shown();

        pass(WatchRing.REPEAT_MS);

        assertTrue("not posted again", shown() != first);
        assertTrue(WatchRing.ringing());
    }

    @Test
    public void stopsAfterItsLimit() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", 10_000);

        pass(12_000);

        assertFalse(WatchRing.ringing());
        assertNull(shown());
    }

    @Test
    public void stopCancelsTheNotificationAndTheRepeats() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);

        WatchRing.stop(context);
        pass(2 * WatchRing.REPEAT_MS);

        assertNull(shown());
        assertFalse(WatchRing.ringing());
    }

    @Test
    public void startingAgainRestartsWithTheNewCaller() {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);
        WatchRing.start(context, "Annaly", "Incoming Telegram call", "org.telegram.messenger", WatchRing.MAX_MS);
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

    @Test
    public void aRingTellsTheWatchToStartOnce() throws Exception {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);
        pass(3 * WatchRing.REPEAT_MS);
        flush();

        assertEquals(List.of("/wristline/ring start|60000|com.whatsapp|Corvan"), sent);
    }

    @Test
    public void aRingTellsTheWatchWhoIsCalling() throws Exception {
        WatchRing.start(context, "Ann", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);
        flush();

        assertEquals(List.of("/wristline/ring " + new String(WatchProtocol.ringStartPayload(
                WatchRing.MAX_MS, "com.whatsapp", "Ann"), StandardCharsets.UTF_8)), sent);
    }

    @Test
    public void stoppingARingTellsTheWatchToStop() throws Exception {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);
        flush();
        sent.clear();

        WatchRing.stop(context);
        WatchRing.stop(context);
        flush();

        assertEquals(List.of("/wristline/ring stop"), sent);
    }

    @Test
    public void stoppingWhenNothingRangSendsNothing() throws Exception {
        WatchRing.stop(context);
        flush();

        assertTrue(sent.isEmpty());
    }

    @Test
    public void aRingThatTimesOutTellsTheWatchToStop() throws Exception {
        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", 10_000);
        flush();
        sent.clear();

        pass(12_000);
        flush();

        assertEquals(List.of("/wristline/ring stop"), sent);
    }

    @Test
    public void aQuickStartThenStopReachTheWatchInOrder() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        WatchSync.sender = (c, path, payload) -> {
            if (order.isEmpty()) {
                // The first send is slow, like waiting for the connected nodes.
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            order.add(new String(payload, StandardCharsets.UTF_8));
        };

        WatchRing.start(context, "Corvan", "Incoming WhatsApp call", "com.whatsapp", WatchRing.MAX_MS);
        WatchRing.stop(context);
        flush();

        assertEquals(List.of("start|60000|com.whatsapp|Corvan", "stop"), order);
    }
}
