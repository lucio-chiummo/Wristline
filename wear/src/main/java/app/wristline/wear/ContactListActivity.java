package app.wristline.wear;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableLinearLayoutManager;
import androidx.wear.widget.WearableRecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * One list behind a home-screen button: an app's people (tap = call on that app, no picker),
 * favourites (tap = call, or pick the app), or all recent calls (tap = call back). Long lists get
 * the A–Z strip; hold a person to star them.
 */
public class ContactListActivity extends Activity {
    /** An app package, {@link AppLists#FAVOURITES} or {@link AppLists#RECENT}. */
    static final String EXTRA_KEY = "key";
    /** Shortest list that gets the A–Z strip. */
    static final int FAST_SCROLL_MIN = 20;
    private static final int TYPE_TITLE = 0;
    private static final int TYPE_CONTACT = 1;
    private static final int TYPE_RECENT = 2;
    private static final int TYPE_HINT = 3;

    private static final class Item {
        final int type;
        final CharSequence text;
        final WatchContact contact;
        final HistoryEntry call;

        Item(int type, CharSequence text, WatchContact contact, HistoryEntry call) {
            this.type = type;
            this.text = text;
            this.contact = contact;
            this.call = call;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private PhoneLink.Contacts loaded = new PhoneLink.Contacts(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    private String key;
    private WearableRecyclerView list;
    private TextView message;
    private View strip;
    private TextView bubble;
    private LetterIndex letters;

    static Intent intent(Context context, String key) {
        return new Intent(context, ContactListActivity.class).putExtra(EXTRA_KEY, key);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        key = getIntent().getStringExtra(EXTRA_KEY);
        list = findViewById(R.id.contacts);
        message = findViewById(R.id.message);
        strip = findViewById(R.id.letter_strip);
        bubble = findViewById(R.id.letter_bubble);
        list.setLayoutManager(new WearableLinearLayoutManager(this, new MainActivity.EdgeShrink()));
        list.setAdapter(adapter);
        strip.setOnTouchListener(this::onStripTouch);
        message.setText(R.string.loading);
        list.setVisibility(View.INVISIBLE);
        new Thread(() -> {
            PhoneLink.Contacts contacts = PhoneLink.contacts(this);
            runOnUiThread(() -> {
                loaded = contacts;
                rebuild();
                message.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
                list.requestFocus(); // rotary crown scrolls the list
            });
        }).start();
    }

    private boolean isApp() {
        return !AppLists.FAVOURITES.equals(key) && !AppLists.RECENT.equals(key);
    }

    private void rebuild() {
        items.clear();
        letters = null;
        List<WatchContact> people = new ArrayList<>();
        if (AppLists.RECENT.equals(key)) {
            items.add(new Item(TYPE_TITLE, getString(R.string.recent), null, null));
            for (HistoryEntry call : loaded.history) {
                items.add(new Item(TYPE_RECENT, null, null, call));
            }
        } else {
            people = AppLists.FAVOURITES.equals(key)
                    ? ContactOrder.favourites(loaded.all, Favourites.ids(this))
                    : AppLists.forApp(loaded.all, key);
            items.add(new Item(TYPE_TITLE, title(people.size()), null, null));
            for (WatchContact contact : people) {
                items.add(new Item(TYPE_CONTACT, null, contact, null));
            }
            if (isApp() && Favourites.ids(this).isEmpty() && !people.isEmpty()) {
                items.add(new Item(TYPE_HINT, getString(R.string.favourite_hint), null, null));
            }
        }
        boolean showStrip = isApp() && people.size() >= FAST_SCROLL_MIN;
        if (showStrip) {
            List<String> names = new ArrayList<>();
            for (WatchContact contact : people) {
                names.add(contact.name);
            }
            letters = LetterIndex.of(names);
        }
        strip.setVisibility(showStrip ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    /** "● WhatsApp · 214" with the dot in the app's colour, or "★ Favourites · 6". */
    private CharSequence title(int count) {
        boolean favourites = AppLists.FAVOURITES.equals(key);
        String name = favourites ? getString(R.string.favourites) : Protocol.label(key);
        SpannableStringBuilder out = new SpannableStringBuilder(favourites ? "★" : "●");
        out.setSpan(new ForegroundColorSpan(favourites ? getColor(R.color.star) : Ui.appColor(this, key)),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append(' ').append(getString(R.string.list_title, name, count));
        return out;
    }

    /** Dragging along the strip jumps the list to that letter and shows it in a bubble. */
    private boolean onStripTouch(View view, MotionEvent event) {
        if (letters == null) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            bubble.setVisibility(View.GONE);
            return true;
        }
        String letter = letters.atFraction(event.getY() / view.getHeight());
        bubble.setText(letter);
        bubble.setVisibility(View.VISIBLE);
        // The people start right after the title row.
        ((LinearLayoutManager) list.getLayoutManager())
                .scrollToPositionWithOffset(1 + letters.position(letter), list.getHeight() / 3);
        return true;
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
            int layout = type == TYPE_TITLE || type == TYPE_HINT ? R.layout.item_section : R.layout.item_person;
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            View row = holder.itemView;
            Activity activity = ContactListActivity.this;
            switch (item.type) {
                case TYPE_TITLE:
                    TextView title = (TextView) row;
                    title.setText(item.text);
                    title.setTextColor(getColor(R.color.text));
                    title.setTextSize(15);
                    // Clear of the round screen's top edge (set, not added: the row is re-bound).
                    title.setPadding(title.getPaddingLeft(), Math.round(32 * getResources().getDisplayMetrics().density),
                            title.getPaddingRight(), title.getPaddingBottom());
                    break;
                case TYPE_HINT:
                    TextView hint = (TextView) row;
                    hint.setText(item.text);
                    hint.setTextColor(getColor(R.color.muted));
                    hint.setTypeface(null, Typeface.NORMAL);
                    hint.setTextSize(12);
                    break;
                case TYPE_RECENT:
                    PersonRows.bindRecent(activity, row, item.call, loaded.all);
                    break;
                case TYPE_CONTACT:
                    View.OnClickListener tap = isApp()
                            ? v -> MainActivity.call(activity, item.contact, key)
                            : v -> MainActivity.pick(activity, item.contact);
                    PersonRows.bindContact(activity, row, item.contact, ContactListActivity.this::rebuild, tap);
                    break;
                default:
                    break;
            }
        }
    }
}
