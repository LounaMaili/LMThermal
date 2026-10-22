# R4 pipeline allocation and reclaimability investigation

Status: finite camera-free attribution and a byte-preserving allocation correction;
**safe physical recording admission is still not established**. Historical real-camera
refusals and private originals remain in [memory admission](MEMORY_ADMISSION.md).
[Machine-readable receipt](live-heap-validation.json) separates replay from physical evidence.

## Allocation attribution

`PipelineHeapProbe` replays the existing sanitized 384×288 room frame through unchanged
`MeasurementGate.evaluate` and native-equivalent math. Three successive 120-frame
windows cover acquisition, acquisition + Auto colors, and acquisition + Auto colors +
full-image ROI + Android bitmap. ART pacing requests 25 Hz; this is not USB cadence,
Compose/presenter scheduling, preview freshness, a live scene or recorder admission.
The first comparison predates duration counters; a separate final receipt measures
window duration/deadline misses. Neither comparison selects a favorable physical GC.

Host thread allocation counters attribute the synchronous operations. They include
counter overhead; first/last SHA checks and runtime probes are outside attributed
operations. ART uses process-wide allocated/freed bytes and natural-GC counts, which
include instrumentation/runtime overhead and cannot be attributed to individual threads.
ART PSS/native snapshots are separate, not an additive Java/graphics decomposition.

| Host operation | Before, bytes/frame (steady phases) | After, bytes/frame | Qualification |
|---|---:|---:|---|
| Parse, inspect, image SHA, native-equivalent measurement | 1,847,928 | 1,178,184 | Fixture, no UVC callback/Compose; exact Float32 hash unchanged |
| Auto renderer and public ARGB copy | about 1,769,720 | about 1,769,720 | Matrix copy + sorted copy + color array + defensive ARGB copy remain |
| Full-image ROI | about 443,176 | about 443,176 | Independent copied matrix; unchanged statistics |

The proven saved array payload is **669,696 bytes/frame**: two 224,256-byte transport
copies used by parameter decoding plus the 221,184-byte liveness image copy. Host
allocation counters differ slightly because of headers/runtime warmup. At an assumed
25 frames/s the eliminated payload churn is 16,742,400 bytes/s; this arithmetic is
not measured real camera throughput. LUT, indices, Float32 matrix, source transport
ownership, public copies, ROI and palette behavior remain intact. Do not cache a LUT
without a separate exact-input proof or recycle arrays that immutable evidence retains.

## Reclaimability, not leak inference

Each replay deliberately retains three immutable measurement generations, with weak
references to the other 117. After the measured phase a **single deliberate diagnostic
GC** is requested. It is available only in test code; no physical admission, field
Start or production code can request it through this probe. All measured Pixel windows
retained exactly those three observed measurement owners after this experiment, with
about 6 MB occupied Java remaining. This supports reclaimability of fixture generations,
not a global reachable-heap bound or proof about actual Compose/lifecycle references.

First paired Pixel ART replay observed occupied peaks (before → after correction):
- Acquisition: 151,872,224 → 98,661,088 bytes.
- Acquisition/colors: 261,726,944 → 133,657,312 bytes.
- Acquisition/colors/ROI/bitmap: 256,762,592 → 202,482,400 bytes.

These are finite snapshots with process/runtime history effects. The third corrected
window still leaves less than the unchanged 110 MiB requirement at its worst snapshot
in the 256 MiB ART process. Lower allocation rate **does not certify safe admission**.
The final serial timing probe took 4.807 /6.030 /7.051 seconds for 120 frames
(acquisition /colors /colors+ROI+bitmap), with20 /120 /120 missed requested40 ms
deadlines. These are retained limitations, not a25 FPS pass. Synchronous fixture
measurement+sort+ROI plus diagnostic overhead is not the concurrent production
pipeline; no timed before comparison exists and no product FPS regression/improvement
is inferred. A full workflow still needs matched physical cadence/freshness validation.

The bitmap case adds one retained Android bitmap; renderer pixels are Java arrays,
while bitmap/native/graphics allocation attribution remains incomplete. No heap dump
or scene/object graph is published.

## Ownership and lifecycle audit

`Ht301AcquiredObservation` retains the already-owned transport, indices and Float32
matrix (1,108,992 array bytes per ready owner); its thermal adapter is a wrapper, not
another matrix copy. An explicitly subscribed observer may retain this immutable owner.
The field observer without a recorder collects counters, not a historical frame list.

Controller, module `stateIn` and common owner flows each retain their latest snapshot;
wrappers may share the same measurement but asynchronous mapping can briefly retain
other generations. `CelsiusPresenter` has one in-flight render and one conflated pending
request plus a completed snapshot. Its ROI worker may hold another displayed owner.
Settings/session invalidation and owner-generation guards are unchanged. Close clears
common measurement/preview, cancels the collector, awaits source shutdown, and disposes
presentation with its ViewModel. Source replacement does not imply immediate Java GC.
These code-level bounded roles are not a measured global owner count for a running UI.
No leak or exact live-camera allocation attribution is claimed from occupied samples.

## Recorder overlap audit and admission decision

The previous [48 MiB scratch / 16 MiB control audit](MEMORY_ADMISSION.md#allocation-audit-unchanged-78-mib-accounting)
remains valid. Two 4 MiB queues, two charged 3 MiB in-flight offers and pending payloads
can coexist. A charged observation is retained ownership, not a bound on temporary
freezing copies. Concatenation, encoded blocks, JSON/body and encode-time validation
can overlap; append-time validation runs later but the first pass's unreachable arrays
can occupy heap until GC. Both full validations remain mandatory. Index/checkpoint
pages and terminal reopen are bounded separately, with object/JSON expansion and codec
native allocations still not certified by a short fixture run.

**No reservation or admission change:** 78 MiB estimate +32 MiB margin, original queue
capacities and Full roles. No `largeHeap`, new process/service workaround, forced
production GC, profile downgrade or favorable-GC retry. The acquisition allocation
correction alone supplies no conservative simultaneous-live + allocator/GC peak proof.
No camera was opened or initialized in this continuation. No physical LMTR/source
oracle, Stop/recovery result or sustained profile was produced. The next gate is a
credible live source/presentation and incremental-recorder allocation/admission proof,
then the conditional single Full smoke and its original-owner Python validation.

## Validation and reproduction

- 407 complete JVM tests (234 core,71 contract,39 recorder,17 bridge,25 field,21 app),
  64 Python tests, app/field build and lint pass.
- Pixel camera-free paired replay and 13 focused tests pass; the final duration-only
  diagnostic is retained separately. Six new host and six Pixel fixture artifacts
  pass independent Python original-owner readback. The six host files are exactly
  byte-identical to the preceding checkpoint's artifacts, not just reader-equivalent.
- Accepted wire and 51 canonical hashes remain unchanged. [Exact-code ART run38573024599](https://github.com/LounaMaili/LMThermal/actions/runs/38573024599)
  passes89/89 on each of API26/API35, including the replay probe and six original-owner
  fixture readbacks each. Runtime logs/source SHA were inspected and preserved locally.

Host: build `:lmtr-field-support:writeTestRuntimeClasspath` using the established host
Gradle settings, then run `org.lmthermal.field.PipelineHeapProbe <new-private-directory>`
with that classpath and `-Xmx256m`. ART: the existing camera-free harness includes
`org.lmthermal.art.PipelineHeapRuntimeTest`. Outputs use unique private directories;
no fixture/canonical output is overwritten and no USB permission is requested.

PR #16 stays DRAFT; #9 stays OPEN. R4 physical acceptance remains pending.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Updated R4 acceptance matrix

| Gate | Current status | Evidence boundary |
|---|---|---|
| Immutable source/encoded fixture parity | PASS for tested fixtures | Six entire files unchanged; independent host/Pixel/ART original-owner checks |
| Source allocation correction | PASS for specific redundant copies | Same bytes/numerics/digest;407 JVM regressions and ART execution |
| Synthetic reclaimability attribution | Observed, finite | Three retained weak owners after separate diagnostic GC; not a UI leak certificate |
| Actual full UI simultaneous-live/admission proof | PENDING | Historical camera refusal plus replay cannot certify allocator/GC peak |
| Physical20-second Full smoke and source-byte parity | NOT RUN in this continuation | Memory safety prerequisite unresolved; no physical output |
| Matched sustained cadence/freshness/memory and recorder lifecycle/recovery | PENDING | Cannot advance from an unvalidated physical smoke |

The documentation-only final receipt commit does not change the runtime-tested code
at8e928b22864c8cb3878c436f17deba069f28936f. Historical failures remain preserved.

## Targeted presentation follow-up

[Measured presentation/ROI work](PRESENTATION_PERFORMANCE.md) retains the rejected exact selector, byte-preserving optimizations and completed finite corrected Compose windows, with the original failed session preserved. Real memory policy witnesses still refuse; no physical recording or safe combined-memory admission is claimed.

## Incremental recorder follow-up

[Recorder allocation engineering](RECORDER_ALLOCATION.md) separates actual buffer lifetimes from logical admission. The previous checkpoint excluded physical device access. Resumed conditional authorization and the still-pending safe admission/physical gates are recorded there.
