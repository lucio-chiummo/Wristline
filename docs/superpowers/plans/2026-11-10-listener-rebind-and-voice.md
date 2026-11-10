# Listener Rebind (and Optional Settings Voice Route) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When Android (HyperOS first) leaves `RelayListener` unbound while Notification access is still granted, Wristline notices, says so on the status card with a Fix button, recovers by itself (request rebind, then disable/enable the component), tries again from the Quick Settings tile, the watch and a periodic check, and logs how long it was unbound.

**Architecture:** A new `ListenerRebind` class owns recovery: `requestRebind`, wait `CHECK_AFTER_MS`, then toggle the component with `setComponentEnabledSetting(..., DONT_KILL_APP)`. It replaces `Diagnostics.rebindListenerIfNeeded`. `RelayListener` records the unbound gap through `Prefs.listenerUnboundSince`. `SetupStatus` gets a `NOT_RECEIVING` kind. A `JobService` (`ListenerCheckJob`, platform `JobScheduler`, no new dependency) runs the check every 15 min while relaying is on. Voice setup stays on the adb path. Only an optional last task adds a per-app Settings route, and only for apps that pass the spike.

**Tech Stack:** Java 21, Android `NotificationListenerService`, `PackageManager`, `JobScheduler`, XML layouts, JUnit 4 + Robolectric 4.17 (`@Config(sdk = 36)`), Gradle.

**Spec:** `docs/superpowers/specs/2026-11-10-no-computer-setup-design.md` (sections "Phase 1b: listener unbound on Xiaomi" and, for the optional Task 7, "Phase 1: voice setup from Settings")

## Global Constraints

- Build/test with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (JDK 21). Phone tests: `./gradlew :app:testDebugUnitTest`. Baseline 2026-11-10: green.
- Every new string in `app/src/main/res/values/strings.xml` **and** `values-it/strings.xml`.
- No new `<uses-permission>`, no new dependency (so `gradle/verification-metadata.xml` is untouched), no internet permission. The job service's `android:permission="android.permission.BIND_JOB_SERVICE"` attribute is a protection, not a requested permission.
- Spike result 2026-11-10: with `READ_PHONE_STATE` revoked, WhatsApp shows its own "needs to know whether you are on an active call" dialog on Answer and the call is never answered. Voice setup stays on `ComputerSetup` / adb, unchanged. Telegram and Signal: untested.
- Never toggle the component while `RelayListener.isConnected()` is true or Notification access is not granted.
- `CHECK_AFTER_MS = 5_000` (spec: "within a few seconds"); periodic check interval `15 min` (the `JobInfo` minimum).
- Event codes stay lower_snake: existing `listener_rebind`, `listener_connected`, new `listener_toggled`.
- Commit only when the owner has asked; until then end each task with the suite green.

## Review Focus

1. **Listener connected and working** → no rebind, never a toggle (a toggle would cut a working listener) (Task 2 test `neverTogglesAConnectedListener`).
2. **Notification access not granted** → no rebind, no toggle; card shows the missing step, not "not receiving" (Task 2 `noAccessNoRebind`, Task 3 `missingAccessIsAStepNotUnbound`).
3. **Home opened while the tile is pulled down** (two triggers within 5 s) → one toggle, not two (Task 2 `oneCheckAtATime`).
4. **Fresh process, Android binds the listener within 5 s** → the card never flashes "not receiving", and no toggle happens (Task 3 `bindsInTimeStaysReady`).
5. **Listener stays unbound all day** → the 100-entry event log isn't flooded by the periodic check (Task 5 `jobRecordsAtMostHourly`).

---

### Task 1: Event log records the unbound gap

**Files:**
- Modify: `app/src/main/java/app/wristline/EventLog.java` (+ `duration`)
- Modify: `app/src/main/java/app/wristline/Prefs.java` (+ `listenerUnboundSince`)
- Modify: `app/src/main/java/app/wristline/RelayListener.java` (`onListenerConnected`, `onListenerDisconnected`)
- Test: `app/src/test/java/app/wristline/EventLogTest.java`, Create `app/src/test/java/app/wristline/ListenerGapTest.java` (Robolectric)

**Interfaces:**
- Produces:
  - `static String EventLog.duration(long ms)`: `"<1m"` below 60 s, `"12m"`, `"2h 14m"`, `"3d 4h"` (two largest units, rounded down).
  - `static long Prefs.listenerUnboundSince(Context)` (0 = not known unbound) / `static void Prefs.setListenerUnboundSince(Context, long)`, key `"listener_unbound_since"`.
  - `onListenerDisconnected`: sets `listenerUnboundSince` to now if it is 0.
  - `onListenerConnected`: if `listenerUnboundSince > 0`, records `LISTENER_CONNECTED` with detail `"after " + duration(now - since) + " unbound"`, then sets it to 0; otherwise records it with empty detail, as today.

- [ ] **Step 1: Write failing tests.**

```java
// EventLogTest
@Test public void durations() {
    assertEquals("<1m", EventLog.duration(59_000));
    assertEquals("12m", EventLog.duration(12 * 60_000 + 30_000));
    assertEquals("2h 14m", EventLog.duration((2 * 60 + 14) * 60_000L));
    assertEquals("3d 4h", EventLog.duration(((3 * 24 + 4) * 60 + 5) * 60_000L));
}
// ListenerGapTest — listener = Robolectric.buildService(RelayListener.class).create().get(); @After listener.onListenerDisconnected()
@Test public void reconnectRecordsHowLongItWasUnbound()  // Prefs.setListenerUnboundSince(now - 2h14m); onListenerConnected() → events[0] code listener_connected, detail "after 2h 14m unbound"; since == 0
@Test public void normalConnectHasNoGap()                // since 0 → detail ""
@Test public void disconnectStartsTheGap()               // connect, disconnect → since > 0; disconnect again keeps the first value
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*EventLogTest' --tests '*ListenerGapTest'` — compile failure.
- [ ] **Step 3: Implement** as in Interfaces (`System.currentTimeMillis()` for now).
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` — PASS.
- [ ] **Step 5: Commit** `feat: log how long the notification listener was unbound`

---

### Task 2: `ListenerRebind`: request, then toggle the component

**Files:**
- Create: `app/src/main/java/app/wristline/ListenerRebind.java`
- Modify: `app/src/main/java/app/wristline/EventLog.java` (+ `LISTENER_TOGGLED = "listener_toggled"`)
- Modify: `app/src/main/java/app/wristline/Diagnostics.java` (delete `rebindListenerIfNeeded`; Javadoc no longer mentions rebinding)
- Modify: `app/src/main/java/app/wristline/MainActivity.java:108` (call `ListenerRebind.ifNeeded(this, ListenerRebind.HOME, null)`)
- Test: Create `app/src/test/java/app/wristline/ListenerRebindTest.java` (Robolectric)

**Interfaces:**
- Consumes: Task 1's `Prefs.setListenerUnboundSince`; `RelayListener.isConnected()`; `Permissions.isDone(ctx, Step.NOTIFICATION_ACCESS)`.
- Produces (final class, static, all work on the main looper with the application context):
  - Triggers: `HOME = "home"`, `QS_TILE = "qs_tile"`, `WATCH = "watch"`, `JOB = "job"`, `FIX = "fix"`; `CHECK_AFTER_MS = 5_000`.
  - `static volatile Runnable onChange`: Home sets it to redraw (same pattern as `Relaying.onChange`).
  - `static void ifNeeded(Context ctx, String trigger, Runnable done)`: if connected, access missing, or a check is already pending → run `done` (if non-null) and return. Otherwise record `LISTENER_REBIND` (detail = trigger), `NotificationListenerService.requestRebind(component)`, and after `CHECK_AFTER_MS`: if still not connected and access still granted → `toggle(ctx, trigger)` and set `listenerUnboundSince` to now if it is 0. Then post `onChange`, run `done`, clear pending.
  - `static void toggle(Context ctx, String trigger)`: `setComponentEnabledSetting(component, COMPONENT_ENABLED_STATE_DISABLED, DONT_KILL_APP)`, then `ENABLED` with `DONT_KILL_APP`, then `requestRebind`; records `LISTENER_TOGGLED` (detail = trigger). Each call goes in its own try/catch `RuntimeException` that logs and records nothing more (diagnostics must never get in the way).
  - `static boolean unbound(Context ctx)`: access granted && `!RelayListener.isConnected()` && `Prefs.listenerUnboundSince(ctx) > 0`. This is the status card's input: it is only true after a failed check or a disconnect, so a fresh process that is still binding does not count.

- [ ] **Step 1: Write failing tests** (grant access with `shadowOf(context.getSystemService(NotificationManager.class)).setNotificationListenerAccessGranted(new ComponentName(context, RelayListener.class), true)`; `wait5s()` = `shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ListenerRebind.CHECK_AFTER_MS))`; `codes()` = event codes newest first; `@After`: `wait5s()` and disconnect any listener the test connected):

```java
@Test public void unboundListenerIsToggledAfterFiveSeconds() // access, not connected → ifNeeded(HOME) → codes [listener_rebind]; wait5s → codes [listener_toggled, listener_rebind]; component state ENABLED; unbound() true
@Test public void bindingInTimeNeedsNoToggle()              // ifNeeded, then listener.onListenerConnected() before wait5s → no listener_toggled; unbound() false
@Test public void neverTogglesAConnectedListener()          // connected → ifNeeded → no events; wait5s → still none
@Test public void noAccessNoRebind()                        // access not granted → ifNeeded → no events; wait5s → none; component state DEFAULT
@Test public void oneCheckAtATime()                         // ifNeeded(HOME); ifNeeded(QS_TILE); wait5s → exactly one listener_toggled, one listener_rebind
@Test public void doneRunsEitherWay()                       // done counter: connected → 1 immediately; unbound → 1 after wait5s
@Test public void homeRedrawsAfterTheCheck()                // onChange counter == 1 after wait5s
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ListenerRebindTest'` — compile failure.
- [ ] **Step 3: Implement** `ListenerRebind` as in Interfaces; delete `Diagnostics.rebindListenerIfNeeded` and switch `MainActivity.onResume` to `ListenerRebind.ifNeeded(this, ListenerRebind.HOME, null)`.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` — PASS.
- [ ] **Step 5: Commit** `feat: toggle the listener component when a rebind request doesn't connect`

---

### Task 3: Status card: "Wristline isn't receiving notifications" + Fix

**Files:**
- Modify: `app/src/main/java/app/wristline/SetupStatus.java` (+ `NOT_RECEIVING`, new `decide` parameter)
- Modify: `app/src/main/java/app/wristline/BatteryHint.java` (+ `withBrandHint`; `description` uses it)
- Modify: `app/src/main/java/app/wristline/MainActivity.java` (`refresh`, `bindStatus`, `onResume`/`onPause` set/clear `ListenerRebind.onChange`)
- Modify: `strings.xml` EN/IT; `README.md` Troubleshooting table; `docs/HOW_TO.md` "If something goes wrong" table
- Test: `app/src/test/java/app/wristline/SetupStatusTest.java`, Create `BatteryHintTextTest.java`, Create `app/src/test/java/app/wristline/ListenerStatusCardTest.java` (Robolectric)

**Interfaces:**
- Consumes: Task 2's `ListenerRebind.unbound`, `ListenerRebind.toggle`, `ListenerRebind.FIX`, `ListenerRebind.onChange`.
- Produces:
  - `static Kind SetupStatus.decide(boolean relayOn, int missingSteps, boolean listenerUnbound, int brokenApps, boolean reachedWatch)`. Order: `PAUSED`, `STEPS_LEFT`, `NOT_RECEIVING`, `NEEDS_REPAIR`, `TRY_IT`, `READY`.
  - `static String BatteryHint.withBrandHint(Context ctx, String text)`: `text` plus `"\n" + hint` for `Brand.of(Build.MANUFACTURER)` when the brand has one.
  - `NOT_RECEIVING` card: amber like `STEPS_LEFT` (`amber_card`, `ic_warning`); title `status_unbound_title`, sub `status_unbound_sub`; attention title `unbound_title`, description `withBrandHint(ctx, getString(unbound_desc))`, button `fix` → `ListenerRebind.toggle(this, FIX)` then sets the field `fixTried = true` and refreshes after `CHECK_AFTER_MS`. If `fixTried` and the card is still `NOT_RECEIVING`, the button is `open_wristline_settings` → `startActivity(Permissions.appInfo(this))` (Xiaomi's Autostart is on that screen). `fixTried` resets when the card leaves `NOT_RECEIVING`.

Copy:

| key | EN | IT |
|---|---|---|
| `status_unbound_title` | `Wristline isn't receiving notifications` | `Wristline non riceve le notifiche` |
| `status_unbound_sub` | `Calls won't ring the watch until this is fixed` | `Le chiamate non squillano sull'orologio finché non è risolto` |
| `unbound_title` | `Android stopped passing calls to Wristline` | `Android ha smesso di passare le chiamate a Wristline` |
| `unbound_desc` | `Notification access is on, but the phone isn't connecting Wristline to it, usually because it closed Wristline in the background. Tap Fix. If it happens again, make sure Wristline's battery use is Unrestricted.` | `L'accesso alle notifiche è attivo, ma il telefono non vi collega Wristline, di solito perché l'ha chiusa in background. Tocca Risolvi. Se succede di nuovo, controlla che l'uso della batteria di Wristline sia Senza restrizioni.` |
| `fix` | `Fix` | `Risolvi` |
| `open_wristline_settings` | `Open Wristline settings` | `Apri impostazioni di Wristline` |

- [ ] **Step 1: Write failing tests.**

```java
// SetupStatusTest: add `false` as the third argument to the existing five, then
@Test public void unboundListenerIsNotReady()   { assertEquals(NOT_RECEIVING, SetupStatus.decide(true, 0, true, 0, true)); }
@Test public void unboundBeforeRepair()         { assertEquals(NOT_RECEIVING, SetupStatus.decide(true, 0, true, 1, true)); }
@Test public void stepsBeforeUnbound()          { assertEquals(STEPS_LEFT, SetupStatus.decide(true, 1, true, 0, true)); }
@Test public void pausedWinsOverUnbound()       { assertEquals(PAUSED, SetupStatus.decide(false, 0, true, 0, true)); }
// BatteryHintTextTest (new Robolectric class; BatteryHintTest stays plain JUnit)
@Test public void brandHintIsAppended()         // ShadowBuild.setManufacturer("Xiaomi") → withBrandHint(ctx, "x") == "x\n" + getString(R.string.battery_hint_xiaomi); "Google" → "x"
// ListenerStatusCardTest: grant all four steps (access as in Task 2; shadowOf(context).grantPermissions(POST_NOTIFICATIONS); ShadowSettings.setCanDrawOverlays(true); shadowOf(PowerManager).setIgnoringBatteryOptimizations("app.wristline", true)); add one call history entry so the card would be READY
@Test public void unboundShowsNotReceiving()        // Prefs.setListenerUnboundSince(1), not connected → status_title == getString(status_unbound_title); attention_button "Fix"
@Test public void xiaomiGetsItsHint()               // ShadowBuild.setManufacturer("Xiaomi") → attention_desc contains getString(battery_hint_xiaomi)
@Test public void fixTogglesNow()                   // click attention_button → codes contain "listener_toggled" with detail "fix" before any idle
@Test public void stillUnboundAfterFixOffersSettings() // click, idleFor CHECK_AFTER_MS → button text "Open Wristline settings"; click → started intent ACTION_APPLICATION_DETAILS_SETTINGS "package:app.wristline"
@Test public void reconnectTurnsTheCardReady()      // unbound card shown; listener.onListenerConnected(); idle → status_title == getString(status_ready_title)
@Test public void bindsInTimeStaysReady()           // since 0, open Home, connect listener within 5 s, idleFor 5 s → never NOT_RECEIVING (title Ready after the open and after the idle)
@Test public void missingAccessIsAStepNotUnbound()  // access not granted, since 1 → title is the steps plural, not status_unbound_title
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SetupStatusTest' --tests '*BatteryHint*' --tests '*ListenerStatusCardTest'` — FAIL.
- [ ] **Step 3: Implement** as in Interfaces. `refresh()` passes `ListenerRebind.unbound(this)`.
- [ ] **Step 4: Docs.** README Troubleshooting, "Watch doesn't ring at all" row: add "If the card says **Wristline isn't receiving notifications**, tap **Fix**. On Xiaomi also turn on Autostart for Wristline." HOW_TO, "It stopped working after a while" row: same sentence at the front.
- [ ] **Step 5: Run** `./gradlew :app:testDebugUnitTest` — PASS.
- [ ] **Step 6: Commit** `feat: status card says when notifications stop reaching Wristline`

---

### Task 4: Rebind from the Quick Settings tile and the watch

**Files:**
- Modify: `app/src/main/java/app/wristline/RelayTileService.java` (`onStartListening`)
- Modify: `app/src/main/java/app/wristline/WatchCallService.java` (`onMessageReceived`, first line)
- Test: `app/src/test/java/app/wristline/RelayingTest.java`, Create `app/src/test/java/app/wristline/WatchRebindTest.java` (Robolectric)

**Interfaces:**
- Consumes: Task 2's `ListenerRebind.ifNeeded`, `QS_TILE`, `WATCH`.
- Produces: `RelayTileService.onStartListening` → `ListenerRebind.ifNeeded(this, QS_TILE, null)` before `update()` (it runs on every pull-down of the shade, so a click is covered). `WatchCallService.onMessageReceived` → `ListenerRebind.ifNeeded(this, WATCH, null)` for every path. The watch tile's Pause/Resume chip arrives here as `PATH_SET_RELAY`, and so do calls from the watch. Behaviour of each path is otherwise unchanged.

- [ ] **Step 1: Write failing tests** (access granted, listener not connected, as in Task 2):

```java
// RelayingTest
@Test public void pullingDownTheShadeRebinds()  // Robolectric.buildService(RelayTileService.class).create().get().onStartListening() → codes contain "listener_rebind" with detail "qs_tile"
// WatchRebindTest — a fake MessageEvent (anonymous class implementing com.google.android.gms.wearable.MessageEvent: path PATH_SET_RELAY, data "0")
@Test public void watchTileChipRebinds()         // service.onMessageReceived(event) → relay paused AND codes contain "listener_rebind" with detail "watch"
@Test public void watchMessageWithListenerUpDoesNothingExtra() // connected → no listener_rebind
```

If `WearableListenerService.onCreate` fails under Robolectric, build the service without `create()` (`Robolectric.buildService(WatchCallService.class).get()`); `onMessageReceived` only needs the attached context.

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*RelayingTest' --tests '*WatchRebindTest'` — FAIL.
- [ ] **Step 3: Implement** the two calls.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` — PASS.
- [ ] **Step 5: Commit** `feat: rebind the listener from the Quick Settings tile and the watch`

---

### Task 5: Periodic check while relaying is on

**Files:**
- Create: `app/src/main/java/app/wristline/ListenerCheckJob.java` (`JobService`)
- Modify: `app/src/main/AndroidManifest.xml` (`<service android:name=".ListenerCheckJob" android:exported="false" android:permission="android.permission.BIND_JOB_SERVICE" />`)
- Modify: `app/src/main/java/app/wristline/Relaying.java` (`set`), `MainActivity.java` (`onResume`), `RelayListener.java` (`onListenerConnected`)
- Modify: `app/src/main/java/app/wristline/ListenerRebind.java` (+ `worthRecording`; `Prefs` + `lastRebindRecorded`)
- Test: Create `app/src/test/java/app/wristline/ListenerCheckJobTest.java` (Robolectric), `ListenerRebindTest.java`

**Interfaces:**
- Consumes: Task 2's `ListenerRebind.ifNeeded(ctx, JOB, done)`.
- Produces:
  - `ListenerCheckJob.JOB_ID = 1`, `INTERVAL_MS = 15 * 60_000L`.
  - `static void ListenerCheckJob.sync(Context)`: when `Prefs.relayOn` → schedule (if not already pending) a periodic job, `setPeriodic(INTERVAL_MS)`, not persisted (no boot permission; `onListenerConnected` re-schedules after a reboot); when paused → `cancel(JOB_ID)`. Called from `Relaying.set` (after saving), `MainActivity.onResume`, `RelayListener.onListenerConnected`.
  - `onStartJob(params)` → `ListenerRebind.ifNeeded(this, ListenerRebind.JOB, () -> jobFinished(params, false))`, returns `true`; `onStopJob` returns `true` (retry).
  - `static boolean ListenerRebind.worthRecording(String trigger, long lastRecordedAt, long now)`: false only when `trigger` is `JOB` and `now - lastRecordedAt < 60 min`. `ifNeeded`/`toggle` skip `Diagnostics.record` (but still act) when it is false; `Prefs.lastRebindRecorded` / `setLastRebindRecorded` (key `"last_rebind_recorded"`) track it.

- [ ] **Step 1: Write failing tests.**

```java
// ListenerRebindTest
@Test public void jobRecordsAtMostHourly() {
    assertTrue(ListenerRebind.worthRecording(ListenerRebind.JOB, 0, 3_600_000));
    assertFalse(ListenerRebind.worthRecording(ListenerRebind.JOB, 1_000, 1_000 + 59 * 60_000));
    assertTrue(ListenerRebind.worthRecording(ListenerRebind.HOME, 1_000, 2_000));
}
// ListenerCheckJobTest — pending jobs via shadowOf(context.getSystemService(JobScheduler.class)).getAllPendingJobs()
@Test public void relayingOnSchedulesTheCheck()   // sync() with relay on → one job, id 1, periodic, interval 15 min, not persisted
@Test public void pausingCancelsTheCheck()        // Relaying.set(ctx, false) → no pending job
@Test public void resumingSchedulesItAgain()      // set false then true → one job
@Test public void connectingSchedulesIt()         // relay on, no job; listener.onListenerConnected() → one job
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*ListenerCheckJobTest' --tests '*ListenerRebindTest'` — FAIL.
- [ ] **Step 3: Implement** as in Interfaces.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` — PASS.
- [ ] **Step 5: Commit** `feat: check the notification listener every 15 minutes while relaying`

---

### Task 6: Full verification and device check

- [ ] **Step 1: Run** `./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest assembleDebug :wear:assembleDebug :app:assembleShrunk` (the CI line) — BUILD SUCCESSFUL. `:app:assembleShrunk` confirms R8 keeps `ListenerCheckJob` (referenced from the manifest).
- [ ] **Step 2: Release build + install** on the owner's phone (keystore and serial as in memory `build-and-install.md`) → `Success`.
- [ ] **Step 3: Reproduce an unbound listener.** Try `adb shell am kill app.wristline`, then `adb shell am force-stop app.wristline`. After each, `adb shell dumpsys notification | grep -i -A2 wristline` shows whether `app.wristline/.RelayListener` is among the live listeners. Use the one that leaves it unbound with access still granted. If neither does on this phone, say so and rely on Step 6.
- [ ] **Step 4: Recovery paths.** From the unbound state, one at a time: pull down Quick Settings; tap the watch tile's chip; open Home. Pass = live listener again within ~5 s; the diagnostic report shows `listener_rebind <trigger>`, `listener_toggled <trigger>` if a toggle was needed, and `listener_connected after … unbound`. Notification access is still granted after a toggle (Settings → Notification access). Then a WhatsApp test call rings the watch.
- [ ] **Step 5: Card.** Unbound with Home open → card says **Wristline isn't receiving notifications**; **Fix** reconnects; card goes **Ready**.
- [ ] **Step 6: Xiaomi.** Build an APK for the field reporter (Xiaomi 2510DPC44G, SDK 37) and ask for a diagnostic report after a few hours in a pocket: expect `listener_toggled job` followed by `listener_connected after … unbound`, and not hours of `listener_rebind` with nothing after. If the toggle does not reconnect on HyperOS, report to the owner before changing anything.
- [ ] **Step 7:** `superpowers:requesting-code-review` on the branch; fix findings; rerun Step 1.

---

### Task 7 (optional): Settings route for Telegram and Signal, only if they pass the spike

Do this only after the spike for that app passes: Phone permission denied in Settings, incoming call answered on the watch with voice on the watch, a call from the watch, hang up from the watch, no prompt from the app. Record the result per app in the spec's Phase 0 section. If neither app passes, skip this task. WhatsApp stays on adb either way (it fails the spike, see Global Constraints).

**Files:**
- Modify: `app/src/main/java/app/wristline/SupportedApp.java` (+ `settingsRouteWorks`, last constructor parameter: `true` only for the apps that passed, `false` for WhatsApp and Instagram)
- Modify: `app/src/main/java/app/wristline/Permissions.java` (+ `appInfo(String packageName)`; `appInfo(Context)` delegates)
- Modify: `app/src/main/res/layout/activity_voice.xml` (+ `settings_route` card above the computer card: `settings_route_steps` TextView, `settings_route_buttons` LinearLayout)
- Modify: `app/src/main/java/app/wristline/VoiceActivity.java` (`refresh`)
- Modify: `strings.xml` EN/IT; `README.md` / `docs/HOW_TO.md` voice section (one paragraph: "For Telegram/Signal you can instead …")
- Test: `SupportedAppTest.java`, Create `app/src/test/java/app/wristline/VoiceSettingsRouteTest.java` (Robolectric)

**Interfaces:**
- Produces:
  - `SupportedApp.settingsRouteWorks`: "Denying the app's Phone permission in Settings gives voice through the watch (spike passed on a real call)."
  - `static Intent Permissions.appInfo(String packageName)` → `ACTION_APPLICATION_DETAILS_SETTINGS`, `Uri.parse("package:" + packageName)`.
  - `static String VoiceActivity.settingsTag(SupportedApp)` = `"settings:" + packageName`. One `Button.Primary` per relayed app in `SET_UP` (or in `NEEDS_REPAIR` with `needsAppOp`) that has `settingsRouteWorks`, text `open_app_settings`. Click → `startActivity(Permissions.appInfo(pkg))`, falling back to `Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS` on `RuntimeException`. The card is GONE when no app qualifies. The `ComputerSetup` card and its button stay exactly as today for every app.
  - No state change: `VoiceState.of` already returns `ON` when `!needsAppOp` and not broken, and `onResume` already calls `refresh()`.

Copy: `settings_route_steps` EN `For %1$s you don't need a computer: open the app's settings, then Permissions → Phone → Don't allow. Come back here and it shows On.` / IT `Per %1$s non serve un computer: apri le impostazioni dell'app, poi Autorizzazioni → Telefono → Non consentire. Torna qui e vedrai Attiva.`; `open_app_settings` EN `Open %1$s settings` / IT `Apri impostazioni di %1$s`. Check the IT labels against an Italian phone.

- [ ] **Step 1: Write failing tests** (written for an app that passed, here Telegram; swap in Signal if only Signal passed):

```java
// SupportedAppTest
@Test public void settingsRouteOnlyWhereTheSpikePassed() // assertFalse(WHATSAPP.settingsRouteWorks); assertFalse(INSTAGRAM.settingsRouteWorks); assertTrue(TELEGRAM.settingsRouteWorks)
// VoiceSettingsRouteTest — WA + TG installed with READ_PHONE_STATE granted, both relayed
@Test public void passedAppGetsASettingsButton()   // findViewWithTag(settingsTag(TG)) != null, text "Open Telegram settings"; tag for WA is null
@Test public void buttonOpensThatAppsSettings()    // click → next started intent ACTION_APPLICATION_DETAILS_SETTINGS, data "package:org.telegram.messenger"
@Test public void whatsappStillUsesTheComputer()   // set_up button text "Set up WhatsApp, Telegram" (ComputerSetup unchanged)
@Test public void comingBackWithPhoneDeniedShowsOn() // controller.pause().stop(); reinstall TG with flags {0}; restart().start().resume() → TG row pill "On", TG settings button gone
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SupportedAppTest' --tests '*VoiceSettingsRouteTest'` — FAIL.
- [ ] **Step 3: Implement** as in Interfaces.
- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest` — PASS.
- [ ] **Step 5: Device check:** deny Phone for that app via the new button, come back → On; test call through the watch.
- [ ] **Step 6: Commit** `feat: Settings route for voice through the watch on <apps>`
