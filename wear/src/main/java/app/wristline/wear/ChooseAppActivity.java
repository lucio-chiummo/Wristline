package app.wristline.wear;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** "Call with WhatsApp / Telegram / Signal" for someone reachable on more than one. */
public class ChooseAppActivity extends Activity {
    private static final String EXTRA_ID = "id";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_APPS = "apps";

    static Intent intent(Context context, WatchContact contact) {
        return new Intent(context, ChooseAppActivity.class)
                .putExtra(EXTRA_ID, contact.id)
                .putExtra(EXTRA_NAME, contact.name)
                .putStringArrayListExtra(EXTRA_APPS, new ArrayList<>(contact.apps));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);
        List<String> apps = getIntent().getStringArrayListExtra(EXTRA_APPS);
        WatchContact contact = new WatchContact(getIntent().getLongExtra(EXTRA_ID, -1),
                getIntent().getStringExtra(EXTRA_NAME), apps == null ? new ArrayList<>() : apps);
        ((TextView) findViewById(R.id.name)).setText(contact.name);
        Ui.avatar(findViewById(R.id.avatar), contact.name);
        bind(findViewById(R.id.whatsapp), contact, Protocol.WHATSAPP);
        bind(findViewById(R.id.telegram), contact, Protocol.TELEGRAM);
        bind(findViewById(R.id.signal), contact, Protocol.SIGNAL);
        if (contact.apps.contains(Protocol.WHATSAPP) && contact.apps.contains(Protocol.TELEGRAM)
                && contact.apps.contains(Protocol.SIGNAL)) {
            // Three side by side overflow a round screen: Signal goes to its own row below.
            View signal = findViewById(R.id.signal);
            ((ViewGroup) signal.getParent()).removeView(signal);
            ((ViewGroup.MarginLayoutParams) signal.getLayoutParams()).setMarginStart(0);
            ViewGroup row2 = findViewById(R.id.apps_row2);
            row2.addView(signal);
            row2.setVisibility(View.VISIBLE);
        }
        // Keep the row centred: the first button shown has no gap before it.
        ViewGroup row = findViewById(R.id.apps_row);
        for (int i = 0; i < row.getChildCount(); i++) {
            View button = row.getChildAt(i);
            if (button.getVisibility() == View.VISIBLE) {
                ((ViewGroup.MarginLayoutParams) button.getLayoutParams()).setMarginStart(0);
                break;
            }
        }
    }

    private void bind(View button, WatchContact contact, String app) {
        // Only offered for people on more than one app; hide the ones they aren't on.
        button.setVisibility(contact.apps.contains(app) ? View.VISIBLE : View.GONE);
        button.setOnClickListener(v -> {
            MainActivity.call(this, contact, app);
            finish();
        });
    }
}
