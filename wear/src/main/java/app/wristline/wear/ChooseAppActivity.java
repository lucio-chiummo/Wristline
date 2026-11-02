package app.wristline.wear;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** "Call with WhatsApp / Telegram" for someone reachable on both. */
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
    }

    private void bind(View button, WatchContact contact, String app) {
        // Only offered for people on both apps; hide one that isn't there anyway.
        button.setVisibility(contact.apps.contains(app) ? View.VISIBLE : View.GONE);
        button.setOnClickListener(v -> {
            MainActivity.call(this, contact, app);
            finish();
        });
    }
}
