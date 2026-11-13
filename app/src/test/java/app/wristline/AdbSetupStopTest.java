package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.Collections;
import java.util.List;

/**
 * The typed pairing code arrives as a newer start of the service. stopSelf(id) is ignored unless id
 * is the latest start, so finishing with the first id would leave the service running.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AdbSetupStopTest {
    @Test
    public void finishingAfterACodeStopsTheLatestStart() {
        StatusNotifications.createChannels(RuntimeEnvironment.getApplication());
        AdbSetupService service = Robolectric.buildService(AdbSetupService.class).create().get();
        ReflectionHelpers.setField(service, "running", true);
        ReflectionHelpers.setField(service, "runStartId", 1);
        // finish() only acts for the run's own worker thread.
        ReflectionHelpers.setField(service, "worker", Thread.currentThread());

        service.onStartCommand(new Intent(AdbSetupService.ACTION_CODE), 0, 2);
        ReflectionHelpers.callInstanceMethod(service, "finish",
                ClassParameter.from(List.class, Collections.emptyList()),
                ClassParameter.from(List.class, null),
                ClassParameter.from(String.class, "failed"));

        assertEquals(2, shadowOf(service).getStopSelfId());
    }

    @Test
    public void aSecondStartWhileRunningIsTheOneFinishingStops() {
        StatusNotifications.createChannels(RuntimeEnvironment.getApplication());
        AdbSetupService service = Robolectric.buildService(AdbSetupService.class).create().get();
        ReflectionHelpers.setField(service, "running", true);
        ReflectionHelpers.setField(service, "runStartId", 1);
        ReflectionHelpers.setField(service, "worker", Thread.currentThread());

        service.onStartCommand(AdbSetupService.startIntent(RuntimeEnvironment.getApplication(),
                new java.util.ArrayList<>(List.of("com.whatsapp"))), 0, 2);
        ReflectionHelpers.callInstanceMethod(service, "finish",
                ClassParameter.from(List.class, Collections.emptyList()),
                ClassParameter.from(List.class, null),
                ClassParameter.from(String.class, "failed"));

        assertEquals(2, shadowOf(service).getStopSelfId());
        // Still the first run's thread: the second start did not start another worker.
        assertEquals(Thread.currentThread(), ReflectionHelpers.getField(service, "worker"));
    }

    private static AdbSetupService serviceShowingTheCodePrompt() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(app);
        AdbSetupService service = Robolectric.buildService(AdbSetupService.class).create().get();
        ReflectionHelpers.setField(service, "running", true);
        app.getSystemService(android.app.NotificationManager.class).notify(StatusNotifications.ID_PAIRING_CODE,
                new android.app.Notification.Builder(app, StatusNotifications.CHANNEL_SETUP)
                        .setSmallIcon(R.drawable.ic_notification).build());
        return service;
    }

    private static boolean codePromptShown() {
        android.app.NotificationManager manager = RuntimeEnvironment.getApplication()
                .getSystemService(android.app.NotificationManager.class);
        return shadowOf(manager).getNotification(StatusNotifications.ID_PAIRING_CODE) != null;
    }

    @Test
    public void timingOutRemovesThePairingCodePrompt() {
        AdbSetupService service = serviceShowingTheCodePrompt();
        org.junit.Assert.assertTrue(codePromptShown());

        service.onTimeout(1);

        org.junit.Assert.assertFalse(codePromptShown());
    }

    @Test
    public void beingDestroyedRemovesThePairingCodePrompt() {
        AdbSetupService service = serviceShowingTheCodePrompt();

        service.onDestroy();

        org.junit.Assert.assertFalse(codePromptShown());
    }
}
