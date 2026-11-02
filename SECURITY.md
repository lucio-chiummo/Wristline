# Security Policy

Wristline reads call notifications and can read contact names, so security problems matter even in a small project.

## Reporting a vulnerability

Please report it privately: open the repository's [Security tab](https://github.com/lucio-chiummo/Wristline/security) and choose **Report a vulnerability**. Don't open a public issue, because people could be exposed before a fix is out.

Include what an attacker can do, what they need (for example another app installed on the phone or watch), the steps to reproduce it, and the Wristline, Android and Wear OS versions you used.

This is a volunteer project with no bug bounty. Reports are handled on a best-effort basis, and you'll be credited in the fix if you want to be.

## Supported versions

Only the [latest release](https://github.com/lucio-chiummo/Wristline/releases/latest) gets security fixes.

## What's in scope

- Other apps, on the phone or the watch, making Wristline do something the user didn't ask for, such as starting a call.
- Caller names, contact names or call history reaching anyone other than the user and their own watch.
- Release APKs that don't match this source code or the signing certificate in the [README](README.md#check-your-download).

## What's out of scope

- Attacks that need an already rooted or compromised device, or an unlocked phone in the attacker's hands.
- Problems in WhatsApp, Telegram, Instagram, Wear OS or Google Play services themselves. Please report those to their vendors.
- Modified or rebuilt copies of Wristline that weren't published in this repository's releases.

If you've found a copy of Wristline being distributed with changes, please report that too.
