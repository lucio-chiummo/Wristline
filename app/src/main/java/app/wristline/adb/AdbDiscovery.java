package app.wristline.adb;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.wristline.SerialQueue;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Finds this phone's own Wireless debugging services on the Wi-Fi network (mDNS). */
final class AdbDiscovery {
    static final String TAG = "Wristline"; // RelayListener.TAG, which this package cannot see
    static final String PAIRING = "_adb-tls-pairing._tcp";
    static final String CONNECT = "_adb-tls-connect._tcp";

    private AdbDiscovery() {
    }

    /** A Wi-Fi network carrying this phone's own address; a VPN over Wi-Fi only has the tunnel's. */
    static boolean isPlainWifi(boolean wifi, boolean vpn) {
        return wifi && !vpn;
    }

    @SuppressWarnings("deprecation") // getAllNetworks: the callback replacement is overkill for a one-off check
    private static List<Network> wifiNetworks(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        List<Network> wifi = new ArrayList<>();
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && isPlainWifi(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))) {
                wifi.add(network);
            }
        }
        return wifi;
    }

    /** True when the phone is on Wi-Fi; Wireless debugging only works there. */
    static boolean onWifi(Context context) {
        return !wifiNetworks(context).isEmpty();
    }

    private static List<InetAddress> ownAddresses(Context context) {
        List<InetAddress> own = new ArrayList<>();
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        for (Network network : wifiNetworks(context)) {
            LinkProperties link = cm.getLinkProperties(network);
            if (link != null) {
                for (LinkAddress address : link.getLinkAddresses()) {
                    own.add(address.getAddress());
                }
            }
        }
        return own;
    }

    /** Whether the service host is one of this phone's own Wi-Fi addresses. */
    static boolean isOwn(List<InetAddress> own, InetAddress host) {
        return host != null && own.contains(host);
    }

    /**
     * Connect to the phone's own Wi-Fi IPv4 address rather than a scoped IPv6 link-local one or
     * loopback: those resolve to the same phone but are fragile to dial.
     */
    static InetAddress preferIpv4(List<InetAddress> own, InetAddress resolved) {
        if (!isOwn(own, resolved) || resolved instanceof Inet4Address) {
            return resolved;
        }
        for (InetAddress address : own) {
            if (address instanceof Inet4Address) {
                return address;
            }
        }
        return resolved;
    }

    /**
     * Blocking: the first service of this type that this phone itself advertises (other phones on the
     * same Wi-Fi advertise too), or null after the timeout.
     */
    @SuppressWarnings("deprecation") // resolveService: its replacement needs API 34
    static InetSocketAddress find(Context context, String serviceType, long timeoutMs) {
        if (!onWifi(context)) {
            return null;
        }
        List<InetAddress> own = ownAddresses(context);
        NsdManager nsd = context.getSystemService(NsdManager.class);
        AtomicReference<InetSocketAddress> found = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        // Android 12/13 resolve one service at a time; others on the same Wi-Fi may be found first.
        SerialQueue<NsdServiceInfo> resolves = new SerialQueue<>();
        Handler main = new Handler(Looper.getMainLooper());
        NsdManager.ResolveListener[] resolver = new NsdManager.ResolveListener[1];
        resolver[0] = new NsdManager.ResolveListener() {
            @Override
            public void onServiceResolved(NsdServiceInfo info) {
                InetAddress host = info.getHost();
                if (isOwn(own, host) && found.compareAndSet(null,
                        new InetSocketAddress(preferIpv4(own, host), info.getPort()))) {
                    done.countDown();
                }
                main.post(this::next);
            }

            @Override
            public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                Log.d(TAG, "mDNS resolve failed: " + errorCode);
                main.post(this::next);
            }

            private void next() {
                NsdServiceInfo next = resolves.done();
                if (next != null && found.get() == null) {
                    nsd.resolveService(next, resolver[0]);
                }
            }
        };

        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo service) {
                main.post(() -> {
                    NsdServiceInfo now = resolves.offer(service);
                    if (now != null) {
                        nsd.resolveService(now, resolver[0]);
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
            }

            @Override
            public void onDiscoveryStarted(String type) {
            }

            @Override
            public void onDiscoveryStopped(String type) {
            }

            @Override
            public void onStartDiscoveryFailed(String type, int errorCode) {
                Log.w(TAG, "mDNS discovery failed: " + errorCode);
                done.countDown();
            }

            @Override
            public void onStopDiscoveryFailed(String type, int errorCode) {
            }
        };

        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
        try {
            done.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                nsd.stopServiceDiscovery(listener);
            } catch (RuntimeException ignored) {
                // Discovery never started.
            }
        }
        return found.get();
    }
}
