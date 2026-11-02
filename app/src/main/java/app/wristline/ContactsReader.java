package app.wristline;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** Reads the WhatsApp/Telegram "Voice call" rows from the phone's contacts (needs READ_CONTACTS). */
final class ContactsReader {
    private ContactsReader() {
    }

    static List<CallableContacts.Contact> callable(Context context) {
        List<String> mimeTypes = new ArrayList<>();
        for (SupportedApp app : SupportedApp.ALL) {
            if (app.callMimeType != null) {
                mimeTypes.add(app.callMimeType);
            }
        }
        StringBuilder where = new StringBuilder(ContactsContract.Data.MIMETYPE + " IN (");
        for (int i = 0; i < mimeTypes.size(); i++) {
            where.append(i == 0 ? "?" : ",?");
        }
        where.append(')');
        List<CallableContacts.Row> rows = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                new String[] {ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.Data.MIMETYPE, ContactsContract.Data._ID},
                where.toString(), mimeTypes.toArray(new String[0]), null)) {
            while (cursor != null && cursor.moveToNext()) {
                String name = cursor.getString(1);
                rows.add(new CallableContacts.Row(cursor.getLong(0), name == null ? "" : name,
                        cursor.getString(2), cursor.getLong(3)));
            }
        } catch (RuntimeException e) {
            // No permission (turned off in Settings) or the provider failed: nothing to offer.
            Log.w(RelayListener.TAG, "could not read contacts", e);
        }
        return CallableContacts.group(rows);
    }

    /** The callable contact with this id, or null. */
    static CallableContacts.Contact find(Context context, long contactId) {
        for (CallableContacts.Contact contact : callable(context)) {
            if (contact.id == contactId) {
                return contact;
            }
        }
        return null;
    }
}
