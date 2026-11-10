# Handoff: Wristline without a computer

Last updated 2026-11-10, late evening. Read this first when resuming.

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

- Branch `listener-rebind` off `main` (commit a3c5f1f holds the docs).
- Plan: `docs/superpowers/plans/2026-11-10-listener-rebind-and-voice.md`, 7 tasks. Task 7 is
  optional and skipped (gated on a Telegram/Signal spike that hasn't run).
- Execution: superpowers:subagent-driven-development. The ledger is
  `.superpowers/sdd/2026-11-10-listener-rebind-and-voice/progress.md` (git-ignored). It names
  which tasks are complete; trust it and `git log` over memory.
- When this handoff was written, Task 1 (log the unbound gap) was dispatched to a Sonnet
  implementer. Check the ledger and `git log --oneline main..listener-rebind` to see how far it
  got.

### To resume

1. `git checkout listener-rebind`, read the ledger, then invoke
   `superpowers:subagent-driven-development` and continue at the first task without a
   `complete` line. Scripts live in the skill's `scripts/` directory (`task-brief`,
   `review-package`).
2. Tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`.
3. Task 6 is a device check on the owner's phone (serial and release build command in memory
   `build-and-install.md`), then an APK for the Xiaomi field reporter.

## Owner's phone state to know about

- WhatsApp: Phone permission granted again, app-op `allow` (as found before the spike).
- Telegram and Signal: app-op was reset to `allow` during the earlier session (by the other
  Claude session), so their voice-through-the-watch setup is currently **undone** on the owner's
  phone. Next Telegram/Signal call answered in full mode will drop and trigger Repair. To restore:
  `adb shell appops set --uid org.telegram.messenger READ_PHONE_STATE ignore` and the same for
  `org.thoughtcrime.securesms`, then close each app once.
- Spike app `app.wristline.scospike` was uninstalled.

## Next steps, in order

1. Finish Phase 1 (listener fix) through the plan, final review, then
   `superpowers:finishing-a-development-branch`.
2. Device check (Task 6) + APK to the Xiaomi reporter.
3. Phase 2, Play, can start any time since it is mostly waiting: create the individual account,
   start identity verification, enrol the existing key, add `bundleRelease` tasks, listing assets.
4. Phase 3, Shizuku: brainstorm the voice screen flow, then plan (superpowers:writing-plans).
5. Optional: spike Telegram and Signal with Phone permission denied (Task 7 gate).

## Open questions for the owner

- Confirm the existing release key becomes the Play app signing key (recommended; the only way
  Play and GitHub installs stay compatible).
- Who are the 12 testers? GitHub users of the project are the natural pool.
