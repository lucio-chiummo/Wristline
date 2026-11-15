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

    /** {@code type} is the {@link AudioDeviceInfo} type a recording takes its sound from; negative when unknown. */
    static String mic(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "mic=phone";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "mic=bluetooth_sco";
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return "mic=ble_headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "mic=wired";
            default:
                return type < 0 ? "mic=unknown" : "mic=type " + type;
        }
    }

    /** Call volume on the phone and on a Bluetooth voice link: a voice nobody hears may just be at zero. */
    static String volume(int call, int callMax, int bluetooth, int bluetoothMax) {
        return "volume call=" + call + "/" + callMax + " bluetooth=" + bluetooth + "/" + bluetoothMax;
    }

    /** AudioManager.STREAM_BLUETOOTH_SCO: the volume of a call on a Bluetooth voice link. */
    private static final int STREAM_BLUETOOTH_SCO = 6;

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
        private final AudioManager.OnCommunicationDeviceChangedListener deviceListener = device -> {
            report("device", device(device == null ? -1 : device.getType()));
            reportSurroundings();
        };
        /** Which microphone each recording uses: the watch's, or the phone's. */
        private final AudioManager.AudioRecordingCallback recordingCallback = new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(java.util.List<android.media.AudioRecordingConfiguration> configs) {
                java.util.List<String> mics = new java.util.ArrayList<>();
                for (android.media.AudioRecordingConfiguration config : configs) {
                    AudioDeviceInfo device = config.getAudioDevice();
                    mics.add(mic(device == null ? -1 : device.getType()));
                }
                report("mic", mics.isEmpty() ? "mic=none" : String.join(",", mics));
            }
        };

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
            last.clear();
            Executor executor = handler::post;
            AudioManager audio = context.getSystemService(AudioManager.class);
            try {
                report("mode", mode(audio.getMode()));
                AudioDeviceInfo device = audio.getCommunicationDevice();
                report("device", device(device == null ? -1 : device.getType()));
                reportSurroundings();
                audio.registerAudioRecordingCallback(recordingCallback, handler);
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
                audio.unregisterAudioRecordingCallback(recordingCallback);
                audio.removeOnModeChangedListener(modeListener);
                audio.removeOnCommunicationDeviceChangedListener(deviceListener);
                context.unregisterReceiver(scoReceiver);
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "could not stop watching the audio link", e);
            }
        }

        /** The devices apps may put a call on, and how loud a call is: kinds and levels only. */
        private void reportSurroundings() {
            try {
                AudioManager audio = context.getSystemService(AudioManager.class);
                java.util.Set<String> offered = new java.util.TreeSet<>();
                for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
                    offered.add(device(device.getType()).substring("device=".length()));
                }
                report("offered", "offered=" + String.join(",", offered));
                report("volume", volume(audio.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
                        audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                        audio.getStreamVolume(STREAM_BLUETOOTH_SCO), audio.getStreamMaxVolume(STREAM_BLUETOOTH_SCO)));
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "could not read the audio surroundings", e);
            }
        }

        private void report(String key, String detail) {
            if (started && !detail.equals(last.put(key, detail))) {
                sink.log(detail);
            }
        }
    }
}
