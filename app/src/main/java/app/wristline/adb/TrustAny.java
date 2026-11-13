package app.wristline.adb;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * Accepts any certificate, client or server. Used only for this phone's own adbd, and it means
 * the connection is not authenticated at TLS level. At pairing the trust comes from the SPAKE2
 * code, not the certificate: a peer without the code cannot finish the exchange. At connect time
 * adbd is self-signed and Wristline pins nothing, so another app on the same phone could listen
 * on the phone's own address, pose as adbd and fake command output. It gains no secret and no
 * privilege by that: adbd's trust in Wristline comes from the pairing code, and Wristline sends
 * only its fixed app-op commands. Never use this for any other connection.
 */
final class TrustAny implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        // No check: see the class comment for what that does and does not allow.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        // No check: see the class comment for what that does and does not allow.
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
