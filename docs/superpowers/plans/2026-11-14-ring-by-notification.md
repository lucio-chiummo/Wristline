# Ring by Notification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let calls ring, and be answered or declined, on watches whose companion app loses the Answer of Wristline's Telecom call (OPPO Watch X3 + OHealth, two field reports), by ringing the watch with Wristline's own Answer/Decline notification instead.

**Architecture:** A user setting, `Prefs.ringByNotification`. When on, an incoming call makes no Telecom call: `WatchRing` posts Wristline's Answer/Decline notification and re-alerts it every 4 s until the call is answered, declined, ends, or 60 s pass. The buttons go through the existing `ActionReceiver`, which presses the calling app's own Answer/Decline. The same `WatchRing` replaces the one-shot fallback notification used when Telecom refuses a call. "Ring my watch" follows the setting, and Home offers the setting when a Telecom test call goes unanswered.

**Tech Stack:** Java 21, Android notifications, JUnit 4 + Robolectric 4.17. Tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`.

**Spec:** this plan's Requirements section. Owner's decisions (2026-11-14): notification only, no Telecom call alongside; buzz every few seconds; offer the mode after a failed test, plus a manual switch. Branch: `ring-by-notification`, stacked on `report-facts`.

## Requirements

R1. Setting "Ring with a notification", off by default, in Home → **When the watch rings**.
R2. Setting on, incoming call allowed by the existing rules: no Telecom call is made. Wristline posts a notification titled with the caller (as today's fallback: title = caller, text = "Incoming <App> call"), category call, with **Answer** and **Decline**, on a channel with high importance, vibration and no sound (the calling app is already playing its ringtone on the phone).
R3. The notification alerts again every 4 s (`WatchRing.REPEAT_MS = 4000`) and stops after 60 s (`WatchRing.MAX_MS = 60_000`) or as soon as the call is answered (watch or phone), declined, or ends.
R4. Answer/Decline press the calling app's own action and are logged `answered_on_watch` / `declined_on_watch` with detail `notification` (existing behaviour of `ActionReceiver`). The call is answered in the app; the voice is on the phone. Voice through the watch in this mode is out of scope (see end).
R5. The call is logged `incoming <app> notification`. Missed-call handling, history, hang-up card: unchanged (the hang-up card already appears whenever there's no Telecom call shown on the watch).
R6. Telecom refusing a call (setting off) rings the same repeating way instead of the single alert.
R7. Ring my watch, setting on: the same notification titled "Wristline · Test"; Answer or Decline there = reached; 30 s (`TestCall.RING_MS`) unanswered = missed. Logged `test_call started notification`, then `answered_on_watch`/`declined_on_watch` `test call`, then `test_call reached|missed` as today. Toast: "Your watch should buzz now. Tap Answer or Decline there."
R8. Setting off and a test call ends missed while Home is showing: Home asks **"Did the watch ring?"** — "If it rang but Answer did nothing, Wristline can ring your watch with a notification instead. It buzzes every few seconds, with Answer and Decline." Buttons: **Switch and ring again** (turns the setting on, starts a test), **Ring again** (same mode), **Cancel**. The status card's button in the "test call didn't reach the watch" state opens the same question. Setting on: no question, the button just rings; the card's hint reads "Check that the watch is connected and that it shows Wristline's notifications. Then try again."
R9. The diagnostic report says `Ring by notification: on|off`.
R10. English and Italian strings; README and HOW_TO gain a short "Answer does nothing on my watch" entry.

## Global Constraints

- No new permissions, no new libraries.
- The notification must not be ongoing: Wear OS doesn't show a phone's ongoing notifications.
- Calls started from the watch, voice through the watch for Telecom-rung calls, and the Pixel Watch path are not touched.
- No caller name in logs or the event log.

## Review Focus

1. A second call from the same app while one rings must not restart or duplicate the ring (tracker already ignores it): test in Task 2.
2. Answering on the phone must stop the buzzing at once: test in Task 2.
3. Turning the setting off while a call is ringing by notification: that call carries on ringing by notification and its buttons work: test in Task 2.
4. A real call arriving during a notification test call replaces the test (as it does for a Telecom test call): test in Task 3.
5. Answer tapped after the call already ended (stale notification on the watch) must do nothing and not log an answer for a later call: test in Task 2.

---

### Task 1: The setting and the ringer

**Files:**
- Modify: `app/src/main/java/app/wristline/Prefs.java`, `app/src/main/java/app/wristline/StatusNotifications.java`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-it/strings.xml`
- Create: `app/src/main/java/app/wristline/WatchRing.java`
- Test: `app/src/test/java/app/wristline/WatchRingTest.java` (create, Robolectric)

**Interfaces (produced):**
- `static boolean Prefs.ringByNotification(Context)` (default false), `static void Prefs.setRingByNotification(Context, boolean)`; key `ring_by_notification`.
- `StatusNotifications.CHANNEL_RING = "watch_ring"`: `IMPORTANCE_HIGH`, `setSound(null, null)`, `enableVibration(true)`; name `R.string.channel_ring` = "Calls ringing by notification" / "Chiamate che squillano con una notifica".
- `final class WatchRing`:
  - `static final long REPEAT_MS = 4000; static final long MAX_MS = 60_000;`
  - `static void start(Context context, String title, String text, long maxMs)` — posts notification id `StatusNotifications.ID_INCOMING_FALLBACK` on `CHANNEL_RING` (small icon `ic_notification`, category call, auto-cancel, `setWhen(now)`, `setTimeoutAfter(maxMs)`, actions Answer → `ActionReceiver.ANSWER` (request code 1), Decline → `ActionReceiver.DECLINE` (request code 2), both immutable broadcasts), then re-posts it every `REPEAT_MS` on the main looper until `stop` or `maxMs` since start. A `start` while ringing restarts.
  - `static void stop(Context context)` — cancels the notification and the repeats.
  - `static boolean ringing()`.

- [ ] **Step 1: Write the failing tests** (`WatchRingTest`; advance time with `ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)`; read the notification with `shadowOf(nm).getNotification(StatusNotifications.ID_INCOMING_FALLBACK)`)

```java
@Test public void settingIsOffByDefault()            // Prefs.ringByNotification false; set true → true
@Test public void postsAnswerAndDecline()            // start → notification present, category call, 2 actions titled "Answer","Decline", not ongoing, channel "watch_ring"
@Test public void alertsAgainEveryFourSeconds()      // when0 = n.when; idle 4000 → notification.when > when0; ringing() true
@Test public void stopsAfterItsLimit()               // start(..., 10_000); idle 12_000 → ringing() false; `when` no longer changes after another 4000
@Test public void stopCancelsTheNotificationAndTheRepeats()  // start; stop → notification null, ringing() false; idle 8000 → still null
@Test public void ringChannelIsSilentButVibrates()   // after StatusNotifications.createChannels: channel "watch_ring" importance HIGH, getSound() null, shouldVibrate() true
```

Use Robolectric's paused looper clock (`SystemClock` advances with `idleMainLooper`), and build `when` from `System.currentTimeMillis()` — if the two clocks don't advance together in this Robolectric version, compare a monotonically increasing `setWhen` built from `SystemClock.elapsedRealtime()` offsets instead.

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'app.wristline.WatchRingTest'` — expected: compilation failure, then (with empty stubs) assertion failures.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the app suite** — expected BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -m "Add the ring-by-notification setting and the repeating watch ring"`

---

### Task 2: Incoming calls ring by notification

**Files:**
- Modify: `app/src/main/java/app/wristline/RelayListener.java` (`startCall`, `postFallbackNotification` → removed in favour of `WatchRing.start`, `clearCall`, `onOngoingCall`), `app/src/main/java/app/wristline/ActionReceiver.java`
- Test: `app/src/test/java/app/wristline/RelayListenerTest.java`, `app/src/test/java/app/wristline/CallDiagnosticsTest.java` (existing fallback-notification tests keep passing)

**Interfaces:**
- Consumes: `Prefs.ringByNotification`, `WatchRing.start/stop/ringing`.
- `RelayListener.startCall`: when the setting is on → `useFullModeForCall = false`; log `INCOMING` with detail `notification`; `WatchRing.start(this, caller, getString(R.string.incoming_call, app.label), WatchRing.MAX_MS)`; no `addNewIncomingCall`. Setting off → unchanged, except the Telecom-refused fallback calls the same `WatchRing.start`.
- `RelayListener.clearCall` and `onOngoingCall` call `WatchRing.stop`. `ActionReceiver` calls `WatchRing.stop` instead of cancelling the id.
- `ActionReceiver` (Answer/Decline): when `RelayListener.incomingCall == null` and no test ring is on (Task 3), it stops the ring and returns without logging — a stale button.

- [ ] **Step 1: Write the failing tests** (`RelayListenerTest`, with `Prefs.setRingByNotification(context, true)` and a `@After` that turns it off and calls `WatchRing.stop`)

```java
@Test public void notificationModeMakesNoTelecomCall()        // ringing(...) → telecom.getAllIncomingCalls().size()==0; fallback notification present; title "Corvan"; event INCOMING detail "notification"
@Test public void notificationModeKeepsRinging()              // idle 4000 → notification.when advanced
@Test public void callEndingStopsTheRing()                    // onNotificationRemoved(ringing) → notification null, WatchRing.ringing() false
@Test public void answeringOnThePhoneStopsTheRing()           // post ongoing(same pkg,key) → WatchRing.ringing() false
@Test public void secondCallDoesNotRestartTheRing()           // k1 ringing; when0; post ringing k2 same app → when unchanged
@Test public void turningTheSettingOffMidRingKeepsThatCallRinging() // setRingByNotification(false) after start → ringing() still true; ActionReceiver ANSWER → event answered_on_watch "notification"
@Test public void telecomRefusalAlsoRingsRepeatedly()         // setting off; make TelecomManager refuse (see existing fallback test for how) → WatchRing.ringing() true
```

`CallDiagnosticsTest`:

```java
@Test public void staleAnswerAfterTheCallEndedDoesNothing()   // currentApp null, incomingCall null → ActionReceiver ANSWER records no event
```

The existing `answerFromTheFallbackNotificationCountsAsHandled` sets `currentApp` with `incomingCall == null` and expects an event: keep it passing by treating "a call is being relayed (`currentApp != null`)" as live; stale = `currentApp == null`.

- [ ] **Step 2: Run** the two test classes — expected assertion failures.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the app suite** — expected BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -m "Ring incoming calls by notification when the setting is on"`

---

### Task 3: Ring my watch follows the setting

**Files:**
- Modify: `app/src/main/java/app/wristline/TestCall.java`, `app/src/main/java/app/wristline/RelayConnection.java` (`testResult` → delegates), `app/src/main/java/app/wristline/RelayListener.java` (`startTestCall`, `startCall`), `app/src/main/java/app/wristline/ActionReceiver.java`, `app/src/main/java/app/wristline/MainActivity.java` (`ringWatch` busy check and toast), strings (en, it)
- Test: `app/src/test/java/app/wristline/TestCallFlowTest.java`

**Interfaces:**
- `static void TestCall.report(Context context, Result result)` — what `RelayConnection.testResult` does today (store the result, log `test_call <result>`, post `onResult` to the main thread); `RelayConnection.testResult` calls it.
- `static volatile boolean TestCall.notificationRinging` — a notification test call is on.
- `static void TestCall.endNotificationTest(Context context)` — stops the ring and clears the flag and its timeout, no result (a real call took over).
- `RelayListener.startTestCall`: setting on → refuse when busy as today or when `TestCall.notificationRinging`; else log `TEST_CALL` detail `started notification`, set the flag, `WatchRing.start(context, test_call_caller, R.string.test_call_text, TestCall.RING_MS)`, and post a main-looper timeout at `RING_MS`: if still ringing → flag off, `WatchRing.stop`, `TestCall.report(MISSED)`. Returns true.
- `ActionReceiver`: when `TestCall.notificationRinging` → flag off, cancel the timeout, `WatchRing.stop`, log `ANSWERED_ON_WATCH`/`DECLINED_ON_WATCH` detail `test call`, `TestCall.report(REACHED)`, return.
- `RelayListener.startCall` calls `TestCall.endNotificationTest(this)` next to `RelayConnection.endTestCall()`.
- Strings: `test_call_text` = "Tap Answer or Decline" / "Tocca Rispondi o Rifiuta"; `test_call_started_notification` = "Your watch should buzz now. Tap Answer or Decline there." / "L'orologio dovrebbe vibrare ora. Tocca Rispondi o Rifiuta lì."

- [ ] **Step 1: Write the failing tests** (`TestCallFlowTest`, setting on)

```java
@Test public void notificationTestRingsWithoutTelecom()     // startTestCall true; no Telecom incoming call; WatchRing.ringing(); event test_call "started notification"
@Test public void answeringTheNotificationTestReachesTheWatch()  // ActionReceiver ANSWER → Prefs.testCallResult REACHED; events answered_on_watch "test call", test_call "reached"; ring stopped
@Test public void decliningTheNotificationTestReachesTheWatch()
@Test public void unansweredNotificationTestIsMissed()      // idle RING_MS+1 → MISSED, ring stopped
@Test public void noSecondTestWhileOneRings()               // second startTestCall → false
@Test public void aRealCallReplacesTheNotificationTest()    // start test; a real ringing notification arrives (build the listener as RelayListenerTest does) → TestCall.notificationRinging false, no test result recorded, the ring now shows the caller
```

- [ ] **Step 2: Run** `--tests 'app.wristline.TestCallFlowTest'` — expected failures.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run the app suite.**
- [ ] **Step 5: Commit** — `git commit -m "Ring my watch follows the ring-by-notification setting"`

---

### Task 4: Home — the switch, the question, the report line, the docs

**Files:**
- Modify: `app/src/main/java/app/wristline/MainActivity.java` (`dndDialog`, status card `TRY_IT` branch, `TestCall.onResult` hook), `app/src/main/java/app/wristline/DiagnosticReport.java`, `app/src/main/java/app/wristline/Diagnostics.java`, strings (en, it), `README.md`, `docs/HOW_TO.md`
- Test: `app/src/test/java/app/wristline/RingQuestionTest.java` (create, Robolectric), `app/src/test/java/app/wristline/DiagnosticReportTest.java`

**Interfaces:**
- `dndDialog` gains a third checkbox `R.string.ring_by_notification` = "Ring with a notification" / "Squilla con una notifica", saved with `Prefs.setRingByNotification`; `dnd_message` gains: "\n\n\"Ring with a notification\": for watches where Answer on the call screen does nothing. The watch buzzes every few seconds with Answer and Decline; you talk on the phone."
- `MainActivity.ringQuestion()` (package-private): the dialog of R8. `TestCall.onResult` runs `refresh()` and then, when the last result is MISSED and the setting is off, `ringQuestion()`. The status card's button in the missed state calls `ringQuestion()` when the setting is off, `ringWatch()` when on; its hint is `try_missed_notification_desc` when on.
- Strings: `ring_question_title` = "Did the watch ring?"; `ring_question_text` (R8's sentence); `ring_question_switch` = "Switch and ring again"; `ring_question_again` = "Ring again"; `try_missed_notification_desc` (R8). Italian: "L'orologio ha squillato?"; "Se ha squillato ma Rispondi non ha fatto nulla, Wristline può far squillare l'orologio con una notifica. Vibra ogni pochi secondi, con Rispondi e Rifiuta."; "Cambia e riprova"; "Riprova"; "Controlla che l'orologio sia connesso e che mostri le notifiche di Wristline. Poi riprova."
- `DiagnosticReport.Facts.ringByNotification` (boolean); line `Ring by notification: on|off` after the `Relaying:` line.

- [ ] **Step 1: Write the failing tests**

`RingQuestionTest`:

```java
@Test public void missedTestAsksWhetherTheWatchRang()   // Prefs.setTestCallResult(MISSED); activity.ringQuestion(); latest AlertDialog title "Did the watch ring?"
@Test public void switchingTurnsTheSettingOnAndRingsAgain() // click positive → Prefs.ringByNotification true; WatchRing.ringing() true
@Test public void ringAgainKeepsTheSetting()            // click neutral → setting still false
@Test public void noQuestionWhenAlreadyRingingByNotification() // setting on, result MISSED, run the onResult hook → no dialog
```

`DiagnosticReportTest`:

```java
@Test public void saysWhetherItRingsByNotification()    // f.ringByNotification=true → contains "Ring by notification: on\n"; default → "off"
```

- [ ] **Step 2: Run** both classes — expected failures.
- [ ] **Step 3: Implement,** then docs: README "What you get"/troubleshooting and HOW_TO "If something goes wrong" gain: **Answer on the watch does nothing** → Home → When the watch rings → Ring with a notification (what changes: buzzes every few seconds, talk on the phone).
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest :app:assembleDebug :app:lintDebug` — expected BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -m "Offer ring by notification after an unanswered test; report and document it"`

---

## Out of scope

- **Voice through the watch in this mode.** It needs a Telecom call made active at the moment of answering, a watch connected for call audio, and a phone that lets the calling app's microphone work meanwhile. The one reporter in this mode showed none of the last two (no watch audio, dead mic while Wristline's call was active). Decide after the `audio_no_watch` and `recording` lines of his next report.
- Xiaomi/HyperOS setup step: separate plan.

## Device check

Owner's phone (Pixel Watch): turn the setting on, Ring my watch → the watch buzzes about every 4 s with Answer/Decline; Answer → Home says reached. A real WhatsApp call → same, Answer opens the call on the phone, hang-up card appears on the watch. Confirm the phone doesn't add its own sound. Turn the setting off again. Then the POCO/OPPO reporter gets the build: this is the first build expected to change what he sees.
