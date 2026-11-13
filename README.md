# Wristline

Take WhatsApp, Telegram, Instagram and Signal calls on your Wear OS watch: ring, answer, decline, hang up, and optionally talk through the watch.

Wristline is a small Android app for the **phone**. It exists because these apps don't ring on the watch on their own. WhatsApp's watch app even stops the phone from forwarding call notifications while its servers never send the calls to the watch, and the Pixel Watch app drops Instagram's call notifications. So calls ring nowhere on the watch.

**New here? Follow the [step-by-step guide](docs/HOW_TO.md).** It covers installing, setup, everyday use and what to do if something goes wrong.

## What it can do

**When someone calls you**

- Your watch rings like a normal phone call, showing the app and who's calling.
- Answer, decline or hang up from your wrist.
- Talk through the watch's own speaker and microphone, without touching your phone. This needs a one-time setup, on the phone or with a computer.
- Move the voice between the watch and the phone during a call.
- See missed calls on the watch, with **Call back** for contacts.

**When you want to call someone** (needs the watch app and a one-time install with a computer)

- Search your contacts on the watch, or tap **WhatsApp**, **Telegram** or **Signal** on its home screen to see just the people you can reach there, A to Z.
- Tap a name to call them, and choose the app if they're on both.
- Keep your favourite people one tap away, and call back from your latest calls, each marked with the app it came from (W, T, S).
- Call with one tap from a tile or a watch-face complication.
- Use the call screen on the watch: mute when the app allows it, change the volume with the crown, hang up, or minimise it.

**Your rules**

- Choose which apps ring the watch: WhatsApp, Telegram, Instagram and Signal.
- Pause it any time, from the app, Quick Settings or the watch tile.
- Follow your phone's Do Not Disturb.
- Stop the watch ringing while you're using the phone.

Instagram can't start calls from outside its own app, so calling from the watch works with WhatsApp, Telegram and Signal.

## What you get

| | Voice on the phone | Voice through the watch |
|---|---|---|
| Watch rings like a normal phone call, showing "WhatsApp · Annaly" | ✅ | ✅ |
| Answer / decline from the watch | ✅ | ✅ |
| Hang up from the watch | ✅ (call-controls card) | ✅ (call screen) |
| Talk through the watch speaker and mic | ❌ phone or earbuds | ✅ |
| Extra setup | none | a one-time setup on the phone, or an `adb` command from a computer |

| App | Ring + answer on the watch | Voice through the watch |
|---|---|---|
| WhatsApp | ✅ tested | ✅ tested |
| Instagram | ✅ tested | ✅ tested, no setup needed (Instagram can't see the call state) |
| Telegram | ✅ tested | ✅ tested, same one-time setup as WhatsApp |
| Signal | ✅ tested | ✅ tested, same one-time setup as WhatsApp |

Also:
- **Ring my watch** (Home): a test call that checks calls reach the watch, no one needs to call you.
- **Missed calls on the watch**, with **Call back** for contacts (WhatsApp's own missed-call
  notifications never reach the watch).
- **Follows Do Not Disturb**: a call your phone would silence doesn't ring the watch; favourites and
  repeat callers your phone lets through still do (Home → **When the watch rings** to turn it off).
- **Pause from anywhere**: Home's switch, a Quick Settings tile, or the chip on the watch tile.
- **Not while I'm using the phone** (optional): calls ring only the phone while it's unlocked in your hand.
- **Share diagnostic report** (Home): setup and recent events, without caller names or numbers,
  through the share sheet only. It shows when recent calls happened, so check it before posting it
  in a public issue.

### Calling from the watch (1.2)

Wristline also has a **watch app**. Turn on **Call from the watch** on the phone (asks for Contacts and Phone), then open Wristline on the watch:

- Home: search everyone, your 3 latest calls (tap to call back; **All recent** for the rest), and buttons for **Favourites**, **WhatsApp**, **Telegram** and **Signal**. Each button opens just those people, A–Z; tapping a name there calls on that app. Hold a name to star it.
- Tap a name to call; the call starts in that app on the phone and the voice goes through the watch.
- Wristline's own call screen on the watch: *Calling… → Ringing… → call timer*, one-tap red hang-up, **Mute** when the app's call notification offers it, **Minimise** (open Wristline again to come back), the crown changes the call volume.
- A tile with your favourites first, then recent people.

Telegram contacts appear only if Telegram's **Sync contacts** is on. Instagram can't start calls from outside the app.

## Requirements

- Android 12 or newer, with WhatsApp, Telegram, Instagram or Signal installed.
- A Wear OS watch connected through its companion app. Tested with the **Pixel Watch app**. Other companion apps are untested.
- The watch's **Call audio** Bluetooth profile switched on (phone Settings → Bluetooth → your watch).

## Install

Download the APKs from the [latest release](https://github.com/lucio-chiummo/Wristline/releases/latest). Wristline isn't on Google Play yet.

**Phone (required).** Open `wristline-phone-1.3.apk` on your phone and allow installing from that source when Android asks. Play Protect may warn that it doesn't know the developer. Then follow [Setup](#setup). This is all you need for calls to ring on the watch, and to answer them there.

Android blocks Notification access for apps installed from a file. If the switch is greyed out: App info → ⋮ (top right) → **Allow restricted settings**, then try again.

**Watch (optional).** Only needed for [calling from the watch](#calling-from-the-watch-12). Wear OS has no file installer, so this takes a computer with [adb](https://developer.android.com/tools/releases/platform-tools):

1. On the watch: Settings → System → About, then tap **Build number** 7 times.
2. In Developer options, turn on **ADB debugging** and **Wireless debugging**, then tap **Pair new device**.
3. On the computer, using the addresses and code the watch shows:

   ```sh
   adb pair <ip>:<pairing-port> <code>
   adb connect <ip>:<port>
   adb -s <ip>:<port> install wristline-watch-1.3.apk
   ```

4. Turn Wireless debugging off again.

Install both APKs from the same release. They only talk to each other when signed with the same key, so a watch app you built yourself won't connect to the released phone app.

### Check your download

Wristline asks for sensitive access, including your call notifications, so only install APKs from this repository's releases. Copies from anywhere else may have been changed.

Each release lists the SHA-256 hash of its APKs. To check a file, compare the hash from `shasum -a 256 wristline-phone-1.3.apk` with that list. To check who signed it, run `apksigner verify --print-certs wristline-phone-1.3.apk` (`apksigner` is in the Android SDK build-tools). The certificate's SHA-256 digest must be:

```text
290f69c60854208f97f093ee80aaa8e1bd73fea6c01be69dde8ec9e02dcb2ef4
```

## Setup

Open Wristline. The status card at the top says what's left to do, with one button for it:

1. **Notification access**: notices incoming calls from the apps you turned on.
2. **Notifications**: for call controls and alerts.
3. **Display over other apps**: opens the call when you tap Answer on the watch.
4. **Unrestricted battery**: stops the phone from closing Wristline in the background.

When the card turns green ("Ready"), calls ring on the watch. Use the switches to choose which apps are relayed, or pause relaying altogether with **Relay calls to my watch**. WhatsApp is on by default, Telegram, Instagram and Signal are off.

### Voice through the watch

You can set this up on the phone alone, or with a computer. Either way it's once for each app.

**On the phone**

1. In Wristline, open **Voice through the watch**, tap **Set up on this phone** and agree.
2. Follow the guide: allow notifications, join Wi-Fi, and turn on Developer options (Settings → About phone → tap **Build number** 7 times).
3. In Developer options, turn on **Wireless debugging**, then tap **Pair device with pairing code**.
4. Type the 6-digit code into Wristline's notification.
5. When Wristline says it's done, turn Wireless debugging off.

Wristline pairs with your phone's own Wireless debugging, keeps the key it paired with in the phone's Keystore (where the Keystore can't hold it, in Wristline's private storage, protected by a Keystore key where possible), and runs only `appops set --uid <package> READ_PHONE_STATE ignore` (or `allow` to undo) and `am kill <package>`. Apps you set up this way are repaired silently after an update when Wireless debugging is on and the key is still authorised. Turning voice off for the last app deletes the key. **Forget this phone's key** on the Voice screen deletes it too.

**With a computer**

This takes a computer with [adb](https://developer.android.com/tools/releases/platform-tools):

1. On the phone: Settings → About phone → tap **Build number** 7 times, then turn on **USB debugging** in Developer options.
2. Connect the phone to the computer and allow debugging when the phone asks.
3. In Wristline, open **Voice through the watch**, tap **Set up with a computer** and choose the app. It shows the exact command, for example:

   ```sh
   adb shell appops set --uid com.whatsapp READ_PHONE_STATE ignore
   ```

4. Run it, close that app once, and tap **I've run it**.
5. Turn USB debugging off again.

If an app update resets the setting and you used the computer, Wristline notices on the next call and asks you to tap **Repair**, which shows the command again.

## How it works

When an app rings, Wristline registers a matching call with Android's own calling system, and the watch's companion app shows that call on the watch as a real call. Tapping **Answer** on the watch presses the app's own Answer button. For voice through the watch, Wristline first activates its call, so Android connects the watch as the Bluetooth call headset, and only then answers the app. The app's audio then goes through the watch.

Voice through the watch needs one setting changed for each app that can see the phone's call state (WhatsApp, Telegram, Signal), because otherwise the app sees "another call" and hangs up. Wristline sets that app's `READ_PHONE_STATE` app-op to `ignore`. The app keeps its Phone permission and works normally otherwise.


## Side effects of voice through the watch

- During a call, the app won't notice a normal phone call coming in.
- Wristline shows up as a calling account named "Wristline" in the phone's settings.

## Troubleshooting

The [guide](docs/HOW_TO.md#if-something-goes-wrong) has more. The most common problems:

| Symptom | Fix |
|---|---|
| Call drops right after answering on the watch | The app reset its setting. Wristline shows **Voice needs repair**: tap **Repair**. |
| Watch doesn't ring at all | If the card says **Wristline isn't receiving notifications**, tap **Fix**. On Xiaomi also turn on Autostart for Wristline. Open Wristline: the card must say **Ready**, and the app's switch must be on. Then tap **Ring my watch**: if the test call rings, Wristline and the watch are fine and the calling app's notification is the problem. |
| Voice stays on the phone | Turn the watch's **Call audio** Bluetooth setting off and on. |
| No calls arrive at all, even on the phone | The app may have been force-stopped. Open it once. |
| Phone silent for WhatsApp messages, watch shows them | That's the Pixel Watch app muting the phone while you wear the watch, not Wristline. |

Logs: `adb logcat -s Wristline`.

## Build

Requires JDK 21+ (Android Studio's bundled JDK works) and the Android SDK. The Gradle wrapper downloads everything else, including SDK platform 37 if licences are accepted.

```sh
./gradlew testDebugUnitTest :wear:testDebugUnitTest  # unit tests (phone + watch)
./gradlew assembleDebug :wear:assembleDebug          # debug APKs → app/ and wear/build/outputs/apk/debug/
./gradlew assembleRelease :wear:assembleRelease      # release APKs → app/ and wear/build/outputs/apk/release/
```

From the command line, point `JAVA_HOME` at a JDK 21 or newer if your default is older.

Release builds are signed from `keystore.properties` in the project root (gitignored), or from the file `WRISTLINE_KEYSTORE_PROPERTIES` points to, which lets you keep your key outside the project:

```properties
storeFile=/absolute/path/to/your.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

The phone and watch apps share one version, set in `gradle.properties` (`wristline.versionCode`, `wristline.versionName`); the watch's versionCode is that plus 1000, so the two APKs of the same package never share a code. Both apps must be signed with the same key, or the watch and phone can't talk to each other.

Neither the keystore nor that file is in the repository; keep your own copies safe. Privacy policy: [PRIVACY.md](PRIVACY.md). Reporting a vulnerability: [SECURITY.md](SECURITY.md).

Gradle checks every dependency against the hashes in `gradle/verification-metadata.xml`. After changing a dependency or the Android Gradle plugin, update them and review the diff. Start from an empty Gradle home: with a filled cache Gradle misses parent POMs, and CI's clean build then fails.

```sh
GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 --continue testDebugUnitTest :wear:testDebugUnitTest assembleDebug :wear:assembleDebug :app:assembleShrunk assembleRelease :wear:assembleRelease
```

That only records artifacts for the system it runs on. A new Android Gradle plugin version brings a new `aapt2` for each system, so also add the hashes of its `linux`, `osx` and `windows` jars from [Google's Maven repository](https://dl.google.com/android/maven2/com/android/tools/build/aapt2/maven-metadata.xml), or CI and other contributors' builds will fail.

## Uninstall

Uninstall Wristline normally. If you used voice through the watch, give the apps their setting back first: Voice through the watch → **Turn off voice through the watch**. If you set an app up on the phone, Wristline does it for you, with Wireless debugging on. If you used a computer, it shows the command to run: `adb shell appops set --uid <package> READ_PHONE_STATE allow`.

## License

Wristline's source code is public, but it isn't open source in the formal sense: it's under the [PolyForm Noncommercial License 1.0.0](LICENSE). The source for each release is at its tag in this repository.

In short:

- You can use, study, change and share it for noncommercial purposes, such as personal use, hobby projects, research, or use by a charity, school or public body.
- You can't use it for anything else, such as selling it or building it into a paid product, without permission from Lucio Chiummo. To ask, open an issue.
- If you share a copy, include those terms (or their URL) and the Required Notice line in [NOTICE](NOTICE).
- The name "Wristline" and its logo and icon aren't licensed. A changed version needs its own name and icon, and can't suggest it comes from the Wristline project.

This summary doesn't replace the license. Third-party components keep their own licenses, listed in [NOTICE](NOTICE).

## Disclaimer

Wristline doesn't modify the apps it relays and never reads, records or transmits call audio. It only presses the apps' own notification buttons, and Android routes the audio. It has no servers, accounts or analytics. It asks for the internet permission only to talk to your phone's own Wireless debugging over the phone's own Wi-Fi address: nothing leaves the phone. The key it pairs with lives in the phone's Keystore. On phones where the Keystore can't hold it, it is kept in Wristline's private storage, protected by a Keystore key where possible. You can delete it with **Forget this phone's key** on the Voice screen, or revoke it in Developer options → Wireless debugging → Paired devices (the entry is `wristline@<model>`). The only commands it runs are the app-op ones above and `am kill`, and you can switch Wireless debugging off right after setup. Data for the watch goes through Google's Wear OS connection ([privacy policy](PRIVACY.md)). It is an independent project, not affiliated with WhatsApp, Meta, Telegram or Google, and may break if those apps or Wear OS change how calls work.
