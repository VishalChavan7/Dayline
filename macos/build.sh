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

# Desktop widgets: a WidgetKit extension inside the app.
swift build -c release --arch arm64 --product DaylineWidgets
WBIN="$(swift build -c release --arch arm64 --show-bin-path)/DaylineWidgets"
APPEX="$APP/Contents/PlugIns/DaylineWidgets.appex"
mkdir -p "$APPEX/Contents/MacOS" "$APPEX/Contents/Resources"
cp "$WBIN" "$APPEX/Contents/MacOS/DaylineWidgets"
sed -e "s/__VERSION__/$VERSION/g" -e "s/__BUILD__/$BUILD/g" Resources/Widgets-Info.plist > "$APPEX/Contents/Info.plist"
# App Intents metadata, so the widgets' buttons (tick a task, check in a habit) work.
TMPD="$(mktemp -d)"
find "$(pwd)/Sources/DaylineWidgets" -name '*.swift' > "$TMPD/sources.txt"
find "$(swift build -c release --arch arm64 --show-bin-path)/DaylineWidgets.build" -name '*.swiftconstvalues' > "$TMPD/constvals.txt"
echo "const values: $(wc -l < "$TMPD/constvals.txt") file(s)"
if xcrun --find appintentsmetadataprocessor >/dev/null 2>&1; then
  xcrun appintentsmetadataprocessor \
    --toolchain-dir "$(dirname "$(dirname "$(dirname "$(xcrun --find swiftc)")")")" \
    --module-name DaylineWidgets --sdk-root "$(xcrun --show-sdk-path)" \
    --xcode-version "$(xcodebuild -version 2>/dev/null | awk '/Build version/{print $3}')" \
    --platform-family macOS --deployment-target 14.0 --bundle-identifier com.dayline.mac.widgets \
    --output "$APPEX/Contents/Resources" --target-triple arm64-apple-macos14.0 \
    --dependency-file "$TMPD/deps.d" --stringsdata-file "$TMPD/intents.stringsdata" \
    --source-file-list "$TMPD/sources.txt" --swift-const-vals-list "$TMPD/constvals.txt" \
    --deployment-aware-processing --no-app-shortcuts-localization --force \
    || { echo "warning: App Intents metadata step failed"; xcrun appintentsmetadataprocessor --help 2>&1 | head -60; }
fi
ls -R "$APPEX/Contents/Resources" | head -20

# Ad-hoc signature (free; no Apple developer account). Keeps the same identity for updates.
# The widget is signed first (sandboxed), then the app around it.
codesign --force --sign - --entitlements Resources/Widgets.entitlements --identifier com.dayline.mac.widgets "$APPEX"
codesign --force --sign - --identifier com.dayline.mac "$APP"
codesign --verify --deep --strict "$APP" && echo "Signature OK"

mkdir -p ../outputs
rm -rf ../outputs/Dayline.app ../outputs/Dayline-mac*.zip
cp -R "$APP" ../outputs/Dayline.app
ditto -c -k --keepParent "$APP" "../outputs/Dayline-mac-$VERSION.zip"
echo "Built Dayline $VERSION ($BUILD)"
