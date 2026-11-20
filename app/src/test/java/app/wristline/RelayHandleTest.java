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

    /** A ringing call the way WhatsApp posts it: call category, full-screen intent, two buttons. */
    private android.service.notification.StatusBarNotification whatsAppRinging() {
        android.app.PendingIntent tap = android.app.PendingIntent.getBroadcast(context, 1,
                new android.content.Intent("test.tap"), android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification n = new android.app.Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Corvan")
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .setFullScreenIntent(tap, true)
                .addAction(new android.app.Notification.Action.Builder(null, "Decline", tap).build())
                .addAction(new android.app.Notification.Action.Builder(null, "Answer", tap).build())
                .build();
        try {
            return android.service.notification.StatusBarNotification.class
                    .getConstructor(String.class, String.class, int.class, String.class, int.class, int.class,
                            android.app.Notification.class, android.os.UserHandle.class, String.class, long.class)
                    .newInstance("com.whatsapp", "com.whatsapp", 1, "k1", 1000, 1, n,
                            android.os.Process.myUserHandle(), null, 0L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void aRelayedCallOnAWarPhoneRingsOnTheCarrier() {
        Prefs.setOwnerWar(context, true);
        carrierAccountEnabled(true);
        RelayListener listener = org.robolectric.Robolectric.buildService(RelayListener.class).create().get();
        try {
            listener.onNotificationPosted(whatsAppRinging());

            assertEquals(RelayListener.carrierHandle(context), shadowOf(telecom).getOnlyIncomingCall().phoneAccount);
        } finally {
            RelayListener.clearCall(context);
            listener.onListenerDisconnected();
        }
    }

    @Test
    public void aWatchCallOutOnAWarPhoneIsPlacedOnTheCarrier() {
        Prefs.setOwnerWar(context, true);
        carrierAccountEnabled(true);
        RelayListener listener = org.robolectric.Robolectric.buildService(RelayListener.class).create().get();
        listener.onListenerConnected();
        try {
            assertTrue(RelayListener.startOutgoing(context, SupportedApp.WHATSAPP, "Corvan", () -> { }));

            assertEquals(RelayListener.carrierHandle(context), shadowOf(telecom).getOnlyOutgoingCall().phoneAccount);
        } finally {
            RelayConnection.pendingAppStart = null;
            RelayListener.clearCall(context);
            listener.onListenerDisconnected();
        }
    }

    @Test
    public void isCarrierMatchesOnlyTheCarrierHandle() {
        assertTrue(RelayListener.isCarrier(RelayListener.carrierHandle(context), context));
        assertFalse(RelayListener.isCarrier(RelayListener.accountHandle(context), context));
        assertFalse(RelayListener.isCarrier(null, context));
    }
}
