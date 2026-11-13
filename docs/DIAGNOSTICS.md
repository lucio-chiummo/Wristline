# Reading a Wristline diagnostic report

People share this from Home → **Share diagnostic report**. It is plain text with no caller names or
numbers. This page explains each line so an issue can be diagnosed from the report alone.

Read the events **from the bottom up**: the newest event is at the top.

## Header

| Line | What it tells you |
|---|---|
| `Device`, `Phone OS build` | Phone maker and model, Android SDK level, and the maker's own build name (HyperOS, ColorOS, One UI…). Makers' builds kill background apps and block pop-ups more than Pixel phones do. |
| `Relaying`, `Notification listener` | `paused` or `NOT connected` means calls can't reach Wristline at all. |
| `Watch` | `ready`: a Wear OS watch with Wristline's watch app on it. `app_missing`: watch connected, no watch app (incoming calls still work). `no_watch`: Wear OS sees no watch. `unknown`: Wear OS services didn't answer. |
| `Companion apps` | Which watch companion app is installed. **Only `Pixel Watch` is tested.** Other companions (OHealth, Galaxy Wearable, Mobvoi, …) relay Wristline's call their own way, and some don't send Answer back. |
| `Bluetooth call audio` | Whether the phone sees a Bluetooth device (the watch) that can carry call audio. `NOT available now` means voice through the watch can't work: the watch's **Call audio** / **Phone calls** Bluetooth profile is off, or the watch isn't connected. |
| `Phone settings` | `battery unrestricted no` or `background restricted yes`: the phone may stop Wristline. `display over other apps no`: answering can't open the calling app. `full-screen calls no`: Android 14+ blocks the ringing screen. `do not disturb`: calls may be silenced on purpose. |
| `Missing permissions` | The setup steps still to do. |
| `Apps` | Each calling app's version, its switch, and `voice` (`ON` = voice through the watch is set up). |

## Events

| Code | Meaning |
|---|---|
| `incoming <app> full mode / basic mode` | The app's call notification was seen; Wristline will ring the watch. Full mode = voice through the watch. |
| `call_notification <app> category=… actions=[…]` | What the app's call notification looked like (buttons only). Logged when a call starts and whenever its buttons change. Shows e.g. WhatsApp offering `Call again` after the other side hung up. |
| `call_created` / `call_not_created` | Telecom made, or failed to make, Wristline's ringing call. No `call_created` after `incoming` means the watch never got a call. |
| `telecom … state ringing / dialing / active / disconnected` | Wristline's call changing state. |
| `telecom … hold asked`, `event <name>` | The watch (or Android) sent something other than Answer/Decline/Hang up. Some companions send Hold. |
| `telecom … ended <cause>` | Why Wristline's call ended: `local` (hung up here), `remote` (the app's call ended), `rejected`, `missed`, `answered_elsewhere` (answered on the phone, or basic mode), `canceled`. |
| `silenced_on_watch` | The watch asked to stop ringing without answering. |
| `answered_on_watch` / `declined_on_watch` | The watch's tap reached Wristline. Ringing then `missed` with neither of these means **the tap never came back from the watch** (companion issue). |
| `audio_route <where> of <available>` | Where the call audio went (`bluetooth` = the watch, `earpiece`, `speaker`) and what was available. `earpiece of earpiece+speaker` = no Bluetooth call audio. |
| `hung_up_on_watch pressed <label>` | Hang-up from the watch, and which app button Wristline pressed. `no app button` = only Wristline's call was ended. |
| `watch_message hangup / mute / relay on / call ignored` | A message from Wristline's watch app reached the phone. |
| `app_action_failed no button / no longer valid` | Wristline couldn't press the app's button (the app changed or withdrew its notification). |
| `outgoing <app> full mode` | The watch asked to call someone. `outgoing_not_started` = the app never showed its call within 20 s. |
| `dropped_taking_audio` | The app hung up while the watch was taking the audio: voice setup is broken for that app. |
| `ended <app>` | The app's call is over. `missed` follows if nobody answered. |
| `voice_moved phone / watch` | Voice moved during a call. |
| `busy`, `switched_off`, `do_not_disturb`, `phone_in_use`, `relay_paused` | A call didn't ring the watch, on purpose. |
| `unreadable_call <shape>` | Wristline didn't recognise the app's buttons (usually after an app update). |
| `telecom_refused` | Android refused Wristline's call. |
| `test_call started / reached / missed` | **Ring my watch**. `reached` = answered or declined on the watch. |
| `listener_*` | Notification access lost and recovered (`after 3m unbound` = calls missed for 3 minutes). |
| `setup_*`, `reinstalled`, `repair_silent` | Voice setup results and automatic repairs after an app update. |

## Common patterns

- **Rings, Answer does nothing:** `call_created`, then `telecom state ringing`, then `missed` with no
  `answered_on_watch`. The watch companion isn't sending Answer to Android. Check `Companion apps`;
  look for `silenced_on_watch` or `hold asked` to see what it sends instead.
- **Answered, no voice on the watch:** `answered_on_watch full mode` but `audio_route earpiece`,
  or `Bluetooth call audio: NOT available now`.
- **Voice one way only:** `audio_route bluetooth` is logged but one side can't hear. That's the Bluetooth
  link or the watch's call volume, not Wristline's routing.
- **Hang-up from the watch did something else:** read `hung_up_on_watch pressed …` and the
  `call_notification` just before it.
- **Calls stop arriving after a while:** `listener_disconnected` or long `unbound` gaps, with
  `battery unrestricted no` or `background restricted yes`.
