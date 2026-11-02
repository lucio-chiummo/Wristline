package app.wristline.wear;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

/**
 * No screen of its own: the tile's Pause/Resume chip opens it to pause or resume relaying on the
 * phone. The phone then publishes the new state, which redraws the tile.
 */
public class ToggleRelayActivity extends Activity {
    /** What to switch relaying to (the tile knows what it showed). */
    static final String EXTRA_ON = "on";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!LaunchToken.matches(this, getIntent())) {
            finish();
            return;
        }
        boolean on = getIntent().getBooleanExtra(EXTRA_ON, true);
        Context app = getApplicationContext();
        new Thread(() -> {
            boolean sent = PhoneLink.setRelay(app, on);
            runOnUiThread(() -> {
                Toast.makeText(app, !sent ? R.string.no_phone : on ? R.string.relay_resumed : R.string.relay_paused,
                        Toast.LENGTH_SHORT).show();
                finish();
            });
        }, "toggle-relay").start();
    }
}
