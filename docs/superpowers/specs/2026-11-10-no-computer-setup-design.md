# Wristline without a computer

Date: 2026-11-10. Status: Phase 0 done (see "Spike results"), owner leading, Phase 1 plan at
`docs/superpowers/plans/2026-11-10-listener-rebind-and-voice.md`.

## Spike results (2026-11-10, owner's vivo X300 Pro, Android 17, Pixel Watch 5)

**Spike A, deny the app's Phone permission instead of the app-op: FAILS for WhatsApp.** With
`READ_PHONE_STATE` revoked (`pm revoke`), Android did set the uid app-op to `ignore` by itself, the
same thing the adb command does. But WhatsApp checks the permission before answering: pressing
Answer brought up WhatsApp's own dialog ("WhatsApp needs to know whether you are on an active
call... Tap Continue > Allow") and the call was never answered, while the watch showed it as ended.
So for WhatsApp the permission must stay granted and only the app-op can be `ignore`, which needs
shell. Telegram and Signal not tested; they may tolerate a denied permission (optional, see plan
Task 7).

**Spike B, route the app's call audio to the watch from outside, with no Telecom call: dead end.**
A throwaway app tried `setMode(IN_COMMUNICATION)` + `setCommunicationDevice(SCO)` during a WhatsApp
call answered on the phone. No Bluetooth call device was available: the Pixel Watch is connected on
the headset profile (`HeadsetStateMachine state=Connected`) but is not the *active* headset device
(`mActiveDevice: null`), so AudioService lists no SCO device. Android keeps watches inactive by
default; Telecom makes the watch active when Wristline's call routes to Bluetooth, and
`BluetoothHeadset.setActiveDevice` is a system API. So an active Telecom call is the only way a
normal app gets the watch's audio link, and an active Telecom call is exactly what makes WhatsApp
hang up unless its call-state access is cut. No way around the app-op for WhatsApp.

**Spike C (2026-11-11), answer the app first, then go active: FAILS for WhatsApp.** Throwaway
branch `spike-answer-first`. The mirror stays ringing while Wristline presses WhatsApp's Answer,
then goes active and routes to the watch. WhatsApp accepts the Answer (no dialog), Telecom makes
the Pixel Watch the active headset and WhatsApp itself switches its route to Bluetooth SCO within
300 ms, so the watch *is* reachable by WhatsApp's audio. But the moment the mirror is active
WhatsApp puts its call on hold (it sees the Telecom call). Variant B ended the mirror 1.5 s after
routing: WhatsApp resumed and asked AudioService to start SCO, the start failed, and 1.4 s after
the mirror ended Bluetooth dropped the watch as the active headset (`Active Device Changed: null
for profile: HEADSET`), the SCO device went away and WhatsApp's call ended. Evidence: `dumpsys
audio` mode/route log and `dumpsys bluetooth_manager --print` active-device log, 13:30:03-13:30:07.
So the watch is an active headset only while a Telecom call is active, and WhatsApp holds while a
Telecom call is active: no ordering or timing between the two gets around the app-op.

**Conclusion.** Voice through the watch for WhatsApp needs a shell-level app-op change. Owner's
decision (2026-11-11): no Shizuku and no dependency on another installed app. If spike D (Telegram
and Signal with the Phone permission denied in Settings) does not remove the need for shell, the
computer-free path is Wristline's own adb client over the phone's Wireless debugging (one-time
pairing on the phone, INTERNET permission, an app-owned key the owner accepts), designed in a
separate document. Phase 3 below is superseded by that decision. Everything else in this plan
(Play for the watch install, the Xiaomi listener fix) stands.

## Intent

Today a new user needs a computer with `adb` twice, and a tolerance for Developer options:

1. **Install the watch app.** Wear OS has no file installer, so the watch APK goes on with
   `adb pair` / `adb connect` / `adb install` over Wireless debugging.
2. **Voice through the watch.** WhatsApp, Telegram and Signal hang up when Wristline's Telecom call
   goes active, because they see the phone's call state. The fix is a shell command per app:
   `adb shell appops set --uid <pkg> READ_PHONE_STATE ignore`.

On top of that, the sideloaded phone APK brings a Play Protect warning and Android's "Allow
restricted settings" detour before Notification access can be switched on.

The goal is that someone who has never heard of adb can install Wristline on phone and watch,
answer calls on the watch and talk through it, using only the phone and the watch.

Note on wording: none of this needs root or a "superuser". The adb commands run as the `shell`
user, which only a computer (or an adb client acting as one) can reach. Nothing in this plan
asks for root, and Wristline keeps its "no internet permission, no shell of its own" stance.

## Goals

- Install phone and watch app with no computer.
- Voice through the watch with no computer, for WhatsApp, Telegram and Signal.
- Keep working for people who already set up with adb, with no extra steps on update.
- Keep the privacy story: no internet permission, no servers, no adb key in app storage.

## Non-goals

- Root support. Never.
- Replacing the Pixel Watch companion or the Wear OS Data Layer.
- Changing how calls are detected or answered.

## Anti-goals

- Bringing back the 1.2-era in-app Wireless debugging client (needs the internet permission and
  an RSA adb key on the phone; deliberately removed before 1.3).
- A setup that works on the developer's phone and nowhere else.

## The two problems and their fixes

### A. Voice through the watch: revoke the app's Phone permission instead of the app-op

`FullMode.needsAppOp` already says: an app that does not hold `READ_PHONE_STATE` needs no setting.
That is why Instagram works with no setup. The app-op trick (`appops set ... ignore`) makes Android
behave, for that app, as if the permission were not granted: `TelecomServiceImpl.canReadPhoneState`
checks the permission and then the app-op, and `getCallState` throws `SecurityException` to apps
targeting Android 12+ either way. The apps already cope with that exception today, since the app-op
path works.

So the user can get the same result from Settings → Apps → WhatsApp → Permissions → Phone →
**Don't allow**. No computer. Android also kills the app's process when a runtime permission is
revoked, which replaces the "close the app once" step.

Observed on the owner's phone on 2026-11-10: Telegram and Signal currently carry
`REVOKED_COMPAT` on `READ_PHONE_STATE` with uid mode `ignore` (the adb path); WhatsApp is on
`allow`. This is the state a spike must start from.

**What must be proven on a real phone before building (Phase 0):**

- WhatsApp, Telegram and Signal, with Phone permission denied: incoming call answered on the watch
  keeps the voice on the watch; a call started from the watch works; hang up from the watch works.
- None of the three apps nags, blocks calling, or re-requests the permission in a way that would
  make users grant it back by reflex.
- After the user grants Phone again, Wristline notices (`needsAppOp` turns true, state goes back to
  SET_UP) and tells them, instead of dropping the next call.

Known side effect, same as today's adb path: while on a WhatsApp/Telegram/Signal call the app
won't notice a regular phone call coming in. Denying the Phone group also removes `CALL_PHONE` from
that app, which these apps don't need for VoIP calls.

If the spike fails for one app (say WhatsApp refuses to call without Phone permission), that app
keeps the computer path and the others move to the new one. The decision is per app.

### B. Watch install and sideload friction: Google Play

Wear OS only gets apps from Play or adb. Play is therefore the only computer-free install, and it
also removes the Play Protect warning, the restricted-settings detour (Play installs are not
restricted; see `InstallSource.restrictedSettingsHint`), and gives both apps automatic updates.

Decisions in this plan (owner can override):

- **One Play app, two form factors.** Phone and watch already share the package `app.wristline`
  with distinct version codes (watch = phone + 1000). Play wants an App Bundle per form factor on
  the same listing: a phone bundle and a Wear OS bundle.
- **Keep the current release key.** Enrol in Play App Signing by uploading the existing release
  keystore as the app signing key (Play allows an existing key at enrolment). Then a Play install is
  signature-identical to a GitHub-release install: sideloaded users can switch to Play without
  uninstalling, and a GitHub watch APK still talks to a Play phone app. If Google is allowed to
  generate a new key instead, every existing user must uninstall and lose settings, and the two
  distribution channels can never mix. Not acceptable.
- **GitHub releases stay** as the channel for people who prefer it, with the adb instructions
  moved to an appendix.
- **Phone app sends the user to the watch's Play Store directly.** Wear's `RemoteActivityHelper`
  opens `market://details?id=app.wristline` on the watch through Play services, so the "Install
  Wristline on your watch" message becomes a button and the user never looks for the listing by
  hand. No internet permission needed on the phone app for this.

Play requirements that apply (verify against current Play Console text when doing it):

- Developer account: USD 25 one-time; identity verification; a personal account created after
  November 2023 must run a closed test with at least 12 testers for 14 days before production
  access. Budget three to four weeks of wall-clock time, mostly waiting.
- App Bundles only (`bundleRelease` for both modules; add any new artifacts to
  `gradle/verification-metadata.xml`).
- Target API: already 36; fine for 2026.
- Data safety form: Contacts (names only, to the user's own watch, not shared), call activity kept
  on device, no data sent to the developer. PRIVACY.md already says this; the form restates it.
- Sensitive permissions: Notification listener, `SYSTEM_ALERT_WINDOW`, `READ_CONTACTS`,
  `CALL_PHONE`. Play reviews these against the "core functionality" rule; the listing text and
  in-app rationale must show that each one is the feature itself. No SMS or Call Log permissions
  are requested, so no declaration form beyond the standard ones is expected.
- Wear OS: the watch bundle goes through Play's Wear OS quality review before it appears on the
  watch Play Store. The app already uses round-screen layouts, a tile and a complication; expect
  the review to ask for watch screenshots and to check that the app does something useful on its
  own screen (it does: contacts, recents, call screen).
- Listing assets: phone screenshots, watch screenshots, icon, feature graphic, short and full
  description, privacy policy URL (PRIVACY.md on GitHub works).
- Licence: PolyForm Noncommercial is fine for a free listing. Nothing in the app is sold.

## What changes in the app

### Phase 0: spike on the owner's phone (half a day, no code)

1. Put WhatsApp back to `allow` so all three apps start from the normal state:
   `adb shell appops set --uid com.whatsapp READ_PHONE_STATE allow` (Telegram and Signal too, so the
   test exercises the new path and not the old one).
2. In Settings, deny Phone for WhatsApp, Telegram and Signal.
3. For each app: receive a call, answer on the watch, confirm voice is on the watch, hang up from
   the watch; then call from the watch. Note any prompt the app shows.
4. Grant Phone back to one app, open Wristline, confirm it shows that app as needing setup.
5. Record results per app in this document.

### Phase 1: voice setup from Settings (SUPERSEDED by the spike results)

Kept for the record. Only the optional per-app variant for Telegram and Signal survives, as Task 7
of the Phase 1 plan, and only if those two pass the same spike WhatsApp failed.

- `VoiceActivity`: for each app in SET_UP or NEEDS_REPAIR, the primary button becomes "Open app
  settings" (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for that package) with the instruction
  "Permissions → Phone → Don't allow". There is no public intent that opens one permission
  directly.
- State: ON when `!needsAppOp`, as now. `fullModeOn` and `markSetUp` stay only for the adb path;
  the permission path needs no "I've run it" because Wristline can read the permission itself.
- Returning to the screen (`onResume`) re-reads the permission and refreshes, so the user sees
  green the moment they come back from Settings.
- Repair: when a call drops while taking audio, `FullMode.onCallDropped` already marks the app
  broken. The repair notification and dialog point to the same Settings route. If the permission
  is still denied (so the drop had another cause), say so instead of sending the user in circles.
- Turn off: "Permissions → Phone → Allow" in the same screen; for apps that are on the adb path
  (`fullModeOn` true and permission still granted) keep today's reset command dialog as the
  fallback.
- Computer path stays as an "Advanced" fold at the bottom for apps that fail the spike, with the
  existing `ComputerSetup` dialog.
- Diagnostics: record per app whether voice is on through "permission denied" or "app-op", so a
  diagnostic report tells the two apart.
- Tests: extend `VoiceStateTest`, `FullModeTurnOffTest`, and the Robolectric screen tests for the
  new button and the onResume refresh. Dead strings (`with_a_computer*`, "Built-in ADB setup"
  comment) go.
- Docs: README "Voice through the watch" becomes three taps in Settings; adb moves to an appendix.

### Phase 1b: listener unbound on Xiaomi (one day)

Field report 2026-11-10, Xiaomi 2510DPC44G, Android SDK 37, Wristline 1.3: "Missing permissions:
none" but "Notification listener: NOT connected". Events show `listener_connected` three times
over two hours, then long runs of `listener_rebind` with no reconnection, and never a
`listener_disconnected`. So the process is killed without the callback, and HyperOS doesn't bind
the service again on its own. `requestRebind` from `MainActivity.onResume` is the only recovery and
it doesn't work there. The status card shows "Ready" the whole time.

Fixes, all in Phase 1 scope:

- Status card: a listener that is granted but not connected is not "Ready". Show "Wristline isn't
  receiving notifications" with the vendor hint (`BatteryHint` already has the Xiaomi Autostart /
  No restrictions text) and a Fix button.
- Stronger rebind: after `requestRebind` fails to connect within a few seconds, disable and
  re-enable the `RelayListener` component with `PackageManager.setComponentEnabledSetting`
  (`DONT_KILL_APP`), which makes the system bind the listener again. Widely used workaround for
  exactly this. Verify on the Xiaomi phone.
- Rebind from more places than `onResume`: the Quick Settings tile, the watch tile's chip, and a
  periodic check while relaying is on, so a phone that sits in a pocket recovers without the user
  opening the app.
- Event log: record how long the listener was unbound when it reconnects, so reports show the gap.

### Phase 2: Google Play (two to four weeks, mostly waiting)

1. Create the developer account; start identity verification on day one.
2. Enrol in Play App Signing with the existing release key. Keep the keystore where it is
   (`WRISTLINE_KEYSTORE_PROPERTIES`), it signs the upload too.
3. Gradle: `:app:bundleRelease` and `:wear:bundleRelease`; add a `release-bundles` note to the
   README build section; refresh verification metadata from an empty Gradle home; CI builds debug
   bundles so breakage shows early.
4. Play Console: listing, Data safety, content rating, permission rationale, Wear OS form factor,
   closed test track with phone + watch bundles.
5. Recruit testers (12 for a personal account) from the GitHub users; they install from Play on
   both devices, which is itself the test of this plan.
6. In-app: `watch_app_missing` and `watch_calling_message` get an "Install on watch" button using
   `RemoteActivityHelper` (androidx.wear.remote-interactions). Hide the "Allow restricted settings"
   hint for Play installs (already done by `InstallSource`).
7. After 14 days and the Wear OS review, promote to production. Update README: Play first, GitHub
   second, adb in the appendix.

### Phase 3: voice through the watch without a computer, via Shizuku (SUPERSEDED 2026-11-11: no Shizuku, see Conclusion)

Promoted from optional after the spike: it is the only computer-free path for WhatsApp.

- Shizuku runs a shell-level service that the user starts on the phone alone: Developer options →
  Wireless debugging, then Shizuku pairs with it from a notification (Android 11+). Shizuku is on
  Play. After a reboot it must be started again, but Wristline needs it only at setup and repair
  time, since the app-op persists.
- Wristline adds the Shizuku API dependency (`dev.rikka.shizuku:api` and `:provider`, Apache 2.0,
  added to NOTICE and to `gradle/verification-metadata.xml`) and asks for Shizuku's permission the
  first time the user taps "Set up". With it, Wristline runs `appops set --uid <pkg>
  READ_PHONE_STATE ignore` (and `allow` to turn off) through Shizuku's shell process. No internet
  permission, no adb key, no shell of its own: the privacy text changes from "you do it with a
  computer" to "you do it with a computer, or with Shizuku on the phone".
- Voice screen offers, per app: "Set up with Shizuku" (primary when Shizuku is installed, with a
  link to Play and a three-step guide when it isn't) and "Set up with a computer" (today's dialog).
  The existing `with_a_computer` strings are the second option.
- Verification stays as it is: `FullMode.markSetUp` after a successful command, repair flow
  unchanged.
- Phone-only watch install for GitHub users (an on-phone adb client such as Wear Installer 2) is
  documented, not built. Play covers it for everyone else.

## Left to the builder

- Exact copy for the Settings route per Android skin (vivo, Samsung, Xiaomi name the screens
  differently; `BatteryHint` already has per-vendor text and can be the model).
- Whether the "Advanced" fold is a collapsed card or a second screen.
- Screenshot and listing assets.

## Play research (2026-11-10)

Sourced notes with URLs: `2026-11-10-play-requirements.md` next to this file. What changes the
plan:

- Existing release key as the Play app signing key: allowed, via "Change key" + the PEPK tool,
  RSA 2048+ bits, and it must be done before the app reaches open testing or production. New apps
  get auto-enrolled in "quantum-ready hybrid signing" with Google keys; check in the Console that a
  custom key still applies, and compare `apksigner` fingerprints on an internal-track install
  before inviting testers.
- Personal account: still 12 testers opted in for 14 continuous days, then "Apply for production",
  reviewed in about a week.
- Target API 36 is the floor since 2026-08-31; Wear OS needs 35 and 64-bit (2026-09-15). Both fine.
- A new Contacts policy applies to apps targeting API 37: a declaration and proof that the Contact
  Picker is not enough. Not now, but it lands with the next targetSdk bump (mandatory 2027-01-27).
- Data safety: on-device processing and phone-to-own-watch transfer count as no collection; declare
  none collected, none shared, privacy policy URL required.
- Wear OS: add the form factor in Advanced settings, dedicated Wear track, same listing; one 1:1
  screenshot of at least 384 px, no frames; common rejections are missing screenshot, round-screen
  layout problems, broken features.
- `RemoteActivityHelper`: Google's sample uses the `https://play.google.com/store/apps/details?id=`
  URL, not `market://`. Use the https form and test on the real watch.
- Listing: don't call the app "open source" (PolyForm Noncommercial is source-available).

## Open questions for the owner

1. Individual or organisation Play account? Individual is cheaper and faster but shows a personal
   name on the listing (address only for merchant accounts).
2. Confirm: the existing release key becomes the Play app signing key.
3. Resolved by the spike: WhatsApp keeps the shell path; Shizuku is the computer-free version of
   it (Phase 3).
4. Order of work proposed: Phase 1 plan (listener fix) now, Phase 2 (Play) in parallel since it
   is mostly waiting, Phase 3 (Shizuku) after Phase 1.
