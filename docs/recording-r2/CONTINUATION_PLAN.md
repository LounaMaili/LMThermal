# R2 throughput diagnosis — predeclared continuation, 2026-10-10

Base: `8a0d1ce0232f4a2f9c2eb6a911eae148c16fc051`, existing
`test/recording-r2-feasibility`. Prior failed evidence and packet remain intact.
No R1b/R3/product recorder or camera/thermometry change is authorized here.

## Before optimizing

Run sequential baseline, observe-only, Analysis freeze-only, freeze plus bounded
discard queue, full Analysis chunk/STORED/framing into a constant-space disposable
sink, real private-file writes without sync, and real private-file writes with
the existing body/footer sync barriers. Each has 10 s warmup and 60 s measurement.
The no-sync/discard ablations are explicitly nondurable and cannot pass storage
gates. Intake initially uses the unchanged legacy freeze/Default scheduler.
Record exact executable revision in each report before starting measurements.

Stage probes attribute wall/thread CPU to matrix/native/transport copies, F32
encoding, context build/encode/decode/hash, admission/encoding validation, role
aggregation/materialization, logical/block/framing hashes, codec, writes and sync.
Writer queue wait and inclusive service/encode totals are separate: do not add
inclusive totals to their components. Samples are bounded. Byte-array allocation
and copy counters are lower bounds; geometric BAOS reallocations are audited,
not mislabeled measured allocations. Android runtime allocation/GC counters are
process-wide. Sample presentation age, completion rate/render time, measurement
observation age and backlog together; one-second memory samples are observational.

## Ownership and existing copies

`transport.cpp::receivedFrame` copies a libuvc callback buffer into a mutex-guarded
native slot. JNI `read` creates/copies a new Java array before clearing that slot.
`Ht301Frame.parse` copies again into private storage. `measure` creates private,
independently allocated IntArray/FloatArray and retains the owned frame; parameters,
points/configuration/trace contain immutable scalar values. The module adapter
wraps this object without recycling it. Public matrix/raw/native/transport APIs
return copies; pixel/temperature getters only read private storage. Retaining a
`Ht301ThermalMeasurement` reference asynchronously is safe for this implementation;
this proof does not generalize to an arbitrary module or native callback buffer.

| Stage | Analysis | Native | Full | Classification |
|---|---:|---:|---:|---|
| `matrix()` | 442368 B | 442368 B | 442368 B | Defensive API copy; avoidable when serialization reads immutable evidence |
| F32 little-endian byte plane | 442368 B | 442368 B | 442368 B | Required format transform |
| `imageBytes()` | 0 | 221184 B | 221184 B | Native owns its plane; Full uses this only for view equality/hash validation |
| `transportBytes()` | 0 | 0 | 224256 B | Required owned Full transport extraction |
| Role BAOS write | Physical payload size | Physical payload size | Physical payload size | Required aggregation, geometric growth avoidable |
| Role `toByteArray()` | Same | Same | Same | Avoidable second materialization |
| STORED `copyOf()` | Same | Same | Same | Avoidable if writer-owned input remains immutable through write |
| DEFLATE output | Compressed size | Compressed size | Compressed size | Required codec transform; BAOS growth/final copy separately audited |

SHA-256 scans bytes but does not copy the complete input. RecordWriter already
writes immutable body parts separately; there is **no joined whole-body copy**.
JSON currently caches per-observation bytes, hashes them, decodes each new context
and encodes complete chunk metadata again. HT context includes dynamic sequence,
trailer extrema and centers, so deduplication by settings alone would be incorrect.
Two complete F32 validations run (admission and encoder). Reader adversarial checks
remain independent and mandatory. No internal trusted-skip is safe for mutable,
public Observation arrays without an explicit immutable ownership boundary.

## After evidence identifies the bottleneck

Use one dedicated preparation thread with a bounded immutable-reference handoff,
explicit overload and no priority boost or arbitrary pacing. Keep the writer's
4 MiB limit. Optimize only measured redundant transforms/aggregation/JSON/scans;
preserve Float32 bits, all roles, closure and every integrity domain. Prove source
immutability and byte equivalence, then repeat stage measurements as needed.

Presentation hypothesis: `collectLatest` plus exact source-identity checks can
cancel/reject every expensive render under a continuously changing valid stream.
This is starvation of publication, not evidence of an unbounded render queue.
Demonstrate it with controlled slow-render/source regression before a production
fix. Any fix must keep one render in flight and one conflated latest request,
invalidate on unavailable/session/device/settings changes, and never restore stale
Celsius after Close/dispose. It affects display only, never recorder admission.

## Sustained rerun and memory

Stage 1: matched baseline, Analysis-STORED, Full-STORED (20 s warmup/120 s each).
Stop escalation if STORED still drops steadily. Stage 2 only after plausible
STORED service: Native-STORED and all three DEFLATE profiles. Zstd remains deferred;
spot checks only if useful. No gate is relaxed from TEST_PLAN.md: actual valid
source stream, zero sustained writer overload, acquisition within 5%, fresh usable
image, truthful gaps, unchanged controls and 64 MiB incremental target.

Clean independent process runs for baseline, Analysis-STORED, Full-STORED and a
selected DEFLATE measure steady/peak/post-GC Java/native/PSS/RSS and explicit live
recorder-owned buffers. Report sampling limits and process allocation attribution;
do not infer a memory pass from bounds alone. Preserve private live files before
instrumentation cleanup. Recover/validate new output without altering old evidence.
If bytes change, rerun affected fault/integrity tests and regenerate the final
Android same-byte packet. Real Windows remains pending until actual execution.
Retain the test-only SAF system-bar fix and add launch regression coverage.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Diagnosis update before live ablations

A controlled Robolectric test reproduced the presentation hypothesis against the
unchanged pre-continuation production source: 100 ms matrix-copy accessor, source
arrival every 10 ms for 100 updates, **zero published renders during arrivals**.
The necessary presentation-only correction passes the same test, completing at
least three renders while arrivals continue, and a blocked-render/Close regression.
Original product/test APKs are retained in ignored local storage for the pre-copy-
optimization ablations. The writer/intake algorithm is still unchanged.

Wireless pairing was genuinely absent and was restored with normal operator pairing.
The original seven live ablations completed; their evidence and measured candidate
changes are recorded in [CONTINUATION_RESULTS.md](CONTINUATION_RESULTS.md).
