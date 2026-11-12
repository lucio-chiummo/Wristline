# Voice through the watch, set up on the phone alone

Date: 2026-11-12. Status: design, written for a planner (superpowers:writing-plans) and the
implementers it dispatches. Supersedes Phase 3 of
`docs/superpowers/specs/2026-11-10-no-computer-setup-design.md`.

## Intent

WhatsApp, Telegram and Signal hang up or hold when Wristline's Telecom call goes active, because
they can see the phone's call state. The only fix that works for WhatsApp is the shell-level
app-op `appops set --uid <pkg> READ_PHONE_STATE ignore` (spikes A, B and C in the 2026-11-10
design: no permission trick and no audio-routing or timing trick gets around it). Today that
command needs a computer with adb. This design gives Wristline its own way to run it on the
phone alone, so anyone who installs the app from Play can get voice through the watch without a
computer and without installing any other app.

The owner's constraints, all binding:

- **No dependency on another installed app.** No Shizuku, no Wear Installer, nothing the user
  has to install besides Wristline. The app is headed for the Play Store and must stand alone.
- **Libraries only when vetted.** Any dependency must be named with its licence, maintainer,
  last release, what it touches and why it beats writing the code ourselves. Security comes
  first: this feature holds shell-level access to the phone.
- **Permissive licences only.** Apache-2.0, MIT, BSD, ISC. No GPL, LGPL or AGPL, no
  "non-commercial" terms, anywhere in the dependency tree.
- **No hidden or non-SDK APIs**, no reflection into the platform. Play policy and future
  Android releases both punish it.

## How it works, in one paragraph

Android 11+ ships "Wireless debugging" in Developer options. It is the same adb daemon (`adbd`)
a computer talks to, reachable over Wi-Fi after a one-time pairing with a six-digit code shown
on the phone. Wristline pairs with its own phone's adbd (the user reads the code in Settings and
types it into a Wristline notification), keeps the key it paired with, connects to adbd over TLS,
and runs the app-op command for each app. After that Wireless debugging can be turned off; the
app-op persists until the app is uninstalled or its data is cleared, in which case the existing
Repair flow runs the command again (no new pairing while the key is still authorised).

This is exactly what Wristline 1.2 did with a third-party library. 1.3 removed it because the
owner did not want the internet permission and an adb key in the app. The owner now accepts both,
provided the implementation is in-house, auditable and clean of GPL. The 1.2 flow is the
reference for the user experience; the transport and pairing code are written new.

## Goals

- A Play user with no computer turns on voice through the watch for WhatsApp, Telegram and
  Signal from Wristline's Voice screen, in one sitting, following on-screen steps.
- The computer path (today's "Set up with a computer" dialog) stays as the second option and
  as the fallback if Google ever blocks on-device adb.
- Repair after an app update or data clear works silently when Wireless debugging is on and the
  key is still authorised, and otherwise tells the user what to turn on.
- Everything the feature does on the network is to this phone itself. No server, no analytics,
  nothing leaves the phone. The privacy text in README and HOW_TO says so.
- The user can revoke: turning voice through the watch off for the last app deletes
  Wristline's adb key; "Revoke debugging authorisations" in Developer options is documented.

## Non-goals

- Installing the watch app from the phone. Google Play covers it (Phase 2 of the earlier design).
- Supporting Android 11 (API 30). Wristline's minSdk is 31 and the TLS exporter API this design
  needs is API 31; keep minSdk 31.
- A general adb client. Only what pairing, connecting and running a handful of shell commands
  needs. No file push, no forwarding, no USB.
- Keeping Wireless debugging on. Wristline needs it only during setup and repair.

## Anti-goals (a failure even if it works)

- Pulling in a GPL or LGPL artifact transitively. The 1.2 library `com.flyfishxu:kadb-android`
  depends on `com.github.Flyfish233:spake2-java` (GPL-3.0) and on
  `org.lsposed.hiddenapibypass` (reflection into hidden platform APIs). Both are disqualifying.
  The upstream `MuntashirAkon/spake2-java` is LGPL-3.0, also out. `MuntashirAkon/libadb-android`
  is GPL-3.0, out. Nobody on this project reads those sources while writing ours (clean room):
  the references are AOSP and BoringSSL, both Apache-2.0.
- Any reflection into Conscrypt or other platform internals to get at TLS keying material. The
  public API exists; use it.
- Leaving the pairing code, the adb key or shell output in logs or in the diagnostic report.
- Running any shell command other than the fixed `appops` and app-restart commands in
  `AppOpsCommands`. No command built from user input.
- Talking to any address that is not this phone's own.

## Facts established by research (2026-11-12)

- `android.net.ssl.SSLSockets.exportKeyingMaterial(SSLSocket, label, context, length)` is a
  public API since API 31 (RFC 5705 exporter). This is what makes an in-house pairing client
  possible without the Conscrypt library or hidden APIs. Android's platform TLS supports TLS 1.3
  and client certificates.
- adb pairing protocol (AOSP `packages/modules/adb`, main branch, Apache-2.0):
  - TLS 1.3 only (`SSL_CTX_set_min/max_proto_version(TLS1_3_VERSION)`). The client presents a
    self-signed certificate; adbd accepts any certificate at this stage (`SetCertVerifyCallback`
    returns 1). The client must accept adbd's self-signed certificate too.
  - After the handshake both sides export 64 bytes of keying material with label `"adb-label"`
    **including its trailing NUL** (10 bytes), no context, `use_context = false`.
  - SPAKE2 password = pairing code bytes (as typed, ASCII digits, no NUL) followed by the 64
    exported bytes.
  - SPAKE2 (BoringSSL `crypto/curve25519/spake25519.cc`, Apache-2.0): client role is alice,
    server role is bob. Client `my_name` is `"adb pair client"`, `their_name` is
    `"adb pair server"`, both lengths **including the trailing NUL** (`sizeof`). Messages are 32
    bytes. Key output is up to 64 bytes (`SPAKE2_MAX_KEY_SIZE`); adbd passes whatever
    `SPAKE2_process_msg` returns.
  - From the SPAKE2 key: HKDF-SHA256, salt empty, info `"adb pairing_auth aes-128-gcm key"` (32
    bytes, no NUL), output 16 bytes, AES-128-GCM, 16-byte tag, ciphertext followed by tag, no
    AAD. Nonce: 12 zero bytes with a little-endian 64-bit counter copied into the first 8;
    separate counters for encrypt and decrypt, each starting at 0 and incremented after every
    successful operation.
  - Framing on the TLS stream: packed header `uint8 version (1)`, `uint8 type`, `uint32 payload
    length (big-endian)`, then the payload. Types: `SPAKE2_MSG = 0`, `PEER_INFO = 1`. Max
    payload 16384.
  - Exchange: client sends SPAKE2_MSG (its 32 bytes), reads adbd's SPAKE2_MSG, derives the
    cipher, sends PEER_INFO (encrypted 8192-byte `PeerInfo`), reads adbd's PEER_INFO and
    decrypts it (must decrypt to exactly 8192 bytes).
  - `PeerInfo` is `uint8 type` + `uint8 data[8191]`, packed. Type `ADB_RSA_PUB_KEY = 0`
    (`ADB_DEVICE_GUID = 1` is what adbd sends back). The host adb fills `data` with the
    contents of `adbkey.pub`: the adb public-key encoding (`android_pubkey` struct, base64) then
    a space and a name such as `wristline@phone`, NUL-terminated, rest zero.
- adb connection after pairing (AOSP, unchanged since Android 11): plain TCP to the
  `_adb-tls-connect._tcp` port, client sends `CNXN` (version 0x01000001, max payload 1 MiB,
  banner `host::features=...`), adbd answers `STLS`, client replies `STLS`, then TLS 1.3 starts on
  the same socket with the client certificate; adbd checks the certificate's RSA public key
  against the keys it stored at pairing. After the handshake adbd sends `CNXN` and the normal
  adb stream protocol follows (`OPEN` `shell,v2,raw:<cmd>` or `shell:<cmd>`, `OKAY`, `WRTE`,
  `CLSE`). The RSA key is RSA-2048 and adbd's `android_pubkey` encoding needs the modulus,
  `n0inv` and `rr` words; `dadb`'s `AdbKeyPair.kt` (mobile-dev-inc, Apache-2.0) is a clean
  reference for that encoding.
- Discovery: adbd advertises `_adb-tls-pairing._tcp` only while the "Pair device with pairing
  code" dialog is open (new port each time), and `_adb-tls-connect._tcp` whenever Wireless
  debugging is on. `NsdManager` sees both. Other phones on the same Wi-Fi advertise too, so a
  resolved service counts only when its address is one of this phone's own Wi-Fi addresses.
- Android 17 ("ADB Wi-Fi 2.0", Google blog 2026-09-09) rebuilt adb's server stack and mDNS and
  makes adbd switch Wireless debugging off on networks the user has not marked trusted. Pairing
  codes remain. Nothing published says the pairing or connect protocol changed, but the owner's
  phone runs Android 17 and nothing is proven until the first task's spike pairs with it.
- Risk, open: in July 2026 (after CVE-2026-0073) an adb maintainer floated binding adbd to
  `wlan0` only, which would break clients connecting over `127.0.0.1`. Issue 526109803 is a
  feature request with no decision. Mitigation here: connect to the phone's own Wi-Fi address,
  never to loopback (1.2 used loopback to dodge an IPv6 scope problem; this design prefers the
  IPv4 Wi-Fi address and falls back to the resolved address with its scope). If Google ever
  blocks on-device adb outright, the computer path remains and the Voice screen's "on this
  phone" button goes away in an update. The design must keep that removal cheap: one entry point
  on the Voice screen, one package of classes.

## Decisions

### No new library

Everything is written in the app, in Java like the rest of the code base. New code, roughly:

| Part | Size | Notes |
|---|---|---|
| Ed25519 group arithmetic + SPAKE2 | ~350 lines | `BigInteger` field arithmetic in Edwards coordinates; one-time use, speed irrelevant |
| adb public-key encoding + key storage | ~120 lines | reference: `dadb` `AdbKeyPair.kt` (Apache-2.0) |
| Pairing client (TLS, framing, HKDF, AES-GCM) | ~250 lines | `javax.net.ssl`, `javax.crypto`, `SSLSockets.exportKeyingMaterial` |
| adb transport (CNXN, STLS, shell stream) | ~300 lines | |
| Discovery (`NsdManager`) | ~160 lines | port of 1.2's `AdbDiscovery` |
| Setup service, notifications, Voice screen changes, strings EN+IT | ~600 lines | port of 1.2's flow |
| Tests | as needed | SPAKE2 round trip both roles, known constants, encodings, framing, discovery filter, step logic |

Why not BouncyCastle (MIT) for the curve: its public Ed25519 API only signs and verifies; the
point arithmetic SPAKE2 needs is not exposed, and its generic `Curve25519` is the Weierstrass
form, which would mean birational maps on top. Plain `BigInteger` Edwards arithmetic is shorter
and readable. Why not vendor BoringSSL C through the NDK: it adds a native toolchain, CMake and
ABI splits to a Java-only build for ~300 lines of C.

The SPAKE2 port is the one piece of real cryptography written here, so it gets the strictest
treatment: a faithful line-by-line port of `spake25519.cc` (including the
`password_scalar` "copy-paste error" compatibility adjustment that BoringSSL keeps for adb; adbd
runs that code), the `M` and `N` points taken from BoringSSL's precomputed tables (the first
entry of each table is the point itself in `(y+x, y−x)` form) and checked in a test against the
points regenerated from the seeds `"edwards25519 point generation seed (M)"` / `"(N)"` with
BoringSSL's generation procedure, a round-trip test (alice and bob in Java derive the same key,
and a wrong password gives a different key), and a test that the hash transcript order matches
(`my_name, their_name, my_msg, their_msg` for alice; swapped for bob; then the shared point;
then the password scalar; each with a little-endian 64-bit length prefix). The arithmetic is not
constant-time; the scalar is single-use and the exchange is local and one-off, which the code
comment states.

### Keys and certificates

- RSA-2048 key pair generated in the Android Keystore (`AndroidKeyStore`, `PURPOSE_SIGN`,
  digests SHA-256, paddings PKCS1 and PSS, no user authentication), certificate subject
  `CN=Wristline`, long validity. The Keystore produces the self-signed certificate itself. The
  private key never leaves the hardware-backed store; TLS client authentication uses it through
  the standard `KeyManager`. TLS 1.3 signs `CertificateVerify` with RSA-PSS, so PSS padding
  must be declared.
- **Spike first.** Task 1 of the plan is a throwaway that pairs and connects to the owner's
  Android 17 phone with a Keystore key. If the Keystore key cannot do the TLS 1.3 handshake on
  some device, fallback: a software RSA key in app-private storage, wrapped with an AES key from
  the Keystore. The spike decides; the design accepts either, Keystore preferred.
- The key is deleted when voice through the watch is turned off for the last app (the 1.2
  behaviour) and on "Forget" from the Voice screen. `LegacyAdbKey` is replaced by the new key
  code and its one-time cleanup of 1.2's PEM file stays.
- adbd remembers the key as `wristline@<phone model>`; the docs say where to revoke it
  (Developer options › Wireless debugging › Paired devices, or "Revoke debugging authorisations").

### Permissions and manifest

Add `INTERNET` and `ACCESS_NETWORK_STATE`. Add `FOREGROUND_SERVICE` and
`FOREGROUND_SERVICE_SHORT_SERVICE` for the setup service (shortService type, ~3 minutes, API
34+; plain foreground service below). Add `android:usesCleartextTraffic="false"` is irrelevant
(adb is TLS); instead add a network security config that pins nothing and allows nothing special,
so the manifest states the app's network use is ordinary TLS. The Play data-safety form will say:
no data collected or shared; internet permission used to configure the phone itself.

### Flow (phone only), ported from 1.2 with these changes

1. **Voice screen.** Each app in `SET_UP` or `NEEDS_REPAIR` shows two buttons: "Set up on this
   phone" (primary) and "Set up with a computer" (today's dialog, unchanged). Apps that need no
   setting (`!needsAppOp`, e.g. Instagram, or Telegram/Signal if spike D passes) keep showing ON.
2. **Consent dialog** naming each app and the change in plain words, with "Agree" / "Cancel".
   Nothing starts before Agree.
3. **Wi-Fi check.** No Wi-Fi: explain and stop. (adbd's Wireless debugging only runs on Wi-Fi.)
4. **Guide.** If Developer options are off (`Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`), show
   the "tap Build number seven times" step with a button to About phone. Then a step with a
   button to Developer options: "turn on Wireless debugging, tap Pair device with pairing code".
5. **Setup service** (foreground, shortService): discovers the pairing service, posts a
   notification with an inline reply field for the six-digit code so the user never leaves
   Settings, pairs, discovers the connect service, connects, runs `appops set` + `appops get` for
   each app, verifies `ignore`, restarts the apps (`am force-stop`, as `AppOpsCommands.restart`
   does today), marks each app set up (`FullMode.markSetUp`), posts the result notification with
   a "Turn off Wireless debugging" action. Already paired and reachable: skip straight to the
   commands. Timeouts, wrong code and re-opened pairing dialog (new port) handled as in 1.2.
6. **Repair.** `FullMode.onCallDropped` today posts the repair notification. New: if the key
   exists and adbd is reachable, repair silently in the background and record it; else the
   notification opens the Voice screen, which resumes at the right step.
7. **Turning off.** "Turn off for <app>" runs `appops set … allow` through the same path when
   reachable, else shows the computer command as today; the last app off deletes the key.
8. **Diagnostics.** New events: `setup_started`, `setup_done`, `setup_failed` (with a short
   reason code, never the code or key), `repair_silent`. Never log command output.

### Networking rules

- Connect only to addresses that `ConnectivityManager` reports as this phone's own Wi-Fi link
  addresses (same filter as 1.2's `AdbDiscovery.ownAddresses`); prefer IPv4.
- Discovery and connections have hard timeouts (discovery 3 s per step, connect 4 s, socket
  read 15 s, pairing 20 s, whole setup 165 s inside shortService's limit).
- Accept adbd's TLS certificate without verification at pairing (the protocol's design: trust
  comes from the SPAKE2 code). At connect time, also no CA check (adbd is self-signed), but the
  socket is only ever opened to an own address found through mDNS, and adbd proves itself by
  completing the adb handshake after TLS. State this in code comments and in the security
  section of the docs.
- One connection at a time, closed in `finally`.

## Security review items for the final whole-branch review

- No GPL/LGPL in `./gradlew :app:dependencies`; `gradle/verification-metadata.xml` unchanged
  except for nothing (no new artifacts).
- SPAKE2 port compared against `spake25519.cc` by a reviewer who reads both; constants match.
- Pairing code, keys, SPAKE2 messages and shell output are never logged (`Log.*`) or recorded.
- Shell commands are exactly `AppOpsCommands` strings; no concatenation of user input.
- All sockets go to own addresses; no DNS names anywhere.
- Keystore key not exportable; software fallback (if used) file mode 0600 in `filesDir`.
- Foreground service stops itself on every path (`finish`, `onTimeout`, `onDestroy`).

## Left to the builder

- Exact Edwards arithmetic representation (affine `BigInteger` with modular inverse per
  operation is fine).
- Whether shell runs over `shell,v2,raw:` (exit codes) or legacy `shell:` with output parsing;
  v2 preferred if adbd advertises `shell_v2` in its CNXN banner.
- Notification channel reuse vs a new one for setup progress.
- Layout details of the guide steps on the Voice screen (follow the existing card style).
- Test doubles for `NsdManager` and sockets (Robolectric shadows exist for NSD; sockets can use a
  local `ServerSocket` in tests for framing).

## Plan outline (for superpowers:writing-plans)

1. **Spike (throwaway branch, owner's phone):** Keystore RSA key, pair with the real adbd on
   Android 17, connect over STLS, run `echo ok`. Proves the protocol facts above and the Keystore
   TLS path. Report, then discard the branch; the real tasks start from a clean tree.
2. Ed25519 + SPAKE2 with tests.
3. adb key (Keystore) + `android_pubkey` encoding + PeerInfo with tests.
4. Pairing client with a fake adbd in tests (TLS server socket in the test, scripted frames).
5. adb transport (CNXN/STLS/shell) with a scripted fake in tests.
6. Discovery.
7. Setup service, notifications, step logic, strings EN+IT.
8. Voice screen: buttons, consent, guide, repair, turn off, forget key.
9. Docs: README, HOW_TO, privacy section, Play data-safety notes, diagnostics events; remove the
   obsolete 1.3 line "Wristline has no shell access of its own".
10. Device check on the owner's phone: full flow from a fresh key, repair after `pm clear`, turn
    off, forget.

Global constraints for every task: Java 21, no new Gradle dependency, strings in EN and IT,
no logging of secrets, every network call bounded by a timeout, minSdk 31.
