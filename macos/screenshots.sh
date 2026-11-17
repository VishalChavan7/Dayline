#!/bin/bash
# Opens the built app with sample data, saves every screen (light and dark) to ../outputs/mac-screenshots, then quits.
set -uo pipefail
cd "$(dirname "$0")"
OUT="$(cd .. && pwd)/outputs/mac-screenshots"
rm -rf "$OUT" && mkdir -p "$OUT"
build/Dayline.app/Contents/MacOS/Dayline --screenshots "$OUT" &
PID=$!
( sleep 150; kill $PID 2>/dev/null ) &
wait $PID
ls "$OUT"

# Frames for the README's sync video (same sample data and day as the phone's screenshots).
STORY="$(cd .. && pwd)/outputs/story-frames"
rm -rf "$STORY" && mkdir -p "$STORY"
build/Dayline.app/Contents/MacOS/Dayline --story "$STORY" &
PID=$!
( sleep 90; kill $PID 2>/dev/null ) &
wait $PID
ls "$STORY"

