package app.wristline.wear;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
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
 * Wristline's own call screen for calls started from the watch (the watch's system call screen
 * can't be opened by other apps). Shows who and which app, "Calling…", "Ringing…" until the other
 * side answers, then the call time; one tap on the red button hangs up. Swipe-to-dismiss is off
 * (it hid the screen without hanging up): "Minimise" leaves it, opening Wristline brings it back.
 * The crown changes the call volume. Closes when the phone says the call ended.
 */
public class InCallActivity extends Activity {
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_APP = "app";
    /** Crown rotation (in AXIS_SCROLL units) per volume step. */
    private static final float CROWN_STEP = 1f;

    /** The call screen while a call is on, shown or minimised, for {@link CallStateService}. */
    static volatile InCallActivity current;

    private TextView status;
    private Chronometer timer;
    private ImageButton mute;
    private ObjectAnimator pulse;
    private float crown;

    static Intent intent(Context context, String name, String app) {
        return new Intent(context, InCallActivity.class)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_APP, app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
        ((TextView) findViewById(R.id.name)).setText(name);
        ((TextView) findViewById(R.id.app)).setText(Protocol.label(app));
        Ui.avatar(findViewById(R.id.avatar), name);
        CallIndicator.show(this, name, app);
        ColorStateList appColor = ColorStateList.valueOf(Ui.appColor(this, app));
        findViewById(R.id.app_dot).setBackgroundTintList(appColor);
        View ring = findViewById(R.id.ring);
        ring.setBackgroundTintList(appColor);
        pulse = pulse(ring);
        status = findViewById(R.id.status);
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

    /** The ring breathes out and fades while the call is connecting. */
    private static ObjectAnimator pulse(View ring) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.82f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.82f, 1f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.15f));
        animator.setDuration(1100);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();
        return animator;
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
            if (Protocol.STATE_ENDED.equals(state.kind)) {
                finish();
                return;
            }
            if (Protocol.STATE_RINGING.equals(state.kind) && timer.getVisibility() != View.VISIBLE) {
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
