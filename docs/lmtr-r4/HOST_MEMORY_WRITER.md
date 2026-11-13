# R4 host memory and writer candidates after the second physical smoke

Status: isolated Fedora engineering, based on `bacfd3a66cb7a63aa8f17d883c01c7ed047fdf6e`.
No ADB/discovery, phone/camera, APK installation, emulator, hosted ART or Actions is
permitted in this phase. Preserve the [second physical report](SECOND_PHYSICAL_ATTEMPT.md),
all original bytes and all historical failed experiments. Host gains are not Pixel RSS proof.

## First demonstrated attribution

The physical file has 12 CHUNKs: 11 mixed observation/loss and one observation-only;
none is gap-only. Eleven retain four Full observations each, and the terminal one
retains one. They carry 60 explicit losses among 45 retained observations. Thus the
[aged one-gap-CHUNK mechanism](https://github.com/LounaMaili/LMThermal/blob/facf86f121d68f796dff7a4e6957c8069d8dd546/docs/lmtr-r4/BACKLOG_DYNAMICS.md)
is a separately demonstrated possibility, not an observed explanation of this file.

## Ownership candidate

- `RecordingPayload.fromFloat32Le` packs original raw bits into one freshly allocated
  private owner. The public byte-array constructor remains defensive. No output array
  or writer callback escapes. HT still asks its immutable source for an independent
  FloatArray copy; it no longer builds an encoded array which the constructor copies
  again. Exactly 442,368 array bytes are avoided per available 384×288 temperature plane.
- `FrozenJson.value` feeds its private bytes directly to the synchronous read-only
  parser. Fresh immutable maps/strings still result; there is no retained metadata tree
  or externally visible byte alias. Source JSON and returned nested maps remain protected.
- Payload role access retains only one immutable String with volatile publication,
  not a whole parsed descriptor or source owner. Competing initial reads may parse
  twice harmlessly; no recorder lock or admission behavior changes. Missing/invalid
  roles still fail on access rather than being defaulted.

The existing negotiated/queued/prepared/pending byte counts do not change: serialized
owners are identical. A small scalar String/object overhead is retained per payload;
it is not charged as another plane or used to lower 78+32 MiB admission. There is no
new native/source/presentation ownership API, pool, borrowed public reader or thermometry.

## Host measurements and qualification

`HostWriterCostProbe` is a JVM-test-only tool. It reads one existing preserved file,
selects the first largest-decoded CHUNK, and reports fixed eight scalar samples after
four warmups. It measures current-thread allocation and inclusive wall/CPU time for:
fixture freezing+Full selection, complete physical metadata closure, and two separately
executed full physical CHUNK validations. Input reading/setup and SHA identity checks
are outside measured intervals. Results retain no payload history and write no payload.
HotSpot counters are not ART, native allocation, reachable heap or RSS measurements.

The initial unpaired exploratory comparison remains private historical evidence.
The completed controlled comparison and its limitations are recorded below, with
the [sanitized receipt](host-memory-writer-validation.json).

New deterministic tests cover raw Float32 bits (signed zero/infinities/NaN payloads),
input/output mutation isolation, nested JSON immutability, exact optional decimal
values, invalid-role failures and concurrent role publication. Existing recorder and
adapter tests pass, including overloaded queues, timed Stop, sync/ACK and incomplete
recovery. All safety budgets, both complete validations and Full retained evidence
remain unchanged. No physical performance improvement is claimed at this checkpoint.

## Physical memory attribution

Five diagnostic samples span 4.224516156 s. Diagnostic metrics and safety-health
probes are different instants; their peaks must not be added as simultaneous owners.

| Sample | Java occupied B | Native allocated B | Diagnostic RSS B |
|---|---:|---:|---:|
| 1 | 50,969,600 | 30,674,096 | 311,840,768 |
| 2 | 41,528,320 | 28,462,160 | 311,451,648 |
| 3 | 68,025,344 | 33,344,128 | 343,236,608 |
| 4 | 108,715,008 | 45,549,328 | 396,242,944 |
| 5 | 152,173,568 | 56,654,192 | 448,307,200 |

Admission RSS was 297,185,280 B; the unchanged +128 MiB ceiling is 431,403,008 B.
The triggering **health** RSS was 440,557,568 B, not the later diagnostic maximum.
Whole-process cumulative Java allocations increased by 960,851,968 B, with four
natural GCs /365 ms cumulative GC time and no new blocking-GC count. Allocations
are not simultaneous live objects. These samples neither locate GC within a seal
nor prove a retained leak or isolate the recorder from Compose/thermometry.

| Category | Defensible attribution | Missing evidence |
|---|---|---|
| Java | Occupied heap includes unreachable garbage; substantial short-window growth | No post-natural-GC plateau or root/dominator evidence of actual retained heap |
| Native USB/UVC, codec, thermometry | Aggregate native allocations grow alongside recording | No component-resolved allocator/driver attribution; RSS is not native allocated bytes |
| Bitmap/Compose/graphics | Normal preview remains active | No matched preview-only window; no separate graphics leak demonstrated |
| Acquisition/presentation | One HT source owns 224,256 transport +442,368 indices +442,368 Float32 =1,108,992 array bytes, before objects/metadata | Role aliases and weak uncollected histories are not a census of strong roots |
| Preparation | Matrix copy, packed Float32 owner, raw/transport copies and defensive owners coexist transiently | C1 removes one plane, not all source ownership |
| Writer/pending | Preparation queue/in-flight maxima 3,145,728 B each; writer queue 3,577,068 B, in-flight 894,269 B; pending 3,599,177 B | Logical maxima at different instants, not additive physical RSS |
| JSON/validation | Closure encodings, fresh trees, decoded blocks and statistics create transient objects | Lower allocated volume need not reduce RSS before natural collection |

The [historical no-recorder Pixel window](PIXEL_LIVE_MEMORY.md) had 528 valid frames
near 25 FPS, sampled Java/RSS maxima 185,249,984/475,676,672 B and 4/20 headroom
failures. It is another build/window/process, supporting investigation of the normal
pipeline too, **not** subtracting its memory from this recorder run. Earlier
[presentation](PRESENTATION_PERFORMANCE.md) and [live-heap](LIVE_HEAP.md) receipts
retain their separate GC and sampling qualifications.

## Conservative simultaneous lifetimes

The reservation remains 4 MiB preparation +4 MiB writer +two 3 MiB in-flight items
+12×4 MiB scratch/pending +16×1 MiB control =78 MiB, plus the unchanged 32 MiB
margin. Engine budget, RSS guard, 1 MiB journal and all capacities remain unchanged.
This accounting is not physical preallocation or a universal ART heap/RSS proof.
Preparation can overlap pending owners, codec body, decoded validation blocks and
JSON trees. Sequential validations can still leave earlier unreachable objects in
occupied heap until natural GC; Stop's full reopening is another lifetime boundary.
Existing bounded 64 KiB codec scratch/segmented output stays intact. No pool, shared
validation cache, borrowed payload or extra queue is introduced. Generic near-limit
metadata still needs the original allowance; small scalar cache overhead is not free.

## Candidate C2: redundant complete canonical encodings

`LmtrMetadata.chunkChecks` encoded the same immutable closure three times for local
schema/limit checks, the CHUNK size assertion and block-group sizing. It now computes
the exact size once **within that synchronous call**, retaining every check. Public
`local` keeps its Unit behavior/error mapping. No size/hash/result is reused across
validation, body or source-generation boundaries. Both full encode/append validations
still independently parse/decompress/hash/check logical views/statistics. The new
regression validates a body, corrupts a copy and requires independent append to reject
it before a CHUNK backend write and to poison checkpointing, for both codecs.

## Fixed host comparison

The predeclared reference/candidate/candidate/reference campaign used four fresh
HotSpot JDK 17.0.20.1+1 processes, `-Xms64m -Xmx256m`, four warmups and eight measured
samples/case, no concurrent benchmarks or forced GC. Results are the mean of two
process medians per arm, not a statistical guarantee. Input is the first largest
decoded physical CHUNK (2,666,496 decoded bytes, 23,450 metadata bytes). Cases batch
fixed repetitions and divide by their count; setup/read/SHA checks are outside timing.

| Inclusive operation | Allocated B/op reference → candidate | Change | Wall ms/op reference → candidate |
|---|---:|---:|---:|
| Fixture freeze +Full selection | 2,894,776 →2,197,008 | −24.10% | 1.736 →1.075 |
| Complete physical metadata closure | 4,087,810 →3,535,351.5 | −13.51% | 5.565 →5.264 |
| Two independent full physical CHUNK validations | 14,764,128 →13,517,296 | −8.45% | 44.227 →43.021 |

CPU changes are −40.78%, −4.81%, −1.22%. Validation wall change (−2.73%) is modest;
none establishes ART throughput or RSS. C1 combines packing/input-copy/cache effects:
its whole reduction cannot be attributed to one array. Counters exclude other threads
and native allocations. JIT/GC/platform variance and limited repetitions remain.
The probe is isolated under `src/hostTest/kotlin`, outside ART's shared test sources.
One preliminary campaign stopped at a missing-main infrastructure error, before any
candidate measurement; its reference/error and corrected source-set build are retained.

Six complete Analysis/Native/Full ×STORED/DEFLATE files remain **byte-identical** to
reference, six observations each (one unsettled then five available). Independent
Python checks original-owner Float32/native/transport, views, source/intake facts
and absence semantics. No canonical bytes are regenerated.

## Writer, backlog and ART versus physical results

| Evidence | Result | Scope |
|---|---|---|
| Physical Full/DEFLATE | 45 ACK, 2 preparation +58 writer losses, RSS stop at 4.309 s | Normal Pixel/USB/Compose; no exclusive stage attribution |
| Historical ART OFF/ON [43238655791](https://github.com/LounaMaili/LMThermal/actions/runs/43238655791) | STORED 81–82/200 ACK; DEFLATE 74–77/200, explicit losses | Four fixed OFF/ON/ON/OFF arms; not matched physical performance |
| Historical ART fixed-pattern [43242970885](https://github.com/LounaMaili/LMThermal/actions/runs/43242970885) | STORED 16–17 ACK then reserve exhaustion; DEFLATE not reached | Preserved negative result; no demonstrated ART throughput gain |
| Current reference and candidate host STORED | Both 199/200 ACK, zero preparation +1 writer loss | Finalized with explicit loss, not a loss-free pass |
| Current reference and candidate host DEFLATE | Both 200/200 ACK, zero drops | Finite saved fixture; no Compose/USB/physical admission |

Each host process first supplies 50 producer-only baseline observations, then 200
offers/codec at 40 ms deadlines. Baseline median intervals are 40.001/40.014 ms;
intake windows about 7.962 s; Stop/drain 105–173 ms. All four outputs/oracles pass
independent Python checks. Candidate sampled occupied Java maxima are
89,939,472/114,924,192 B versus 121,614,424/135,038,592 B. These one-run GC-dependent
peaks are not retained heap, RSS, a plateau or a statistically established reduction.
Writer p95 times do not uniformly improve; allocation savings are the stronger result.

Physical seal+append p50/p95/max is 348.613/390.316/611.553 ms; encode+full-validation
p95 249.873 ms, append p95 173.720 ms, codec-call p95 11.692 ms. Four observations
per CHUNK correspond to 160 ms supply at 25 Hz, consistent with a service deficit.
Metrics are nested/inclusive distributions, not exclusive phases: subtracting their
percentiles would falsely isolate backend or compression. Host timing cannot explain
ART-specific GC/scheduling or the physical backend. Final ACK/window 10.443318/s
includes drain; it is not a ten-second throughput or ACK-latency measurement.

`HostBacklogBoundaryProbe` adds eight deterministic cases: both codecs, fresh/aged
witnesses, 32/33 losses. The unchanged 4 MiB queues, conservative 3 MiB source offer
and 1 MiB journal are used. A latch holds preparation: 32 losses charge exactly 1 MiB,
all are explicit after drain; the 33rd is refused and prevents END. Python checks
the incomplete verified prefix. These are correctness boundaries, not performance
or proof that aged-gap amplification caused this physical file.
Existing delayed-sync/ACK, Stop races, torn append, allocation/source failure and
sticky-unrepresented-loss tests remain unchanged.

The [bounded loss-run prototype](https://github.com/LounaMaili/LMThermal/blob/61e8f265319ff846a182cbe7600ac32f5bff3104/docs/lmtr-r4/LOSS_RUNS_DESIGN.md)
stays isolated and unadopted. Changing coalescing/timer/reserve release now adds risk
without explaining this zero-gap-only topology. No journal capacity is increased.

## Proposed bounded runtime diagnostics (not enabled here)

Use existing offers/admissions/ACKs/drops, queue/in-flight/pending counters, composition,
GC totals, heap/native/RSS/PSS and separate current source/preview roles first. Any
future extension should preallocate fixed scalar histograms, count overflow/omissions,
publish bounded snapshots outside recorder locks and retain no payloads, JSON trees,
Throwable chains or per-frame history. The separate fine-writer instrumentation has
512 KiB preallocation plus bounded scalar objects; it is **not imported here**.
Its internal validation/build/backend timings and missing/interrupted counts could
support a later fixed OFF/ON campaign. Observer clocks must not become source witnesses,
deadlines or ACK conditions. A matched normal-preview window and timestamps around
natural GC/OS category samples are needed before component attribution. Root/native
investigation needs separate authorization; no forced GC to pass Start or summing
overlapping Java/native/PSS/RSS/graphics categories.

## Executed checks and host reproduction

- 449 successful JVM module executions: core 234, contract 74, recorder 53,
  bridge 20, field support 47, app 21. Shared test sources cause overlap; these
  are execution counts, not 449 distinct invariants.
- 64 Python unit tests; 51 unchanged corpus cases in each reader, 33 canonical-plan
  byte comparisons, 29 hostile cross-language combinations, three cancellation modes
  and 14 queued-engine interoperability cases pass. Cancellation initially failed
  output-JSON parsing; the checker did not retain raw child stdout. The unchanged
  assertions pass with JVM warnings routed to stderr, and a later direct unmodified
  probe returns valid JSON. The exact transient stdout cause is not established.
- Six byte-identical profile/codec files and ten independent full-size output/oracle
  checks (six fixture files plus four stress files), plus eight backlog readbacks.
- App/field build and lint pass. The ART test APK compiles, with neither host-only
  probe in its DEX; **no ART runtime** test or device install occurs. `git diff --check`
  passes. Engine, field safety/preflight, wire and prior evidence remain unchanged.

Host-only commands use the repository JDK/cache, with no connected tasks:

```bash
./gradlew --offline -p tools/lmtr-r3-host test :lmtr-field-support:writeTestRuntimeClasspath
java -Xms64m -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.HostWriterCostProbe PRIVATE_EXISTING_LMTR NEW_PRIVATE_REPORT_JSON
java -Xms64m -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.HostBacklogBoundaryProbe NEW_PRIVATE_OUTPUT_DIRECTORY
```

For comparisons, freeze each compiled runtime/classpath first, pin its hashes and use
a predeclared fresh-process order. Preserve failures rather than overwriting output.
The actual private receipts include immutable runtime manifests, all logs and original
hash checks; documentation-check success alone is not runtime conformance.

## Future ten-second protocol — not executed

1. Obtain fresh explicit Pixel/HT availability and non-sensitive-scene authorization.
   Only then use normal wireless ADB discovery. Preserve apps/data and earlier evidence;
   pin code and built/installed APK hashes and create a new private run identifier.
2. Normal explicit Connect/Open and validated initialization only when needed; confirm
   changing READY frames and normal Compose/Celsius/ROI. No implicit replug/reinitialize.
3. Record one honest admission witness: heap/headroom, system availability, baseline
   RSS, thermal/battery/storage, unchanged 78+32 MiB, capacities, profile/codec/duration.
   Refusal is preserved, never retried for a favorable sample.
4. One Start, exactly ten requested seconds, Full/DEFLATE-1 unless explicitly changed
   by the owner to a supported profile. Normal preview/load; no GC/fallback/second Start.
   Normal Stop if safety has not stopped it; distinguish intake duration from drain.
5. Preserve outcome, samples, LMTR/oracle/APK/log hashes, including incomplete files.
   Separate acquisition, eligible positions, accepted, ACK, prep/writer/source losses,
   gaps, queue peaks, seal/append/codec distributions, GC/memory and terminal reason.
6. Independent Python and Linux Desktop full/recovery readback as applicable, original
   source hashes, all per-role parity, timeline/point/ROI/palette. Report integrity,
   duration, safety and loss-free throughput as separate gates.

| Explicit profile | Retains when supplied by the HT module | Absent by choice |
|---|---|---|
| Analysis | Float32, validity if supplied, source/context/settings, honest unavailable states | Original raw14 and UVC transport/trailer bytes |
| Native | Analysis plus original native samples | Original UVC transport/trailer |
| Full | Native plus original transport/same-observation view and all available advertised evidence | No implicit downgrade; unsupported roles remain explicit |

For a valid HT frame without mask, physical payload sizes are 442,368 /663,552 /
666,624 B respectively. Full views the 221,184-byte native image inside 224,256 transport:
Native saves only **3,072 physical block B/frame**, not an entire packet. Logical queue
charges/unavailable-frame behavior differ. The adapter still freezes **all available
roles before profile selection**: Analysis/Native do not avoid that initial work.
Profile-aware freezing is a separate candidate architecture, never silent Full omission.
Each comparison needs its own authorization; no profile is an admission workaround.

**Thirty to sixty seconds is not ready.** First require the short candidate to reach
duration with exact integrity/loss accounting, no safety stop and useful preview;
then compare matched memory/GC/queue trajectories and writer service on separately
authorized ART/Pixel. Lossless physical 25 Hz remains an unmet separate gate. A longer
attempt needs explicit approval and bounded backend/storage/recovery evidence;
host allocation improvements alone are insufficient.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
