# R4 bounded writer instrumentation — local review

Status: local instrumentation only, based on `dbfab7ee031275ba5251b00262f7aa9cfc3aa2c3`.
No performance optimization, new ART run, physical recording or throughput acceptance
is claimed. Historical result B and the earlier fatal API35 suite remain distinct evidence.

## Ownership and clocks

`RecorderInstrumentation` is an optional, default-disabled scalar observer on the existing
single writer. Its fixed `RecorderPhase` vocabulary and clock are separate from source,
intake, timer, acknowledgment and Stop witnesses. The default diagnostic clock is
`System.nanoTime`; a test clock can be injected without advancing recorder time.
It receives no body, payload, stream ID, throwable or stack trace. All clock/observer
exceptions are isolated; saturated exception counters remain visible in the report.
An exhausted runtime or a blocking/malicious observer is not made safe by this API.

The existing seal/codec observers retain their original boundary locations. Their wall
intervals now include some instrumentation overhead and must not be compared naively
with historical intervals. Fine metrics do not replace or rename historical report keys.

## Timing boundaries

All durations are wall-clock values, including scheduling and possible GC interference.
Only successful operations enter `completed`; an operation which throws enters
`interrupted`, even if it did not throw `InterruptedException`. A bad clock yields an
invalid sample (`elapsedNs=-1`) while retaining the operation outcome. Exceptions are
reported in `finally`, without replacing the original operation exception.

| Fine phase | Boundary | Included / excluded |
|---|---|---|
| `CHUNK_BUILD` | Original assembly function | Descriptor/context construction, payload/view checks, statistics generation, grouping, copies, codec, hashes and canonical JSON; excludes the first full CHUNK validation. |
| `CHUNK_FIRST_VALIDATION` | First `LmtrChunk.validate` call | Full schema, hashes, codec decode, Float32/mask/statistics and payload checks; no backend call. |
| `CHUNK_ENCODE_TOTAL` | Instrumented `RecordingChunk.encode` | Includes build and first validation, scalar counting and nested observer overhead. |
| `APPEND_CHUNK` | Entire `LmtrAppendSession.appendChunk` | Includes second full validation, journal state, backend CHUNK append, summary/acknowledgment, rolling index and possible automatic 32-CHUNK checkpoint. |
| `BACKEND_APPEND` | Entire injected backend call | Classified as HEADER/CHUNK/INDEX/CHECKPOINT/END/OTHER; includes all work performed by that backend. It is **not** disk-only or fsync-only. |
| `ENTRY_PROCESSING` | Dequeued Work processing | Context/facts decoding, context hash, segment/event creation and accounting, including any nested seal caused by this entry. Excludes dequeue/wait, preparation and subsequent idle timer checks. |
| `CHECKPOINT` | Engine's explicit checkpoint call | Includes its index/sync work. Automatic checkpoints inside append and the final checkpoint inside finish are not additional instances of this phase. |
| `FINALIZATION` | After the unchanged END guard through finish | Includes segment_end, final seal if needed, final checkpoint and END. Excludes backend close and reader reopen. A failed END guard never enters this phase. |
| `BACKEND_CLOSE` | Normal or failure-cleanup close attempt | Includes close exceptions; never adds a retry or moves cleanup before first-failure capture. |
| `READBACK` | Existing `check(full=false)` reopen | Structural finalization check, not the independent Python full readback. |

`CHUNK_BUILD` and `CHUNK_FIRST_VALIDATION` are disjoint subintervals of encode.
Encode and append are sequential disjoint operations for one successful seal.
`codec_wall` is nested in build/encode and must never be added again. Backend calls
are nested in append/checkpoint/finalization as applicable. Entry processing can
contain encode/append; finalization can too. Summing all phase totals would double
count work. The total encode interval also includes diagnostic overhead between its
subintervals, so build + validation need not equal encode exactly.

Append minus backend is **not** the exact second-validation cost: it also includes
state/summary/index work and diagnostic overhead. Per-record fsync, exclusive entry
bookkeeping, preparation latency, queue residence, END-guard/exception cleanup cost
and observer overhead are not independently timed. No unsupported attribution is made.

## Fixed aggregation and attribution

`FieldDistribution` retains the first N valid samples (default 8192; range 0..8192).
The historical `count`, `omitted`, `p50_ns`, `p95_ns` and `max_ns` keys remain compatible.
Quantiles use index `floor((n-1)*q)` over retained samples. `max_ns` is the retained
sample maximum; `max_all_ns` and minimum cover **all valid calls**, even after N.

New fields expose observed/valid/invalid/interrupted counts, exact sum and overflow.
Omitted means a valid sample not retained for quantiles; it still enters the sum and
extrema. Negative/unavailable times never enter these statistics. Empty/fully invalid
sets have null sum/extrema/quantiles. Sum overflow is sticky, exports null with
`sum_overflow=true`, and never pretends that a saturated Long is an exact sum.
Count saturation is separately flagged. Zero durations and zero known gap counts
remain valid data.

`FieldWriterMetrics` preallocates 6 backend-type cells and 10 phase cells, each with
completed/interrupted status and four fixed distributions: duration, body bytes,
observation entries and gap entries. One unused phase cell keeps indexing fixed.
At capacity 512 this is 128 LongArrays / **524288 bytes of sample storage**, plus
object/scalar overhead. The default existing metric buffers are separate. These
allocations occur before engine workers and the offered load. They are diagnostic
cost, not a reduction or increase of the recorder's 78 + 32 MiB policy or its queues.

Body size means the attempted CHUNK/backend **body**, without framing overhead; it
is not proof of bytes written when a call fails. Observation/gap-entry counts describe
the exact pending list for encode/append, not successful acknowledgments. A gap entry
may represent multiple missing observations. Backend timing does not parse the body
again to invent such attribution; unknown fields have zero attributed calls and null
sum/extrema. Future unknown record types share OTHER, never create dynamic buckets.

Updates use fixed scalar/sample storage. No event history, per-frame metric object,
payload buffer or growing identifier map is added. Existing per-distribution locks
only guard diagnostic data; no recorder lock ordering, wakeup, queue size, admission,
codec, hash, acknowledgment or timer policy is changed. JSON construction and sorting
allocate when reporting after worker termination, not during normal metric updates.
Snapshot generation is not an atomic live snapshot across all cells. The stress probe
reports fine metrics after Stop/worker termination. Diagnostic buffers and report
generation can still perturb heap, GC, scheduling and the next codec in the same
process; such effects require ART measurement rather than a claim of zero overhead.

## Local checks and exact-byte scope

The [local receipt](writer-instrumentation-validation.json) separates commands and
coverage from future ART work. Deterministic tests exercise:

- first-N omissions with exact full totals, empty/invalid/zero values and sticky overflow;
- concurrent aggregate updates and fixed record/status categories;
- nested exact fake-clock scopes, clock regression/overflow and clock exceptions;
- observer exceptions including an injected observer-only allocation error;
- failed assembly versus failed first validation and interrupted append;
- unchanged bytes for STORED/DEFLATE, native views and masked Float32 evidence;
- idle seals/checkpoints and no END after loss-reserve exhaustion.

`HtMemoryProbe <new-directory> --writer-metrics` is an opt-in regression helper only.
It uses the unchanged six saved-fixture observations, fixed engineering identities and
fake source clock. Comparing its complete LMTR files against the default mode and
preserved fixed-identity references is meaningful. Comparing arbitrary UUID/wall-time
runs for whole-file equality would not be. Independent Python checks retain full
payload/oracle, integrity, prefix recovery and source-immutability assertions.
No 200-observation cadence campaign was executed for this local change.

## Future ART comparison — not executed

1. Obtain separate authorization, review the full local patch and pin its exact source
   and APK hashes. Keep this branch uncommitted/unpublished until authorized. Review the
   publication branch and exact CI branch filter separately: the historical targeted
   workflow does not automatically select this new local investigation branch.
2. On Ubuntu 24.04, run only the existing API35 x86_64 google_apis targeted replay
   `org.lmthermal.art.HtMemoryRuntimeTest#fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs`
   in a fresh process, with cameras disabled: baseline 50, then 200 Full observations/codec at 40 ms,
   unchanged 4 MiB preferred chunk, bounded queues, memory guards, Stop and assertions.
3. Preserve original ZIP/digests, environment/image fingerprint, JVM/ART result,
   stress/run-result files, incomplete LMTR/oracles, first_failure and terminal counters.
   Fine metrics are in stress.json; an early crash can leave only earlier coarse
   receipts. `finally` is not guaranteed after VM/process death. Do not promise that
   always-upload creates files which the runtime never wrote.
4. Assess correctness, finalization, integrity, throughput and instrumentation quality
   separately. Invalid/omitted/overflow/observer-error fields must accompany all totals.
   Independent Python readback is required; zero-loss and cadence gates remain unchanged.
5. Only then compare an instrumented reference against an equally instrumented candidate
   with **one** performance change, identical load/configuration and a predeclared finite
   run order. Preserve every failure; no favorable reruns or forced GC. Compare work
   per retained observation/decoded byte as well as totals, heap/GC and lost measurements.

The historical fatal API35 suite needs a **separate** experiment: same preceding seven
tests/process state versus isolated replay, and analysis of gap-only CHUNKs, deadline
backlog and control-reserve pressure. A targeted success does not explain that fatal.

Possible later optimizations remain intra-validation digest reuse, fixed-regex
precompilation, bounded hex conversion and private JSON reconstruction/copy reduction.
None is implemented here; their effect on ART throughput remains unmeasured. Preserve
both complete validations and their independence, sync/ack ordering, exact evidence,
explicit gaps, refusal of END after unrepresented loss and all frozen fixtures.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
