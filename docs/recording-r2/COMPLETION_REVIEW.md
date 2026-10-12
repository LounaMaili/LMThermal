# R2 completion review and integration preparation

Review date: **2026-10-12**. Reviewed published tip:
`6592c6455fc47a3caa6ca003938447c8b1788b28`, existing
`test/recording-r2-feasibility`; comparison base `origin/main`:
`5174b923bcc2011228d15d648f2766e941d8684e`. Installed/executed bounded-chunk
candidate: `531b8fd0653d503309f5def0899380a939b6f6d6`.

**The recommended HT-301/STORED/4 MiB DEFLATE/app-owned-file baseline is ready for
R2 closure after explicit owner integration/closure approval. No essential R2
feasibility test remains missing for that scope.** #7 remains open; #6 remains
open for R1b. This review does not approve wire semantics, merge code or start R3.
Unsupported configurations below are exclusions, not concealed passes.

## Gates A–F

The [acceptance checklist](../RECORDING_R2_VALIDATION.md) remains authoritative.
The [historical aggregate](reports/aggregate.json) and failed matrices are unchanged.

| Gate | Verdict | Exact evidence | Limits and consequence for R1b |
|---|---|---|---|
| A: Android throughput, preview and memory | **PASS WITH LIMITATIONS** | [4 MiB profiles](reports/android-continuation-deflate-4m.json), [clean Full-STORED](reports/android-continuation-full-stored-clean.json), [baseline/STORED](reports/android-continuation-stage1.json), [clean comparisons](reports/continuation-resume-summary.json), [cost/copy model](reports/continuation-cost-summary.json), [isolated thermometry](reports/thermometry-cost.json) | Finite 20 s warmup + about 126 s measurement per profile on Pixel 8/HT-301, not indefinite operation or another camera/ABI. Sampled process memory is not an absolute allocator bound. Recommend 4 MiB preferred DEFLATE chunks for this workload; negotiate larger-module memory separately. |
| B: lossless codecs and cross-platform bytes | **PASS** for the published packet/baseline | [Android production of packet](reports/android-final-packet.json), [final regeneration](reports/continuation-final-packet.json), [Linux](reports/linux-packet.json), [native Windows](reports/windows-packet.json), [device codec studies](CODECS.md) | Exact synthetic Float32/mask/native/transport/context parity, not physical calibration or accepted LMTR v1. Optional Zstd parity passes but successful optimized sustained Zstd is not established; product adoption stays deferred. |
| C: append-only chunks/index/limits | **PASS WITH LIMITATIONS** | [framing/recovery](FRAMING_RECOVERY.md), [Kotlin sparse >4 GiB](reports/sparse-kotlin.json), [Python sparse](reports/sparse-python.json), [short-chunk live readback](reports/continuation-resume-readback.json), `FramingFaultTest.timeCountByteAndFinalStopChunkPoliciesRemainBounded`, `PrototypeTest.indexPagesStayLazyAcrossHundredsOfChunks` | Paged/bounded navigation and no final payload rewrite demonstrated. Sparse large singleton used 229560320 B JVM heap; hard file ceilings are not a 64 MiB RAM promise. Time threshold is tested on observation admission, not an idle wall-clock flush timer. R1b must settle that policy and large-geometry admission. |
| D: interruption/recovery/failure | **PASS WITH LIMITATIONS** | [8 actual process kills on final executable](reports/continuation-short-chunk-process-kill.json), [4 live-file recovery copies](reports/continuation-resume-recovery.json), [physical detach](reports/android-first-detach.json), `FramingFaultTest`, `StorageTest.refusalOrSyncFailureAtMultipleChunkBoundariesPreservesEarlierCommits`, `StorageTest.failedCloseDoesNotReportSuccessfulStopEvenIfEndBytesSurvive` | Software/process-kill/sync evidence, not physical power-loss certification. Recovery suffix is limited to 64 chunks/1024 records and one damaged checkpoint interval; corrupt promised payload remains an error. No automatic writer resume. |
| E: restricted views/hostile inputs | **PASS** for tested prototype rules | [hostile/view matrix](HOSTILE_VIEWS.md), `PrototypeTest.invalidViewFormsContextAndHashAreRejected`, `OptimizedProducerTest.shorterChunksRetainEveryLogicalRoleAndCanBeSoughtIndependently`, `tools/recording_r2/test_reader.py`, Android/Linux/Windows packet | Same-frame/chunk contiguous native slice into materialized acquisition only; independent parent/logical hashes, checked shape/dtype/length and alternate 13×7 geometry. No approval of nesting/strides/transforms or a complete generic extension grammar. Hostile suites ran JVM/Linux; Windows ran the same reader's packet cases, not an additional full hostile corpus. |
| F: storage/publication/capacity | **PASS WITH LIMITATIONS** | [backend matrix](BACKENDS.md), [Downloads success](reports/android-saf-success.json), [cancel](reports/android-saf-cancel.json), [temporary grant loss](reports/android-saf-permission.json), Android packet read-only descriptor proof, `StorageTest.exportRequiresSuccessfulCloseAndExactReadback`, `StorageTest.unknownCapacityPermitsStartAndKnownInsufficientCapacityBlocks` | Recommend app-owned local canonical files. SAF proof is one 213579-byte local export with a 16 MiB probe ceiling; no multi-GB/remote/pipe/direct-SAF durability guarantee. External no-copy URI access and backup/restore are not demonstrated. Capacity knowledge does not establish durability. |

### Selected live configuration

Matched acquisition baseline: **25.000395 FPS**. All delivered valid measurements
were retained, with **zero preparation/writer loss**:

| 4 MiB DEFLATE profile | Acquisition FPS | Saved / delivered valid | Preview age p50/p95/max ms | Seal p95/max ms |
|---|---:|---:|---|---|
| Analysis | 24.999 | 3114 / 3114 | 85 / 109 / 117 | 75 / 86 |
| Native | 25.001 | 3156 / 3156 | 83 / 109 / 136 | 79 / 104 |
| Full | 24.997 | 3162 / 3162 | 85 / 117 / 203 | 93 / 140 |

Analysis has 41 explicit unavailable observations; Native/Full have none. Measured
unobserved/replaced/malformed counters are zero. The operator confirmed all previews
usable. Sampled backlog repeatedly drains; both preparation and writer queues stay
**4 MiB**, with one in-flight item in each stage accounted separately. The failed
16 MiB Native-DEFLATE run still records **31 writer losses** and six source losses;
its seal p95/max was 312/513 ms. It is not a recommended configuration.

Fresh-process baseline, Analysis-STORED, Full-STORED and selected Analysis-DEFLATE
comparisons are available. Observed steady/peak/post-GC differences are below the
64 MiB engineering target; largest steady difference is RSS **+53.35 MiB**. Owned
array-slot peaks are 24.05/34.76/10.88 MiB for those three recorder configurations.
Java/native samples are 100 ms; PSS/RSS samples 1 s. Separate process subtraction
includes runtime/camera/presentation variation and can miss short peaks. Slot
inventories exclude headers/text/codec scratch and can double-count references.
The earlier clean Analysis-STORED run overlapped backup load, explicitly disclosed.
Neither theoretical bounds nor these samples certify an absolute/indefinite limit.

## Cross-platform review

Android wrote the source synthetic packet and the final executable regenerated
all **15 `.r2proto` member hashes unchanged**. ZIP SHA-256 remains
`49cc244c60ca51b71c4d1ee38b4360e7e4bc8f490216ad4887f0fd1b770117cc`.
This review recalculated ZIP/member hashes and compared stored case rows; it did
not repeat codec, hardware or platform tests.

- Linux: 15/15 expected outcomes, reference Zstd 1.5.7.
- Native Windows 11/Python 3.12.10: baseline **10/10**, five explicit Zstd skips,
  no loaded Zstd library; separate optional run **15/15**, zero skips, preinstalled
  reference 1.5.7 with the pinned DLL hash in the Windows report.
- Executed Windows case rows exactly equal both published Linux reports, including
  source/logical hashes, counts, recovery classification, integrity errors and read
  counts. Lazy seeking and independent synthetic byte checks agree. Missing END
  remains incomplete; committed corruption rejects as `committed_payload_integrity`.

These are **noncanonical R2 synthetic vectors**, not R3 or final LMTR v1 fixtures.
Public reports, source/tests and the small packet permit review without private
scene files. Private live recordings remain preserved; they are not an implementation
specification or prerequisite for R1b decisions.

## Separate production presentation review

Only `app/src/main/java/org/lmthermal/app/CelsiusPresenter.kt` changes production
source against main. `app/build.gradle.kts` adds R2 only to `androidTestImplementation`
and test JNI sources; no recorder/Zstd product dependency or action is introduced.

| Check | Review conclusion / coverage |
|---|---|
| Latest rendering | One render in flight plus one conflated pending request. New valid frames replace pending work; a compatible older completed render can publish. This deliberately avoids exact-current-frame starvation; bitmap/range/readings share its original measurement. Recorder retention is independent. Controlled old/new reproduction and `CelsiusStarvationTest.continuingValidSourceMustNotStarveCompletedSlowRenders` support the change. |
| Close/unavailable/error | Collector increments generation and clears output; pre/post-render publication checks require valid current streaming state, no error and matching generation/key. UI also suppresses readings when source measurement is absent. Blocked-render Close JVM regression and ART unavailable/disposal tests cover stale-value restoration. |
| Source/settings invalidation | RenderKey includes module metadata, device, geometry and settings; current sequence/receipt must not precede the request. Rapid settings replacement is ART-tested. The generation is presenter-local, not an intrinsic session ID; direct blocked module/device/geometry replacement and rapid same-device reopen races deserve focused integration coverage rather than an unsupported exhaustive claim. |
| Cancellation/lifecycle | ViewModel owns/disposes presenter. `dispose` advances generation, closes channel, cancels scope/ROI and clears output. Cancellation propagates from rendering and `ensureActive`; synchronous matrix/sort/bitmap work need not stop instantly, but cannot publish after completed disposal. No USB lifetime or initialization change. |
| Other modules | Generic `ThermalMeasurement`/geometry/optional samples remain the only inputs. Existing ART 160×120 temperature-only test needs no HT raw samples; preview-only sources clear Celsius. Not validation of another commercial thermal camera. |

No demonstrated production regression was found in the reviewed intended lifecycle
paths. **Integrate the justified starvation fix separately**, with its JVM/ART
regressions and presentation documentation, and add the focused replacement/reopen
race coverage during that integration review. This is an integration assurance
follow-up, not a missing HT-301 codec/storage feasibility run. Do not copy the mixed
`9f9d609` commit wholesale; it also changes disposable recorder code.

## Integration recommendation

| Option | Assessment |
|---|---|
| Merge entire feasibility branch | Runtime recorder isolation is real, but 105 files include disposable APIs, benchmark harnesses and 37k lines of evidence/tooling. Makes experimental mechanics look like the implementation baseline. Not recommended as the default integration. |
| Keep evidence branch and ad hoc selective integration | Preserves evidence, but mixed commits and loosely selected patches make provenance/coverage harder to review. Better than importing prototype APIs, but needs a defined durable file set. |
| Focused reviewed integration branch(es) | **Recommended.** Stage durable documentation/evidence integration, then a separate focused presentation-fix change with its tests. Keep R2 tools/source/evidence branch intact at pinned commits; exclude the R2 Gradle module, Android test recorder/probe/JNI and disposable reader/writer APIs from production integration. Owner approves each merge. |

No integration branch or merge is created here. The final R1b specification should
link this review, exact framing proposal and pinned public numeric/Windows evidence.
Preserve branch/history and packet labels; do not delete or rewrite failed results.

## Documentation corrections

| Files | Correction |
|---|---|
| `FRAMING_PROPOSAL.md`, `FRAMING_RECOVERY.md`, `CODECS.md`, `../COMMON_RECORDING_DESIGN.md` | Replace stale universal 16 MiB recommendation/unproven-memory wording with the tested 4 MiB preference and measured sampling limits; distinguish default, queue, hard bounds and idle flush policy. |
| `../RECORDING_IMPLEMENTATION_PLAN.md`, `../RECORDING_R2_VALIDATION.md`, `../LMTR_FORMAT_CANDIDATE.md` | R2 baseline reviewed; owner integration/closure and complete R1b approval still pending. Preserve accepted R1a semantics and all original gate criteria. |
| `ANDROID_BENCHMARK.md`, `HOSTILE_VIEWS.md`, `REGRESSION.md`, `CONTINUATION_RESULTS.md`, `TEST_PLAN.md`, `CONTINUATION_PLAN.md` | Label historical failure/pending snapshots and link current review; do not rewrite original plans, numerical results or failed evidence. |
| `../ANDROID_CELSIUS_PRESENTATION.md` | Completed finite pressure validation and separate production-fix integration/coverage boundary. |
| `README.md`, repository `README.md`, `WINDOWS_VALIDATION.md`, tooling `README.md`, `CHANGELOG.md` | Current review/evidence index, scoped Windows completion, integration recommendation and review provenance. |

This review adds [machine-readable verdicts/consistency checks](reports/completion-review.json).
Application, prototype, packet bytes and existing numeric reports are unchanged.

## R1b owner handoff — ready to prepare the complete contract

| Decision requiring explicit approval | Evidence-backed input / still unresolved |
|---|---|
| Identity/version | Separate `.lmtr` / `lmthermal-recording` / `kind=recording`; final container/schema versions and compatibility rules remain provisional. |
| Magic/header/footer | Tested 40-byte LE header/72-byte footer, checked nonnegative signed-64 domain, CRC/SHA coverage and sync-before-ack order. Proposed `LMTRREC1`/`LMTRCMT1` are not emitted/tested release bytes; select final flags/types/optional-record policy. |
| Complete generic JSON | Specify every required/optional field, types/defaults, IDs/references, availability, mask/filler/statistics, context closure, provenance/privacy, unknown optional value precision/binary preservation and version negotiation. Prototype is a radiometric subset, not the complete camera-independent visual/preview-only/multistream/extension grammar. |
| Clocks/sequences | Define acquisition/host/relative/UTC domains, units/resolution/unknown quality, creation/finalization, sequence resets/segments, gap timing/count authority and scheduling. R2 live uses millisecond host receipt expressed as exact ns strings; detection bounds are not invented sensor timestamps. |
| Features/extensions | Final names, scopes and required/optional preservation/rejection rules, including recovery. Prototype rejects unknown required features/types; comprehensive future optional extension compatibility is not demonstrated. |
| Codecs | Recommend mandatory STORED and RFC1950-wrapped DEFLATE decoders; encoder level 1 is tested. IDs remain provisional. Keep Zstd optional/deferred; successful optional parity is not production adoption. |
| Restricted views | Only same-frame/chunk contiguous native into materialized acquisition, checked bounds/shape/dtype/endian and independent logical/parent hashes. Generic modules need not use views; do not require HT geometry, raw14 or transport globally. |
| Preferred/hard/memory limits | Recommend **4 MiB preferred chunks for tested HT-301/DEFLATE**, separately from two 4 MiB queues and the provisional 64 MiB decoded+metadata/65 MiB record ceilings. Prototype default still 16 MiB. Approve generic per-module admission/decoder budgets and idle flush policy; sampled 64 MiB incremental target is not a universal hard bound. |
| Index/checkpoint/recovery | Approve 256-child/depth-8/512 KiB pages, checkpoint cadence, validated prefix/suffix limits, missing-END/unknown-tail rules and corruption classification; no implicit source repair/resume. More severe damage and generic resource negotiation remain limited. |
| Backends/publication | Initially app-owned local canonical files with scoped filesystem/API sync assurance; read-only access is not backup. Local SAF small verified export only. Decide retention/backup/privacy/URI lifetime and any later large-file/direct/remote provider validation independently. |

R1a full-grid/exact Celsius/lossless evidence, truthful gaps/clocks, immutable source,
profile choice and coexistence invariants remain accepted. LMTX v1 is unchanged.
These choices must become a complete camera-independent Git-tracked normative
contract with owner acceptance before R3. No acceptance is inferred from R2 review.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
