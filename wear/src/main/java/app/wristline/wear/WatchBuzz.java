package app.wristline.wear;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Vibrates the watch like a ringing call, on the phone's say-so. The watch's notification bridge
 * alerts only once for a notification that is re-posted, so the app buzzes for the whole ring itself.
 */
final class WatchBuzz {
    /** A ring never vibrates longer than this, whatever the phone asks. */
    static final long MAX_MS = 60_000;
    /** One cycle: a double buzz, then a pause. The vibrator is on for the even slots, off for the odd. */
    static final long[] CYCLE_MS = {700, 500, 700, 1600};

    private WatchBuzz() {
    }

    /** Replaces any ring in progress. Does nothing without a vibrator. */
    static void start(Context context, long maxMs) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        vibrator.cancel();
        // One waveform that doesn't repeat: if the phone's "stop" never arrives, it ends by itself.
        VibrationEffect effect = VibrationEffect.createWaveform(timings(maxMs), -1);
        if (Build.VERSION.SDK_INT >= 33) {
            // Ringtone usage: silent mode and Do Not Disturb are respected.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE));
        } else {
            vibrator.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
        }
    }

    static void stop(Context context) {
        Vibrator vibrator = context.getSystemService(Vibrator.class);
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    /** Off (0, so it starts at once), on, off, on, … covering {@code min(maxMs, MAX_MS)}. */
    static long[] timings(long maxMs) {
        long left = Math.max(1, Math.min(maxMs, MAX_MS));
        java.util.List<Long> out = new java.util.ArrayList<>();
        out.add(0L);
        for (int i = 0; left > 0; i++) {
            long ms = Math.min(CYCLE_MS[i % CYCLE_MS.length], left);
            out.add(ms);
            left -= ms;
        }
        long[] timings = new long[out.size()];
        for (int i = 0; i < timings.length; i++) {
            timings[i] = out.get(i);
        }
        return timings;
    }
}
