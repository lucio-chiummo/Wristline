package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class CallableContactsTest {
    private static final String WA = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call";
    private static final String TG = "vnd.android.cursor.item/vnd.org.telegram.messenger.android.call";

    @Test
    public void groupsRowsPerContactSortedByName() {
        List<CallableContacts.Contact> contacts = CallableContacts.group(List.of(
                new CallableContacts.Row(7, "Lumen Roskin", TG, 701),
                new CallableContacts.Row(3, "Annaly Biavon", WA, 301),
                new CallableContacts.Row(3, "Annaly Biavon", TG, 302),
                new CallableContacts.Row(5, "corvan", WA, 501)));
        assertEquals(3, contacts.size());
        assertEquals("Annaly Biavon", contacts.get(0).name);
        assertEquals(Long.valueOf(301), contacts.get(0).callRows.get("com.whatsapp"));
        assertEquals(Long.valueOf(302), contacts.get(0).callRows.get("org.telegram.messenger"));
        assertEquals("corvan", contacts.get(1).name); // case-insensitive order
        assertEquals("Lumen Roskin", contacts.get(2).name);
    }

    @Test
    public void ignoresVideoAndOtherRows() {
        List<CallableContacts.Contact> contacts = CallableContacts.group(List.of(
                new CallableContacts.Row(3, "Annaly", "vnd.android.cursor.item/vnd.com.whatsapp.video.call", 300),
                new CallableContacts.Row(3, "Annaly", "vnd.android.cursor.item/phone_v2", 299)));
        assertTrue(contacts.isEmpty());
    }

    @Test
    public void keepsFirstRowWhenAnAppAppearsTwice() { // WhatsApp writes one row per number
        List<CallableContacts.Contact> contacts = CallableContacts.group(List.of(
                new CallableContacts.Row(3, "Annaly", WA, 301),
                new CallableContacts.Row(3, "Annaly", WA, 305)));
        assertEquals(Long.valueOf(301), contacts.get(0).callRows.get("com.whatsapp"));
    }

    @Test
    public void appForMimeType() {
        assertEquals(SupportedApp.WHATSAPP, SupportedApp.forCallMimeType(WA));
        assertEquals(SupportedApp.TELEGRAM, SupportedApp.forCallMimeType(TG));
        assertNull(SupportedApp.forCallMimeType("vnd.android.cursor.item/phone_v2"));
    }
}
