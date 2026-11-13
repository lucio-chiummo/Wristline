package app.wristline.adb;

import static app.wristline.adb.Ed25519Test.hex;
import static app.wristline.adb.Ed25519Test.hexBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import app.wristline.adb.Ed25519.Point;
import app.wristline.adb.Spake2.Role;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import org.junit.Test;

/** SPAKE2 as adbd runs it (BoringSSL spake25519.cc): agreement and the exact transcript. */
public class Spake2Test {
    // Verbatim from the comment above the precomputed tables in BoringSSL's spake25519.cc.
    private static final String N_X_FROM_COMMENT =
            "49918732221787544735331783592030787422991506689877079631459872391322455579424";
    private static final String N_Y_FROM_COMMENT =
            "54629554431565467720832445949441049581317094546788069926228343916274969994000";
    private static final String N_ENCODED_FROM_COMMENT =
            "10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778";
    private static final String M_X_FROM_COMMENT =
            "31406539342727633121250288103050113562375374900226415211311216773867585644232";
    private static final String M_Y_FROM_COMMENT =
            "21177308356423958466833845032658859666296341766942662650232962324899758529114";
    private static final String M_ENCODED_FROM_COMMENT =
            "5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e";

    // First 64 bytes (table entry 0 = 1*P as encodeint(x) || encodeint(y)) of each table.
    private static final String N_TABLE_ENTRY_0 =
            "201bc5b343177110441e73b3ae3fbf9ff544c8138fd101c28a1a6dea4d005d6e"
            + "10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778";
    private static final String M_TABLE_ENTRY_0 =
            "c8a663c597f1ee40ab6242ee256f326c752ca7d3bd323b1e119cbd04a9786f45"
            + "5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e";

    @Test
    public void mAndNMatchBoringSslComments() {
        Point m = Ed25519.decode(Spake2.M_ENCODED);
        assertEquals(new BigInteger(M_X_FROM_COMMENT), m.x);
        assertEquals(new BigInteger(M_Y_FROM_COMMENT), m.y);
        assertEquals(M_ENCODED_FROM_COMMENT, hex(Spake2.M_ENCODED));
        Point n = Ed25519.decode(Spake2.N_ENCODED);
        assertEquals(new BigInteger(N_X_FROM_COMMENT), n.x);
        assertEquals(new BigInteger(N_Y_FROM_COMMENT), n.y);
        assertEquals(N_ENCODED_FROM_COMMENT, hex(Spake2.N_ENCODED));
    }

    @Test
    public void tableEntryZeroMatchesComments() {
        assertEquals(M_TABLE_ENTRY_0, hex(Ed25519.encodeScalarLE(new BigInteger(M_X_FROM_COMMENT), 32))
                + hex(Ed25519.encodeScalarLE(new BigInteger(M_Y_FROM_COMMENT), 32)));
        assertEquals(N_TABLE_ENTRY_0, hex(Ed25519.encodeScalarLE(new BigInteger(N_X_FROM_COMMENT), 32))
                + hex(Ed25519.encodeScalarLE(new BigInteger(N_Y_FROM_COMMENT), 32)));
    }

    @Test
    public void passwordScalarHackPutsTheMaskInThePrimeOrderSubgroup() throws Exception {
        // M and N were found by hashing to the curve with no cofactor clearing, so they carry a
        // small-order component; that is why the compatibility step changes the mask point.
        Point m = Ed25519.decode(Spake2.M_ENCODED);
        Point n = Ed25519.decode(Spake2.N_ENCODED);
        BigInteger eightL = Ed25519.L.shiftLeft(3);
        assertEquals(Ed25519.IDENTITY, Ed25519.mul(eightL, m));
        assertEquals(Ed25519.IDENTITY, Ed25519.mul(eightL, n));
        assertFalse(Ed25519.IDENTITY.equals(Ed25519.mul(Ed25519.L, m)));
        assertFalse(Ed25519.IDENTITY.equals(Ed25519.mul(Ed25519.L, n)));
        byte[] h = MessageDigest.getInstance("SHA-512").digest("123456".getBytes(StandardCharsets.US_ASCII));
        BigInteger w = Spake2.passwordScalar(h);
        assertEquals(Ed25519.IDENTITY, Ed25519.mul(Ed25519.L, Ed25519.mul(w, m)));
        assertEquals(Ed25519.IDENTITY, Ed25519.mul(Ed25519.L, Ed25519.mul(w, n)));
    }

    @Test
    public void aliceAndBobAgreeOnTheKey() {
        byte[] pw = "123456".getBytes(StandardCharsets.US_ASCII);
        Spake2 a = new Spake2(Role.ALICE, name("adb pair client"), name("adb pair server"), new SecureRandom());
        Spake2 b = new Spake2(Role.BOB, name("adb pair server"), name("adb pair client"), new SecureRandom());
        byte[] ma = a.generateMessage(pw);
        byte[] mb = b.generateMessage(pw);
        assertEquals(32, ma.length);
        byte[] ka = a.processMessage(mb);
        assertEquals(64, ka.length);
        assertArrayEquals(ka, b.processMessage(ma));
    }

    @Test
    public void wrongPasswordGivesDifferentKey() {
        Spake2 a = new Spake2(Role.ALICE, name("adb pair client"), name("adb pair server"), new SecureRandom());
        Spake2 b = new Spake2(Role.BOB, name("adb pair server"), name("adb pair client"), new SecureRandom());
        byte[] ma = a.generateMessage("123456".getBytes(StandardCharsets.US_ASCII));
        byte[] mb = b.generateMessage("654321".getBytes(StandardCharsets.US_ASCII));
        assertFalse(Arrays.equals(a.processMessage(mb), b.processMessage(ma)));
    }

    @Test
    public void processRejectsOffCurveMessage() {
        Spake2 a = new Spake2(Role.ALICE, name("adb pair client"), name("adb pair server"), new SecureRandom());
        a.generateMessage("123456".getBytes(StandardCharsets.US_ASCII));
        byte[] bad = new byte[32];
        bad[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> a.processMessage(bad));
    }

    @Test
    public void callsOutOfOrderAreRejected() {
        byte[] pw = "123456".getBytes(StandardCharsets.US_ASCII);
        Spake2 a = new Spake2(Role.ALICE, name("c"), name("s"), new SecureRandom());
        assertThrows(IllegalStateException.class, () -> a.processMessage(new byte[32]));
        a.generateMessage(pw);
        assertThrows(IllegalStateException.class, () -> a.generateMessage(pw));
    }

    @Test
    public void passwordScalarIsMultipleOfEightAndCongruentModL() throws Exception {
        for (String pw : new String[] {"123456", "654321", "000000", "999999", "1", ""}) {
            byte[] h = MessageDigest.getInstance("SHA-512").digest(pw.getBytes(StandardCharsets.US_ASCII));
            BigInteger reduced = Ed25519.decodeScalarLE(h).mod(Ed25519.L);
            BigInteger w = Spake2.passwordScalar(h);
            assertEquals(0, w.mod(BigInteger.valueOf(8)).intValue());
            assertEquals(reduced, w.mod(Ed25519.L));
            assertTrue(w.bitLength() <= 256);
        }
    }

    @Test
    public void transcriptOrderMatchesBoringSsl() throws Exception {
        // "654321" needs an odd multiple of l in the compatibility step, which changes the mask
        // (M and N have order-2 components), so it would catch the step being skipped.
        for (String password : new String[] {"123456", "654321", "111111"}) {
            checkTranscript(password.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static void checkTranscript(byte[] pw) throws Exception {
        byte[] aliceName = name("adb pair client");
        byte[] bobName = name("adb pair server");
        BigInteger x = new BigInteger("1234567890abcdef1234567890abcdef", 16).shiftLeft(3);
        Spake2 a = new Spake2(Role.ALICE, aliceName, bobName, Ed25519.encodeScalarLE(x, 32));
        Spake2 b = new Spake2(Role.BOB, bobName, aliceName, new SecureRandom());
        byte[] ma = a.generateMessage(pw);
        byte[] mb = b.generateMessage(pw);

        // Independent recomputation from the C, using M/N from the comment encodings.
        Point m = Ed25519.decode(hexBytes(M_ENCODED_FROM_COMMENT));
        Point n = Ed25519.decode(hexBytes(N_ENCODED_FROM_COMMENT));
        byte[] pwHash = MessageDigest.getInstance("SHA-512").digest(pw);
        // The C's three conditional adds amount to: reduced + k*l with k in 0..7 making it a
        // multiple of 8. Search for that k here rather than reuse Spake2.passwordScalar.
        BigInteger reduced = Ed25519.decodeScalarLE(pwHash).mod(Ed25519.L);
        BigInteger w = null;
        for (int k = 0; k < 8; k++) {
            BigInteger c = reduced.add(Ed25519.L.multiply(BigInteger.valueOf(k)));
            if (c.mod(BigInteger.valueOf(8)).signum() == 0) {
                w = c;
            }
        }
        assertArrayEquals(Ed25519.encode(Ed25519.add(Ed25519.mul(x, Ed25519.B), Ed25519.mul(w, m))), ma);
        Point q = Ed25519.sub(Ed25519.decode(mb), Ed25519.mul(w, n));
        byte[] shared = Ed25519.encode(Ed25519.mul(x, q));
        ByteArrayOutputStream t = new ByteArrayOutputStream();
        for (byte[] v : new byte[][] {aliceName, bobName, ma, mb, shared, pwHash}) {
            t.write(Ed25519.encodeScalarLE(BigInteger.valueOf(v.length), 8));
            t.write(v);
        }
        byte[] expected = MessageDigest.getInstance("SHA-512").digest(t.toByteArray());

        byte[] ka = a.processMessage(mb);
        assertArrayEquals(expected, ka);
        assertArrayEquals(ka, b.processMessage(ma));
    }

    /** adb passes {@code sizeof} its name literals, so the trailing NUL is part of the name. */
    private static byte[] name(String s) {
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        return Arrays.copyOf(b, b.length + 1);
    }
}
