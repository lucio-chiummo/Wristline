package app.wristline.wear;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;

/**
 * Wristline's incoming call screen while the phone rings by notification: who and which app, a red
 * Decline and a green Answer, over the lock screen. Answer and Decline do what the phone
 * notification's buttons do; after Answer the call goes on in {@link InCallActivity}. Opened by
 * {@link IncomingRing}'s full-screen intent, closed when the ring stops. On a Pixel Watch the
 * double pinch answers and the wrist turn declines; Back declines too, rather than hiding the ring.
 */
public class IncomingActivity extends Activity {
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_APP = "app";
    /** Set by the notification's buttons: {@link #ACTION_ANSWER} or {@link #ACTION_DECLINE}. */
    static final String EXTRA_ACTION = "action";
    static final String ACTION_ANSWER = "answer";
    static final String ACTION_DECLINE = "decline";

    /** The incoming screen while it's open, for {@link IncomingRing#stop}. */
    static volatile IncomingActivity current;

    private ObjectAnimator pulse;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::finish;
    /** Answered or declined: further presses (screen or notification) do nothing. */
    private boolean done;
    /** The watch's gestures while the screen is in front: {@link WatchGestures}. */
    private Object gestures;

    static Intent intent(Context context, Protocol.Ring ring) {
        return new Intent(context, IncomingActivity.class)
                .putExtra(EXTRA_NAME, ring.name)
                .putExtra(EXTRA_APP, ring.app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        setContentView(R.layout.activity_incoming);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        findViewById(R.id.status_separator).setVisibility(View.GONE);
        findViewById(R.id.status).setVisibility(View.GONE);
        findViewById(R.id.answer).setOnClickListener(v -> answer());
        findViewById(R.id.decline).setOnClickListener(v -> decline());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::decline);
        }
        current = this;
        show(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        gestures = WatchGestures.listen(this, this::answer, this::decline);
    }

    @Override
    protected void onPause() {
        WatchGestures.stop(this, gestures);
        gestures = null;
        super.onPause();
    }

    /** Back before Android 13. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        decline();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent);
    }

    /**
     * Shows the caller, or closes when the ring is already over (the phone's stop beat the screen,
     * or it was lost): the screen never outlives the ring. Then the notification's Answer or
     * Decline, if one was pressed.
     */
    private void show(Intent intent) {
        long left = IncomingRing.remainingMs();
        if (left <= 0) {
            finish();
            return;
        }
        if (pulse != null) {
            pulse.cancel();
        }
        pulse = Ui.callHeader(this, name(), app());
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, left);
        String action = intent.getStringExtra(EXTRA_ACTION);
        if (ACTION_ANSWER.equals(action)) {
            answer();
        } else if (ACTION_DECLINE.equals(action)) {
            decline();
        }
    }

    private String name() {
        return getIntent().getStringExtra(EXTRA_NAME);
    }

    private String app() {
        return getIntent().getStringExtra(EXTRA_APP);
    }

    private void answer() {
        if (done) {
            return;
        }
        done = true;
        new Thread(() -> PhoneLink.answer(getApplicationContext())).start();
        ringOver();
        // The phone then sends the call's state, which the call screen shows.
        startActivity(InCallActivity.answered(this, name(), app()));
        finish();
    }

    private void decline() {
        if (done) {
            return;
        }
        done = true;
        new Thread(() -> PhoneLink.decline(getApplicationContext())).start();
        ringOver();
        finish();
    }

    private void ringOver() {
        WatchBuzz.stop(this);
        IncomingRing.stop(this);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timeout);
        if (pulse != null) {
            pulse.cancel();
        }
        if (current == this) {
            current = null;
        }
        super.onDestroy();
    }
}
