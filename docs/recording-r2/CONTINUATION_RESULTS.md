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
must not be described as saved matrices. Independent read-only validation of
the original no-sync/durable files confirms 1378/1356 saved matrices and
59/57 gap-range entries, exact payload integrity and source immutability
([report](reports/android-continuation-original-files.json)). These short original-path runs stayed
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
The sustained candidate evidence below tests freshness under actual recording pressure.

## Candidate regression and completed STORED stages

Private-producer fixture tests preserve exact Float32/native/transport and complete
context, source immutability and defensive generic validation. JSON hostile bounds,
STORED ownership and preparation-thread/drain regressions pass. Production camera,
thermometry, controls, coordinates and cadence are unchanged.

The fresh JVM/build/lint pass has 271 tests (core 232, app 12, prototype 27),
0 failures/errors/skips, 0 lint errors and 11 existing warnings. Localization
(110 English/French keys), shared camera boundaries (25 sources), nine independent
Python checks and four focused Pixel regressions pass. All eight actual child-process
kill cases pass again. [Regression report](reports/continuation-regression.json),
[fault report](reports/continuation-process-kill.json).

The executed source is `9f9d6097960d17188611e2da159ac4606cee148d`.
Each stage has 20 s warmup, GC, then 1200 100 ms ticks; elapsed periods include
instrumentation overhead (approximately 126–129 s).

| Stage | Acquisition FPS | Saved valid / observed valid | Preparation/writer drops | Render FPS | Display age p50/p95/max ms |
|---|---:|---:|---:|---:|---|
| Matched baseline | 25.000 | No writer / 3099 | 0 / 0 | 24.31 | 83 / 114 / 153 |
| Analysis-STORED | 24.995 | 3164 / 3164 | 0 / 0 | 25.00 | 78 / 103 / 116 |
| Full-STORED | 25.006 | 3156 / 3156 | 0 / 0 | 24.97 | 82 / 112 / 152 |
| Fresh-process Analysis-STORED | 24.992 | 3114 / 3114 | 0 / 0 | 22.41 | 106 / 154 / 218 |
| Native-STORED | 24.993 | 3113 / 3113 | 0 / 0 | 19.78 | 117 / 256 / 338 |

[Stage 1 numeric evidence](reports/android-continuation-stage1.json),
[fresh Analysis/Native evidence](reports/android-continuation-analysis-native.json).
Baseline has 68 unavailable raw14-unsettled observations. Initial Analysis and Full
have no unavailable/unobserved/malformed/replaced frames: every callback during
those measured periods was saved. Fresh Analysis has 68 unavailable and seven
replaced/unobserved frames; Native has 108 replaced/unobserved frames. These are
source gaps, not writer loss. Every delivered valid measurement was retained.
Independent readback confirms 3114 Analysis matrices plus 75 gap entries, and
3113 Native matrices plus 106 gap entries covering 108 missing sequences, with
complete integrity and source immutability ([report](reports/continuation-analysis-native-readback.json)).
The fresh Analysis/Native run overlapped private evidence backup; do not attribute
the replacements to that load without a controlled causal comparison.

The operator explicitly confirmed hand/object movement was **very usable and
fluid** during Analysis-STORED. Measured freshness remains below a second in all
completed candidate stages; controls alone are not treated as presentation proof.

For initial Analysis, inclusive preparation wall/CPU falls from 11.338/10.687 to
5.805/5.359 ms per frame; Float32 encoding falls from 7.800/7.376 to
0.893/0.831 ms. Candidate chunk encode p95 is 63.165 ms versus original discard
535.69 ms; metadata p95 is 18.649 ms versus 246.57 ms. Loads/frequency differ, so
these are stage observations, not a controlled CPU microbenchmark ratio.

Both preparation and writer remain byte-bounded at 4 MiB, with no priority boost.
Initial Analysis/Full measured writer maxima are 1331953/3341203 B; preparation
max is one queued 1108992 B source. Sampled backlog drains repeatedly and final
stop takes 80/63 ms. There is no sustained backlog trend. Every committed entry,
including gaps, remains separately counted from saved matrices.

Independent readback of the complete Stage 1 files verifies all payloads, logical/
block/framing integrity, native Full views and source immutability: Analysis
3164 valid matrices, zero gaps, 125 chunks; Full 3156, zero gaps, 127 chunks.
[Readback report](reports/continuation-stage1-readback.json). Private recordings
are preserved locally outside Git; no scene fixture or private machine data added.

The original preferred 16 MiB DEFLATE candidate is now measured:
Analysis saved 3087/3087 with zero recorder loss, acquisition 25.005 FPS, render
24.35 FPS and age 84/108/122 ms. Native saved 3131/3162 with **31 explicit writer
drops**, six separate source replacements and zero preparation loss; render
24.59 FPS and age 86/117/177 ms. Full escalation stopped, so it is not a pass.
[Preserved failed configuration](reports/android-continuation-deflate-16m.json).
Native chunk p95 311.789 ms / max 512.830 ms, writer-service max 529.550 ms, queue
max 3990999 B: the approximately 240 ms six-frame queue window is exceeded by
sealing bursts despite mean service 10.114 ms per observed valid frame. No bulk
backup overlapped this run. The predeclared 4 MiB preferred-chunk repeat tests
this remaining bounded burst problem without increasing queues or reducing rate.

Clean Full/final selected DEFLATE memory and the shorter-chunk repeat remain
pending at this intermediate report. Earlier recovery/sparse/view/provider evidence is preserved;
the test-only SAF launch and final same-byte packet are checked after live runs.
Real Windows execution remains a required pending gate.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
