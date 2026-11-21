package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** What the report says about the phone's audio during a call: states and kinds only. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AudioLinkTest {
    @Test
    public void namesTheVoiceLinkStates() {
        assertEquals("sco=connected", AudioLink.sco(AudioManager.SCO_AUDIO_STATE_CONNECTED));
        assertEquals("sco=connecting", AudioLink.sco(AudioManager.SCO_AUDIO_STATE_CONNECTING));
        assertEquals("sco=disconnected", AudioLink.sco(AudioManager.SCO_AUDIO_STATE_DISCONNECTED));
        assertEquals("sco=error", AudioLink.sco(AudioManager.SCO_AUDIO_STATE_ERROR));
    }

    @Test
    public void namesTheAudioModes() {
        assertEquals("mode=normal", AudioLink.mode(AudioManager.MODE_NORMAL));
        assertEquals("mode=in_call", AudioLink.mode(AudioManager.MODE_IN_CALL));
        assertEquals("mode=in_communication", AudioLink.mode(AudioManager.MODE_IN_COMMUNICATION));
        assertEquals("mode=99", AudioLink.mode(99));
    }

    @Test
    public void namesTheKindOfDeviceNeverTheDevice() {
        assertEquals("device=earpiece", AudioLink.device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE));
        assertEquals("device=speaker", AudioLink.device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertEquals("device=bluetooth_sco", AudioLink.device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO));
        assertEquals("device=ble_headset", AudioLink.device(AudioDeviceInfo.TYPE_BLE_HEADSET));
        assertEquals("device=type 12", AudioLink.device(12));
        assertEquals("device=none", AudioLink.device(-1));
    }

    @Test
    public void logsTheVoiceLinkChangingOnceEach() {
        Context context = RuntimeEnvironment.getApplication();
        List<String> logged = new ArrayList<>();
        AudioLink.Watcher watcher = new AudioLink.Watcher(context, new Handler(Looper.getMainLooper()), logged::add);
        watcher.start();
        logged.clear();

        Intent connected = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                .putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTED);
        context.sendBroadcast(connected);
        context.sendBroadcast(connected);
        ShadowLooper.idleMainLooper();

        assertEquals(List.of("sco=connected"), logged);

        watcher.stop();
        context.sendBroadcast(new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
                .putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED));
        ShadowLooper.idleMainLooper();
        assertFalse(logged.contains("sco=disconnected"));
    }

    @Test
    public void logsTheModeWhenItStarts() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSystemService(AudioManager.class).setMode(AudioManager.MODE_IN_COMMUNICATION);
        List<String> logged = new ArrayList<>();
        AudioLink.Watcher watcher = new AudioLink.Watcher(context, new Handler(Looper.getMainLooper()), logged::add);

        watcher.start();

        assertTrue(logged.toString(), logged.contains("mode=in_communication"));
        watcher.stop();
    }

    @Test
    public void namesTheKindOfMicrophoneNeverTheDevice() {
        assertEquals("mic=phone", AudioLink.mic(AudioDeviceInfo.TYPE_BUILTIN_MIC));
        assertEquals("mic=bluetooth_sco", AudioLink.mic(AudioDeviceInfo.TYPE_BLUETOOTH_SCO));
        assertEquals("mic=ble_headset", AudioLink.mic(AudioDeviceInfo.TYPE_BLE_HEADSET));
        assertEquals("mic=type 12", AudioLink.mic(12));
        assertEquals("mic=unknown", AudioLink.mic(-1));
    }

    @Test
    public void writesVolumesAsLevelOverMaximum() {
        assertEquals("volume call=3/7 bluetooth=0/15", AudioLink.volume(3, 7, 0, 15));
    }

    @Test
    public void logsVolumesAndTheDevicesOfferedWhenItStarts() {
        Context context = RuntimeEnvironment.getApplication();
        List<String> logged = new ArrayList<>();
        AudioLink.Watcher watcher = new AudioLink.Watcher(context, new Handler(Looper.getMainLooper()), logged::add);

        watcher.start();

        assertTrue(logged.toString(), logged.stream().anyMatch(l -> l.startsWith("volume call=")));
        assertTrue(logged.toString(), logged.stream().anyMatch(l -> l.startsWith("offered=")));
        watcher.stop();
    }

    @Test
    public void saysWhereVoicesPlayNeverWhose() {
        assertEquals("players voice=2 on=bluetooth_sco,earpiece",
                AudioLink.players(2, List.of("earpiece", "bluetooth_sco", "earpiece")));
        assertEquals("players voice=0", AudioLink.players(0, List.of()));
    }

    @Test
    public void saysWhetherThePhoneIsLockedAndLit() {
        assertEquals("screen=locked,off", AudioLink.screen(true, false));
        assertEquals("screen=unlocked,on", AudioLink.screen(false, true));
    }

    @Test
    public void listsDeviceKindsOnceEachInOrder() {
        assertEquals("outputs=bluetooth_sco,earpiece", AudioLink.kinds("outputs", new int[] {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE}));
        assertEquals("inputs=none", AudioLink.kinds("inputs", new int[0]));
        assertEquals("inputs=phone_mic", AudioLink.kinds("inputs", new int[] {AudioDeviceInfo.TYPE_BUILTIN_MIC}));
    }

    @Test
    public void logsTheScreenAndTheDevicesWhenItStarts() {
        Context context = RuntimeEnvironment.getApplication();
        List<String> logged = new ArrayList<>();
        AudioLink.Watcher watcher = new AudioLink.Watcher(context, new Handler(Looper.getMainLooper()), logged::add);

        watcher.start();

        for (String prefix : new String[] {"screen=", "outputs=", "inputs=", "players voice=", "sco_on="}) {
            assertTrue(prefix + " missing in " + logged, logged.stream().anyMatch(l -> l.startsWith(prefix)));
        }
        watcher.stop();
    }

    @Test
    public void tellsWhenABluetoothVoiceDeviceAppears() {
        Context context = RuntimeEnvironment.getApplication();
        android.media.AudioManager audio = context.getSystemService(android.media.AudioManager.class);
        List<String> logged = new ArrayList<>();
        List<String> told = new ArrayList<>();
        AudioLink.Watcher watcher = new AudioLink.Watcher(context, new Handler(Looper.getMainLooper()),
                logged::add, () -> told.add("sco appeared"));
        watcher.start();
        told.clear();

        shadowOf(audio).addOutputDevice(org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build(), true);
        ShadowLooper.idleMainLooper();

        assertEquals("the watch's voice link appeared: the take is worth another try", List.of("sco appeared"), told);
        watcher.stop();
    }

    @Test
    public void staysQuietWhenOtherDevicesAppear() {
        Context context = RuntimeEnvironment.getApplication();
        android.media.AudioManager audio = context.getSystemService(android.media.AudioManager.class);
        List<String> logged = new ArrayList<>();
        List<String> told = new ArrayList<>();
        AudioLink.Watcher watcher = new AudioLink.Watcher(context, new Handler(Looper.getMainLooper()),
                logged::add, () -> told.add("sco appeared"));
        watcher.start();
        told.clear();

        shadowOf(audio).addOutputDevice(org.robolectric.shadows.AudioDeviceInfoBuilder.newBuilder()
                .setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build(), true);
        ShadowLooper.idleMainLooper();

        assertTrue(told.isEmpty());
        watcher.stop();
    }
}
