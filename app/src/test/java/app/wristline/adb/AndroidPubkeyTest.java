package app.wristline.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Base64;

import org.junit.BeforeClass;
import org.junit.Test;

/** The {@code android_pubkey} struct adbd stores in adb_keys when pairing succeeds. */
public class AndroidPubkeyTest {
    private static RSAPublicKey key;
    private static byte[] out;

    @BeforeClass
    public static void generate() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        key = (RSAPublicKey) g.generateKeyPair().getPublic();
        out = AndroidPubkey.encode(key);
    }

    @Test
    public void encodeIs524BytesWithLenAndExponent() {
        assertEquals(524, out.length);
        assertEquals(64L, le32(out, 0));
        assertEquals(65537L, le32(out, 520));
    }

    @Test
    public void modulusWordsAreLittleEndianLowWordFirst() {
        BigInteger mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
        assertEquals(key.getModulus().and(mask).longValue(), le32(out, 8));
        assertEquals(key.getModulus(), words(out, 8));
    }

    @Test
    public void n0invMatchesDefinition() {
        long n0inv = le32(out, 4);
        long n0 = le32(out, 8);
        assertEquals(0xFFFFFFFFL, (n0inv * n0) & 0xFFFFFFFFL);
    }

    @Test
    public void rrIs2Pow4096ModN() {
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(key.getModulus()), words(out, 8 + 256));
    }

    @Test
    public void lineHasBase64ThenSpaceThenName() {
        String line = AndroidPubkey.line(key, "wristline@X300Pro");
        assertTrue(line.matches("[A-Za-z0-9+/=]+ wristline@X300Pro"));
        String b64 = line.substring(0, line.indexOf(' '));
        assertEquals(java.util.Arrays.toString(out), java.util.Arrays.toString(Base64.getDecoder().decode(b64)));
    }

    static long le32(byte[] b, int off) {
        return (b[off] & 0xFFL) | (b[off + 1] & 0xFFL) << 8 | (b[off + 2] & 0xFFL) << 16 | (b[off + 3] & 0xFFL) << 24;
    }

    /** 64 little-endian words starting at {@code off}, lowest word first, as one number. */
    private static BigInteger words(byte[] b, int off) {
        BigInteger v = BigInteger.ZERO;
        for (int i = 63; i >= 0; i--) {
            v = v.shiftLeft(32).or(BigInteger.valueOf(le32(b, off + 4 * i)));
        }
        return v;
    }
}
