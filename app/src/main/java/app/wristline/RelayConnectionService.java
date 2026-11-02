package app.wristline;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/** Creates the Telecom call that the Pixel Watch companion bridges to the watch. */
public class RelayConnectionService extends ConnectionService {
    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle account, ConnectionRequest request) {
        String caller = request.getExtras().getString(RelayListener.EXTRA_CALLER, getString(R.string.phone_account_label));
        RelayConnection connection = new RelayConnection(this);
        connection.setAddress(Uri.fromParts(PhoneAccount.SCHEME_SIP, caller, null), TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName(caller, TelecomManager.PRESENTATION_ALLOWED);
        connection.setRinging();
        if (request.getExtras().getBoolean(TestCall.EXTRA)) {
            connection.markTestCall();
        }
        RelayConnection.current = connection;
        Log.i(RelayListener.TAG, "mirrored call created");
        return connection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle account, ConnectionRequest request) {
        android.os.Bundle extras = request.getExtras();
        String caller = extras == null ? null : extras.getString(RelayListener.EXTRA_CALLER);
        if (caller == null) {
            caller = getString(R.string.phone_account_label);
        }
        RelayConnection connection = new RelayConnection(this);
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName(caller, TelecomManager.PRESENTATION_ALLOWED);
        connection.startOutgoing(extras != null && extras.getBoolean(RelayListener.EXTRA_FULL_MODE));
        RelayConnection.current = connection;
        Log.i(RelayListener.TAG, "outgoing mirror created");
        return connection;
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle account, ConnectionRequest request) {
        Log.w(RelayListener.TAG, "Telecom could not create the outgoing mirror; starting the call on the phone only");
        Runnable startApp = RelayConnection.pendingAppStart;
        RelayConnection.pendingAppStart = null;
        if (startApp != null) {
            startApp.run();
        }
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle account, ConnectionRequest request) {
        Log.w(RelayListener.TAG, "Telecom could not create the mirrored call");
    }
}
