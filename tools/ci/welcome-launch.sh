#!/usr/bin/env bash
# Runtime acceptance for Issue #27: the debug APK installs on an emulator and
# opens to Welcome.
#
# This lives in a file rather than inline in the workflow because the emulator
# action executes its `script:` with /usr/bin/sh, where the multi-line
# constructs this needs are fragile. Job-log download is not available to the
# agent authoring this branch, so the failure reason is mirrored into GitHub
# Actions annotations.
set -uo pipefail

log=connected.log
: > "$log"

annotate() {
  # $1 = label, remaining stdin = lines
  while IFS= read -r line; do
    printf '::error::%s: %s\n' "$1" "$line"
  done
}

echo "=== device ==="
adb devices
sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
model="$(adb shell getprop ro.product.model | tr -d '\r')"
release="$(adb shell getprop ro.build.version.release | tr -d '\r')"
abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "::notice::DEVICE: model=${model} api=${sdk} release=${release} abi=${abi}"

chmod +x ./gradlew

# Installs the debug APK plus the test APK and runs WelcomeLaunchTest, which
# launches MainActivity and asserts the Welcome surface is displayed.
echo "=== :app:connectedDebugAndroidTest ==="
./gradlew --no-daemon --dependency-verification=strict \
  :app:connectedDebugAndroidTest >>"$log" 2>&1
status=$?

tail -n 120 "$log"

if [ "$status" -ne 0 ]; then
  awk '/\* What went wrong:/{f=1} f{print} /\* Try:/{f=0}' "$log" \
    | grep -v "^[[:space:]]*at " | head -n 18 | annotate WHY
  grep -iE "^e: |error:|FAILED|Installation failed|INSTALL_|Test .* failed|AssertionError" "$log" \
    | sort -u | head -n 10 | annotate ERR
  echo "=== logcat ==="
  adb logcat -d -t 400 2>/dev/null \
    | grep -iE "AndroidRuntime|TestRunner|appt|FATAL" | tail -n 30 \
    | head -n 10 | annotate LOGCAT
  exit "$status"
fi

# Independent evidence, taken from the device itself, that a plain
# launcher-style start reaches Welcome. The instrumentation above already
# asserts the Welcome nodes; this records what the window manager reports for
# an ordinary cold launch.
# AGP uninstalls both APKs once connectedDebugAndroidTest finishes, so the
# supplementary cold start below must reinstall the debug APK first. Without
# this the launch fails with "Activity class ... does not exist", which is an
# artifact of the harness rather than anything about the app.
echo "=== reinstall the debug APK for the cold-start check ==="
apk="$(find app/build/outputs/apk/debug -name '*.apk' | head -n 1)"
test -n "$apk" || { echo "::error::No debug APK found to reinstall"; exit 1; }
echo "installing ${apk}"
adb install -r -t "$apk"

echo "=== launcher-style cold start ==="
adb shell am force-stop dev.anthracite.appt
# `monkey` returns before the activity is actually resumed and can bounce off
# the launcher, so start the LAUNCHER intent directly and wait for it. -W
# blocks until the launch completes; -S forces a cold start.
start_out="$(adb shell am start -W -S \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n dev.anthracite.appt/.MainActivity 2>&1 | tr -d '\r')"
echo "${start_out}"
echo "${start_out}" | head -n 8 | while IFS= read -r line; do
  printf '::notice::AMSTART: %s\\n' "$line"
done

# Wait for the window manager to report AppT focused, rather than assuming a
# fixed sleep is long enough on a cold emulator.
for _ in $(seq 1 20); do
  if adb shell dumpsys window 2>/dev/null \
      | grep -q 'dev.anthracite.appt/.*MainActivity'; then
    break
  fi
  sleep 1
done

focus="$(adb shell dumpsys window 2>/dev/null \
  | grep -iE 'mCurrentFocus|mFocusedApp' | head -n 2 | tr -d '\r')"
resumed="$(adb shell dumpsys activity activities 2>/dev/null \
  | grep -iE 'mResumedActivity|topResumedActivity' | head -n 2 | tr -d '\r')"
echo "focus:   ${focus}"
echo "resumed: ${resumed}"
echo "::notice::FOCUS: ${focus}"
echo "::notice::RESUMED: ${resumed}"

# The cold start must land on AppT's single activity, not a crash dialog or
# the launcher.
case "${focus}${resumed}" in
  *dev.anthracite.appt/*MainActivity*) ;;
  *)
    echo "::error::Cold launch did not resume dev.anthracite.appt/.MainActivity"
    adb logcat -d -t 400 2>/dev/null \
      | grep -iE "AndroidRuntime|FATAL|appt|ActivityManager.*appt" \
      | tail -n 20 | head -n 8 | annotate COLDLOG
    exit 1
    ;;
esac

echo "Runtime acceptance: device=${model} api=${sdk} — launch reached Welcome."
