package app.wristline.wear;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.text.format.DateUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

/** Fills an {@code item_person} row: a contact, or a recent call. Shared by the home screen and the lists. */
final class PersonRows {
    private PersonRows() {
    }

    /**
     * A contact: star when a favourite, name, the apps that reach them. Hold to star or unstar,
     * then {@code onChanged} runs so the screen can redraw.
     */
    static void bindContact(Activity activity, View row, WatchContact contact, Runnable onChanged,
            View.OnClickListener onTap) {
        boolean isFavourite = Favourites.ids(activity).contains(contact.id);
        Ui.avatar(row.findViewById(R.id.avatar), contact.name);
        android.widget.ImageView badge = row.findViewById(R.id.badge);
        badge.setVisibility(isFavourite ? View.VISIBLE : View.GONE);
        badge.setImageResource(R.drawable.ic_star);
        badge.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.star)));
        ((TextView) row.findViewById(R.id.name)).setText(contact.name);
        TextView detail = row.findViewById(R.id.detail);
        detail.setText(Ui.apps(activity, contact.apps));
        detail.setCompoundDrawablesRelative(null, null, null, null);
        detail.setTextColor(activity.getColor(R.color.muted));
        row.findViewById(R.id.app_badge).setVisibility(View.GONE);
        List<String> labels = new ArrayList<>();
        for (String app : contact.apps) {
            labels.add(Protocol.label(app));
        }
        row.setContentDescription(contact.name + ", " + String.join(", ", labels));
        row.setStateDescription(isFavourite ? activity.getString(R.string.favourite) : null);
        // TalkBack reads the hold action as "Add to favourites" instead of "long press".
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK,
                        activity.getString(isFavourite ? R.string.remove_favourite : R.string.add_favourite)));
            }
        });
        row.setOnClickListener(onTap);
        row.setOnLongClickListener(v -> {
            boolean now = Favourites.toggle(activity, contact.id);
            Toast.makeText(activity, activity.getString(now ? R.string.starred : R.string.unstarred, contact.name),
                    Toast.LENGTH_SHORT).show();
            onChanged.run();
            return true;
        });
    }

    /** A recent call: direction on the avatar, the app and when. Tap to call back. */
    static void bindRecent(Activity activity, View row, HistoryEntry call, List<WatchContact> all) {
        Ui.avatar(row.findViewById(R.id.avatar), call.name);
        row.findViewById(R.id.badge).setVisibility(View.GONE);
        Ui.appBadge(row.findViewById(R.id.app_badge), call.app);
        ((TextView) row.findViewById(R.id.name)).setText(call.name);
        String when = when(call);
        // The direction leads the detail line: red "Missed · 8:55 PM", or an arrow and the time.
        int tint = activity.getColor(call.outgoing ? R.color.accent : call.missed ? R.color.hang_up : R.color.muted);
        android.graphics.drawable.Drawable arrow = activity.getDrawable(call.outgoing ? R.drawable.ic_call_made
                : call.missed ? R.drawable.ic_call_missed : R.drawable.ic_call_received).mutate();
        int size = Math.round(13 * activity.getResources().getDisplayMetrics().density);
        arrow.setBounds(0, 0, size, size);
        arrow.setTint(tint);
        TextView detail = row.findViewById(R.id.detail);
        detail.setCompoundDrawablesRelative(arrow, null, null, null);
        detail.setCompoundDrawablePadding(Math.round(4 * activity.getResources().getDisplayMetrics().density));
        detail.setText(call.missed ? activity.getString(R.string.recent_missed_short, when) : when);
        detail.setTextColor(activity.getColor(call.missed ? R.color.hang_up : R.color.muted));
        row.setContentDescription(activity.getString(call.outgoing ? R.string.recent_outgoing
                        : call.missed ? R.string.recent_missed : R.string.recent_incoming,
                call.name, Protocol.label(call.app), when));
        // Rows are recycled: drop what a contact row set up.
        row.setStateDescription(null);
        row.setAccessibilityDelegate(null);
        row.setOnLongClickListener(null);
        row.setLongClickable(false);
        row.setOnClickListener(v -> MainActivity.callBack(activity, all, call));
    }

    static String when(HistoryEntry call) {
        return DateUtils.formatSameDayTime(call.time, System.currentTimeMillis(),
                DateFormat.SHORT, DateFormat.SHORT).toString();
    }
}
