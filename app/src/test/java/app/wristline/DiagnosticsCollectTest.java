package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;

/** Facts the report reads from the phone itself. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class DiagnosticsCollectTest {
    private AudioManager audio() {
        return RuntimeEnvironment.getApplication().getSystemService(AudioManager.class);
    }

    @Test
    public void bluetoothCallAudioSeesSco() {
        AudioManager am = audio();
        shadowOf(am).addOutputDevice(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build(), false);
        assertEquals(Boolean.TRUE, Diagnostics.bluetoothCallAudio(am));
    }

    @Test
    public void bluetoothCallAudioSeesBleHeadset() {
        AudioManager am = audio();
        shadowOf(am).addOutputDevice(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLE_HEADSET).build(), false);
        assertEquals(Boolean.TRUE, Diagnostics.bluetoothCallAudio(am));
    }

    @Test
    public void bluetoothCallAudioFalseWithoutHeadset() {
        AudioManager am = audio();
        shadowOf(am).addOutputDevice(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build(), false);
        assertEquals(Boolean.FALSE, Diagnostics.bluetoothCallAudio(am));
    }
}
