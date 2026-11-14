package app.wristline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/** The phone's call bridges (InCallServices) and watch companion apps, as the report lists them. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class CallBridgesTest {
    private PackageManager pm;

    @Before
    public void setUp() {
        pm = RuntimeEnvironment.getApplication().getPackageManager();
    }

    private void install(String pkg, String version) {
        PackageInfo info = new PackageInfo();
        info.packageName = pkg;
        info.versionName = version;
        shadowOf(pm).installPackage(info);
    }

    /** meta: null leaves the key out; otherwise the boolean value it declares. */
    private void bridge(String pkg, String name, Boolean selfManaged, Boolean ui) {
        ServiceInfo service = new ServiceInfo();
        service.packageName = pkg;
        service.name = name;
        Bundle meta = new Bundle();
        if (selfManaged != null) {
            meta.putBoolean(CallBridges.META_SELF_MANAGED, selfManaged);
        }
        if (ui != null) {
            meta.putBoolean(CallBridges.META_UI, ui);
        }
        service.metaData = meta;
        ResolveInfo resolve = new ResolveInfo();
        resolve.serviceInfo = service;
        shadowOf(pm).addResolveInfoForIntent(new Intent(CallBridges.ACTION_IN_CALL_SERVICE), resolve);
    }

    @Test
    public void readsSelfManagedFlagAsBoolean() {
        install("com.example.bridge", "2.0");
        install("com.example.ohealth", "4.2");
        bridge("com.example.ohealth", "Ui", false, true);
        bridge("com.example.bridge", "Bridge", true, null);

        List<CallBridges.Bridge> bridges = CallBridges.inCallServices(pm);

        assertEquals(2, bridges.size());
        assertEquals("com.example.bridge", bridges.get(0).packageName);
        assertEquals("2.0", bridges.get(0).version);
        assertTrue(bridges.get(0).selfManaged);
        assertFalse(bridges.get(0).ui);
        assertEquals("com.example.ohealth", bridges.get(1).packageName);
        assertFalse(bridges.get(1).selfManaged);
        assertTrue(bridges.get(1).ui);
    }

    @Test
    public void onePackageWithTwoServicesIsListedOnce() {
        install("com.example.bridge", "2.0");
        bridge("com.example.bridge", "A", false, null);
        bridge("com.example.bridge", "B", true, null);

        List<CallBridges.Bridge> bridges = CallBridges.inCallServices(pm);

        assertEquals(1, bridges.size());
        assertTrue(bridges.get(0).selfManaged);
    }

    @Test
    public void listsOnlyInstalledCompanions() {
        install("com.heytap.health.international", "4.2.0");

        assertEquals(List.of("com.heytap.health.international 4.2.0"), CallBridges.installedCompanions(pm));
    }
}
