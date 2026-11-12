package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.ConnectivityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** The phases of the on-phone setup that don't need a real Wireless debugging to talk to. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AdbSetupServiceTest {
    private Application context;
    private ServiceController<AdbSetupService> controller;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
        // No Wi-Fi: the setup can't look for the pairing service, so it fails straight away.
        shadowOf(context.getSystemService(ConnectivityManager.class)).clearAllNetworks();
        controller = Robolectric.buildService(AdbSetupService.class).create();
    }

    @After
    public void tearDown() {
        SetupProgress.set(SetupProgress.Phase.IDLE, null, 0);
    }

    private void start() {
        Prefs.setConsent(context, SupportedApp.WHATSAPP, true);
        ArrayList<String> packages = new ArrayList<>(List.of("com.whatsapp"));
        controller.withIntent(AdbSetupService.startIntent(context, packages)).startCommand(0, 1);
    }

    /** The work runs on its own thread; wait for it to finish and stop the service. */
    private void awaitFinished() throws InterruptedException {
        for (int i = 0; i < 100 && !shadowOf(controller.get()).isStoppedBySelf(); i++) {
            Thread.sleep(20);
        }
    }

    private Notification posted(int id) {
        return shadowOf(context.getSystemService(NotificationManager.class)).getNotification(id);
    }

    @Test
    public void startGoesToTheForegroundWithTheProgressNotification() {
        start();

        assertNotNull(shadowOf(controller.get()).getLastForegroundNotification());
        assertEquals(StatusNotifications.ID_PAIRING, shadowOf(controller.get()).getLastForegroundNotificationId());
    }

    @Test
    public void withoutWifiItFailsWithTryAgain() throws InterruptedException {
        start();
        awaitFinished();

        assertEquals(SetupProgress.Phase.FAILED, SetupProgress.phase());
        assertEquals(context.getString(R.string.wifi_needed), SetupProgress.message());
        Notification result = posted(StatusNotifications.ID_SETUP_RESULT);
        assertNotNull(result);
        assertEquals(context.getString(R.string.setup_failed_title),
                result.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
        assertEquals(1, result.actions.length);
        assertEquals(context.getString(R.string.try_again), result.actions[0].title.toString());
        assertTrue(shadowOf(controller.get()).isStoppedBySelf());
    }

    @Test
    public void systemTimeoutMarksTheSetupFailed() {
        SetupProgress.set(SetupProgress.Phase.WAITING_FOR_CODE);

        controller.get().onTimeout(1);

        assertEquals(SetupProgress.Phase.FAILED, SetupProgress.phase());
        assertEquals(context.getString(R.string.setup_not_paired), SetupProgress.message());
    }

    @Test
    public void destroyedMidSetupMarksItFailedSoTheGuideOffersTryAgain() {
        SetupProgress.set(SetupProgress.Phase.PAIRING);

        controller.destroy();

        assertEquals(SetupProgress.Phase.FAILED, SetupProgress.phase());
    }

    @Test
    public void destroyedAfterSuccessKeepsDone() {
        SetupProgress.set(SetupProgress.Phase.DONE);

        controller.destroy();

        assertEquals(SetupProgress.Phase.DONE, SetupProgress.phase());
    }

    @Test
    public void appWithoutConsentIsNeverSetUpAndRunsNoShell() throws InterruptedException {
        FullMode.Adb previous = FullMode.adb;
        FullMode.adb = new FullMode.Adb() {
            @Override
            public boolean isReachable(android.content.Context c) {
                throw new AssertionError("no shell without consent");
            }

            @Override
            public List<String> run(android.content.Context c, List<String> commands) {
                throw new AssertionError("no shell without consent");
            }

            @Override
            public void forgetKey(android.content.Context c) {
            }
        };
        try {
            ArrayList<String> packages = new ArrayList<>(List.of("com.whatsapp"));
            controller.withIntent(AdbSetupService.startIntent(context, packages)).startCommand(0, 1);
            awaitFinished();

            assertEquals(SetupProgress.Phase.FAILED, SetupProgress.phase());
            assertEquals(context.getString(R.string.setup_no_consent), SetupProgress.message());
        } finally {
            FullMode.adb = previous;
        }
    }
}
