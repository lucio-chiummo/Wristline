package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowTelecomManager;

/** The notification-to-Telecom flow of {@link RelayListener}, without a phone. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class RelayListenerTest {
    private Context context;
    private RelayListener listener;
    private ShadowTelecomManager telecom;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        listener = Robolectric.buildService(RelayListener.class).create().get();
        telecom = shadowOf(context.getSystemService(TelecomManager.class));
    }

    @After
    public void tearDown() {
        RelayListener.clearCall(context);
        RelayConnection.current = null;
    }

    private PendingIntent intent(int requestCode) {
        return PendingIntent.getBroadcast(context, requestCode, new Intent("test." + requestCode), PendingIntent.FLAG_IMMUTABLE);
    }

    /** A ringing call the way WhatsApp posts it: call category, full-screen intent, two buttons. */
    private StatusBarNotification ringing(String pkg, String key, String caller) {
        Notification n = new Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(caller)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(intent(1), true)
                .addAction(new Notification.Action.Builder(null, "Decline", intent(2)).build())
                .addAction(new Notification.Action.Builder(null, "Answer", intent(3)).build())
                .build();
        return post(pkg, key, n);
    }

    /** The ongoing call: call category, ongoing flag, a single "Hang up". */
    private StatusBarNotification ongoing(String pkg, String key) {
        Notification n = new Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Corvan")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Hang up", intent(4)).build())
                .build();
        return post(pkg, key, n);
    }

    private StatusBarNotification post(String pkg, String key, Notification n) {
        // The hidden constructor is what the system uses; the SDK stubs don't expose it.
        try {
            return StatusBarNotification.class
                    .getConstructor(String.class, String.class, int.class, String.class, int.class, int.class,
                            Notification.class, android.os.UserHandle.class, String.class, long.class)
                    .newInstance(pkg, pkg, key.hashCode(), key, 1000, 1, n, Process.myUserHandle(), null, 0L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** People paste logs into public issues: names stay out of them. */
    @Test
    public void callersNameStaysOutOfTheLog() {
        ShadowLog.clear();

        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));

        assertNotNull(RelayListener.incomingCall);
        boolean logged = false;
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
            logged |= item.tag.equals(RelayListener.TAG) && item.msg.contains("incoming call");
            assertFalse(item.msg, item.msg.contains("Corvan"));
        }
        assertTrue(logged);
    }

    @Test
    public void incomingWhatsAppCallIsMirroredAsATelecomCall() {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));

        ShadowTelecomManager.CallRecord call = telecom.getOnlyIncomingCall();
        assertEquals("WhatsApp · Corvan", call.extras.getString(RelayListener.EXTRA_CALLER));
        assertSame(SupportedApp.WHATSAPP, RelayListener.currentApp);
        assertNotNull(RelayListener.incomingCall);
    }

    @Test
    public void unreadableRingingNotificationIsRecordedAndToldOnce() {
        // Ringing (CallStyle incoming) but with buttons Wristline can't tell apart.
        Notification n = new Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Corvan")
                .setCategory(Notification.CATEGORY_CALL)
                .addAction(new Notification.Action.Builder(null, "A", intent(2)).build())
                .addAction(new Notification.Action.Builder(null, "B", intent(3)).build())
                .addAction(new Notification.Action.Builder(null, "C", intent(5)).build())
                .build();
        n.extras.putInt(Notification.EXTRA_CALL_TYPE, 1);

        listener.onNotificationPosted(post("com.whatsapp", "k1", n));
        listener.onNotificationPosted(post("com.whatsapp", "k2", n));

        assertEquals(0, telecom.getAllIncomingCalls().size());
        java.util.List<EventLog.Entry> events = Prefs.events(context);
        assertEquals(EventLog.UNREADABLE_CALL, events.get(0).code);
        assertTrue(events.get(0).detail.contains("actions=[A, B, C]"));
        assertFalse(events.get(0).detail.contains("Corvan"));
        android.app.NotificationManager nm = context.getSystemService(android.app.NotificationManager.class);
        assertEquals(1, shadowOf(nm).size());
    }

    /** Stands in for the phone's Do Not Disturb (Robolectric has no rankings). */
    public static class SilencedListener extends RelayListener {
        @Override
        boolean passesDnd(android.service.notification.StatusBarNotification sbn) {
            return false;
        }
    }

    @Test
    public void callDoNotDisturbSilencesDoesNotRingTheWatch() {
        RelayListener silenced = Robolectric.buildService(SilencedListener.class).create().get();

        silenced.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));

        assertEquals(0, telecom.getAllIncomingCalls().size());
        assertEquals(EventLog.DO_NOT_DISTURB, Prefs.events(context).get(0).code);
    }

    /** The phone is unlocked and in the user's hand. */
    public static class InUseListener extends RelayListener {
        @Override
        boolean phoneInUse() {
            return true;
        }
    }

    @Test
    public void notWhileUsingThePhoneWhenAsked() {
        RelayListener inUse = Robolectric.buildService(InUseListener.class).create().get();
        inUse.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        assertEquals(1, telecom.getAllIncomingCalls().size());
        RelayListener.clearCall(context);

        Prefs.setNotWhileInUse(context, true);
        RelayListener inUseAgain = Robolectric.buildService(InUseListener.class).create().get();
        inUseAgain.onNotificationPosted(ringing("com.whatsapp", "k2", "Annaly"));

        assertEquals(1, telecom.getAllIncomingCalls().size());
        assertEquals(EventLog.PHONE_IN_USE, Prefs.events(context).get(0).code);
    }

    @Test
    public void notFollowingDoNotDisturbRingsAnyway() {
        Prefs.setFollowDnd(context, false);
        RelayListener silenced = Robolectric.buildService(SilencedListener.class).create().get();

        silenced.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));

        assertEquals(1, telecom.getAllIncomingCalls().size());
    }

    /** Instagram's in-call notification: CallStyle ongoing (callType 2), "Hang up", no call category. */
    private StatusBarNotification instagramInCall() {
        Notification n = new Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Audio call")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Hang up", intent(6)).build())
                .build();
        n.extras.putInt(Notification.EXTRA_CALL_TYPE, 2);
        n.extras.putParcelable(Notification.EXTRA_HANG_UP_INTENT, intent(6));
        return post("com.instagram.android", "20025", n);
    }

    @Test
    public void instagramAnsweredOnTheWatchStaysOnTheWatch() {
        Prefs.setAppOn(context, SupportedApp.INSTAGRAM, true);
        listener.onNotificationPosted(ringing("com.instagram.android", "igvc_1", "Corvan"));
        // Answered: the ringing notification goes, the in-call one follows within the grace period.
        listener.onNotificationRemoved(ringing("com.instagram.android", "igvc_1", "Corvan"));
        listener.onNotificationPosted(instagramInCall());
        ShadowLooper.idleMainLooper(CallTracker.GRACE_MS + 1, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertSame(SupportedApp.INSTAGRAM, RelayListener.currentApp);
        assertNotNull(RelayListener.ongoingCall);
        assertNotNull(RelayListener.ongoingCall.hangUp);
    }

    @Test
    public void callFromAnUnsupportedAppIsIgnored() {
        listener.onNotificationPosted(ringing("com.example.dialer", "k1", "Corvan"));

        assertNull(RelayListener.currentApp);
        assertNull(RelayListener.incomingCall);
        assertEquals(0, telecom.getAllIncomingCalls().size());
    }

    @Test
    public void masterSwitchOffStopsNewCalls() {
        Prefs.setRelayOn(context, false);

        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));

        assertNull(RelayListener.currentApp);
        assertEquals(0, telecom.getAllIncomingCalls().size());
    }

    @Test
    public void appSwitchOffStopsNewCalls() {
        Prefs.setAppOn(context, SupportedApp.WHATSAPP, false);

        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));

        assertNull(RelayListener.currentApp);
        assertEquals(0, telecom.getAllIncomingCalls().size());
    }

    @Test
    public void secondCallWhileOneRingsIsNotMirroredAgain() {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        listener.onNotificationPosted(ringing("com.whatsapp", "k2", "Annaly"));

        assertEquals(1, telecom.getAllIncomingCalls().size());
    }

    @Test
    public void ongoingNotificationKeepsTheCallAndStoresItsButtons() {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        listener.onNotificationPosted(ongoing("com.whatsapp", "k1"));

        CallActions call = RelayListener.ongoingCall;
        assertNotNull(call);
        assertEquals(1, call.actions.length);
        assertNotNull(call.hangUp);
    }

    @Test
    public void removingTheNotificationOfAnOngoingCallEndsIt() {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        listener.onNotificationPosted(ongoing("com.whatsapp", "k1"));

        listener.onNotificationRemoved(ongoing("com.whatsapp", "k1"));
        // The grace period decides whether an in-call notification follows.
        ShadowLooper.idleMainLooper(CallTracker.GRACE_MS + 1, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertNull(RelayListener.currentApp);
        assertNull(RelayListener.ongoingCall);
    }

    /** The missed-call notification, waiting for its thread (it gives the history entry time to land). */
    private Notification missedNotification() throws InterruptedException {
        android.app.NotificationManager nm = context.getSystemService(android.app.NotificationManager.class);
        for (int i = 0; i < 60; i++) {
            for (Notification n : shadowOf(nm).getAllNotifications()) {
                if (Notification.CATEGORY_MISSED_CALL.equals(n.category)) {
                    return n;
                }
            }
            Thread.sleep(25);
        }
        return null;
    }

    private void ringThenStop() {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        listener.onNotificationRemoved(ringing("com.whatsapp", "k1", "Corvan"));
        ShadowLooper.idleMainLooper(CallTracker.GRACE_MS + 1, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    public void unansweredCallIsToldAsMissedAndMarkedInTheHistory() throws InterruptedException {
        ringThenStop();

        Notification missed = missedNotification();
        assertNotNull(missed);
        assertEquals(context.getString(R.string.missed_title, "WhatsApp"),
                missed.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals("Corvan", missed.extras.getCharSequence(Notification.EXTRA_TEXT).toString());
        // Calling from the watch is off: no Call back.
        assertNull(missed.actions);
        for (int i = 0; i < 40 && Prefs.callHistory(context).isEmpty(); i++) {
            Thread.sleep(25);
        }
        assertTrue(Prefs.callHistory(context).get(0).missed);
        assertEquals(EventLog.MISSED, Prefs.events(context).get(0).code);
    }

    @Test
    public void answeredCallIsNotMissed() throws InterruptedException {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        listener.onNotificationPosted(ongoing("com.whatsapp", "k1"));
        listener.onNotificationRemoved(ongoing("com.whatsapp", "k1"));
        ShadowLooper.idleMainLooper(CallTracker.GRACE_MS + 1, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertNull(missedNotification());
    }

    @Test
    public void declinedOnTheWatchIsNotMissed() throws InterruptedException {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        RelayListener.incomingHandledOnWatch();
        listener.onNotificationRemoved(ringing("com.whatsapp", "k1", "Corvan"));
        ShadowLooper.idleMainLooper(CallTracker.GRACE_MS + 1, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertNull(missedNotification());
    }

    private List<EventLog.Entry> withCode(String code) {
        List<EventLog.Entry> out = new java.util.ArrayList<>();
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code)) {
                out.add(e);
            }
        }
        return out;
    }

    @Test
    public void inCallNotificationWithNoTrackedCallIsRecordedOnce() {
        listener.onNotificationPosted(ongoing("com.whatsapp", "k9"));
        listener.onNotificationPosted(ongoing("com.whatsapp", "k9"));   // the call timer refreshes it constantly

        List<EventLog.Entry> untracked = withCode(EventLog.UNTRACKED_CALL);
        assertEquals(1, untracked.size());
        assertEquals("com.whatsapp", untracked.get(0).app);
        assertEquals("", untracked.get(0).detail);
    }

    @Test
    public void relayedCallIsNotUntracked() {
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        listener.onNotificationPosted(ongoing("com.whatsapp", "k1"));

        assertEquals(0, withCode(EventLog.UNTRACKED_CALL).size());
    }

    @Test
    public void aSecondUntrackedCallIsRecordedAfterTheFirstEnds() {
        listener.onNotificationPosted(ongoing("com.whatsapp", "k9"));
        listener.onNotificationRemoved(ongoing("com.whatsapp", "k9"));
        listener.onNotificationPosted(ongoing("com.whatsapp", "k9"));

        assertEquals(2, withCode(EventLog.UNTRACKED_CALL).size());
    }

    @Test
    public void anotherAppsCallDuringARelayedOneIsNotUntracked() {
        // Busy with WhatsApp: Telegram's call is a different story, already logged as busy when it rang.
        listener.onNotificationPosted(ringing("com.whatsapp", "k1", "Corvan"));
        listener.onNotificationPosted(ongoing("org.telegram.messenger", "t1"));

        assertEquals(0, withCode(EventLog.UNTRACKED_CALL).size());
    }
}
