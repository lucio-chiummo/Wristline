package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** When a missed call offers "Call back". */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class MissedCallsTest {
    private Application context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE);
        Prefs.setWatchCalling(context, true);
    }

    @Test
    public void callableContactOnWhatsAppOrTelegram() {
        assertTrue(MissedCalls.canCallBack(context, SupportedApp.WHATSAPP, 7));
        assertTrue(MissedCalls.canCallBack(context, SupportedApp.TELEGRAM, 7));
    }

    @Test
    public void notAContact() {
        assertFalse(MissedCalls.canCallBack(context, SupportedApp.WHATSAPP, -1));
    }

    @Test
    public void instagramHasNoWayToStartACall() {
        assertFalse(MissedCalls.canCallBack(context, SupportedApp.INSTAGRAM, 7));
    }

    @Test
    public void callingFromTheWatchOff() {
        Prefs.setWatchCalling(context, false);
        assertFalse(MissedCalls.canCallBack(context, SupportedApp.WHATSAPP, 7));
    }

    @Test
    public void withoutTheCallPermission() {
        shadowOf(context).denyPermissions(Manifest.permission.CALL_PHONE);
        assertFalse(MissedCalls.canCallBack(context, SupportedApp.WHATSAPP, 7));
    }

    @Test
    public void twoMissedCallsGetTwoNotifications() {
        assertTrue(MissedCalls.notificationId(1_000_000) != MissedCalls.notificationId(1_060_000));
    }
}
