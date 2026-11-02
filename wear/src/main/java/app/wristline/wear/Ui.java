package app.wristline.wear;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import java.util.List;

/** Shared look of the watch screens: avatars and app colours. */
final class Ui {
    private Ui() {
    }

    /** Fills an avatar circle with the person's initials, in their colour. */
    static void avatar(TextView view, String name) {
        Context context = view.getContext();
        view.setText(Avatar.initials(name));
        view.setBackgroundTintList(ColorStateList.valueOf(avatarColor(context, name)));
    }

    /** The person's avatar colour (also used by the tile). */
    static int avatarColor(Context context, String name) {
        TypedArray colors = context.getResources().obtainTypedArray(R.array.avatar_colors);
        try {
            return colors.getColor(Avatar.colorIndex(name), 0);
        } finally {
            colors.recycle();
        }
    }

    static int appColor(Context context, String app) {
        return context.getColor(Protocol.WHATSAPP.equals(app) ? R.color.whatsapp
                : Protocol.TELEGRAM.equals(app) ? R.color.telegram : R.color.muted);
    }

    /** "● WhatsApp ● Telegram", each dot in the app's colour. */
    static CharSequence apps(Context context, List<String> apps) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (String app : apps) {
            if (out.length() > 0) {
                out.append(' ');
            }
            int start = out.length();
            out.append('●');
            out.setSpan(new ForegroundColorSpan(appColor(context, app)), start, out.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.append(' ').append(Protocol.label(app));
        }
        return out;
    }
}
