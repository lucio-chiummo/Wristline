package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A war phone relays the call as one managed Telecom call on the carrier account: Telecom owns the
 * audio mode and the routes, so Wristline only asks for the watch and never takes the voice.
 * Robolectric runs no Telecom call filtering or audio arbitration: §15–16 of
 * docs/POCO_BETA8_WAR_PLAN.md hold the hardware evidence these tests can't give.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, shadows = RelayHandleTest.KeepsEnabled.class)
public class ManagedRelayTest {
    private Context context;
    private RelayConnectionService service;
    private TelecomManager telecom;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        service = Robolectric.buildService(RelayConnectionService.class).create().get();
        telecom = context.getSystemService(TelecomManager.class);
        VoiceHold.sdk = 37;
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        Prefs.setOwnerWar(context, true);
        RelayListener.registerAccount(context);
        carrierAccountEnabled(true);
    }

    private void carrierAccountEnabled(boolean enabled) {
        PhoneAccount carrier = telecom.getPhoneAccount(RelayListener.carrierHandle(context));
        org.robolectric.util.ReflectionHelpers.setField(carrier, "mIsEnabled", enabled);
        telecom.registerPhoneAccount(carrier);
    }

    @After
    public void tearDown() {
        VoiceHold.sdk = android.os.Build.VERSION.SDK_INT;
        RelayListener.currentApp = null;
        RelayListener.incomingCall = null;
        RelayListener.useFullModeForCall = false;
        RelayConnection.current = null;
        Prefs.setOwnerWar(context, false);
    }

    private EventLog.Entry latest(String code) {
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new AssertionError("no " + code + " recorded");
    }

    private boolean recorded(String code, String detailStart) {
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(code) && e.detail.startsWith(detailStart)) {
                return true;
            }
        }
        return false;
    }

    private boolean voiceHeld() {
        for (EventLog.Entry e : Prefs.events(context)) {
            if (e.code.equals(EventLog.AUDIO_LINK) && e.detail.startsWith("hold=")) {
                return true;
            }
        }
        return false;
    }

    private int audioMode() {
        return context.getSystemService(android.media.AudioManager.class).getMode();
    }

    private void offerWatchToApps() {
        shadowOf(context.getSystemService(android.media.AudioManager.class))
                .setAvailableCommunicationDevices(java.util.List.of(
                        org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                                .setType(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build()));
    }

    private static final int EARPIECE = CallAudioState.ROUTE_EARPIECE;
    private static final int BLUETOOTH = CallAudioState.ROUTE_BLUETOOTH;
    private static final int ALL = EARPIECE | BLUETOOTH | CallAudioState.ROUTE_SPEAKER;

    private static CallAudioState audio(int route, int supported) {
        return new CallAudioState(false, route, supported);
    }

    private ConnectionRequest request() {
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        return new ConnectionRequest(null, null, extras);
    }

    private android.app.PendingIntent appAnswer() {
        return android.app.PendingIntent.getBroadcast(context, 7, new android.content.Intent("test.answer"),
                android.app.PendingIntent.FLAG_IMMUTABLE);
    }

    private CallActions ringingCall() {
        android.app.PendingIntent other = android.app.PendingIntent.getBroadcast(context, 8,
                new android.content.Intent("test.other"), android.app.PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification n = new android.app.Notification.Builder(context, "calls")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Annaly")
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .setFullScreenIntent(other, true)
                .addAction(new android.app.Notification.Action.Builder(null, "Decline", other).build())
                .addAction(new android.app.Notification.Action.Builder(null, "Answer", appAnswer()).build())
                .build();
        return CallActions.from(n, false);
    }

    private boolean appAnswered() {
        for (android.content.Intent sent : shadowOf((android.app.Application) context).getBroadcastIntents()) {
            if ("test.answer".equals(sent.getAction())) {
                return true;
            }
        }
        return false;
    }

    /** Answer tapped on Wristline's ringing notification, voice through the watch set up. */
    private boolean answerOnTheNotification() {
        RelayListener.incomingCall = ringingCall();
        RelayListener.useFullModeForCall = true;
        return RelayListener.answerWithVoiceOnWatch(context);
    }

    @Test
    public void aNotificationAnswerOnAWarPhonePlacesTheManagedCall() {
        assertTrue(answerOnTheNotification());

        org.robolectric.shadows.ShadowTelecomManager.OutgoingCallRecord placed = shadowOf(telecom).getOnlyOutgoingCall();
        assertEquals(RelayListener.carrierHandle(context), placed.phoneAccount);
        Bundle callExtras = placed.extras.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
        assertTrue(callExtras.getBoolean(RelayListener.EXTRA_ANSWERED));
        assertTrue(callExtras.containsKey(RelayListener.EXTRA_CALLER));
        assertTrue("a managed incoming call is filtered before it may go active",
                shadowOf(telecom).getAllIncomingCalls().isEmpty());
    }

    @Test
    public void aNotificationAnswerElsewhereStaysAnIncomingCall() {
        Prefs.setOwnerWar(context, false);

        assertTrue(answerOnTheNotification());

        assertEquals(RelayListener.accountHandle(context), shadowOf(telecom).getOnlyIncomingCall().phoneAccount);
        assertTrue(shadowOf(telecom).getAllOutgoingCalls().isEmpty());
    }

    @Test
    public void anAnsweredOutgoingRequestOnTheCarrierGoesActiveAndAnswersTheApp() {
        RelayListener.incomingCall = ringingCall();
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_ANSWERED, true);

        Connection c = service.onCreateOutgoingConnection(RelayListener.carrierHandle(context),
                new ConnectionRequest(RelayListener.carrierHandle(context),
                        Uri.fromParts(PhoneAccount.SCHEME_SIP, "WhatsApp · Annaly", null), extras));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();

        assertTrue(c instanceof RelayConnection);
        assertTrue(((RelayConnection) c).managed);
        assertEquals(Connection.STATE_ACTIVE, c.getState());
        assertFalse("app answered before the watch's audio link", appAnswered());
        idle(RelayConnection.ANSWER_DELAY_MS);
        assertTrue("app never answered", appAnswered());
        ((RelayConnection) c).onAbort();
    }

    @Test
    public void aRefusedAnsweredManagedCallAnswersTheAppOnThePhone() {
        RelayListener.incomingCall = ringingCall();
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_ANSWERED, true);

        service.onCreateOutgoingConnectionFailed(RelayListener.carrierHandle(context),
                new ConnectionRequest(RelayListener.carrierHandle(context), null, extras));

        assertTrue("an answered call refused by Telecom must still be answered", appAnswered());
        assertNull(RelayConnection.current);
        assertEquals("telecom refused", latest(EventLog.CARRIER).detail);
    }

    @Test
    public void aRefusedWatchCallOutStillStartsTheApp() {
        boolean[] started = {false};
        RelayConnection.pendingAppStart = () -> started[0] = true;
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_FULL_MODE, true);

        service.onCreateOutgoingConnectionFailed(RelayListener.carrierHandle(context),
                new ConnectionRequest(RelayListener.carrierHandle(context), null, extras));

        assertTrue(started[0]);
        assertNull(RelayConnection.pendingAppStart);
        assertEquals("telecom refused", latest(EventLog.CARRIER).detail);
    }

    /** An answered managed call on the carrier account, with the watch offered for calls. */
    private RelayConnection answeredManagedCall() {
        RelayListener.incomingCall = ringingCall();
        offerWatchToApps();
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_ANSWERED, true);
        RelayConnection connection = (RelayConnection) service.onCreateIncomingConnection(
                RelayListener.carrierHandle(context), new ConnectionRequest(null, null, extras));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        return connection;
    }

    @Test
    public void anIncomingCallOnTheCarrierIsManagedAndVoip() {
        RelayConnection c = (RelayConnection) service.onCreateIncomingConnection(
                RelayListener.carrierHandle(context), request());
        assertTrue(c.managed);
        assertEquals(0, c.getConnectionProperties() & Connection.PROPERTY_SELF_MANAGED);
        assertTrue(c.getAudioModeIsVoip());
    }

    @Test
    public void anIncomingCallOnTheRelayStaysSelfManaged() {
        RelayConnection c = (RelayConnection) service.onCreateIncomingConnection(
                RelayListener.accountHandle(context), request());
        assertFalse(c.managed);
        assertEquals(Connection.PROPERTY_SELF_MANAGED, c.getConnectionProperties() & Connection.PROPERTY_SELF_MANAGED);
        assertTrue(c.getAudioModeIsVoip());
    }

    @Test
    public void aManagedCallRoutesToTheWatchWithoutTakingTheVoice() {
        RelayConnection connection = answeredManagedCall();
        assertTrue(connection.isActive());

        connection.onCallAudioStateChanged(audio(EARPIECE, ALL));

        assertEquals(BLUETOOTH, connection.requestedRoute);
        assertFalse("Telecom owns the voice: no VoiceHold take", voiceHeld());
        assertEquals(android.media.AudioManager.MODE_NORMAL, audioMode());
        connection.onAbort();
    }

    @Test
    public void aManagedCallsFallFromTheWatchNeverArmsOrReportsTheWar() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredManagedCall();

        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        connection.onCallAudioStateChanged(audio(EARPIECE, ALL));   // at once, inside WAR_WINDOW_MS

        assertFalse(recorded(EventLog.AUDIO_GAVE_UP, "owner war"));
        assertFalse("a managed call must not latch the war", Prefs.ownerWar(context));
        connection.onAbort();
    }

    @Test
    public void aManagedCallGivesUpAfterMaxBouncesLikeAnyOther() {
        RelayConnection connection = answeredManagedCall();

        for (int i = 0; i < RelayConnection.MAX_BOUNCES; i++) {
            connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
            connection.onCallAudioStateChanged(audio(EARPIECE, ALL));
        }

        assertEquals("left the watch " + RelayConnection.MAX_BOUNCES + " times",
                latest(EventLog.AUDIO_GAVE_UP).detail);
        assertEquals(Boolean.FALSE, RelayConnection.voiceOnWatch());
        connection.onAbort();
    }

    @Test
    public void movingAManagedCallsVoiceToThePhoneAndBackOnlyReroutes() {
        RelayConnection connection = answeredManagedCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));

        assertTrue(RelayConnection.moveVoice(true));
        assertEquals(EARPIECE, connection.requestedRoute);
        assertFalse(voiceHeld());
        connection.onCallAudioStateChanged(audio(EARPIECE, ALL));   // Telecom reports the move

        assertTrue(RelayConnection.moveVoice(false));
        assertEquals(BLUETOOTH, connection.requestedRoute);
        assertFalse(voiceHeld());
        connection.onAbort();
    }

    @Test
    public void aManagedCallIsRecorded() {
        service.onCreateIncomingConnection(RelayListener.carrierHandle(context), request());

        assertEquals("managed relay", latest(EventLog.CARRIER).detail);
    }

    @Test
    public void aCallWristlineDidNotPlaceOnTheCarrierIsRefused() {
        Connection c = service.onCreateOutgoingConnection(RelayListener.carrierHandle(context),
                new ConnectionRequest(RelayListener.carrierHandle(context), Uri.parse("tel:+390000000"), new Bundle()));

        assertEquals(Connection.STATE_DISCONNECTED, c.getState());
        assertEquals(DisconnectCause.ERROR, c.getDisconnectCause().getCode());
        assertEquals("Wristline can't place phone calls", c.getDisconnectCause().getLabel().toString());
        assertEquals("Wristline can't place phone calls", c.getDisconnectCause().getDescription().toString());
        assertEquals("not placed by Wristline", c.getDisconnectCause().getReason());
        assertNull(RelayConnection.current);
    }

    @Test
    public void aWatchCallOutOnTheCarrierIsManaged() {
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_FULL_MODE, true);

        Connection c = service.onCreateOutgoingConnection(RelayListener.carrierHandle(context),
                new ConnectionRequest(RelayListener.carrierHandle(context), Uri.parse("tel:+390000000"), extras));

        assertTrue(c instanceof RelayConnection);
        assertTrue(((RelayConnection) c).managed);
        assertSame(c, RelayConnection.current);
        ((RelayConnection) c).onAbort();
    }

    /** An answered self-managed call on the relay account, with the watch offered for calls. */
    private RelayConnection answeredSelfManagedCall() {
        RelayListener.incomingCall = ringingCall();
        offerWatchToApps();
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_ANSWERED, true);
        RelayConnection connection = (RelayConnection) service.onCreateIncomingConnection(
                RelayListener.accountHandle(context), new ConnectionRequest(null, null, extras));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        return connection;
    }

    private void idle(long ms) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private android.media.AudioManager audioManager() {
        return context.getSystemService(android.media.AudioManager.class);
    }

    private void offerEarpieceToo() {
        shadowOf(audioManager()).setAvailableCommunicationDevices(java.util.List.of(
                org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                        .setType(android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE).build(),
                org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                        .setType(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build()));
    }

    private void switchToEarpiece() {
        audioManager().setCommunicationDevice(org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE).build());
    }

    @Test
    public void registersAManagedCarrierAccountBesideTheSelfManagedOne() {
        RelayListener.registerAccount(context);

        PhoneAccount carrier = null;
        PhoneAccount relay = null;
        for (PhoneAccount account : shadowOf(telecom).getAllPhoneAccounts()) {
            if ("carrier".equals(account.getAccountHandle().getId())) {
                carrier = account;
            } else if ("relay".equals(account.getAccountHandle().getId())) {
                relay = account;
            }
        }
        assertTrue("carrier account not registered", carrier != null);
        assertEquals("the carrier must be a managed call: Telecom owns its audio mode",
                0, carrier.getCapabilities() & PhoneAccount.CAPABILITY_SELF_MANAGED);
        assertTrue("the relay stays self-managed",
                relay != null && (relay.getCapabilities() & PhoneAccount.CAPABILITY_SELF_MANAGED) != 0);
    }

    @Test
    public void aPhoneThatNeverLostAWarHoldsTheVoiceItself() {
        Prefs.setOwnerWar(context, false);
        carrierAccountEnabled(false);
        RelayConnection connection = answeredSelfManagedCall();

        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));

        assertTrue("no second call on a phone that never lost the war", shadowOf(telecom).getAllOutgoingCalls().isEmpty());
        assertEquals("hold=taken", latest(EventLog.AUDIO_LINK).detail);
        connection.onAbort();
    }

    @Test
    public void aLossInsideTheWarWindowGivesUpAndArmsTheWar() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredSelfManagedCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        offerEarpieceToo();

        switchToEarpiece();
        idle(VoiceHold.WATCH_CHECK_MS);

        assertEquals("owner war (left without Telecom seeing it)", latest(EventLog.AUDIO_GAVE_UP).detail);
        assertTrue("the invisible war must arm the managed relay for the next call", Prefs.ownerWar(context));
        assertEquals(Boolean.FALSE, RelayConnection.voiceOnWatch());
        connection.onAbort();
    }

    @Test
    public void aLossLaterThanTheWarWindowGivesUpWithoutArming() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredSelfManagedCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        offerEarpieceToo();
        idle(RelayConnection.WAR_WINDOW_MS + 500);

        switchToEarpiece();
        idle(VoiceHold.WATCH_CHECK_MS);

        assertEquals("left without Telecom seeing it", latest(EventLog.AUDIO_GAVE_UP).detail);
        assertFalse("a late loss is a real drop, not a war", Prefs.ownerWar(context));
        connection.onAbort();
    }

    @Test
    public void aFallAt2500msIsWarWithTheWiderWindow() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredSelfManagedCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        idle(2500);

        connection.onCallAudioStateChanged(audio(EARPIECE, ALL));

        assertEquals("the POCO's 2.45 s fall must read as war", "owner war", latest(EventLog.AUDIO_GAVE_UP).detail);
        assertTrue(Prefs.ownerWar(context));
        connection.onAbort();
    }

    @Test
    public void aBounceGiveUpDoesNotArmTheWar() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredSelfManagedCall();
        for (int i = 0; i < RelayConnection.MAX_BOUNCES; i++) {
            connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
            idle(RelayConnection.WAR_WINDOW_MS + 500);
            connection.onCallAudioStateChanged(audio(EARPIECE, ALL));
        }

        assertEquals("left the watch " + RelayConnection.MAX_BOUNCES + " times", latest(EventLog.AUDIO_GAVE_UP).detail);
        assertFalse("repeated real dropouts must not latch the managed relay", Prefs.ownerWar(context));
        connection.onAbort();
    }
}
