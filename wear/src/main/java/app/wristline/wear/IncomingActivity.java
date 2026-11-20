package app.wristline.wear;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

/**
 * Wristline's incoming call screen while the phone rings by notification: who and which app, a red
 * Decline and a green Answer, over the lock screen. Answer and Decline do what the phone
 * notification's buttons do; after Answer the call goes on in {@link InCallActivity}. Opened by
 * {@link IncomingRing}'s full-screen intent, closed when the ring stops.
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
        pulse = Ui.callHeader(this, name(), app());
        findViewById(R.id.status_separator).setVisibility(View.GONE);
        findViewById(R.id.status).setVisibility(View.GONE);
        findViewById(R.id.answer).setOnClickListener(v -> answer());
        findViewById(R.id.decline).setOnClickListener(v -> decline());
        current = this;
        onAction(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        onAction(intent);
    }

    /** The notification's Answer or Decline button. */
    private void onAction(Intent intent) {
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
        new Thread(() -> PhoneLink.answer(getApplicationContext())).start();
        ringOver();
        // The phone then sends the call's state, which the call screen shows.
        startActivity(InCallActivity.answered(this, name(), app()));
        finish();
    }

    private void decline() {
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
        pulse.cancel();
        if (current == this) {
            current = null;
        }
        super.onDestroy();
    }
}
