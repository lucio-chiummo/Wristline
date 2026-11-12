package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationManager;
import android.net.ConnectivityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The shell is a fake; the real one is unreachable here once the default Wi-Fi network is cleared. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class FullModeRepairTest {
    private static final SupportedApp WHATSAPP = SupportedApp.WHATSAPP;

    private Application context;
    private NotificationManager nm;
    private FullMode.Adb realAdb;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
        nm = context.getSystemService(NotificationManager.class);
        shadowOf(context.getSystemService(ConnectivityManager.class)).clearAllNetworks();
        realAdb = FullMode.adb;
    }

    @After
    public void tearDown() {
        FullMode.adb = realAdb;
    }

    private static class FakeAdb implements FullMode.Adb {
        final List<String> seen = new ArrayList<>();
        boolean reachable = true;
        boolean shortAnswer;

        @Override
        public boolean isReachable(android.content.Context context) {
            return reachable;
        }

        @Override
        public List<String> run(android.content.Context context, List<String> commands) {
            seen.addAll(commands);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < commands.size(); i++) {
                out.add(i % 2 == 1 ? "Uid mode: READ_PHONE_STATE: ignore" : "");
            }
            if (shortAnswer) {
                out.remove(out.size() - 1);
            }
            return out;
        }

        @Override
        public void forgetKey(android.content.Context context) {
        }
    }

    private void awaitRepairNotification() throws InterruptedException {
        long end = System.currentTimeMillis() + 2000;
        while (shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR) == null
                && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
    }

    @Test
    public void repairWithoutConsentPostsNotification() {
        Prefs.setConsent(context, WHATSAPP, false);

        FullMode.onCallDropped(context, WHATSAPP);

        assertNotNull(shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR));
        assertTrue(Prefs.broken(context, WHATSAPP));
    }

    @Test
    public void repairWithConsentButUnreachableFallsBackToNotification() throws Exception {
        Prefs.setConsent(context, WHATSAPP, true);

        FullMode.onCallDropped(context, WHATSAPP);
        awaitRepairNotification();

        assertNotNull(shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR));
        assertTrue(Prefs.broken(context, WHATSAPP));
    }

    @Test
    public void repairWithConsentAndReachableAdbRepairsSilently() throws Exception {
        FakeAdb fake = new FakeAdb();
        FullMode.adb = fake;
        Prefs.setConsent(context, WHATSAPP, true);

        FullMode.onCallDropped(context, WHATSAPP);
        long end = System.currentTimeMillis() + 2000;
        while (Prefs.broken(context, WHATSAPP) && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }

        assertFalse(Prefs.broken(context, WHATSAPP));
        assertNull(shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR));
        assertTrue(fake.seen.contains(AppOpsCommands.set("com.whatsapp")));
        assertTrue(fake.seen.contains(AppOpsCommands.get("com.whatsapp")));
        assertTrue(fake.seen.contains(AppOpsCommands.restart("com.whatsapp")));
        assertEquals(EventLog.REPAIR_SILENT, Prefs.events(context).get(0).code);
    }

    @Test
    public void applyWithMismatchedOutputFailsInsteadOfThrowing() {
        FakeAdb fake = new FakeAdb();
        fake.shortAnswer = true;
        FullMode.adb = fake;

        assertNull(FullMode.apply(context, List.of(WHATSAPP)));
    }

    @Test
    public void disableClearsBrokenAndRepairNotification() {
        FullMode.adb = new FakeAdb() {
            @Override
            public List<String> run(android.content.Context c, List<String> commands) {
                List<String> out = new ArrayList<>();
                for (int i = 0; i < commands.size(); i++) {
                    out.add(i % 2 == 1 ? "READ_PHONE_STATE: allow" : "");
                }
                return out;
            }
        };
        FullMode.markSetUp(context, WHATSAPP);
        Prefs.setBroken(context, WHATSAPP, true);
        StatusNotifications.showRepairNeeded(context, WHATSAPP);

        FullMode.disable(context, List.of(WHATSAPP));

        assertFalse(Prefs.broken(context, WHATSAPP));
        assertNull(shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR));
    }
}
