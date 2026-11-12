package app.wristline.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.net.ConnectivityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AdbDiscoveryTest {
    private Application ctx;
    private ConnectivityManager cm;

    @Before
    public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
        cm = ctx.getSystemService(ConnectivityManager.class);
    }

    private static InetAddress ip(String literal) throws Exception {
        return InetAddress.getByName(literal);
    }

    @Test
    public void plainWifiNetworkHoldsOurAddress() {
        assertTrue(AdbDiscovery.isPlainWifi(true, false));
    }

    @Test
    public void vpnOverWifiIsNotPlainWifi() { // its link addresses are the tunnel's
        assertFalse(AdbDiscovery.isPlainWifi(true, true));
        assertTrue(AdbDiscovery.isPlainWifi(true, false));
        assertFalse(AdbDiscovery.isPlainWifi(false, false));
    }

    @Test
    public void mobileDataDoesNot() {
        assertFalse(AdbDiscovery.isPlainWifi(false, false));
    }

    @Test
    public void onWifiFalseWithoutNetworks() {
        shadowOf(cm).clearAllNetworks();
        assertFalse(AdbDiscovery.onWifi(ctx));
    }

    @Test
    public void findReturnsNullOffWifi() {
        shadowOf(cm).clearAllNetworks();
        long start = System.nanoTime();
        assertNull(AdbDiscovery.find(ctx, AdbDiscovery.PAIRING, 100));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000);
    }

    @Test
    public void foreignAddressIsIgnored() throws Exception {
        List<InetAddress> own = Arrays.asList(ip("192.168.1.20"));
        assertFalse(AdbDiscovery.isOwn(own, ip("192.168.1.50")));
        assertFalse(AdbDiscovery.isOwn(own, null));
        assertTrue(AdbDiscovery.isOwn(own, ip("192.168.1.20")));
    }

    @Test
    public void preferIpv4OverLinkLocalIpv6() throws Exception {
        InetAddress v6 = ip("fe80::1");
        InetAddress v4 = ip("192.168.1.20");
        assertEquals(v4, AdbDiscovery.preferIpv4(Arrays.asList(v6, v4), v6));
    }

    @Test
    public void preferIpv4KeepsWhatIsAlreadyBest() throws Exception {
        InetAddress v6 = ip("fe80::1");
        InetAddress v4 = ip("192.168.1.20");
        assertSame(v4, AdbDiscovery.preferIpv4(Arrays.asList(v6, v4), v4));
        assertSame(v6, AdbDiscovery.preferIpv4(Arrays.asList(v6), v6)); // no IPv4 of ours
        InetAddress foreign = ip("fe80::2");
        assertSame(foreign, AdbDiscovery.preferIpv4(Arrays.asList(v6, v4), foreign));
    }
}
