package app.wristline;

import static app.wristline.CallShape.Kind.INCOMING;
import static app.wristline.CallShape.Kind.NONE;
import static app.wristline.CallShape.Kind.ONGOING;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Notification shapes from the Instagram capture (2026-11-01) and Telegram's VoIPService source. */
public class CallShapeTest {
    @Test
    public void instagramRinging() {
        assertEquals(INCOMING, new CallShape(true, true, 1, true, true, 2, false).classify(false));
    }

    @Test
    public void instagramInCall() {
        // Captured 2026-11-01 and seen again 2026-11-02: id 20025, CallStyle callType=2, "Hang up",
        // but no call category. Missing it ended Instagram calls on the watch after 2 s.
        assertEquals(ONGOING, new CallShape(false, true, 2, false, false, 1, true).classify(false));
    }

    @Test
    public void callStyleRingingWithoutCategory() {
        assertEquals(INCOMING, new CallShape(false, true, 1, true, true, 2, false).classify(false));
    }

    @Test
    public void missedCallIsNotIncoming() {
        assertEquals(NONE, new CallShape(true, false, null, false, false, 2, false).classify(false));
    }

    @Test
    public void whatsAppRingingWithoutCallStyle() {
        assertEquals(INCOMING, new CallShape(true, true, null, true, false, 2, false).classify(false));
    }

    @Test
    public void whatsAppOngoingWithoutCallStyle() {
        assertEquals(ONGOING, new CallShape(true, true, null, false, false, 3, false).classify(false));
    }

    @Test
    public void telegramOngoingNeedsFlag() {
        CallShape shape = new CallShape(false, true, null, false, false, 1, true);
        assertEquals(NONE, shape.classify(false));
        assertEquals(ONGOING, shape.classify(true));
    }

    @Test
    public void telegramOneButtonNotificationWithoutHangUpIsNotTheCall() { // live location: "Stop"
        assertEquals(NONE, new CallShape(false, true, null, false, false, 1, false).classify(true));
    }

    @Test
    public void ordinaryNotificationIsNone() {
        assertEquals(NONE, new CallShape(false, false, null, false, false, 2, false).classify(true));
    }

    @Test
    public void signalRinging() { // captured 2026-11-10: calls_v3, CallStyle callType=1, Decline/Answer
        assertEquals(INCOMING, new CallShape(true, true, 1, true, true, 2, false).classify(false));
    }

    @Test
    public void signalInCall() { // captured 2026-11-10: call_status, CallStyle callType=2, "Hang up"
        assertEquals(ONGOING, new CallShape(true, true, 2, false, false, 1, true).classify(false));
    }

    @Test
    public void signalConnectingStatusIsNotTheCall() { // "Connecting…": ongoing, no category, no actions
        assertEquals(NONE, new CallShape(false, true, null, false, false, 0, false).classify(false));
    }
}
