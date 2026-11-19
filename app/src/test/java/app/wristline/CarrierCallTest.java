package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
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
 * The carrier call: on a phone that lost the mode-owner war, a second Telecom call — a managed
 * one — makes Telecom own the audio mode, so the calling app can't keep pulling the voice off the
 * watch (see docs/BETA8_HYPEROS_FIX.md).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class CarrierCallTest {
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
    }

    @After
    public void tearDown() {
        VoiceHold.sdk = android.os.Build.VERSION.SDK_INT;
        RelayListener.currentApp = null;
        RelayListener.incomingCall = null;
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
        org.robolectric.Shadows.shadowOf(context.getSystemService(android.media.AudioManager.class))
                .setAvailableCommunicationDevices(java.util.List.of(
                        org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                                .setType(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build()));
    }

    private static final int EARPIECE = CallAudioState.ROUTE_EARPIECE;
    private static final int BLUETOOTH = CallAudioState.ROUTE_BLUETOOTH;
    private static final int ALL = EARPIECE | BLUETOOTH | CallAudioState.ROUTE_SPEAKER;

    private static android.telecom.CallAudioState audio(int route, int supported) {
        return new android.telecom.CallAudioState(false, route, supported);
    }

    private ConnectionRequest carrierRequest() {
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        return new ConnectionRequest(RelayListener.carrierHandle(context), null, extras);
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

    /** An answered call (answered on our ringing notification), with the watch offered for calls. */
    private RelayConnection answeredCall() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;
        RelayListener.incomingCall = ringingCall();
        offerWatchToApps();
        RelayConnectionService created = Robolectric.buildService(RelayConnectionService.class).create().get();
        Bundle extras = new Bundle();
        extras.putString(RelayListener.EXTRA_CALLER, "WhatsApp · Annaly");
        extras.putBoolean(RelayListener.EXTRA_ANSWERED, true);
        RelayConnection connection = (RelayConnection) created.onCreateIncomingConnection(null,
                new ConnectionRequest(null, null, extras));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        return connection;
    }

    /** Telecom making the carrier call Wristline asked for. */
    private Connection carrierUp() {
        return service.onCreateOutgoingConnection(RelayListener.carrierHandle(context), carrierRequest());
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
        assertTrue("the mirror stays self-managed",
                relay != null && (relay.getCapabilities() & PhoneAccount.CAPABILITY_SELF_MANAGED) != 0);
    }

    @Test
    public void aWarCallAsksTelecomForTheCarrierInsteadOfHoldingTheVoice() {
        RelayConnection connection = answeredCall();

        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));

        assertEquals("Telecom must be asked for the carrier call, not fought for the mode",
                RelayListener.carrierHandle(context), shadowOf(telecom).getOnlyOutgoingCall().phoneAccount);
        assertFalse("no VoiceHold while Telecom can own the mode", voiceHeld());
        assertEquals(android.media.AudioManager.MODE_NORMAL, audioMode());

        Connection carrier = carrierUp();
        assertEquals("the carrier is active at once: it only exists to own the mode",
                Connection.STATE_ACTIVE, carrier.getState());
        assertEquals("the carrier must not be self-managed, or Telecom won't hold the mode for it",
                0, carrier.getConnectionProperties() & Connection.PROPERTY_SELF_MANAGED);
        assertEquals("created", latest(EventLog.CARRIER).detail);
        connection.onAbort();
    }

    @Test
    public void aPhoneThatNeverLostAWarHoldsTheVoiceItself() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredCall();

        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));

        assertTrue("no carrier on a phone that never lost the war", shadowOf(telecom).getAllOutgoingCalls().isEmpty());
        assertEquals("hold=taken", latest(EventLog.AUDIO_LINK).detail);
        connection.onAbort();
    }

    @Test
    public void theCarrierEndsWithTheCallAndIsRecorded() {
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        carrierUp();

        connection.onAbort();

        assertNull("the carrier must not outlive the call", connection.myCarrier);
        assertEquals("ended", latest(EventLog.CARRIER).detail);
    }

    @Test
    public void movingTheVoiceToThePhoneEndsTheCarrierBackRecreatesIt() {
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        carrierUp();

        RelayConnection.moveVoice(true);

        assertNull(connection.myCarrier);
        assertEquals("ended", latest(EventLog.CARRIER).detail);

        RelayConnection.moveVoice(false);

        assertEquals("the carrier comes back with the voice",
                RelayListener.carrierHandle(context), shadowOf(telecom).getLastOutgoingCall().phoneAccount);
        assertEquals(2, shadowOf(telecom).getAllOutgoingCalls().size());
        connection.onAbort();
    }

    @Test
    public void aCarrierTelecomRefusesFallsBackToTheVoiceHold() {
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));

        service.onCreateOutgoingConnectionFailed(RelayListener.carrierHandle(context), carrierRequest());

        assertEquals("failed", latest(EventLog.CARRIER).detail);
        assertEquals("the call must carry on the beta 7 way", "hold=taken",
                latest(EventLog.AUDIO_LINK).detail);
        assertEquals(android.media.AudioManager.MODE_IN_COMMUNICATION, audioMode());
        connection.onAbort();
    }

    @Test
    public void mirrorRouteNoiseDoesNotGiveUpWhileTheCarrierIsUp() {
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        carrierUp();

        for (int i = 0; i < RelayConnection.MAX_BOUNCES + 1; i++) {
            connection.onCallAudioStateChanged(audio(EARPIECE, ALL));
        }

        assertEquals("the mirror's route is not the call's voice while the carrier is up",
                Boolean.TRUE, RelayConnection.voiceOnWatch());
        connection.onAbort();
    }

    @Test
    public void aCarrierForACallThatIsOverIsPutDownAtOnce() {
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        RelayConnection.current = null;   // the call ended while Telecom was still making the carrier

        Connection carrier = carrierUp();

        assertEquals("nobody to own the mode for: the carrier must go at once",
                Connection.STATE_DISCONNECTED, carrier.getState());
        assertNull(connection.myCarrier);
    }

    @Test
    public void aCarrierArrivingAfterTheVoiceMovedToThePhoneIsPutDownAtOnce() {
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        RelayConnection.moveVoice(true);   // the user chose the phone while the carrier was still coming

        Connection carrier = carrierUp();

        assertEquals("the carrier would pull the voice back to the watch against the user's choice",
                Connection.STATE_DISCONNECTED, carrier.getState());
        assertNull(connection.myCarrier);
        assertEquals("ended", latest(EventLog.CARRIER).detail);
    }

    @Test
    public void theCarrierIsAskedForOnlyOncePerCall() {
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        connection.onCallAudioStateChanged(audio(EARPIECE, ALL));
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));

        assertEquals("asked but not made yet must not ask again",
                1, shadowOf(telecom).getAllOutgoingCalls().size());
        connection.onAbort();
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
    public void aLossInsideTheWarWindowGivesUpAndArmsTheWar() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredCall();
        connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
        offerEarpieceToo();

        switchToEarpiece();
        idle(VoiceHold.WATCH_CHECK_MS);

        assertEquals("owner war (left without Telecom seeing it)", latest(EventLog.AUDIO_GAVE_UP).detail);
        assertTrue("the invisible war must arm the carrier for the next call", Prefs.ownerWar(context));
        assertEquals(Boolean.FALSE, RelayConnection.voiceOnWatch());
        connection.onAbort();
    }

    @Test
    public void aLossLaterThanTheWarWindowGivesUpWithoutArming() {
        Prefs.setOwnerWar(context, false);
        RelayConnection connection = answeredCall();
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
        RelayConnection connection = answeredCall();
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
        RelayConnection connection = answeredCall();
        for (int i = 0; i < RelayConnection.MAX_BOUNCES; i++) {
            connection.onCallAudioStateChanged(audio(BLUETOOTH, ALL));
            idle(RelayConnection.WAR_WINDOW_MS + 500);
            connection.onCallAudioStateChanged(audio(EARPIECE, ALL));
        }

        assertEquals("left the watch " + RelayConnection.MAX_BOUNCES + " times", latest(EventLog.AUDIO_GAVE_UP).detail);
        assertFalse("repeated real dropouts must not latch the carrier", Prefs.ownerWar(context));
        connection.onAbort();
    }
}
