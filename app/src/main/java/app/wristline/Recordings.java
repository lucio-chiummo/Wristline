package app.wristline;

import android.media.MediaRecorder;

import java.util.List;

/**
 * The phone's active recordings as the diagnostic report writes them: what kind of microphone
 * input and whether Android is muting it. Never which app, device or session.
 */
final class Recordings {
    private Recordings() {
    }

    static String source(int audioSource) {
        switch (audioSource) {
            case MediaRecorder.AudioSource.MIC:
                return "mic";
            case MediaRecorder.AudioSource.VOICE_RECOGNITION:
                return "voice_recognition";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return "voice_communication";
            case MediaRecorder.AudioSource.UNPROCESSED:
                return "unprocessed";
            case MediaRecorder.AudioSource.VOICE_PERFORMANCE:
                return "voice_performance";
            default:
                return "source" + audioSource;
        }
    }

    /** "source=voice_communication silenced=true". */
    static String describe(int audioSource, boolean silenced) {
        return "source=" + source(audioSource) + " silenced=" + silenced;
    }

    /** All of them on one line; "none" when nothing is recording. */
    static String summary(List<String> described) {
        return described.isEmpty() ? "none" : String.join("; ", described);
    }
}
