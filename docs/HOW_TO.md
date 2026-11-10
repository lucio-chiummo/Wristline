# How to use Wristline

Wristline makes WhatsApp, Telegram, Instagram and Signal calls ring on your Wear OS watch, so you can answer, decline and hang up from your wrist.

## What it can do

**When someone calls you**

- Your watch rings like a normal phone call, showing the app and who's calling.
- Answer, decline or hang up from your wrist.
- Talk through the watch's own speaker and microphone, without touching your phone. This needs a one-time setup with a computer.
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

**The basics take about 5 minutes and need only your phone.** Two extras are optional and need a computer: talking through the watch, and starting calls from the watch.

**Jump to:** [What it can do](#what-it-can-do) · [Install](#1-install-the-app) · [Set up](#2-give-wristline-its-permissions) · [Test](#3-test-it) · [Everyday use](#everyday-use) · [Talk through the watch](#optional-talk-through-the-watch) · [Call from the watch](#optional-call-from-the-watch) · [Problems](#if-something-goes-wrong) · [Uninstall](#turning-things-off-and-uninstalling)

## What you need

- An Android phone with Android 12 or newer.
- WhatsApp, Telegram, Instagram or Signal on that phone.
- A Wear OS watch connected to the phone through its companion app. Wristline has only been tested with a Pixel Watch and the Pixel Watch app, so other watches may not work.

## 1. Install the app

1. On your phone, open the [latest release](https://github.com/lucio-chiummo/Wristline/releases/latest) and download `wristline-phone-1.3.apk`.

   Only download Wristline from that page. Copies from anywhere else may have been changed.
2. Open the downloaded file. If Android asks, allow your browser or file app to install apps.
3. If Play Protect warns that it doesn't know the developer, you can continue. It shows this because Wristline isn't on Google Play.

## 2. Give Wristline its permissions

Open Wristline. The card at the top tells you what's left and gives you one button at a time. Tap **Allow** and follow it until nothing is left.

| Permission | Why Wristline needs it |
|---|---|
| **Notification access** | To notice incoming calls from the apps you turned on. It ignores all other notifications. |
| **Notifications** | To show call controls and alerts. |
| **Display over other apps** | To open the call when you tap Answer on the watch. |
| **Unrestricted battery** | To stop your phone closing Wristline in the background. Find Wristline in the list and choose "Don't optimise" or "Unrestricted". |

**Is the Notification access switch greyed out?** Android does this for apps installed from a file. Open Settings → Apps → Wristline, tap ⋮ at the top right, choose **Allow restricted settings**, then try again.

Then check one setting on your phone: go to Settings → Bluetooth, open your watch, and keep **Call audio** (or **Phone calls**) on.

## 3. Test it

1. In Wristline, tap **Ring my watch**. Nobody needs to call you.
2. Your watch should ring. Answer or decline it there.
3. When the card says **Ready**, you're done.

WhatsApp is on by default. To use Telegram, Instagram or Signal, turn on their switches on the Wristline home screen.

## Everyday use

**When a call comes in,** your watch rings like a normal phone call and shows the app and the caller's name.

- **Answer** on the watch. The call opens on your phone, and you talk on the phone or your earbuds. To talk through the watch itself, see [below](#optional-talk-through-the-watch).
- **Decline** on the watch to reject the call.
- **Hang up** from the call-controls card on the watch.

**Other things you can do:**

| To do this | Do this |
|---|---|
| Pause all calls to the watch | Turn off **Relay calls to my watch**, use the Quick Settings tile, or use the chip on the watch tile if you have the watch app |
| Choose which apps ring the watch | Use each app's switch on the home screen |
| Follow Do Not Disturb, or stop ringing while you're using the phone | **When the watch rings** |
| See or clear recent calls | **Call history** |

Missed calls also show up on your watch.

## Optional: talk through the watch

Without this, you answer on the watch but talk on your phone or earbuds. With it, you use the watch's own speaker and microphone.

**You need a computer**, once for each app. WhatsApp, Telegram and Signal need this. Instagram doesn't.

**What this changes:** the app can no longer see whether your phone is in a call. So during a call in that app, it won't notice a normal phone call coming in. It otherwise works as usual, and you can undo this at any time.

### First, get adb on your computer

`adb` is Google's free tool for talking to Android devices.

1. Download [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) for your computer and unzip it.
2. Open a terminal (Command Prompt on Windows, Terminal on a Mac) in the unzipped `platform-tools` folder. If your computer can't find `adb`, type `./adb` instead on a Mac or Linux.

### Then, set it up

1. On your phone, go to Settings → About phone and tap **Build number** 7 times. This turns on Developer options. The exact menu varies by phone.
2. In Developer options, turn on **USB debugging**.
3. Connect the phone to the computer with a cable. If the phone asks whether to allow debugging, tap **Allow**.
4. In Wristline, open **Voice through the watch** and tap **Set up** for your app. Wristline shows the exact command. For WhatsApp, it's:

   ```sh
   adb shell appops set --uid com.whatsapp READ_PHONE_STATE ignore
   ```

5. Type that command on the computer and press Enter. If nothing is printed, it worked.
6. Close that app once on your phone, by swiping it away from your recent apps.
7. In Wristline, tap **I've run it**.
8. Turn **USB debugging** off again.

Make a test call to check that you can hear through the watch.

**During a call,** a small notification lets you move the voice to the phone and back, for example if the watch battery runs low.

**If Wristline says "Voice needs repair",** an app update or reinstall reset the setting. Tap **Repair** and run the command again. Until then, you talk on the phone.

## Optional: call from the watch

This lets you start WhatsApp, Telegram and Signal calls from the watch: search, favourites, recent calls and all your contacts. It needs the watch app, and installing that needs a computer with `adb` ([get it above](#first-get-adb-on-your-computer)). You only do this once. Instagram can't start calls this way.

### Install the watch app

1. From the same release, download `wristline-watch-1.3.apk` to your computer.
2. On the watch, go to Settings → System → About and tap **Build number** 7 times.
3. In Developer options on the watch, turn on **ADB debugging** and **Wireless debugging**, then tap **Pair new device**.
4. On the computer, using the addresses and the code the watch shows:

   ```sh
   adb pair <ip>:<pairing-port> <code>
   adb connect <ip>:<port>
   adb -s <ip>:<port> install wristline-watch-1.3.apk
   ```

5. Turn **Wireless debugging** off on the watch.

### Turn it on

1. On your phone, open Wristline, tap **Call from the watch**, then **Turn on**.
2. Allow Contacts and Phone when Android asks. Wristline sends only your contacts' names to your watch, never numbers or photos, and it never calls phone numbers itself.
3. Open Wristline on the watch. Your latest calls are at the top: tap one to call back. Below them, tap **WhatsApp**, **Telegram** or **Signal** to see just those people, then tap a name to call on that app. Hold a name to make it a favourite.

For a quick way to call, add the Wristline tile, or the complication on your watch face. One tap on a person there starts the call straight away.

Telegram contacts only appear if **Sync contacts** is on in Telegram's settings.

## If something goes wrong

| What's happening | What to try |
|---|---|
| The watch doesn't ring at all | The card must say **Ready**, and the app's switch must be on. Then tap **Ring my watch**. If that rings, Wristline and the watch are fine, so the problem is with that app's notification. |
| The Notification access switch is greyed out | Settings → Apps → Wristline → ⋮ → **Allow restricted settings**. |
| It stopped working after a while | Check that the battery setting is still **Unrestricted**, then open Wristline once. Some phones have an extra battery setting, which Wristline points out on those phones. |
| The call drops right after answering on the watch | The app reset its setting. Tap **Repair** and run the command again. |
| The voice stays on the phone | On your phone, go to Settings → Bluetooth → your watch, then turn **Call audio** off and on. |
| No calls arrive at all, even on the phone | The calling app may have been force-stopped. Open it once. |
| The phone is silent for WhatsApp messages | That's the Pixel Watch app muting the phone while you wear the watch, not Wristline. |
| `adb` says no devices were found | Check the cable and that **USB debugging** is on, then look for the Allow prompt on the phone. |
| No contacts show on the watch | Check that **Call from the watch** is on. For Telegram, turn on **Sync contacts** in Telegram. |

**Still stuck?** [Open an issue](https://github.com/lucio-chiummo/Wristline/issues). It helps to attach a report from **Share diagnostic report** on the home screen. It has no caller names or numbers, but it does show when your recent calls happened, and issues are public, so read it first.

Found a security problem? Please don't post it publicly. See the [security policy](../SECURITY.md).

## Turning things off and uninstalling

- **Pause for a while:** turn off **Relay calls to my watch**.
- **Stop talking through the watch:** open **Voice through the watch**, tap **Turn off voice through the watch**, and run the command it shows on a computer. For WhatsApp, it's:

  ```sh
  adb shell appops set --uid com.whatsapp READ_PHONE_STATE allow
  ```

  Then close the app once and tap **I've run it**.
- **Uninstall:** if you set up talking through the watch, turn that off first, because uninstalling Wristline doesn't undo the setting. Then uninstall it like any other app.

## Your privacy

Wristline has no servers, accounts, analytics or ads, and the phone app doesn't ask for the internet permission. It never listens to or records calls. Your watch gets its data through Google's Wear OS connection. The [privacy policy](../PRIVACY.md) has the details.
