#!/usr/bin/env bash
# Measures channel change time: from a log mark just before a D-pad key press to the
# decoder's first picture ([GetVisibleSize] from the Amlogic C2 decoder). Same method for any app.
# Usage: zap-bench.sh <adb-serial> <rounds> [key]   (the app must be playing live TV)
set -euo pipefail
S=$1; N=${2:-8}; KEY=${3:-KEYCODE_DPAD_DOWN}
for i in $(seq 1 "$N"); do
  adb -s "$S" logcat -c
  adb -s "$S" shell "log -t ZAPMARK go; input keyevent $KEY"
  sleep 6
  adb -s "$S" logcat -d -v epoch | awk '
    /ZAPMARK/ && !t0 { t0 = $1 }
    t0 && /GetVisibleSize/ { printf "%d\n", ($1 - t0) * 1000; exit }'
done
