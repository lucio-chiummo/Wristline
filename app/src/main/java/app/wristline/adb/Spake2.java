package app.wristline.adb;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * SPAKE2 over Ed25519, ported line by line from BoringSSL's {@code spake25519.cc} (Apache-2.0,
 * Copyright 2016 The BoringSSL Authors). adbd runs that exact code for Wireless-debugging pairing,
 * so every detail that changes the bytes on the wire or the derived key (cofactor shift, the
 * password-scalar compatibility step, transcript order) is kept as the C does it.
 *
 * <p>One instance per pairing attempt: {@link #generateMessage} once, then {@link #processMessage}
 * once. Nothing here logs; the key, scalars and password hash must never be logged by callers.
 */
final class Spake2 {
    enum Role { ALICE, BOB }

    /** Encoding of M from the comment in spake25519.cc (alice masks with M). */
    static final byte[] M_ENCODED = hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e");

    /** Encoding of N from the comment in spake25519.cc (bob masks with N). */
    static final byte[] N_ENCODED = hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778");

    private static final Ed25519.Point M = Ed25519.decode(M_ENCODED);
    private static final Ed25519.Point N = Ed25519.decode(N_ENCODED);
    private static final BigInteger TWO_256_MASK = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    private enum State { INIT, MSG_GENERATED, KEY_GENERATED }

    private final Role role;
    private final byte[] myName;
    private final byte[] theirName;
    private final BigInteger privateKey;
    private State state = State.INIT;
    private byte[] myMsg;
    private byte[] passwordHash;
    private BigInteger passwordScalar;

    /** Draws the private scalar from {@code random} the way SPAKE2_generate_msg does. */
    Spake2(Role role, byte[] myName, byte[] theirName, SecureRandom random) {
        this(role, myName, theirName, privateScalarFrom(random));
    }

    /** Takes an already reduced-and-shifted private scalar (32 bytes, little-endian); for tests. */
    Spake2(Role role, byte[] myName, byte[] theirName, byte[] privateScalar32) {
        if (privateScalar32.length != 32) {
            throw new IllegalArgumentException("private scalar must be 32 bytes");
        }
        this.role = role;
        this.myName = myName.clone();
        this.theirName = theirName.clone();
        this.privateKey = Ed25519.decodeScalarLE(privateScalar32);
    }

    private static byte[] privateScalarFrom(SecureRandom random) {
        byte[] tmp = new byte[64];
        random.nextBytes(tmp);
        // x25519_sc_reduce: the 64 bytes as a little-endian integer mod l.
        BigInteger reduced = Ed25519.decodeScalarLE(tmp).mod(Ed25519.L);
        Arrays.fill(tmp, (byte) 0);
        // left_shift_3 on the 32-byte array: times the cofactor 8, truncated to 256 bits.
        BigInteger shifted = reduced.shiftLeft(3).and(TWO_256_MASK);
        return Ed25519.encodeScalarLE(shifted, 32);
    }

    /**
     * BoringSSL's compatibility step: the reduced password scalar plus l, 2l, 4l depending on its
     * low bits, tested one after the other on the running sum exactly as the C does, so the result
     * is a multiple of 8 and equal to the reduced value mod l.
     */
    static BigInteger passwordScalar(byte[] passwordHash64) {
        BigInteger s = Ed25519.decodeScalarLE(passwordHash64).mod(Ed25519.L);
        BigInteger order = Ed25519.L;
        if (s.testBit(0)) {
            s = s.add(order);
        }
        order = order.shiftLeft(1);
        if (s.testBit(1)) {
            s = s.add(order);
        }
        order = order.shiftLeft(1);
        if (s.testBit(2)) {
            s = s.add(order);
        }
        return s;
    }

    /** P* = x*B + w*(M or N); returns its 32-byte encoding. Callable once. */
    byte[] generateMessage(byte[] password) {
        if (state != State.INIT) {
            throw new IllegalStateException("message already generated");
        }
        Ed25519.Point p = Ed25519.mul(privateKey, Ed25519.B);
        passwordHash = sha512().digest(password);
        passwordScalar = passwordScalar(passwordHash);
        Ed25519.Point mask = Ed25519.mul(passwordScalar, role == Role.ALICE ? M : N);
        myMsg = Ed25519.encode(Ed25519.add(p, mask));
        state = State.MSG_GENERATED;
        return myMsg.clone();
    }

    /** Unmasks the peer's message and returns the 64-byte SHA-512 key. Callable once. */
    byte[] processMessage(byte[] theirMsg) {
        if (state != State.MSG_GENERATED) {
            throw new IllegalStateException("generateMessage first, and only once");
        }
        if (theirMsg.length != 32) {
            throw new IllegalArgumentException("peer message must be 32 bytes");
        }
        Ed25519.Point qStar = Ed25519.decode(theirMsg);
        Ed25519.Point peersMask = Ed25519.mul(passwordScalar, role == Role.ALICE ? N : M);
        Ed25519.Point q = Ed25519.sub(qStar, peersMask);
        byte[] dhShared = Ed25519.encode(Ed25519.mul(privateKey, q));

        MessageDigest sha = sha512();
        if (role == Role.ALICE) {
            updateWithLengthPrefix(sha, myName);
            updateWithLengthPrefix(sha, theirName);
            updateWithLengthPrefix(sha, myMsg);
            updateWithLengthPrefix(sha, theirMsg);
        } else {
            updateWithLengthPrefix(sha, theirName);
            updateWithLengthPrefix(sha, myName);
            updateWithLengthPrefix(sha, theirMsg);
            updateWithLengthPrefix(sha, myMsg);
        }
        updateWithLengthPrefix(sha, dhShared);
        updateWithLengthPrefix(sha, passwordHash);
        byte[] key = sha.digest();

        Arrays.fill(dhShared, (byte) 0);
        Arrays.fill(passwordHash, (byte) 0);
        state = State.KEY_GENERATED;
        return key;
    }

    /** 8-byte little-endian length, then the data. */
    private static void updateWithLengthPrefix(MessageDigest sha, byte[] data) {
        long l = data.length;
        byte[] lenLe = new byte[8];
        for (int i = 0; i < 8; i++) {
            lenLe[i] = (byte) (l & 0xff);
            l >>>= 8;
        }
        sha.update(lenLe);
        sha.update(data);
    }

    private static MessageDigest sha512() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
