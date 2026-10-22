# R4 memory admission diagnosis

Status: ready-state refusal reproduced; no safe reduction of admission established and no physical LMTR created.
[Exact-SHA memory receipt](memory-admission-validation.json) records all actual/fixture/ART evidence separately.
The [previous refusal](field-unattended-validation.json) remains historical evidence.
[Field protocol](FIELD_VALIDATION.md), [source ownership](SOURCE_VALIDATION.md) and
[R2 review](../recording-r2/COMPLETION_REVIEW.md) retain their separate scope.

## Witnesses and limits

Runtime `maxMemory` is the Java ceiling; `totalMemory - freeMemory` is occupied heap,
including uncollected garbage, **not measured reachable live heap**. Headroom is their
sampled difference. Native codec/JNI and graphics/bitmap memory, RSS/PSS and logical
recorder reservations are separate. No forced GC or RSS-to-Java inference is used.
The original refusing sample was not retained; no later sample reconstructs it.

Existing APK 30a2f69 was used first, in a verified display/nonmeasurement stream:
maximum 268435456 B, occupied 18432192 B, headroom 250003264 B; RSS 247869440 B.
The 110 MiB memory condition passed, then the existing readiness gate refused Start.
No engine/file was created. The USB chooser initially opened the production app;
normal explicit field Connect subsequently succeeded without a new prompt. The
empty dumpsys permission section was not proof that normal USB open would fail.
This connected display sample does not explain the earlier Celsius-mode refusal.

New read-only sampling on recorder code `be41db4` and field APK SHA-256
`af80253b93ee8e3613d9eed74399c778fb7fffa8dcbd4483391e85b4773fa6a5`
used one deliberately fresh process, without forced GC/relaunch retries. The Pixel had
normal field USB access, an authorized non-sensitive scene and thermal status 0.

| Phase | Actual samples | Occupied Java minimum/maximum (B) | Minimum headroom (B) |
|---|---:|---:|---:|
| Fresh process |11/20 (partial)|4724608 /26408240|242027216|
| Foreground idle |20/20|6819488 /39026336|229409120|
| Connected/display (includes opening)|20/20|7151808 /96383168|172052288|
| Actually RADIOMETRIC_READY |20/20|16761024 /228847808|39587648|

Java maximum is 268435456 B (256 MiB). **Nine of 20 ready samples fail the unchanged
115343360 B requirement**; the worst occupied snapshot is218.25 MiB with37.75 MiB
headroom. This reproduces the refusal condition before any recorder is created.
Two natural GCs occurred in the ready window and allocation/free counters advanced.
The wide occupied range is consistent with allocation/GC churn; no exact reachable
live-heap bound, leak diagnosis or individual presentation/thermometry attribution
is established. This window evaluates admission read-only; it is not a second
attempt to create a recorder or a successful admission chosen between GCs.

Ready-phase separately sampled peaks: native allocated 44977696 B, RSS 480600064 B,
PSS 420301824 B. Component peaks need not be simultaneous and must not be added.
Graphics/bitmap peak is not separately measured. The fresh window is partial with
cause not established; its original 11 samples are preserved, not promoted to a full
window. A follow-up field-only diagnostic patch adds explicit completed/cancel/error
receipts; it was build/lint checked without repeating that window or physical tests.

No 20-second real file or source oracle exists; no original current-camera byte parity,
recording throughput, queue/memory stability or recorder Stop/recovery is certified.
The normal Close again left zero active owners. No long run, forced GC, favorable-GC
selection, capacity increase, margin reduction or profile downgrade followed. The
copy optimization below cannot fix pre-encoder source/presentation heap occupancy.
Further physical work requires a credible source/Java live-allocation/admission proof
while retaining the safety margin and evidence. Camera/thermometry were not changed.

## Allocation audit (unchanged 78 MiB accounting)

| Category | Implementation/lifetime | Qualification |
|---|---|---|
| Preparation + writer queues | 4 MiB each, explicit charges | Simultaneously resident; do not increase |
| Two in-flight offers | 3 MiB each | Retained immutable source/prepared closure, not every transient freeze buffer |
| Pending/encoding/validation | 12 × max(4 MiB preferred,3 MiB observation) =48 MiB | Conservative accounting; includes pending ownership and temporaries; not measured RSS |
| Control/metadata | 16 ×1 MiB =16 MiB | Frozen JSON, event journal, bounded index pages/frontiers and decode metadata; not preallocated arrays |
| Additional headroom | 32 MiB | Runtime Java and system admission margin; unchanged |

A ready HT owner retains 224256 transport bytes +442368 indices +442368 Float32
matrix =1108992 array bytes, excluding object/header/lookup/metadata overhead.
Adapter freezing can also hold matrix copy, Float32 encoding buffer, constructor-owned
copy, raw image copy/owner, transport copy/owner and canonical JSON. The 3 MiB offer
is an ownership/admission charge, not a claim that every transient copy fits in 3 MiB.

Before this change, RecordingChunk grouped `payload.bytes()` copies for all materialized
payloads and separately copied temperature/mask for cache statistics. The writer now
hashes/copies/statistically inspects retained immutable owners via internal operations
that never expose their arrays. Public bytes() and constructor defensive copies remain.
No source, native view, unknown JSON, Float32 bit or validation is discarded.

Remaining arrays: grouped concatenation; codec output (STORED owned copy or DEFLATE
BAOS growth/output +64 KiB scratch and native zlib state); accumulated stored blocks;
JSON bytes/body; decoded blocks; logical/view slices during validation. Both encode and
append perform complete validation. Those validations are sequential; their previous
unreachable outputs can still occupy the heap until normal GC. Checkpoint/index work
is bounded by fanout/depth/body admission, not duration-sized payload lists. Stop clears
pending ownership before terminal read-only reopening; a failed preparer must exit
before its source/oracle reservation is reused.

The relevant inventory depends on actual logical/stored block sizes, JSON object
expansion, JVM/ART liveness and native codec allocations. Eliminating a proven copy is
not grounds to lower 12× scratch or 16× control. Generic/jumbo metadata and all transient
allocator overhead are not certified by a six-observation sample. All original factors
and safety limits remain unchanged until a stronger admission proof exists.

## Reproduction (no camera)

`HtMemoryProbe` uses the existing sanitized 384×288 fixture, six observations/case
(one unavailable then five ready), all three profiles and codecs 0/1. Fixed synthetic
identities/ticks permit exact baseline/candidate artifact comparisons. This is not an
actual camera source clock or throughput benchmark. It checks six acknowledgments,
zero preparation/writer drops, original owner hashes, and unchanged 4 MiB queue bounds.
Occupied heap sampling every approximately5 ms can miss brief peaks and is not a
reachable-heap measurement or allocator certificate; Android additionally samples native
allocated/PSS separately. Neither these samples nor R2 certify indefinite runtime.

The isolated API26/API35 workflow preserves every new memory-* directory and performs
independent Python readback per profile/codec. The Pixel camera-free route remains separate
from physical LMTR recording. All engineering outputs stay outside the canonical corpus.

Read-only field sampling uses action `org.lmthermal.field.SAMPLE_HEALTH` and phase extra
`fresh_process`, `foreground_idle`, `connected`, `ready` or `before_admission`. It writes
20 samples at 100 ms to a new app-private `files/r4-memory/<UUID>/health.jsonl`. Labels do
not imply readiness: each sample carries actual lifecycle, measurement availability,
health/refusal and process metrics. It never connects, initializes or starts a recorder.
Fresh-process isolation is deliberate and finite, not repeated relaunches for lucky GC.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Executed host/ART checks

- 405 JVM tests;64 Python contract tests; app/field build and lint pass. Admission refusal
  regressions still check insufficient Java/system heap, storage, permission and lifecycle.
- Six old/new full-size engineering files are **byte-identical**, with12 independent
  Python original-owner readbacks. The private pre-edit recorder JAR and all outputs remain.
- Six sequential cases per host process have GC-dependent occupied peaks; some candidate
  peaks are higher. Do not claim uniform memory improvement from these samples. The proven
  benefit is eliminating specific duplicate arrays without changing encoded bytes.
- The original unchanged fixture oracle independently checks Float32, native/acquisition,
  same-observation views, unavailable states, source facts/context/intake and immutability.
  It is fixture evidence, not current physical source parity or calibrated temperature.
- Actual Pixel camera-free ART:12 tests, six full-size case files independently checked.
- Exact code `be41db4`: [API26/API35 run 38562284281](https://github.com/LounaMaili/LMThermal/actions/runs/38562284281)
  passes 88/88 tests, retains six memory cases/readbacks per API and all prior engineering outputs.
- The frozen wire and 51 pinned canonical fixtures remain unchanged. #9 stays OPEN and
  PR #16 DRAFT/unmerged. R4 is not finished; R5/R6/R7 and Desktop are untouched.

Recorder Start is refused while a read-only health window remains active; its no-recorder provenance and admission timing cannot silently overlap another explicit recording action.

## Follow-up: pipeline allocation versus reachable owners

[Live-heap investigation](LIVE_HEAP.md) records finite host/Pixel replay, isolated diagnostic GC and exact byte-preserving removal of two calibration transport copies and the liveness SHA input copy. These results do not establish a safe admission policy; 78 MiB +32 MiB, queues and both validation boundaries remain unchanged. Original actual-camera receipts above are historical and are not replaced by replay evidence.

[Targeted presentation receipts](PRESENTATION_PERFORMANCE.md) add real Auto/ROI headroom witnesses, the partial failed Compose window and the completed corrected four-window repeat. The110 MiB requirement and both validation boundaries are unchanged; fixture speed/allocation savings are not safe admission proof.

## Incremental recorder follow-up

[Recorder allocation engineering](RECORDER_ALLOCATION.md) separates actual buffer lifetimes from logical admission. The previous checkpoint excluded physical device access. Resumed conditional authorization and the still-pending safe admission/physical gates are recorded there.
