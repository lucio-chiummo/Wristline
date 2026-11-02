package app.wristline.wear;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.RemoteException;
import android.util.Log;

import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationText;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Watch-face complication: the first person on the tile (favourites first, then recent calls),
 * one tap calls them. With nobody yet, it opens Wristline.
 */
public class QuickCallComplicationService extends ComplicationDataSourceService {
    @Override
    public void onComplicationRequest(ComplicationRequest request, ComplicationRequestListener listener) {
        new Thread(() -> {
            PhoneLink.Contacts contacts = PhoneLink.contacts(this);
            List<WatchContact> quick = ContactOrder.quick(ContactOrder.favourites(contacts.all, Favourites.ids(this)),
                    ContactOrder.recent(contacts.all, contacts.recent, 1), 1);
            try {
                listener.onComplicationData(data(request.getComplicationType(), quick.isEmpty() ? null : quick.get(0)));
            } catch (RemoteException e) {
                Log.w(PhoneLink.TAG, "could not send the complication", e);
            }
        }, "quick-call-complication").start();
    }

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        List<String> apps = new ArrayList<>();
        apps.add(Protocol.WHATSAPP);
        return data(type, new WatchContact(-1, "Annaly", apps));
    }

    /** {@code contact} null: nobody to call yet. */
    private ComplicationData data(ComplicationType type, WatchContact contact) {
        String name = contact == null ? getString(R.string.app_name) : contact.name;
        ComplicationText description = new PlainComplicationText.Builder(contact == null
                ? getString(R.string.app_name) : getString(R.string.complication_call, name)).build();
        MonochromaticImage icon = new MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_call)).build();
        PendingIntent tap = tap(contact);
        if (type == ComplicationType.MONOCHROMATIC_IMAGE) {
            return new MonochromaticImageComplicationData.Builder(icon, description).setTapAction(tap).build();
        }
        String text = contact == null ? getString(R.string.complication_empty) : Avatar.shortName(name);
        return new ShortTextComplicationData.Builder(new PlainComplicationText.Builder(text).build(), description)
                .setMonochromaticImage(icon)
                .setTapAction(tap)
                .build();
    }

    private PendingIntent tap(WatchContact contact) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (contact != null && contact.id >= 0) {
            open.putExtra(MainActivity.EXTRA_CONTACT_ID, contact.id)
                    .putExtra(LaunchToken.EXTRA, LaunchToken.get(this));
        }
        // One request code per contact: the extra must not be reused from an older intent.
        int code = contact == null ? 0 : (int) contact.id;
        return PendingIntent.getActivity(this, code, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Favourites, contacts or recent calls changed: redraw the complication on every watch face. */
    static void refresh(Context context) {
        try {
            ComplicationDataSourceUpdateRequester.create(context,
                    new ComponentName(context, QuickCallComplicationService.class)).requestUpdateAll();
        } catch (RuntimeException e) {
            Log.w(PhoneLink.TAG, "could not refresh the complication", e);
        }
    }
}
