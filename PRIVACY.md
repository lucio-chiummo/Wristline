# Privacy Policy: Wristline

_Last updated: 2 November 2026_

Wristline helps you answer WhatsApp, Telegram and Instagram calls on a Wear OS watch. This policy explains what the app accesses and what it does with it.

## Summary

**The developer of Wristline receives none of your data.** Wristline has no servers, no accounts, no analytics and no advertising. The phone app doesn't ask for the internet permission. It keeps a short call history on your phone, and data for the watch app goes to your own watch through Google's Wear OS connection ([details](#the-wear-os-connection)).

## What the app accesses, and why

| Access | Why | What happens to the data |
|---|---|---|
| **Notification access** | To notice when WhatsApp, Telegram or Instagram shows an incoming-call or in-call notification. | Only **call** notifications from the apps you switched on are used. All other notifications are ignored. From a call notification, Wristline reads the caller's display name (to show it on the watch) and the notification's buttons (Answer, Decline, Hang up). The name and time of each relayed call are saved in the call history below, and sent to your watch if "Call from the watch" is on. Everything else from the notification is kept in memory only for the duration of the call. |
| **Manage own calls** (Android calling system) | To show the call on your watch as a real call. | The caller's display name is passed to Android's calling system on your phone, which shows it on your paired watch. It stays on your devices. |
| **Display over other apps** | So the app's own call screen can open when you answer from the watch. | No data involved. |
| **Notifications** | To show call controls and status messages. | No data involved. |
| **Contacts** (only if you turn on "Call from the watch") | To show the people you can call with WhatsApp or Telegram on your watch. | Wristline reads contact names and which of those apps can reach them, and sends that list, with your recent calls, only to the Wristline app on your own paired watch over the Wear OS connection. |
| **Make phone calls** (`CALL_PHONE`, only with "Call from the watch") | WhatsApp and Telegram start their voice calls through Android's call system; starting one for you needs this permission. | Calls start only when you tap a name on your watch. |

Voice through the watch needs one app setting (`READ_PHONE_STATE` app-op) changed, which you do yourself with a computer. Wristline has no shell access of its own, and updating deletes the Wireless debugging key older versions could leave behind.

Wristline never listens to, records or transmits call audio. Android routes the audio between the calling app and your watch; Wristline only presses that app's own buttons.

## Data stored on the device

Wristline stores only the following, in the app's private storage:

- Its own settings (which apps are relayed, whether voice through the watch is on).
- The last 20 relayed calls (time, app, caller's name), whether or not you call from the watch. You can see and clear them under Home → Call history.
- A log of its last 100 events for the diagnostic report, with their times (for example "incoming call, WhatsApp, answered on the watch", setup results, and the button labels of a call notification it couldn't read). It holds no caller names or numbers; where such a button label repeats the notification's text or contains a number, that part is masked.

The watch app keeps your favourites on the watch. Uninstalling the app deletes all of this. It does not undo the setting changed for voice through the watch, so turn that off first.

## The Wear OS connection

Contacts, recent calls and call status reach your watch through the Wear OS Data Layer, which Google Play services runs. Android only lets the same app, signed with the same key, read this data. It goes directly over Bluetooth when it can. Google's documentation says that otherwise it is routed through Google Cloud, end-to-end encrypted, and that the contact and call lists may also be backed up to the cloud. Wristline doesn't control this routing.

## Third parties

Wristline is not affiliated with, endorsed by, or sponsored by WhatsApp, Meta, Telegram or Google. Apart from Google's Wear OS connection above, it shares no data with them or anyone else. The diagnostic report (Home → Share diagnostic report) leaves the phone only if you share it yourself, through the app you pick in Android's share sheet; it contains the phone model, Android version, app versions, Wristline's settings and the event log above, including when recent calls happened. Check it before posting it anywhere public, such as a GitHub issue.

## Children

Wristline is not directed at children under 13.

## Changes

Changes to this policy will be published at this address with a new date.

## Contact

Questions: open an issue at https://github.com/lucio-chiummo/Wristline/issues
