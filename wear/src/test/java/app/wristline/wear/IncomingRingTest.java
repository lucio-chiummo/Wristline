package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** The watch's own incoming call screen while the phone rings by notification. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class IncomingRingTest {
    private Application context;
    private NotificationManager manager;
    private final BlockingQueue<String> sent = new LinkedBlockingQueue<>();
    private final Protocol.Ring ring = Protocol.parseRing("start|60000|" + Protocol.WHATSAPP + "|Corvan");

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        manager = context.getSystemService(NotificationManager.class);
        shadowOf(context.getSystemService(Vibrator.class)).setHasVibrator(true);
        PhoneLink.sender = (path, payload) -> {
            sent.add(path);
            return true;
        };
    }

    @After
    public void tearDown() {
        PhoneLink.sender = null;
        IncomingRing.stop(context);
    }

    /** Opens the screen as the full-screen intent would, past IncomingRing's own attempt to start it. */
    private IncomingActivity openIncomingScreen() {
        while (shadowOf(context).getNextStartedActivity() != null) {
            // IncomingRing.start's direct start of the screen
        }
        return Robolectric.buildActivity(IncomingActivity.class, IncomingActivity.intent(context, ring)).setup().get();
    }

    @Test
    public void aRingWithACallerPostsAFullScreenCallNotification() {
        IncomingRing.start(context, ring);

        assertEquals(1, shadowOf(manager).size());
        Intent opened = shadowOf(context).getNextStartedActivity();
        assertEquals("tries to open the screen too", IncomingActivity.class.getName(), opened.getComponent().getClassName());
        Notification notification = shadowOf(manager).getNotification(IncomingRing.ID);
        assertNotNull(notification);
        assertEquals(Notification.CATEGORY_CALL, notification.category);
        assertTrue("ongoing", (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0);
        assertNotNull(notification.fullScreenIntent);
        Intent full = shadowOf(notification.fullScreenIntent).getSavedIntent();
        assertEquals(IncomingActivity.class.getName(), full.getComponent().getClassName());
        assertEquals(2, notification.actions.length);
        assertEquals(context.getString(R.string.answer), notification.actions[0].title.toString());
        assertEquals(context.getString(R.string.decline), notification.actions[1].title.toString());
        NotificationChannel channel = manager.getNotificationChannel(notification.getChannelId());
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.getImportance());
        assertNull("WatchBuzz vibrates, the channel stays silent", channel.getSound());
        assertFalse(channel.shouldVibrate());
    }

    @Test
    public void aRingStopClosesTheIncomingScreen() {
        IncomingRing.start(context, ring);
        IncomingActivity screen = openIncomingScreen();

        IncomingRing.stop(context);

        assertEquals(0, shadowOf(manager).size());
        assertTrue(screen.isFinishing());
    }

    @Test
    public void anOldRingMessageOnlyBuzzes() {
        CallStateService.onRing(context, "start|60000");

        assertNotNull(shadowOf(context.getSystemService(Vibrator.class)).getPattern());
        assertEquals(0, shadowOf(manager).size());
    }

    @Test
    public void aRingStopFromThePhoneClosesTheScreen() {
        CallStateService.onRing(context, "start|60000|" + Protocol.WHATSAPP + "|Corvan");
        assertEquals(1, shadowOf(manager).size());
        IncomingActivity screen = openIncomingScreen();

        CallStateService.onRing(context, "stop");

        assertEquals(0, shadowOf(manager).size());
        assertTrue(screen.isFinishing());
    }

    @Test
    public void answerTellsThePhoneAndOpensTheCallScreen() throws InterruptedException {
        IncomingRing.start(context, ring);
        IncomingActivity screen = openIncomingScreen();

        screen.findViewById(R.id.answer).performClick();

        assertEquals(Protocol.PATH_ANSWER, sent.poll(5, TimeUnit.SECONDS));
        Intent started = shadowOf(screen).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(InCallActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("Corvan", started.getStringExtra(InCallActivity.EXTRA_NAME));
        assertEquals(Protocol.WHATSAPP, started.getStringExtra(InCallActivity.EXTRA_APP));
        assertTrue(screen.isFinishing());
        assertEquals(0, shadowOf(manager).size());
    }

    @Test
    public void anAnsweredCallScreenSaysConnecting() {
        ActivityController<InCallActivity> screen = answeredCallScreen();

        assertEquals(context.getString(R.string.connecting), status(screen.get()));
        screen.destroy();
    }

    /** After Answer the phone says "ringing" until the app shows a timer: not the other side ringing. */
    @Test
    public void anAnsweredCallStaysConnectingWhileThePhoneSaysRinging() {
        ActivityController<InCallActivity> screen = answeredCallScreen();

        screen.get().onState(Protocol.CallState.parse(Protocol.STATE_RINGING));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(context.getString(R.string.connecting), status(screen.get()));

        screen.get().onState(Protocol.CallState.parse(Protocol.STATE_ACTIVE + ";1000;off"));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, screen.get().findViewById(R.id.timer).getVisibility());
        assertEquals(View.GONE, screen.get().findViewById(R.id.status).getVisibility());
        screen.destroy();
    }

    /** A call started from the watch still shows the other side ringing. */
    @Test
    public void aCallFromTheWatchStillSaysRinging() {
        ActivityController<InCallActivity> screen = Robolectric.buildActivity(InCallActivity.class,
                InCallActivity.intent(context, "Corvan", Protocol.WHATSAPP)).setup();

        screen.get().onState(Protocol.CallState.parse(Protocol.STATE_RINGING));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(context.getString(R.string.ringing), status(screen.get()));
        screen.destroy();
    }

    private ActivityController<InCallActivity> answeredCallScreen() {
        return Robolectric.buildActivity(InCallActivity.class,
                InCallActivity.answered(context, "Corvan", Protocol.WHATSAPP)).setup();
    }

    private static String status(InCallActivity screen) {
        return ((TextView) screen.findViewById(R.id.status)).getText().toString();
    }

    @Test
    public void declineTellsThePhoneAndCloses() throws InterruptedException {
        IncomingRing.start(context, ring);
        IncomingActivity screen = openIncomingScreen();

        screen.findViewById(R.id.decline).performClick();

        assertEquals(Protocol.PATH_DECLINE, sent.poll(5, TimeUnit.SECONDS));
        assertTrue(screen.isFinishing());
        assertEquals(0, shadowOf(manager).size());
        assertNull(shadowOf(screen).getNextStartedActivity());
    }
}
