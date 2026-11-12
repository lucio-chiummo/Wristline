package app.wristline.adb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A minimal self-signed X.509 v3 certificate, DER written by hand: serial 1,
 * sha256WithRSAEncryption, subject = issuer = {@code CN=<cn>}, no extensions. Android has no
 * public certificate builder and Wristline takes no library for this; adbd only needs a
 * certificate carrying the key it learnt at pairing, so the smallest valid one is enough.
 */
final class SelfSigned {
    /** 1.2.840.113549.1.1.11 sha256WithRSAEncryption, with NULL parameters. */
    private static final byte[] SHA256_WITH_RSA = {
        0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00
    };
    /** 2.5.4.3 commonName. */
    private static final byte[] OID_CN = {0x06, 0x03, 0x55, 0x04, 0x03};

    private SelfSigned() {
    }

    static X509Certificate certificate(KeyPair pair, String cn, Date notBefore, Date notAfter)
            throws GeneralSecurityException {
        byte[] name = name(cn);
        byte[] tbs = tlv(0x30,
                tlv(0xA0, tlv(0x02, new byte[] {2})), // [0] EXPLICIT version v3
                tlv(0x02, new byte[] {1}), // serialNumber
                SHA256_WITH_RSA,
                name, // issuer
                tlv(0x30, time(notBefore), time(notAfter)),
                name, // subject
                pair.getPublic().getEncoded()); // SubjectPublicKeyInfo, already DER
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(tbs);
        byte[] signature = signer.sign();
        byte[] bits = new byte[signature.length + 1]; // leading 0 = no unused bits
        System.arraycopy(signature, 0, bits, 1, signature.length);
        byte[] der = tlv(0x30, tbs, SHA256_WITH_RSA, tlv(0x03, bits));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
    }

    private static byte[] name(String cn) {
        byte[] value = tlv(0x0C, cn.getBytes(StandardCharsets.UTF_8)); // UTF8String
        return tlv(0x30, tlv(0x31, tlv(0x30, OID_CN, value)));
    }

    /** RFC 5280 4.1.2.5: UTCTime through 2049, GeneralizedTime from 2050, both in UTC with seconds. */
    private static byte[] time(Date date) {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Calendar calendar = Calendar.getInstance(utc, Locale.US);
        calendar.setTime(date);
        boolean utcTime = calendar.get(Calendar.YEAR) >= 1950 && calendar.get(Calendar.YEAR) < 2050;
        SimpleDateFormat format = new SimpleDateFormat(utcTime ? "yyMMddHHmmss'Z'" : "yyyyMMddHHmmss'Z'", Locale.US);
        format.setTimeZone(utc);
        return tlv(utcTime ? 0x17 : 0x18, format.format(date).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] tlv(int tag, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            body.write(part, 0, part.length);
        }
        int length = body.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (length < 0x80) {
            out.write(length);
        } else {
            int bytes = 0;
            for (int l = length; l > 0; l >>= 8) {
                bytes++;
            }
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) {
                out.write(length >> (8 * i));
            }
        }
        out.write(body.toByteArray(), 0, length);
        return out.toByteArray();
    }
}
