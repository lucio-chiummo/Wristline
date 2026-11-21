package app.wristline;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * Keeps the voice of a call on the watch on Android 17 and later.
 *
 * Up to Android 16 the Bluetooth voice link Telecom opens for Wristline's call wins over whatever
 * the calling app asks for, so the app's voice plays on the watch. From Android 17 the audio
 * framework owns that link: it follows the device asked for by the app that owns the audio mode
 * (the most recent one in communication mode that is playing or recording a voice), and it only
 * stays up while a stream plays on it. The calling app owns the mode, asks for nothing, and the
 * link Telecom opened is dropped within a second.
 *
 * So Wristline becomes that owner for the length of its call: it enters communication mode, asks
 * for the watch, and keeps a silent voice stream playing so Android counts it as active. The
 * calling app's voice follows, as there is one call device for the whole phone.
 */
final class VoiceHold {
    /** Overridden in tests. */
    static int sdk = Build.VERSION.SDK_INT;

    /** How long after taking the voice the report says whether it held. */
    static final long CHECK_MS = 1500;
    /** How often the hold is watched for a loss Telecom never reports (the HyperOS earpiece switch). */
    static final long WATCH_CHECK_MS = 500;

    private static final int SAMPLE_RATE = 8000;

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable check = this::logCheck;
    private final Runnable poll = this::checkWatch;

    private final Context context;
    private final AudioLink.Sink log;
    /** Told once when the voice leaves the watch while held; null in tests that only watch the log. */
    private final Runnable onLost;
    private AudioTrack silence;
    private boolean held;

    VoiceHold(Context context, AudioLink.Sink log) {
        this(context, log, null);
    }

    VoiceHold(Context context, AudioLink.Sink log, Runnable onLost) {
        this.context = context.getApplicationContext();
        this.log = log;
        this.onLost = onLost;
    }

    /**
     * Whether the watch still carries the call voice: TRUE on a watch device, FALSE on anything
     * else (the calling app pulled it to the earpiece), null when no watch is offered at all (a
     * real drop, left to the route path). The communication device is what the POCO's hold check
     * already proved moves under HyperOS without Telecom reporting a route change.
     */
    static Boolean watchHasVoice(Context context) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        boolean watchOffered = false;
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                watchOffered = true;
                break;
            }
        }
        if (!watchOffered) {
            return null;
        }
        AudioDeviceInfo on = audio.getCommunicationDevice();
        int type = on == null ? -1 : on.getType();
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    static boolean needed(int sdk) {
        return sdk >= 37;
    }

    static boolean needed() {
        return needed(sdk);
    }

    /**
     * Takes the call audio for the watch, or takes it back when the calling app took it since.
     * False when Android offers no watch to put a call on.
     */
    boolean take() {
        AudioManager audio = context.getSystemService(AudioManager.class);
        try {
            AudioDeviceInfo watch = null;
            for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
                if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                    watch = device;
                    break;
                }
            }
            if (watch == null) {
                log.log("hold=no watch offered");
                return false;
            }
            playSilence();
            // Setting the mode again makes Wristline the most recent owner.
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            boolean accepted = audio.setCommunicationDevice(watch);
            if (!accepted) {
                // A take that didn't take: no hold to report, no stream to keep Android
                // counting Wristline as in a call.
                log.log("hold=refused");
                stopSilence();
                return false;
            }
            log.log(held ? "hold=retaken" : "hold=taken");
            held = true;
            handler.removeCallbacks(check);
            handler.postDelayed(check, CHECK_MS);
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, WATCH_CHECK_MS);
            return true;
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not hold the voice on the watch", e);
            log.log("hold=failed " + e.getClass().getSimpleName());
            return false;
        }
    }

    /** Gives the call audio back to the calling app. Does nothing when it was never taken. */
    void release() {
        if (!held) {
            return;
        }
        held = false;
        handler.removeCallbacks(check);
        handler.removeCallbacks(poll);
        AudioManager audio = context.getSystemService(AudioManager.class);
        try {
            audio.clearCommunicationDevice();
            audio.setMode(AudioManager.MODE_NORMAL);
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not let go of the call audio", e);
        }
        stopSilence();
        log.log("hold=released");
    }

    /**
     * The voice left the watch while Wristline held it, with no Telecom route change to show for
     * it: stop watching and tell the call once. The hold is left taken so the caller's release()
     * does the letting go; polling stops here, so the callback cannot fire twice.
     */
    private void checkWatch() {
        if (!held) {
            return;
        }
        if (watchHasVoice(context) == Boolean.FALSE) {
            handler.removeCallbacks(poll);
            AudioDeviceInfo on = context.getSystemService(AudioManager.class).getCommunicationDevice();
            log.log("hold=lost " + AudioLink.device(on == null ? -1 : on.getType()));
            if (onLost != null) {
                onLost.run();
            }
            return;
        }
        handler.postDelayed(poll, WATCH_CHECK_MS);
    }

    /** For the report: what the phone's call audio looks like once the hold has had time to apply. */
    private void logCheck() {
        if (!held) {
            return;
        }
        try {
            AudioManager audio = context.getSystemService(AudioManager.class);
            AudioDeviceInfo device = audio.getCommunicationDevice();
            AudioTrack track = silence;
            AudioDeviceInfo routed = track == null ? null : track.getRoutedDevice();
            log.log("hold check " + AudioLink.mode(audio.getMode())
                    + " " + AudioLink.device(device == null ? -1 : device.getType())
                    + " stream=" + (track == null ? "none"
                            : track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING ? "playing" : "stopped")
                    + " on=" + AudioLink.device(routed == null ? -1 : routed.getType()).substring("device=".length())
                    + " sco_on=" + audio.isBluetoothScoOn());
        } catch (RuntimeException e) {
            log.log("hold check failed " + e.getClass().getSimpleName());
        }
    }

    /** A looping second of silence as a voice stream: what makes Android count Wristline as in a call. */
    private void playSilence() {
        if (silence != null) {
            return;
        }
        try {
            byte[] zeros = new byte[SAMPLE_RATE * 2];
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(zeros.length)
                    .build();
            track.write(zeros, 0, zeros.length);
            track.setLoopPoints(0, SAMPLE_RATE, -1);
            track.play();
            silence = track;
        } catch (RuntimeException e) {
            Log.w(RelayListener.TAG, "could not play the silent voice stream", e);
            log.log("hold=no stream " + e.getClass().getSimpleName());
        }
    }

    private void stopSilence() {
        AudioTrack track = silence;
        silence = null;
        if (track != null) {
            try {
                track.stop();
                track.release();
            } catch (RuntimeException e) {
                Log.w(RelayListener.TAG, "could not stop the silent voice stream", e);
            }
        }
    }
}
