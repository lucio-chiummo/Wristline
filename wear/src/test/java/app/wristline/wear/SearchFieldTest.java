package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The keyboard's Search/Enter key on the watch list's search field (crashed the app on 2026-11-02). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w228dp-h228dp-round-watch-xhdpi")
public class SearchFieldTest {
    @After
    public void tearDown() {
        PhoneLink.preview = null;
    }

    private static MainActivity withContacts() throws InterruptedException {
        List<WatchContact> all = new ArrayList<>();
        for (String name : new String[] {"Annaly", "Corvan", "Galvane", "Lumen", "Mirelle"}) {
            List<String> apps = new ArrayList<>();
            apps.add(Protocol.WHATSAPP);
            all.add(new WatchContact(all.size() + 1, name, apps));
        }
        PhoneLink.preview = new PhoneLink.Contacts(all, new ArrayList<>(), new ArrayList<>());
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        for (int i = 0; i < 10; i++) {
            Thread.sleep(30);
            shadowOf(Looper.getMainLooper()).idle();
        }
        return activity;
    }

    private static EditText search(MainActivity activity) {
        RecyclerView list = activity.findViewById(R.id.contacts);
        return list.getChildAt(0).findViewById(R.id.search);
    }

    @Test
    public void searchKeyKeepsTheAppOpenAndTheResults() throws InterruptedException {
        MainActivity activity = withContacts();
        EditText field = search(activity);
        field.requestFocus();
        field.setText("an");
        shadowOf(Looper.getMainLooper()).idle();

        // What the watch keyboard sends for its Search key.
        field.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
        field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(activity.isFinishing());
        assertEquals("an", field.getText().toString());
        // Keyboard closed, the list (not the field) has focus so the crown scrolls the results.
        assertFalse(field.hasFocus());
        // The list itself, not a row: Android's default "move focus down" into rows being rebuilt
        // is what crashed on the watch.
        assertTrue(((RecyclerView) activity.findViewById(R.id.contacts)).isFocused());
    }
}
