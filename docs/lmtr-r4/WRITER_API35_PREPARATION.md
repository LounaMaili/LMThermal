# R4 writer timing: final local review and API35 preparation

Status: **local preparation only**. No commit, push, Actions run, emulator, ADB or physical
camera/device access is authorized by this review. The original 13-file patch remains
unchanged in its original worktree; this separate local preparation branch starts at
`dbfab7ee031275ba5251b00262f7aa9cfc3aa2c3`. No writer optimization is included. The [preparation receipt](writer-api35-preparation.json)
records local checks separately from the inherited validation history.

## Review of the 13-file instrumentation snapshot

| File | Review conclusion |
|---|---|
| `RecorderEngine.kt` | Optional scalar timing wraps existing writer calls. Decision rules, intake witnesses, timers, limits, END guard and durable acknowledgment ordering remain unchanged. |
| `RecorderInstrumentation.kt` | Separate clock; observer/clock exceptions isolated; fixed vocabulary and saturated failure counts; no body/throwable retained. Blocking observers and VM exhaustion remain outside this isolation guarantee. |
| `RecordingChunk.kt` | Original assembly extracted without optimization; the first full validation remains after assembly and the independent second validation remains in append. |
| `FieldPreflight.kt` | Fixed sample arrays, checked exact sums, overflow/invalid/interrupted/omitted accounting. Admission/clock policy prefix unchanged. Quantiles are first-N, not a full-population estimate after omissions. |
| `HtRecorderStressProbe.kt` | Adds bounded reporting, not a loss/cadence relaxation. Reports retained across the two codecs contain scalar/JSON results rather than aggregate buffers or payload owners. |
| `HtMemoryProbe.kt` | Optional fixed-identity fixture instrumentation enables exact-byte checks; its new last parameter broke one old Android trailing-lambda call, corrected at that call site in this preparation only. |
| `RecorderEngineTest.kt` | Eight additional host regressions, including internal-boundary tests. They require test friendship when compiled in the separate Android test module. |
| `WriterMetricsTest.kt` | Five deterministic aggregate regressions; no throughput or ART inference. |
| `WRITER_INSTRUMENTATION.md` | Nesting/exclusions and diagnostic overhead stated. Logical invariants do not imply identical real-clock drop decisions. |
| `writer-instrumentation-validation.json` | Historical local receipt retained unchanged; not a receipt for a new ART execution. |
| `docs/lmtr-r4/README.md` | Scoped documentation link only. |
| `docs/SPECIFICATION.md` | Diagnostic API description only. |
| `CHANGELOG.md` | Local-only status, no recording or throughput acceptance claim. |

Timing results never feed recorder decisions. Nevertheless, elapsed wall time and heap
occupancy can change because of instrumentation; this can indirectly change deadline
expiry, drops or a memory guard. Separate clocks preserve witness values/domains and
policy, not identical execution schedules. Callbacks run synchronously before returning
to the caller, so some delay before acknowledgment is possible; acknowledgment never
moves before backend synchronization. No cross-call validation cache is introduced.

All aggregate updates are scalar/fixed-array operations. Per-distribution monitors do
not take the recorder intake lock; no worker, wakeup or queue ordering is added. The
normal path has one metrics writer and reporting follows worker termination. There is
no atomic live snapshot across cells. Error counters use CAS with saturation; this is
bounded storage, not a universal wait-free API under arbitrary external contention.

## Memory cost and lifetime

The raw sample storage is exactly 128 arrays × 512 Longs × 8 = **524288 bytes**.
The additional object graph is real, not free. A host-only Java instrumentation census
with HotSpot17, compressed oops and 8-byte alignment measured the following reachable
shallow graph before reporting, excluding shared enum/class metadata:

| Object category | Count | Host bytes |
|---|---:|---:|
| LongArrays, including headers | 128 | 526336 |
| FieldTotal | 768 | 18432 |
| FieldDistribution | 128 | 8192 |
| Cell / Status | 16 / 32 | 384 / 1024 |
| Object arrays | 3 | 152 |
| FieldWriterMetrics / RecorderInstrumentation | 1 / 1 | 32 / 32 |
| AtomicLong / clock callback | 2 / 1 | 48 / 16 |
| **Total** | **1080** | **554648** |

This is a HotSpot object-layout observation, **not ART live heap, allocation volume,
RSS, native memory, class-loading cost or a peak/admission guarantee**. ART headers and
layout remain unmeasured. The other six coarse distribution buffers are separate and
present in both modes of the planned controlled comparison.

The engine holds instrumentation, which holds its observer; FieldWriterMetrics holds
instrumentation back. This unreachable cycle does not itself prevent collection.
It retains aggregate scalars/arrays only, not payloads, frames, exception stacks or IDs.
Its lifetime is at least the codec's engine lifetime through Stop, reporting and cleanup.
There is no explicit proof that the old engine/metrics locals have died when the next
codec allocates its metrics. Conservatively allow overlap of two graphs until old roots
are replaced/collected; twice the host graph would be 1109296 bytes, not a measured ART
peak. No explicit GC is added to shorten this lifetime or favor a snapshot.

Reporting allocates maps, strings and temporary sorted copies. Each distribution can
create up to two N-element LongArray copies for sorting; returned maps retain statistics,
not the original arrays. Reports for up to two codecs remain in the stress report list.
Transient/uncollected reporting allocations and retained JSON graphs add cost beyond
512 KiB. These are finite/bounded by this protocol, but no exact ART peak is asserted.
The unchanged 78 + 32 MiB policy does not magically absorb these as zero-cost memory.

The runtime `platform_before` counter sample follows fine-table allocation. Its GC/
bytes-allocated deltas therefore **exclude preallocation**, while occupied-heap/headroom
samples can include its retention. `platform_after` is captured before fine-report map/
JSON generation, which can then affect the next codec's process state. No subtraction
of those deltas yields exact incremental instrumentation cost or total test peak memory.
The future ON/OFF comparison must retain this limitation and the fixed lifetime ledger.

## Why 442 versus the earlier 450 JVM tests

| Module | Previous first-failure campaign | Writer-instrumentation campaign |
|---|---:|---:|
| lmtr-contract | 73 | 73 |
| lmtr-recorder | 58 | 66 |
| camera-recording | 20 | 20 |
| lmtr-field-support | 44 | 49 |
| core | 234 | 234 |
| app | 21 | not run |
| **Total** | **450** | **442** |

The common host scope grew from 429 to 442 (+8 recorder, +5 aggregates). The previous
450 included 21 app unit tests; the later campaign omitted that unchanged app suite.
Thus 450 + 13 - 21 = 442. This is a scope difference, not eight removed/failing tests.
The 64 Python tests and 51 canonical cases are separate counts, never added as JVM tests.
This preparation reruns the directly affected JVM suites; inherited receipts remain
historical and are not relabeled as new executions.

## Test packaging corrections, without changing recorder code

Compiling the copied 13-file patch exposed two concrete Android-test compile failures:

1. `HtMemoryProbe.run(root) { ... }` targeted its new final Boolean parameter. The
   unrelated fixture test now names `platformSample`; its body and assertions are unchanged.
2. The Android test module compiles shared recorder test sources as a different Kotlin
   module. New internal-boundary JVM tests lacked friendship. The opt-in
   `-PlmtrWriterTimingOnly=true` profile adds the recorder **JAR** to the Android-test
   Kotlin task's friend paths. All test sources remain packaged; no tests/assertions
   are excluded, ignored or made public. The default historical profile is not changed.

Initial Java/Kotlin source filters and a classes-directory friend path were ineffective;
the logs are retained. The JAR friend path matches the binary dependency actually used
by the compiler. APK packaging succeeds; that does not demonstrate ART execution.
The full/default historical compilation of this unmerged patch is not claimed fixed.
The dedicated collector always requests the explicit targeted packaging profile.

## Isolated workflow and collection

`.github/workflows/lmtr-r4-writer-api35.yml` accepts only pushes to
`codex/r4-writer-api35-preparation`, with an exact repository/ref job guard, one job,
Ubuntu24.04, API35 x86_64 google_apis, no snapshots and both cameras disabled. It has
no matrix, dispatch, pull_request or schedule trigger. Historical R4/R3 workflows and
the published first-failure diagnostic are unchanged and do not match this branch.
Permissions remain `contents: read`; actions retain the proved 40-character SHA pins.

The first prepared execution is **fine metrics ON only**. The exact test remains:
`org.lmthermal.art.HtMemoryRuntimeTest#fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs`.
The same 50 baseline observations and 200 Full observations/codec, 40 ms scheduling,
queues, reservation/margin, Stop and assertions remain intact. A single method invocation
still covers STORED then DEFLATE and skips the latter after an unresolved earlier failure.

`runner.temp` is used only at step scope, independently in preflight, emulator collector,
always-host-seal and always-upload. All resolve to the identical run/attempt-specific
path outside checkout. Upload never relies on a successful preflight output. Host sealing
also runs on failure and records preparation/replay outcomes with HOST_SHA256SUMS.
No tool is installed automatically when Actionlint/ShellCheck is unavailable.
Context rules: https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability

`validate_lmtr_r4_writer_api35.py` reuses the proved serial-qualified orchestration:
clean exact checkout, reviewed source hashes, emulator/qemu/API/ABI/image verification,
APK hashes, one explicit test, raw instrumentation/commands/logcat, post-measurement
freeze of the isolated test package, original tar, bounded extraction and SHA manifests.
It adds the mode and compile-profile receipt. No operation may fall back to a physical
ADB target. These commands are prepared source; none was run locally in this review.

The unchanged independent Python analyzer checks all LMTR/oracle evidence, including
incomplete recovery, and preserves its A/B/C semantics. The new offline report checker
assesses fine-data vocabulary, exact-total accounting, omissions/interruption/overflow
and mode identity separately. Its quality result does not turn a JUnit success or a
finalized file into lossless throughput acceptance. Invalid/missing fine evidence makes
the collector exit 20 while retaining the original readback outcome as a separate field.
A coherent but limited report remains explicitly limited, not fabricated complete data.

Collect raw stress/run-result, fine sums and counts/types, coarse producer/codec timings,
heap/native/PSS and runtime GC samples, queues/drops/acknowledgments, first_failure,
terminal state, all complete/incomplete LMTR and oracles, archive and source/APK/image
receipts, Python verdicts and manifests. VM/process death can prevent finally/reporting;
preflight/emulator failure can mean no recorder archive ever existed. Always-upload
preserves available evidence, not imaginary missing files. Keep an unmodified original
GitHub artifact ZIP and compare its SHA256 against GitHub's digest when supplied; also
retain run/job/attempt metadata and logs in a new private persistent directory after
separate authorization. Existing preservation directories must never be overwritten.

## Defensible comparison, not executed

The test-only `lmtrWriterMetrics=on|off` argument controls **fine** callbacks/tables.
OFF keeps coarse metrics and their new checked aggregation enabled, identically to ON;
it does not recreate historical dbfab7e bytecode or remove every measurement. OFF records
an explicit mode and null fine report, not fake zero timings. Same APK/source bytes,
same workload and unchanged assertions are required for both arms.

Run43056135998 is historical context, not a matched control. A single new ON run verifies
packaging, ART compatibility and report usefulness only. It cannot estimate overhead.
A later separately approved comparison needs at least ON and OFF in fresh target
processes, on the same runner/image/APK/configuration. For an order-balanced finite
comparison predeclare two blocks, OFF→ON and ON→OFF (four method invocations total).
Preserve every arm, including early failures; no extra trials selected for favorable results.
Archive and verify each arm before resetting only the disposable emulator test package's
data; otherwise multiple stress directories would make the one-run analyzer ambiguous.
Do not use this procedure on a physical device. Do not force GC or alter memory/queues.

Prefer one hosted job per block for environment matching, but admit remaining host jitter,
filesystem cache, emulator thermal/runtime/profile state and baseline variation. Exact
runner image/JDK/emulator/system-image fingerprints and natural GC counters accompany
all arms. UUID/timestamp-varying files need independent payload/oracle parity, not assumed
whole-file identity. Compare producer cadence/baseline, retained observations and gaps,
queue peaks, Stop, heap/GC and fine timing quality separately. Normalize CPU/allocation
work by retained observations/decoded bytes as well as totals. No new numeric cadence
threshold or acceptance tolerance is introduced.

A performance candidate follows later with **one** change and the same instrumentation
in both reference/candidate arms. The historical fatal API35 investigation is separate:
controlled predecessor order/process state, gap-only CHUNKs and backlog/control-reserve
pressure. No isolated success explains that fatal retroactively.

## Next authorization boundary

Owner review must cover the inherited instrumentation plus this preparation patch,
the conditional test friendship, mode switch, final source hashes and trigger filters.
Only a new explicit authorization may permit a normal commit/push of this new branch,
one ON workflow invocation, evidence download/preservation and independent readback.
It would not permit OFF/order-balanced repetitions, optimization, historical-matrix runs,
changes to published branches, physical devices, merge or R5/R6. No such action is taken now.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
