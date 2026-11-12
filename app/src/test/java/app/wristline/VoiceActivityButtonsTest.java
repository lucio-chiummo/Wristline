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
}
