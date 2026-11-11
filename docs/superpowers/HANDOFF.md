# Handoff: Wristline without a computer

Last updated 2026-11-11, morning. Read this first when resuming.

## The goal

Make Wristline usable by anyone: no adb, no computer, for both the watch app install and voice
through the watch. Owner (Lucio) leads with Claude as planner; Claude dispatches subagents for
code (Opus/Sonnet) and research.

## What is settled

- **Not root, shell.** The two computer steps are `adb install` on the watch and
  `adb shell appops set --uid <pkg> READ_PHONE_STATE ignore` per app.
- **Watch install → Google Play.** Only computer-free path on Wear OS. Individual developer
  account (owner's decision). Enrol Play App Signing with the existing release key ("Change key" +
  PEPK, before open testing) so Play and GitHub installs share a signature. 12 testers for 14 days
  before production. Notes with sources: `docs/superpowers/specs/2026-11-10-play-requirements.md`.
- **Voice through the watch for WhatsApp needs shell, proven.** Two spikes on the owner's vivo
  X300 Pro (Android 17) with a Pixel Watch 5:
  - Denying WhatsApp's Phone permission: WhatsApp blocks Answer with its own "needs to know
    whether you are on an active call" dialog. Dead.
  - Forcing call audio to the watch from a second app without a Telecom call: the watch is
    HFP-connected but never the *active* headset device, and only Telecom (system API) can make
    it active. Dead.
  - So the computer-free path is **Shizuku** (phone-only shell). Planned as Phase 3, not started.
- **Xiaomi/HyperOS kills the listener** without `onListenerDisconnected` and never rebinds it;
  the status card still says Ready. This is the code work in progress (Phase 1 plan).

All of it is in `docs/superpowers/specs/2026-11-10-no-computer-setup-design.md` (sections
"Spike results", Phases 1b, 2, 3, "Play research").

## Work in progress

- Branch `listener-rebind` off `main`. **Phase 1 code is done**: Tasks 1-5 implemented, task-reviewed,
  final whole-branch review clean after one fix wave (head `1f91fa7`). CI line green at that head.
  Task 7 skipped (gate not met). Deferred minors (none blocking) are listed in the final review of
  2026-11-11; the biggest: QS tile rebind lines are unthrottled, Fix gives no feedback for 5 s,
  `seenAt` goes stale while relaying is paused.
- Release `v1.3` built from `1f91fa7` and installed on the owner's phone (vivo). Field APK for the
  Xiaomi reporter: `~/Desktop/Wristline-out/wristline-1.3-listener-rebind-1f91fa7.apk`.
- Task 6 device check on the vivo: **no way to leave the listener unbound** (`am kill` ignored,
  `force-stop`/`am crash` restart and rebind, `kill -9` and `pm disable-user` refused). Recovery
  paths and the card could not be exercised on this phone; the Xiaomi field report is the gate.
  Expect `listener_toggled job` then `listener_connected after … unbound`; if the toggle doesn't
  reconnect on HyperOS, report to the owner before changing anything. Also check after the field
  run whether the periodic job survives a HyperOS kill (`dumpsys jobscheduler | grep wristline`).
- Telegram contacts (2026-11-11): the owner's phone had Telegram's Contacts permission denied and
  sync off, so no Telegram rows exist in the contacts provider. Not a Wristline bug; already in the
  docs. Possible follow-up: detect "Telegram installed, zero rows" and show the hint on the card.

### To resume

1. `git checkout listener-rebind`. The branch is ready to merge or PR (superpowers:finishing-a-development-branch);
   the owner decides. The SDD workspace was deleted after the clean final review; `git log` is the record.
2. Tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`.

## Owner's phone state to know about

- WhatsApp: Phone permission granted again, app-op `allow` (as found before the spike).
- Telegram and Signal: app-op was reset to `allow` during the earlier session (by the other
  Claude session), so their voice-through-the-watch setup is currently **undone** on the owner's
  phone. Next Telegram/Signal call answered in full mode will drop and trigger Repair. To restore:
  `adb shell appops set --uid org.telegram.messenger READ_PHONE_STATE ignore` and the same for
  `org.thoughtcrime.securesms`, then close each app once.
- Spike app `app.wristline.scospike` was uninstalled.

## Next steps, in order

1. Merge or PR `listener-rebind` (owner's call), then release v1.3 on GitHub.
2. Send the field APK to the Xiaomi reporter and wait for a diagnostic report (Task 6 step 6).
3. Phase 2, Play, can start any time since it is mostly waiting: create the individual account,
   start identity verification, enrol the existing key, add `bundleRelease` tasks, listing assets.
4. Phase 3, Shizuku: brainstorm the voice screen flow, then plan (superpowers:writing-plans).
5. Optional: spike Telegram and Signal with Phone permission denied (Task 7 gate).

## Open questions for the owner

- Confirm the existing release key becomes the Play app signing key (recommended; the only way
  Play and GitHub installs stay compatible).
- Who are the 12 testers? GitHub users of the project are the natural pool.
