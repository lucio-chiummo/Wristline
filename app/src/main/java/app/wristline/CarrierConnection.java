package app.wristline;

import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;

/**
 * A second Telecom call, a managed one, that exists only so Telecom owns the audio mode while the
 * calling app's call has its voice on the watch (see docs/BETA8_HYPEROS_FIX.md). On HyperOS no
 * self-managed call gets that, and without Telecom as the mode owner the calling app's audio
 * framework closes the watch's voice link again and again — the war beta 7 lost on every call.
 *
 * Carries no audio of its own: it is active at once, routed to the watch, and destroyed with the
 * call it stands in for. Only made on a phone that has lost the war (see {@link Prefs#ownerWar}).
 */
public class CarrierConnection extends Connection {
    CarrierConnection() {
        setConnectionCapabilities(CAPABILITY_MUTE);
        setAudioModeIsVoip(true);
        setActive();
        setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
    }

    @Override
    public void onDisconnect() {
        // The user ended the stand-in on the phone's call screen: the real call carries on.
        end(new DisconnectCause(DisconnectCause.LOCAL));
    }

    @Override
    public void onAbort() {
        end(new DisconnectCause(DisconnectCause.CANCELED));
    }

    void end(DisconnectCause cause) {
        setDisconnected(cause);
        destroy();
    }
}
