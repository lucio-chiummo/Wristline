package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowTelecomManager;

import java.util.concurrent.TimeUnit;

/** "Ring my watch": starting it, and how its stand-in call behaves on the watch's buttons. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class TestCallFlowTest {
    private Context context;
    private ShadowTelecomManager telecom;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        telecom = shadowOf(context.getSystemService(TelecomManager.class));
    }

    @After
    public void tearDown() {
        RelayListener.currentApp = null;
        RelayConnection.current = null;
    }

    private RelayConnection ringingTestCall() {
        RelayConnection connection = new RelayConnection(context);
        connection.setRinging();
        connection.markTestCall();
        RelayConnection.current = connection;
        return connection;
    }

    @Test
    public void startsATelecomCallMarkedAsATest() {
        assertTrue(RelayListener.startTestCall(context));

        ShadowTelecomManager.CallRecord call = telecom.getOnlyIncomingCall();
        assertTrue(call.extras.getBoolean(TestCall.EXTRA));
        assertEquals(context.getString(R.string.test_call_caller), call.extras.getString(RelayListener.EXTRA_CALLER));
    }

    @Test
    public void refusedWhileARealCallIsRelayed() {
        RelayListener.currentApp = SupportedApp.WHATSAPP;

        assertFalse(RelayListener.startTestCall(context));
        assertEquals(0, telecom.getAllIncomingCalls().size());
    }

    @Test
    public void declineEndsIt() {
        RelayConnection connection = ringingTestCall();

        connection.onReject();

        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertEquals(DisconnectCause.REJECTED, connection.getDisconnectCause().getCode());
        assertNull(RelayConnection.current);
        // Declining on the watch proves the call reached it.
        assertEquals(TestCall.Result.REACHED, Prefs.testCallResult(context));
    }

    @Test
    public void answerGoesActiveThenHangsUpByItself() {
        RelayConnection connection = ringingTestCall();

        connection.onAnswer();
        assertEquals(Connection.STATE_ACTIVE, connection.getState());
        assertEquals(TestCall.Result.REACHED, Prefs.testCallResult(context));

        ShadowLooper.idleMainLooper(TestCall.ANSWERED_MS, TimeUnit.MILLISECONDS);
        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertEquals(DisconnectCause.LOCAL, connection.getDisconnectCause().getCode());
    }

    @Test
    public void unansweredStopsRinging() {
        RelayConnection connection = ringingTestCall();

        ShadowLooper.idleMainLooper(TestCall.RING_MS, TimeUnit.MILLISECONDS);

        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertEquals(DisconnectCause.MISSED, connection.getDisconnectCause().getCode());
        assertEquals(TestCall.Result.MISSED, Prefs.testCallResult(context));
    }

    @Test
    public void homeIsToldWhenTheResultIsKnown() {
        int[] told = {0};
        TestCall.onResult = () -> told[0]++;
        try {
            ringingTestCall().onReject();
            ShadowLooper.idleMainLooper();
        } finally {
            TestCall.onResult = null;
        }
        assertEquals(1, told[0]);
    }

    @Test
    public void aRealCallReplacesARingingTest() {
        RelayConnection connection = ringingTestCall();

        RelayConnection.endTestCall();

        assertEquals(Connection.STATE_DISCONNECTED, connection.getState());
        assertNull(RelayConnection.current);
    }
}
