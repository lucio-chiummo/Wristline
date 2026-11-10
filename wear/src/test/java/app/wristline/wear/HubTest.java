package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The hub home screen: latest calls, "All recent", one button per app. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w228dp-h228dp-round-watch-xhdpi")
public class HubTest {
    private static final long NOW = System.currentTimeMillis();

    @After
    public void tearDown() {
        PhoneLink.preview = null;
    }

    static List<WatchContact> people() {
        List<WatchContact> all = new ArrayList<>();
        all.add(new WatchContact(1, "Annaly Biavon", new ArrayList<>(List.of(Protocol.WHATSAPP, Protocol.SIGNAL))));
        all.add(new WatchContact(2, "Corvan", new ArrayList<>(List.of(Protocol.WHATSAPP))));
        all.add(new WatchContact(4, "Lumen Roskin", new ArrayList<>(List.of(Protocol.WHATSAPP))));
        return all;
    }

    static List<HistoryEntry> calls(int n) {
        String[] names = {"Corvan", "Lumen Roskin", "Annaly Biavon", "Corvan", "Lumen Roskin", "Annaly Biavon", "Corvan"};
        long[] ids = {2, 4, 1, 2, 4, 1, 2};
        List<HistoryEntry> history = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            history.add(new HistoryEntry(NOW - (i + 1) * 3_600_000L, false, Protocol.WHATSAPP, ids[i], names[i]));
        }
        return history;
    }

    static void settle() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            Thread.sleep(30);
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    /** Lays the screen out tall enough that every row of the list exists, then reads all the text. */
    static List<String> texts(android.app.Activity activity) {
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(456, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(6000, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 456, 6000);
        List<String> out = new ArrayList<>();
        collect(activity.findViewById(R.id.contacts), out);
        return out;
    }

    private static void collect(View view, List<String> out) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof TextView && !(view instanceof EditText)) {
            out.add(((TextView) view).getText().toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), out);
            }
        }
    }

    static int count(List<String> texts, String text) {
        int n = 0;
        for (String t : texts) {
            if (t.equals(text)) {
                n++;
            }
        }
        return n;
    }

    private MainActivity open(List<HistoryEntry> history) throws InterruptedException {
        PhoneLink.preview = new PhoneLink.Contacts(people(), new ArrayList<>(), history);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        return activity;
    }

    @Test
    public void homeShowsThreeRecentsThenAllRecent() throws Exception {
        List<String> texts = texts(open(calls(5)));
        // Calls 1–3: Corvan, Lumen, Annaly; calls 4 and 5 stay off the home screen.
        assertEquals(1, count(texts, "Corvan"));
        assertEquals(1, count(texts, "Lumen Roskin"));
        assertEquals(1, count(texts, "Annaly Biavon"));
        assertTrue(texts.contains("All recent"));
    }

    @Test
    public void homeHasNoAllRecentForThreeOrFewer() throws Exception {
        assertFalse(texts(open(calls(3))).contains("All recent"));
    }

    @Test
    public void buttonsShowCounts() throws Exception {
        MainActivity activity = open(calls(1));
        texts(activity);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_whatsapp).getVisibility());
        assertEquals("3", ((TextView) activity.findViewById(R.id.btn_whatsapp_count)).getText().toString());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_signal).getVisibility());
        assertEquals("1", ((TextView) activity.findViewById(R.id.btn_signal_count)).getText().toString());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_favourites).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_telegram).getVisibility());
    }

    @Test
    public void signalButtonOpensSignalList() throws Exception {
        MainActivity activity = open(calls(1));
        texts(activity);
        activity.findViewById(R.id.btn_signal).performClick();
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertEquals(ContactListActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(Protocol.SIGNAL, started.getStringExtra(ContactListActivity.EXTRA_KEY));
    }

    @Test
    public void allRecentOpensRecentList() throws Exception {
        MainActivity activity = open(calls(5));
        List<String> texts = texts(activity);
        assertTrue(texts.contains("All recent"));
        findText(activity.findViewById(R.id.contacts), "All recent").performClick();
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertEquals(AppLists.RECENT, started.getStringExtra(ContactListActivity.EXTRA_KEY));
    }

    @Test
    public void searchStillFiltersEveryone() throws Exception {
        MainActivity activity = open(calls(1));
        texts(activity);
        ((EditText) activity.findViewById(R.id.search)).setText("lum");
        settle();
        List<String> texts = texts(activity);
        assertEquals(1, count(texts, "Lumen Roskin"));
        assertFalse(texts.contains("Corvan"));
        assertTrue(activity.findViewById(R.id.btn_whatsapp) == null
                || !activity.findViewById(R.id.btn_whatsapp).isShown());
    }

    static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void resumeShowsNewCall() throws Exception {
        ActivityController<MainActivity> controller = controller(calls(1)); // Corvan
        List<HistoryEntry> newer = new ArrayList<>();
        newer.add(new HistoryEntry(NOW, false, Protocol.SIGNAL, 1, "Annaly Biavon"));
        newer.addAll(calls(1));
        PhoneLink.preview = new PhoneLink.Contacts(people(), new ArrayList<>(), newer);
        controller.pause().stop().restart().start().resume();
        settle();
        List<String> texts = texts(controller.get());
        // The new call is listed, above the older one.
        assertTrue(texts.contains("Annaly Biavon"));
        assertTrue(texts.indexOf("Annaly Biavon") < texts.indexOf("Corvan"));
    }

    @Test
    public void reloadKeepsSearchResults() throws Exception {
        ActivityController<MainActivity> controller = controller(calls(1));
        MainActivity activity = controller.get();
        texts(activity);
        EditText search = activity.findViewById(R.id.search);
        search.setText("cor");
        settle();
        PhoneLink.preview = new PhoneLink.Contacts(people(), new ArrayList<>(), calls(3));
        controller.pause().resume();
        settle();
        List<String> texts = texts(activity);
        assertEquals("cor", ((EditText) activity.findViewById(R.id.search)).getText().toString());
        assertEquals(1, count(texts, "Corvan"));
        assertFalse(texts.contains("Recent"));
    }

    @Test
    public void quietReloadDoesNotRestoreRemovedFavourites() throws Exception {
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        PhoneLink.preview = new PhoneLink.Contacts(people(), new ArrayList<>(), calls(1), new ArrayList<>(List.of(1L)));
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        assertTrue(Favourites.ids(app).contains(1L)); // restored from the phone's backup on first open
        Favourites.toggle(app, 1L); // the owner unstars the last favourite
        controller.pause().resume();
        settle();
        assertTrue(Favourites.ids(app).isEmpty());
    }

    @Test
    public void phoneUpdateRefreshesOpenHome() throws Exception {
        ActivityController<MainActivity> controller = controller(calls(1)); // Corvan
        List<HistoryEntry> newer = new ArrayList<>();
        newer.add(new HistoryEntry(NOW, false, Protocol.SIGNAL, 1, "Annaly Biavon", true));
        newer.addAll(calls(1));
        PhoneLink.preview = new PhoneLink.Contacts(people(), new ArrayList<>(), newer);
        CallStateService.contactsChanged(); // the phone published while home stays on screen
        settle();
        List<String> texts = texts(controller.get());
        assertTrue(texts.contains("Annaly Biavon"));
        assertTrue(texts.indexOf("Annaly Biavon") < texts.indexOf("Corvan"));
    }

    /** Kept for Task 4: a controller so tests can pause and resume the home screen. */
    static ActivityController<MainActivity> controller(List<HistoryEntry> history) throws InterruptedException {
        PhoneLink.preview = new PhoneLink.Contacts(people(), new ArrayList<>(), history);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        settle();
        return controller;
    }
}
