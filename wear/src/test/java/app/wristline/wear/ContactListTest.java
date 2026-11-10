package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;

/** The lists behind the home buttons: one app's people, favourites, all recent calls. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w228dp-h228dp-round-watch-xhdpi")
public class ContactListTest {
    @After
    public void tearDown() {
        PhoneLink.preview = null;
    }

    private ContactListActivity open(String key, List<WatchContact> all, List<HistoryEntry> history) throws Exception {
        PhoneLink.preview = new PhoneLink.Contacts(all, new ArrayList<>(), history);
        ContactListActivity activity = Robolectric.buildActivity(ContactListActivity.class,
                ContactListActivity.intent(RuntimeEnvironment.getApplication(), key)).setup().get();
        HubTest.settle();
        HubTest.texts(activity);
        return activity;
    }

    private static View row(ContactListActivity activity, String name) {
        View text = HubTest.findText(activity.findViewById(R.id.contacts), name);
        assertNotNull(name, text);
        View row = text;
        while (!row.isClickable()) {
            row = (View) row.getParent();
        }
        return row;
    }

    @Test
    public void appListCallsThatAppDirectly() throws Exception {
        ContactListActivity activity = open(Protocol.SIGNAL, HubTest.people(), new ArrayList<>());
        row(activity, "Annaly Biavon").performClick(); // on WhatsApp and Signal
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertEquals(InCallActivity.class.getName(), started.getComponent().getClassName());
        assertTrue(started.toUri(0).contains(Protocol.SIGNAL));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void appListShowsOnlyThatApp() throws Exception {
        List<String> texts = HubTest.texts(open(Protocol.SIGNAL, HubTest.people(), new ArrayList<>()));
        assertTrue(texts.contains("Annaly Biavon"));
        assertEquals(0, HubTest.count(texts, "Corvan"));
    }

    @Test
    public void appListTitleShowsCount() throws Exception {
        List<String> texts = HubTest.texts(open(Protocol.WHATSAPP, HubTest.people(), new ArrayList<>()));
        assertTrue(texts.toString(), texts.contains("● WhatsApp · 3"));
    }

    @Test
    public void favouritesListUsesPicker() throws Exception {
        List<WatchContact> all = new ArrayList<>();
        all.add(new WatchContact(1, "Annaly Biavon", new ArrayList<>(List.of(Protocol.WHATSAPP, Protocol.TELEGRAM))));
        Favourites.toggle(RuntimeEnvironment.getApplication(), 1);
        ContactListActivity activity = open(AppLists.FAVOURITES, all, new ArrayList<>());
        row(activity, "Annaly Biavon").performClick();
        assertEquals(ChooseAppActivity.class.getName(),
                shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void recentListShowsAllHistory() throws Exception {
        List<String> texts = HubTest.texts(open(AppLists.RECENT, HubTest.people(), HubTest.calls(7)));
        assertEquals(3, HubTest.count(texts, "Corvan"));
        assertEquals(2, HubTest.count(texts, "Lumen Roskin"));
        assertEquals(2, HubTest.count(texts, "Annaly Biavon"));
    }

    @Test
    public void recentStrangerCantCallBack() throws Exception {
        List<HistoryEntry> history = new ArrayList<>();
        history.add(new HistoryEntry(System.currentTimeMillis(), false, Protocol.SIGNAL, -1, "Unknown", true));
        ContactListActivity activity = open(AppLists.RECENT, HubTest.people(), history);
        row(activity, "Unknown").performClick();
        assertEquals(activity.getString(R.string.cant_call_back), ShadowToast.getTextOfLatestToast());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void recentRowHasAppBadge() throws Exception {
        List<HistoryEntry> history = new ArrayList<>();
        history.add(new HistoryEntry(System.currentTimeMillis(), false, Protocol.SIGNAL, 1, "Annaly Biavon"));
        ContactListActivity activity = open(AppLists.RECENT, HubTest.people(), history);
        View row = row(activity, "Annaly Biavon");
        TextView badge = row.findViewById(R.id.app_badge);
        assertEquals(View.VISIBLE, badge.getVisibility());
        assertEquals("S", badge.getText().toString());
    }
}
