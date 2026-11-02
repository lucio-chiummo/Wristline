package app.wristline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Contacts the user can call from the watch: grouped from the apps' "Voice call" contact rows. */
final class CallableContacts {
    /** One contacts data row (as read from ContactsContract.Data). */
    static final class Row {
        final long contactId;
        final String name;
        final String mimeType;
        final long dataId;

        Row(long contactId, String name, String mimeType, long dataId) {
            this.contactId = contactId;
            this.name = name;
            this.mimeType = mimeType;
            this.dataId = dataId;
        }
    }

    /** A person and, per app package, the data row that starts a voice call with them. */
    static final class Contact {
        final long id;
        final String name;
        final Map<String, Long> callRows = new LinkedHashMap<>();

        Contact(long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private CallableContacts() {
    }

    static List<Contact> group(List<Row> rows) {
        Map<Long, Contact> byId = new LinkedHashMap<>();
        for (Row row : rows) {
            SupportedApp app = SupportedApp.forCallMimeType(row.mimeType);
            if (app == null) {
                continue;
            }
            Contact contact = byId.computeIfAbsent(row.contactId, id -> new Contact(id, row.name));
            contact.callRows.putIfAbsent(app.packageName, row.dataId);
        }
        List<Contact> contacts = new ArrayList<>(byId.values());
        Collections.sort(contacts, (a, b) -> a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT)));
        return contacts;
    }
}
