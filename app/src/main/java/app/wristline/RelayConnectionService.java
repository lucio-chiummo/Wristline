package app.wristline;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/** Creates the Telecom call that the Pixel Watch companion bridges to the watch. */
public class RelayConnectionService extends ConnectionService {
    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle account, ConnectionRequest request) {
        String caller = request.getExtras().getString(RelayListener.EXTRA_CALLER, getString(R.string.phone_account_label));
        boolean managed = RelayListener.isCarrier(account, this);
        RelayConnection connection = new RelayConnection(this, managed);
        connection.setAddress(Uri.fromParts(PhoneAccount.SCHEME_SIP, caller, null), TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName(caller, TelecomManager.PRESENTATION_ALLOWED);
        connection.setRinging();
        boolean testCall = request.getExtras().getBoolean(TestCall.EXTRA);
        if (testCall) {
            connection.markTestCall();
        }
        RelayConnection.current = connection;
        if (managed) {
            Diagnostics.record(this, EventLog.CARRIER, testCall ? null : RelayListener.currentApp, "managed relay");
        }
        if (request.getExtras().getBoolean(RelayListener.EXTRA_ANSWERED)) {
            connection.startAnswered();
        }
        Log.i(RelayListener.TAG, "mirrored call created");
        Diagnostics.record(this, EventLog.CALL_CREATED, testCall ? null : RelayListener.currentApp,
                testCall ? "test call" : "");
        return connection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle account, ConnectionRequest request) {
        boolean managed = RelayListener.isCarrier(account, this);
        android.os.Bundle extras = request.getExtras();
        if (managed && (extras == null || !extras.containsKey(RelayListener.EXTRA_CALLER))) {
            // Only a call Wristline places (it names the caller) is relayed; the carrier account
            // must not become a way to dial out from the phone.
            Log.w(RelayListener.TAG, "refused a call on the carrier that Wristline didn't place");
            Diagnostics.record(this, EventLog.CARRIER, RelayListener.currentApp, "refused");
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR));
        }
        String caller = extras == null ? null : extras.getString(RelayListener.EXTRA_CALLER);
        if (caller == null) {
            caller = getString(R.string.phone_account_label);
        }
        RelayConnection connection = new RelayConnection(this, managed);
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName(caller, TelecomManager.PRESENTATION_ALLOWED);
        RelayConnection.current = connection;
        if (extras != null && extras.getBoolean(RelayListener.EXTRA_ANSWERED)) {
            // Answered on the notification of a war phone: placed, not rung, so no call filtering.
            connection.startAnswered();
        } else {
            connection.startOutgoing(extras != null && extras.getBoolean(RelayListener.EXTRA_FULL_MODE));
        }
        if (managed) {
            Diagnostics.record(this, EventLog.CARRIER, RelayListener.currentApp, "managed relay");
        }
        Log.i(RelayListener.TAG, "outgoing mirror created");
        return connection;
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle account, ConnectionRequest request) {
        Log.w(RelayListener.TAG, "Telecom could not create the outgoing mirror; starting the call on the phone only");
        if (RelayListener.isCarrier(account, this)) {
            // HyperOS drops a managed call at once while any self-managed call is on.
            Diagnostics.record(this, EventLog.CARRIER, RelayListener.currentApp, "telecom refused");
        }
        if (request != null && request.getExtras() != null
                && request.getExtras().getBoolean(RelayListener.EXTRA_ANSWERED)) {
            answerOnThePhone();
            return;
        }
        Runnable startApp = RelayConnection.pendingAppStart;
        RelayConnection.pendingAppStart = null;
        if (startApp != null) {
            startApp.run();
        }
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle account, ConnectionRequest request) {
        Log.w(RelayListener.TAG, "Telecom could not create the mirrored call");
        boolean testCall = request != null && request.getExtras().getBoolean(TestCall.EXTRA);
        Diagnostics.record(this, EventLog.CALL_NOT_CREATED, testCall ? null : RelayListener.currentApp,
                testCall ? "test call" : "");
        if (request != null && request.getExtras().getBoolean(RelayListener.EXTRA_ANSWERED)) {
            answerOnThePhone();
        }
    }

    /** Already answered on the watch: don't lose the call, take it on the phone. */
    private void answerOnThePhone() {
        CallActions call = RelayListener.incomingCall;
        RelayListener.send(this, call == null ? null : call.answer);
    }
}
