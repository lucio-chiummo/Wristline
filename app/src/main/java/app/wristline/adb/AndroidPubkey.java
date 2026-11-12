package app.wristline.adb;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * The public key as adbd stores it in {@code adb_keys}: the {@code android_pubkey} struct of
 * libcrypto_utils (length in words, {@code n0inv = -1/n mod 2^32}, the modulus and
 * {@code rr = (2^2048)^2 mod n} as little-endian 32-bit words lowest first, the exponent),
 * Base64 encoded and followed by a name. Derivation follows dadb's {@code AdbKeyPair.kt}
 * (Apache-2.0). Pairing sends this line; adbd then trusts the TLS certificate with this key.
 */
final class AndroidPubkey {
    private static final int WORDS = 64;
    private static final int SIZE = 4 + 4 + 4 * WORDS + 4 * WORDS + 4;
    private static final BigInteger TWO_32 = BigInteger.ONE.shiftLeft(32);

    private AndroidPubkey() {
    }

    /** The 524-byte struct; only 2048-bit keys fit it. */
    static byte[] encode(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        if (n.bitLength() != WORDS * 32) {
            throw new IllegalArgumentException("adb keys are 2048-bit RSA");
        }
        BigInteger n0inv = n.mod(TWO_32).modInverse(TWO_32).negate().mod(TWO_32);
        BigInteger rr = BigInteger.ONE.shiftLeft(2 * WORDS * 32).mod(n);
        ByteBuffer out = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(WORDS);
        out.putInt(n0inv.intValue());
        putWords(out, n);
        putWords(out, rr);
        out.putInt(key.getPublicExponent().intValue());
        return out.array();
    }

    /** {@code Base64(encode(key)) + " " + name}, no line breaks and no trailing newline. */
    static String line(RSAPublicKey key, String name) {
        return Base64.getEncoder().encodeToString(encode(key)) + " " + name;
    }

    private static void putWords(ByteBuffer out, BigInteger value) {
        for (int i = 0; i < WORDS; i++) {
            out.putInt(value.shiftRight(32 * i).intValue());
        }
    }
}
