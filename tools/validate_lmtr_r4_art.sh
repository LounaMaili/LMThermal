#!/usr/bin/env bash
# Test-only application and emulator; no production APK, USB permission, pairing or camera.
set -euo pipefail
reports_dir="$1"
adb shell getprop ro.product.model > "$reports_dir/device-model.txt"
adb shell getprop ro.build.version.sdk > "$reports_dir/device-sdk.txt"
adb shell getprop ro.product.cpu.abilist > "$reports_dir/device-abis.txt"
adb logcat -c
trap 'adb logcat -d > "$reports_dir/logcat.txt" || true' EXIT
./gradlew :lmtr-art:connectedDebugAndroidTest --console=plain 2>&1 | tee "$reports_dir/gradle.txt"
adb exec-out run-as org.lmthermal.art tar cf - files/r4 > "$reports_dir/synthetic.tar"
tar xf "$reports_dir/synthetic.tar" -C "$reports_dir"
python3 tools/check_lmtr_r4.py --existing "$reports_dir/files/r4/generated" --report "$reports_dir/python-readback.json"
test -z "$(git status --porcelain)"
