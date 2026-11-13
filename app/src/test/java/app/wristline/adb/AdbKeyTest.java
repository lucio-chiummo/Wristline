package app.wristline.adb;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.file.Files;
import java.security.Principal;
import java.security.Signature;

import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;

/** Robolectric has no AndroidKeyStore, so this is the software backend plus what both share. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class AdbKeyTest {
    private Application ctx;

    @Before
    public void setUp() {
        ctx = RuntimeEnvironment.getApplication();
    }

    @Test
    public void loadCreatesKeyAndCertificateOnce() {
        assertFalse(AdbKey.exists(ctx));
        AdbKey a = AdbKey.load(ctx);
        AdbKey b = AdbKey.load(ctx);

        assertEquals(a.publicKey(), b.publicKey());
        assertEquals(a.privateKey(), b.privateKey());
        assertTrue(AdbKey.exists(ctx));
        assertEquals("CN=Wristline", a.certificate().getSubjectX500Principal().getName());
        assertEquals(AdbKey.Backend.SOFTWARE, a.backend());
        assertEquals(2048, a.publicKey().getModulus().bitLength());
    }

    @Test
    public void certificateIsSelfSignedWithItsOwnKey() throws Exception {
        AdbKey k = AdbKey.load(ctx);
        k.certificate().verify(k.publicKey());
        assertEquals(k.publicKey(), k.certificate().getPublicKey());
    }

    @Test
    public void certificateIsValidNowAndForYears() throws Exception {
        AdbKey k = AdbKey.load(ctx);
        long now = System.currentTimeMillis();
        k.certificate().checkValidity();
        assertTrue(k.certificate().getNotAfter().getTime() > now + 19L * 365 * 86_400_000L);
    }

    @Test
    public void privateKeySignsForThePublicKey() throws Exception {
        AdbKey k = AdbKey.load(ctx);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(k.privateKey());
        s.update(new byte[] {1, 2, 3});
        byte[] sig = s.sign();
        s.initVerify(k.publicKey());
        s.update(new byte[] {1, 2, 3});
        assertTrue(s.verify(sig));
    }

    @Test
    public void softwareKeyIsStoredPlainWhenNoWrappingKey() throws Exception {
        AdbKey.load(ctx);
        byte[] stored = Files.readAllBytes(new File(ctx.getFilesDir(), "adb/key.p8").toPath());
        assertEquals(0, stored[0]);
        assertTrue(new File(ctx.getFilesDir(), "adb/cert.der").exists());
    }

    @Test
    public void forgetDeletesEverything() {
        AdbKey first = AdbKey.load(ctx);
        AdbKey.forget(ctx);

        assertFalse(AdbKey.exists(ctx));
        assertFalse(new File(ctx.getFilesDir(), "adb/key.p8").exists());
        assertFalse(new File(ctx.getFilesDir(), "adb/cert.der").exists());
        assertNotEquals(first.publicKey(), AdbKey.load(ctx).publicKey());
    }

    @Test
    public void peerInfoLayout() {
        AdbKey k = AdbKey.load(ctx);
        byte[] p = k.peerInfo();

        assertEquals(8192, p.length);
        assertEquals(0, p[0]);
        int nul = indexOfNul(p, 1);
        String line = new String(p, 1, nul - 1, US_ASCII);
        assertEquals(AndroidPubkey.line(k.publicKey(), k.peerName()), line);
        for (int i = nul; i < p.length; i++) {
            assertEquals(0, p[i]);
        }
    }

    @Test
    public void peerNameKeepsOnlySafeCharacters() {
        assertEquals("wristline@" + Build.MODEL.replaceAll("[^A-Za-z0-9_-]", ""), AdbKey.load(ctx).peerName());
    }

    @Test
    public void keyManagerAlwaysChoosesWristline() {
        AdbKey k = AdbKey.load(ctx);
        X509KeyManager km = k.keyManager();

        assertEquals("wristline", km.chooseClientAlias(new String[] {"RSA"}, new Principal[] {new X500Principal("CN=fake")}, null));
        assertEquals("wristline", km.chooseClientAlias(null, null, null));
        assertEquals(1, km.getCertificateChain("wristline").length);
        assertSame(k.certificate(), km.getCertificateChain("wristline")[0]);
        assertSame(k.privateKey(), km.getPrivateKey("wristline"));
    }

    @Test
    public void loadDeletesLegacyPemFile() throws Exception {
        File dir = new File(ctx.getFilesDir(), "adb");
        dir.mkdirs();
        File pem = new File(dir, "adbkey.pem");
        File tmp = new File(dir, "adbkey.pem.tmp");
        Files.write(pem.toPath(), new byte[] {1, 2, 3});
        Files.write(tmp.toPath(), new byte[] {1, 2, 3});

        AdbKey.load(ctx);

        assertFalse(pem.exists());
        assertFalse(tmp.exists());
    }

    private static int indexOfNul(byte[] b, int from) {
        for (int i = from; i < b.length; i++) {
            if (b[i] == 0) {
                return i;
            }
        }
        return -1;
    }
}
