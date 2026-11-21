package app.wristline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowTelecomManager;

/**
 * Reading the carrier account's enabled state. vivo on Android 17 refuses getPhoneAccount even
 * for the app's own account without READ_PHONE_NUMBERS, which Wristline doesn't hold: the home
 * screen crashed on open. The app's own accounts are still readable through
 * getRegisteredPhoneAccounts.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, shadows = CarrierEnabledTest.VivoTelecom.class)
public class CarrierEnabledTest {
    private Context context;
    private TelecomManager telecom;

    @Implements(TelecomManager.class)
    public static class VivoTelecom extends ShadowTelecomManager {
        static boolean refuseGetPhoneAccount;
        static boolean refuseRegistered;

        @Implementation
        @Override
        protected PhoneAccount getPhoneAccount(PhoneAccountHandle handle) {
            if (refuseGetPhoneAccount) {
                throw new SecurityException("getPhoneAccount: Neither user 10234 nor current process"
                        + " has android.permission.READ_PHONE_NUMBERS");
            }
            return super.getPhoneAccount(handle);
        }

        @Implementation(minSdk = 35)
        protected List<PhoneAccount> getRegisteredPhoneAccounts() {
            if (refuseRegistered) {
                throw new SecurityException("refused");
            }
            List<PhoneAccount> own = new ArrayList<>();
            for (PhoneAccount account : getAllPhoneAccounts()) {
                if (RuntimeEnvironment.getApplication().getPackageName()
                        .equals(account.getAccountHandle().getComponentName().getPackageName())) {
                    own.add(account);
                }
            }
            return own;
        }
    }

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        telecom = context.getSystemService(TelecomManager.class);
        RelayListener.registerAccount(context);
    }

    @After
    public void tearDown() {
        VivoTelecom.refuseGetPhoneAccount = false;
        VivoTelecom.refuseRegistered = false;
    }

    private void carrierAccountEnabled(boolean enabled) {
        PhoneAccount carrier = telecom.getPhoneAccount(RelayListener.carrierHandle(context));
        org.robolectric.util.ReflectionHelpers.setField(carrier, "mIsEnabled", enabled);
        telecom.registerPhoneAccount(carrier);
    }

    @Test
    public void readsTheEnabledCarrierWhenGetPhoneAccountIsRefused() {
        carrierAccountEnabled(true);
        VivoTelecom.refuseGetPhoneAccount = true;

        assertTrue(RelayListener.carrierEnabled(context));
    }

    @Test
    public void readsTheDisabledCarrierWhenGetPhoneAccountIsRefused() {
        carrierAccountEnabled(false);
        VivoTelecom.refuseGetPhoneAccount = true;

        assertFalse(RelayListener.carrierEnabled(context));
    }

    @Test
    public void reportsDisabledInsteadOfCrashingWhenEveryReadIsRefused() {
        carrierAccountEnabled(true);
        VivoTelecom.refuseGetPhoneAccount = true;
        VivoTelecom.refuseRegistered = true;

        assertFalse(RelayListener.carrierEnabled(context));
    }

    @Test
    public void stillReadsGetPhoneAccountWhereItIsAllowed() {
        carrierAccountEnabled(true);

        assertTrue(RelayListener.carrierEnabled(context));
    }
}
