# Watch hub redesign + Signal

Date: 2026-11-10 · Branch: `signal-support`

## Why

Signal is joining WhatsApp and Telegram as an app you can call from the watch. The watch home screen
today is one long scroll: search, favourites, 10 recent calls, then every callable contact A–Z
(~250 people). With a third app it gets worse, and the owner can't see "just my Signal people".
The recent-calls list is also often out of date.

Signal itself has no watch app: without Wristline, a Signal call does nothing on the watch.

## Goals

1. **Hub home screen** on the watch (option B from the mockups):
   search → latest calls → one big button per app.
2. **One list per app** (WhatsApp, Telegram, Signal) plus a Favourites list.
3. **History always current** when the home screen is shown.
4. **Signal parity with WhatsApp**: ring/answer/decline/hang up on the watch, missed calls with Call
   back, Signal contacts on the watch, call out from the watch, **and talk through the watch**.

## Non-goals

- No change to the tile, the quick-call complication, the in-call screen or the phone app's UI
  (beyond the Signal voice setup row that the phone already renders per app).
- No video calls. No Instagram call-out (Instagram can't start calls from outside its app).
- No new phone↔watch protocol messages unless the history fix needs one (see "History").

## Anti-goals (would be a failure even if it "works")

- Swipe-sideways navigation between pages: on Wear OS swipe-right closes the app.
- Anything that sends numbers or photos to the watch (privacy promise in the phone app: names only).
- A Signal call that gets dropped or put on hold because Wristline took the audio.

## Watch: screens

### Home (`MainActivity`)

Vertical list, crown-scrollable, rounded-screen padding as today:

1. **Search field** (existing row): searches everyone across all apps. While a query is typed, the
   rest of home is replaced by the matching contacts (today's behaviour).
2. **Recent** section header, then the **latest 3** calls. Each row: avatar, name, app dot, time;
   missed calls in red ("Missed · 2 min"). Tap = call back with that call's app (existing
   `callBack`). If there are more than 3 calls, a row **"All recent"** opens the full-history list.
3. **App buttons** in a 2-column grid: **Favourites**, **WhatsApp**, **Telegram**, **Signal**, each
   showing its contact count, in the app's colour (button backgrounds: existing `whatsapp_button`,
   `telegram_button`, new `signal_button`, and a favourites amber tone). A button whose count is 0 is
   hidden (app not installed, contacts not synced, or no favourites yet). If every button is hidden
   and there's no history, show the existing "no contacts" message.
4. The favourite hint ("hold a name to star it") moves into the app lists (shown when there are no
   favourites).

### App badge on people rows (owner asked; option A chosen as Claude's call)

Every person row that is tied to one app (recent calls, and app-list rows if useful) shows a small
round badge on the bottom-right of the avatar: app colour fill, the app's letter in dark
(`on_badge`): **W** WhatsApp, **T** Telegram, **S** Signal, **I** Instagram (incoming only), with a
ring in the row's card colour so it separates from the avatar. Rows for people on several apps
(favourites, search results) keep the existing coloured dots + labels instead of a badge. Drawn by
Wristline; no app icons are shipped or sent from the phone.

### App switches vs. watch buttons (Claude's call)

The phone's per-app switch keeps meaning "this app's incoming calls ring the watch" only. Watch app
buttons and call-out ignore it (today's behaviour): turning Signal ringing off doesn't take Signal
contacts off the watch. No phone-side change.

### App list (new `ContactListActivity`)

Opened from an app button with that app's package, or with "favourites", or "recent".

- **Title** row: app dot + "WhatsApp · 214" (Favourites: "★ Favourites · 6"; Recent: "Recent").
- **App list**: that app's contacts A–Z; the A–Z jump strip shows when the list is long (existing
  `FAST_SCROLL_MIN` / `LetterIndex` logic). Tap = call **on that app** directly, no app picker.
  A person on several apps appears in each of those lists.
- **Favourites list**: favourites in their saved order, with app dots. Tap = existing `pick()`
  (picker only if the person is on several apps).
- **Recent list**: the full history the phone sends (up to its cap), same row style as home.
- Hold a contact row = star/unstar (existing behaviour).

### App picker (`ChooseAppActivity`)

Already updated on this branch: third button for Signal, wraps to a second row when a person is on
all three apps. Still used from search results, favourites and the tile/complication.

## History

Today `MainActivity` loads contacts + history only in `onCreate`
(`wear/.../MainActivity.java:83`), so a home screen kept in the background shows old calls.
Required: reload whenever home becomes visible again (`onResume`/`onStart`), without flicker
(keep showing the previous data until the new load lands) and without stealing focus from the
search keyboard. The builder must first confirm with a test or log that this is the cause (the
phone may also be late to publish; `WatchSync.publish` / `recordCall`); fix whichever it is.

## Signal: voice through the watch

Facts from the phone (2026-11-10): Signal writes "Signal voice call" contact rows
(`vnd.org.thoughtcrime.securesms.call`, 9 contacts on the owner's phone, checked over adb), so
`ContactsReader` finds Signal contacts with no extra work. Signal holds `READ_PHONE_STATE` (granted) and
`MANAGE_OWN_CALLS`, and registers a **self-managed Telecom PhoneAccount**. Ringing and in-call
notifications already classify correctly (tests in `CallShapeTest`).

- Same mechanism as WhatsApp/Telegram: the one-time `appops set --uid org.thoughtcrime.securesms
  READ_PHONE_STATE ignore` so Signal doesn't see Wristline's call go off-hook. Existing
  `FullMode` / `AppOpsCommands` / setup screen already handle any app that holds the permission.
- **Unknown, must be tested on a real call**: whether Telecom puts Signal's own self-managed call
  on hold, or Signal hangs up, when Wristline's self-managed call becomes active. Test plan:
  apply the appop, answer a Signal call from the watch with voice-through-watch on, talk 30 s,
  hang up from the watch; record with `tools/capture_calls.sh` plus `dumpsys telecom`.
- **Also test a call started from the watch** (my call): `OutgoingCalls` raises the watch's call
  before Signal's, and its comment warns an app already in a call hangs up when our call takes the
  audio. Check too that Signal's contact entry places the call directly (no confirmation screen).
- **Who tests** (my call): Claude drives adb, install, capture and logs; the owner answers/places
  the calls with a second Signal account calling in.
- If it works: set `SIGNAL.fullModeVerified = true` and drop `beta`; README table row updated.
- If it doesn't: keep voice on the phone for Signal, record what Telecom did in the
  `SupportedApp.SIGNAL` comment, and bring the finding back to the owner before trying anything
  heavier.

## Already done on this branch (keep)

- `SupportedApp.SIGNAL` (off by default, beta, separate in-call notification, Signal call mimetype),
  manifest `<queries>`, phone strings EN/IT, README/HOW_TO, watch `Protocol.SIGNAL`, label, colour,
  `ChooseAppActivity` third button. Phone + watch unit tests pass.

## Constraints

- Java, existing view-based UI (`WearableRecyclerView`, XML layouts), no Compose migration.
- Strings in EN and IT (`values/` and `values-it/`).
- Unit tests in the existing style (Robolectric where the current tests use it).
- Build with JDK 21 (Android Studio's JBR); the system default JDK 26 fails.
- Install on the owner's phone with the **release** signing key (`~/.wristline-keys/`, via
  `WRISTLINE_KEYSTORE_PROPERTIES`); a debug build can't replace the installed app.

## Left to the builder

- Exact dp sizes, spacing and the favourites amber tone, within the existing visual language
  (`colors.xml`, `bg_circle`, card rows).
- Whether the app list is a new Activity or a mode of `MainActivity` (new Activity preferred:
  back = swipe-right returns to home naturally).
- How counts are computed (from `PhoneLink.Contacts.all` per `apps` entry).
