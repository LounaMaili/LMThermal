# R4 authoritative source and ART integration

## Test-only runtime route

`lmtr-art` is a separate Android application, not a product flavor or ABI expansion.
It has no camera/USB permissions, native build, launch UI or dependency on `app`.
The existing contract/recorder code is packaged on ART, with shared engine tests,
51 unchanged canonical assets, exact JSON/Float32/SHA/CRC/codec tests and actual
engine outputs for independent Python readback. Canonical fixtures are inputs only.

Build with `./gradlew :lmtr-art:assembleDebug :lmtr-art:assembleDebugAndroidTest`.
Run `:lmtr-art:connectedDebugAndroidTest` on a camera-free emulator. This is not a
physical HT-301/Pixel8 test. Product ABI remains arm64-v8a. No new pairing is needed.

The branch-restricted `lmtr-r4-art.yml` tests API26 (product minimum) and API35 on
x86_64 Ubuntu-hosted emulators. Exact checkout SHA, read-only token, pinned actions,
45-minute/job bounds, disabled emulator cameras and always-retained artifacts are
required. Logs retain device SDK/ABI/model, exact source SHA and synthetic outputs.
`check_lmtr_r4.py --existing DIR --report REPORT` uses the independent Python reader,
not a host JVM substitute for ART. Native Windows PR #15 remains isolated/unmerged.

Local infrastructure has SDK35 but no emulator executable/AVD. CI runtime evidence
is pending; packaging success alone is not ART conformance.

## Integration work in progress

Authoritative callback/lifecycle implementation and its new tests will be recorded
here as they are validated. Recording must never subscribe to conflated camera/UI
StateFlow or Celsius rendering. No recording product UI or R5/R6 is authorized.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
