package app.wristline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * For the report: what the phone's audio does while a call's voice should be on the watch. The
 * Bluetooth voice link going up and down, the audio mode, and the kind of device calls play on.
 * States and kinds only: never a device's name or address. Changes nothing.
 */
final class AudioLink {
    private AudioLink() {
    }

    static String sco(int state) {
        switch (state) {
            case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                return "sco=connected";
            case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                return "sco=connecting";
            case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                return "sco=disconnected";
            default:
                return "sco=error";
        }
    }

    static String mode(int mode) {
        switch (mode) {
            case AudioManager.MODE_NORMAL:
                return "mode=normal";
            case AudioManager.MODE_RINGTONE:
                return "mode=ringtone";
            case AudioManager.MODE_IN_CALL:
                return "mode=in_call";
            case AudioManager.MODE_IN_COMMUNICATION:
                return "mode=in_communication";
            default:
                return "mode=" + mode;
        }
    }

    /** {@code type} is an {@link AudioDeviceInfo} type; negative for no device. */
    static String device(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "device=earpiece";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "device=speaker";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "device=bluetooth_sco";
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return "device=ble_headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "device=wired";
            default:
                return type < 0 ? "device=none" : "device=type " + type;
        }
    }

    interface Sink {
        void log(String detail);
    }

    /** Reports each change once, from {@link #start} to {@link #stop}. */
    static final class Watcher {
        private final Context context;
        private final Handler handler;
        private final Sink sink;
        private final Map<String, String> last = new HashMap<>();
        private boolean started;

        private final BroadcastReceiver scoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                report("sco", sco(intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_ERROR)));
            }
        };
        private final AudioManager.OnModeChangedListener modeListener = mode -> report("mode", mode(mode));
        private final AudioManager.OnCommunicationDeviceChangedListener deviceListener =
                device -> report("device", device(device == null ? -1 : device.getType()));

        Watcher(Context context, Handler handler, Sink sink) {
            this.context = context.getApplicationContext();
            this.handler = handler;
            this.sink = sink;
        }

        void start() {
            if (started) {
                return;
            }
            started = true;
            Executor executor = handler::post;
            AudioManager audio = context.getSystemService(AudioManager.class);
            try {
                report("mode", mode(audio.getMode()));
                AudioDeviceInfo device = audio.getCommunicationDevice();
                report("device", device(device == null ? -1 : device.getType()));
                audio.addOnModeChangedListener(executor, modeListener);
                audio.addOnCommunicationDeviceChangedListener(executor, deviceListener);
                context.registerReceiver(scoReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
                        null, handler, Context.RECEIVER_EXPORTED);
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "could not watch the audio link", e);
            }
        }

        void stop() {
            if (!started) {
                return;
            }
            started = false;
            AudioManager audio = context.getSystemService(AudioManager.class);
            try {
                audio.removeOnModeChangedListener(modeListener);
                audio.removeOnCommunicationDeviceChangedListener(deviceListener);
                context.unregisterReceiver(scoReceiver);
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "could not stop watching the audio link", e);
            }
        }

        private void report(String key, String detail) {
            if (started && !detail.equals(last.put(key, detail))) {
                sink.log(detail);
            }
        }
    }
}
