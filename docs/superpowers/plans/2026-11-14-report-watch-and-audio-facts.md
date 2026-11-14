# Diagnostic Report: Watch, Companion and Audio Facts — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the shared diagnostic report answer, by itself, the questions the 2026-11-13 field report (POCO F8 Pro + OPPO Watch X3) left open: which watch and companion app are in play, whether the companion's call bridge sees self-managed calls, whether call audio ever had a Bluetooth route, what Telecom told the connection, and what started each outgoing call.

**Architecture:** Three sources feed the existing `DiagnosticReport.Facts`: (1) phone-side package queries (InCallService inventory, known companion apps, default dialer); (2) a new `/wristline/watchinfo` data item the watch app publishes with its `Build` facts, read at report time; (3) new event-log entries recorded by `RelayConnection` (Telecom state, audio route) and `OutgoingCalls` (every app start, with its trigger). Nothing personal enters the report: package names, versions, `Build` fields, Telecom constants and counts only.

**Tech Stack:** Java 21, Android SDK, Telecom `Connection`/`CallAudioState`, Wear OS Data Layer (`DataClient`), JUnit 4 + Robolectric 4.17 (`./gradlew :app:testDebugUnitTest`, `./gradlew :wear:testDebugUnitTest`).

**Spec:** none as a separate document. Requirements come from the 2026-11-14 analysis of the field report (this plan's "Requirements" section). Analysis summary: companion delivered Decline but never Answer to Wristline's Telecom call; full-mode outgoing call never routed audio to the watch; a WhatsApp call started with no visible trigger and no `outgoing` event; HyperOS unbound the listener within minutes.

## Requirements

R1. The report names the watch: manufacturer, model, Android SDK, Wristline watch-app version — when the watch app is installed. Otherwise it says the watch app isn't reporting. It also gives the number of connected Wear nodes, never their display names.
R2. The report lists every installed `InCallService` (package, version, whether it declares `android.telecom.INCLUDE_SELF_MANAGED_CALLS`, whether it declares `android.telecom.IN_CALL_SERVICE_UI`) and the default dialer package.
R3. The report lists which known watch-companion apps are installed, with versions.
R4. The report's phone line gains brand, Android release and build display, and the phone's locale tag (button-label matching depends on it).
R5. The report says whether a Bluetooth call-audio output device (`TYPE_BLUETOOTH_SCO` or `TYPE_BLE_HEADSET`) exists at report time. No device names.
R6. Each change of Telecom audio state on Wristline's connection is logged once (`audio_route`, detail `route=<x> supported=<a,b,c>`); when `routeToWatch` finds no Bluetooth route while active, that is logged once per connection (`audio_no_watch`).
R7. Each Telecom state change on Wristline's connection is logged (`call_state`, detail = `Connection.stateToString`).
R8. Every start of a calling app's call by Wristline is logged (`app_started`, detail = trigger: `watch`, `call back`), including when the notification listener is unbound (`outgoing_unmirrored`, detail `listener off`).
R9. Privacy: no caller names, numbers, contact ids, Bluetooth or Wear node names, account identifiers. Only the vocabularies above. `EventLog`'s class comment and `DiagnosticReport`'s header line stay true.

## Global Constraints

- Java 21; no new third-party libraries ([[in-house-no-shizuku]] rule: no third-party app or library dependencies beyond what's already in `app/build.gradle.kts`).
- No new runtime permissions. `BLUETOOTH_CONNECT` is explicitly out: `AudioManager.getDevices` needs none.
- Package visibility: Android 11+ requires `<queries>` entries for every package or intent queried. Add them in `app/src/main/AndroidManifest.xml`; keep the existing `<queries>` block.
- Report text is English, lower-case event codes with underscores, as the existing codes.
- Keep `WatchProtocol` (phone) and `Protocol` (watch) in sync, as their class comments require.
- Diagnostics must never throw into a call path: wrap every new collector in try/catch and degrade to "unknown".

## Review Focus

1. A companion that declares `INCLUDE_SELF_MANAGED_CALLS` with `android:value="false"` must read as `self-managed no`, not `yes` (meta-data presence ≠ true). Test in Task 2.
2. `onCallAudioStateChanged` fires repeatedly with identical state during a call; the log must not fill its 100-entry cap with duplicates. Test in Task 5.
3. A phone with no watch app installed and no Wear nodes (Play services missing) must still produce a report, with "not reporting" and "connected watches: unknown". Test in Task 3 (null/exception paths).
4. `OutgoingCalls.start` with the listener unbound still logs `app_started` and `outgoing_unmirrored` — the exact blind spot from the field report. Test in Task 6.
5. Many `InCallService` entries (OEM dialer, Bluetooth, companion, Android Auto) must not push the events section out of the share sheet's practical length: cap the list at 12 entries with a trailing "… and N more". Test in Task 2.

---

### Task 1: Phone facts and Bluetooth call-audio device (R4, R5)

**Files:**
- Modify: `app/src/main/java/app/wristline/DiagnosticReport.java` (`Facts`, `build`)
- Modify: `app/src/main/java/app/wristline/Diagnostics.java:33-53` (`collect`)
- Test: `app/src/test/java/app/wristline/DiagnosticReportTest.java`, `app/src/test/java/app/wristline/DiagnosticsCollectTest.java` (create, Robolectric)

**Interfaces:**
- Produces on `Facts`: `String brand`, `String release`, `String display`, `String locale`, `Boolean bluetoothCallAudio` (null = unknown).
- Produces: `static Boolean Diagnostics.bluetoothCallAudio(AudioManager)` — true when any output device of type `AudioDeviceInfo.TYPE_BLUETOOTH_SCO` or `TYPE_BLE_HEADSET` exists; null on exception.

- [ ] **Step 1: Write the failing tests**

In `DiagnosticReportTest`, extend `facts()` with `brand="Acme"`, `release="17"`, `display="ACME.1"`, `locale="it-IT"`, `bluetoothCallAudio=true`, and add:

```java
@Test
public void phoneLineNamesBrandReleaseAndLocale() {
    String report = DiagnosticReport.build(facts());
    assertTrue(report.contains("Device: Acme Phone 1 (Acme), Android 17 (ACME.1), SDK 36, patch 2026-01-01, locale it-IT\n"));
    assertTrue(report.contains("Bluetooth call audio device: yes\n"));
}

@Test
public void unknownBluetoothSaysUnknown() {
    DiagnosticReport.Facts f = facts();
    f.bluetoothCallAudio = null;
    assertTrue(DiagnosticReport.build(f).contains("Bluetooth call audio device: unknown\n"));
}
```

Create `DiagnosticsCollectTest` (`@RunWith(RobolectricTestRunner.class)`):

```java
@Test
public void bluetoothCallAudioSeesScoAndBle() {
    AudioManager am = ApplicationProvider.getApplicationContext().getSystemService(AudioManager.class);
    shadowOf(am).addOutputDevice(AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO).build(), true);
    assertEquals(Boolean.TRUE, Diagnostics.bluetoothCallAudio(am));
}

@Test
public void bluetoothCallAudioFalseWithoutHeadset() {
    AudioManager am = ApplicationProvider.getApplicationContext().getSystemService(AudioManager.class);
    assertEquals(Boolean.FALSE, Diagnostics.bluetoothCallAudio(am));
}
```

(`org.robolectric.shadows.AudioDeviceInfoBuilder` and `ShadowAudioManager.addOutputDevice` exist in Robolectric 4.17. If `addOutputDevice` isn't available on the shadow in this version, use `addOutputDeviceWithDirectProfiles`; the assertion stays.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'app.wristline.DiagnosticReportTest' --tests 'app.wristline.DiagnosticsCollectTest'`
Expected: compilation failure on the new `Facts` fields and `Diagnostics.bluetoothCallAudio`.

- [ ] **Step 3: Implement**

`Facts`: add the five fields (defaults `""` / `null`). `build`: replace the Device line with the format above; after the `adb key present` line add `Bluetooth call audio device: yes|no|unknown`. `collect`: fill from `Build.BRAND`, `Build.VERSION.RELEASE`, `Build.DISPLAY`, `Locale.getDefault().toLanguageTag()`, and `bluetoothCallAudio(context.getSystemService(AudioManager.class))`. Implement `bluetoothCallAudio` over `AudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)`; catch `RuntimeException` → null.

- [ ] **Step 4: Run the whole app test suite**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/wristline/DiagnosticReport.java app/src/main/java/app/wristline/Diagnostics.java app/src/test/java/app/wristline/DiagnosticReportTest.java app/src/test/java/app/wristline/DiagnosticsCollectTest.java
git commit -m "Report brand, Android release, locale and Bluetooth call-audio device"
```

---

### Task 2: Call bridges and companion apps (R2, R3)

**Files:**
- Create: `app/src/main/java/app/wristline/CallBridges.java`
- Modify: `app/src/main/AndroidManifest.xml:25-30` (`<queries>`)
- Modify: `app/src/main/java/app/wristline/DiagnosticReport.java`, `Diagnostics.java` (`collect`)
- Test: `app/src/test/java/app/wristline/CallBridgesTest.java` (create, Robolectric), `DiagnosticReportTest.java`

**Interfaces:**
- Produces: `final class CallBridges` with
  - `static final class Bridge { final String packageName; final String version; final boolean selfManaged; final boolean ui; }`
  - `static List<Bridge> inCallServices(PackageManager pm)` — one entry per package offering `android.telecom.InCallService`, sorted by package name, de-duplicated per package (`selfManaged`/`ui` true if any of its services declares it true).
  - `static String defaultDialer(Context context)` — `TelecomManager.getDefaultDialerPackage()`, `"unknown"` on exception/null.
  - `static final String[] COMPANIONS` — `com.google.android.apps.wear.companion` (Pixel Watch), `com.google.android.wearable.app` (Wear OS by Google), `com.heytap.health.international`, `com.heytap.health` (OHealth), `com.samsung.android.app.watchmanager` (Galaxy Wearable), `com.xiaomi.wearable` (Mi Fitness), `com.huawei.health`, `com.mobvoi.companion.aw`.
  - `static List<String> installedCompanions(PackageManager pm)` — `"<package> <version>"` for each installed one.
  - `static final int MAX_LISTED = 12`.
- Produces on `Facts`: `List<CallBridges.Bridge> bridges`, `String defaultDialer`, `List<String> companions`.

- [ ] **Step 1: Write the failing tests**

`CallBridgesTest`:

```java
@Test
public void readsSelfManagedFlagAsBoolean() {
    // register two services via shadowOf(pm).addResolveInfoForIntent(new Intent("android.telecom.InCallService"), ...)
    // "com.example.bridge" with metaData INCLUDE_SELF_MANAGED_CALLS=true, IN_CALL_SERVICE_UI absent
    // "com.example.ohealth" with metaData INCLUDE_SELF_MANAGED_CALLS=false, IN_CALL_SERVICE_UI=true
    List<CallBridges.Bridge> bridges = CallBridges.inCallServices(pm);
    assertEquals(2, bridges.size());
    assertEquals("com.example.bridge", bridges.get(0).packageName);
    assertTrue(bridges.get(0).selfManaged);
    assertFalse(bridges.get(0).ui);
    assertFalse(bridges.get(1).selfManaged);
    assertTrue(bridges.get(1).ui);
}

@Test
public void onePackageWithTwoServicesIsListedOnce() { /* same package twice, one selfManaged=true → size 1, selfManaged true */ }

@Test
public void listsOnlyInstalledCompanions() {
    // install com.heytap.health.international versionName "4.2.0" via shadowOf(pm).installPackage
    assertEquals(List.of("com.heytap.health.international 4.2.0"), CallBridges.installedCompanions(pm));
}
```

`DiagnosticReportTest`:

```java
@Test
public void listsCallBridgesAndCompanions() {
    DiagnosticReport.Facts f = facts();
    f.defaultDialer = "com.android.dialer";
    f.bridges = List.of(new CallBridges.Bridge("com.example.bridge", "2.0", true, false));
    f.companions = List.of("com.heytap.health.international 4.2.0");
    String report = DiagnosticReport.build(f);
    assertTrue(report.contains("Default dialer: com.android.dialer\n"));
    assertTrue(report.contains("Call bridges (InCallService):\n- com.example.bridge 2.0, self-managed yes, ui no\n"));
    assertTrue(report.contains("Watch companion apps:\n- com.heytap.health.international 4.2.0\n"));
}

@Test
public void capsLongBridgeLists() {
    // 14 bridges → 12 lines then "- … and 2 more\n"
}

@Test
public void noBridgesOrCompanionsSaysNone() {
    // empty lists → "Call bridges (InCallService): none\n" and "Watch companion apps: none\n"
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'app.wristline.CallBridgesTest' --tests 'app.wristline.DiagnosticReportTest'`
Expected: compilation failure (`CallBridges` missing).

- [ ] **Step 3: Implement**

`CallBridges.inCallServices`: `pm.queryIntentServices(new Intent("android.telecom.InCallService"), PackageManager.GET_META_DATA)`; read `serviceInfo.metaData.getBoolean("android.telecom.INCLUDE_SELF_MANAGED_CALLS", false)` and `getBoolean("android.telecom.IN_CALL_SERVICE_UI", false)` (null metaData → false); version via `pm.getPackageInfo(pkg, 0).versionName` (`"?"` when null). Catch `RuntimeException` → empty list.

Manifest `<queries>`: add `<intent><action android:name="android.telecom.InCallService" /></intent>` and one `<package android:name=…/>` per `COMPANIONS` entry.

`Facts`/`build`: the three sections in the order Default dialer → Call bridges → Watch companion apps, placed after the `Apps:` block and before `Recent events`. `collect` fills them.

- [ ] **Step 4: Run the whole app test suite**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/app/wristline/CallBridges.java app/src/main/java/app/wristline/DiagnosticReport.java app/src/main/java/app/wristline/Diagnostics.java app/src/test/java/app/wristline/CallBridgesTest.java app/src/test/java/app/wristline/DiagnosticReportTest.java
git commit -m "Report the phone's call bridges, default dialer and watch companion apps"
```

---

### Task 3: Watch facts over the Data Layer (R1)

**Files:**
- Modify: `wear/src/main/java/app/wristline/wear/Protocol.java`, `app/src/main/java/app/wristline/WatchProtocol.java` (new path + keys, identical on both sides)
- Create: `wear/src/main/java/app/wristline/wear/WatchInfo.java`
- Modify: `wear/src/main/java/app/wristline/wear/MainActivity.java:66` (`onCreate`), `wear/src/main/java/app/wristline/wear/RecentTileService.java:26` (`onTileRequest`)
- Modify: `app/src/main/java/app/wristline/WatchSync.java` (read side), `DiagnosticReport.java`, `Diagnostics.java`
- Test: `wear/src/test/java/app/wristline/wear/WatchInfoTest.java` (create), `app/src/test/java/app/wristline/DiagnosticReportTest.java`

**Interfaces:**
- Protocol (both sides): `PATH_WATCH_INFO = "/wristline/watchinfo"`, `KEY_MANUFACTURER = "manufacturer"`, `KEY_MODEL = "model"`, `KEY_SDK = "sdk"`, `KEY_VERSION = "version"`.
- Watch: `static void WatchInfo.publish(Context)` — off the main thread, `PutDataMapRequest.create(PATH_WATCH_INFO)` with `Build.MANUFACTURER`, `Build.MODEL`, `Build.VERSION.SDK_INT`, the watch app's `versionName`; `setUrgent()`; swallow exceptions with a `Log.w(PhoneLink.TAG, …)`. `static DataMap WatchInfo.dataMap(String manufacturer, String model, int sdk, String version)` — pure builder, tested.
- Phone: `static final class WatchSync.WatchFacts { final String manufacturer, model, version; final int sdk; WatchFacts(String manufacturer, String model, int sdk, String version) }`; `static WatchFacts WatchSync.watchFacts(Context)` — blocking read of the data item (`Tasks.await`, 5 s), null when absent/failing; `static Integer WatchSync.connectedNodeCount(Context)` — count only, null on failure.
- `Facts`: `WatchSync.WatchFacts watch` (nullable), `Integer connectedWatches` (nullable).

- [ ] **Step 1: Write the failing tests**

`WatchInfoTest` (wear, Robolectric as the other wear tests):

```java
@Test
public void dataMapCarriesOnlyBuildFacts() {
    DataMap map = WatchInfo.dataMap("OPPO", "OPPO Watch X3", 35, "1.4");
    assertEquals("OPPO", map.getString(Protocol.KEY_MANUFACTURER));
    assertEquals("OPPO Watch X3", map.getString(Protocol.KEY_MODEL));
    assertEquals(35, map.getInt(Protocol.KEY_SDK));
    assertEquals("1.4", map.getString(Protocol.KEY_VERSION));
    assertEquals(4, map.keySet().size());
}
```

`DiagnosticReportTest`:

```java
@Test
public void namesTheWatchWhenItReports() {
    DiagnosticReport.Facts f = facts();
    f.watch = new WatchSync.WatchFacts("OPPO", "OPPO Watch X3", 35, "1.4");
    f.connectedWatches = 1;
    assertTrue(DiagnosticReport.build(f).contains("Watch: OPPO OPPO Watch X3, SDK 35, Wristline watch app 1.4 · connected watches: 1\n"));
}

@Test
public void watchUnknownWhenNothingReports() {
    DiagnosticReport.Facts f = facts();  // watch null, connectedWatches null
    assertTrue(DiagnosticReport.build(f).contains("Watch: watch app not reporting · connected watches: unknown\n"));
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :wear:testDebugUnitTest --tests 'app.wristline.wear.WatchInfoTest' :app:testDebugUnitTest --tests 'app.wristline.DiagnosticReportTest'`
Expected: compilation failures (`WatchInfo`, `WatchFacts`, new fields).

- [ ] **Step 3: Implement**

Watch: `WatchInfo.publish(this)` as the last line of `MainActivity.onCreate` and the first line of `RecentTileService.onTileRequest`. Phone: `watchFacts` reads `Wearable.getDataClient(context).getDataItems(uri)` for `wear:` scheme + `PATH_WATCH_INFO` as `PhoneLink.relayOn` does on the watch side; `connectedNodeCount` is `getConnectedNodes().size()`. `collect` fills both (these block up to 5 s each; `collect` already runs where `shareIntent` is built — confirm it's off the main thread in `MainActivity`; if it isn't, move the share-sheet build to a background thread with the result posted back). Watch line goes right after the Device line.

- [ ] **Step 4: Run both test suites**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest :wear:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add wear/src/main/java/app/wristline/wear/Protocol.java wear/src/main/java/app/wristline/wear/WatchInfo.java wear/src/main/java/app/wristline/wear/MainActivity.java wear/src/main/java/app/wristline/wear/RecentTileService.java wear/src/test/java/app/wristline/wear/WatchInfoTest.java app/src/main/java/app/wristline/WatchProtocol.java app/src/main/java/app/wristline/WatchSync.java app/src/main/java/app/wristline/DiagnosticReport.java app/src/main/java/app/wristline/Diagnostics.java app/src/test/java/app/wristline/DiagnosticReportTest.java
git commit -m "Report the watch's model and app version over the Data Layer"
```

---

### Task 4: Telecom state events on the connection (R7)

**Files:**
- Modify: `app/src/main/java/app/wristline/EventLog.java` (codes), `app/src/main/java/app/wristline/RelayConnection.java`
- Test: `app/src/test/java/app/wristline/CallDiagnosticsTest.java`

**Interfaces:**
- Produces: `EventLog.CALL_STATE = "call_state"`.
- `RelayConnection.onStateChanged(int state)` override → `Diagnostics.record(context, EventLog.CALL_STATE, RelayListener.currentApp, Connection.stateToString(state).toLowerCase(Locale.ROOT))`.

- [ ] **Step 1: Write the failing test**

In `CallDiagnosticsTest` (follow the existing pattern at lines 103–130 for constructing a `RelayConnection` and reading `Prefs.events`):

```java
@Test
public void telecomStateChangesAreRecorded() {
    RelayConnection connection = new RelayConnection(context);
    connection.setRinging();
    connection.setActive();
    List<EventLog.Entry> events = Prefs.events(context);  // newest first
    assertEquals("call_state", events.get(0).code);
    assertEquals("active", events.get(0).detail);
    assertEquals("ringing", events.get(1).detail);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'app.wristline.CallDiagnosticsTest'`
Expected: FAIL — no `call_state` entries.

- [ ] **Step 3: Implement** the override and the constant.

- [ ] **Step 4: Run the app test suite** — expected BUILD SUCCESSFUL. If `TestCallFlowTest` or `CallDiagnosticsTest` assert on `events.get(0)` for other codes and now see `call_state` first, adjust those assertions to search by code rather than position; don't drop the new event.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/wristline/EventLog.java app/src/main/java/app/wristline/RelayConnection.java app/src/test/java/app/wristline/CallDiagnosticsTest.java
git commit -m "Record Telecom state changes of the mirrored call"
```

---

### Task 5: Audio route events (R6)

**Files:**
- Create: `app/src/main/java/app/wristline/AudioRoute.java`
- Modify: `app/src/main/java/app/wristline/EventLog.java`, `app/src/main/java/app/wristline/RelayConnection.java:260-283`
- Test: `app/src/test/java/app/wristline/AudioRouteTest.java` (create), `app/src/test/java/app/wristline/CallDiagnosticsTest.java`

**Interfaces:**
- Produces: `EventLog.AUDIO_ROUTE = "audio_route"`, `EventLog.AUDIO_NO_WATCH = "audio_no_watch"`.
- `static String AudioRoute.describe(int route, int supportedMask)` → `"route=<name> supported=<name,name,…>"`, names lower-case from `CallAudioState.audioRouteToString` (e.g. `earpiece`, `bluetooth`, `wired_headset`, `speaker`, `streaming`), supported names in ascending bit order, `none` when the mask is 0.
- `RelayConnection`: private `String lastRouteDetail`; private `boolean noWatchLogged`.

- [ ] **Step 1: Write the failing tests**

`AudioRouteTest`:

```java
@Test
public void describesRouteAndSupportedMask() {
    assertEquals("route=earpiece supported=earpiece,bluetooth,speaker",
        AudioRoute.describe(CallAudioState.ROUTE_EARPIECE,
            CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_BLUETOOTH | CallAudioState.ROUTE_SPEAKER));
    assertEquals("route=speaker supported=none", AudioRoute.describe(CallAudioState.ROUTE_SPEAKER, 0));
}
```

`CallDiagnosticsTest`:

```java
@Test
public void audioStateIsRecordedOncePerChange() {
    RelayConnection connection = new RelayConnection(context);
    CallAudioState state = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
    connection.onCallAudioStateChanged(state);
    connection.onCallAudioStateChanged(state);
    connection.onCallAudioStateChanged(new CallAudioState(false, CallAudioState.ROUTE_SPEAKER, CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER));
    List<EventLog.Entry> routes = eventsWithCode(EventLog.AUDIO_ROUTE);  // helper over Prefs.events(context)
    assertEquals(2, routes.size());
    assertEquals("route=speaker supported=earpiece,speaker", routes.get(0).detail);
}

@Test
public void missingBluetoothRouteWhileActiveIsRecordedOnce() {
    RelayConnection connection = new RelayConnection(context);
    connection.setActive();
    CallAudioState noBt = new CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE | CallAudioState.ROUTE_SPEAKER);
    connection.onCallAudioStateChanged(noBt);
    connection.onCallAudioStateChanged(noBt);
    assertEquals(1, eventsWithCode(EventLog.AUDIO_NO_WATCH).size());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'app.wristline.AudioRouteTest' --tests 'app.wristline.CallDiagnosticsTest'`
Expected: compilation failure (`AudioRoute`, constants).

- [ ] **Step 3: Implement**

`onCallAudioStateChanged`: compute `AudioRoute.describe(state.getRoute(), state.getSupportedRouteMask())`; record `AUDIO_ROUTE` only when it differs from `lastRouteDetail`; then call `routeToWatch(state)` as today. In `routeToWatch`, in the branch where `isActive()`, `state != null` and the mask lacks `ROUTE_BLUETOOTH`: record `AUDIO_NO_WATCH` with detail `"supported=<names>"` once (`noWatchLogged`). `voiceOnPhone` early return stays before both.

- [ ] **Step 4: Run the app test suite** — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/wristline/AudioRoute.java app/src/main/java/app/wristline/EventLog.java app/src/main/java/app/wristline/RelayConnection.java app/src/test/java/app/wristline/AudioRouteTest.java app/src/test/java/app/wristline/CallDiagnosticsTest.java
git commit -m "Record the call's audio route and a missing Bluetooth route"
```

---

### Task 6: Every app start is logged with its trigger (R8)

**Files:**
- Modify: `app/src/main/java/app/wristline/EventLog.java`, `app/src/main/java/app/wristline/OutgoingCalls.java:15-43`, `app/src/main/java/app/wristline/WatchCallService.java:38`, `app/src/main/java/app/wristline/MissedCalls.java:115`, `app/src/main/java/app/wristline/RelayListener.java:289-294`
- Test: `app/src/test/java/app/wristline/OutgoingCallsTest.java`

**Interfaces:**
- Produces: `EventLog.APP_STARTED = "app_started"`, `EventLog.OUTGOING_UNMIRRORED = "outgoing_unmirrored"`.
- `OutgoingCalls.start(Context context, long contactId, SupportedApp app, String trigger)` — `trigger` is `OutgoingCalls.TRIGGER_WATCH = "watch"` or `OutgoingCalls.TRIGGER_CALL_BACK = "call back"`. Records `APP_STARTED` (app, trigger) immediately before each `startApp` call that runs (both the mirrored runnable and the phone-only fallback). The old 3-argument overload is removed; both callers pass their trigger.
- `RelayListener.startOutgoing` records `OUTGOING_UNMIRRORED` with detail `"listener off"` where it logs "notification access is off".

- [ ] **Step 1: Write the failing tests**

Read `OutgoingCallsTest` first and reuse its fakes for `ContactsReader`/`startApp`. Add:

```java
@Test
public void phoneOnlyStartIsStillRecorded() {
    // listener unbound (RelayListener.instance null in tests) → start(..., TRIGGER_WATCH)
    List<EventLog.Entry> events = Prefs.events(context);
    assertTrue(hasCode(events, EventLog.OUTGOING_UNMIRRORED));
    EventLog.Entry started = firstWithCode(events, EventLog.APP_STARTED);
    assertEquals("com.whatsapp", started.app);
    assertEquals("watch", started.detail);
}
```

If `OutgoingCallsTest` can't reach `startApp` without a resolvable contact, add a package-private seam `OutgoingCalls.starter` (interface `boolean start(Context, Intent, SupportedApp)`) defaulting to the current `startApp`, and swap it in the test — same pattern as `FullMode.adb`.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest --tests 'app.wristline.OutgoingCallsTest'`
Expected: compilation failure on the 4-argument `start`.

- [ ] **Step 3: Implement** the constants, the parameter, the two records, and update both callers (`WatchCallService`: `TRIGGER_WATCH`; `MissedCalls.CallBackReceiver`: `TRIGGER_CALL_BACK`).

- [ ] **Step 4: Run the app test suite** — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/wristline/EventLog.java app/src/main/java/app/wristline/OutgoingCalls.java app/src/main/java/app/wristline/WatchCallService.java app/src/main/java/app/wristline/MissedCalls.java app/src/main/java/app/wristline/RelayListener.java app/src/test/java/app/wristline/OutgoingCallsTest.java
git commit -m "Record every app call Wristline starts, with what triggered it"
```

---

### Task 7: Privacy guard and docs (R9)

**Files:**
- Modify: `app/src/main/java/app/wristline/EventLog.java` (class comment lists the new codes' vocabularies), `README.md` ("Share diagnostic report" bullet), `docs/HOW_TO.md` (same bullet, if present)
- Test: `app/src/test/java/app/wristline/DiagnosticReportTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
public void reportHoldsNoPersonalFields() {
    DiagnosticReport.Facts f = facts();
    f.watch = new WatchSync.WatchFacts("OPPO", "OPPO Watch X3", 35, "1.4");
    f.bridges = List.of(new CallBridges.Bridge("com.example.bridge", "2.0", true, false));
    f.events = List.of(
        new EventLog.Entry(0, EventLog.AUDIO_ROUTE, "", "route=earpiece supported=earpiece,bluetooth"),
        new EventLog.Entry(0, EventLog.APP_STARTED, "com.whatsapp", "watch"));
    String report = DiagnosticReport.build(f);
    // Every line is one of the fixed headings, a "- <package> …" line, or an event line whose detail
    // is drawn from the fixed vocabularies; no field of Facts carries a name, number or node id.
    for (String line : report.split("\n")) {
        assertFalse(line, line.matches(".*\\+?\\d{6,}.*"));          // no phone-number-length digit runs outside timestamps…
    }
}
```

Adjust the regex so the timestamp prefix (`yyyy-MM-dd HH:mm:ss`) is stripped before the digit check; versions like `2.26.45.12` pass because digit runs are split by dots. The point of the test is to fail if a future field smuggles a number in.

- [ ] **Step 2: Run test to verify it fails or passes**

Run the `DiagnosticReportTest` class. Expected: PASS once the regex is correct (this test is a guard; if it fails, fix the test's timestamp handling, not the report).

- [ ] **Step 3: Update docs**

`EventLog` class comment: add one sentence naming the new codes and that details are Telecom constants and trigger words only. README/HOW_TO "Share diagnostic report" bullet: add "the watch's model and app version, the phone's call-bridge apps" to what the report shows; keep the "no caller names or numbers" wording.

- [ ] **Step 4: Run both test suites** — expected BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/wristline/EventLog.java README.md docs/HOW_TO.md app/src/test/java/app/wristline/DiagnosticReportTest.java
git commit -m "Guard the report against personal data; document the new facts"
```

---

## Out of scope (separate plans)

- HyperOS/Xiaomi setup-card hint (Autostart, background pop-ups).
- A companion-independent ring/answer path through Wristline's own watch app ([[all-wear-os-watches]]).
- Any change to how calls are answered, routed or started: this plan only observes.

## Device check after merge

Build and install on the owner's phone and watch ([[build-and-install]]), run **Ring my watch**, answer on the watch, then **Share diagnostic report** and confirm: a `Watch:` line with the Pixel Watch model; `com.google.android.apps.wear.companion` under Call bridges with `self-managed yes`; `call_state ringing` → `call_state active`; one `audio_route … bluetooth` line; no personal data. Then send the field APK to the POCO/OPPO reporter.
