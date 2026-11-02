package app.wristline.wear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Looper;

import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ModifiersBuilders;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity and ToggleRelayActivity are exported because tiles can only launch exported
 * activities, so any app on the watch can start them. Only Wristline's own tile and complication
 * know the token that makes them act.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w228dp-h228dp-round-watch-xhdpi")
public class LaunchTokenTest {
    private static final long CONTACT = 42;
    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        List<String> apps = new ArrayList<>();
        apps.add(Protocol.WHATSAPP);
        List<WatchContact> all = new ArrayList<>();
        all.add(new WatchContact(CONTACT, "Corvan", apps));
        PhoneLink.preview = new PhoneLink.Contacts(all, new ArrayList<>(), new ArrayList<>());
    }

    @After
    public void tearDown() {
        PhoneLink.preview = null;
    }

    private Intent callIntent() {
        return new Intent(context, MainActivity.class).putExtra(MainActivity.EXTRA_CONTACT_ID, CONTACT);
    }

    /** Runs MainActivity's load and says whether it went on to the call screen. */
    private boolean startsCall(Intent intent) throws InterruptedException {
        Activity activity = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        for (int i = 0; i < 20; i++) {
            Thread.sleep(30);
            shadowOf(Looper.getMainLooper()).idle();
            Intent next;
            while ((next = shadowOf(activity).getNextStartedActivity()) != null) {
                if (next.getComponent() != null && next.getComponent().getClassName().endsWith("InCallActivity")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void contactIdWithoutTokenDoesNotCall() throws InterruptedException {
        assertFalse(startsCall(callIntent()));
    }

    @Test
    public void contactIdWithWrongTokenDoesNotCall() throws InterruptedException {
        assertFalse(startsCall(callIntent().putExtra(LaunchToken.EXTRA, "guess")));
    }

    @Test
    public void contactIdWithTheTokenCalls() throws InterruptedException {
        assertTrue(startsCall(callIntent().putExtra(LaunchToken.EXTRA, LaunchToken.get(context))));
    }

    @Test
    public void relayToggleWithoutTokenDoesNothing() {
        Intent intent = new Intent(context, ToggleRelayActivity.class).putExtra(ToggleRelayActivity.EXTRA_ON, false);

        Activity activity = Robolectric.buildActivity(ToggleRelayActivity.class, intent).create().get();

        assertTrue(activity.isFinishing());
    }

    @Test
    public void relayToggleWithTheTokenGoesToThePhone() {
        Intent intent = new Intent(context, ToggleRelayActivity.class)
                .putExtra(ToggleRelayActivity.EXTRA_ON, false)
                .putExtra(LaunchToken.EXTRA, LaunchToken.get(context));

        Activity activity = Robolectric.buildActivity(ToggleRelayActivity.class, intent).create().get();

        assertFalse(activity.isFinishing());
    }

    @Test
    public void tokenStaysTheSameAndIsLongEnoughNotToGuess() {
        String token = LaunchToken.get(context);

        assertEquals(token, LaunchToken.get(context));
        assertTrue(token.length() >= 16);
    }

    @Test
    public void tokenDiffersBetweenInstalls() {
        String first = LaunchToken.get(context);
        context.getSharedPreferences("watch", Application.MODE_PRIVATE).edit().clear().commit();

        assertNotEquals(first, LaunchToken.get(context));
    }

    @Test
    public void complicationTapCarriesTheToken() {
        QuickCallComplicationService service = Robolectric.buildService(QuickCallComplicationService.class).create().get();
        WatchContact contact = new WatchContact(CONTACT, "Corvan", new ArrayList<>());

        PendingIntent tap = ReflectionHelpers.callInstanceMethod(service, "tap",
                ClassParameter.from(WatchContact.class, contact));

        assertTrue(LaunchToken.matches(context, shadowOf(tap).getSavedIntent()));
    }

    private static String stringExtra(ActionBuilders.AndroidActivity activity, String key) {
        ActionBuilders.AndroidExtra extra = activity.getKeyToExtraMapping().get(key);
        return extra == null ? null : ((ActionBuilders.AndroidStringExtra) extra).getValue();
    }

    @Test
    public void tileContactCarriesTheToken() {
        RecentTileService tile = Robolectric.buildService(RecentTileService.class).create().get();

        ModifiersBuilders.Clickable clickable = ReflectionHelpers.callInstanceMethod(tile, "launch",
                ClassParameter.from(String.class, "contact-42"), ClassParameter.from(long.class, CONTACT));

        ActionBuilders.LaunchAction action = (ActionBuilders.LaunchAction) clickable.getOnClick();
        assertEquals(LaunchToken.get(context), stringExtra(action.getAndroidActivity(), LaunchToken.EXTRA));
    }

    @Test
    public void tileRelayChipCarriesTheToken() {
        RecentTileService tile = Robolectric.buildService(RecentTileService.class).create().get();

        ActionBuilders.AndroidActivity activity = ReflectionHelpers.callInstanceMethod(tile, "relayActivity",
                ClassParameter.from(boolean.class, true));

        assertEquals(LaunchToken.get(context), stringExtra(activity, LaunchToken.EXTRA));
    }
}
