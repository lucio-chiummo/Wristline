package app.wristline.adb;

import java.math.BigInteger;

/**
 * Edwards25519 point arithmetic, just enough for SPAKE2 in adb Wireless-debugging pairing. The
 * JDK has no public API for raw curve points, and the app takes no third-party crypto library,
 * so the group maths lives here in affine coordinates on {@link BigInteger}.
 *
 * <p>Not constant time: {@link #mul} is double-and-add and {@code modInverse}/{@code modPow}
 * branch on their inputs. That is acceptable here because every secret scalar is drawn fresh for
 * one pairing attempt, used once, and the exchange runs on the phone itself (to its own adbd, at
 * the phone's own Wi-Fi address), so there is no repeated-query timing oracle for an attacker to
 * measure.
 */
final class Ed25519 {
    /** Field prime 2^255 - 19. */
    static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));

    /** Order of the prime-order subgroup, 2^252 + 27742317777372353535851937790883648493. */
    static final BigInteger L = BigInteger.ONE.shiftLeft(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));

    /** Curve constant d = -121665 / 121666 mod p. */
    static final BigInteger D = BigInteger.valueOf(-121665)
            .multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);

    /** sqrt(-1) mod p = 2^((p-1)/4), used when recovering x. */
    private static final BigInteger SQRT_M1 =
            BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).shiftRight(2), P);

    /** A point in affine coordinates, both reduced mod p. */
    static final class Point {
        final BigInteger x;
        final BigInteger y;

        Point(BigInteger x, BigInteger y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Point)) {
                return false;
            }
            Point other = (Point) o;
            return x.equals(other.x) && y.equals(other.y);
        }

        @Override
        public int hashCode() {
            return 31 * x.hashCode() + y.hashCode();
        }
    }

    static final Point IDENTITY = new Point(BigInteger.ZERO, BigInteger.ONE);

    /** Base point from RFC 8032: y = 4/5, x even. */
    static final Point B = decode(baseEncoding());

    private Ed25519() {
    }

    private static byte[] baseEncoding() {
        BigInteger y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P);
        return encodeScalarLE(y, 32);
    }

    /** True when {@code p} satisfies -x^2 + y^2 = 1 + d x^2 y^2 (mod p). */
    static boolean onCurve(Point p) {
        BigInteger x2 = p.x.multiply(p.x).mod(P);
        BigInteger y2 = p.y.multiply(p.y).mod(P);
        BigInteger left = y2.subtract(x2).mod(P);
        BigInteger right = BigInteger.ONE.add(D.multiply(x2).multiply(y2)).mod(P);
        return left.equals(right);
    }

    /**
     * Twisted Edwards addition (a = -1). The formula is complete for this curve because d is not
     * a square mod p, so the denominators are never zero and it also doubles.
     */
    static Point add(Point a, Point b) {
        BigInteger x1y2 = a.x.multiply(b.y);
        BigInteger y1x2 = a.y.multiply(b.x);
        BigInteger y1y2 = a.y.multiply(b.y);
        BigInteger x1x2 = a.x.multiply(b.x);
        BigInteger dxy = D.multiply(x1x2).multiply(y1y2).mod(P);
        BigInteger x3 = x1y2.add(y1x2)
                .multiply(BigInteger.ONE.add(dxy).modInverse(P)).mod(P);
        BigInteger y3 = y1y2.add(x1x2)
                .multiply(BigInteger.ONE.subtract(dxy).mod(P).modInverse(P)).mod(P);
        return new Point(x3, y3);
    }

    static Point negate(Point p) {
        return new Point(P.subtract(p.x).mod(P), p.y);
    }

    static Point sub(Point a, Point b) {
        return add(a, negate(b));
    }

    /** k * p by double-and-add; k must be non-negative. Not constant time, see class comment. */
    static Point mul(BigInteger k, Point p) {
        if (k.signum() < 0) {
            throw new IllegalArgumentException("negative scalar");
        }
        Point result = IDENTITY;
        for (int i = k.bitLength() - 1; i >= 0; i--) {
            result = add(result, result);
            if (k.testBit(i)) {
                result = add(result, p);
            }
        }
        return result;
    }

    /** RFC 8032 encoding: 32 bytes, little-endian y, x parity in bit 255. */
    static byte[] encode(Point p) {
        byte[] out = encodeScalarLE(p.y, 32);
        if (p.x.testBit(0)) {
            out[31] |= (byte) 0x80;
        }
        return out;
    }

    /** RFC 8032 section 5.1.3 decoding; rejects malformed and off-curve encodings. */
    static Point decode(byte[] enc) {
        if (enc == null || enc.length != 32) {
            throw new IllegalArgumentException("point encoding must be 32 bytes");
        }
        byte[] copy = enc.clone();
        boolean sign = (copy[31] & 0x80) != 0;
        copy[31] &= 0x7f;
        BigInteger y = decodeScalarLE(copy);
        if (y.compareTo(P) >= 0) {
            throw new IllegalArgumentException("y not reduced");
        }
        BigInteger y2 = y.multiply(y).mod(P);
        BigInteger u = y2.subtract(BigInteger.ONE).mod(P);
        BigInteger v = D.multiply(y2).add(BigInteger.ONE).mod(P);
        BigInteger x2 = u.multiply(v.modInverse(P)).mod(P);
        BigInteger x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        if (!x.multiply(x).mod(P).equals(x2)) {
            x = x.multiply(SQRT_M1).mod(P);
            if (!x.multiply(x).mod(P).equals(x2)) {
                throw new IllegalArgumentException("not on the curve");
            }
        }
        if (x.signum() == 0 && sign) {
            throw new IllegalArgumentException("x = 0 with sign bit set");
        }
        if (x.testBit(0) != sign) {
            x = P.subtract(x);
        }
        return new Point(x, y);
    }

    /** Unsigned little-endian bytes to an integer. */
    static BigInteger decodeScalarLE(byte[] b) {
        byte[] be = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            be[i] = b[b.length - 1 - i];
        }
        return new BigInteger(1, be);
    }

    /** Non-negative integer to {@code len} little-endian bytes; must fit. */
    static byte[] encodeScalarLE(BigInteger k, int len) {
        if (k.signum() < 0 || k.bitLength() > 8 * len) {
            throw new IllegalArgumentException("scalar does not fit");
        }
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) k.shiftRight(8 * i).intValue();
        }
        return out;
    }
}
