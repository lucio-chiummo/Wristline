package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.Collections;

/** The Voice screen offers setup on the phone first, asks for consent, and hides "forget key" without a key. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class VoiceActivityButtonsTest {
    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
        PackageInfo info = new PackageInfo();
        info.packageName = SupportedApp.WHATSAPP.packageName;
        info.firstInstallTime = 1000;
        info.requestedPermissions = new String[] {Manifest.permission.READ_PHONE_STATE};
        info.requestedPermissionsFlags = new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
        shadowOf(context.getPackageManager()).installPackage(info);
    }

    private VoiceActivity open() {
        return Robolectric.buildActivity(VoiceActivity.class).setup().get();
    }

    @Test
    public void showsBothSetupButtonsWhenAnAppNeedsSetup() {
        VoiceActivity activity = open();

        assertEquals(View.VISIBLE, activity.findViewById(R.id.set_up_on_phone).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.set_up).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.set_up_section).getVisibility());
    }

    @Test
    public void consentDialogListsEachAppBeforeStartingTheGuide() {
        VoiceActivity activity = open();

        activity.findViewById(R.id.set_up_on_phone).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        String message = ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
        assertTrue(message, message.contains("WhatsApp"));
        assertEquals(null, shadowOf(activity).getNextStartedActivity());
        assertTrue(!Prefs.consent(context, SupportedApp.WHATSAPP));

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(VoiceSetupActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(Collections.singletonList("com.whatsapp"),
                started.getStringArrayListExtra(VoiceSetupActivity.EXTRA_PACKAGES));
        assertTrue(Prefs.consent(context, SupportedApp.WHATSAPP));
    }

    @Test
    public void forgetKeyHiddenWithoutKey() {
        assertEquals(View.GONE, open().findViewById(R.id.forget_key).getVisibility());
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> guidePackages() throws Exception {
        VoiceSetupActivity guide = Robolectric.buildActivity(VoiceSetupActivity.class,
                new Intent(context, VoiceSetupActivity.class)).setup().get();
        java.lang.reflect.Field field = VoiceSetupActivity.class.getDeclaredField("packages");
        field.setAccessible(true);
        return (java.util.List<String>) field.get(guide);
    }

    @Test
    public void guideOpenedFromANotificationSkipsAppsWithoutConsent() throws Exception {
        Prefs.setAppOn(context, SupportedApp.WHATSAPP, true);

        assertTrue(guidePackages().isEmpty());
    }

    @Test
    public void guideOpenedFromANotificationKeepsAppsThatConsented() throws Exception {
        Prefs.setAppOn(context, SupportedApp.WHATSAPP, true);
        Prefs.setConsent(context, SupportedApp.WHATSAPP, true);

        assertEquals(Collections.singletonList("com.whatsapp"), guidePackages());
    }

    private VoiceSetupActivity guideFromNotification() {
        SetupProgress.set(SetupProgress.Phase.IDLE);
        return Robolectric.buildActivity(VoiceSetupActivity.class,
                new Intent(context, VoiceSetupActivity.class)).setup().get();
    }

    @Test
    public void guideWithNothingToSetUpSaysSoInsteadOfWaiting() {
        Prefs.setAppOn(context, SupportedApp.WHATSAPP, true); // needs setup, but no consent

        VoiceSetupActivity guide = guideFromNotification();

        assertEquals(View.VISIBLE, guide.findViewById(R.id.guide_nothing).getVisibility());
        assertEquals(View.GONE, guide.findViewById(R.id.guide_steps).getVisibility());
        assertEquals(View.GONE, guide.findViewById(R.id.guide_done).getVisibility());
    }

    @Test
    public void guideWithEveryAppOnShowsTheDoneCard() {
        Prefs.setAppOn(context, SupportedApp.WHATSAPP, true);
        FullMode.markSetUp(context, SupportedApp.WHATSAPP);

        VoiceSetupActivity guide = guideFromNotification();

        assertEquals(View.VISIBLE, guide.findViewById(R.id.guide_done).getVisibility());
        assertEquals(View.GONE, guide.findViewById(R.id.guide_nothing).getVisibility());
        assertEquals(View.GONE, guide.findViewById(R.id.guide_steps).getVisibility());
        String text = ((TextView) guide.findViewById(R.id.guide_done_text)).getText().toString();
        assertTrue(text, text.contains("WhatsApp"));
    }
}
