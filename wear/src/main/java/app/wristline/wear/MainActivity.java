package app.wristline.wear;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Search, favourites, recent calls and all contacts reachable on WhatsApp or Telegram. Tap to call
 * (or choose the app when both); hold a contact to star it.
 */
public class MainActivity extends Activity {
    /** From the tile or complication, with {@link LaunchToken#EXTRA}: call this contact straight away. */
    static final String EXTRA_CONTACT_ID = "contact_id";
    private static final int RECENT_SHOWN = 10;
    private static final int TYPE_SEARCH = 0;
    private static final int TYPE_SECTION = 1;
    private static final int TYPE_CONTACT = 2;
    private static final int TYPE_RECENT = 3;
    private static final int TYPE_HINT = 4;

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
    private static final int FAST_SCROLL_MIN = 20;
    private View strip;
    private TextView bubble;
    private LetterIndex letters;
    /** Adapter position of the first row of "All contacts". */
    private int allStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        list = findViewById(R.id.contacts);
        message = findViewById(R.id.message);
        list.setLayoutManager(new WearableLinearLayoutManager(this, new EdgeShrink()));
        list.setAdapter(adapter);
        list.requestFocus(); // rotary crown scrolls the list
        strip = findViewById(R.id.letter_strip);
        bubble = findViewById(R.id.letter_bubble);
        strip.setOnTouchListener(this::onStripTouch);
        askForNotificationsOnce();
        load();
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
    }

    /**
     * Rows shrink and fade as they near the top and bottom of the round screen, where the edges
     * would otherwise cut them off.
     */
    private static final class EdgeShrink extends WearableLinearLayoutManager.LayoutCallback {
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

    private void load() {
        showMessage(R.string.loading, 0);
        new Thread(() -> {
            PhoneLink.Contacts contacts = PhoneLink.contacts(this);
            Favourites.restoreIfEmpty(this, contacts.favourites);
            runOnUiThread(() -> {
                loaded = contacts;
                rebuild();
                if (contacts.all.isEmpty()) {
                    showMessage(R.string.no_contacts, R.drawable.ic_phone_link);
                } else {
                    message.setVisibility(View.GONE);
                    list.setVisibility(View.VISIBLE);
                    // Hidden while loading, it lost focus: take it back so the crown scrolls it.
                    list.requestFocus();
                }
                long fromTile = LaunchToken.matches(this, getIntent())
                        ? getIntent().getLongExtra(EXTRA_CONTACT_ID, -1) : -1;
                getIntent().removeExtra(EXTRA_CONTACT_ID);
                WatchContact contact = byId(fromTile);
                if (contact != null) {
                    pick(contact);
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
            List<WatchContact> favourites = ContactOrder.favourites(loaded.all, Favourites.ids(this));
            if (!favourites.isEmpty()) {
                items.add(new Item(TYPE_SECTION, getString(R.string.favourites), null, null));
                for (WatchContact contact : favourites) {
                    items.add(new Item(TYPE_CONTACT, null, contact, null));
                }
            }
            if (!loaded.history.isEmpty()) {
                items.add(new Item(TYPE_SECTION, getString(R.string.recent), null, null));
                for (HistoryEntry call : loaded.history.subList(0, Math.min(RECENT_SHOWN, loaded.history.size()))) {
                    items.add(new Item(TYPE_RECENT, null, null, call));
                }
            }
            if (!loaded.all.isEmpty()) {
                items.add(new Item(TYPE_SECTION, getString(R.string.all_contacts), null, null));
                allStart = items.size();
                for (WatchContact contact : loaded.all) {
                    items.add(new Item(TYPE_CONTACT, null, contact, null));
                }
                if (favourites.isEmpty()) {
                    items.add(new Item(TYPE_HINT, getString(R.string.favourite_hint), null, null));
                }
            }
        }
        updateStrip();
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

    /** The A–Z strip is for the full list only: not while searching, not for short lists. */
    private void updateStrip() {
        boolean show = query.trim().isEmpty() && loaded.all.size() >= FAST_SCROLL_MIN;
        if (show) {
            List<String> names = new ArrayList<>();
            for (WatchContact contact : loaded.all) {
                names.add(contact.name);
            }
            letters = LetterIndex.of(names);
        }
        strip.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /** Dragging along the strip jumps the list to that letter and shows it in a bubble. */
    private boolean onStripTouch(View view, android.view.MotionEvent event) {
        if (letters == null) {
            return false;
        }
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
            bubble.setVisibility(View.GONE);
            return true;
        }
        String letter = letters.atFraction(event.getY() / view.getHeight());
        bubble.setText(letter);
        bubble.setVisibility(View.VISIBLE);
        ((androidx.recyclerview.widget.LinearLayoutManager) list.getLayoutManager())
                .scrollToPositionWithOffset(allStart + letters.position(letter), list.getHeight() / 3);
        return true;
    }

    private WatchContact byId(long id) {
        for (WatchContact contact : loaded.all) {
            if (contact.id == id) {
                return contact;
            }
        }
        return null;
    }

    private void pick(WatchContact contact) {
        if (contact.apps.size() == 1) {
            call(this, contact, contact.apps.get(0));
        } else {
            startActivity(ChooseAppActivity.intent(this, contact));
        }
    }

    private void callBack(HistoryEntry call) {
        WatchContact contact = byId(call.contactId);
        if (contact == null) {
            Toast.makeText(this, R.string.cant_call_back, Toast.LENGTH_LONG).show();
        } else if (contact.apps.contains(call.app)) {
            call(this, contact, call.app);
        } else {
            pick(contact);
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
                    : type == TYPE_SECTION || type == TYPE_HINT ? R.layout.item_section
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
                case TYPE_HINT:
                    TextView title = (TextView) row;
                    title.setText(item.text);
                    title.setTextColor(getColor(item.type == TYPE_HINT ? R.color.muted : R.color.accent));
                    title.setTypeface(null, item.type == TYPE_HINT ? Typeface.NORMAL : Typeface.BOLD);
                    title.setTextSize(item.type == TYPE_HINT ? 12 : 13);
                    break;
                case TYPE_RECENT:
                    bindRecent(row, item.call);
                    break;
                case TYPE_CONTACT:
                    bindContact(row, item.contact);
                    break;
                default:
                    break;
            }
        }
    }

    private void bindContact(View row, WatchContact contact) {
        boolean isFavourite = Favourites.ids(this).contains(contact.id);
        Ui.avatar(row.findViewById(R.id.avatar), contact.name);
        ImageView badge = row.findViewById(R.id.badge);
        badge.setVisibility(isFavourite ? View.VISIBLE : View.GONE);
        badge.setImageResource(R.drawable.ic_star);
        badge.setImageTintList(ColorStateList.valueOf(getColor(R.color.star)));
        ((TextView) row.findViewById(R.id.name)).setText(contact.name);
        ((TextView) row.findViewById(R.id.detail)).setText(Ui.apps(this, contact.apps));
        List<String> labels = new ArrayList<>();
        for (String app : contact.apps) {
            labels.add(Protocol.label(app));
        }
        row.setContentDescription(contact.name + ", " + String.join(", ", labels));
        row.setStateDescription(isFavourite ? getString(R.string.favourite) : null);
        // TalkBack reads the hold action as "Add to favourites" instead of "long press".
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        getString(isFavourite ? R.string.remove_favourite : R.string.add_favourite)));
            }
        });
        row.setOnClickListener(v -> pick(contact));
        row.setOnLongClickListener(v -> {
            boolean now = Favourites.toggle(this, contact.id);
            Toast.makeText(this, getString(now ? R.string.starred : R.string.unstarred, contact.name),
                    Toast.LENGTH_SHORT).show();
            rebuild();
            return true;
        });
    }

    private void bindRecent(View row, HistoryEntry call) {
        Ui.avatar(row.findViewById(R.id.avatar), call.name);
        ImageView direction = row.findViewById(R.id.badge);
        direction.setVisibility(View.VISIBLE);
        direction.setImageResource(call.outgoing ? R.drawable.ic_call_made
                : call.missed ? R.drawable.ic_call_missed : R.drawable.ic_call_received);
        direction.setImageTintList(ColorStateList.valueOf(getColor(call.outgoing ? R.color.accent
                : call.missed ? R.color.hang_up : R.color.whatsapp)));
        ((TextView) row.findViewById(R.id.name)).setText(call.name);
        String when = DateUtils.formatSameDayTime(call.time, System.currentTimeMillis(),
                DateFormat.SHORT, DateFormat.SHORT).toString();
        ((TextView) row.findViewById(R.id.detail)).setText(Protocol.label(call.app) + " · " + when);
        row.setContentDescription(getString(call.outgoing ? R.string.recent_outgoing
                        : call.missed ? R.string.recent_missed : R.string.recent_incoming,
                call.name, Protocol.label(call.app), when));
        row.setOnClickListener(v -> callBack(call));
    }
}
