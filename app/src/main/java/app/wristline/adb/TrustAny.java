package app.wristline.adb;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * Accepts any certificate, client or server. Safe here only because of how adb works: at pairing
 * the trust comes from the SPAKE2 code, not the certificate (a peer without the code cannot
 * finish the exchange); at connect time adbd is self-signed, so no CA check is possible, but the
 * socket is only ever opened to this phone's own address found through mDNS, and adbd proves
 * itself by completing the adb handshake after TLS. Never use this for any other connection.
 */
final class TrustAny implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // Trust comes from the SPAKE2 code (pairing) or the own-address rule (connect); see above.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        // Trust comes from the SPAKE2 code (pairing) or the own-address rule (connect); see above.
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
