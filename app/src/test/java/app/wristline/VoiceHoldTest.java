package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;

/** Android 17: the voice only stays on the watch while Wristline itself asks for it there. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class VoiceHoldTest {
    private Context context;
    private AudioManager audio;
    private final List<String> logged = new ArrayList<>();

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        audio = context.getSystemService(AudioManager.class);
    }

    private void offer(int... types) {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (int type : types) {
            devices.add(AudioDeviceInfoBuilder.newBuilder().setType(type).build());
        }
        shadowOf(audio).setAvailableCommunicationDevices(devices);
    }

    @Test
    public void onlyNeededFromAndroid17() {
        assertFalse("Android 16 keeps the voice on the watch by itself", VoiceHold.needed(36));
        assertTrue(VoiceHold.needed(37));
    }

    @Test
    public void takingPutsTheCallAudioOnTheWatch() {
        offer(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        VoiceHold hold = new VoiceHold(context, logged::add);

        assertTrue(hold.take());

        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audio.getMode());
        assertEquals(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, audio.getCommunicationDevice().getType());
        assertEquals(List.of("hold=taken"), logged);
        hold.release();
    }

    @Test
    public void takingAgainSaysSoOnce() {
        offer(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        VoiceHold hold = new VoiceHold(context, logged::add);

        hold.take();
        hold.take();

        assertEquals(List.of("hold=taken", "hold=retaken"), logged);
        hold.release();
    }

    @Test
    public void withNoWatchOfferedItLeavesTheAudioAlone() {
        offer(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        VoiceHold hold = new VoiceHold(context, logged::add);

        assertFalse(hold.take());

        assertEquals(AudioManager.MODE_NORMAL, audio.getMode());
        assertEquals(List.of("hold=no watch offered"), logged);
    }

    @Test
    public void releasingGivesTheAudioBack() {
        offer(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        VoiceHold hold = new VoiceHold(context, logged::add);
        hold.take();

        hold.release();

        assertEquals(AudioManager.MODE_NORMAL, audio.getMode());
        assertNull(audio.getCommunicationDevice());
        assertEquals("hold=released", logged.get(logged.size() - 1));
    }

    @Test
    public void releasingWhatWasNeverTakenTouchesNothing() {
        audio.setMode(AudioManager.MODE_RINGTONE);

        new VoiceHold(context, logged::add).release();

        assertEquals(AudioManager.MODE_RINGTONE, audio.getMode());
        assertTrue(logged.isEmpty());
    }

    @Test
    public void aMomentAfterTakingItSaysWhetherItHeld() {
        offer(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        VoiceHold hold = new VoiceHold(context, logged::add);
        hold.take();

        org.robolectric.shadows.ShadowLooper.idleMainLooper(VoiceHold.CHECK_MS, java.util.concurrent.TimeUnit.MILLISECONDS);

        String check = logged.get(logged.size() - 1);
        assertTrue(check, check.startsWith("hold check mode=in_communication device=bluetooth_sco stream="));
        hold.release();
    }

    @Test
    public void noCheckAfterLettingGo() {
        offer(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        VoiceHold hold = new VoiceHold(context, logged::add);
        hold.take();
        hold.release();

        org.robolectric.shadows.ShadowLooper.idleMainLooper(VoiceHold.CHECK_MS, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertEquals("hold=released", logged.get(logged.size() - 1));
    }
}
