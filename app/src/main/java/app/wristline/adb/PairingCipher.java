package app.wristline.adb;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * adb's pairing_auth cipher (adb/pairing_auth/aes_128_gcm.cpp): AES-128-GCM keyed by HKDF-SHA256
 * of the SPAKE2 key, nonce = 64-bit little-endian message counter then 4 zero bytes, one counter
 * per direction. Not thread-safe; one instance per pairing attempt. Never log what passes here.
 */
final class PairingCipher {
    private static final byte[] INFO = "adb pairing_auth aes-128-gcm key".getBytes(StandardCharsets.US_ASCII);
    private static final int KEY_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int SHA256_BYTES = 32;

    private final SecretKeySpec key;
    private long encryptCounter;
    private long decryptCounter;

    PairingCipher(byte[] spake2Key) {
        byte[] raw = hkdfSha256(spake2Key, new byte[0], INFO, KEY_BYTES);
        key = new SecretKeySpec(raw, "AES");
        Arrays.fill(raw, (byte) 0);
    }

    byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        byte[] out = run(Cipher.ENCRYPT_MODE, encryptCounter, plain);
        encryptCounter++;
        return out;
    }

    /** Throws {@link GeneralSecurityException} on a bad tag (wrong code, or out-of-order message). */
    byte[] decrypt(byte[] sealed) throws GeneralSecurityException {
        byte[] out = run(Cipher.DECRYPT_MODE, decryptCounter, sealed);
        decryptCounter++;
        return out;
    }

    private byte[] run(int mode, long counter, byte[] input) throws GeneralSecurityException {
        byte[] nonce = new byte[NONCE_BYTES];
        for (int i = 0; i < 8; i++) {
            nonce[i] = (byte) (counter >>> (8 * i));
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        return cipher.doFinal(input);
    }

    /** RFC 5869 HKDF with HMAC-SHA256; an empty salt means HashLen zero bytes, as the RFC says. */
    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) {
        if (length < 0 || length > 255 * SHA256_BYTES) {
            throw new IllegalArgumentException("bad HKDF length");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // SecretKeySpec refuses an empty key, so spell out the RFC's default salt.
            mac.init(new SecretKeySpec(salt.length == 0 ? new byte[SHA256_BYTES] : salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] out = new byte[length];
            byte[] t = new byte[0];
            int done = 0;
            for (int i = 1; done < length; i++) {
                mac.update(t);
                mac.update(info);
                mac.update((byte) i);
                t = mac.doFinal();
                int n = Math.min(t.length, length - done);
                System.arraycopy(t, 0, out, done, n);
                done += n;
            }
            Arrays.fill(prk, (byte) 0);
            Arrays.fill(t, (byte) 0);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
