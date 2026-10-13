# R4 authoritative source and ART integration

## Test-only runtime route

`lmtr-art` is a separate Android application, not a product flavor or ABI expansion.
It has no camera/USB permissions, native build, launch UI or dependency on `app`.
The existing contract/recorder code is packaged on ART, with shared engine tests,
51 unchanged canonical assets, exact JSON/Float32/SHA/CRC/codec tests and actual
engine outputs for independent Python readback. Canonical fixtures are inputs only.

Build with `./gradlew -p tools/lmtr-r4-art :lmtr-art:assembleDebug :lmtr-art:assembleDebugAndroidTest`.
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
or test execution. API35 passed 53 ART tests and both independent readbacks; this infrastructure
failure was not a passing API26 gate.

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

The ART build entry point now excludes the production `app` project entirely.
A later API26 attempt booted successfully but root-project Gradle configuration tried
to install the product NDK and failed before tests. `tools/lmtr-r4-art` reuses the
same module sources with pinned AGP/Kotlin versions and needs only JDK17/SDK35;
no NDK/CMake/UVC/JNI dependency is configured. This fixes an integration-tool boundary,
not a recorder assertion. The product root build/ABI remains unchanged.

## Reviewed owner-guard runtime checkpoint

[Run 38414074626](https://github.com/LounaMaili/LMThermal/actions/runs/38414074626),
exact source `b5c9dca80ce174f0ff5125c9f42e1bce66921115`, passed **55 tests on each
API26/API35 x86_64 emulator**, including the two owner race/observer regressions.
[Receipt](art-owner-checkpoint.json) retains model/ABI/SDK, source SHA, report/archive
hashes and both independent Python readbacks per API: 12 actual engine files (ten
completed, two bounded recovered prefixes) plus 75 source observations. Logical
Float32/mask/native/acquisition bytes, contexts/sequences and source immutability pass.
Canonical assets are all 51 unchanged R3 inputs; no new canonical corpus is generated.

API26 reports Android SDK built for x86_64 (`x86_64,x86`), API35 reports
sdk_gphone64_x86_64 (`x86_64,arm64-v8a`); `java.vm.name` is `Dalvik` on ART. This is
actual Android instrumentation, not a JVM inference or Pixel8/HT-301 test. The shared
33 engine +17 source/lifecycle +5 ART-specific tests cover idle/Stop/queue/recovery,
storage faults, source epochs, exact bytes/masks, feature/version corpus rejection and
close/reopen. They do not execute every host JSON/adversarial vector on ART, measure
ART/RSS peaks, prove infinite schedules or certify filesystem/power-loss durability.

[Run 38414323583](https://github.com/LounaMaili/LMThermal/actions/runs/38414323583)
on `d79a0361754bc8d145aa5ab07be663a8fafcf060` also passed both API jobs, 55 tests
per API and both readbacks after the literal matrix correction. Earlier API26 failures
and the rejected matrix-expression run are preserved; no failed run is relabeled.
The final isolated-build run is a separate exact-SHA gate recorded below when reviewed.

## Isolated-build failure preserved

[Run 38415016892](https://github.com/LounaMaili/LMThermal/actions/runs/38415016892)
on `7b587e246455e246651f2547c1a059163763f4ae` passed API35 (55 tests and both
readbacks), but API26 executed all 55 with one failed assertion: exhausted-loss-reserve
must preserve an incomplete source. A later preparation exception could overwrite the
reason used as the no-END guard. The engine now retains a sticky unrepresented-loss
flag under its intake lock; unexpected worker failure also prevents certifying abandoned
admitted work. A deterministic mixed-failure regression protects this without weakening
the original assertion. This is a genuine recorder bug, not infrastructure or a wire
change. Historical passing checkpoints do not erase this failure; final ART must retest.

The harness now extracts its synthetic `files/r4` outputs even after a test assertion
failure, while returning that failure after independent readback. It does not export
unrelated test-private caches/fixture frames; successful file parity cannot override a
failed test verdict. The earlier isolated API26 failure has logs but no extracted files,
because its old harness exited at the assertion gate.

The test-only `AcquisitionOwnershipCostProbe` measures the exceptional source-callback
copy of 224255 synthetic malformed transport bytes independently of the off-worker
HT fixture benchmark. It verifies retained bytes survive source mutation. The ART
source probe also runs it; no physical capture or thermometry invocation is introduced.

Additional deterministic tests cover both failure orderings, Stop/detach/source-error/
memory-pressure racing a lost journal plus a later worker error, repeated Stop/source
error after failure, and unexpected worker exit without an exhausted journal. Fully
journaled per-item source failure remains finalizable with failed END and a known-count
gap; ordinary represented errors are not mislabeled incomplete. Post-footer navigation
failure retains its acknowledgment, unknown recovered tail and unchanged source hash.

The continuation source probe hashes its original JSONL oracle and checks source clocks/
receipt/acquisition/sequence facts independently. Common intake ticks must lie between
genuine pre/post callback witnesses. Snapshot counters include charged in-flight and
pending peaks, independently of queue peaks; these are accounting metrics, not ART/RSS.

The actual engine probe now emits 14 engineering cases, including mixed unjournaled
loss after an acknowledged CHUNK and an honestly finalized failed source with a known
count gap. Independent Python compares original payload hashes/contexts/sequences in
the recovered prefix (including prior torn/navigation cases) and exact gap reasons/ranges.
No-END/unknown-tail and source hash preservation remain required.

Unexpected worker exit marks unrepresented loss only when a dequeued item or queued
intake is abandoned. An interrupted idle worker with no admitted evidence can honestly
finalize an empty failed END. A dedicated instance-specific fault test checks this
boundary, without exposing thread interruption as a product API.

## Final reviewed code checkpoint

[Run 38435068678](https://github.com/LounaMaili/LMThermal/actions/runs/38435068678)
checks exact source `abea098e69332f0a16ee779f6181c3af9040947c`: **61/61 on each
API26 AOSP x86_64 and API35 Google APIs x86_64**. [Final receipt](art-final-code-validation.json)
retains environment, artifact hashes, ownership-copy measurements and independent
Python parity for 14 actual engine files plus 75 source observations per API.
The source oracle SHA, exact clock facts and genuine intake bounds also pass. The
39 recorder +17 bridge +5 ART tests include mixed failure orderings, no-END protection,
honest represented failure/idle interruption, native/acquisition views and ownership.
No skipped assertion or earlier failure is reclassified.

The [56-test sticky checkpoint](art-sticky-checkpoint.json),
[60-test clock checkpoint](art-clock-checkpoint.json) and
[60-test recovered-prefix checkpoint](art-prefix-checkpoint.json) preserve distinct
source SHAs and scope. This continuation adds only documentation after that tested code checkpoint; those
commits do not imply another unreviewed runtime result. Physical Pixel8/HT-301 source/cadence/freshness/memory/lifecycle gates remain open.


The [field preparation](FIELD_VALIDATION.md) adds the same test-only clock/resource/source-route helpers to the emulator suite and three existing-room pre-transform oracle readbacks. Exact new runtime receipts are tracked separately; the arm64 field APK is neither installed nor executed by this camera-disabled workflow.


## Field-preparation checkpoints and a scheduling failure

[Run 38449569251](https://github.com/LounaMaili/LMThermal/actions/runs/38449569251)
on `e1ab47a2951203da76acc1f733cb4ae2ce2d5d76` passed **79 tests per API26/API35**,
14 engine files, 75 synthetic source observations and three original-owner existing-room
profile readbacks per API. This remains a scoped passing checkpoint, not current physical acceptance.

[Run 38450510713](https://github.com/LounaMaili/LMThermal/actions/runs/38450510713)
on `f2230c0e9de1a694d55b4d37e114d5b873d34f15` executed **80 tests**: API35 passed;
API26 failed `sourceFreezeRunsOnExistingPreparationWorkerAndOverloadDoesNotFreezeRejectedOwner`,
expected two acknowledgments but observed one. Its independent engine/source/field
readbacks still passed; they do not override the failed assertion. The preparation-only
test reserved one writer slot: while HEADER sync lagged, the second prepared owner
could legitimately encounter writer overload. The test now reserves both accepted
writer entries, keeping its one-slot preparation queue, exact two freezes/preparation
thread/two acknowledgments and adding zero writer drops. This isolates the intended
preparation assertion without changing any product/field capacity or writer-overload
behavior/coverage. Final exact-source ART must pass independently. Historical failed
logs/artifacts are retained privately and linked here, never relabeled.
