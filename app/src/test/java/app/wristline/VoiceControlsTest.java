package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Moving the voice of a call in progress between the watch and the phone. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class VoiceControlsTest {
    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
    }

    @After
    public void tearDown() {
        RelayConnection.current = null;
    }

    private RelayConnection activeCall() {
        RelayConnection connection = new RelayConnection(context);
        connection.setActive();
        RelayConnection.current = connection;
        VoiceControls.update(context);
        return connection;
    }

    private Notification controls() {
        return shadowOf(context.getSystemService(NotificationManager.class)).getNotification(StatusNotifications.ID_VOICE);
    }

    @Test
    public void callWithVoiceOnTheWatchOffersToMoveIt() {
        activeCall();

        assertTrue(RelayConnection.voiceOnWatch());
        Notification n = controls();
        assertNotNull(n);
        assertEquals(context.getString(R.string.move_voice_to_phone), n.actions[0].title.toString());
    }

    @Test
    public void movedToThePhoneAndBack() {
        activeCall();

        assertTrue(RelayConnection.moveVoice(true));
        assertFalse(RelayConnection.voiceOnWatch());
        assertEquals(context.getString(R.string.move_voice_to_watch), controls().actions[0].title.toString());

        assertTrue(RelayConnection.moveVoice(false));
        assertTrue(RelayConnection.voiceOnWatch());
    }

    @Test
    public void noCallNothingToMove() {
        assertFalse(RelayConnection.moveVoice(true));
        assertNull(RelayConnection.voiceOnWatch());
        assertNull(controls());
    }

    @Test
    public void controlsGoWhenTheCallEnds() {
        RelayConnection connection = activeCall();

        connection.onDisconnect();

        assertNull(controls());
    }
}
