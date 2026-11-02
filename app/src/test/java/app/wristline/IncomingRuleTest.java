package app.wristline;

import static app.wristline.IncomingRule.Decision.DO_NOT_DISTURB;
import static app.wristline.IncomingRule.Decision.PHONE_IN_USE;
import static app.wristline.IncomingRule.Decision.RELAY;
import static app.wristline.IncomingRule.Decision.SWITCHED_OFF;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IncomingRuleTest {
    @Test
    public void relaysWhenOnAndDoNotDisturbLetsItThrough() {
        assertEquals(RELAY, IncomingRule.decide(true, true, true, true, false, false));
    }

    @Test
    public void switchesComeFirst() {
        assertEquals(SWITCHED_OFF, IncomingRule.decide(false, true, true, false, false, false));
        assertEquals(SWITCHED_OFF, IncomingRule.decide(true, false, true, true, false, false));
    }

    @Test
    public void silencedByDoNotDisturb() {
        assertEquals(DO_NOT_DISTURB, IncomingRule.decide(true, true, true, false, false, false));
    }

    @Test
    public void notFollowingDoNotDisturbRingsAnyway() {
        assertEquals(RELAY, IncomingRule.decide(true, true, false, false, false, false));
    }

    @Test
    public void notWhileUsingThePhone() {
        assertEquals(PHONE_IN_USE, IncomingRule.decide(true, true, true, true, true, true));
        assertEquals(RELAY, IncomingRule.decide(true, true, true, true, true, false));
        // Off by default: ringing the watch while the phone is in hand is the usual behaviour.
        assertEquals(RELAY, IncomingRule.decide(true, true, true, true, false, true));
    }
}
