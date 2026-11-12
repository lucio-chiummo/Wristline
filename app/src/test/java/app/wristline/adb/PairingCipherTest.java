package app.wristline.adb;

import static app.wristline.adb.Ed25519Test.hex;
import static app.wristline.adb.Ed25519Test.hexBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.Test;

/** adb's pairing_auth AES-128-GCM: HKDF key, counter nonces, one direction each way. */
public class PairingCipherTest {
    private static final byte[] KEY64 = new byte[64];
    private static final byte[] MSG = "peer info goes here".getBytes(StandardCharsets.US_ASCII);

    static {
        for (int i = 0; i < KEY64.length; i++) {
            KEY64[i] = (byte) (i * 7 + 3);
        }
    }

    @Test
    public void hkdfRfc5869TestCase1() {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] okm = PairingCipher.hkdfSha256(ikm, hexBytes("000102030405060708090a0b0c"),
                hexBytes("f0f1f2f3f4f5f6f7f8f9"), 42);
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
                hex(okm));
    }

    @Test
    public void hkdfRfc5869TestCase3EmptySaltAndInfo() {
        byte[] ikm = new byte[22];
        Arrays.fill(ikm, (byte) 0x0b);
        byte[] okm = PairingCipher.hkdfSha256(ikm, new byte[0], new byte[0], 42);
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8",
                hex(okm));
    }

    @Test
    public void encryptThenDecryptRoundTrips() throws Exception {
        PairingCipher a = new PairingCipher(KEY64);
        PairingCipher b = new PairingCipher(KEY64);
        assertArrayEquals(MSG, b.decrypt(a.encrypt(MSG)));
    }

    @Test
    public void ciphertextIsPlainPlus16() throws Exception {
        assertEquals(MSG.length + 16, new PairingCipher(KEY64).encrypt(MSG).length);
    }

    @Test
    public void nonceCountersAdvanceIndependently() throws Exception {
        PairingCipher a = new PairingCipher(KEY64);
        byte[] first = a.encrypt(MSG);
        byte[] second = a.encrypt(MSG);
        PairingCipher inOrder = new PairingCipher(KEY64);
        assertArrayEquals(MSG, inOrder.decrypt(first));
        assertArrayEquals(MSG, inOrder.decrypt(second));

        PairingCipher outOfOrder = new PairingCipher(KEY64);
        assertThrows(GeneralSecurityException.class, () -> outOfOrder.decrypt(second));

        // Encrypting does not move the decrypt counter.
        PairingCipher both = new PairingCipher(KEY64);
        both.encrypt(MSG);
        assertArrayEquals(MSG, both.decrypt(first));
    }

    @Test
    public void badTagThrows() throws Exception {
        byte[] sealed = new PairingCipher(KEY64).encrypt(MSG);
        sealed[sealed.length - 1] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> new PairingCipher(KEY64).decrypt(sealed));
    }

    @Test
    public void firstNonceIsZeroCounter() throws Exception {
        byte[] aesKey = PairingCipher.hkdfSha256(KEY64, new byte[0],
                "adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.US_ASCII), 16);
        Cipher manual = Cipher.getInstance("AES/GCM/NoPadding");
        manual.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, new byte[12]));
        assertArrayEquals(manual.doFinal(MSG), new PairingCipher(KEY64).encrypt(MSG));
    }

    @Test
    public void secondNonceIsCounterOneLittleEndian() throws Exception {
        byte[] aesKey = PairingCipher.hkdfSha256(KEY64, new byte[0],
                "adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = new byte[12];
        nonce[0] = 1;
        Cipher manual = Cipher.getInstance("AES/GCM/NoPadding");
        manual.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, nonce));
        PairingCipher c = new PairingCipher(KEY64);
        c.encrypt(MSG);
        assertArrayEquals(manual.doFinal(MSG), c.encrypt(MSG));
    }
}
