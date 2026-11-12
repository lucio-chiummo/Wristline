# On-phone voice setup (in-house adb client) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user turn on voice through the watch for WhatsApp, Telegram and Signal from the phone alone: Wristline pairs with the phone's own Wireless debugging, connects over TLS and runs the app-op commands itself.

**Architecture:** New package `app.wristline.adb` holds everything that speaks adb: Ed25519/SPAKE2 maths, the RSA key and its adb public-key encoding, the pairing client (TLS 1.3 + SPAKE2 + AES-GCM frames), the connect transport (CNXN/STLS/shell streams), mDNS discovery, and one facade `AdbShell`. The user flow (consent, guide screen, foreground setup service, pairing-code notification, repair, turn off) is ported from Wristline 1.2's own code at `/Users/luciochiummo/Desktop/CallRelay-public` (commit 6361f81, same package `app.wristline`, the owner's code), re-pointed at the new facade. No new Gradle dependency.

**Tech Stack:** Java 21, Android minSdk 31 / targetSdk 36, `java.security` + `javax.crypto` + `javax.net.ssl` + `android.net.ssl.SSLSockets`, `AndroidKeyStore`, `NsdManager`, JUnit 4 + Robolectric 4.17 (`@Config(sdk = 36)`).

**Spec:** `docs/superpowers/specs/2026-11-12-on-phone-voice-setup-design.md` (read it first; it carries the protocol constants and the owner's constraints).

## Global Constraints

- No new Gradle dependency, no change to `gradle/verification-metadata.xml`. No GPL/LGPL code read or copied: references are the spec, BoringSSL's `spake25519.cc` (Apache-2.0, copy at `/private/tmp/claude-501/-Users-luciochiummo-Desktop-Wristline/122b1ea2-7e66-4822-93fb-f3cc9a604a3e/scratchpad/refs/spake25519.cc`, or `https://raw.githubusercontent.com/google/boringssl/main/crypto/curve25519/spake25519.cc`), dadb's `AdbKeyPair.kt` (Apache-2.0, same `refs/` folder), AOSP adb sources, and Wristline 1.2's own code.
- No hidden or non-SDK API, no reflection into the platform. `Settings.Global.getInt(resolver, "adb_wifi_enabled", 0)` is allowed as a hint only.
- Never log or record the pairing code, keys, SPAKE2 messages, exported keying material, or shell output. `Log.w` of an exception class and message is fine; `Diagnostics.record` detail strings are fixed reason codes.
- Shell commands are only the strings `AppOpsCommands` returns. No command text built from user input.
- Sockets only to this phone's own Wi-Fi addresses, found by `AdbDiscovery`; never to a host name; every socket has `connect` and `SO_TIMEOUT` set. Timeouts: discovery step 3000 ms, connect 4000 ms, socket read 15000 ms, pairing 20000 ms, whole setup 165000 ms.
- All user-visible strings in `values/strings.xml` and `values-it/strings.xml`, same keys.
- Tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests '<Class>'` must pass; the full suite before each commit of a task.
- Commit messages in the repo's style (imperative, no trailer except the Co-Authored-By line the session requires).

## Review Focus

- A phone on Wi-Fi plus a VPN: `AdbDiscovery.onWifi` must be false (a VPN hides the own address); test in Task 4.
- Another phone on the same Wi-Fi advertising `_adb-tls-pairing._tcp`: must be ignored because its address is not ours; test in Task 4.
- adbd's PEER_INFO frame decrypting to the wrong length, or a header with version 2 or payload 0: `PairingClient` must fail cleanly, not hang; tests in Task 3.
- adbd sending `CNXN` directly without `STLS` (a rooted/odd build) or a `CLSE` before `OKAY`: `AdbTransport` must throw, not loop; tests in Task 5.
- The user types the code after the pairing dialog moved to a new port: the service re-discovers before pairing (1.2 behaviour, kept in Task 7).

---

### Task 1: Ed25519 arithmetic and SPAKE2

**Files:**
- Create: `app/src/main/java/app/wristline/adb/Ed25519.java`
- Create: `app/src/main/java/app/wristline/adb/Spake2.java`
- Test: `app/src/test/java/app/wristline/adb/Ed25519Test.java`
- Test: `app/src/test/java/app/wristline/adb/Spake2Test.java`

**Interfaces:**
- Produces:
  - `final class Ed25519` with `static final BigInteger P` (2^255 − 19), `static final BigInteger L` (2^252 + 27742317777372353535851937790883648493), `static final BigInteger D`; nested `static final class Point { final BigInteger x, y; }`; `static final Point B` (base point); `static Point add(Point a, Point b)`, `static Point sub(Point a, Point b)`, `static Point mul(BigInteger k, Point p)`, `static byte[] encode(Point p)` (32 bytes, little-endian y with the x parity in bit 255), `static Point decode(byte[] enc)` (throws `IllegalArgumentException` when not on the curve or malformed), `static BigInteger decodeScalarLE(byte[] b)`, `static byte[] encodeScalarLE(BigInteger k, int len)`, `static boolean onCurve(Point p)`; `Point` has `equals`/`hashCode` on x and y.
  - `final class Spake2` with `enum Role { ALICE, BOB }`; constructor `Spake2(Role role, byte[] myName, byte[] theirName, SecureRandom random)`; `byte[] generateMessage(byte[] password)` (32 bytes, callable once); `byte[] processMessage(byte[] theirMessage)` (returns the 64-byte SHA-512 key, callable once after `generateMessage`; throws `IllegalArgumentException` on a bad point); `static final byte[] M_ENCODED`, `static final byte[] N_ENCODED` (the 32-byte encodings).

- [ ] **Step 1: Write the failing Ed25519 tests**

```java
@Test public void basePointIsOnCurve() { assertTrue(Ed25519.onCurve(Ed25519.B)); }
@Test public void encodeDecodeRoundTrip() { Point p = Ed25519.mul(BigInteger.valueOf(12345), Ed25519.B); assertEquals(p, Ed25519.decode(Ed25519.encode(p))); }
@Test public void mulByOrderGivesIdentity() { Point id = Ed25519.mul(Ed25519.L, Ed25519.B); assertEquals(BigInteger.ZERO, id.x); assertEquals(BigInteger.ONE, id.y); }
@Test public void addThenSubRestores() { Point a = Ed25519.mul(BigInteger.valueOf(7), Ed25519.B); Point b = Ed25519.mul(BigInteger.valueOf(11), Ed25519.B); assertEquals(a, Ed25519.sub(Ed25519.add(a, b), b)); }
@Test public void basePointEncodingMatchesRfc8032() { assertEquals("5866666666666666666666666666666666666666666666666666666666666666", hex(Ed25519.encode(Ed25519.B))); }
@Test public void decodeRejectsOffCurve() { byte[] bad = new byte[32]; bad[0] = 2; assertThrows(IllegalArgumentException.class, () -> Ed25519.decode(bad)); }
@Test public void ed25519PublicKeyFromRfc8032Vector1() {
    // RFC 8032 test 1: secret 9d61b19d...; a = clamp(SHA-512(secret)[0..32]); A = a*B
    byte[] secret = hexBytes("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
    byte[] h = MessageDigest.getInstance("SHA-512").digest(secret);
    byte[] a = Arrays.copyOf(h, 32); a[0] &= (byte) 248; a[31] &= 127; a[31] |= 64;
    assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", hex(Ed25519.encode(Ed25519.mul(Ed25519.decodeScalarLE(a), Ed25519.B))));
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'app.wristline.adb.Ed25519Test'`
Expected: compilation failure (class missing).

- [ ] **Step 3: Implement `Ed25519`**

Affine coordinates with `BigInteger`, modular inverse per operation (`modInverse`), twisted Edwards addition formula for `-x² + y² = 1 + d x² y²` with `d = -121665 / 121666 mod p`. Decode per RFC 8032 §5.1.3 (recover x from y, pick parity, reject when `x = 0` and parity bit set, reject y ≥ p). Scalar multiplication: double-and-add is fine (the comment states it is not constant time and why that is acceptable here: single-use scalars, local one-off exchange).

- [ ] **Step 4: Run the tests to verify they pass**

Expected: all `Ed25519Test` PASS.

- [ ] **Step 5: Write the failing SPAKE2 tests**

```java
@Test public void mAndNMatchBoringSslComments() {
    // Affine x/y of M and N from the comments in BoringSSL's spake25519.cc; copy the decimal/hex
    // values verbatim into the test. Table entry 0 is encodeint(x) || encodeint(y); check that
    // decode(M_ENCODED) has those coordinates and that both tables' entry 0 agree.
    Point m = Ed25519.decode(Spake2.M_ENCODED); assertEquals(new BigInteger(M_X_FROM_COMMENT), m.x); assertEquals(new BigInteger(M_Y_FROM_COMMENT), m.y);
    Point n = Ed25519.decode(Spake2.N_ENCODED); assertEquals(new BigInteger(N_X_FROM_COMMENT), n.x); assertEquals(new BigInteger(N_Y_FROM_COMMENT), n.y);
}
@Test public void aliceAndBobAgreeOnTheKey() {
    byte[] pw = "123456".getBytes(StandardCharsets.US_ASCII);
    Spake2 a = new Spake2(Role.ALICE, name("adb pair client"), name("adb pair server"), new SecureRandom());
    Spake2 b = new Spake2(Role.BOB, name("adb pair server"), name("adb pair client"), new SecureRandom());
    byte[] ma = a.generateMessage(pw); byte[] mb = b.generateMessage(pw);
    assertEquals(32, ma.length);
    byte[] ka = a.processMessage(mb);
    assertEquals(64, ka.length);
    assertArrayEquals(ka, b.processMessage(ma));
}
@Test public void wrongPasswordGivesDifferentKey() { /* same, bob uses "654321"; assertFalse(Arrays.equals(...)) */ }
@Test public void processRejectsOffCurveMessage() { /* generateMessage then processMessage(new byte[32] with byte[0]=2) throws IllegalArgumentException */ }
@Test public void transcriptOrderMatchesBoringSsl() {
    // Recompute alice's key by hand from her private scalar (expose a package-private constructor
    // taking the 32-byte reduced private scalar for tests) and compare with processMessage.
}
```
`name(String s)` returns the bytes **plus a trailing NUL** (adb passes `sizeof`).

- [ ] **Step 6: Run the tests to verify they fail**

Expected: compilation failure (`Spake2` missing).

- [ ] **Step 7: Implement `Spake2` as a line-by-line port of `spake25519.cc`**

Fixed by the reference, restated here so the implementer does not have to guess:
- `generateMessage`: 64 random bytes → `sc_reduce` (interpret as little-endian, mod L) → `left_shift_3` (×8 mod 2^256, as the C does on the byte array: implement as `BigInteger.shiftLeft(3)` then keep 256 bits) → private scalar `x`. `P = x·B`. Password scalar: `SHA-512(password)` → little-endian mod L → BoringSSL's compatibility step: add L once for each of the low three bits set, in the order bit 0, bit 1, bit 2 (so the result is a multiple of 8 but equal to the reduced value mod L; port exactly). `mask = w·M` for ALICE, `w·N` for BOB. Message = `encode(P + mask)`. Remember `myMsg`, `w`, `passwordHash` (the full 64-byte SHA-512).
- `processMessage`: decode their message (reject bad points); `peersMask = w·N` for ALICE, `w·M` for BOB; `Q = their − peersMask`; `shared = encode(x·Q)`. Key = SHA-512 over `lp(aliceName) lp(bobName) lp(aliceMsg) lp(bobMsg) lp(shared) lp(passwordHash)` where `lp(v)` is the 8-byte little-endian length of `v` followed by `v`, and alice/bob are by role (ALICE: my/their; BOB: their/my). Return the 64 bytes.
- `M_ENCODED`/`N_ENCODED`: from the affine x/y in the C comments, encoded with `Ed25519.encode` at class init, or as literal 32-byte arrays with a test that they decode to those coordinates.

- [ ] **Step 8: Run the tests to verify they pass**

Expected: all `Spake2Test` and `Ed25519Test` PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/wristline/adb/Ed25519.java app/src/main/java/app/wristline/adb/Spake2.java app/src/test/java/app/wristline/adb/
git commit -m "Add Ed25519 arithmetic and a SPAKE2 port for adb pairing"
```

---

### Task 2: adb key, certificate and adb public-key encoding

**Files:**
- Create: `app/src/main/java/app/wristline/adb/AdbKey.java`
- Create: `app/src/main/java/app/wristline/adb/AndroidPubkey.java`
- Modify: `app/src/main/java/app/wristline/LegacyAdbKey.java` (keep; AdbKey calls `LegacyAdbKey.delete` once on first use)
- Test: `app/src/test/java/app/wristline/adb/AndroidPubkeyTest.java`
- Test: `app/src/test/java/app/wristline/adb/AdbKeyTest.java`

**Interfaces:**
- Produces:
  - `final class AndroidPubkey` with `static byte[] encode(RSAPublicKey key)` (the 524-byte `android_pubkey` struct: `uint32 len = 64` words, `uint32 n0inv`, `uint32 n[64]` little-endian words of the modulus, `uint32 rr[64]` (`2^4096 mod n`), `uint32 exponent`, all little-endian; see dadb `AdbKeyPair.kt` for the derivation of `n0inv` and `rr`) and `static String line(RSAPublicKey key, String name)` = `Base64(encode(key)) + " " + name` (no newline, Base64 without line breaks).
  - `final class AdbKey` with `static synchronized AdbKey load(Context context)` (creates the key pair on first call), `static synchronized void forget(Context context)`, `static boolean exists(Context context)`, `X509Certificate certificate()`, `PrivateKey privateKey()`, `RSAPublicKey publicKey()`, `String peerName()` (= `"wristline@" + Build.MODEL` with every char outside `[A-Za-z0-9_-]` dropped), `byte[] peerInfo()` (8192 bytes: byte 0 = 0 (`ADB_RSA_PUB_KEY`), then `line(publicKey(), peerName())` as US-ASCII, NUL, zero padding; throws if the line exceeds 8190 bytes), `javax.net.ssl.X509KeyManager keyManager()` (always returns alias `"wristline"`, this certificate chain and this private key, for every `chooseClientAlias` call regardless of issuers).
  - Storage: alias `"wristline-adb"` in `AndroidKeyStore`, `KeyGenParameterSpec` with `PURPOSE_SIGN`, `RSAKeyGenParameterSpec(2048, F4)`, `DIGEST_SHA256`, signature paddings `SIGNATURE_PADDING_RSA_PKCS1` and `SIGNATURE_PADDING_RSA_PSS`, `setCertificateSubject(new X500Principal("CN=Wristline"))`, `setCertificateNotBefore(now − 1 day)`, `setCertificateNotAfter(now + 20 years)`. If `KeyPairGenerator`/keystore throws (`ProviderException`, `KeyStoreException`, …), fall back to a software RSA-2048 key stored in `new File(context.getFilesDir(), "adb/key.p8")`, wrapped with AES-256-GCM under keystore alias `"wristline-adb-wrap"` (`PURPOSE_ENCRYPT|DECRYPT`, `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`) when that key can be created, else stored as is (`filesDir` is app-private; this is the Robolectric case), with a one-byte format prefix (0 = plain, 1 = wrapped), plus `adb/cert.der`; the certificate is built as a minimal self-signed X.509 v3 DER (TBSCertificate with serial 1, `sha256WithRSAEncryption`, subject = issuer = `CN=Wristline`, validity as above, no extensions) signed with `Signature.getInstance("SHA256withRSA")`. Which backend is in use is `enum Backend { KEYSTORE, SOFTWARE } backend()`.

- [ ] **Step 1: Write the failing `AndroidPubkey` tests**

```java
@Test public void encodeIs524BytesWithLenAndExponent() {
    RSAPublicKey k = generate2048(); byte[] out = AndroidPubkey.encode(k);
    assertEquals(524, out.length);
    assertEquals(64, le32(out, 0)); assertEquals(65537, le32(out, 520));
}
@Test public void modulusWordsAreLittleEndianLowWordFirst() { /* le32(out, 8) == lowest 32 bits of modulus */ }
@Test public void n0invMatchesDefinition() { /* (n0inv * n[0]) mod 2^32 == 2^32 - 1  i.e. n0inv == -n^-1 mod 2^32 */ }
@Test public void rrIs2Pow4096ModN() { /* decode words 136..391 as little-endian BigInteger == 2^4096 mod n */ }
@Test public void lineHasBase64ThenSpaceThenName() { assertTrue(AndroidPubkey.line(k, "wristline@X300Pro").matches("[A-Za-z0-9+/=]+ wristline@X300Pro")); }
```

- [ ] **Step 2: Run to verify failure, implement `AndroidPubkey`, run to verify pass**

Run: `... --tests 'app.wristline.adb.AndroidPubkeyTest'`. Expected: FAIL then PASS.

- [ ] **Step 3: Write the failing `AdbKey` tests (Robolectric, `@Config(sdk = 36)`)**

Robolectric has no `AndroidKeyStore`; these tests exercise the software backend and the parts common to both.

```java
@Test public void loadCreatesKeyAndCertificateOnce() { AdbKey a = AdbKey.load(ctx); AdbKey b = AdbKey.load(ctx); assertEquals(a.publicKey(), b.publicKey()); assertTrue(AdbKey.exists(ctx)); assertEquals("CN=Wristline", a.certificate().getSubjectX500Principal().getName()); }
@Test public void certificateIsSelfSignedWithItsOwnKey() throws Exception { AdbKey k = AdbKey.load(ctx); k.certificate().verify(k.publicKey()); }
@Test public void forgetDeletesEverything() { AdbKey.load(ctx); AdbKey.forget(ctx); assertFalse(AdbKey.exists(ctx)); assertFalse(new File(ctx.getFilesDir(), "adb/key.p8").exists()); }
@Test public void peerInfoLayout() { byte[] p = AdbKey.load(ctx).peerInfo(); assertEquals(8192, p.length); assertEquals(0, p[0]); String line = new String(p, 1, indexOfNul(p, 1) - 1, US_ASCII); assertEquals(AndroidPubkey.line(k.publicKey(), k.peerName()), line); }
@Test public void peerNameKeepsOnlySafeCharacters() { assertEquals("wristline@" + Build.MODEL.replaceAll("[^A-Za-z0-9_-]", ""), AdbKey.load(ctx).peerName()); }
@Test public void keyManagerAlwaysChoosesWristline() { X509KeyManager km = AdbKey.load(ctx).keyManager(); assertEquals("wristline", km.chooseClientAlias(new String[]{"RSA"}, new Principal[]{new X500Principal("CN=fake")}, null)); assertEquals(1, km.getCertificateChain("wristline").length); }
@Test public void loadDeletesLegacyPemFile() { /* create filesDir/adb/adbkey.pem, load, assert gone */ }
```

- [ ] **Step 4: Run to verify failure, implement `AdbKey`, run to verify pass**

In Robolectric the keystore path throws, so `load` takes the software path; keep the keystore path in a separate private method `keystoreOrNull()` with a one-line `Log.i` of which backend is used (no key material).

- [ ] **Step 5: Full suite, then commit**

```bash
git add app/src/main/java/app/wristline/adb/AdbKey.java app/src/main/java/app/wristline/adb/AndroidPubkey.java app/src/test/java/app/wristline/adb/
git commit -m "Add the adb key pair, its certificate and the adb public-key encoding"
```

---

### Task 3: Pairing client

**Files:**
- Create: `app/src/main/java/app/wristline/adb/PairingClient.java`
- Create: `app/src/main/java/app/wristline/adb/PairingCipher.java`
- Create: `app/src/main/java/app/wristline/adb/TrustAny.java` (an `X509TrustManager` that accepts everything, with the comment from the spec's "Networking rules")
- Test: `app/src/test/java/app/wristline/adb/PairingCipherTest.java`
- Test: `app/src/test/java/app/wristline/adb/PairingClientTest.java`

**Interfaces:**
- Consumes: `Spake2`, `AdbKey.keyManager()`, `AdbKey.peerInfo()`.
- Produces:
  - `final class PairingCipher` with `PairingCipher(byte[] spake2Key)` (HKDF-SHA256, salt empty, info `"adb pairing_auth aes-128-gcm key"`, 16 bytes; implement HKDF with `javax.crypto.Mac` `HmacSHA256`), `byte[] encrypt(byte[] plain)`, `byte[] decrypt(byte[] cipher)` (AES/GCM/NoPadding, 128-bit tag, 12-byte nonce = 8-byte little-endian counter + 4 zero bytes, separate encrypt/decrypt counters starting at 0, incremented after success; `decrypt` throws `GeneralSecurityException` on a bad tag), `static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length)`.
  - `final class PairingClient` with `static void pair(InetSocketAddress address, String code, AdbKey key, int connectTimeoutMs, int readTimeoutMs) throws IOException`, and the package-private `static void pairOn(SSLSocket tls, String code, AdbKey key, KeyExporter exporter) throws IOException` on an already-handshaken socket (tests). `interface KeyExporter { byte[] export(SSLSocket s, String label, int length) throws IOException; }` with the production `AndroidKeyExporter` (see Step 3).
  - Frame helpers (package-private, tested): `static byte[] header(int type, int payloadLength)` (6 bytes: 1, type, big-endian length) and `static int[] readHeader(DataInputStream in)` → `{version, type, length}` throwing `IOException` when version ≠ 1, type ∉ {0,1}, length = 0 or > 16384.

- [ ] **Step 1: Write the failing `PairingCipher` tests**

```java
@Test public void hkdfRfc5869TestCase1() { /* IKM 0x0b×22, salt 000102..0c, info f0..f9, L=42 → OKM 3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865 */ }
@Test public void encryptThenDecryptRoundTrips() { PairingCipher a = new PairingCipher(key64), b = new PairingCipher(key64); assertArrayEquals(msg, b.decrypt(a.encrypt(msg))); }
@Test public void ciphertextIsPlainPlus16() { assertEquals(msg.length + 16, new PairingCipher(key64).encrypt(msg).length); }
@Test public void nonceCountersAdvanceIndependently() { /* a.encrypt twice; b.decrypt both in order OK; decrypting the second first throws */ }
@Test public void firstNonceIsZeroCounter() { /* with key64 fixed, compare encrypt(msg) against a manual AES-GCM with nonce new byte[12] */ }
```

- [ ] **Step 2: Run to verify failure, implement `PairingCipher`, run to verify pass**

- [ ] **Step 3: Write the failing `PairingClient` tests**

A fake adbd in the test: `SSLServerSocket` (TLS 1.3, self-signed RSA cert generated in the test with the same minimal DER builder as Task 2's software backend, exposed as package-private `SelfSigned.certificate(KeyPair, String cn, …)` if you factor it out) on `127.0.0.1`, serving one scripted session on a thread; the client side is `pairOn` over an `SSLSocket` from an `SSLContext` with `TrustAny` and `key.keyManager()`. The fake runs the real `Spake2` as BOB with `name("adb pair server")`/`name("adb pair client")`, the password = code bytes + its own 64 exported bytes. On the JVM, `SSLSockets.exportKeyingMaterial` does not exist: factor the exporter into `interface KeyExporter { byte[] export(SSLSocket s, String label, int length) throws IOException; }` with the production implementation `AndroidKeyExporter` calling `android.net.ssl.SSLSockets.exportKeyingMaterial(s, "adb-label\0", null, 64)` (note the embedded NUL in the label; `isSupportedSocket` must be true, else `IOException`), and a test implementation that returns a fixed 64-byte array on both sides. `pairOn(SSLSocket, String code, AdbKey key, KeyExporter exporter)`.

```java
@Test public void pairsWithFakeAdbd() throws Exception { /* fake accepts: reads SPAKE2_MSG, sends its own, derives cipher, reads PEER_INFO (decrypts to 8192 bytes, byte0==0, line parses), sends encrypted 8192-byte GUID PeerInfo (type 1). pairOn returns normally. */ }
@Test public void wrongCodeFails() { /* fake uses "000000": PEER_INFO decrypt on the fake fails → fake closes; client throws IOException (either on its decrypt or on EOF) */ }
@Test public void peerInfoOfWrongLengthFails() { /* fake sends encrypted 100 bytes as PEER_INFO → IOException */ }
@Test public void headerVersion2Rejected() { /* fake answers SPAKE2_MSG with version byte 2 → IOException */ }
@Test public void zeroPayloadRejected() { /* header with length 0 → IOException */ }
@Test public void socketReadTimeoutIsSet() { /* fake never answers; socket SO_TIMEOUT 500 in test → SocketTimeoutException within ~1 s */ }
```

- [ ] **Step 4: Run to verify failure, implement `PairingClient`, run to verify pass**

Flow in `pairOn`: `exported = exporter.export(tls, "adb-label\0", 64)`; `password = codeAscii || exported`; `spake = new Spake2(ALICE, name("adb pair client"), name("adb pair server"), new SecureRandom())`; write header(0, 32) + `generateMessage(password)`; read header, require type 0, read `length` bytes; `cipher = new PairingCipher(spake.processMessage(theirs))`; `enc = cipher.encrypt(key.peerInfo())`; write header(1, enc.length) + enc; read header, require type 1, read, `plain = cipher.decrypt(...)`, require `plain.length == 8192`. `pair(address, …)` opens a plain `Socket` with connect timeout, wraps it with `SSLContext.getInstance("TLSv1.3")` initialised with `{key.keyManager()}` and `{new TrustAny()}`, `setEnabledProtocols({"TLSv1.3"})`, `setUseClientMode(true)`, `startHandshake()`, then `pairOn(..., new AndroidKeyExporter())`, and closes the socket in `finally`.

- [ ] **Step 5: Full suite, then commit**

```bash
git add app/src/main/java/app/wristline/adb/ app/src/test/java/app/wristline/adb/
git commit -m "Add the adb pairing client"
```

---

### Task 4: Discovery

**Files:**
- Create: `app/src/main/java/app/wristline/adb/AdbDiscovery.java` (port of 1.2's `AdbDiscovery.java`, package changed; keep its `SerialQueue` dependency: copy 1.2's `SerialQueue.java` into `app.wristline.adb` if Wristline lacks it, check first)
- Test: `app/src/test/java/app/wristline/adb/AdbDiscoveryTest.java` (port of 1.2's, plus the two below)

**Interfaces:**
- Produces: `static final String PAIRING = "_adb-tls-pairing._tcp"`, `CONNECT = "_adb-tls-connect._tcp"`; `static boolean onWifi(Context)`; `static boolean isPlainWifi(boolean wifi, boolean vpn)`; `static InetSocketAddress find(Context, String serviceType, long timeoutMs)` (blocking; only services resolving to one of this phone's own Wi-Fi link addresses; prefers an IPv4 own address when the service resolves to several); package-private `static InetAddress preferIpv4(List<InetAddress> own, InetAddress resolved)`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void vpnOverWifiIsNotPlainWifi() { assertFalse(AdbDiscovery.isPlainWifi(true, true)); assertTrue(AdbDiscovery.isPlainWifi(true, false)); assertFalse(AdbDiscovery.isPlainWifi(false, false)); }
@Test public void onWifiFalseWithoutNetworks() { shadowOf(cm).clearAllNetworks(); assertFalse(AdbDiscovery.onWifi(ctx)); }
@Test public void findReturnsNullOffWifi() { /* clearAllNetworks → find(ctx, PAIRING, 100) == null quickly */ }
@Test public void foreignAddressIsIgnored() { /* with Robolectric's ShadowNsdManager if available, or by testing the package-private filter `static boolean isOwn(List<InetAddress> own, InetAddress host)`: 192.168.1.50 not in own [192.168.1.20] → false */ }
@Test public void preferIpv4() { /* own = [fe80::1, 192.168.1.20], resolved fe80::1 → 192.168.1.20 */ }
```

- [ ] **Step 2: Run to verify failure, port and adjust `AdbDiscovery`, run to verify pass**

- [ ] **Step 3: Full suite, then commit**

```bash
git add app/src/main/java/app/wristline/adb/AdbDiscovery.java app/src/main/java/app/wristline/adb/SerialQueue.java app/src/test/java/app/wristline/adb/AdbDiscoveryTest.java
git commit -m "Find this phone's own Wireless debugging services over mDNS"
```

---

### Task 5: adb transport (connect over STLS, run shell commands)

**Files:**
- Create: `app/src/main/java/app/wristline/adb/AdbMessage.java`
- Create: `app/src/main/java/app/wristline/adb/AdbTransport.java`
- Create: `app/src/main/java/app/wristline/adb/AdbShell.java`
- Test: `app/src/test/java/app/wristline/adb/AdbMessageTest.java`
- Test: `app/src/test/java/app/wristline/adb/AdbTransportTest.java`

**Interfaces:**
- Consumes: `AdbKey`, `TrustAny`, `AdbDiscovery` (Task 4).
- Produces:
  - `final class AdbMessage { final int command, arg0, arg1; final byte[] payload; }` with `static final int A_CNXN = 0x4e584e43, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f, A_CLSE = 0x45534c43, A_WRTE = 0x45545257, A_STLS = 0x534c5453`, `static final int VERSION = 0x01000001, STLS_VERSION = 0x01000000, MAX_PAYLOAD = 1024 * 1024`; `byte[] encode()` (24-byte header: command, arg0, arg1, length, checksum = 0, magic = command ^ 0xffffffff, all little-endian; then payload) and `static AdbMessage read(DataInputStream in) throws IOException` (validates magic and length ≤ MAX_PAYLOAD).
  - `final class AdbTransport implements Closeable` with `static AdbTransport connect(InetSocketAddress address, AdbKey key, int connectTimeoutMs, int readTimeoutMs) throws IOException` and a package-private `static AdbTransport connectOn(Socket socket, AdbKey key, SSLContext tlsContext) throws IOException` for tests; `String shell(String command) throws IOException` (runs `shell,v2,raw:` when the device's CNXN banner lists `shell_v2`, else `shell:`; returns stdout+stderr as UTF-8; for v2 parses the shell protocol packets `id(1) length(4 LE) data`, ids 1 = stdout, 2 = stderr, 3 = exit; throws `IOException` when exit ≠ 0 with the exit code in the message, output not included); `String banner()`; `void close()`.
  - `final class AdbShell` (facade used by the app) with `static boolean isReachable(Context context)` (discover connect service, `AdbTransport.connect`, true if CNXN completed; no shell command), `static List<String> run(Context context, List<String> commands)` (null when adbd cannot be reached or any command throws; outputs in order), `static boolean pair(Context context, InetSocketAddress pairing, String code)` (blocking, with the 20 s pairing timeout on a side thread as 1.2 did), `static void forgetKey(Context context)` → `AdbKey.forget`. Constants `CONNECT_TIMEOUT_MS = 4000`, `SOCKET_TIMEOUT_MS = 15000`, `PAIR_TIMEOUT_MS = 20000`.

- [ ] **Step 1: Write the failing `AdbMessage` tests**

```java
@Test public void encodeHeaderLayout() { byte[] b = new AdbMessage(A_CNXN, VERSION, MAX_PAYLOAD, "host::\0".getBytes()).encode(); assertEquals(A_CNXN, le32(b, 0)); assertEquals(VERSION, le32(b, 4)); assertEquals(MAX_PAYLOAD, le32(b, 8)); assertEquals(7, le32(b, 12)); assertEquals(0, le32(b, 16)); assertEquals(A_CNXN ^ 0xffffffff, le32(b, 20)); }
@Test public void readRoundTrips() { /* read(new DataInputStream(new ByteArrayInputStream(encode()))) equals */ }
@Test public void readRejectsBadMagic() { /* flip a magic byte → IOException */ }
@Test public void readRejectsOversizedPayload() { /* length = MAX_PAYLOAD + 1 → IOException before allocating */ }
```

- [ ] **Step 2: Run to verify failure, implement `AdbMessage`, run to verify pass**

- [ ] **Step 3: Write the failing `AdbTransport` tests**

Fake adbd on a `ServerSocket` in the test, scripted per test on a thread; TLS via a test `SSLContext` (server cert self-signed as in Task 3, client uses `key.keyManager()` + `TrustAny`). The fake: read CNXN → send STLS(STLS_VERSION, 0) → read STLS → wrap its socket with `SSLServerSocketFactory`-created `SSLSocket` in server mode (`createSocket(socket, host, port, true)`, `setUseClientMode(false)`, `setWantClientAuth(true)`) → handshake → send CNXN(VERSION, MAX_PAYLOAD, `"device::ro.product.name=x;features=shell_v2,cmd\0"`) → read OPEN → send OKAY(remoteId=1, localId=arg0) → send WRTE with v2 packets `[1][len]["ok\n"]` then `[3][1][0]` → read OKAY → send CLSE.

```java
@Test public void connectsOverStlsAndRunsShellV2() throws Exception { try (AdbTransport t = connectToFake(script)) { assertEquals("ok\n", t.shell("echo ok")); assertTrue(t.banner().contains("shell_v2")); } }
@Test public void fallsBackToLegacyShellWithoutShellV2() { /* banner "device::features=cmd" → OPEN payload is "shell:echo ok\0", fake writes raw "ok\n" then CLSE */ }
@Test public void nonZeroExitThrows() { /* v2 exit packet value 1 → IOException with message containing "1" */ }
@Test public void cnxnWithoutStlsThrows() { /* fake answers CNXN immediately → IOException("expected STLS") */ }
@Test public void clseBeforeOkayThrows() { /* after OPEN fake sends CLSE → IOException */ }
@Test public void openPayloadIsNulTerminated() { /* fake records OPEN payload; assertEquals("shell,v2,raw:echo ok\0", ...) */ }
@Test public void clientCertificateIsPresented() { /* fake's SSLSession.getPeerCertificates()[0] equals key.certificate() */ }
```

- [ ] **Step 4: Run to verify failure, implement `AdbTransport` and `AdbShell`, run to verify pass**

Client CNXN banner: `"host::features=shell_v2,cmd\0"`. After STLS: `tlsContext.getSocketFactory().createSocket(socket, host, port, true)`, client mode, `setEnabledProtocols({"TLSv1.3"})`, handshake, then read CNXN. Local stream ids start at 1 and increment per `shell`. `AdbShell.run` connects once and runs all commands on that transport.

- [ ] **Step 5: Full suite, then commit**

```bash
git add app/src/main/java/app/wristline/adb/ app/src/test/java/app/wristline/adb/
git commit -m "Add the adb transport and the AdbShell facade"
```

---

### Task 6: App-op commands, apply, disable and silent repair

**Files:**
- Modify: `app/src/main/java/app/wristline/AppOpsCommands.java` (add `get`, `restart` = `am kill <pkg>`, `isIgnored`, `isAllowed` from 1.2)
- Modify: `app/src/main/java/app/wristline/FullMode.java` (add `apply`, `disable`, consent-gated silent repair in `needsRepair`, from 1.2's version at `CallRelay-public/.../FullMode.java`, re-pointed at `app.wristline.adb.AdbShell`)
- Modify: `app/src/main/java/app/wristline/Prefs.java` (add `consent`/`setConsent` from 1.2)
- Modify: `app/src/main/java/app/wristline/EventLog.java` (add `SETUP_DONE = "setup_done"`, `SETUP_FAILED = "setup_failed"`, `REPAIR_SILENT = "repair_silent"`)
- Modify: `app/src/main/AndroidManifest.xml` (add `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`)
- Test: `app/src/test/java/app/wristline/AppOpsCommandsTest.java` (extend or create), `FullModeRepairTest.java`

**Interfaces:**
- Consumes: `AdbShell.run`, `AdbShell.isReachable`, `AdbShell.forgetKey`.
- Produces: `static List<SupportedApp> FullMode.apply(Context, List<SupportedApp>)` (null when adbd unreachable; sets `appops set`, verifies with `get`/`isIgnored`, `markSetUp`, then `am kill` the apps that took it), `static boolean FullMode.disable(Context, List<SupportedApp>)` (resets apps that `needsAppOp`, verifies `isAllowed`, kills them, `markTurnedOff`; when every app is back, `AdbShell.forgetKey`), `needsRepair`: when `Prefs.consent(app)` and `AdbShell.isReachable`, run `apply` on a background thread, record `REPAIR_SILENT` on success and clear `broken`; otherwise `showRepairNeeded` as today.

- [ ] **Step 1: Write the failing tests**

```java
// AppOpsCommandsTest
@Test public void restartUsesAmKillNotForceStop() { assertEquals("am kill com.whatsapp", AppOpsCommands.restart("com.whatsapp")); }
@Test public void isIgnoredReadsUidMode() { assertTrue(AppOpsCommands.isIgnored("Uid mode: READ_PHONE_STATE: ignore\nREAD_PHONE_STATE: allow; time=...")); assertFalse(AppOpsCommands.isIgnored("READ_PHONE_STATE: allow")); }
@Test public void isAllowedAcceptsAllowAndNoOperations() { assertTrue(AppOpsCommands.isAllowed("No operations.")); assertTrue(AppOpsCommands.isAllowed("READ_PHONE_STATE: allow")); assertFalse(AppOpsCommands.isAllowed("Uid mode: READ_PHONE_STATE: ignore")); }
// FullModeRepairTest (Robolectric; AdbShell unreachable because there is no Wi-Fi)
@Test public void repairWithoutConsentPostsNotification() { Prefs.setConsent(ctx, WHATSAPP, false); FullMode.onCallDropped(ctx, WHATSAPP); assertNotNull(shadowOf(nm).getNotification(StatusNotifications.ID_REPAIR)); assertTrue(Prefs.broken(ctx, WHATSAPP)); }
@Test public void repairWithConsentButUnreachableFallsBackToNotification() throws Exception { Prefs.setConsent(ctx, WHATSAPP, true); FullMode.onCallDropped(ctx, WHATSAPP); awaitBackground(); assertNotNull(shadowOf(nm).getNotification(ID_REPAIR)); }
```

- [ ] **Step 2: Run to verify failure, implement, run to verify pass**

Port 1.2's `FullMode.apply/disable/needsRepair` bodies; keep today's `markTurnedOff` and `appsWithVoiceOn`. Also update `FullMode`'s class comment and `AppOpsCommands`'s to say "Wristline's built-in adb client, or a computer".

- [ ] **Step 3: Full suite, then commit**

```bash
git add app/src/main/java/app/wristline/ app/src/main/AndroidManifest.xml app/src/test/java/app/wristline/
git commit -m "Apply, undo and silently repair the app-op setting through the built-in adb client"
```

---

### Task 7: Setup service, progress, steps and pairing-code notification

**Files:**
- Create (ported from 1.2, same names): `AdbSetupService.java`, `SetupProgress.java`, `SetupStep.java`, `SetupStart.java`, `PairingCode.java` in `app/src/main/java/app/wristline/`
- Modify: `StatusNotifications.java` (add `CHANNEL_SETUP = "voice_setup"` created with `IMPORTANCE_HIGH`, `setSound(null, null)`, keep the `adb_setup` deletion; add `ID_PAIRING = 4`, `ID_SETUP_RESULT = 5`, `ID_PAIRING_CODE = 6`; port 1.2's `showPairingCodeRequest`/progress helpers if they live there, check 1.2)
- Modify: `Permissions.java` (add `canShowCodePopUp` from 1.2: POST_NOTIFICATIONS granted and the setup channel not blocked and app notifications enabled)
- Modify: `AppUi.java` (add `wirelessDebuggingSettings()` from 1.2)
- Modify: `AndroidManifest.xml` (service `.AdbSetupService`, `exported="false"`, `foregroundServiceType="shortService"`)
- Modify: `res/values/strings.xml`, `res/values-it/strings.xml` (every `setup_*`, `pair_*`, `wireless_debugging_*`, `turn_off_wireless_debugging`, `try_again` key the ported code references; copy both languages from 1.2)
- Tests (ported from 1.2): `AdbSetupServiceTest.java`, `AdbSetupStopTest.java`, `SetupStepTest.java`, `SetupStartTest.java`, `PairingCodeTest.java`, `CodePopUpTest.java`

**Interfaces:**
- Consumes: `app.wristline.adb.AdbDiscovery`, `AdbShell.isReachable/pair`, `FullMode.apply`.
- Produces: `static Intent AdbSetupService.startIntent(Context, ArrayList<String> packages)`; the `SetupProgress` phases and `listener` exactly as 1.2; `SetupStep.whileLooking(...)`; `SetupStart.ready(...)`; `PairingCode.normalize(CharSequence)`.

- [ ] **Step 1: Port the 1.2 tests first and run them to see them fail to compile**

- [ ] **Step 2: Port the classes, adjusting imports to `app.wristline.adb.*` and `FullMode.apply`; service timeouts per Global Constraints; `startForeground` with `FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` on API ≥ 34, untyped below**

- [ ] **Step 3: Run the ported tests and the full suite; verify pass**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/ app/src/test/
git commit -m "Set up voice through the watch from a foreground service with a pairing-code notification"
```

---

### Task 8: Voice screen, consent and the guided setup screen

**Files:**
- Create (ported from 1.2): `VoiceSetupActivity.java`, `GuideSteps.java`; layouts `activity_voice_setup.xml`, `item_guide_step.xml`, `view_code_example.xml` (check the drawables they reference exist: `bg_step_todo`, `bg_circle`, `ic_check`; copy any missing from 1.2)
- Modify: `VoiceActivity.java` (add the "Set up on this phone" primary button → consent dialog (`consent_title`/`consent_app` strings) → `Prefs.setConsent(app, true)` per app → `startActivity(VoiceSetupActivity.intent(this, apps))`; "Set up with a computer" stays as the second button; `turnOff()` first tries `FullMode.disable` on a background thread when `AdbShell.isReachable`, else shows today's reset dialog; add an overflow/`forget_key` action visible when `AdbKey.exists`, confirm dialog, `AdbShell.forgetKey`), `res/layout/activity_voice.xml`
- Modify: `AndroidManifest.xml` (activity `.VoiceSetupActivity`, `exported="false"`)
- Modify: strings EN+IT (`guide_*`, `consent_*`, `set_up_on_phone`, `forget_key*`, `dev_path_*`, `open_*` keys from 1.2; `Brand` already exists in Wristline, check `dev_path_*` strings)
- Tests: `GuideStepsTest.java` (ported), `VoiceActivityButtonsTest.java` (new, Robolectric)

**Interfaces:**
- Consumes: everything above.
- Produces: `static Intent VoiceSetupActivity.intent(Activity, List<SupportedApp>)`.

- [ ] **Step 1: Write the failing `VoiceActivityButtonsTest`**

```java
@Test public void showsBothSetupButtonsWhenAnAppNeedsSetup() { /* WhatsApp installed+on+needsAppOp (shadow PackageManager grants READ_PHONE_STATE to com.whatsapp) → buttons set_up_on_phone and set_up visible */ }
@Test public void consentDialogListsEachAppBeforeStartingTheGuide() { /* click set_up_on_phone → latest AlertDialog message contains "WhatsApp"; click positive → next started Activity is VoiceSetupActivity with EXTRA_PACKAGES [com.whatsapp]; Prefs.consent true */ }
@Test public void forgetKeyHiddenWithoutKey() { /* no key → forget action GONE */ }
```

- [ ] **Step 2: Run to verify failure, port `GuideSteps`/`VoiceSetupActivity`, change `VoiceActivity`, run to verify pass**

- [ ] **Step 3: Full suite, then commit**

```bash
git add app/src/main/ app/src/test/
git commit -m "Offer voice setup on the phone from the Voice screen"
```

---

### Task 9: Docs and diagnostics text

**Files:**
- Modify: `README.md` (the "Voice through the watch" setup section: on-phone path first, computer second; the privacy paragraph: internet permission is used only to talk to this phone's own Wireless debugging; where the key lives and how to revoke it; remove "Wristline has no shell access of its own, and it doesn't ask for the internet permission")
- Modify: `docs/HOW_TO.md` (same two sections)
- Modify: `docs/superpowers/HANDOFF.md` (status line; Play data-safety note: no data collected or shared)
- Modify: `app/src/main/java/app/wristline/DiagnosticReport.java` if the report lists permissions or network use (check; add a line "Set up on this phone: yes/no" from `AdbKey.exists`)

- [ ] **Step 1: Make the edits; `grep -rn "no shell access\|internet permission" README.md docs/` shows only the new wording**

- [ ] **Step 2: Commit**

```bash
git add README.md docs/ app/src/main/java/app/wristline/DiagnosticReport.java
git commit -m "Document voice setup on the phone and the built-in adb client"
```

---

### Task 10: Device check on the owner's phone (owner present)

**Files:** none (report in the ledger). Build: `JAVA_HOME=... WRISTLINE_KEYSTORE_PROPERTIES=$HOME/.wristline-keys/keystore.properties ./gradlew :app:assembleRelease`, install on serial `10AFCB1Z4F00380`.

- [ ] **Step 1: Fresh state:** `adb shell appops set --uid com.whatsapp READ_PHONE_STATE allow`; in Developer options › Wireless debugging › Paired devices, forget any "wristline@…" entry; Wristline › Voice shows WhatsApp "Set up".
- [ ] **Step 2: Full flow:** Set up on this phone → consent → guide → Developer options → Wireless debugging → Pair device with pairing code → type the code into the notification. Expected: result notification "Voice through the watch is on", `adb shell appops get --uid com.whatsapp READ_PHONE_STATE` shows `Uid mode: READ_PHONE_STATE: ignore`, Paired devices lists `wristline@V2514`. Record which `AdbKey.backend()` was used (log line) and whether the keystore path worked on Android 17.
- [ ] **Step 3: Call test:** a WhatsApp call answered from the watch has its voice on the watch (the real purpose).
- [ ] **Step 4: Silent repair:** uninstall and reinstall WhatsApp (or `adb shell appops set --uid com.whatsapp READ_PHONE_STATE allow` and answer a call from the watch so it drops): with Wireless debugging on, expect `repair_silent` in the diagnostic report and no repair notification.
- [ ] **Step 5: Turn off:** Voice › Turn off: `appops get` shows `allow`, key forgotten (`AdbKey.exists` false, Paired devices entry can be removed by the user).
- [ ] **Step 6: Re-pair after `adb shell pm clear app.wristline`:** the guide asks for a new code and succeeds.
- [ ] **Step 7:** record results in `docs/superpowers/HANDOFF.md`; commit.

```bash
git add docs/superpowers/HANDOFF.md
git commit -m "Record the on-phone setup device check"
```
