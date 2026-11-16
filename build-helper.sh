#!/bin/bash
# Dayline build helper
# Runs ONLY the fixed Gradle tasks listed below, when a request file appears in .agent/.
# Logs go to .agent/log.txt; the APK and screenshots are copied to ./outputs.
# Stop it any time with Ctrl+C (it also exits after 3 hours idle).

cd "$(dirname "$0")" || exit 1
ROOT="$(pwd)"
AG="$ROOT/.agent"
mkdir -p "$AG"

if [ -z "$JAVA_HOME" ]; then
  for c in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
           "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
    if [ -x "$c/bin/java" ]; then export JAVA_HOME="$c"; break; fi
  done
fi
if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null)"
  export JAVA_HOME
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
echo "sdk.dir=$ANDROID_HOME" > "$ROOT/local.properties"

{
  echo "JAVA_HOME=$JAVA_HOME"
  "$JAVA_HOME/bin/java" -version 2>&1
  echo "ANDROID_HOME=$ANDROID_HOME"
  echo "platforms:"; ls "$ANDROID_HOME/platforms" 2>&1
  echo "build-tools:"; ls "$ANDROID_HOME/build-tools" 2>&1
  echo "licenses:"; ls "$ANDROID_HOME/licenses" 2>&1
  sw_vers 2>&1
} > "$AG/env.txt"
echo "ready" > "$AG/status"

echo ""
echo "  Dayline build helper is running — leave this window open."
echo "  Press Ctrl+C to stop."
echo ""

idle=0
while true; do
  if [ -f "$AG/request" ]; then
    req="$(head -1 "$AG/request" | tr -d '[:space:]')"
    rm -f "$AG/request" "$AG/done"
    case "$req" in
      compile)     tasks=":app:compileDebugKotlin" ;;
      build)       tasks=":app:assembleDebug" ;;
      release)     tasks=":app:assembleRelease" ;;
      test)        tasks=":app:testDebugUnitTest" ;;
      screenshots) tasks=":app:recordRoborazziDebug" ;;
      all)         tasks=":app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:recordRoborazziDebug" ;;
      stop)        echo "Stopped."; sh ./gradlew --stop >/dev/null 2>&1; echo "stopped" > "$AG/status"; exit 0 ;;
      *)           echo "Unknown request: $req" > "$AG/log.txt"; echo 1 > "$AG/done"; continue ;;
    esac
    echo "running" > "$AG/status"
    echo "$(date '+%H:%M:%S')  running $tasks"
    sh ./gradlew $tasks --console=plain --continue > "$AG/log.txt" 2>&1
    code=$?
    mkdir -p "$ROOT/outputs"
    cp app/build/outputs/apk/debug/*.apk "$ROOT/outputs/" 2>/dev/null
    cp app/build/outputs/apk/release/*.apk "$ROOT/outputs/" 2>/dev/null
    if [ -d app/build/outputs/roborazzi ]; then
      rm -rf "$ROOT/outputs/screenshots"
      cp -R app/build/outputs/roborazzi "$ROOT/outputs/screenshots"
    fi
    if [ -d app/build/test-results ]; then
      rm -rf "$AG/test-results"
      cp -R app/build/test-results "$AG/test-results"
    fi
    echo "$code" > "$AG/done"
    echo "ready" > "$AG/status"
    echo "$(date '+%H:%M:%S')  finished (exit code $code)"
    idle=0
  fi
  sleep 3
  idle=$((idle + 3))
  if [ $idle -gt 10800 ]; then echo "Idle for 3 hours — exiting."; exit 0; fi
done
