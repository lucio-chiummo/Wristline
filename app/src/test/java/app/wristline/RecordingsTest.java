package app.wristline;

import static org.junit.Assert.assertEquals;

import android.media.MediaRecorder;

import org.junit.Test;

import java.util.List;

public class RecordingsTest {
    @Test
    public void describesSourceAndSilence() {
        assertEquals("source=voice_communication silenced=true",
                Recordings.describe(MediaRecorder.AudioSource.VOICE_COMMUNICATION, true));
        assertEquals("source=mic silenced=false", Recordings.describe(MediaRecorder.AudioSource.MIC, false));
        assertEquals("source=source1999 silenced=false", Recordings.describe(1999, false));
    }

    @Test
    public void namesTheCallSources() {
        assertEquals("voice_recognition", Recordings.source(MediaRecorder.AudioSource.VOICE_RECOGNITION));
        assertEquals("unprocessed", Recordings.source(MediaRecorder.AudioSource.UNPROCESSED));
        assertEquals("voice_performance", Recordings.source(MediaRecorder.AudioSource.VOICE_PERFORMANCE));
    }

    @Test
    public void summaryJoinsOrSaysNone() {
        assertEquals("none", Recordings.summary(List.of()));
        assertEquals("source=mic silenced=false; source=voice_communication silenced=true",
                Recordings.summary(List.of("source=mic silenced=false", "source=voice_communication silenced=true")));
    }
}
