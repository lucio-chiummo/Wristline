# Watch incoming screen + answer-first managed relay — design

Date: 2026-11-21. Branch: notification-voice (v1.5-beta9, one tester round left on POCO F8 Pro / HyperOS).

## Intent

1. **Full-screen incoming call on the watch.** With ring by notification the watch shows only a
   notification. The owner wants a call screen over everything, like a real call.
2. **Port the proven r7/r8 spikes** (hardware-proven on vivo + Pixel Watch 5, 2026-11-21: voice both
   ways, no WhatsApp ringtone after answer, buzz stops):
   - r8: on a war phone (managed relay), answer the app first, place the managed call 1500 ms later.
     Before r8 WhatsApp started its ringtone once a managed call existed while it rang.
   - r7 watch: stopping the buzz replaces it with a 1 ms silent ringtone-usage vibration, because
     `Vibrator.cancel()` misses a vibration started by an earlier process instance.

## Goals

- Ringing by notification + Wristline's watch app installed → watch shows Wristline's incoming
  screen (avatar, name, app, Answer, Decline) on top of everything, while it buzzes.
- Answer = same as the notification's Answer (`ActionReceiver` ANSWER path). Decline = same as Decline.
- After Answer the screen becomes Wristline's call screen (`InCallActivity`: timer, mute, hang up,
  crown volume) and closes when the call ends. (Owner's "sounds good" to the design; the
  after-answer question went unanswered → this is my call.)
- Ring stops (phone stops ringing, call ends, answered elsewhere) → incoming screen closes.

## Non-goals / anti-goals

- No change for watches without Wristline's watch app: the phone notification stays as today (backup).
- Don't port r7's STREAM_RING mute: r7 alone didn't fix the ringtone, r8 did; muting the system ring
  stream risks leaving it muted after a crash. (If the rehearsal shows the ringtone back, revisit.)
- No new third-party dependencies.

## Approaches

1. **Watch app posts its own CATEGORY_CALL notification with a full-screen intent** (chosen). Wear OS
   opens the activity at once; the supported way for calling apps (WhatsApp's watch app does this).
2. Start the activity straight from `CallStateService`: blocked by background-activity-start rules.
3. Full-screen intent on the phone's bridged notification: bridges don't carry full-screen intents.

## Design

Phone (`app`):
- Ring start payload carries caller and app: `"start|<maxMs>|<app package>|<name>"` (name last, may
  contain `|`). Old watch apps parse `start|<ms>` only → keep parsing tolerant on the watch.
- New watch→phone messages `PATH_ANSWER = "/wristline/answer"`, `PATH_DECLINE = "/wristline/decline"`
  handled in `WatchCallService` by the same code as `ActionReceiver` ANSWER/DECLINE (extract shared
  method; no duplication).
- After an answer with voice on watch, the phone tells the watch the call state like it does for
  watch-started calls (`active` with connected time, mute state, `ended`) so the call screen works.
- r8 port: in `answerWithVoiceOnWatch` carrier branch, answer the app first, place the managed call
  after `MANAGED_PLACE_DELAY_MS = 1500` unless the call is gone; `RelayConnection.startAnswered()` for
  an app-answered managed call goes active + routes to watch without answering the app again; every
  fallback (timeout, refused, failed) for an app-answered call leaves voice on the phone and must not
  send the app's answer a second time.

Watch (`wear`):
- `IncomingActivity`: InCallActivity look (avatar, name, app dot/colour, pulsing ring), green Answer,
  red Decline, show-when-locked + turn-screen-on, keep screen on. Answer → `PhoneLink.answer`, opens
  `InCallActivity` (status "Connecting…"); Decline → `PhoneLink.decline`, finish.
- On ring start, `CallStateService` posts a CATEGORY_CALL, high-importance notification on a new
  channel with `setFullScreenIntent(IncomingActivity)`, ongoing, with Answer/Decline actions too;
  on ring stop it cancels it and closes the screen. Manifest: `USE_FULL_SCREEN_INTENT`.
- `WatchBuzz.stop` gets the r7 replace-vibration.

## Left to the builder

Layout details, string wording (EN + IT), channel id, exact class split.
