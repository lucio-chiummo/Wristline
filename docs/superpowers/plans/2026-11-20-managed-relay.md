# Managed Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a phone that has lost the mode-owner war, relay WhatsApp (and every supported app's) calls as one *managed* VoIP Telecom call on the carrier account, so Telecom owns `MODE_IN_COMMUNICATION` and the voice stays on the watch — replacing the dead carrier-beside-the-mirror design.

**Architecture:** One decision point, `RelayListener.relayHandle(Context)`, picks the carrier account (managed) or the relay account (self-managed) for every `addNewIncomingCall` / `placeCall`. `RelayConnectionService` builds a `RelayConnection` that knows which it is; a managed one is not self-managed, stays VoIP, routes with `setAudioRoute` only and never takes the voice itself (no `VoiceHold`), so nothing in Wristline fights Telecom for the mode. The whole carrier machinery (`CarrierConnection`, `engageCarrier`, the carrier ask/timeout/check) is deleted.

**Tech Stack:** Java, Android Telecom (`ConnectionService`, `PhoneAccount`), Robolectric 4.17 unit tests, Gradle; JDK 21 from Android Studio's `jbr`.

**Spec:** `docs/POCO_BETA8_WAR_PLAN.md` §15 (hardware results E1–E6), with §13 (account enablement, Home card) and §14 (why the two-call carrier fails). Background: `docs/BETA8_HYPEROS_FIX.md`.

## Global Constraints

- Managed connection: connection properties **without** `PROPERTY_SELF_MANAGED`, `setAudioModeIsVoip(true)` — never `false` (E3: `IN_CALL` silences the calling app's microphone).
- Managed only when `Prefs.ownerWar(context) && RelayListener.carrierEnabled(context)`; every other phone keeps today's self-managed relay unchanged.
- The carrier account keeps label "Wristline — voice on watch", schemes `tel` + `sip`, `CAPABILITY_CALL_PROVIDER` (§13 — it must stay enableable).
- No new permissions (`READ_PHONE_NUMBERS` stays out; see `carrierEnabled()` fallback, commit `2eed656`).
- Report keeps the line `Mode-owner war: … · carrier account: enabled/DISABLED`; event code `EventLog.CARRIER` is reused for the managed relay's events.
- Build: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, release with `WRISTLINE_KEYSTORE_PROPERTIES=$HOME/.wristline-keys/keystore.properties`.
- Tests: `./gradlew testDebugUnitTest` all modules green (635 before this plan, minus deleted carrier tests, plus new ones).
- Commit messages: plain sentence subject in the repo's style ("Read the carrier account without crashing…"), with the session's attribution lines.

## Review Focus

1. **A call Wristline did not place arrives on the carrier account** (the dialer offers the enabled call provider; the user dials a number with it) — expected: refused at once with a failed connection, no mirror, no app started, no crash.
2. **War armed mid-call** (call A loses the war while self-managed) — expected: call A keeps today's give-up (voice on phone); only the *next* call is managed. A connection never changes kind mid-call.
3. **Carrier account switched off again after being enabled** — expected: `relayHandle` falls back to the relay account; Home shows the CARRIER_OFF card again; no call is placed on a disabled account.
4. **Managed call's route falls from Bluetooth** (watch out of range, or HyperOS still moving the device) — expected: never re-arms or reports "owner war"; bounce give-up (`MAX_BOUNCES`) still keeps the voice on the phone instead of fighting.
5. **The user hangs up from the phone's dialer screen** (the managed call shows in Google Phone) — expected: same as a hang-up from the watch: `onDisconnect` ends the calling app's call and the mirror.

---

### Task 1: One decision point for the relay account

**Files:**
- Modify: `app/src/main/java/app/wristline/RelayListener.java` (new method beside `carrierHandle`, ~line 80; the three `addNewIncomingCall(accountHandle(…), extras)` calls at ~309, ~351, ~404; `EXTRA_PHONE_ACCOUNT_HANDLE` in `outgoing()` ~527)
- Test: `app/src/test/java/app/wristline/RelayHandleTest.java` (create)

**Interfaces:**
- Produces: `static PhoneAccountHandle RelayListener.relayHandle(Context context)` — `carrierHandle(context)` when `Prefs.ownerWar(context) && carrierEnabled(context)`, else `accountHandle(context)`.
- Produces: `static boolean RelayListener.isCarrier(PhoneAccountHandle account, Context context)` — true when `account` is non-null and its id equals `carrierHandle(context).getId()`. (Replaces the inline id comparisons in `RelayConnectionService`.)

- [ ] **Step 1: Write the failing tests** (Robolectric, `@Config(sdk = 36)`; enable/disable the carrier the way `CarrierCallTest.carrierAccountEnabled` does — reflection on `mIsEnabled` then re-register)

```java
@Test public void aPhoneThatNeverLostTheWarRelaysSelfManaged() {
    carrierAccountEnabled(true);
    assertEquals(RelayListener.accountHandle(context), RelayListener.relayHandle(context));
}
@Test public void aWarPhoneWithTheCarrierOffRelaysSelfManaged() {
    Prefs.setOwnerWar(context, true); carrierAccountEnabled(false);
    assertEquals(RelayListener.accountHandle(context), RelayListener.relayHandle(context));
}
@Test public void aWarPhoneWithTheCarrierOnRelaysOnTheCarrier() {
    Prefs.setOwnerWar(context, true); carrierAccountEnabled(true);
    assertEquals(RelayListener.carrierHandle(context), RelayListener.relayHandle(context));
}
@Test public void theTestCallRingsOnTheChosenAccount() {
    Prefs.setOwnerWar(context, true); carrierAccountEnabled(true);
    assertTrue(RelayListener.startTestCall(context));
    // ShadowTelecomManager.getAllIncomingCalls(): the one call's handle is carrierHandle(context)
}
@Test public void isCarrierMatchesOnlyTheCarrierHandle() {
    assertTrue(RelayListener.isCarrier(RelayListener.carrierHandle(context), context));
    assertFalse(RelayListener.isCarrier(RelayListener.accountHandle(context), context));
    assertFalse(RelayListener.isCarrier(null, context));
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'app.wristline.RelayHandleTest'` — expected: compile failure (`relayHandle`, `isCarrier` undefined).
- [ ] **Step 3: Implement** `relayHandle` and `isCarrier`; replace `accountHandle(…)` with `relayHandle(…)` at the three `addNewIncomingCall` sites and the outgoing `EXTRA_PHONE_ACCOUNT_HANDLE`. Leave `registerAccount` and every other `accountHandle` use alone.
- [ ] **Step 4: Run** the new test class, then `./gradlew testDebugUnitTest` — expected: all green except possibly `CarrierCallTest` cases that assumed the relay account on a war phone (Task 3 deletes that class; note failures, do not fix them here).
- [ ] **Step 5: Commit** — "Pick the relay account in one place: the carrier on a war phone that enabled it"

### Task 2: The managed relay connection, and refusing calls Wristline didn't place

**Files:**
- Modify: `app/src/main/java/app/wristline/RelayConnection.java` (constructor ~line 77; `onCallAudioStateChanged` ~451; `routeToWatch` ~527; `retryVoiceToWatch` ~702; `moveVoice` ~126)
- Modify: `app/src/main/java/app/wristline/RelayConnectionService.java` (`onCreateIncomingConnection`, `onCreateOutgoingConnection`)
- Test: `app/src/test/java/app/wristline/ManagedRelayTest.java` (create; copy the helpers `latest`, `voiceHeld`, `audio`, `offerWatchToApps`, `ringingCall`, `appAnswer` from `CarrierCallTest`, setUp with `VoiceHold.sdk = 37`, `Prefs.setOwnerWar(context, true)`, carrier enabled)

**Interfaces:**
- Consumes: `RelayListener.isCarrier(PhoneAccountHandle, Context)` (Task 1).
- Produces: `RelayConnection(Context context, boolean managed)`; `final boolean managed` field (package-private, read by tests). The one-arg constructor is removed; every caller passes the kind.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void anIncomingCallOnTheCarrierIsManagedAndVoip() {
    RelayConnection c = (RelayConnection) service.onCreateIncomingConnection(RelayListener.carrierHandle(context), request());
    assertTrue(c.managed);
    assertEquals(0, c.getConnectionProperties() & Connection.PROPERTY_SELF_MANAGED);
    assertTrue(c.getAudioModeIsVoip());
}
@Test public void anIncomingCallOnTheRelayStaysSelfManaged() {
    RelayConnection c = (RelayConnection) service.onCreateIncomingConnection(RelayListener.accountHandle(context), request());
    assertFalse(c.managed);
    assertEquals(Connection.PROPERTY_SELF_MANAGED, c.getConnectionProperties() & Connection.PROPERTY_SELF_MANAGED);
}
@Test public void aManagedCallRoutesToTheWatchWithoutTakingTheVoice() {
    // answer, then onCallAudioStateChanged(audio(EARPIECE, ALL)) with offerWatchToApps()
    // expect: route requested BLUETOOTH (getCallAudioState / shadow), voiceHeld() == false, audioMode() unchanged by Wristline
}
@Test public void aManagedCallsFallFromTheWatchNeverArmsOrReportsTheWar() {
    // active on BLUETOOTH, then audio(EARPIECE, ALL) at once (inside WAR_WINDOW_MS)
    // expect: no AUDIO_GAVE_UP entry whose detail starts with "owner war"
}
@Test public void aManagedCallGivesUpAfterMaxBouncesLikeAnyOther() {
    // MAX_BOUNCES falls BLUETOOTH→EARPIECE outside the war window
    // expect: latest(EventLog.AUDIO_GAVE_UP).detail == "left the watch 3 times"; voiceOnWatch() == false
}
@Test public void movingAManagedCallsVoiceToThePhoneAndBackOnlyReroutes() {
    // moveVoice(true) → route EARPIECE, voiceHeld() false; moveVoice(false) → route BLUETOOTH, voiceHeld() false
}
@Test public void aManagedCallIsRecorded() {
    // after onCreateIncomingConnection on the carrier: latest(EventLog.CARRIER).detail == "managed relay"
}
@Test public void aCallWristlineDidNotPlaceOnTheCarrierIsRefused() {
    Connection c = service.onCreateOutgoingConnection(RelayListener.carrierHandle(context),
            new ConnectionRequest(RelayListener.carrierHandle(context), Uri.parse("tel:+390000000"), new Bundle()));
    assertEquals(Connection.STATE_DISCONNECTED, c.getState());
    assertEquals(DisconnectCause.ERROR, c.getDisconnectCause().getCode());
    assertNull(RelayConnection.current);
}
@Test public void aWatchCallOutOnTheCarrierIsManaged() {
    // EXTRA_OUTGOING_CALL_EXTRAS-style request with EXTRA_CALLER and EXTRA_FULL_MODE true, on carrierHandle
    // expect: RelayConnection, managed == true, RelayConnection.current == it
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'app.wristline.ManagedRelayTest'` — expected: compile failure (two-arg constructor, `managed`).
- [ ] **Step 3: Implement.**
  - Constructor: `setConnectionProperties(managed ? 0 : PROPERTY_SELF_MANAGED)`; `setAudioModeIsVoip(true)` for both.
  - `routeToWatch`: the take block (`VoiceHold.needed()` … `voiceHold.take()`) runs only when `!managed`; the `setAudioRoute(ROUTE_BLUETOOTH)` at the end runs for both.
  - `onCallAudioStateChanged`: `war` is false when `managed`; bounce counting unchanged.
  - `retryVoiceToWatch`: no-op when `managed`.
  - `moveVoice`: for `managed`, only `setAudioRoute(EARPIECE)` / `routeToWatch`; the hold release stays harmless.
  - Service `onCreateIncomingConnection`: `new RelayConnection(this, RelayListener.isCarrier(account, this))`; when managed, `Diagnostics.record(this, EventLog.CARRIER, testCall ? null : RelayListener.currentApp, "managed relay")`.
  - Service `onCreateOutgoingConnection`: if `isCarrier` and the extras carry no `RelayListener.EXTRA_CALLER`, log and return `Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR))`; otherwise build `new RelayConnection(this, isCarrier(account, this))` as today, with the same "managed relay" record.
- [ ] **Step 4: Run** `ManagedRelayTest`, then `VoiceHoldTest`, `TestCallFlowTest`, `RelayListenerTest` — expected: PASS.
- [ ] **Step 5: Commit** — "Relay a war phone's calls as one managed VoIP call, and refuse calls Wristline didn't place"

### Task 3: Delete the carrier-beside-the-mirror machinery

**Files:**
- Delete: `app/src/main/java/app/wristline/CarrierConnection.java`, `app/src/test/java/app/wristline/CarrierCallTest.java`
- Modify: `app/src/main/java/app/wristline/RelayConnection.java` — remove `CARRIER_WAIT_MS`, `CARRIER_CHECK_MS`, `carrierAsked`, `carrierAskId`, `watchCarrier`, `myCarrier`, `carrierFailedThisCall`, `engageCarrier`, `carrierTimedOut`, `failCarrier`, `checkCarrier`, `carrierFailed`, `carrierCreated`, `carrierGone`, `endCarrier` and their call sites (`moveVoice`, `end`, `onCallAudioStateChanged`'s `myCarrier != null` branch, the reset at ~147)
- Modify: `app/src/main/java/app/wristline/RelayConnectionService.java` — remove `createCarrier` and the carrier branch of `onCreateOutgoingConnectionFailed`
- Test: move into `ManagedRelayTest` the war-arming tests that still describe live code: `registersAManagedCarrierAccountBesideTheSelfManagedOne`, `aPhoneThatNeverLostAWarHoldsTheVoiceItself`, `aLossInsideTheWarWindowGivesUpAndArmsTheWar`, `aLossLaterThanTheWarWindowGivesUpWithoutArming`, `aFallAt2500msIsWarWithTheWiderWindow`, `aBounceGiveUpDoesNotArmTheWar` — run them on a **self-managed** connection (relay handle, carrier off), since only self-managed calls arm the war.

**Interfaces:**
- Consumes: Task 2's `RelayConnection(Context, boolean)`.
- Produces: nothing new; `rg -n 'Carrier(Connection|Created|Failed|Gone)|engageCarrier|CARRIER_(WAIT|CHECK)_MS|myCarrier' app/src` returns no hits.

- [ ] **Step 1: Move the six tests** into `ManagedRelayTest` (self-managed setup per test), delete `CarrierCallTest`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'app.wristline.ManagedRelayTest'` — expected: PASS (they test code that stays).
- [ ] **Step 3: Delete** the machinery listed above.
- [ ] **Step 4: Run** the `rg` above (no hits) and `./gradlew testDebugUnitTest` — expected: all green, both modules.
- [ ] **Step 5: Commit** — "Remove the carrier call beside the mirror: Telecom asks before it, and the managed relay replaces it"

### Task 4: Rehearse on the vivo, write the round script, release

**Files:**
- Modify: `docs/POCO_BETA8_WAR_PLAN.md` (append §16)
- Modify: `app/src/main/java/app/wristline/DiagnosticReport.java:100` — `"lost (carrier armed)"` → `"lost (managed relay)"` (no test pins it today)
- No other code changes. Spike branches stay unmerged.

- [ ] **Step 1: Build the rehearsal APK.** The vivo never loses the war, and no shipped path sets `Prefs.ownerWar` except a war-shaped loss. Make throwaway branch `spike-managed-r2` off `notification-voice` with one change — `Prefs.ownerWar` returns `true` — build release, install on `adb -s 10AFCB1Z4F00380`. Never merge; delete the branch after Step 3.
- [ ] **Step 2: Rehearse four calls**, reading `dumpsys telecom`, `dumpsys audio` (`setMode` history, `RecordActivityMonitor`) after each:
  1. "Ring my watch" (answer with `adb shell input keyevent KEYCODE_HEADSETHOOK` if the watch can't) — expected: `CREATED … selfMgd:false`, no `USER_CONFIRMATION`, `setMode(MODE_IN_COMMUNICATION)` from `com.android.server.telecom`, BT route.
  2. Real WhatsApp incoming answered on the watch — expected: as 1, plus WhatsApp `rec … not silenced`; owner hears and is heard.
  3. Watch call-out on WhatsApp — expected: `MO - outgoing … selfMgd:false`, `ACTIVE`, BT, `not silenced`.
  4. Carrier account switched off in the enable screen → "Ring my watch" — expected: self-managed (`selfMgd` absent / relay account), Home shows the CARRIER_OFF card.
- [ ] **Step 3: Append §16** to `docs/POCO_BETA8_WAR_PLAN.md`: the four results, and the **round script** for the POCO tester:
  1. Install, open Wristline, all setup green.
  2. Call A: WhatsApp incoming, answer on the watch. Expected on HyperOS: the war, the voice comes to the phone — this arms the war. Finish the call on the phone.
  3. Open Wristline: the card asks to enable "Wristline — voice on watch"; tap it, flip the switch, come back until the card clears. If it never clears, stop and send the report.
  4. Call B: WhatsApp incoming, answer on the watch, talk 20 s both ways. The phone's dialer showing the call is expected.
  5. Call C: call out from the watch on WhatsApp, talk 20 s both ways.
  6. Send the report.
- [ ] **Step 4: Release.** Push `notification-voice`; `gh release create v1.5-beta9 --prerelease` with `wristline-phone-1.5-beta9.apk` and `wristline-watch-1.5-beta9.apk` built from the new head (same asset names as v1.5-beta8); notes state the round script above.
- [ ] **Step 5: Commit** §16 before the push — "Write down the managed relay rehearsal and the last round's script"
