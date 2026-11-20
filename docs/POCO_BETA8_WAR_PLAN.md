# POCO beta 8 war: plan for the last test round

Status: plan only. No code changed. Written from code reading; nothing built or run.
Target: v1.5-beta9, one round left. Must work end-to-end in that round.

Tester phone: Xiaomi 2510DPC44G (POCO), Android 17 / SDK 37, HyperOS. Watch: OPPO OWWE261.

## 0. Decisions

- **D1 Hold-loss detection (Defect 2).** While `VoiceHold` holds the voice, poll `getCommunicationDevice()` every 500 ms. If the device is no longer a watch device while the watch is still offered, that is a loss. Call back into `RelayConnection`. Catches the invisible HyperOS earpiece switch that Telecom never reports.
- **D2 One give-up path (Defect 1, reworked).** All give-ups go through `giveUpVoice(reason, war)`. Owner war is armed only when the loss is war-shaped: inside `WAR_WINDOW_MS` of the take. The literal Defect 1 ("bounce give-up always arms") is not adopted. Reason in section 2.3.
- **D3 Window 2000 to 3000 ms (Defect 3).** Recommended as a second net. Regression stated in section 3.
- **D4 Carrier lifecycle hardened.** Three holes found in current code (section 1). Each makes the carrier fail once for the call, then falls back to `VoiceHold`. A `VoiceHold` loss sends the voice to the phone. Forward-only state machine, so no fight oscillation.
- **D5 Call-out while a relayed call is alive: refuse.** Nothing is started. The watch gets `busy` then `ended`, and shows "Already on a call". Tester hangs up first (section 5).
- **D6 No owner-war reset this round.** `ownerWar` stays set-only. Spec for later in section 7.

## 1. Findings beyond the diagnosis (verified in code)

- **F1 Dead carrier is invisible.** `CarrierConnection.onDisconnect` and `onAbort` end the carrier but never tell `RelayConnection`. `myCarrier` stays set. `engageCarrier()` then returns true forever, and `onCallAudioStateChanged` skips all loss logic while `myCarrier != null`. Result: a silent call with no hold and no detection. Must fix.
- **F2 Asked but never made.** `engageCarrier()` returns true once `carrierAsked` is set, even if Telecom neither creates the carrier nor calls `onCreateOutgoingConnectionFailed` (for example, a silently dropped placeCall). No timeout. Silent. Must fix.
- **F3 Late carrier accepted.** `carrierCreated()` ends a carrier only when there is no connection, a test call, or `voiceOnPhone`. A carrier that arrives after a timeout or failure is kept, giving two owners. Must fix.
- **F4 Refusal still launches the app.** `OutgoingCalls.start` (line 42): when `RelayListener.startOutgoing` returns false, the app is started anyway, unmirrored. That is the second WhatsApp request the tester saw. A refusal must return before `startApp`.
- **F5 Watch messages can reorder.** `WatchSync.callState` starts a new thread per call. Two states sent in a row (busy, then ended) can arrive out of order. The pair needs one thread.
- **F6 Call-outs also use the carrier.** `startOutgoing` calls `routeToWatch`, which calls `engageCarrier`. On a war phone the call-out is a carrier test too.
- **F7 Bounce path is Android 16 only in practice.** On SDK 37 the war branch fires before the bounce counter, so bounces only happen for falls more than the window after the latest take. The existing test `stopsMovingTheVoiceWhenItKeepsLeavingTheWatch` runs with `VoiceHold.needed()` false.
- **F8 Tracker can be idle while the mirror lives.** The diagnosis says `outgoing()` reached START, so `CallTracker` was idle while `RelayConnection.current` (call 1) was alive. The code path that clears the tracker without ending the connection was not found in this read. The guard therefore checks `RelayConnection.current` directly, not the tracker. Confirm the event order in the report.
- **F9 POCO timing.** `VoiceHold.logCheck` at 1.5 s already showed `device=earpiece`. The switch happened within about 1.5 s of a take, before the Telecom-visible fall at 2.45 s. A 500 ms poll sees it within about 0.5 s of the switch. That loss is war-shaped even with the 2000 ms window.

## 2. Fix A: hold-loss detection and the give-up path

Files: `VoiceHold.java`, `RelayConnection.java`.

### 2.1 VoiceHold (VoiceHold.java)

- `static final long WATCH_CHECK_MS = 500;`
- New constructor `VoiceHold(Context, AudioLink.Sink, Runnable onLost)`. The existing 2-arg constructor delegates with `null`, so `VoiceHoldTest` compiles unchanged.
- `private final Runnable watch = this::checkWatch;`
- `take()`: after `held = true`, `handler.postDelayed(watch, WATCH_CHECK_MS)`. The existing `check` at `CHECK_MS` stays as the report line.
- `private void checkWatch()`:
  - return if `!held`;
  - `Boolean on = watchHasVoice(context)`;
  - if `on == Boolean.FALSE`: `handler.removeCallbacks(watch)`, log `hold=lost device=<type>`, run `onLost`. Do not release here. `held` must still be true when `onLost` runs, so the caller's `release()` does the work. Polling stops, so the callback fires once;
  - otherwise re-post `watch` after `WATCH_CHECK_MS`.
- `release()`: also `handler.removeCallbacks(watch)`. It already sets `held = false` before anything else, so a switch caused by the user's own move is never seen as a loss.
- `static Boolean watchHasVoice(Context)` (used by the carrier check too):
  - `null` when no SCO or BLE device is in `getAvailableCommunicationDevices()`. The watch is gone: a real drop, left to the route path;
  - `TRUE` when `getCommunicationDevice()` is SCO or BLE;
  - `FALSE` otherwise, including `null` device.

Why a poll, not `OnCommunicationDeviceChangedListener`: the listener exists and Robolectric shadows it, but whether HyperOS dispatches it on this switch is unverified. This is the last round. The poll reads the same call the existing `logCheck` already reads and proved on the POCO.

### 2.2 Wiring (RelayConnection.java)

- Constructor: `voiceHold = new VoiceHold(this.context, sink, this::onVoiceLost);`
- `private void onVoiceLost()`:
  - if `testCall || voiceOnPhone || !holdingVoice`: `voiceHold.release()` and return;
  - `boolean war = lastTakeAt > 0 && elapsed - lastTakeAt < WAR_WINDOW_MS;`
  - `giveUpVoice(war ? "owner war (left without Telecom seeing it)" : "left without Telecom seeing it", war);`

### 2.3 One give-up path (Defect 1 reworked)

```java
/** The voice left the watch while Wristline held it: stop fighting for this call. War only when the loss came inside the take window. */
private void giveUpVoice(String reason, boolean war) {
    if (war) {
        Prefs.setOwnerWar(context, true);
    }
    voiceOnPhone = true;
    voiceHold.release();
    holdingVoice = false;
    if (!testCall && audioState != null && audioState.getRoute() == CallAudioState.ROUTE_BLUETOOTH) {
        setAudioRoute(CallAudioState.ROUTE_EARPIECE); // Telecom must not keep a dead watch route
    }
    Log.w(RelayListener.TAG, "the voice left the watch: staying on the phone");
    Diagnostics.record(context, EventLog.AUDIO_GAVE_UP, testCall ? null : RelayListener.currentApp, reason);
    VoiceControls.update(context);
}
```

Callers:
- route fall inside the window (existing war branch): `giveUpVoice("owner war", true)`. Keeps the exact detail that `CallDiagnosticsTest` asserts.
- bounce counter reaches `MAX_BOUNCES`: `giveUpVoice("left the watch " + bounces + " times", false)`.
- hold-loss poll: `onVoiceLost` above.

Why the bounce path does not arm (Defect 1 not adopted as written):
- A war-shaped first loss gives up at once (the war branch), so reaching `MAX_BOUNCES` means every fall was outside the window: real-drop shape.
- Arming there would turn three real dropouts into a permanent carrier on the phone.
- The POCO sample is covered without it: its loss is war-shaped (F9), so it arms through the poll or the war branch.
- Fallback if the next report shows an unshaped first loss on the POCO: switch to "arm on any give-up while held" (one line, `war = true`). Trade-off in section 7.

## 3. Fix D3: war window

`RelayConnection.WAR_WINDOW_MS`: 2000 to 3000. Separate commit so it can be reverted alone.

- Why: a second net if the poll misses the switch, and the POCO sample was 2.45 s on the Telecom-visible fall.
- Regression: a real Telecom fall within 3 s of a take (a take happens at answer and after each fall) now arms the carrier permanently on that phone. Narrow band, accepted.
- `CallDiagnosticsTest.onAndroid17AFallBackToThePhoneTakesTheVoiceAgain` uses `WAR_WINDOW_MS + 500`, so it still holds.

## 4. Fix D4: carrier lifecycle and the in-call fallback

### 4.1 Per-call state (forward only)

```
ASK (carrierAsked) -> CARRIER up -> FALLBACK (carrierFailedThisCall, VoiceHold) -> PHONE (voiceOnPhone)
```

Automatic transitions only go forward:
- asked, not created within `CARRIER_WAIT_MS` (2000): `failCarrier("timeout")`
- Telecom `onCreateOutgoingConnectionFailed`: `failed` (existing; `carrierFailed()` sets the flag)
- carrier up, watch no longer holds the voice (checked from `CARRIER_CHECK_MS` 1500, then every 500 ms): `failCarrier("unheld")`
- carrier destroyed by Telecom or the user (`CarrierConnection.onDisconnect` or `onAbort`): `carrierGone` gives `lost`
- `VoiceHold` loss, or war-shaped route fall: PHONE (`giveUpVoice`)

User actions can go back: `moveVoice(false)` re-enters. Because `carrierFailedThisCall` stays set, it re-enters at FALLBACK, never at ASK. The carrier is asked at most once per call (existing `carrierAsked`, plus the failure flag).

Oscillation is impossible: at most one carrier attempt, one `VoiceHold` attempt, then PHONE on the next loss. Each gap is bounded.

### 4.2 Changes (RelayConnection.java)

- `private int carrierAskId;` `engageCarrier()` increments it on each ask and posts `carrierTimedOut(askId)` after `CARRIER_WAIT_MS`. `carrierTimedOut` acts only if the id is current, `myCarrier == null`, and `!carrierFailedThisCall`. A timed-out late carrier is then ended in `carrierCreated`.
- `engageCarrier()` records `Diagnostics.record(CARRIER, app, "asked")` at the ask.
- `private void failCarrier(String why)`: set `carrierFailedThisCall = true`; record `CARRIER why`; `endCarrier()` (records `ended`); `routeToWatch(audioState)`. This is where `VoiceHold` takes over.
- `static void carrierCreated(CarrierConnection)`: also end the new carrier when `carrierFailedThisCall` is set. On accept: `myCarrier = carrier` and `handler.postDelayed(watchCarrier, CARRIER_CHECK_MS)`.
- `private final Runnable watchCarrier = this::checkCarrier;`
  ```java
  private void checkCarrier() {
      if (myCarrier == null || voiceOnPhone) return;
      if (VoiceHold.watchHasVoice(context) == Boolean.FALSE) { failCarrier("unheld"); return; }
      handler.postDelayed(watchCarrier, VoiceHold.WATCH_CHECK_MS);
  }
  ```
- `static void carrierGone(CarrierConnection carrier)`: if `current != null && current.myCarrier == carrier`, then `myCarrier = null`, `carrierFailedThisCall = true`, record `CARRIER lost`, `routeToWatch(audioState)`. `endCarrier()` nulls `myCarrier` before `carrier.end()`, so the identity check prevents a double report.
- `end()` already calls `handler.removeCallbacksAndMessages(null)`, which covers the new timers.

### 4.3 CarrierConnection.java

`onDisconnect` and `onAbort` call `RelayConnection.carrierGone(this)` before `end(...)`. `end` is called by `RelayConnection` itself and does not call back.

### 4.4 What the user hears on POCO if the carrier fails

- Carrier not created by 2 s: `timeout`. `VoiceHold` takes the voice at about 2 s. WhatsApp was answered at 1 s, so there is a short gap. If the take is lost war-shaped, the call goes to the phone.
- Carrier up but watch dropped at 1.5 s or later: `unheld`. Carrier ends. `VoiceHold` takes the voice. Next loss goes to the phone.
- Floor: every path ends with the voice on the watch (carrier or hold) or on the phone with the route set (`giveUpVoice` resets a dead Bluetooth route). The call is never left with no owner and no route.

## 5. Fix D5: call-out while a relayed call is alive

Choice: refuse, tell the watch, hang up nothing.

Why not hang up the live call first: one tap would silently end someone else's call. The watch has no cheap prompt. WhatsApp cannot carry two calls, so the call-out cannot succeed without ending the first anyway. Refusal is safe and easy to explain.

Changes:
- `EventLog.OUTGOING_REFUSED = "outgoing_refused"`.
- `RelayConnection.isRelayingCall()`: `current != null && !current.testCall`.
- `RelayListener.relayedCallInProgress()`: `RelayConnection.isRelayingCall() || (ongoingCall != null && currentApp != null)`.
- `OutgoingCalls.start`: right after the row lookup, before `startOutgoing`/`startApp`:
  ```java
  if (RelayListener.relayedCallInProgress()) {
      Log.i(RelayListener.TAG, "not calling: a call is on");
      Diagnostics.record(context, EventLog.OUTGOING_REFUSED, app, trigger);
      WatchSync.callStates(context, app, contact.name, WatchProtocol.STATE_BUSY, WatchProtocol.STATE_ENDED);
      return;
  }
  ```
- `WatchSync.callStates(Context, SupportedApp, String name, String... states)`: one thread, payloads sent in order (fixes F5). Existing `callState` unchanged.
- `WatchProtocol.STATE_BUSY = "busy"` (phone). `wear/.../Protocol.java`: `STATE_BUSY = "busy"`.
- Wear `InCallActivity.onState`: `BUSY` shows a Toast (`R.string.already_on_call`, English only; the wear module has no values-it) and `finish()`.
- Compatibility: an old watch ignores `busy` and closes on `ended`. A new watch shows the toast. An old phone sends nothing new.

Round instruction: hang up before calling out. The refusal message is visible on the watch, so a repeat is understandable.

## 6. Round dynamics and timing

### Call A: owner war not yet armed

| t (from answer) | Event |
| --- | --- |
| 0 | onAnswer, setActive, routeToWatch, `take` (`hold=taken`), poll starts |
| <=1.5 s | Switch to earpiece with no Telecom route change |
| <=+0.5 s | Poll sees non-watch device: `hold=lost`, `giveUpVoice("owner war (...)", true)`, war armed, voice on phone |
| 1.0 | answerApp: WhatsApp answers, voice goes to phone |
| 2.45 | Telecom fall: ignored (`voiceOnPhone`) |

Watch silence about 1 to 2 s (beta 8: silent until the third bounce).

### Call B: carrier armed

| t | Event |
| --- | --- |
| 0 | onAnswer, setActive, routeToWatch, `engageCarrier` (`CARRIER asked`), placeCall |
| 0.1 to 0.5 | Telecom creates carrier: `CARRIER created`, `carrierCreated`, watch check scheduled at +1.5 s |
| 1.0 | answerApp: WhatsApp answers into a Telecom-owned mode |
| 1.5 | First watch check: device watch, continue every 500 ms |
| 2.0 | Timeout: no-op (carrier exists) |

Failure branches: not created by 2.0 gives `timeout`, then `VoiceHold` at about 2 s. Unheld at 1.5 gives `unheld`, then `VoiceHold` at about 1.5 s.

### Call-out: armed

Same as call B, through `startOutgoing` (`setActive` after 1 s). If a relayed call is alive, the watch gets busy and nothing else happens (section 5).

## 7. Detection philosophy and trade-off

- **Lost: aggressive.** Any evidence the voice left the watch while held gives up this call at once: poll switch, Telecom fall, or (after three bounces) repeated falls. The voice is never fought past one gap.
- **War: conservative.** Arm the carrier only when the loss is inside `WAR_WINDOW_MS` (3 s) of its take. A real dropout after 3 s never arms. Bounce give-ups never arm.
- **Cost.** A real dropout within 3 s of a take (for example, the watch reconnecting at answer) arms the carrier permanently on that phone. The POCO evidence (switch within 1.5 s) supports war, and vivo never wars, so vivo stays unarmed unless it has a war-shaped loss.
- **Alternative (not chosen).** Arm on any give-up while held. Guarantees the carrier on the POCO whatever the timing. Cost: vivo or Pixel users with real drops would get the carrier, against the design doc's claim that vivo never engages. Use only if the next report shows an unshaped first loss on the POCO.
- **Reset: none this round.** `Prefs.setOwnerWar` is set-only; an armed phone stays armed until app data is cleared. Spec for beta 9: clear after a carrier call completes with no `unheld` or `lost` and a whole-call hold, or after N clean carrier calls. Not in this round.

## 8. Files, constants, log details, prefs

| File | Change |
| --- | --- |
| `VoiceHold.java` | `WATCH_CHECK_MS`, poll `checkWatch`, 3-arg constructor with `onLost`, `watchHasVoice`, `release` cancels poll |
| `RelayConnection.java` | VoiceHold wiring, `onVoiceLost`, `giveUpVoice`, `WAR_WINDOW_MS = 3000`, `CARRIER_WAIT_MS = 2000`, `CARRIER_CHECK_MS = 1500`, `carrierAskId`, `failCarrier`, `checkCarrier`, `carrierGone`, `carrierCreated` guard, `isRelayingCall` |
| `CarrierConnection.java` | `onDisconnect` and `onAbort` call `RelayConnection.carrierGone(this)` |
| `RelayListener.java` | `relayedCallInProgress()` |
| `OutgoingCalls.java` | refusal guard before any start |
| `WatchSync.java` | `callStates(...)`, one thread |
| `WatchProtocol.java` | `STATE_BUSY` |
| `EventLog.java` | `OUTGOING_REFUSED` |
| `wear/.../Protocol.java` | `STATE_BUSY` |
| `wear/.../InCallActivity.java` | busy toast, finish |
| `wear/src/main/res/values/strings.xml` | `already_on_call` |
| `Prefs.java` | no new keys (`owner_war` unchanged) |

New log details, using existing constants:
- `CARRIER`: `asked`, `created`, `failed`, `timeout`, `unheld`, `lost`, `ended`
- `AUDIO_GAVE_UP`: `owner war`, `owner war (left without Telecom seeing it)`, `left without Telecom seeing it`, `left the watch N times`
- `AUDIO_LINK`: `hold=lost device=<type>` (new line from the poll)
- `OUTGOING_REFUSED`: trigger (`watch` or `call back`)

## 9. Test plan

Commands (JDK 21; see the build-and-install memory for JAVA_HOME):

```
cd /Users/luciochiummo/Desktop/Wristline
./gradlew :app:testDebugUnitTest --tests 'app.wristline.VoiceHoldTest' --tests 'app.wristline.CarrierCallTest' --tests 'app.wristline.CallDiagnosticsTest' --tests 'app.wristline.OutgoingCallsTest' --tests 'app.wristline.WatchProtocolTest'
./gradlew :wear:testDebugUnitTest --tests 'app.wristline.wear.CallStateTest'
./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest   # before the final commit
```

Robolectric facts used: `ShadowAudioManager` has `setCommunicationDevice` (accepted when the device is offered) and `getCommunicationDevice`. Simulate a HyperOS switch by calling `audio.setCommunicationDevice(earpieceInfo)` with the earpiece offered, then `ShadowLooper.idleMainLooper(...)`. Don't rely on the shadow's listener dispatch.

### VoiceHoldTest (new cases)
1. `aSwitchToTheEarpieceWhileHeldIsReportedOnce`: offer earpiece and SCO, take, switch to earpiece, idle 500 ms. Expect one `onLost` and log `hold=lost device=earpiece`. Idle 2000 more: still one.
2. `aHeldVoiceThatStaysOnTheWatchIsNeverReported`: take, idle 5000 ms. Zero `onLost`.
3. `aWatchDroppingOutOfTheListIsNotALoss`: take, remove SCO from the offered list, set the earpiece as communication device, idle. Zero `onLost` (real drop).
4. `releasingBeforeASwitchIsNotALoss`: take, release, switch, idle. Zero `onLost`.
5. `watchHasVoiceSaysWhatItSees`: null with no watch offered; TRUE on SCO; FALSE on earpiece.

Existing cases keep passing: `aMomentAfterTakingItSaysWhetherItHeld` (its idle now also runs the poll; no loss), `noCheckAfterLettingGo`.

### CarrierCallTest (new cases; `setUp` sets owner war true, so tests that need it false must set it)
6. `aLossInsideTheWarWindowGivesUpAndArmsTheWar` (C4): owner war false, `answeredCall`, BT route (take), switch to earpiece, idle 500. Expect detail `owner war (left without Telecom seeing it)`, `Prefs.ownerWar` true, `voiceOnWatch()` FALSE.
7. `aLossLaterThanTheWarWindowGivesUpWithoutArming` (C4): owner war false, take, idle `WAR_WINDOW_MS + 500`, switch, idle 500. Expect `left without Telecom seeing it`, owner war still false.
8. `aBounceGiveUpDoesNotArmTheWar` (C4): owner war false, three cycles of BT, idle `WAR_WINDOW_MS + 500`, EARPIECE. Expect `left the watch 3 times`, owner war false.
9. `aFallAt2500msIsWarWithTheWiderWindow` (C5): owner war false, BT, idle 2500, EARPIECE. Expect `owner war`.
10. `aCarrierThatIsDestroyedMidCallFallsBackToTheVoiceHold` (C6): carrier up, carrier `onDisconnect`. Expect `myCarrier` null, `CARRIER lost`, `hold=taken`, asked count stays 1.
11. `aCarrierNeverMadeTimesOutToTheVoiceHold` (C6): BT, idle `CARRIER_WAIT_MS`. Expect `CARRIER timeout`, `hold=taken`.
12. `aCarrierMadeAfterTheTimeoutIsPutDown` (C6): after case 11, `carrierUp()`. Expect DISCONNECTED, `CARRIER ended`.
13. `aCarrierTheWatchStopsHoldingIsFailedInCall` (C6): carrier up, earpiece as communication device, idle `CARRIER_CHECK_MS`. Expect `CARRIER unheld`, `hold=taken`, `myCarrier` null.
14. `theCarrierIsNotAskedAgainAfterItFailed` (C6): after 10 or 13, BT three times. Expect outgoing count 1.
15. `theAskIsRecorded` (C6): `CARRIER asked` present after first BT on a war phone.

Existing `CarrierCallTest` cases (refused-then-hold, mirror noise, asked-only-once, moved voice, late carrier after voice moved) must stay green unchanged.

### CallDiagnosticsTest (existing; must stay green)
- `onAndroid17AFallRightAfterTheTakeGivesUpAtOnce`: exact `owner war` detail; `voiceOnWatch()` FALSE; `Prefs.ownerWar` true.
- `onAndroid17AFallBackToThePhoneTakesTheVoiceAgain`: idle `WAR_WINDOW_MS + 500`; still re-takes.
- `stopsMovingTheVoiceWhenItKeepsLeavingTheWatch`: Android 16 path, exact `left the watch 3 times`.

### OutgoingCallsTest (new cases)
16. `aCallOutWhileARelayedCallIsOnIsRefused`: relayed call via `RelayConnectionService` (as `CarrierCallTest.answeredCall`), then `OutgoingCalls.start(..., TRIGGER_WATCH)`. Expect no started activity (`getNextStartedActivity` null), `OUTGOING_REFUSED` with detail `watch`, `RelayListener.currentApp` and `ongoingCall` unchanged.
17. `aTestCallDoesNotRefuseACallOut`: test call current, start. Expect the activity started.
18. `aCallOutAfterTheCallEndsStarts`: end the call, start. Expect the activity started.

### WatchProtocolTest / wear CallStateTest
- Phone: `STATE_BUSY` is `"busy"` and `callStatePayload` carries it.
- Wear: `CallState.parse("busy").kind` is `"busy"`.
- Not unit-tested: send ordering in `WatchSync.callStates` (review only) and the toast (covered by the round).

## 10. Commit split and order

Each commit builds and its tests pass before the next. One concern each.

1. `Refuse a call-out while a relayed call is on` (D5 phone side): `EventLog.OUTGOING_REFUSED`, `RelayConnection.isRelayingCall`, `RelayListener.relayedCallInProgress`, `OutgoingCalls` guard, `WatchSync.callStates`, `WatchProtocol.STATE_BUSY`. Tests 16 to 18.
2. `Show the watch a refused call-out` (D5 watch side): wear `Protocol.STATE_BUSY`, `InCallActivity` busy handling, string. Wear `CallStateTest`.
3. `Notice the voice leaving the watch while it is held` (D1 VoiceHold): poll, `onLost` constructor, `watchHasVoice`, `release` cancels poll. Tests 1 to 5. No `RelayConnection` change yet.
4. `Give up the hold on any loss; arm the war only inside the window` (D1 wiring, D2): VoiceHold wiring, `onVoiceLost`, `giveUpVoice`, route-fall and bounce callers moved onto it. Tests 6 to 8 and the existing `CallDiagnosticsTest` cases.
5. `Widen the war window to 3 s` (D3): one constant. Test 9. Separate so it can be reverted alone.
6. `Fail the carrier once when it dies, never comes, or stops holding` (D4, F1 to F3, F5 path): `CarrierConnection` callbacks, `carrierGone`, timeout, `checkCarrier`, `carrierCreated` guard, `asked` log. Tests 10 to 15.

Dependencies: 1 and 2 are independent of the rest. 3 before 4. 5 after 4. 6 after 4 (uses `giveUpVoice`-era fields only indirectly, but tests assume the new route-fall path).

## 11. Round script and what the report must show

Before the round: install the beta 9 phone APK and the matching watch build. The watch needs the new build for the toast. Nothing else to set up.

Round:
1. Incoming call A: answer on the watch. Let it run until the voice is on the phone, then hang up.
   Expect: `hold=taken`, then `hold=lost device=earpiece` within about 2 s of answer; `AUDIO_GAVE_UP` `owner war (left without Telecom seeing it)`; no `hold=` lines after that.
2. Incoming call B: answer on the watch. Talk 20 s or more. Hang up on the watch.
   Expect: `CARRIER asked`, `CARRIER created`; no `hold=` lines for the whole call; no `unheld`, `lost`, `timeout`, `failed`; `sco_on=true` on the audio link lines; the watch carries the voice.
3. Call out from the watch with nothing active.
   Expect: `CARRIER asked` and `created` again; the call connects. If a call is still on, the watch shows "Already on a call": hang up first and retry.

The next report must show, for call B (and the call-out):
- `CARRIER` events: `asked`, then `created`, and none of `failed`, `timeout`, `unheld`, `lost`.
- Whole-call `sco_on=true` on the audio link lines, and no `hold=` lines.
- System log `PhoneStateEvent`: mode owner `com.android.server.telecom` through call B.
- For call A: `AUDIO_GAVE_UP` `owner war` within about 2 s of answer.
- `OUTGOING_REFUSED` if a call-out was tried during a call.

## 12. Risks and what stays unverified

- **R1 Carrier path on POCO is unverified.** Telecom may refuse, or may create the carrier without owning the mode. Fallback is `VoiceHold`, then the phone. Up to about 2 s of watch silence per fallback step. The round's report decides.
- **R2 Device-level check.** `watchHasVoice` reads the communication device, not the calling app's stream route. A carrier could hold the device while the app's stream plays elsewhere. The report's `sco_on` and mode-owner lines are the arbiter.
- **R3 Poll timing.** The 1.5 s reading proves the switch is visible on the device. Sub-500 ms timing is not proven.
- **R4 Carrier account state.** A managed Telecom account may need enabling by the user in calling-account settings, and `placeCall` may fail if it is disabled. Not handled in code (`PhoneAccount.isEnabled` not read). If so, the report shows `failed`, then `VoiceHold`. Consider recording `isEnabled` in the carrier event for the next round.
- **R5 Visible carrier.** The carrier shows as a call on the phone's call screen and in the call log. Known cost, accepted for the experiment.
- **R6 Permanent arming.** No reset; window widened to 3 s (section 3 and 7).
- **R7 Watch build.** Old watch: `busy` ignored, then `ended` closes the screen silently. New watch: toast.
- **R8 Call-outs are carrier tests too.** The mirror's `routeToWatch` calls `engageCarrier`, so a failing carrier there shows on the call-out.
- **R9 Answer timing.** WhatsApp is answered at 1 s. The carrier wait is 2 s. If Telecom takes longer, WhatsApp answers before the mode owner exists. Effect unknown.
- **R10 Tracker versus connection (F8).** The guard reads `RelayConnection.current`. Confirm from the report's event order what cleared the tracker during the tester's call.
- **R11 Robolectric is not HyperOS.** Shadows store the device the test sets, and the switch is simulated. The poll's behaviour on the real device is inferred from the beta 8 `logCheck` line.
- **R12 Nothing run.** This plan is from code reading. Build, tests and the round are the first real evidence.

## 13. Enablement, found on hardware after the plan (2026-11-19, owner's vivo, Android 17)

The plan's R4 was real and would have failed the round on its own:

- `dumpsys telecom` shows the relay account `[X]` (enabled) and the carrier account `[ ]`
  (**disabled**). A managed account registered by a non-dialer starts off; only the user can
  enable it. As planned, beta 9 would log `CARRIER asked` then `timeout` on every war call.
- The public `ACTION_CHANGE_PHONE_ACCOUNTS` screen (`com.android.phone`
  `PhoneAccountSettingsActivity`) lists only the SIM on vivo — no third-party account at all.
- Telecom's own `EnableAccountPreferenceActivity` listed **nothing** for a SIP-only account with
  no capabilities. With `SCHEME_TEL` added and `CAPABILITY_CALL_PROVIDER` set, the account appears
  there as "Wristline — voice on watch" with a switch (screenshot-verified on the vivo). The switch
  has not been flipped yet; the owner does that.
- Re-taking the voice at device level is not an alternative: `BETA8_HYPEROS_FIX.md` proves from the
  decompiled HyperOS audio framework that every WhatsApp `setMode` while recording stops SCO, and
  Wristline's own take provokes it. The carrier is the only door.

Code added on top of the plan (all unit-tested, 631 green):

- `RelayListener.carrierEnabled()`; `engageCarrier()` records `CARRIER account enabled=<bool>` and,
  when off, `CARRIER disabled` and holds the voice at once instead of waiting `CARRIER_WAIT_MS`.
- Carrier account: own label ("Wristline — voice on watch"), `tel` + `sip` schemes,
  `CAPABILITY_CALL_PROVIDER` — the three things that make it visible and enableable.
- Home card: `SetupStatus.Kind.CARRIER_OFF` on a war phone with the account off; button opens the
  enable screen (Telecom component names first, the two public actions as fallback, written path
  last). English and Italian.
- Report line: `Mode-owner war: … · carrier account: enabled/DISABLED`.

Round script change (replaces §11's "nothing else to set up"): between call A and call B, open
Wristline, tap the card, enable "Wristline — voice on watch", come back until the card clears.
Call B and the call-out then run the carrier. If the card never clears, stop and report — do not
spend the round.

Still unverified, and only the POCO can answer: the switch enabling on HyperOS; Telecom creating
the carrier once enabled (R1); the carrier owning the mode there. The vivo cannot reproduce the
war — its Telecom holds the mode even for self-managed calls — so it can prove enablement and
registration, nothing about the war.

Known cost of `CAPABILITY_CALL_PROVIDER`: once enabled, the account can appear in the dialer's
outgoing-account chooser. A dialer-originated call on it would create a stand-in; refusing
requests without Wristline's own caller extra is a follow-up, not in this round.

## 14. R1 rehearsed on the vivo (2026-11-20, Android 17): Telecom asks the user, the carrier is never created

Throwaway branch `spike-carrier-r1` (owner war forced on, test call allowed to engage the carrier),
release build, carrier account `[X]` enabled, Pixel Watch 5 HFP-connected. Never merged.

First, a beta 9 crash the rehearsal surfaced before any call: `carrierEnabled()` →
`TelecomManager.getPhoneAccount` throws `SecurityException` (READ_PHONE_NUMBERS) on vivo Android 17
even for Wristline's own account, so Home crashed on open. Fixed on `notification-voice`
(`getRegisteredPhoneAccounts` fallback, unit-tested); with the fix Home opens and shows "Ready",
i.e. the carrier is read as enabled.

The run (test call answered from the phone with the headset key — `onAnswer` is the same path as a
watch answer; the watch was charging and never got the answer within the 30 s ring twice):

```
17:31:26.698 test call answered on the watch
17:31:26.738 asking Telecom to carry the call's audio mode (the owner war)
Telecom CallTC@65 (MO - outgoing), target "Wristline — voice on watch", selfMgd:false
  17:31:26.745 CREATED
  17:31:26.867 USER_CONFIRMATION   → ConfirmCallDialogActivity:
               "Placing this call will end your Wristline call."  [OK] [Cancel]
  17:32:18.962 USER_CANCELLED (dialog cancelled by hand)
```

`onCreateOutgoingConnection` was never reached: no `CARRIER created`, no `CARRIER failed`.

**R1 fails, and not in a way HyperOS can rescue.** This is stock Telecom: a managed outgoing call
placed while a self-managed call is up needs the user to confirm, and confirming disconnects the
self-managed call. In a real war call the self-managed calls are Wristline's relay and WhatsApp's
own; OK would end the very call the carrier is meant to carry, Cancel leaves the war as it was. The
POCO would show the same dialog (wording "…will end your WhatsApp call" or "…Wristline call").

Consequence: beta 9 as built must not go to the POCO tester. On a war phone with the account
enabled, every call would pop that dialog over the call screen. The carrier-as-outgoing-call
design is dead; a different way to get Telecom to own the mode (e.g. a managed *incoming* call, if
answering it does not end self-managed calls, or nothing at all) is a planning question for the
next plan, not a tweak for this round.

Not measured, because the carrier never existed: `dumpsys telecom` / audio mode owner with the
carrier up.

## 15. The way that works on hardware: the relayed call itself is the managed call (2026-11-20, vivo)

Throwaway branch `spike-managed-relay` (never merge). Each step on the owner's vivo, Android 17,
Pixel Watch 5 on HFP, carrier account enabled.

| # | What the spike did | What Telecom / AudioService did |
|---|---|---|
| E1 | Test call rung on the carrier account, connection **not** self-managed, `setAudioModeIsVoip(false)` | No dialog. `SET_RINGING` → answered → `SET_ACTIVE` → BT. `setMode(MODE_IN_CALL)` by Telecom (pid 2559, uid 1000) |
| E2 | Same, the app calls `setMode(MODE_IN_COMMUNICATION)` 1.5 s after answer | Ignored: mode stays `IN_CALL`, owner Telecom, communication device stays `bt_sco` |
| E3 | Real WhatsApp call relayed the E1 way | **Caller could not hear the owner.** `RecordActivityMonitor`: every WhatsApp capture `src:VOICE_COMMUNICATION silenced`, then `src:MIC silenced` (retries to hang-up). In `IN_CALL` only the mode owner may capture |
| E4 | Real WhatsApp call, managed connection with `setAudioModeIsVoip(true)` | Works both ways (owner confirmed). `setMode(MODE_IN_COMMUNICATION)` by Telecom; WhatsApp capture `not silenced`; route BT |
| E5 | E4 + the app's `setMode(IN_COMMUNICATION)` probe | Stacked under Telecom, never selected while the call was up (`selected … by pid=2559`) |
| E6 | Watch call-out placed with `placeCall` on the carrier account (managed, VoIP) | No confirm dialog, no account chooser: `CREATED selfMgd:false` → `DIALING` → `ACTIVE` → BT; mode by Telecom; WhatsApp capture not silenced; works both ways (owner confirmed) |

What this settles:

- R1's dialog came from *two* calls (a managed one beside a self-managed one). With **one** call —
  the relay itself managed — Telecom never asks.
- The managed call must be **VoIP** (`IN_COMMUNICATION`). `IN_CALL` silences the calling app's
  microphone; the earlier carrier design had it right on paper (`setAudioModeIsVoip(true)`).
- A privileged Telecom owner is not displaced by an app's `setMode` (E2, E5) — the property
  `BETA8_HYPEROS_FIX.md` needs to end the war. The vivo cannot show the war itself; that the
  POCO's Telecom behaves as its decompiled `VoipCallFocusState` says stays the round's bet.
- WhatsApp registers no Telecom account on this phone: no second call to collide with.

Costs seen, accepted for war phones only:

- The phone's dialer (Google Phone) shows the call: heads-up when ringing, `InCallActivity` on
  a call-out. Its call screening runs (`SCREENING_COMPLETED [Allow …]`).
- Managed calls ring the phone with its own ringtone/vibration besides the calling app's ring,
  and go into the phone's call log.
- Once enabled, the account (`CAPABILITY_CALL_PROVIDER`) is a call provider the dialer may offer;
  a call Wristline did not place must be refused.

Replaces the carrier-beside-the-mirror design of §§1–13 and the R1 path of §14. Plan:
`docs/superpowers/plans/2026-11-20-managed-relay.md`.

## 16. Managed relay rehearsed on the vivo (2026-11-20), and the last round's script

Plan code (`notification-voice` through `ff6c02b`) with only `Prefs.ownerWar` forced true on a
throwaway branch (`spike-managed-r2`, deleted), release build, carrier account enabled, Pixel
Watch 5 on HFP:

| # | Call | Result |
|---|---|---|
| 1 | "Ring my watch", answered with the headset key | On "Wristline — voice on watch", no confirmation; `setMode(MODE_IN_COMMUNICATION)` from Telecom at answer; BT route |
| 2 | Real WhatsApp incoming, answered on the watch | Same; WhatsApp `src:VOICE_COMMUNICATION not silenced`; owner: heard both ways |
| 3 | WhatsApp call-out from the watch | `MO - outgoing` on the carrier account → `DIALING` → `ACTIVE` → BT; WhatsApp capture not silenced; owner: works both ways |
| 4 | Carrier account switched off | Not rehearsed on hardware (the switch is the owner's, in Telecom's screen); `RelayHandleTest` covers the fallback to the self-managed relay |

The vivo cannot show the war; what HyperOS does with a privileged Telecom owner is the round's bet
(§15).

Round script for the POCO tester (replaces §11 and §13's):

1. Install beta 9 (phone and watch), open Wristline, all setup green.
2. **Call A:** a WhatsApp call to the POCO, answer on the watch. On HyperOS the war is expected:
   the voice comes to the phone. That is what arms the managed relay. Finish the call on the phone.
3. Open Wristline: a card asks to enable "Wristline — voice on watch". Tap it, flip the switch,
   come back until the card clears. If it never clears, stop and send the report.
4. **Call B:** a WhatsApp call to the POCO, answer on the watch, talk 20 s both ways. The phone's
   dialer showing the call, and the phone ringing too, are expected.
5. **Call C:** call out from the watch on WhatsApp, talk 20 s both ways.
6. Send the report.
