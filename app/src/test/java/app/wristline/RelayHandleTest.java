package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowTelecomManager;

/** Which Telecom account a relayed call rings on: one decision, made in relayHandle. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, shadows = RelayHandleTest.KeepsEnabled.class)
public class RelayHandleTest {
    /**
     * Real Telecom keeps the user's enabled choice when an app re-registers its account (the
     * listener does, before each call); the stock shadow takes the new account's flag instead.
     */
    @Implements(TelecomManager.class)
    public static class KeepsEnabled extends ShadowTelecomManager {
        @Implementation
        @Override
        protected void registerPhoneAccount(PhoneAccount account) {
            PhoneAccount before = getPhoneAccount(account.getAccountHandle());
            if (before != null) {
                org.robolectric.util.ReflectionHelpers.setField(account, "mIsEnabled", before.isEnabled());
            }
            super.registerPhoneAccount(account);
        }
    }

    private Context context;
    private TelecomManager telecom;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        telecom = context.getSystemService(TelecomManager.class);
        RelayListener.registerAccount(context);
    }

    @After
    public void tearDown() {
        RelayListener.currentApp = null;
        RelayConnection.current = null;
        Prefs.setOwnerWar(context, false);
    }

    /** Models the user enabling (or not) the carrier in Calling accounts. */
    private void carrierAccountEnabled(boolean enabled) {
        PhoneAccount carrier = telecom.getPhoneAccount(RelayListener.carrierHandle(context));
        org.robolectric.util.ReflectionHelpers.setField(carrier, "mIsEnabled", enabled);
        telecom.registerPhoneAccount(carrier);
    }

    @Test
    public void aPhoneThatNeverLostTheWarRelaysSelfManaged() {
        carrierAccountEnabled(true);
        assertEquals(RelayListener.accountHandle(context), RelayListener.relayHandle(context));
    }

    @Test
    public void aWarPhoneWithTheCarrierOffRelaysSelfManaged() {
        Prefs.setOwnerWar(context, true);
        carrierAccountEnabled(false);
        assertEquals(RelayListener.accountHandle(context), RelayListener.relayHandle(context));
    }

    @Test
    public void aWarPhoneWithTheCarrierOnRelaysOnTheCarrier() {
        Prefs.setOwnerWar(context, true);
        carrierAccountEnabled(true);
        assertEquals(RelayListener.carrierHandle(context), RelayListener.relayHandle(context));
    }

    @Test
    public void theTestCallRingsOnTheChosenAccount() {
        Prefs.setOwnerWar(context, true);
        carrierAccountEnabled(true);
        assertTrue(RelayListener.startTestCall(context));
        ShadowTelecomManager.CallRecord call = shadowOf(telecom).getOnlyIncomingCall();
        assertEquals(RelayListener.carrierHandle(context), call.phoneAccount);
    }

    @Test
    public void isCarrierMatchesOnlyTheCarrierHandle() {
        assertTrue(RelayListener.isCarrier(RelayListener.carrierHandle(context), context));
        assertFalse(RelayListener.isCarrier(RelayListener.accountHandle(context), context));
        assertFalse(RelayListener.isCarrier(null, context));
    }
}
