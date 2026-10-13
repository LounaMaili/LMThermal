# R4 authoritative source and ART integration

## Test-only runtime route

`lmtr-art` is a separate Android application, not a product flavor or ABI expansion.
It has no camera/USB permissions, native build, launch UI or dependency on `app`.
The existing contract/recorder code is packaged on ART, with shared engine tests,
51 unchanged canonical assets, exact JSON/Float32/SHA/CRC/codec tests and actual
engine outputs for independent Python readback. Canonical fixtures are inputs only.

Build with `./gradlew :lmtr-art:assembleDebug :lmtr-art:assembleDebugAndroidTest`.
Use `tools/validate_lmtr_r4_art.sh REPORTS_DIR` on a camera-free emulator to retain
outputs for independent readback; `connectedDebugAndroidTest` removes the target on exit. This is not a
physical HT-301/Pixel8 test. Product ABI remains arm64-v8a. No new pairing is needed.

The branch-restricted `lmtr-r4-art.yml` tests API26 (product minimum) and API35 on
x86_64 Ubuntu-hosted emulators. Exact checkout SHA, read-only token, pinned actions,
45-minute/job bounds, disabled emulator cameras and always-retained artifacts are
required. Logs retain device SDK/ABI/model, exact source SHA and synthetic outputs.
`check_lmtr_r4.py --existing DIR --report REPORT` uses the independent Python reader,
not a host JVM substitute for ART. Native Windows PR #15 remains isolated/unmerged.

Local infrastructure has SDK35 but no emulator executable/AVD. CI runtime evidence is recorded below; packaging success alone is not ART conformance.

## Integration work in progress

Authoritative callback/lifecycle implementation is described below. Recording must never subscribe to conflated camera/UI
StateFlow or Celsius rendering. No recording product UI or R5/R6 is authorized.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Authoritative acquisition route

Core `CameraSession.observations` is optional and inert by default. The HT-301
controller publishes immediately after its existing `MeasurementGate.evaluate`, before
bitmap/latest StateFlow publication. No transport, control or thermometry algorithm is
changed. It captures `elapsedRealtimeNanos` at read receipt, retains existing immutable
frame/matrix/index owners and reports native callback replacement/malformed counter
increments separately. Those are host UVC signals, not sensor frame numbers or UI losses.

`camera-recording` is not a product dependency. `Ht301RecordingAdapter` checks exact
session/source identity and supplies deferred `RecordingOffer`s. The existing preparation
worker copies source Float32 bits and exact native/acquisition bytes, verifies native view
slices later, and reuses `Ht301ExportEvidence.interpretation` without invoking the still
exporter or running thermometry. Current parsed settings/calibration are retained even
when radiometry is unavailable; no last-good Celsius is substituted. Complete all-valid
HT measurements have no mask (exact all-valid semantics); arbitrary modules can preserve
actual masks. Unknown sensor/UTC time remains unknown. Original callback sequence and
original millisecond measurement receipt are separate module evidence, not precision
manufactured from milliseconds. Context retains IEEE source bits and exact finite Decimal
values; unavailable/nonfinite facts are not plausible replacement values.

A small source wrapper costs no extra image copy for complete frames. Malformed transport
is copied only for an active consumer. Deferred offers charge a conservative 3 MiB HT peak
including retained source and prepared data; standard queues remain 4 MiB each. The source
never hashes/compresses/writes/assembles a chunk. Actual copy/metadata cost is measured in
a host probe, not assumed as Pixel8 performance.

`CameraRecorderBinding` is an opt-in `CameraSessionLifetime` observer. Explicit Start
attaches one source consumer; Connect/open does not start recording. Epoch check and
admission are atomic with immediate cutoff. Close/error/detach/replacement/background/
screen-off/disposal unsubscribe and stop without waiting for preparation/footer I/O on UI.
Rotation uses existing CameraUiLifetime retention. Same-device reconnect receives a new
session; old captured callbacks cannot enter the next recording. A new Start is refused
while the earlier engine drains, keeping reservations bounded. No background service,
SAF, recording UI, settings/profile product screen or Desktop integration is added.

## First ART checkpoint (historical)

Run [38406304259](https://github.com/LounaMaili/LMThermal/actions/runs/38406304259),
source `e80d88fb9ab346d25c3821f0b4df9e14421f0511`, actually passed 37 tests on each
API26/API35 x86_64 emulator. Overall run **failed**: connectedDebugAndroidTest uninstalled
its target before `run-as` extraction (`unknown package`). Logs/XML/tar failure artifacts
remain preserved. The revised harness packages then installs/runs instrumentation directly,
verifies its success log and extracts only synthetic app-owned files before uninstall.
This historical run does not establish the later source/lifecycle code or independent
ART output parity; the final run must validate those separately.

Second checkpoint run [38411040771](https://github.com/LounaMaili/LMThermal/actions/runs/38411040771)
on `ca96404` passed the full API35 job (50 ART tests plus both Python readbacks).
API26 stopped before tests because logcat's startup clear was unavailable. Log clearing
is now best effort rather than a conformance gate; instrumentation verdicts remain strict.
All ADB harness operations explicitly select an emulator and verify `ro.kernel.qemu=1`,
so this helper cannot accidentally install/run against a physical paired phone.

Third checkpoint run [38412292500](https://github.com/LounaMaili/LMThermal/actions/runs/38412292500)
on `10a4014238d28af58bf3a405cacf9ac76df03bf6` is preserved. API26 failed during
SDK system-image download (`Error on ZipFile unknown archive`), before any emulator
or test execution. API35 verdicts and the final runtime receipt must be inspected
separately; an infrastructure failure is not a passing API26 gate.

The synthetic UI cadence now updates `CameraPreview`, not an acquisition-statistics
counter. Recorder-clock scale is one nanosecond but its actual resolution remains
unknown (`null`); the test does not manufacture measured clock precision. These
metadata corrections leave the accepted wire and canonical inputs unchanged.

The optional owner notifications are serialized with the existing owner-generation
invalidation lock. Close racing a late module-open completion cannot repopulate the
recorder source after cutoff; exceptions in the optional observer cannot suppress
camera release. Two latch-driven regressions exercise both paths without hardware.

API26 now uses the official AOSP `default` x86_64 image, API35 remains `google_apis`.
The harness requires no Google services. The API26 Google image download failed in
successive jobs before ART execution; official SDK metadata also provides the smaller
AOSP image. This changes only emulator provisioning, not product ABI/minimum SDK or
test assertions. Historical failures remain evidence; the replacement job must pass
its own instrumentation and independent file-readback gates.
