# Beta 8: the HyperOS SCO drop, designed out

Written 2026-11-18, after the beta7 POCO report (2026-11-17, POCO F8 Pro, WhatsApp calls) and a
second pass over the decompiled HyperOS audio framework. Builds on `ANDROID17_VOICE_ROUTE.md`;
everything below cites the decompiled sources unless said otherwise. Source files:
`/tmp/hyperos/{AudioService,AudioDeviceBroker,RouteSelectionStack,AudioServiceStubImpl,
AudioDeviceBrokerStubImpl}.java` (services.jar), `BtHelper.java`,
`AudioModeSession.java`, and `CallAudioModeStateMachine.java` (Telecom.apk), ROM
OS4.0.2.0.XPKMIXM.

## The war mechanism, fully code-confirmed

1. `AudioService.setMode` → `onUpdateAudioMode` → **`postSetModeOwner` on every `setMode`
   call** (`AudioService` ~line 6157) — not just when the owner changes. A same-owner
   re-assert by WhatsApp fires the same message as a flip.
2. Broker message 16/17 (`AudioDeviceBroker` ~line 2137): sets `mAudioModeOwner` +
   `setModeOwnerToken`, then — while the mode is `IN_CALL`/`IN_COMMUNICATION` — calls
   `onUpdateCommunicationRouteClient("setNewModeOwner")`.
3. `topClient()` (`RouteSelectionStack` line 137): the client whose token equals the mode
   owner token wins; if the owner's token is not in the route stack, the answer is empty
   (an owner exists, so the last-active fallback does not apply).
4. Empty `topClient()` → the two stop guards, then `mBtHelper.stopBluetoothSco`
   (`AudioDeviceBroker` line 2709-2710):
   - `shouldSkipStopScoForScoEnhance` skips only when the mode is RINGTONE/IN_COMMUNICATION
     **and the new mode owner is not recording** (`AudioServiceStubImpl` line 2687).
     WhatsApp always records during a call → never skips.
   - `shouldSkipStopScoForBtRouteFallback` skips only for a disabled BLE-headset client
     (device type 26) → never applies to classic HFP.
5. So: every `setMode` out of WhatsApp while it owns the mode closes the SCO link, and
   Wristline's own `take()` is what provokes those re-`setMode` calls (each route change
   re-inits WhatsApp's audio). The beta7 report matches: `hold=taken` → fall within ~2 s →
   `hold=retaken` → fall → ×3 → `audio_gave_up`, on every full-mode call.

Three things are newly proven, beyond the earlier teardown:

- **No out-of-arbitration request exists.** The deprecated `startBluetoothSco()` goes through
  `startBluetoothScoForClient` → `setCommunicationRouteForClient` → the same route-client
  stack (`AudioDeviceBroker` lines 1237-1248). A "request SCO and stay out of the mode
  fight" design is impossible; any SCO request is a route client with Wristline's token, and
  every owner message with WhatsApp as recording owner still stops it.
- **Xiaomi's own bounce mitigation does not cover this path.** `onNotifyBtStopBluetoothSco`
  delays a stop when the *next* mode-owner pid holds a SCO request (multi-VoIP support), and
  WeChat gets a forced owner signal — but these hooks are not called from the
  `setNewModeOwner` stop path. The direct `stopBluetoothSco` at line 2710 bypasses them.
- **HyperOS Telecom kept AOSP's call-audio machinery.** `CallAudioModeStateMachine` is
  present in Telecom.apk; its `VoipCallFocusState.enter` calls `setMode(MODE_IN_COMMUNICATION)`
  from `com.android.server.telecom` — a **privileged** mode handler, which no app can
  displace. It runs for managed calls only; that is why the self-managed mirror gets no
  owner on HyperOS while vivo's customized Telecom holds the mode even for self-managed
  calls.

Conclusion: within the audio framework the war is unwinnable — re-taking, passive SCO
requests, and instant re-asserts all die on the same guard. The fix has to change who owns
the mode. The stable state to copy is vivo's: Telecom as privileged owner with its own
route client. HyperOS will not do that for a self-managed mirror, so Wristline has to give
Telecom a *managed* call to drive.

## The fix: a carrier call

A second Telecom connection, created when the call is answered from the watch, that is
**not** self-managed and carries no audio of its own:

- `PhoneAccount` "carrier" (second handle, no `CAPABILITY_SELF_MANAGED`, SIP scheme) beside
  the existing self-managed "relay" account. The mirror keeps handling ringing exactly as
  now — the carrier is created only at answer time, so nothing about ring-by-notification
  changes.
- On answer (full mode, Android 17+): `placeCall` on the carrier account →
  `onCreateOutgoingConnection` → new connection (no `PROPERTY_SELF_MANAGED`,
  `setAudioModeIsVoip(true)`) that goes straight `setActive()` and
  `setAudioRoute(ROUTE_BLUETOOTH)`. Telecom then:
  1. holds the mode as privileged owner (`VoipCallFocusState` → `setMode(3)`) — WhatsApp's
     `setMode` storm can no longer move the owner, so no owner messages with a
     recording owner ever fire;
  2. connects HFP audio to the watch through its own route controller ("cf to hfp
     switch") — Telecom's route client sits in `RouteSelectionStack` with the *owner's*
     token, so `topClient()` is rule 1, never empty, and the stop guards are unreachable.
- `answerApp()` runs after the carrier is active, as `ANSWER_DELAY_MS` allows today.
- Destroyed on hang-up, on `moveVoice(toPhone)`, re-created on `moveVoice(toWatch)`.
- While the carrier is up, `VoiceHold` is not used (its `setMode` would be harmless — the
  privileged owner always wins — but it adds nothing and its route client confuses the
  stack's activity checks).

This is vivo's proven-stable configuration, forced through the one door HyperOS leaves
open (managed calls drive `CallAudioModeStateMachine`), instead of depending on a vendor
customization.

### When the carrier engages

Learned per phone, not per call: only after a call shows the war signature on Android 17
(a fall from the watch within ~2 s of `hold=taken`). First war call → set the pref →
carrier from the next answered call on. Rationale:

- vivo never wars (Telecom already owns its mode) — the carrier must never engage there;
- an AOSP-shaped Android 17 phone may or may not war; adaptive gating keeps the current
  `VoiceHold` behavior as the default there until field data says otherwise.

If carrier creation fails (`onCreateOutgoingConnectionFailed`, missing `CALL_PHONE`
permission, Telecom refusing a non-dialer managed account), the answer falls back to the
current `VoiceHold` path — the failure is reportable (`carrier=failed`) and the call
proceeds as in beta7.

### Known costs, accepted for the experiment

- The phone's InCallUI (Google dialer on the POCO) shows an active "Wristline" call while
  the carrier is up. Acceptable while the carrier is war-gated; revisit if it graduates to
  default.
- Telecom writes a CallLog entry for the carrier call (caller string as address).
- `CALL_PHONE` (normal permission) must be granted for `placeCall` on a managed account.

## Also in beta 8, independent of the carrier

1. **The vivo latch bug** (from `ANDROID17_VOICE_ROUTE.md`): `routeToWatch` latches
   `holdingVoice = true` before `take()` succeeds; a `hold=no watch offered` never retries
   (the SCO device appears ~100 ms later, after the attempt). Fix: `VoiceHold.take()`
   latches `held` only on an accepted take; `routeToWatch` stores the return value; and
   `AudioLink.Watcher` gains an optional `onBluetoothDevicesAdded` hook so `RelayConnection`
   retries the take when a SCO/BLE device appears while the call is active and unheld.
2. **One-flap capitulation**: a fall within 2 s of a take is the war signature — stop
   fighting immediately (release, voice to the phone) instead of taking three bounces.
   War phones give their users one audible gap, not six. Non-war falls (a real Bluetooth
   drop, the watch leaving range) keep the existing `MAX_BOUNCES = 3`.

## Files

| File | Change |
| --- | --- |
| `VoiceHold.java` | `held` latches only on accepted take; return value meaningful |
| `AudioLink.java` | `Watcher` constructor takes an optional devices-added hook (null = today's report-only behavior) |
| `RelayConnection.java` | latch fix wiring; war signature detection (`lastTakeAt`); carrier lifecycle (create on answer when the pref is set, destroy on end/`moveVoice(toPhone)`, re-create on `moveVoice(toWatch)`); skip `VoiceHold` while the carrier is up |
| `RelayConnectionService.java` | route `onCreateOutgoingConnection` by account id; create the carrier connection |
| `RelayListener.java` | register the second, managed `PhoneAccount` |
| new `CarrierConnection.java` | minimal managed connection: active, voip, `ROUTE_BLUETOOTH`, no self-managed property |
| `Prefs.java` | the war-gate pref |
| `EventLog.java` / `Diagnostics` | `carrier=created/failed/ended`, `audio_gave_up … owner war` |
| `VoiceHoldTest`-style unit tests | take-latch semantics, war detection timing, carrier gating |

## Verification

- POCO reporter runs beta8: first call shows the war once (one gap, `audio_gave_up … owner
  war`); from the second call on, the report must show `carrier=created`, whole-call
  `sco_on=true`, no `hold=` lines at all, and — in the system log's `PhoneStateEvent` —
  the mode owned by `com.android.server.telecom` throughout.
- vivo owner re-tests: no behavior change (no war → carrier never engages; latch fix should
  be invisible since `routeToWatch` never fires there today).
- Still unknown and worth one ask: the POCO's `bluetooth.sco.managed_by_audio` (the beta7
  report copy was truncated before the system log). The carrier fix works on either value —
  it sidesteps `preferredCommunicationDevice()` arbitration entirely — so this is for the
  record, not a gate.
