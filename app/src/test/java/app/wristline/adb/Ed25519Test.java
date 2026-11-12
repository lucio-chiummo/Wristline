package app.wristline.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import app.wristline.adb.Ed25519.Point;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

import org.junit.Test;

/** The curve maths SPAKE2 rests on, checked against RFC 8032. */
public class Ed25519Test {
    @Test
    public void basePointIsOnCurve() {
        assertTrue(Ed25519.onCurve(Ed25519.B));
    }

    @Test
    public void encodeDecodeRoundTrip() {
        Point p = Ed25519.mul(BigInteger.valueOf(12345), Ed25519.B);
        assertEquals(p, Ed25519.decode(Ed25519.encode(p)));
    }

    @Test
    public void mulByOrderGivesIdentity() {
        Point id = Ed25519.mul(Ed25519.L, Ed25519.B);
        assertEquals(BigInteger.ZERO, id.x);
        assertEquals(BigInteger.ONE, id.y);
    }

    @Test
    public void addThenSubRestores() {
        Point a = Ed25519.mul(BigInteger.valueOf(7), Ed25519.B);
        Point b = Ed25519.mul(BigInteger.valueOf(11), Ed25519.B);
        assertEquals(a, Ed25519.sub(Ed25519.add(a, b), b));
    }

    @Test
    public void basePointEncodingMatchesRfc8032() {
        assertEquals("5866666666666666666666666666666666666666666666666666666666666666",
                hex(Ed25519.encode(Ed25519.B)));
    }

    @Test
    public void decodeRejectsOffCurve() {
        byte[] bad = new byte[32];
        bad[0] = 2;
        assertThrows(IllegalArgumentException.class, () -> Ed25519.decode(bad));
    }

    @Test
    public void decodeRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> Ed25519.decode(new byte[31]));
    }

    @Test
    public void decodeRejectsNonCanonicalY() {
        // y = p encodes as p's little-endian bytes; y must be < p.
        byte[] enc = Ed25519.encodeScalarLE(Ed25519.P, 32);
        assertThrows(IllegalArgumentException.class, () -> Ed25519.decode(enc));
    }

    @Test
    public void decodeRejectsNegativeZeroX() {
        // y = 1 gives x = 0; with the sign bit set the encoding is malformed.
        byte[] enc = new byte[32];
        enc[0] = 1;
        enc[31] = (byte) 0x80;
        assertThrows(IllegalArgumentException.class, () -> Ed25519.decode(enc));
    }

    @Test
    public void ed25519PublicKeyFromRfc8032Vector1() throws Exception {
        // RFC 8032 test 1: secret 9d61b19d...; a = clamp(SHA-512(secret)[0..32]); A = a*B
        byte[] secret = hexBytes("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
        byte[] h = MessageDigest.getInstance("SHA-512").digest(secret);
        byte[] a = Arrays.copyOf(h, 32);
        a[0] &= (byte) 248;
        a[31] &= 127;
        a[31] |= 64;
        assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
                hex(Ed25519.encode(Ed25519.mul(Ed25519.decodeScalarLE(a), Ed25519.B))));
    }

    @Test
    public void scalarRoundTrip() {
        BigInteger k = Ed25519.L.subtract(BigInteger.ONE);
        assertEquals(k, Ed25519.decodeScalarLE(Ed25519.encodeScalarLE(k, 32)));
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xff));
        }
        return sb.toString();
    }

    static byte[] hexBytes(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
