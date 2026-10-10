# R2 measured continuation — 2026-10-10

**Noncanonical test prototype. R2 remains open; no R1b/R3/product recorder.**
The [original failed matrices](ANDROID_BENCHMARK.md) and old packet are preserved.
This continuation follows the [predeclared plan](CONTINUATION_PLAN.md).

## Original-path ablations

[Complete numeric report](reports/android-continuation-ablations.json).
Installed source `bec70991ef7b76707bed150d0e687fe86381168a`; original intake,
chunk/writer and production presentation behavior. A transcription typo in the
instrumentation revision argument (`bec7099f`) is retained and explicitly corrected
in the report provenance; preserved APK hashes identify the executed bytes.
Each stage: 10 s warmup, GC, approximately 62 s for 60 measured intervals.
Private scene files and reports are backed up outside Git; no private scene fixture added.

| Stage | Acquisition FPS | Accepted valid | Writer drops | Render FPS | Display age p50/p95/max ms |
|---|---:|---:|---:|---:|---|
| Baseline | 24.988 | Not observed | 0 | 10.77 | 102 / 184 / 251 |
| Observe | 24.988 | 1562 | 0 | 10.77 | 99 / 201 / 299 |
| Freeze | 25.007 | 1562 | 0 | 12.74 | 90 / 151 / 175 |
| Queue | 24.992 | 1564 | 0 | 11.82 | 102 / 166 / 291 |
| Chunk/STORED, discard sink | 24.988 | 1572 | 137 | 12.19 | 94 / 241 / 688 |
| File without sync | 24.992 | 1582 | 204 | 12.82 | 95 / 305 / 477 |
| Durable file | 24.999 | 1575 | 219 | 12.32 | 102 / 350 / 433 |

Baseline deliberately had no recorder source observer. Its zero source-invalid/
sequence counters mean not observed. Committed entries include gap ranges and
must not be described as saved matrices. These short original-path runs stayed
fresh; the historical multi-second presentation failure is not relabeled as
occurring in these new runs. Discard/no-sync cannot pass durability gates.

### Cost model

Freeze-only per valid frame: matrix copy 0.470 ms wall / 0.436 ms thread CPU;
per-pixel Float32 serialization 7.800 / 7.376 ms; context encode 2.760 / 2.598 ms;
inclusive freeze 11.338 / 10.687 ms (p95 approximately 21 ms).

Discard-sink per accepted frame wall: admission validation 9.301 ms, second
producer validation 8.608 ms, context decode 0.751 ms, metadata encode 6.845 ms,
role aggregation 0.385 ms, materialization 0.137 ms, STORED copy 0.138 ms;
logical/block/framing hashes 0.262 / 0.238 / 0.233 ms. Inclusive writer service
26.797 ms; **chunk encode p95 535.69 ms**, queue wait p95 420.73 ms. Do not add
inclusive totals to component costs.

No-sync chunk encode p95 597.64 ms, durable p95 655.76 ms. Durable file writes
cost 0.196 ms per accepted frame (call p95 4.65 ms); sync 0.791 ms per accepted
frame (call p95 22.81 ms). **Storage alone is not the root**: discard already
loses frames. Monolithic validation/JSON sealing bursts exceed the tolerance of
the unchanged 4 MiB queue even when average service is below 40 ms/frame.

R2 JSON encoded every string using a newly allocated charset encoder and parsed
its entire completed output into a discarded tree. Chunk metadata then decoded
cached contexts and encoded/parsed the complete tree again. Dynamic HT sequence,
calibration/centers/extrema remain complete; settings-only dedup would be wrong.

## Measured-path candidate

- `OwnedObservation.ht301` is the sole private trusted producer factory. It retains
  the existing proven immutable measurement asynchronously; exported arrays/maps
  are never available to a public mutating caller. Core already establishes finite
  raw14 temperatures. Generic mutable observations still validate at admission
  and chunk encoding; hostile reader checks are unchanged.
- One nonblocking immutable-reference handoff to `r2-preparation`, byte-bounded
  at 4 MiB based on retained source frame + indices + temperatures (1108992 B per
  source), one in-flight item. Preparation overload is explicit and separate
  from source gaps and writer loss. No priority boost or per-frame coroutine jobs.
- Bulk little-endian FloatBuffer transform preserves exact Float32 bits. Public
  matrix copy remains a deliberate API ownership boundary; no core API change.
- One exact allocation/fill per role replaces BAOS growth/final materialization.
  STORED consumes the private owned role block through append without a second
  copy. Defensive decode remains. RecordWriter already writes body parts without
  a joined whole-body array. DEFLATE allocation behavior is separately measured.
- Full uses the proven source image prefix directly for its view/hash, avoiding
  extraction and equality scanning of an otherwise redundant native plane.
- Direct JSON encoding establishes UTF-8/surrogate/string/depth/item/byte/numeric
  invariants without a discarded parse tree. Private built contexts are reused
  directly; generic callers still snapshot by decoding cached bytes. Precision
  and all dynamic fields remain. Every logical, block and framing digest remains.

### Full-frame copy lower bounds, STORED

| Profile | Original B/frame | Candidate B/frame |
|---|---:|---:|
| Analysis | 2211840 | 1327104 |
| Native | 3096576 | 1769472 |
| Full | 3330048 | 1775616 |

Includes defensive matrix copy, required F32 transform, native/transport extraction
where applicable and role fills; original also includes role final copy and STORED
copy. Excludes original geometric reallocations, JSON, codec scratch and object
headers. SHA scans do not copy whole inputs. These are audited lower bounds,
not isolated allocator or process-memory measurements.

## Presentation root and correction

Controlled slow-render/source regression against unchanged production source:
100 ms matrix accessor, 100 source updates at 10 ms, **zero completed published
renders during arrivals**. Exact identity plus collectLatest cancelled/rejected
ongoing renders. There was no evidence of an unbounded render FIFO.

Necessary presentation-only correction uses one in-flight render and one conflated
latest request. Compatible newer valid sources allow publication; generation/key
invalidation on unavailable/Close/dispose/device/geometry/settings rejects obsolete
results. Identical controlled regression now publishes 10 during arrivals; blocked
render followed by Close cannot restore Celsius. Recording admission is independent.
Sustained freshness under actual recording pressure still requires the candidate run.

## Gates at candidate preparation

Private-producer fixture tests preserve exact Float32/native/transport and complete
context, source immutability and defensive generic validation. JSON hostile bounds,
STORED ownership and preparation-thread/drain regressions pass. Production camera,
thermometry, controls, coordinates and cadence are unchanged.

Staged sustained candidate, clean incremental memory and actual final Windows
execution remain pending. Earlier recovery/sparse/view/provider evidence remains
available; affected integrity/fault checks will be rerun on final candidate bytes.
Test-only SAF layout fix remains; camera-free launch regression is available.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
