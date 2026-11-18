# Android 17 voice route: who takes it off the watch

Analysis of AOSP `main` (= Android 17, API 37) sources, written 2026-11-17 ahead of the
beta7 field report. Sources read: `AudioService.java`, `AudioDeviceBroker.java`,
`BtHelper.java`, `PlaybackActivityMonitor.java` (frameworks/base @ main),
`CallAudioModeStateMachine.java` (packages/services/Telecomm @ main). Line numbers below
refer to those files as fetched that day.

Goal: name the exact mechanism that closes the watch's Bluetooth voice link ~1 s after
the calling app's audio starts, even though `VoiceHold.take()` is accepted every time —
and to predict what the beta7 system log will show for each candidate.

## How Android 17 decides where a call's voice plays

Two independent arbitrations, both in frameworks/base audio services:

### 1. Who owns the audio mode (AudioService.java)

A per-process stack of "mode handlers" (`SetModeDeathHandler`), one per process that called
`setMode()` with anything other than NORMAL (setMode, ~line 6536):

- The owner is picked by `getAudioModeOwnerHandler()` (~line 6440):
  - a **privileged** handler (holds `MODIFY_PHONE_STATE`: phone process, Telecom)
    always beats non-privileged ones — privileged handlers count as always active;
  - among non-privileged handlers, only ones with an **active voice stream** qualify
    (`updateAudioModeHandlers`, ~line 4778: active = `USAGE_VOICE_COMMUNICATION`
    playback or `VOICE_COMMUNICATION` recording, nothing else counts);
  - among qualifying ones, the **most recently updated** (`setMode` call time) wins.
- A handler entering `MODE_IN_COMMUNICATION` is force-marked active and re-checked after
  **6 s** (`MSG_CHECK_MODE_FOR_UID`, ~line 10408; `CHECK_MODE_FOR_UID_PERIOD_MS = 6000`).
  If its UID then has no playback/recording at all (`isPlaybackActiveForUid`), it is
  demoted and stops being owner.
- Every playback/recording configuration change re-runs this selection.

### 2. Which communication device the whole phone uses (AudioDeviceBroker.java)

Apps that call `setCommunicationDevice()` become per-UID "communication route clients"
(`mCommunicationRouteClients`, newest first). The winner is picked by
`topCommunicationRouteClient()` (~line 514):

1. the route client whose **UID equals the mode owner's UID**;
2. otherwise, if there is **no mode owner at all** (pid 0), the first client in the stack
   — but only while it is active (voice playback/recording for its UID);
3. otherwise **nobody** → the requested communication device is `null`.

A `null` winner means `updateCommunicationRoute()` (~line 2566) removes the preferred
device for the communication strategy → the policy engine falls back to its default
(built-in earpiece), and the Bluetooth voice link is closed:
- `scoManagedByAudio` on (new Android 17 path): the HAL closes the SCO stream when the
  strategy stops routing to the SCO device;
- legacy path: `BtHelper.stopBluetoothSco()` → HeadsetService disconnects SCO.

Both stop paths log through `AudioDeviceBroker`/`BtHelper` tags, which beta7 captures.

## The bounce, step by step

Full mode, answer from the watch, on a phone where the calling app grabs the audio:

1. `VoiceHold.take()` → Wristline plays a silent `USAGE_VOICE_COMMUNICATION` stream,
   calls `setMode(MODE_IN_COMMUNICATION)` (newest non-privileged active handler → owner)
   and `setCommunicationDevice(watch)` (route client, position 0). Mode owner UID ==
   route client UID → rule 1 → watch. Voice reaches the watch. `hold=taken`. ✓
2. `answerApp()` after 1 s → the calling app answers. Its audio starts
   (active voice stream for its UID) **and** it calls `setMode(MODE_IN_COMMUNICATION)`
   (every WebRTC-style calling app does). Its handler is now the newest active
   non-privileged one → **mode owner flips to the calling app**.
3. Mode owner change posts `MSG_L_SET_MODE_OWNER` (AudioDeviceBroker ~line 2031) →
   `topCommunicationRouteClient()`: the calling app has **no route client** (it never
   called `setCommunicationDevice`) and a mode owner exists → rule 3 → nobody →
   preferred communication device = null → strategy preference removed → earpiece,
   SCO closed. Voice falls off the watch. ~1 s after answering: matches the field data.
4. Telecom reports the route change → `RelayConnection.routeToWatch()` →
   `VoiceHold.take()` again → owner flips back, route client rule 1 → watch again.
   `hold=retaken` — accepted every time, exactly as reported. ✓
5. Something flips the owner again (candidates below) → step 3 repeats → after 3 falls
   the bounce guard gives up (`audio_gave_up`).

So VoiceHold is not failing to take; it is losing a repeated race it cannot win by
re-taking, because each take is only valid until the other side's next `setMode`.

## What flips the owner back (candidates for the re-falls)

- **A. The calling app re-inits its audio.** WebRTC-style apps call `setMode` again on
  audio-device changes — and the route flapping itself (watch ↔ earpiece) triggers the
  app's device-change listener → `setMode` → flip → route change → … A self-sustaining
  loop with ~1 s earpiece gaps.
- **B. The 6-second demotion.** Wristline's handler is force-active on each `take()`,
  then checked 6 s later. If the silent stream is somehow not counted as active
  playback, Wristline is demoted and the calling app (still playing voice) becomes
  owner without doing anything. Falls would then be ~6 s apart, not ~1 s.
- **C. The calling app calls `setCommunicationDevice` itself** (earpiece or speaker) —
  then its own route client exists and wins by rule 1 outright; re-taking fights it
  directly. Would show as `setCommunicationRouteForClient for uid: <calling app>` lines.
- **D. Telecom holds a privileged mode handler** — would beat both apps permanently;
  the mode stack entries from `com.android.server.telecom` would show it. Self-managed
  connections do not drive Telecom's `CallAudioModeStateMachine` (self-managed =
  connection handles its own audio), so this is expected *not* to happen, but the
  `CallAudioMode` lines in the report will say.

## What the beta7 report should show (and what it decides)

The report's first line is `getprop bluetooth.sco.managed_by_audio` — which teardown
path the phone uses. Then, per candidate:

| Look for | Meaning |
| --- | --- |
| `setBluetoothScoOn: false … from: setNewModeOwner` (AudioDeviceBroker) | owner flip closed the link — the mechanism above confirmed |
| `updateCommunicationRoute, preferredCommunicationDevice: null eventSource: setNewModeOwner` | same, from the device event log in `dumpsys audio` |
| Mode log entries (`PhoneStateEvent`/`Mode owner` in `dumpsys audio`): whose package set the mode and when | tells A (app re-calls setMode repeatedly, ~1 s loop) vs B (Wristline demoted, ~6 s loop) |
| `setCommunicationRouteForClient for uid: … device: …` | distinguishes C; shows whether the calling app picks its own device |
| `CallAudioMode` lines | D — Telecom in the mode stack or not |
| `CommunicationRouteClient` state checks / `updateCommunicationRouteClientState` | B — Wristline's client going inactive |
| `onScoAudioStateChanged broadcasting state: 3` vs `1` alternation | the physical link up/down, timestamps give the loop period |

The event order around each fall tells the winner: `setNewModeOwner` → `updateCommunicationRoute
preferredCommunicationDevice: null` → SCO disconnect, all within the same second, is
candidate A/C. A fall 6 s after a take with a `CommunicationRouteClient` state change in
between is candidate B.

## The vivo contradiction

v1.4 (no VoiceHold at all) held WhatsApp voice on the watch on the owner's vivo
(Android 17) + Pixel Watch 5. Same arbitration code should close it there too. Something
differs per vendor:

- `bluetooth.sco.managed_by_audio` (captured as the report's first line; HyperOS/POCO
  may set it differently from vivo);
- vendor audio-policy customizations (the arbitration above is AOSP; both OEMs modify);
- which of the calling apps' setMode/device calls actually fire on each skin.

Action: run beta7 on the owner's vivo, one WhatsApp call, same report — the diff between
the two phones' logs localises the vendor difference directly.

## vivo evidence (2026-11-17, beta7 on owner's vivo V2514, two WhatsApp calls)

Both calls identical. Full event sequence from `dumpsys audio` (telecom mirror active, answered
from watch, voice on watch whole call, ~35–55 s):

1. Telecom mirror goes active → **Telecom holds the mode as privileged owner**
   (`setMode(MODE_IN_COMMUNICATION) from com.android.server.telecom`).
2. Telecom's own route client picks **earpiece** first
   (`setCommunicationDevice() targetDevice:earpiece from telecom`).
3. Telecom routes the call to HFP (`cf to hfp switch` event) → SCO to Pixel Watch 5 comes up.
4. **WhatsApp itself calls `startBluetoothSco()`** → WhatsApp becomes a route client on `bt_sco`.
5. The SCO-connected event (`BtHelper.receiveBtEvent(true)`) flips `preferredCommunicationDevice`
   to `bt_sco … Pixel Watch 5`. On vivo `bluetooth.sco.managed_by_audio` is unset (legacy path),
   and in legacy mode a connected SCO outranks *all* route-client arbitration — the SCO-on
   override in `preferredCommunicationDevice()` is unconditional.
6. Nothing changes until Telecom `clearCommunicationDevice()` at call end.

Consequences:

- **vivo needs no VoiceHold at all** — indeed VoiceHold never engaged on vivo: zero
  `setMode`/`setCommunicationDevice` calls from Wristline appear in either call. Telecom's audio
  states apparently never reached the connection with a Bluetooth route, so `routeToWatch()`
  never triggered the take (in-app report will confirm `hold=no watch offered` vs no route events).
- The vivo-vs-POCO difference now has a concrete shape: on vivo, (a) Telecom sets the mode
  (privileged owner, stable — no ping-pong), and (b) WhatsApp itself requests SCO. If on the
  POCO WhatsApp also calls `startBluetoothSco` and SCO still drops, HyperOS's audio stack is
  actively closing it (check `bluetooth.sco.managed_by_audio` there, first line of beta7's
  system-log section). If WhatsApp there does *not* request SCO, the only requester is
  Wristline's VoiceHold, and its mode-owner loss takes SCO down with it
  (`setCommunicationRouteForClient`'s `stopBluetoothSco` path).
- Note: Telecom owning the mode as a *privileged* handler would beat both apps in the
  arbitration on any phone. On the POCO the mirror call is also Telecom's, so the mode owner
  there should also be Telecom — if beta7's report shows WhatsApp (or nobody) owning the mode
  instead, HyperOS is deviating from AOSP mode handling, which is the real bug to work around.

## Study conclusions (2026-11-17, part A/B complete; C pending)

**A — AOSP arbitration cannot drop a connected SCO.** Traced every stop path in
`AudioDeviceBroker`/`BtHelper`: `onUpdateCommunicationRouteClient`'s stop condition can never
fire on a mode-owner flip (both `isBluetoothScoRequested()` and `wasScoRequested` are evaluated
after the flip — both false); `preferredCommunicationDevice()`'s SCO-connected override keeps
the watch preferred in legacy *and* `scoManagedByAudio` modes; the 6 s demotion checks can't hit
Wristline while the silence stream plays. So the POCO's 1 s drop must originate in the
Bluetooth stack (`HeadsetService` HFP audio close), the `scoManagedByAudio` HAL stream
semantics, or HyperOS customizations — not in the audio-framework arbitration VoiceHold
fights. The vivo logs support this: on vivo nothing closes SCO for the whole call.

**B — Telecom drives both mode and SCO for the mirror call.** Vivo-verified: Telecom holds the
mode as privileged owner for the entire self-managed mirror call (RINGTONE → IN_COMMUNICATION
→ NORMAL at end), and Telecom's route controller (`CallAudioRouteController` →
`PendingAudioRoute`/`BluetoothRouteManager`) connects HFP audio ("cf to hfp switch") and
calls `clearCommunicationDevice()` only at call end. On AOSP-shaped phones the mirror call is
audio-stable by construction.

**C — BT stack closes HFP audio only on explicit request or remote action.** In
`HeadsetService`, the only senders of `DISCONNECT_AUDIO` are: `stopVoiceRecognition` (legacy,
when `scoManagedByAudio` off — then it also does `clearCommunicationDevice()`), the public
`disconnectAudio()` (called by apps or the system, e.g. `BtHelper.stopBluetoothSco`), and
native `HeadsetStackEvent`s (radio link, remote close). No internal timer, no mode-watcher, no
call-state watcher closes audio on its own. Combined with A: **nothing in AOSP main closes
HFP audio mid-call while a requester holds it.** The POCO closer is therefore one of:
(1) HyperOS customization in audio/BT/telecom, (2) `scoManagedByAudio` HAL-stream routing
semantics (strategy stops routing to SCO → HAL closes the stream even while the BT profile
audio is up), or (3) **the watch side closing HFP audio** — OPPO Watch X3/OHealth is an actor
we never inspected; a remote close arrives as a native stack event. The beta7 report's
`HeadsetStateMachine` lines (`AudioDisconnecting`, `mAudioState`) discriminate: a remote close
shows native/bta_ag/btm_sco events before the disconnect, an audio-framework close shows
`stopBluetoothSco`/`setBluetoothScoOn: false` first.

**Known bug found on vivo (beta8 candidate, independent of POCO):** `routeToWatch()` sets
`holdingVoice = true` before `VoiceHold.take()` succeeds; when `take()` hits
`hold=no watch offered` (vivo: SCO device appeared ~95 ms after the attempt) it never retries —
`!onWatch || !holdingVoice` stays false for the rest of the call. Fix: only latch
`holdingVoice` on a successful take, and re-attempt when the offered devices gain a Bluetooth
one (`AudioLink.Watcher` already observes `onAudioDevicesAdded`).

**Critical path:** the POCO reporter's beta7 report. With A established, the report decides
between the BT-stack/vendor closers; without it any fix is a guess.

## HyperOS teardown (2026-11-18, POCO F8 Pro ROM OS4.0.2.0.XPKMIXM Global, Android 17)

ROM: `cdnorg.d.miui.com/OS4.0.2.0.XPKMIXM/annibale_global-ota_full-OS4.0.2.0.XPKMIXM-user-17.0-52a5ad5b93.zip`
(reporter's exact build). Extracted `system.img` (EROFS, `fsck.erofs --extract`), decompiled
`services.jar` + `system_ext/framework/miui-services.jar` with jadx. Xiaomi rewrote the whole
arbitration:

- `AudioDeviceBroker` uses a custom **`RouteSelectionStack`** (`com.android.server.audio.RouteSelectionStack`)
  instead of AOSP's route-client list. `topClient()`: (1) client whose token == the **mode owner
  token** wins; (2) if a mode owner exists but has no route client → **empty → nobody → earpiece**;
  (3) no owner → last active client.
- Every mode-owner change → `onUpdateCommunicationRouteClient("setNewModeOwner")`
  (AudioDeviceBroker ~line 2140) → topClient() → if null → `stopBluetoothSco` (~line 2710),
  guarded only by Xiaomi's `AudioServiceStubImpl.shouldSkipStopScoForScoEnhance`
  (miui-services.jar, line 2687): skip-stop only when mode is IN_CALL/IN_COMMUNICATION **and the
  new mode owner is not recording**. The owner here is **WhatsApp, which always records during a
  call** → guard fails → **SCO closed on every owner flip, even though Wristline's SCO route
  client is still active in the stack.** AOSP's stop condition is unreachable (study conclusion A);
  Xiaomi made it reachable and tied it to the owner.
- `preferredCommunicationDevice()` additionally consults Xiaomi's
  `AudioDeviceBrokerStubImpl.getFallbackBtCommunicationDevice` + `inCallClient()`, and
  `setCommunicationRouteForClient` gates starts through `shouldSkipStopScoForScoEnhance`-style
  guards and an `isSupportFakeHfp` per-app check (line 510).
- Mode-owner selection itself (`AudioService.getAudioModeOwnerHandler` ~line 5774) keeps AOSP
  semantics: privileged handler always wins, else newest active. So the POCO's owner flips mean
  **no privileged handler is active there** — HyperOS Telecom does NOT hold the mode for the
  self-managed mirror call (vivo's does, which is why vivo never flips and never drops).

**POCO loop, proven end to end:** answer → WhatsApp `setMode(IN_COMMUNICATION)` (owner) →
Wristline `take()` → owner flips to Wristline, SCO up → WhatsApp's audio re-inits on the route
change (or otherwise re-calls setMode) → owner flips to WhatsApp → WhatsApp has no route client →
topClient empty → `shouldSkipStopScoForScoEnhance` fails (owner records) → `stopBluetoothSco` →
Telecom routes earpiece → Wristline re-takes → owner flips again → repeat ×3 → give up. Matches
every observed timestamp, the `device=earpiece` hold checks while `sco_on=true`, and vivo-vs-POCO
difference (vivo: WhatsApp requests SCO via `startBluetoothSco`, never re-flips the owner).

## Fix directions (pick after the log, not before)

- If A (app re-inits, ~1 s loop): re-taking can never stabilise it — Wristline would
  need to re-assert on *mode change* (`OnModeChangedListener`, already in
  `AudioLink.Watcher` for reporting) within milliseconds, or stop fighting and keep the
  route only while no other owner exists. Each flip costs a reroute; even a fast
  re-assert leaves an audible gap and keeps the loop alive. May be unwinnable by
  arbitration — then the fallback is to accept the phone voice (current guard behavior)
  or route the app's *notification-answer* differently.
- If B (6 s demotion): make the silent stream unambiguous — e.g. a
  `VOICE_COMMUNICATION` *recording* (AudioRecord) alongside playback, or verify the
  static-mode track registers as active playback; fix is small and local to VoiceHold.
- If C (app picks its own device): unfixable by arbitration for the same reason as A;
  document the limitation.
- If D (Telecom holds the mode): flip approach entirely — become the owner Telecom
  expects instead of fighting it.
