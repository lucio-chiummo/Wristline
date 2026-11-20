# Watch incoming screen + answer-first managed relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the hardware-proven r7/r8 spikes, and show Wristline's own full-screen incoming call on the watch when the phone rings by notification.

**Architecture:** Phone (`app`) answers the calling app before it places the managed relay call (war phones). The phone's existing `/wristline/ring` message gains caller + app; the watch app posts its own CATEGORY_CALL notification with a full-screen intent to a new `IncomingActivity`, whose Answer/Decline go back as `/wristline/answer` / `/wristline/decline` and run the exact code path of the phone notification's buttons. After Answer the watch shows the existing `InCallActivity`, fed by the existing `/wristline/callstate` messages.

**Tech Stack:** Java, Android SDK (phone minSdk per app module, watch minSdk 30 / target 36), Wear Data Layer MessageClient, Robolectric 4.17, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-21-watch-incoming-screen-design.md`

## Global Constraints

- No new third-party dependencies.
- Match surrounding style: plain-sentence commit subjects, Javadoc density like neighbours, EN + IT strings (`values-it`).
- Every commit ends with:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  `Claude-Session: https://claude.ai/code/session_01L7WdWBR3vSL7Vx8QKqdyZj`
- Branch notification-voice; do not push. Never read the keystore passwords file.
- Tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest` all green.
- The phone notification ring (`WatchRing` re-posts, Answer/Decline buttons) stays exactly as is: it is the backup for watches without Wristline's watch app.
- Do not port r7's STREAM_RING mute.

## Review Focus

1. Answering from the watch screen after the call already ended / was answered on the phone → nothing answered twice, screen closes (Task 3 test `anAnswerFromTheWatchWhenNothingRingsDoesNothing`).
2. Managed app-answered call whose Telecom call fails, is refused, or never arrives → the app is never answered a second time; voice stays on the phone (Task 1 tests).
3. Caller names containing `|` and old payload `start|<ms>` from an older phone → watch parses both (Task 4 tests).
4. Ring stop arriving before/after the incoming screen opened → screen and notification both go (Task 4 test `aRingStopClosesTheIncomingScreen`).
5. Ring with no name (older phone) → watch only buzzes, no blank screen (Task 4 test `anOldRingMessageOnlyBuzzes`).

---

### Task 1: Answer the app before the managed call exists (r8 port)

Hardware (vivo, 2026-11-21, spike branch `spike-managed-r6`, commit c57725f — read it with `git show c57725f`, don't merge): with the managed relay, WhatsApp started its ringtone once a managed call existed while it was ringing or just answered. Answering WhatsApp first and placing the managed call 1500 ms later fixed it: voice both ways, no ringtone.

**Files:**
- Modify: `app/src/main/java/app/wristline/RelayListener.java` (`answerWithVoiceOnWatch` carrier branch ~L428-441, `answeredPlaceTimedOut` ~L482)
- Modify: `app/src/main/java/app/wristline/RelayConnection.java` (`startAnswered` ~L235)
- Modify: `app/src/main/java/app/wristline/RelayConnectionService.java` (`onCreateOutgoingConnectionFailed`)
- Test: `app/src/test/java/app/wristline/ManagedRelayTest.java`

**Interfaces:**
- Produces: `static final String EXTRA_APP_ANSWERED = "app.wristline.APP_ANSWERED";` and `static final long MANAGED_PLACE_DELAY_MS = 1500;` in `RelayListener`.

- [ ] **Step 1: Update/write the failing tests** in ManagedRelayTest (reuse its war-phone setup):
  - `aNotificationAnswerOnAWarPhoneAnswersTheAppFirst`: `answerWithVoiceOnWatch` returns true; the app's answer PendingIntent fired immediately; no call placed yet; after advancing the main looper `MANAGED_PLACE_DELAY_MS` a call is placed on `carrierHandle` whose outgoing extras carry `EXTRA_ANSWERED` and `EXTRA_APP_ANSWERED` true.
  - `theManagedCallIsNotPlacedWhenTheCallEndedMeanwhile`: answer, then clear the call (incomingCall and ongoingCall null) before the delay → nothing placed.
  - `anAppAnsweredManagedCallGoesActiveWithoutAnsweringAgain`: `onCreateOutgoingConnection(carrierHandle, extras CALLER+ANSWERED+APP_ANSWERED+matching ANSWER_ID)` → connection becomes ACTIVE after `RelayConnection.ANSWER_DELAY_MS`; the app's answer is NOT sent by the connection.
  - `aRefusedAppAnsweredManagedCallDoesNotAnswerAgain`: `onCreateOutgoingConnectionFailed` with APP_ANSWERED → answer not sent; CARRIER "telecom refused" recorded.
  - `anAppAnsweredManagedCallTelecomNeverMadeDoesNotAnswerAgain`: placed, then advance `ANSWERED_PLACE_WAIT_MS` with no connection → answer not sent again; CARRIER "telecom never made the call" recorded.
  - Replace the old `aNotificationAnswerOnAWarPhonePlacesTheManagedCall` / `anAnsweredOutgoingRequestOnTheCarrierGoesActiveAndAnswersTheApp` expectations accordingly. The non-carrier path (`addNewIncomingCall`, connection answers the app after ANSWER_DELAY_MS) stays unchanged and its tests stay.
- [ ] **Step 2: Run**, expect the new tests to FAIL.
- [ ] **Step 3: Implement.**
  - `answerWithVoiceOnWatch` carrier branch: `send(context, call.answer)`; put `EXTRA_APP_ANSWERED`; after `MANAGED_PLACE_DELAY_MS` on the main looper: if `incomingCall != call && ongoingCall == null` log + return; else arm, put `EXTRA_ANSWER_ID`, `placeCall` (catch RuntimeException → `Diagnostics.record(TELECOM_REFUSED, …, "answered " + class)`, claim, voice stays on phone), then schedule the `ANSWERED_PLACE_WAIT_MS` timeout. Keep `callAudio.stop()` as now. Comment the why in 1-2 lines (WhatsApp rings once a managed call exists while it isn't answered).
  - `answeredPlaceTimedOut`: drop the `incomingCall != call` check (after the app's answer the ringing call is gone); on claim, record "telecom never made the call" and do NOT answer again (the app is already answered on the phone).
  - `onCreateOutgoingConnectionFailed`: for `EXTRA_APP_ANSWERED` claim but don't `answerOnThePhone`.
  - `RelayConnection.startAnswered()`: read `EXTRA_APP_ANSWERED` (pass it as a boolean param `startAnswered(boolean appAnswered)` from the service); when true: `awaitingAppAnswer = false`, call must still exist (`RelayListener.incomingCall != null || RelayListener.ongoingCall != null`, else end CANCELED), then after `ANSWER_DELAY_MS`: `setActive(); routeToWatch(null); VoiceControls.update(context);`. Don't send the answer. Incoming path call sites pass false.
- [ ] **Step 4: Run** the full app suite, expect PASS.
- [ ] **Step 5: Commit** "Answer the app before the managed call exists, so WhatsApp never rings into it".

### Task 2: Stop the watch buzz even after the watch app restarted (r7 port)

`Vibrator.cancel()` only reaches a vibration this process instance started; on hardware the buzz ran on after the app was frozen/restarted. A new ringtone-usage vibration replaces whatever the app has running.

**Files:** Modify `wear/src/main/java/app/wristline/wear/WatchBuzz.java` (`stop`); Test `wear/src/test/java/app/wristline/wear/WatchBuzzTest.java`.

- [ ] **Step 1: Test** `aStopReplacesTheBuzzWithASilentRingtoneVibration`: after `stop`, ShadowVibrator's last effect is a waveform with total ≤ 1 ms, amplitude 0, non-repeating, ringtone usage (same usage check style as the existing start test). Keep `aStopCancelsTheVibration` passing (cancel still called first).
- [ ] **Step 2: Run**, FAIL.
- [ ] **Step 3: Implement** in `stop`: after `cancel()`, vibrate `VibrationEffect.createWaveform(new long[]{1}, new int[]{0}, -1)` with the same attributes helper `start` uses (extract a private helper so the usage code is not duplicated). No vibrator → nothing.
- [ ] **Step 4: Run** wear suite, PASS.
- [ ] **Step 5: Commit** "Stop the watch buzz even when the app that started it was restarted".

### Task 3: Phone side of the watch incoming screen

**Files:**
- Modify: `app/src/main/java/app/wristline/WatchProtocol.java`, `WatchSync.java`, `WatchRing.java`, `ActionReceiver.java`, `WatchCallService.java`, `RelayListener.java`, `TestCall.java`
- Test: `app/src/test/java/app/wristline/WatchProtocolTest.java`, `WatchRingTest.java`, new `WatchAnswerTest.java`

**Interfaces:**
- Produces (watch side mirrors these exact strings):
  - `WatchProtocol.ringStartPayload(long maxMs, String appPackage, String name)` → UTF-8 `"start|<maxMs>|<appPackage or empty>|<name or empty>"` (name last; may contain `|`).
  - `WatchProtocol.PATH_ANSWER = "/wristline/answer"`, `WatchProtocol.PATH_DECLINE = "/wristline/decline"`, empty payloads.
  - `WatchRing.start(Context, String title, String text, String appPackage, long maxMs)`; RelayListener passes `app.packageName` and the caller as title (the payload name = title); TestCall passes null.
  - `static void ActionReceiver.handleRingAnswer(Context, boolean answer)`: the whole current body after the ONGOING branch, moved out unchanged; `onReceive` and `WatchCallService` both call it.

- [ ] **Step 1: Tests.**
  - WatchProtocolTest `aRingStartCarriesTheAppAndTheCaller`: `ringStartPayload(60000, "com.whatsapp", "Ann | Bob")` == `"start|60000|com.whatsapp|Ann | Bob"`; null app/name → `"start|60000||"`.
  - WatchRingTest: existing tests updated to the new signature; `aRingTellsTheWatchWhoIsCalling`: the single ring-start send's payload equals `ringStartPayload(MAX_MS, "com.whatsapp", "Ann")`.
  - WatchAnswerTest (Robolectric, reuse RelayListenerTest/TestCallFlowTest setup for a ringing-by-notification call): `anAnswerFromTheWatchRunsTheNotificationAnswer` (message PATH_ANSWER to `WatchCallService.onMessageReceived` → same outcome as the ANSWER broadcast: ring stopped, incoming handled, app's answer sent on a basic-mode call); `aDeclineFromTheWatchRunsTheNotificationDecline`; `anAnswerFromTheWatchWhenNothingRingsDoesNothing` (no current call → no PendingIntent sent, no crash); `anAnsweredCallKeepsTheWatchCallScreenInformed` (after answer, the app's ongoing notification → a `/wristline/callstate` "active" message with the caller name is sent; after the call ends → "ended").
- [ ] **Step 2: Run**, FAIL.
- [ ] **Step 3: Implement.**
  - Protocol/Sync/Ring per Interfaces (the single-thread ring queue and its 5 s timeout stay).
  - `WatchCallService.onMessageReceived`: PATH_ANSWER → `ActionReceiver.handleRingAnswer(this, true)`; PATH_DECLINE → `(this, false)`; posted to the main thread like the receiver runs.
  - Keep the watch call screen informed: when an answer comes from the watch (either button) and the call is a real relayed call, set the call screen name the way `outgoing` does (`outgoingName = incomingCaller; toldWatch = null;`) through a small static `RelayListener.showCallOnWatch()` — so `onOngoingCall` sends active/mute and `endCall` sends ended. Javadoc of `outgoingName` updated: the call shown on the watch's call screen (started or answered there). The missed-call branch in `endCall` stays correct (incomingHandled is already true).
- [ ] **Step 4: Run** app suite, PASS.
- [ ] **Step 5: Commit** "Let the watch answer or decline a ringing call and follow it on its call screen".

### Task 4: Watch incoming screen

**Files:**
- Create: `wear/src/main/java/app/wristline/wear/IncomingActivity.java`, `wear/src/main/java/app/wristline/wear/IncomingRing.java`, `wear/src/main/res/layout/activity_incoming.xml`
- Modify: `Protocol.java`, `PhoneLink.java`, `CallStateService.java`, `InCallActivity.java`, `wear/src/main/AndroidManifest.xml`, `wear/src/main/res/values/strings.xml`, `values-it/strings.xml`
- Test: `wear/src/test/java/app/wristline/wear/RingProtocolTest.java`, new `IncomingRingTest.java`

**Interfaces:**
- Consumes: Task 3's payload `"start|<maxMs>|<app>|<name>"`, paths `/wristline/answer`, `/wristline/decline`.
- Produces: `Protocol.Ring Protocol.parseRing(String payload)` → null for stop/garbage; fields `long maxMs`, `String app` (null if empty), `String name` (null if empty). `ringMaxMs` keeps working (delegate). `PhoneLink.answer(Context)`, `PhoneLink.decline(Context)`.

- [ ] **Step 1: Tests.**
  - RingProtocolTest: `parsesAFullRing` (`"start|60000|com.whatsapp|Ann | Bob"` → 60000, "com.whatsapp", "Ann | Bob"); `parsesAnOldRing` (`"start|60000"` → 60000, null, null); `stopAndGarbageAreNull`.
  - IncomingRingTest (Robolectric): `aRingWithACallerPostsAFullScreenCallNotification` (`IncomingRing.start(ctx, ring)` → one notification, category CALL, `fullScreenIntent` targets IncomingActivity, ongoing, actions Answer/Decline, channel has no sound and no vibration); `aRingStopClosesTheIncomingScreen` (start, open IncomingActivity via Robolectric, `IncomingRing.stop` → notification gone, activity finishing); `anOldRingMessageOnlyBuzzes` (`CallStateService` given `"start|60000"` → WatchBuzz vibrates, no notification); `answerTellsThePhoneAndOpensTheCallScreen` (click Answer → `PhoneLink` sender saw `/wristline/answer`, next started activity is InCallActivity with the name/app, IncomingActivity finishing) — make PhoneLink's send observable with a package-private static hook if none exists; `declineTellsThePhoneAndCloses`.
- [ ] **Step 2: Run**, FAIL.
- [ ] **Step 3: Implement.**
  - `IncomingRing` (final, static): `start(Context, Protocol.Ring)` creates channel `incoming_call` (IMPORTANCE_HIGH, `setSound(null, null)`, `enableVibration(false)` — WatchBuzz vibrates), posts notification id `IncomingRing.ID` CATEGORY_CALL, ongoing, `setFullScreenIntent(pi, true)`, content = name + app label, Answer/Decline actions (broadcast to a small receiver or the activity with an action extra, builder's call), timeout = maxMs; then also tries `startActivity(IncomingActivity)` in try/catch (Wear may launch only via the full-screen intent; a blocked start is harmless). Logs `NotificationManager.canUseFullScreenIntent()` on API 34+. `stop(Context)`: cancel notification, finish `IncomingActivity.current`.
  - `CallStateService` PATH_RING: parse; start → `WatchBuzz.start` and, if name != null, `IncomingRing.start`; stop → `WatchBuzz.stop` + `IncomingRing.stop`.
  - `IncomingActivity`: InCallActivity's look (avatar, name, app label + coloured dot, pulsing ring), green Answer and red Decline buttons; `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, FLAG_KEEP_SCREEN_ON; swipe-to-dismiss off; `static volatile IncomingActivity current`. Answer: background thread `PhoneLink.answer`, `WatchBuzz.stop`, cancel notification, start `InCallActivity.intent(ctx, name, app)` with initial status "Connecting…" (add an optional extra to InCallActivity; default stays "Calling…"), finish. Decline: `PhoneLink.decline`, buzz stop, cancel notification, finish.
  - Manifest: `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />`; activity `IncomingActivity` exported=false, showWhenLocked/turnScreenOn, `taskAffinity=""`, launchMode singleTop, theme `@style/InCall`, excludeFromRecents.
  - Strings EN/IT: Answer/Rispondi, Decline/Rifiuta, Connecting…/Connessione…, channel name "Incoming calls"/"Chiamate in arrivo".
- [ ] **Step 4: Run** wear suite, PASS. Also `./gradlew :wear:assembleRelease :app:assembleRelease` builds.
- [ ] **Step 5: Commit** "Show a full-screen incoming call on the watch while the phone rings by notification".
