package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkCapabilities;

import java.util.Collections;

/**
 * A finished run stays finished on the guide: DONE shows the done card however the guide is
 * reached, a guide whose apps are all on never starts a run, and FAILED waits for Try again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class SetupGuideResultTest {
    private Application app;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(app);
        PackageInfo info = new PackageInfo();
        info.packageName = SupportedApp.WHATSAPP.packageName;
        info.firstInstallTime = 1000;
        info.requestedPermissions = new String[] {Manifest.permission.READ_PHONE_STATE};
        info.requestedPermissionsFlags = new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
        shadowOf(app.getPackageManager()).installPackage(info);
        Prefs.setAppOn(app, SupportedApp.WHATSAPP, true);
        Prefs.setConsent(app, SupportedApp.WHATSAPP, true);
        everythingReady();
    }

    @After
    public void tearDown() {
        SetupProgress.listener = null;
        SetupProgress.set(SetupProgress.Phase.IDLE);
    }

    /** Notifications, Wi-Fi, Developer options and Wireless debugging all on. */
    private void everythingReady() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        NetworkCapabilities wifi = ShadowNetworkCapabilities.newInstance();
        shadowOf(wifi).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        for (Network network : cm.getAllNetworks()) {
            shadowOf(cm).setNetworkCapabilities(network, wifi);
        }
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
        Settings.Global.putInt(app.getContentResolver(), "adb_wifi_enabled", 1);
    }

    private ActivityController<VoiceSetupActivity> guideFor(SupportedApp... apps) {
        Intent intent = new Intent(app, VoiceSetupActivity.class);
        java.util.ArrayList<String> packages = new java.util.ArrayList<>();
        for (SupportedApp a : apps) {
            packages.add(a.packageName);
        }
        intent.putStringArrayListExtra(VoiceSetupActivity.EXTRA_PACKAGES, packages);
        return Robolectric.buildActivity(VoiceSetupActivity.class, intent);
    }

    private static void assertDoneCard(VoiceSetupActivity guide) {
        assertEquals(View.VISIBLE, guide.findViewById(R.id.guide_done).getVisibility());
        assertEquals(View.GONE, guide.findViewById(R.id.guide_steps).getVisibility());
        String text = ((TextView) guide.findViewById(R.id.guide_done_text)).getText().toString();
        assertTrue(text, text.contains("WhatsApp"));
    }

    @Test
    public void readyGuideWithAnAppToSetUpStartsTheRun() { // the control: "ready" really is ready here
        guideFor(SupportedApp.WHATSAPP).setup();

        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull(started);
        assertEquals(AdbSetupService.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void doneRunThenReopenedGuideShowsDoneAndStartsNothing() {
        FullMode.markSetUp(app, SupportedApp.WHATSAPP); // what a successful run leaves behind
        SetupProgress.set(SetupProgress.Phase.DONE, "done", 0);

        VoiceSetupActivity guide = guideFor(SupportedApp.WHATSAPP).setup().get();

        assertDoneCard(guide);
        assertNull(shadowOf(app).getNextStartedService());
        assertEquals(SetupProgress.Phase.DONE, SetupProgress.phase());
    }

    @Test
    public void runFinishingWhileGuideIsPausedShowsDoneOnReturn() {
        SetupProgress.set(SetupProgress.Phase.WAITING_FOR_CODE, null, System.currentTimeMillis() + 60_000);
        ActivityController<VoiceSetupActivity> controller = guideFor(SupportedApp.WHATSAPP).setup();
        controller.pause().stop();

        FullMode.markSetUp(app, SupportedApp.WHATSAPP);
        SetupProgress.set(SetupProgress.Phase.DONE, "done", 0);
        controller.restart().resume();
        shadowOf(Looper.getMainLooper()).idle();

        assertDoneCard(controller.get());
        assertNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void doneRunSurvivesTheGuideBeingRecreated() {
        SetupProgress.set(SetupProgress.Phase.WAITING_FOR_CODE, null, System.currentTimeMillis() + 60_000);
        ActivityController<VoiceSetupActivity> controller = guideFor(SupportedApp.WHATSAPP).setup();
        FullMode.markSetUp(app, SupportedApp.WHATSAPP);
        SetupProgress.set(SetupProgress.Phase.DONE, "done", 0);

        controller.recreate();
        shadowOf(Looper.getMainLooper()).idle();

        assertDoneCard(controller.get());
        assertNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void doneRunThenGuideFromTheResultNotificationShowsDone() {
        FullMode.markSetUp(app, SupportedApp.WHATSAPP);
        SetupProgress.set(SetupProgress.Phase.DONE, "done", 0);

        VoiceSetupActivity guide = Robolectric.buildActivity(VoiceSetupActivity.class,
                new Intent(app, VoiceSetupActivity.class)).setup().get();

        assertDoneCard(guide);
        assertNull(shadowOf(app).getNextStartedService());
    }

    @Test
    public void guideWhoseAppsAreAllOnNeverStartsARun() {
        FullMode.markSetUp(app, SupportedApp.WHATSAPP);
        SetupProgress.set(SetupProgress.Phase.IDLE);

        VoiceSetupActivity guide = guideFor(SupportedApp.WHATSAPP).setup().get();

        assertDoneCard(guide);
        assertNull(shadowOf(app).getNextStartedService());
        assertEquals(SetupProgress.Phase.IDLE, SetupProgress.phase());
    }

    @Test
    public void failedRunShowsTheFailureAndOnlyTryAgainStartsANewOne() {
        SetupProgress.set(SetupProgress.Phase.FAILED, "It went wrong", 0);

        VoiceSetupActivity guide = guideFor(SupportedApp.WHATSAPP).setup().get();

        assertEquals(SetupProgress.Phase.FAILED, SetupProgress.phase());
        assertNull(shadowOf(app).getNextStartedService());
        assertEquals(View.GONE, guide.findViewById(R.id.guide_done).getVisibility());
        View pair = ((android.widget.LinearLayout) guide.findViewById(R.id.guide_steps))
                .getChildAt(6); // steps and dividers: step 4 is the 7th child
        String status = ((TextView) pair.findViewById(R.id.step_status)).getText().toString();
        assertTrue(status, status.contains("It went wrong"));
        android.widget.Button tryAgain = pair.findViewById(R.id.step_button);
        assertEquals(View.VISIBLE, tryAgain.getVisibility());
        assertEquals(app.getString(R.string.try_again), tryAgain.getText().toString());

        tryAgain.performClick();

        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull(started);
        assertEquals(Collections.singletonList("com.whatsapp"),
                started.getStringArrayListExtra(AdbSetupService.EXTRA_PACKAGES));
    }
}
