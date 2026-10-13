#!/usr/bin/env bash
# Test-only application and emulator; no production APK, USB permission, pairing or camera.
set -euo pipefail
reports_dir="$1"
# Refuse accidental physical-device execution when invoked outside the CI emulator.
test "$(adb -e shell getprop ro.kernel.qemu | tr -d '\r')" = 1
adb -e shell getprop ro.product.model > "$reports_dir/device-model.txt"
adb -e shell getprop ro.build.version.sdk > "$reports_dir/device-sdk.txt"
adb -e shell getprop ro.product.cpu.abilist > "$reports_dir/device-abis.txt"
adb -e logcat -c || printf 'Log clear unavailable; retain full runtime log instead.\n' > "$reports_dir/log-clear-warning.txt"
trap 'adb -e logcat -d > "$reports_dir/logcat.txt" || true' EXIT
# Run directly after packaging: connectedDebugAndroidTest uninstalls its target,
# deleting private synthetic output before host readback. No physical app is touched.
./gradlew -p tools/lmtr-r4-art :lmtr-art:assembleDebug :lmtr-art:assembleDebugAndroidTest --console=plain 2>&1 | tee "$reports_dir/gradle.txt"
adb -e install -r lmtr-art/build/outputs/apk/debug/lmtr-art-debug.apk
adb -e install -r lmtr-art/build/outputs/apk/androidTest/debug/lmtr-art-debug-androidTest.apk
adb -e shell am instrument -w -r org.lmthermal.art.test/androidx.test.runner.AndroidJUnitRunner | tee "$reports_dir/instrumentation.txt"
grep -Eq '^OK \([1-9][0-9]* tests?\)' "$reports_dir/instrumentation.txt"
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED' "$reports_dir/instrumentation.txt"; then exit 1; fi
adb -e exec-out run-as org.lmthermal.art tar cf - files/r4 > "$reports_dir/synthetic.tar"
tar xf "$reports_dir/synthetic.tar" -C "$reports_dir"
python3 tools/check_lmtr_r4.py --existing "$reports_dir/files/r4/generated" --report "$reports_dir/python-readback.json"
python3 tools/check_lmtr_r4.py --existing "$reports_dir/files/r4/source/generated" --report "$reports_dir/python-source-readback.json"
test -z "$(git status --porcelain)"
