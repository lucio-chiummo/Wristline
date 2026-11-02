package app.wristline;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** Small helpers shared by the screens. */
final class AppUi {
    private AppUi() {
    }

    /** Supported apps installed on this phone, in table order. */
    static List<SupportedApp> installedApps(Context context) {
        List<SupportedApp> installed = new ArrayList<>();
        for (SupportedApp app : SupportedApp.ALL) {
            try {
                context.getPackageManager().getApplicationInfo(app.packageName, 0);
                installed.add(app);
            } catch (PackageManager.NameNotFoundException e) {
                // Not installed: not listed.
            }
        }
        return installed;
    }

    static Drawable icon(Context context, SupportedApp app) {
        try {
            return context.getPackageManager().getApplicationIcon(app.packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return context.getDrawable(R.drawable.bg_icon_tile);
        }
    }

    /** "WhatsApp, Telegram". */
    static String labels(Context context, List<SupportedApp> apps) {
        StringBuilder out = new StringBuilder();
        for (SupportedApp app : apps) {
            if (out.length() > 0) {
                out.append(context.getString(R.string.list_separator));
            }
            out.append(app.label);
        }
        return out.toString();
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(context.getResources().getDisplayMetrics().density))));
        view.setBackgroundColor(context.getColor(R.color.divider));
        return view;
    }
}
