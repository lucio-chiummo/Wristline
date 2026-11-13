package app.wristline.adb;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

/** Key material for the loopback TLS 1.3 fakes of adbd that the pairing and transport tests stand up. */
final class TlsTestSupport {
    private TlsTestSupport() {
    }

    /** Key managers offering a day-valid self-signed certificate for {@code pair}. */
    static KeyManager[] keyManagers(KeyPair pair, String cn) throws Exception {
        long now = System.currentTimeMillis();
        X509Certificate cert = SelfSigned.certificate(pair, cn, new Date(now - 86_400_000L), new Date(now + 86_400_000L));
        char[] pass = "test".toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, pass);
        store.setKeyEntry("k", pair.getPrivate(), pass, new Certificate[] {cert});
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, pass);
        return factory.getKeyManagers();
    }

    static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
