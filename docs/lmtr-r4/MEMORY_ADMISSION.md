# R4 memory admission diagnosis

Status: investigation; no reduced admission requirement or physical recorder acceptance.
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

Physical smoke remains conditional on actual ready-state witnesses, safety, ART and
host gates. If another admission refuses, preserve its exact witness and stop.

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
not grounds to lower 12× scratch or16× control. Generic/jumbo metadata and all transient
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
20 samples at100 ms to a new app-private `files/r4-memory/<UUID>/health.jsonl`. Labels do
not imply readiness: each sample carries actual lifecycle, measurement availability,
health/refusal and process metrics. It never connects, initializes or starts a recorder.
Fresh-process isolation is deliberate and finite, not repeated relaunches for lucky GC.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
