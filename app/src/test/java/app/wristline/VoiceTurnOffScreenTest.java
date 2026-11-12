package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
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

/**
 * Voice through the watch is set up and turned off with a computer: the Voice screen gives the exact
 * adb command, and only changes its state once the user says it ran.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class VoiceTurnOffScreenTest {
    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        StatusNotifications.createChannels(context);
        install(SupportedApp.TELEGRAM, true);
        install(SupportedApp.WHATSAPP, true);
    }

    private void install(SupportedApp app, boolean readsPhoneState) {
        PackageInfo info = new PackageInfo();
        info.packageName = app.packageName;
        info.firstInstallTime = 1000;
        if (readsPhoneState) {
            info.requestedPermissions = new String[] {Manifest.permission.READ_PHONE_STATE};
            info.requestedPermissionsFlags = new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
        }
        shadowOf(context.getPackageManager()).installPackage(info);
    }

    private VoiceActivity open() {
        return Robolectric.buildActivity(VoiceActivity.class).setup().get();
    }

    private static AlertDialog tapForDialog(VoiceActivity activity, int button) {
        activity.findViewById(button).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private static void tap(AlertDialog dialog, int button) {
        dialog.getButton(button).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static String message(AlertDialog dialog) {
        return ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
    }

    @Test
    public void setUpShowsTheCommandForTheApp() {
        AlertDialog dialog = tapForDialog(open(), R.id.set_up);

        String text = message(dialog);
        assertTrue(text, text.contains("adb shell appops set --uid com.whatsapp READ_PHONE_STATE ignore"));
        assertFalse(Prefs.fullModeOn(context, SupportedApp.WHATSAPP));
    }

    @Test
    public void sayingSetUpRanTurnsVoiceOn() {
        VoiceActivity activity = open();

        tap(tapForDialog(activity, R.id.set_up), DialogInterface.BUTTON_POSITIVE);

        assertTrue(Prefs.fullModeOn(context, SupportedApp.WHATSAPP));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.turn_off).getVisibility());
    }

    @Test
    public void turnOffIsOfferedWhenTheAppsRelaySwitchIsOff() {
        FullMode.markSetUp(context, SupportedApp.TELEGRAM);
        Prefs.setAppOn(context, SupportedApp.TELEGRAM, false);

        assertEquals(View.VISIBLE, open().findViewById(R.id.turn_off).getVisibility());
    }

    @Test
    public void turnOffIsHiddenWhenNothingWasSetUp() {
        assertEquals(View.GONE, open().findViewById(R.id.turn_off).getVisibility());
    }

    @Test
    public void turnOffShowsTheCommandAndChangesNothingYet() {
        FullMode.markSetUp(context, SupportedApp.TELEGRAM);

        AlertDialog dialog = tapForDialog(open(), R.id.turn_off);

        String text = message(dialog);
        assertTrue(text, text.contains("adb shell appops set --uid org.telegram.messenger READ_PHONE_STATE allow"));
        assertTrue(Prefs.fullModeOn(context, SupportedApp.TELEGRAM));
    }

    @Test
    public void sayingTurnOffRanTurnsVoiceOff() {
        FullMode.markSetUp(context, SupportedApp.TELEGRAM);
        VoiceActivity activity = open();

        tap(tapForDialog(activity, R.id.turn_off), DialogInterface.BUTTON_POSITIVE);

        assertFalse(Prefs.fullModeOn(context, SupportedApp.TELEGRAM));
        assertEquals(View.GONE, activity.findViewById(R.id.turn_off).getVisibility());
    }

    @Test
    public void cancellingTurnOffLeavesVoiceOn() {
        FullMode.markSetUp(context, SupportedApp.TELEGRAM);
        VoiceActivity activity = open();

        tap(tapForDialog(activity, R.id.turn_off), DialogInterface.BUTTON_NEGATIVE);

        assertTrue(Prefs.fullModeOn(context, SupportedApp.TELEGRAM));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.turn_off).getVisibility());
    }

    /** Without the permission no setting was changed (or the app is gone): nothing to run. */
    @Test
    public void appWithNoSettingToUndoTurnsOffWithoutACommand() {
        FullMode.markSetUp(context, SupportedApp.INSTAGRAM);
        install(SupportedApp.INSTAGRAM, false);
        VoiceActivity activity = open();

        activity.findViewById(R.id.turn_off).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(Prefs.fullModeOn(context, SupportedApp.INSTAGRAM));
        assertEquals(View.GONE, activity.findViewById(R.id.turn_off).getVisibility());
    }
}
