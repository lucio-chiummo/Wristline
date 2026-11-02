#!/bin/bash
# Records how a calling app behaves during an incoming call, for adding new apps to Wristline.
# Usage: tools/capture_calls.sh [adb-serial] [package ...]
#   default packages: org.telegram.messenger com.instagram.android
# Writes to captures/: full logcat, the app's call notification records (once per second while
# present) and any Telecom PhoneAccount the app registers. Stop with Ctrl+C.
set -u
SERIAL=${1:-$(adb devices | awk 'NR==2{print $1}')}
shift || true
PACKAGES=${*:-org.telegram.messenger com.instagram.android}
PATTERN=$(echo "$PACKAGES" | sed 's/ /|/g; s/\./\\./g')
OUT="$(dirname "$0")/../captures/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
echo "Recording $PACKAGES on $SERIAL into $OUT"
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" logcat -v time > "$OUT/logcat.txt" &
LOGCAT=$!
trap 'kill $LOGCAT 2>/dev/null; echo "Saved to $OUT"; exit 0' INT TERM
while true; do
  D=$(adb -s "$SERIAL" shell dumpsys notification --noredact 2>/dev/null)
  RECORDS=$(echo "$D" | grep -E -A 70 "NotificationRecord\(.*pkg=($PATTERN) ")
  if [ -n "$RECORDS" ]; then
    echo "$RECORDS" > "$OUT/notif_$(date +%H%M%S).txt"
  fi
  adb -s "$SERIAL" shell dumpsys telecom 2>/dev/null | grep -E "PhoneAccount: ComponentInfo\{($PATTERN)" >> "$OUT/telecom.txt"
  sleep 1
done
