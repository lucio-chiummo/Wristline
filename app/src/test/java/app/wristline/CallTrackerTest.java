package app.wristline;

import static app.wristline.CallTracker.Result.CHECK_LATER;
import static app.wristline.CallTracker.Result.END;
import static app.wristline.CallTracker.Result.IGNORED;
import static app.wristline.CallTracker.Result.NONE;
import static app.wristline.CallTracker.Result.ONGOING;
import static app.wristline.CallTracker.Result.START;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallTrackerTest {
    @Test
    public void whatsAppSameKeyLifecycle() {
        CallTracker t = new CallTracker();
        assertEquals(START, t.incoming("com.whatsapp", "k", true));
        assertEquals(ONGOING, t.ongoing("com.whatsapp", "k"));
        assertEquals(END, t.removed("com.whatsapp", "k"));
        assertFalse(t.isTracking());
    }

    @Test
    public void ongoingBeforeIncomingRemoved() { // Instagram keeps the ringing notification
        CallTracker t = new CallTracker();
        t.incoming("com.instagram.android", "igvc", true);
        assertEquals(ONGOING, t.ongoing("com.instagram.android", "20025"));
        assertEquals(NONE, t.removed("com.instagram.android", "igvc"));
        assertEquals(END, t.removed("com.instagram.android", "20025"));
    }

    @Test
    public void incomingRemovedThenOngoingWithinGrace() { // Telegram swaps 202 for 201 on answer
        CallTracker t = new CallTracker();
        t.incoming("org.telegram.messenger", "202", true);
        assertEquals(CHECK_LATER, t.removed("org.telegram.messenger", "202"));
        assertEquals(ONGOING, t.ongoing("org.telegram.messenger", "201"));
        assertEquals(NONE, t.graceExpired());
        assertEquals(END, t.removed("org.telegram.messenger", "201"));
    }

    @Test
    public void declinedEndsAfterGrace() {
        CallTracker t = new CallTracker();
        t.incoming("org.telegram.messenger", "202", true);
        assertEquals(CHECK_LATER, t.removed("org.telegram.messenger", "202"));
        assertEquals(END, t.graceExpired());
        assertFalse(t.isTracking());
    }

    @Test
    public void secondPackageIgnoredWhileTracking() {
        CallTracker t = new CallTracker();
        t.incoming("com.whatsapp", "k", true);
        assertEquals(IGNORED, t.incoming("com.instagram.android", "igvc", true));
        assertEquals(NONE, t.removed("com.instagram.android", "igvc"));
        assertEquals("com.whatsapp", t.trackedPackage());
    }

    @Test
    public void notAllowedDoesNotStartButTrackedCallContinues() {
        CallTracker t = new CallTracker();
        assertEquals(IGNORED, t.incoming("com.whatsapp", "a", false));
        assertFalse(t.isTracking());
        t.incoming("com.whatsapp", "b", true);
        assertEquals(NONE, t.incoming("com.whatsapp", "b", false)); // repost while ringing, switch now off
        assertEquals(ONGOING, t.ongoing("com.whatsapp", "b"));
    }

    @Test
    public void ongoingFromOtherPackageIsNone() {
        CallTracker t = new CallTracker();
        t.incoming("com.whatsapp", "k", true);
        assertEquals(NONE, t.ongoing("com.instagram.android", "20025"));
    }

    @Test
    public void graceExpiredWhenIdleIsNone() {
        assertEquals(NONE, new CallTracker().graceExpired());
    }

    @Test
    public void incomingRemovedWhileWatchTakesAudioEndsAtOnce() { // app hung up on our active call
        CallTracker t = new CallTracker();
        t.incoming("com.whatsapp", "k", true);
        assertEquals(END, t.removed("com.whatsapp", "k", true));
        assertFalse(t.isTracking());
    }

    @Test
    public void ringingKeyRepostedAsNonCallActsAsRemoved() { // Instagram turns igvc into "missed call"
        CallTracker t = new CallTracker();
        t.incoming("com.instagram.android", "igvc", true);
        assertEquals(CHECK_LATER, t.notACall("com.instagram.android", "igvc"));
        assertEquals(END, t.graceExpired());
    }

    @Test
    public void unrelatedNonCallIsNone() {
        CallTracker t = new CallTracker();
        t.incoming("com.whatsapp", "k", true);
        assertEquals(NONE, t.notACall("com.whatsapp", "message"));
        assertEquals("com.whatsapp", t.trackedPackage());
    }

    @Test
    public void singleNotificationAppEndsAtOnceWhenRingingGoes() { // WhatsApp: caller cancelled
        CallTracker t = new CallTracker();
        t.incoming("com.whatsapp", "k", true);
        assertEquals(END, t.removed("com.whatsapp", "k", false, false));
        assertFalse(t.isTracking());
    }

    @Test
    public void onlyTheTrackedRingingKeyIsTheIncomingCall() { // a second call from the same app
        CallTracker t = new CallTracker();
        t.incoming("com.whatsapp", "first", true);
        assertTrue(t.isIncomingKey("com.whatsapp", "first"));
        assertEquals(NONE, t.incoming("com.whatsapp", "second", true));
        assertFalse(t.isIncomingKey("com.whatsapp", "second"));
    }

    @Test
    public void staleGraceCheckDoesNotEndTheNextCall() {
        CallTracker t = new CallTracker();
        t.incoming("org.telegram.messenger", "202", true);
        t.removed("org.telegram.messenger", "202");
        int firstCall = t.callId();
        assertEquals(END, t.graceExpired(firstCall));
        t.incoming("org.telegram.messenger", "202b", true);
        t.removed("org.telegram.messenger", "202b");
        assertEquals(NONE, t.graceExpired(firstCall)); // first call's timer firing late
        assertEquals(END, t.graceExpired(t.callId()));
    }

    @Test
    public void outgoingCallFollowsTheAppsInCallNotification() {
        CallTracker t = new CallTracker();
        assertEquals(START, t.outgoing("com.whatsapp"));
        assertEquals(ONGOING, t.ongoing("com.whatsapp", "k"));
        assertEquals(END, t.removed("com.whatsapp", "k", false, false));
    }

    @Test
    public void outgoingCallThatNeverStartsEndsAfterTheCheck() {
        CallTracker t = new CallTracker();
        t.outgoing("com.whatsapp");
        assertEquals(END, t.graceExpired(t.callId()));
    }

    @Test
    public void outgoingIgnoredWhileAnotherCallIsRelayed() {
        CallTracker t = new CallTracker();
        t.incoming("org.telegram.messenger", "202", true);
        assertEquals(IGNORED, t.outgoing("com.whatsapp"));
    }

    @Test
    public void ringingNotificationOfTheAppDuringOutgoingIsNotANewIncomingCall() {
        CallTracker t = new CallTracker();
        t.outgoing("com.whatsapp");
        assertEquals(NONE, t.incoming("com.whatsapp", "k", true));
    }
}
