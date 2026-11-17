#!/bin/bash
# Builds Dayline.app (Apple silicon) with the Swift command-line tools — no Xcode needed.
# Output: build/Dayline.app, copied to ../outputs/Dayline.app and ../outputs/Dayline-mac-<version>.zip
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(cat VERSION)"
BUILD="$(date -u +%y%m%d%H%M)"

swift build -c release --arch arm64 --product Dayline
BIN="$(swift build -c release --arch arm64 --show-bin-path)/Dayline"

APP=build/Dayline.app
rm -rf build && mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/Dayline"
cp Resources/AppIcon.icns "$APP/Contents/Resources/AppIcon.icns"
sed -e "s/__VERSION__/$VERSION/g" -e "s/__BUILD__/$BUILD/g" Resources/Info.plist > "$APP/Contents/Info.plist"
# Ad-hoc signature (free; no Apple developer account). Keeps the same identity for updates.
codesign --force --sign - --identifier com.dayline.mac "$APP"

mkdir -p ../outputs
rm -rf ../outputs/Dayline.app ../outputs/Dayline-mac*.zip
cp -R "$APP" ../outputs/Dayline.app
ditto -c -k --keepParent "$APP" "../outputs/Dayline-mac-$VERSION.zip"
echo "Built Dayline $VERSION ($BUILD)"
