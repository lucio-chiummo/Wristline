package app.wristline.adb;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.junit.Test;

/** The minimal self-signed certificate parses as X.509 and verifies under its own key. */
public class SelfSignedTest {
    @Test
    public void parsesAndVerifiesWithItsOwnKey() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair pair = g.generateKeyPair();
        Date notBefore = new Date(1_700_000_000_000L);
        Date notAfter = new Date(1_700_000_000_000L + 20L * 365 * 86_400_000L);

        X509Certificate cert = SelfSigned.certificate(pair, "Wristline", notBefore, notAfter);

        cert.verify(pair.getPublic());
        assertEquals(3, cert.getVersion());
        assertEquals(BigInteger.ONE, cert.getSerialNumber());
        assertEquals("SHA256withRSA", cert.getSigAlgName());
        assertEquals("CN=Wristline", cert.getSubjectX500Principal().getName());
        assertEquals("CN=Wristline", cert.getIssuerX500Principal().getName());
        assertEquals(notBefore, cert.getNotBefore());
        assertEquals(notAfter, cert.getNotAfter());
        assertEquals(pair.getPublic(), cert.getPublicKey());
    }

    /** RFC 5280: dates from 2050 on are GeneralizedTime, not UTCTime. */
    @Test
    public void datesFrom2050AreGeneralizedTime() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair pair = g.generateKeyPair();
        Date notBefore = new Date(1_700_000_000_000L);
        Date notAfter = new Date(2_600_000_000_000L); // 2052

        X509Certificate cert = SelfSigned.certificate(pair, "fake-adbd", notBefore, notAfter);

        cert.verify(pair.getPublic());
        assertEquals(notAfter, cert.getNotAfter());
        assertEquals("CN=fake-adbd", cert.getSubjectX500Principal().getName());
    }
}
