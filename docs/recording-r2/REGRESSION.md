# R2 regression and scope checks

## Historical 2026-10-07 checks

Final checks on 2026-10-07, using the repository JDK/Gradle environment:

| Check | Result |
|---|---|
| `:core:test` | 232 passed |
| `:app:testDebugUnitTest` | 10 passed |
| `:recording-r2:test` | 20 passed, including metadata budget and failed-close regressions |
| `:app:assembleDebug`, `:app:assembleDebugAndroidTest` | Passed |
| `:app:lintDebug` | 0 errors; 11 existing warnings |
| `:app:connectedDebugAndroidTest`, Pixel 8 Android 17 | 44 discovered: 40 passed, 4 explicit opt-in experiments skipped; 0 failures |
| Explicit Pixel R2 packet + metadata classes | 3 passed; same 15 file hashes as the published packet |
| Localization checker | 110 default / 110 French keys passed |
| Shared camera-boundary checker | 25 shared sources passed |
| Independent Python hostile-reader tests | 7 passed |
| Independent Linux Android-written packet | All 15 cases passed; exact bytes and immutable sources |
| Actual child-process kill matrix | All 8 cases passed |
| `git diff --check` | Passed |

The four skipped general-suite experiments are the live sustained test, two codec
packet tests and isolated thermometry cost test. They require explicit opt-in and
were executed separately. The live test methods finished, but both recording
matrices **failed the performance acceptance gate**; JUnit completion is not R2
acceptance. The final metadata admission correction has not passed a new live run.

Existing lint warnings: eight GradleDependency, one ChromeOsAbiSupport, one
DataExtractionRules and one MissingApplicationIcon. No new lint error.

Before connected-test uninstall/data cleanup, all private live evidence was copied
to ignored local storage: 30 files, 9,299,060,736 archive bytes. Archive bounds and
all three benchmark reports were verified readable. The private scene archive is
not committed. Sanitized SAF results were also preserved before cleanup.

Compared with base `5174b923bcc2011228d15d648f2766e941d8684e`, production
`app/src/main`, `core/src/main` and Accepted `docs/LMTX_FORMAT_V1.md` have no diff.
Disposable code is confined to `recording-r2`, tooling and Android test sources;
the app APK contains no R2/Zstd library. Desktop remained read-only and clean.

Real Windows is pending. No R1b acceptance, canonical R3 fixture, production
recorder, camera/session/thermometry change or independent calibration claim.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.


## Measured continuation — 2026-10-10

Executed candidate source: `9f9d6097960d17188611e2da159ac4606cee148d`.
Fresh `--rerun-tasks` JVM/build/lint checks: core 232, app 12, prototype 27,
**271 passed**, zero failures/errors/skips; both debug APK builds pass.
Lint has zero errors and the same 11 existing warnings. Localization checks
110 English/French keys and camera-boundary checks 25 shared sources. Nine
independent Python reader/SAF-helper tests pass. The actual eight-case child-process
kill matrix passes again on the optimized writer.

Direct instrumentation (without uninstall/data-clear) discovers 47 Pixel tests:
**41 passed, six opt-in experiments skipped, zero failures**. The skipped methods
are candidate live, original ablation live, historical sustained live, two packet
methods and isolated thermometry cost. Packet/metadata classes then run explicitly:
three passed. The test-only SAF Activity launch/layout check passes on the Pixel
(visible instructions, clickable on-screen button, no overlap); it opens no USB
and does not select a destination. Historical provider guarantees remain limited
to the preserved local-provider matrix, not all providers.

[Numeric checks](reports/continuation-regression.json),
[actual process kills](reports/continuation-process-kill.json),
[completed live results and remaining gates](CONTINUATION_RESULTS.md).
The opt-in DEFLATE wait first expired at DETECTED before measurement; this is not
a performance result or a passing live test. Its log is preserved privately.

Unlike the historical scope statement above, this continuation deliberately changes
one production presentation source: `CelsiusPresenter.kt`, after a controlled
publication-starvation reproduction and JVM/ART regression. Compared with the
continuation base `8a0d1ce0232f4a2f9c2eb6a911eae148c16fc051`, no other production
source changes; core, camera/session/thermometry, native coordinates and Accepted
LMTX v1 are unchanged. The app APK still has no R2/Zstd dependency, storage action
or product recorder. Desktop remains read-only and clean. No R1b/R3 work or merge.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.


### Bounded DEFLATE target candidate

The explicitly predeclared smaller-chunk candidate is
`531b8fd0653d503309f5def0899380a939b6f6d6`. Default remains 16 MiB; the live
repeat explicitly requests 4 MiB preferred chunks. No reader hard bound or
wire field changes. All 272 JVM tests pass (core 232, app 12, prototype 28),
including later-chunk logical-role exactness and independent seek. Both APK builds,
lint (0 errors/11 existing warnings), localization and shared boundaries pass.
The installed matching APKs again pass 41 Pixel tests (six opt-in skipped).
All eight actual process-kill cases pass on this source too
([report](reports/continuation-short-chunk-process-kill.json)). Live and final
memory/packet results are now recorded in the continuation report.

### Resume validation on the same installed candidate

Read-only installed APK hashes match the committed candidate; no source rebuild,
reinstall, data clear or old-test resumption was needed. Fresh unique Full-STORED
and 4 MiB Analysis/Native/Full DEFLATE runs pass throughput/freshness and measured
clean-process memory targets. The operator confirms all previews usable.
Three explicit final Pixel packet/context tests and nine Python reader/SAF-helper
tests pass. All 15 regenerated packet members are byte-identical and independently
pass Linux validation. Existing 272 JVM, 41 general Pixel and eight process-kill
results remain applicable to the unchanged executable; they were not repeated.
The full private live files, independent readback and recovery evidence are linked
from [the completed continuation](CONTINUATION_RESULTS.md).
Real Windows remains pending, R2 stays open, and this resumed task changes only
documentation/numeric reports. No new application/camera/thermometry code or merge.

## Native Windows packet validation — 2026-10-11

Validated source: `736aba08fa78d070d0a31b29826b7cd4b63fe2ec`, clean existing R2
branch. Native Windows 11 Pro 25H2 build 26200.9550/x64; Python 3.12.10 (64-bit).
The original ZIP hash matches before extraction and after validation. Fresh local
extraction, no source-member edits or packet regeneration.

| Windows check | Result |
|---|---|
| STORED/DEFLATE `--baseline-only` | Exit 0; 10 expected outcomes passed: 6 complete, 2 recovered, 2 corruption rejected; 5 explicit Zstd skips |
| Separate optional all-codec run | Exit 0; 15 expected outcomes passed: 9 complete, 3 recovered, 3 corruption rejected; no skips; preinstalled reference Zstd 1.5.7 |
| Existing Linux report comparison | All 10 baseline and all 15 optional case rows exactly equal both published reports |
| Exact synthetic checks | Float32/masks/native/acquisition/context/gaps, restricted native views and lazy seeks passed |
| Missing-END recovery | Each case retains 5 frames/1 gap/1 chunk and remains incomplete |
| Committed corruption | Explicit `committed_payload_integrity` rejection for each codec |
| Source integrity | Archive, manifest and all 15 member hashes unchanged |
| `git diff --check` | Passed |

[Sanitized numeric report](reports/windows-packet.json) and
[Windows guide/review boundary](WINDOWS_VALIDATION.md). No unexpected failures or
packet compatibility differences. Historical Linux/Android/JVM/process-kill/live
suites were not rerun; their existing scoped evidence is preserved. This task
changes only evidence/documentation, with no format/prototype or Desktop change.
R2 is ready for completion review; no R1b acceptance, R3/product work or merge.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
