package app.wristline.wear;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.os.Looper;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the watch screens with sample people to {@code wear/build/screens/*.png} (Pixel Watch
 * size, round), so the look can be checked without a watch. Fails only if a screen can't render.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 36, qualifiers = "w228dp-h228dp-round-watch-xhdpi")
public class WatchScreensTest {
    private static final File OUT = new File("build/screens");

    @After
    public void tearDown() {
        PhoneLink.preview = null;
    }

    private static List<String> apps(String... packages) {
        List<String> list = new ArrayList<>();
        for (String p : packages) {
            list.add(p);
        }
        return list;
    }

    private static PhoneLink.Contacts sample() {
        List<WatchContact> all = new ArrayList<>();
        all.add(new WatchContact(1, "Annaly Biavon", apps(Protocol.WHATSAPP, Protocol.TELEGRAM)));
        all.add(new WatchContact(2, "Corvan", apps(Protocol.WHATSAPP)));
        all.add(new WatchContact(3, "Galvane Vantor", apps(Protocol.WHATSAPP)));
        all.add(new WatchContact(4, "Lumen Roskin", apps(Protocol.TELEGRAM)));
        all.add(new WatchContact(5, "Mirelle", apps(Protocol.WHATSAPP)));
        all.add(new WatchContact(6, "Pellam Noxley", apps(Protocol.WHATSAPP)));
        long now = System.currentTimeMillis();
        List<HistoryEntry> history = new ArrayList<>();
        history.add(new HistoryEntry(now - 20 * 60_000, true, Protocol.WHATSAPP, 2, "Corvan"));
        history.add(new HistoryEntry(now - 3 * 3_600_000, false, Protocol.WHATSAPP, 5, "Mirelle"));
        history.add(new HistoryEntry(now - 26 * 3_600_000, false, Protocol.TELEGRAM, 4, "Lumen Roskin"));
        return new PhoneLink.Contacts(all, new ArrayList<>(), history);
    }

    /** MainActivity loads on a thread and shows the result on the main thread. */
    private static void settle() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            Thread.sleep(30);
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private static void save(Activity activity, String name) throws IOException {
        View root = activity.getWindow().getDecorView();
        int size = 456;
        root.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, size, size);
        Bitmap screen = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(screen);
        // The watch face is round: grey outside it, like the bezel.
        canvas.drawColor(Color.rgb(48, 48, 52));
        Path round = new Path();
        round.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW);
        canvas.clipPath(round);
        root.draw(canvas);
        assertTrue(OUT.isDirectory() || OUT.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(OUT, name + ".png"))) {
            screen.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void contactList() throws Exception {
        PhoneLink.preview = sample();
        Favourites.toggle(RuntimeEnvironment.getApplication(), 5);
        Favourites.toggle(RuntimeEnvironment.getApplication(), 1);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        save(activity, "1-list-top");

        RecyclerView list = activity.findViewById(R.id.contacts);
        // The crown scrolls whatever has focus: the list must have it once loaded.
        assertTrue(list.hasFocus());
        list.scrollBy(0, 330);
        settle();
        save(activity, "2-list-recent");

        list.scrollBy(0, 420);
        settle();
        save(activity, "3-list-all");
    }

    @Test
    public void noContacts() throws Exception {
        PhoneLink.preview = new PhoneLink.Contacts(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        save(activity, "4-empty");
    }

    @Test
    public void chooseApp() throws Exception {
        WatchContact annaly = new WatchContact(1, "Annaly Biavon", apps(Protocol.WHATSAPP, Protocol.TELEGRAM));
        Activity activity = Robolectric.buildActivity(ChooseAppActivity.class,
                ChooseAppActivity.intent(RuntimeEnvironment.getApplication(), annaly)).setup().get();
        save(activity, "5-choose-app");
    }

    @Test
    public void callScreen() throws Exception {
        Intent intent = InCallActivity.intent(RuntimeEnvironment.getApplication(), "Corvan", Protocol.WHATSAPP);
        InCallActivity activity = Robolectric.buildActivity(InCallActivity.class, intent).setup().get();
        activity.onState(Protocol.CallState.parse(Protocol.STATE_RINGING));
        shadowOf(Looper.getMainLooper()).idle();
        save(activity, "6-call-ringing");

        activity.onState(Protocol.CallState.parse(Protocol.STATE_ACTIVE + ";83000;" + Protocol.MUTE_OFF));
        shadowOf(Looper.getMainLooper()).idle();
        save(activity, "7-call-active");
    }
}
