package app.wristline.wear;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The hub: search everyone, the latest calls (tap to call back), then one button per app
 * (Favourites, WhatsApp, Telegram, Signal) that opens just those people in {@link ContactListActivity}.
 */
public class MainActivity extends Activity {
    /** From the tile or complication, with {@link LaunchToken#EXTRA}: call this contact straight away. */
    static final String EXTRA_CONTACT_ID = "contact_id";
    /** Latest calls shown on the home screen; the rest are behind "All recent". */
    static final int RECENT_ON_HOME = 3;
    private static final int TYPE_SEARCH = 0;
    private static final int TYPE_SECTION = 1;
    private static final int TYPE_CONTACT = 2;
    private static final int TYPE_RECENT = 3;
    private static final int TYPE_MORE = 4;
    private static final int TYPE_APPS = 5;

    /** One row of the list. */
    private static final class Item {
        final int type;
        final String text;
        final WatchContact contact;
        final HistoryEntry call;

        Item(int type, String text, WatchContact contact, HistoryEntry call) {
            this.type = type;
            this.text = text;
            this.contact = contact;
            this.call = call;
        }
    }

    private WearableRecyclerView list;
    private TextView message;
    private final Adapter adapter = new Adapter();
    private final List<Item> items = new ArrayList<>();
    private PhoneLink.Contacts loaded = new PhoneLink.Contacts(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private String query = "";
    /** The first load has landed; later ones (on return to the screen) are quiet. */
    private boolean everLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        list = findViewById(R.id.contacts);
        message = findViewById(R.id.message);
        list.setLayoutManager(new WearableLinearLayoutManager(this, new EdgeShrink()));
        list.setAdapter(adapter);
        list.requestFocus(); // rotary crown scrolls the list
        askForNotificationsOnce();
        load(false);
    }

    /** For the call indicator on the watch face; asked once, the app works without it. */
    private void askForNotificationsOnce() {
        android.content.SharedPreferences prefs = getSharedPreferences("watch", MODE_PRIVATE);
        if (prefs.getBoolean("asked_notifications", false)
                || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        prefs.edit().putBoolean("asked_notifications", true).apply();
        requestPermissions(new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A call screen minimised during a call: opening Wristline brings it back.
        InCallActivity.resume(this);
        // Back on the home screen after a call (or after a while away): show the latest calls.
        if (everLoaded) {
            load(true);
        }
        // The phone publishes a call a moment after it ends (missed calls a little later): reload
        // then too, while this screen is showing.
        CallStateService.onContactsChanged = this::onContactsChanged;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (CallStateService.onContactsChanged != null) {
            CallStateService.onContactsChanged = null;
        }
    }

    private void onContactsChanged() {
        if (everLoaded && !isDestroyed()) {
            load(true);
        }
    }

    /**
     * Rows shrink and fade as they near the top and bottom of the round screen, where the edges
     * would otherwise cut them off.
     */
    static final class EdgeShrink extends WearableLinearLayoutManager.LayoutCallback {
        @Override
        public void onLayoutFinished(View child, RecyclerView parent) {
            float centre = child.getY() + child.getHeight() / 2f;
            float half = parent.getHeight() / 2f;
            // 0 in the middle of the screen, 1 at the very top or bottom.
            float off = Math.min(1f, Math.abs(centre - half) / half);
            float edge = Math.max(0f, (off - 0.55f) / 0.45f);
            float scale = 1f - 0.18f * edge;
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(1f - 0.5f * edge);
        }
    }

    /**
     * Reads contacts and calls from the phone's synced data. {@code quiet}: a reload while the
     * screen is already showing data, so no "Loading" and no focus grab (the search keyboard may be
     * open); the rows below the search field are swapped when the data lands.
     */
    private void load(boolean quiet) {
        if (!quiet) {
            showMessage(R.string.loading, 0);
        }
        new Thread(() -> {
            PhoneLink.Contacts contacts = PhoneLink.contacts(this);
            // Only when the screen opens: on a reload, an empty list is the owner's own doing
            // (they unstarred the last one), and the phone's backup isn't republished to say so.
            if (!quiet) {
                Favourites.restoreIfEmpty(this, contacts.favourites);
            }
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                boolean wasShowing = list.getVisibility() == View.VISIBLE;
                loaded = contacts;
                everLoaded = true;
                rebuild();
                if (contacts.all.isEmpty() && contacts.history.isEmpty()) {
                    showMessage(R.string.no_contacts, R.drawable.ic_phone_link);
                } else if (!quiet || !wasShowing) {
                    message.setVisibility(View.GONE);
                    list.setVisibility(View.VISIBLE);
                    // Hidden while loading, it lost focus: take it back so the crown scrolls it.
                    list.requestFocus();
                }
                if (quiet) {
                    return;
                }
                long fromTile = LaunchToken.matches(this, getIntent())
                        ? getIntent().getLongExtra(EXTRA_CONTACT_ID, -1) : -1;
                getIntent().removeExtra(EXTRA_CONTACT_ID);
                WatchContact contact = byId(loaded.all, fromTile);
                if (contact != null) {
                    pick(this, contact);
                }
            });
        }).start();
    }

    /** Loading or nothing to show: the message alone, without the search field behind it. */
    private void showMessage(int text, int icon) {
        message.setText(text);
        message.setCompoundDrawablesRelativeWithIntrinsicBounds(0, icon, 0, 0);
        message.setCompoundDrawableTintList(ColorStateList.valueOf(getColor(R.color.muted)));
        message.setVisibility(View.VISIBLE);
        list.setVisibility(View.INVISIBLE);
    }

    private void rebuild() {
        int oldCount = items.size();
        items.clear();
        items.add(new Item(TYPE_SEARCH, null, null, null));
        if (!query.trim().isEmpty()) {
            for (WatchContact contact : ContactSearch.filter(loaded.all, query)) {
                items.add(new Item(TYPE_CONTACT, null, contact, null));
            }
        } else {
            if (!loaded.history.isEmpty()) {
                items.add(new Item(TYPE_SECTION, getString(R.string.recent), null, null));
                for (HistoryEntry call : loaded.history.subList(0, Math.min(RECENT_ON_HOME, loaded.history.size()))) {
                    items.add(new Item(TYPE_RECENT, null, null, call));
                }
                if (loaded.history.size() > RECENT_ON_HOME) {
                    items.add(new Item(TYPE_MORE, getString(R.string.all_recent), null, null));
                }
            }
            if (!AppLists.buttons(loaded.all, Favourites.ids(this)).isEmpty()) {
                items.add(new Item(TYPE_APPS, null, null, null));
            }
        }
        // Replace only the rows below the search field: notifyDataSetChanged() re-binds the field's
        // row too, which takes its focus away and closes the watch keyboard after every letter.
        if (oldCount == 0) {
            adapter.notifyDataSetChanged();
        } else {
            if (oldCount > 1) {
                adapter.notifyItemRangeRemoved(1, oldCount - 1);
            }
            if (items.size() > 1) {
                adapter.notifyItemRangeInserted(1, items.size() - 1);
            }
        }
    }

    static WatchContact byId(List<WatchContact> all, long id) {
        for (WatchContact contact : all) {
            if (contact.id == id) {
                return contact;
            }
        }
        return null;
    }

    /** Calls with the person's only app, or asks which app when they're on several. */
    static void pick(Activity activity, WatchContact contact) {
        if (contact.apps.size() == 1) {
            call(activity, contact, contact.apps.get(0));
        } else {
            activity.startActivity(ChooseAppActivity.intent(activity, contact));
        }
    }

    /** Calls back with the same app as the call, when the caller is a callable contact. */
    static void callBack(Activity activity, List<WatchContact> all, HistoryEntry call) {
        WatchContact contact = byId(all, call.contactId);
        if (contact == null) {
            Toast.makeText(activity, R.string.cant_call_back, Toast.LENGTH_LONG).show();
        } else if (contact.apps.contains(call.app)) {
            call(activity, contact, call.app);
        } else {
            pick(activity, contact);
        }
    }

    /** Asks the phone to call and opens Wristline's call screen on the watch. */
    static void call(Activity activity, WatchContact contact, String app) {
        activity.startActivity(InCallActivity.intent(activity, contact.name, app));
        new Thread(() -> {
            boolean sent = PhoneLink.call(activity, contact.id, app);
            if (!sent) {
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, R.string.no_phone, Toast.LENGTH_LONG).show();
                    InCallActivity screen = InCallActivity.current;
                    if (screen != null) {
                        screen.onState(Protocol.CallState.parse(Protocol.STATE_ENDED));
                    }
                });
            }
        }).start();
    }

    /** Shows the buttons that have people behind them, in order, two per row with no holes. */
    private void bindButtons(View view) {
        Set<Long> favourites = Favourites.ids(this);
        List<String> shown = AppLists.buttons(loaded.all, favourites);
        LinearLayout row1 = view.findViewById(R.id.buttons_row1);
        LinearLayout row2 = view.findViewById(R.id.buttons_row2);
        String[] keys = {AppLists.FAVOURITES, Protocol.WHATSAPP, Protocol.TELEGRAM, Protocol.SIGNAL};
        int[] ids = {R.id.btn_favourites, R.id.btn_whatsapp, R.id.btn_telegram, R.id.btn_signal};
        int[] counts = {R.id.btn_favourites_count, R.id.btn_whatsapp_count, R.id.btn_telegram_count, R.id.btn_signal_count};
        int[] labels = {R.id.btn_favourites_label, R.id.btn_whatsapp_label, R.id.btn_telegram_label, R.id.btn_signal_label};
        List<View> buttons = new ArrayList<>();
        for (int id : ids) {
            View button = view.findViewById(id);
            ((ViewGroup) button.getParent()).removeView(button);
            buttons.add(button);
        }
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            View button = buttons.get(i);
            boolean favouritesKey = AppLists.FAVOURITES.equals(key);
            int n = AppLists.count(loaded.all, favourites, key);
            String label = favouritesKey ? getString(R.string.favourites) : Protocol.label(key);
            int fg = favouritesKey ? getColor(R.color.star) : Ui.appColor(this, key);
            int bg = getColor(favouritesKey ? R.color.favourites_button
                    : Protocol.WHATSAPP.equals(key) ? R.color.whatsapp_button
                    : Protocol.TELEGRAM.equals(key) ? R.color.telegram_button : R.color.signal_button);
            button.setBackgroundTintList(ColorStateList.valueOf(bg));
            TextView labelView = button.findViewById(labels[i]);
            labelView.setText(favouritesKey ? "★ " + label : label);
            labelView.setTextColor(fg);
            TextView countView = button.findViewById(counts[i]);
            countView.setText(String.valueOf(n));
            countView.setTextColor(fg);
            button.setContentDescription(getString(R.string.app_button_description, label, n));
            button.setOnClickListener(v -> startActivity(ContactListActivity.intent(this, key)));
            int position = shown.indexOf(key);
            button.setVisibility(position < 0 ? View.GONE : View.VISIBLE);
            (position >= 0 && position < 2 ? row1 : row2).addView(button);
        }
        // Visible buttons first within each row, so a lone button sits on the left like the rest.
        for (LinearLayout row : new LinearLayout[] {row1, row2}) {
            List<View> hidden = new ArrayList<>();
            for (int i = 0; i < row.getChildCount(); i++) {
                if (row.getChildAt(i).getVisibility() != View.VISIBLE) {
                    hidden.add(row.getChildAt(i));
                }
            }
            for (View h : hidden) {
                row.removeView(h);
                row.addView(h);
            }
        }
        row2.setVisibility(shown.size() > 2 ? View.VISIBLE : View.GONE);
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            int layout = type == TYPE_SEARCH ? R.layout.item_search
                    : type == TYPE_SECTION || type == TYPE_MORE ? R.layout.item_section
                    : type == TYPE_APPS ? R.layout.item_app_buttons
                    : R.layout.item_person;
            View view = inflater.inflate(layout, parent, false);
            if (type == TYPE_SEARCH) {
                EditText search = view.findViewById(R.id.search);
                // The keyboard's Search/Enter key: keep the results, close the keyboard and hand the
                // crown to the list. Left to Android, Enter moves focus "down" into rows that are
                // being rebuilt after the last letter, and that threw (the app closed).
                search.setOnEditorActionListener((field, actionId, event) -> {
                    getSystemService(InputMethodManager.class).hideSoftInputFromWindow(field.getWindowToken(), 0);
                    field.clearFocus();
                    list.requestFocus();
                    return true;
                });
                search.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        query = s.toString();
                        list.post(MainActivity.this::rebuild);
                    }
                });
            }
            return new RecyclerView.ViewHolder(view) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            View row = holder.itemView;
            switch (item.type) {
                case TYPE_SECTION:
                    ((TextView) row).setText(item.text);
                    break;
                case TYPE_MORE:
                    TextView more = (TextView) row;
                    more.setText(item.text);
                    more.setTextColor(getColor(R.color.text));
                    more.setBackgroundResource(R.drawable.bg_pill);
                    more.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.card_high)));
                    more.setClickable(true);
                    more.setFocusable(true);
                    more.setOnClickListener(v -> startActivity(ContactListActivity.intent(MainActivity.this, AppLists.RECENT)));
                    break;
                case TYPE_RECENT:
                    PersonRows.bindRecent(MainActivity.this, row, item.call, loaded.all);
                    break;
                case TYPE_CONTACT:
                    PersonRows.bindContact(MainActivity.this, row, item.contact, MainActivity.this::rebuild,
                            v -> pick(MainActivity.this, item.contact));
                    break;
                case TYPE_APPS:
                    bindButtons(row);
                    break;
                default:
                    break;
            }
        }
    }
}
