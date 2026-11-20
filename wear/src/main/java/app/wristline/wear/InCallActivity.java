package app.wristline.wear;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * Wristline's own call screen for calls started from the watch, and for calls answered on
 * {@link IncomingActivity} (the watch's system call screen can't be opened by other apps). Shows
 * who and which app, "Calling…", "Ringing…" until the other side answers, then the call time;
 * one tap on the red button hangs up. Swipe-to-dismiss is off (it hid the screen without hanging
 * up): "Minimise" leaves it, opening Wristline brings it back. The crown changes the call volume. Closes when the phone says the call ended.
 */
public class InCallActivity extends Activity {
    static final String EXTRA_NAME = "name";
    static final String EXTRA_APP = "app";
    /** Set for a call answered on the watch: "Connecting…" instead of "Calling…" and "Ringing…". */
    private static final String EXTRA_ANSWERED = "answered";
    /** Crown rotation (in AXIS_SCROLL units) per volume step. */
    private static final float CROWN_STEP = 1f;

    /** The call screen while a call is on, shown or minimised, for {@link CallStateService}. */
    static volatile InCallActivity current;

    private TextView status;
    private Chronometer timer;
    private ImageButton mute;
    private ObjectAnimator pulse;
    private float crown;
    /** Answered on the watch: stays "Connecting…" until the call is active. */
    private boolean answered;

    static Intent intent(Context context, String name, String app) {
        return new Intent(context, InCallActivity.class)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_APP, app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    /** The call screen for a call just answered from {@link IncomingActivity}. */
    static Intent answered(Context context, String name, String app) {
        return intent(context, name, app).putExtra(EXTRA_ANSWERED, true);
    }

    /** Brings back a minimised call screen; false when there's no call. */
    static boolean resume(Context context) {
        InCallActivity screen = current;
        if (screen == null || screen.isFinishing()) {
            return false;
        }
        context.startActivity(new Intent(context, InCallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_call);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String app = getIntent().getStringExtra(EXTRA_APP);
        pulse = Ui.callHeader(this, name, app);
        CallIndicator.show(this, name, app);
        status = findViewById(R.id.status);
        answered = getIntent().getBooleanExtra(EXTRA_ANSWERED, false);
        if (answered) {
            status.setText(R.string.connecting);
            // Answered over a locked watch: the call follows the incoming screen over the lock screen.
            setShowWhenLocked(true);
        }
        timer = findViewById(R.id.timer);
        mute = findViewById(R.id.mute);
        findViewById(R.id.hang_up).setOnClickListener(v -> {
            new Thread(() -> PhoneLink.hangUp(getApplicationContext())).start();
            finish();
        });
        mute.setOnClickListener(v -> new Thread(() -> PhoneLink.toggleMute(getApplicationContext())).start());
        findViewById(R.id.minimise).setOnClickListener(v -> minimise());
        current = this;
    }

    /**
     * To the watch face; the call and this screen carry on. Not moveTaskToBack: that reveals
     * MainActivity, whose onResume brings a running call straight back.
     */
    private void minimise() {
        startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Connected: the ring stops pulsing and stays, quieter. */
    private void stopPulse() {
        if (pulse.isRunning()) {
            pulse.cancel();
            View ring = findViewById(R.id.ring);
            ring.setScaleX(1f);
            ring.setScaleY(1f);
            ring.setAlpha(0.35f);
        }
    }

    @Override
    protected void onDestroy() {
        pulse.cancel();
        CallIndicator.hide(this);
        if (current == this) {
            current = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL
                && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            // Turning the crown clockwise gives a negative AXIS_SCROLL.
            crown += -event.getAxisValue(MotionEvent.AXIS_SCROLL);
            while (Math.abs(crown) >= CROWN_STEP) {
                boolean up = crown > 0;
                crown -= up ? CROWN_STEP : -CROWN_STEP;
                getSystemService(AudioManager.class).adjustStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    void onState(Protocol.CallState state) {
        runOnUiThread(() -> {
            if (Protocol.STATE_BUSY.equals(state.kind)) {
                android.widget.Toast.makeText(this, R.string.already_on_call, android.widget.Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (Protocol.STATE_ENDED.equals(state.kind)) {
                finish();
                return;
            }
            // An answered call's "ringing" is the app still connecting, not the other side ringing.
            if (Protocol.STATE_RINGING.equals(state.kind) && timer.getVisibility() != View.VISIBLE && !answered) {
                status.setText(R.string.ringing);
            } else if (Protocol.STATE_ACTIVE.equals(state.kind) && timer.getVisibility() != View.VISIBLE) {
                status.setVisibility(View.GONE);
                timer.setVisibility(View.VISIBLE);
                stopPulse();
                timer.setBase(SystemClock.elapsedRealtime() - Math.max(0, state.connectedMs));
                timer.start();
            }
            mute.setVisibility(state.canMute ? View.VISIBLE : View.GONE);
            findViewById(R.id.mute_balance).setVisibility(state.canMute ? View.INVISIBLE : View.GONE);
            mute.setImageResource(state.muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
            mute.setContentDescription(getString(state.muted ? R.string.unmute : R.string.mute));
            mute.setActivated(state.muted);
        });
    }
}
