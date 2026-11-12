package app.wristline.adb;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;

/**
 * The RSA key and self-signed certificate Wristline shows adbd. Pairing gives adbd the public key
 * (as {@link #peerInfo}); afterwards adbd lets in any TLS client whose certificate carries it.
 *
 * <p>The key lives in AndroidKeyStore so it never leaves secure hardware. Some keystores refuse
 * (or Robolectric has none); then a software key is kept in app-private storage, AES-GCM wrapped
 * by a keystore key when one can be made, else as is. Whichever exists first is kept, so the
 * identity adbd trusts does not change under it. Nothing here logs key material.
 */
public final class AdbKey {
    public enum Backend { KEYSTORE, SOFTWARE }

    private static final String TAG = "Wristline"; // RelayListener.TAG, which this package cannot see
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "wristline-adb";
    private static final String WRAP_ALIAS = "wristline-adb-wrap";
    private static final String CLIENT_ALIAS = "wristline";
    private static final String CN = "Wristline";
    private static final String DIR = "adb";
    private static final String KEY_FILE = "key.p8";
    private static final String CERT_FILE = "cert.der";
    private static final String[] LEGACY_FILES = {"adbkey.pem", "adbkey.pem.tmp"};
    private static final byte FORMAT_PLAIN = 0;
    private static final byte FORMAT_WRAPPED = 1;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final long DAY_MS = 86_400_000L;
    private static final long YEARS_20_MS = 20L * 365 * DAY_MS + 5 * DAY_MS;
    private static final int PEER_INFO_BYTES = 8192;
    private static final byte ADB_RSA_PUB_KEY = 0;

    private final Backend backend;
    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    private AdbKey(Backend backend, PrivateKey privateKey, X509Certificate certificate) {
        this.backend = backend;
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    /** The key, created on first call. Throws {@link IllegalStateException} if it cannot be read or made. */
    public static synchronized AdbKey load(Context context) {
        deleteLegacyPem(context);
        AdbKey key;
        try {
            if (new File(dir(context), KEY_FILE).exists()) {
                key = loadSoftware(context);
            } else {
                key = keystoreOrNull();
                if (key == null) {
                    key = createSoftware(context);
                }
            }
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            throw new IllegalStateException("adb key unavailable", e);
        }
        Log.i(TAG, "adb key backend: " + key.backend);
        return key;
    }

    /** Deletes the key and certificate: adbd will no longer recognise Wristline. */
    public static synchronized void forget(Context context) {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            store.deleteEntry(ALIAS);
            store.deleteEntry(WRAP_ALIAS);
        } catch (GeneralSecurityException | IOException | ProviderException e) {
            // No keystore: nothing stored there.
        }
        File dir = dir(context);
        new File(dir, KEY_FILE).delete();
        new File(dir, KEY_FILE + ".tmp").delete();
        new File(dir, CERT_FILE).delete();
        dir.delete();
    }

    public static boolean exists(Context context) {
        if (new File(dir(context), KEY_FILE).exists()) {
            return true;
        }
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            return store.containsAlias(ALIAS);
        } catch (GeneralSecurityException | IOException | ProviderException e) {
            return false;
        }
    }

    public Backend backend() {
        return backend;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return (RSAPublicKey) certificate.getPublicKey();
    }

    /** Name adbd shows under Paired devices; only characters adb's key line format is safe with. */
    public String peerName() {
        String model = Build.MODEL == null ? "" : Build.MODEL;
        return "wristline@" + model.replaceAll("[^A-Za-z0-9_-]", "");
    }

    /** The PeerInfo sent during pairing: type ADB_RSA_PUB_KEY, then the adb key line, NUL padded. */
    public byte[] peerInfo() {
        byte[] line = AndroidPubkey.line(publicKey(), peerName()).getBytes(StandardCharsets.US_ASCII);
        if (line.length > PEER_INFO_BYTES - 2) {
            throw new IllegalStateException("adb key line too long");
        }
        byte[] out = new byte[PEER_INFO_BYTES];
        out[0] = ADB_RSA_PUB_KEY;
        System.arraycopy(line, 0, out, 1, line.length);
        return out;
    }

    /**
     * Offers this certificate for every client-auth request. adbd's CertificateRequest lists no
     * issuer we would match, so the issuers are ignored on purpose.
     */
    public X509KeyManager keyManager() {
        X509Certificate[] chain = {certificate};
        return new X509ExtendedKeyManager() {
            @Override
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return CLIENT_ALIAS;
            }

            @Override
            public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
                return CLIENT_ALIAS;
            }

            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return new String[] {CLIENT_ALIAS};
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                return CLIENT_ALIAS.equals(alias) ? chain.clone() : null;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                return CLIENT_ALIAS.equals(alias) ? privateKey : null;
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return null;
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return null;
            }
        };
    }

    /**
     * The keystore key, made if missing; null when this device's keystore (or Robolectric) cannot
     * make one. A key that exists but cannot be read throws instead: falling back would silently
     * swap the identity adbd trusts.
     */
    private static AdbKey keystoreOrNull() throws GeneralSecurityException, IOException {
        KeyStore store;
        try {
            store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            if (!store.containsAlias(ALIAS)) {
                long now = System.currentTimeMillis();
                KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE);
                // Android's TLS stack (Conscrypt) does the TLS signature on a keystore key through a
                // raw private-key operation (Cipher RSA/ECB/NoPadding for TLS 1.3 PSS, where it pads
                // itself; RSA/ECB/PKCS1Padding for TLS 1.2), so the key must allow DECRYPT with
                // DIGEST_NONE and those encryption paddings, not just SHA256 signatures.
                generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
                        .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                                KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE,
                                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                        .setCertificateSubject(new X500Principal("CN=" + CN))
                        .setCertificateNotBefore(new Date(now - DAY_MS))
                        .setCertificateNotAfter(new Date(now + YEARS_20_MS))
                        .build());
                generator.generateKeyPair();
            }
        } catch (GeneralSecurityException | IOException | ProviderException e) {
            return null;
        }
        PrivateKey key = (PrivateKey) store.getKey(ALIAS, null);
        Certificate cert = store.getCertificate(ALIAS);
        if (key == null || !(cert instanceof X509Certificate)) {
            throw new UnrecoverableKeyException("adb keystore entry incomplete");
        }
        return new AdbKey(Backend.KEYSTORE, key, (X509Certificate) cert);
    }

    private static AdbKey createSoftware(Context context) throws GeneralSecurityException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        KeyPair pair = generator.generateKeyPair();
        long now = System.currentTimeMillis();
        X509Certificate cert = SelfSigned.certificate(pair, CN, new Date(now - DAY_MS), new Date(now + YEARS_20_MS));

        byte[] pkcs8 = pair.getPrivate().getEncoded();
        SecretKey wrap = wrapKeyOrNull();
        byte[] stored;
        if (wrap != null) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrap);
            byte[] iv = cipher.getIV();
            byte[] sealed = cipher.doFinal(pkcs8);
            stored = ByteBuffer.allocate(1 + iv.length + sealed.length)
                    .put(FORMAT_WRAPPED).put(iv).put(sealed).array();
        } else {
            stored = ByteBuffer.allocate(1 + pkcs8.length).put(FORMAT_PLAIN).put(pkcs8).array();
        }
        Arrays.fill(pkcs8, (byte) 0);

        File dir = dir(context);
        // Certificate first: key.p8 appearing (by rename) is what marks the pair complete.
        write(new File(dir, CERT_FILE), cert.getEncoded());
        File tmp = new File(dir, KEY_FILE + ".tmp");
        write(tmp, stored);
        if (!tmp.renameTo(new File(dir, KEY_FILE))) {
            tmp.delete();
            throw new IOException("could not store adb key");
        }
        return new AdbKey(Backend.SOFTWARE, pair.getPrivate(), cert);
    }

    private static AdbKey loadSoftware(Context context) throws GeneralSecurityException, IOException {
        File dir = dir(context);
        byte[] stored = Files.readAllBytes(new File(dir, KEY_FILE).toPath());
        byte[] pkcs8;
        if (stored.length > 1 && stored[0] == FORMAT_PLAIN) {
            pkcs8 = Arrays.copyOfRange(stored, 1, stored.length);
        } else if (stored.length > 1 + GCM_IV_BYTES && stored[0] == FORMAT_WRAPPED) {
            SecretKey wrap = existingWrapKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, wrap,
                    new GCMParameterSpec(GCM_TAG_BITS, stored, 1, GCM_IV_BYTES));
            pkcs8 = cipher.doFinal(stored, 1 + GCM_IV_BYTES, stored.length - 1 - GCM_IV_BYTES);
        } else {
            throw new IOException("unknown adb key format");
        }
        Arrays.fill(stored, (byte) 0);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        Arrays.fill(pkcs8, (byte) 0);
        byte[] der = Files.readAllBytes(new File(dir, CERT_FILE).toPath());
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(der));
        return new AdbKey(Backend.SOFTWARE, key, cert);
    }

    /** A fresh keystore AES-256-GCM key to wrap the software key with; null when the keystore cannot. */
    private static SecretKey wrapKeyOrNull() {
        try {
            KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            store.deleteEntry(WRAP_ALIAS);
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(WRAP_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return generator.generateKey();
        } catch (GeneralSecurityException | IOException | ProviderException e) {
            return null;
        }
    }

    private static SecretKey existingWrapKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        SecretKey key = (SecretKey) store.getKey(WRAP_ALIAS, null);
        if (key == null) {
            // Keystore cleared under us: the wrapped key cannot be read any more.
            throw new UnrecoverableKeyException("adb key wrapping key missing");
        }
        return key;
    }

    private static void write(File file, byte[] data) throws IOException {
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.getFD().sync();
        }
    }

    /** Older versions left a PEM ADB key here; it has no use now. */
    private static void deleteLegacyPem(Context context) {
        File dir = dir(context);
        for (String name : LEGACY_FILES) {
            new File(dir, name).delete();
        }
    }

    private static File dir(Context context) {
        return new File(context.getFilesDir(), DIR);
    }
}
