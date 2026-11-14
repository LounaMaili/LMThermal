# Offline paths to a scientifically usable thermal sequence

Status: **engineering study and isolated host prototypes, not product integration**.
No device, camera, ADB, emulator, APK installation, Actions, physical capture or
format change is part of this campaign. Thirty seconds is a future physical goal,
not a hardware authorization. Analysis remains the accepted product default.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## References and evidence boundaries

- Production candidate: `db0de1ddd1d92370829937b6ef3cf6feb2d6489c`;
  study parent: `f75bb403b66720927e4dfd012ae02ed7f56c79c0`.
  Every production `src/main` file, the frozen [wire](../LMTR_WIRE_V1.md), both
  independent validation boundaries, queues, journal and 78+32 MiB are unchanged.
- Desktop inspected/tested read-only at
  `dd1c1ff4d76fef4a7cf4ed90dd76354b2ed8ca86`. Its Python recorder still writes
  `lmthermal-radiometric-recording` v1 directories, not common LMTR.
- [Second physical attempt](SECOND_PHYSICAL_ATTEMPT.md): interrupted END at
  4.308975303 s, 105 positions, 45 acknowledged observations, two preparation and
  58 writer losses; acquisition about 25 Hz. Original file SHA-256 remains
  `0ca64fc9a9a9507a241f20c1b358d176c476972603f088bd64cafcea9cb76a3f`.
  Integrity of the retained measurements does not demonstrate continuity.
- [Memory/ownership investigation](HOST_MEMORY_WRITER.md) and
  [writer attribution](WRITER_PHASE_ATTRIBUTION.md) remain evidence for their
  respective host and physical scopes. The five physical memory points cannot
  identify reachable heap, native/graphics ownership or short RSS peaks uniquely.
- The machine-readable [receipt](temporal-recording-study-validation.json) records
  this campaign. Original files, logs, oracles, frozen classpaths, unsuccessful
  preparation outputs and per-sample measurements remain in private local evidence.
  Same-filesystem preservation is not an independent backup.

## Scientific acceptance target

A useful first sequence needs at least 30 s of an actual authorized scene, an
explicit requested sampling policy, complete Float32 planes and validity for each
retained measurement, original interpretation/provenance, truthful clock domains,
all scheduled unavailability/loss/interruption, immutable source bytes, and Desktop
navigation with point/ROI analysis over time. A requested rate is not a promise of
that many valid saved planes. Report acquired, selected, unavailable, accepted,
lost, acknowledged and valid-plane counts separately, with their time denominators.

Record genuine host receipt and recorder-intake time separately from planned slots
and unknown sensor exposure time. Host nanosecond units do not establish nanosecond
sensor precision. Never carry a last-good plane across an unavailable observation.
Sampling may alias scene changes; no interpolation is an original measurement.

## A — Profile-aware Android Analysis preparation

### Existing behavior and demonstrated cost

`Ht301RecordingAdapter.offer` defers freezing to the charged preparation worker.
Currently `freeze` constructs temperature, raw image and transport owners before
`RecordingProfiles.select` discards unrequested roles. Analysis already retains
the complete original Float32 plane, measurement availability, exact context and
provenance. It intentionally omits raw/native and transport binary evidence; those
omissions must remain distinct from unsupported or unavailable source evidence.
Full remains explicit and retains all meaningful offered evidence. No fallback
from Full to Analysis is proposed.

For a ready HT observation, the acquired immutable arrays account for 1,108,992 B
(224,256 transport + 442,368 decoded indices + 442,368 matrix), before objects and
other owners. Selection cannot undo these acquisition/thermometry/presentation
costs. Each source offer still charges 3 MiB. The source can coexist with a private
matrix copy and encoded plane during preparation; no pooling/borrowing is introduced.

The host-only `OfflineProfileAdapter` copies the current fact/context construction
and avoids creating binary owners for unrequested roles. It is deliberately
isolated in `src/hostTest`: no product factory/subscription/API uses it. A later
implementation should share the fact builder and receive an immutable explicit
profile before freezing, rather than maintain two diverging HT adapters.

Fresh-process ABBA order (eager, profile-aware, profile-aware, eager), JDK 17,
`-Xms64m -Xmx256m`, four warmups, eight samples of six preparations per profile:

| Profile | Eager allocation B/observation | Prototype allocation B/observation | Change | Eager/prototype inclusive ms |
|---|---:|---:|---:|---:|
| Analysis | 2,197,024 | 1,267,744 | −42.30% | 1.240 /1.113 |
| Native | 2,205,752 | 1,744,392 | −20.92% | 0.858 /0.750 |
| Full | 2,200,608 | 2,200,619 | effectively unchanged | 0.671 /0.674 |

Medians pool the 16 samples per arm/profile. Current-thread allocated bytes include
freeze+selection, not acquisition, thermometry, oracle or writer workers. Wall time
is order/JIT/GC sensitive and is not the primary claim. No GC was requested. Avoiding
two raw-image and two transport arrays accounts for 890,880 B of the Analysis saving;
the measured remainder includes descriptor/metadata work. At 25 selected/s the
measured allocation difference is about 23.23 MB/s, **not** a measured RSS reduction.

Selected prepared charge is identical before/after: Analysis 448,223 B,
Native 669,785 B, Full 894,251 B for this ready fixture. Nine such Analysis entries
fit the 4 MiB writer byte queue versus four Full entries, without increasing its
capacity. Actual metadata/closure charges and source offer limits remain enforced;
these counts are illustrative, not new queue-item limits. Pending and in-flight
owners, encoder buffers, both full validation decodes and reader/index overhead
remain separate simultaneously-live costs.

Six simulated observations (one raw14-unsettled, five available) produce exactly
the same complete LMTR bytes for all profiles and both codecs across all four arms:

| Profile | STORED file bytes | DEFLATE-1 file bytes |
|---|---:|---:|
| Analysis | 2,238,051 | 548,856 |
| Native | 3,568,517 | 1,035,053 |
| Full | 3,594,140 | 1,044,518 |

The independent original-owner oracle checks selected roles and exact payload hashes,
receipt/intake, unavailable state and native views, with source immutability. Full
retains raw as a checked view into transport; Native must materialize raw without
that parent. Native saves only 3,072 physical B/available HT observation relative to
Full's view-based physical payload; it is not the major memory/throughput lever.

### Writer and integrity boundary

Analysis reduces physical available-image data from 666,624 B (Float32+transport)
to 442,368 B, retaining the full grid. A validity mask, when required, is additional
original evidence and cannot be omitted. These ready HT samples are all-valid;
separate tiny masked examples and existing generic tests cover mask semantics.
The encoder still validates the complete CHUNK and the append boundary independently
validates it again. Neither result nor a decoded cache is reused to bypass a check.

The R4 pending target is charged ownership, not just physical body bytes. Analysis
can put more observations/context definitions into a CHUNK; its per-CHUNK cost must
not be compared as if it had the same observation count as Full. Existing timer
sealing, durable footer acknowledgement, bounded loss reserve and Stop remain in use.

**Remaining:** physical RSS/GC/graphics/native headroom; ART costs; source/presentation
owner overlap; direct adapter integration and bounds review. The test-only oracle
adds hashing on preparation and is not a production performance baseline.

## B — Explicit recording rate while acquisition continues

### Frozen v1 is expressive, but the current adapter is not a sampler

Wire §7/§8 already provides `sampling.mode=periodic`, integer `period_ns`, origin,
scheduled ranges, actual receipt/acquisition, monotonic intake, observed unavailable
Observations, and distinct missed/preparation/writer gaps. Acquisition-following
sampling has null period/origin/schedule. A persistent policy/rate change starts a
new segment. Optional evidence cannot redefine these base semantics.

| Requested Hz | Exact v1 integer period | Recommendation |
|---|---:|---|
| 5 | 200,000,000 ns | first controlled implementation target |
| 10 | 100,000,000 ns | next candidate after 5 Hz qualification |
| 15 | not representable exactly | do not silently advertise exact 15 Hz |
| 25 | 40,000,000 ns | supported declaration, separate performance gate |

66,666,667 ns means about 14.999999925 Hz. It could be explicitly presented as an
approximate 15 Hz policy with its exact stored period, after a separate product
choice; no exact 15 Hz or rational schedule support is added here. A rational
sensor-clock tick definition does not change the integer schedule formula.

Recommended selection: first new callback at/after a declared deadline. At 25 Hz
acquisition and 10 Hz selection, receipts can be 20 ms late and alternate 80/120 ms
spacing; the ideal 100 ms deadline is never substituted for receipt/intake. At idle,
a bounded timer detects elapsed slots independently of callbacks; no stale plane,
backdated receipt or matrix catch-up is allowed. Stop serializes the cutoff and
the last elapsed scheduled slots. Source uncertainty remains separate.

| Event | Required truth |
|---|---|
| Observed source frame not selected by explicit policy | selection evidence/counter; not recorder overload or invented vendor loss |
| Selected callback with unavailable measurement | Observation with reason, no temperature, no stale readings |
| Scheduled slot without a newly observed callback | `missed_schedule` gap with known slot range; actual detection time, no fake receipt |
| Offered/accepted observation lost at preparation/writer | exact known observation identity/category, associated slot retained; no ACK |
| Footer-synced observation | acknowledged Observation; available/unavailable and valid-plane count remain distinct |

Current `RecorderEngine.admit` requires contiguous offered observation counters.
Passing original module ordinals 0, 5, 10 after filtering is refused; periodic metadata
does not excuse unexplained counter holes. The isolated prototype explicitly creates
an actual **selected-callback counter**, preserves original acquisition ordinal and
callback counter separately, and labels the new counter domain in a namespaced
optional extension. It does not silently relabel the existing module counter as
dense or claim that original callbacks were lost. This adapter-boundary choice needs
explicit production review/documentation, not a wire change. No counter advances for
an unobserved scheduled slot.

**Important implementation gaps:** the current automatic overload path constructs
`scheduled=null` after lightening a source offer. It preserves observation loss but
does not retain the selected slot association needed for a fully auditable periodic
product. A bounded scalar scheduled-range witness must survive both overload stages.
A complete monotonic idle/Stop slot ledger is also absent. Readers validate supplied
schedule formulas/counters; a successful parse does not prove every elapsed requested
slot was represented. The new simulator/positive wire examples do not implement these
production paths or establish runtime sampler conformance.

The load prototype keeps cumulative intentional-nonselection totals in its private
report; the LMTR keeps policy and per-entry original/selected counters. It does not
yet persist a complete self-contained selection ledger for the beginning/terminal
tail. A production design must retain bounded cumulative evidence at orderly closure
(for example in a namespaced segment-end extension) and leave an incomplete tail
unknown after a crash. This is a proposed producer convention, not new mandatory
v1 syntax. Maintain counter continuity across policy changes or explicitly qualify
a new counter domain; never invent a physical source-session reset to bypass admission.

Three noncanonical 3×2 masked examples at 5/10/25 Hz express available, observed
unavailable, two missed slots, a writer loss and recovery to an available observation.
Both readers and Desktop accept the valid examples; unexplained original-counter
skips are refused by the writer. All 51 canonical R3 fixtures remain unchanged.

### Finite host load, with normal engine protections

One predeclared ten-second window each, DEFLATE-1,25 Hz synthetic HT gate replay;
source callback 100 is deliberately measurement-unavailable. No USB, Compose, real
scene change or physical liveness gate is simulated as validated. Full is an explicit
separate profile, never a downgraded run. Queues stay 4 MiB each, control 1 MiB,
reservation 78 MiB, headroom 32 MiB, no forced GC. Original callback receipt and genuine
HotSpot recorder-intake witnesses are independently checked; source origin is simulated.

| Profile / Hz | Acquired synthetic callbacks | Intentionally unselected | ACK /available planes | Prep /writer loss | Missed slots | LMTR bytes | Stop ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| Analysis 5 | 250 | 200 | 50 /49 | 0 /0 | 0 | 5,328,961 | 94.5 |
| Analysis 10 | 250 | 150 | 100 /99 | 0 /0 | 0 | 10,721,204 | 126.2 |
| Analysis 25 | 250 | 0 | 250 /249 | 0 /0 | 0 | 26,954,459 | 103.4 |
| Full 25 | 250 | 0 | 250 /249 | 0 /0 | 0 | 47,582,004 | 188.2 |

All four end completed and drain; no queued/in-flight/pending bytes remain. Maximum
sampled writer queue is 448,701 /897,400 /1,346,106 /1,789,460 B respectively.
Minimum sampled Java headroom is 218.45 /208.45 /161.53 /156.49 MB; these sequential
process-phase points include garbage and are neither retained heap nor RSS/ART
comparisons. They cannot justify reducing the unchanged admission reservation.

Inclusive encode+first-validation p95 is 51.97 /72.49 /70.22 /54.59 ms per CHUNK;
inclusive append+second-validation p95 is 30.28 /35.74 /34.84 /28.01 ms. Pending
ownership allows more Analysis observations per CHUNK; these are not per-frame
codec costs or disjoint pure validation times. Earlier
[attribution](WRITER_PHASE_ATTRIBUTION.md) remains the controlled two-boundary detail.
Success of even Full 25 on HotSpot does not contradict or repair the physical Pixel
writer losses/RSS stop. The original failed ART/physical cases remain separate facts.

## C — Existing direct Linux Desktop recording

### Available now

At the pinned Desktop source, `radiometric_recorder.py` offers 1/2/5/10/25 Hz,
default 5; no 15 Hz. A monotonic sampler reads a paired latest observation/ROI slot,
copies exact raw14 and Float32, and hands off to a four-frame queue. Writer chunks
hold at most 16 valid frames, stack the arrays, compress NPZ, fsync, publish a new
chunk by exclusive hard link, fsync timeline and atomically replace the manifest.
Stop drains and commits a shorter final chunk. The strict legacy loader and
two-chunk playback cache support point and inspection ROI values without thermometry
recomputation. This is already a possible independent Linux study tool.

There are two latest-frame boundaries: acquisition worker to Qt and Qt to recorder.
`lmthermal_viewer._poll` updates the recorder after UI conflation. The current sampler
does not subscribe to every authoritative camera observation. UI/thermometry/camera
timing therefore needs real qualification; a 25 FPS view counter is not proof of
measurement continuity. Duplicate latest owners become `no_new_observation`, not a
second stored plane. For a more rigorous future path, decouple recorder input from
UI conflation with an explicitly owned/qualified observation channel.

Legacy NPZ stores full raw14 and Float32 plus per-frame parameters/lookup/summary,
but **no original transport**, no generic partial validity mask and no common LMTR
receipt/intake clock grammar. It rejects nonfinite matrices, representing invalid
measurements as gaps. It has count-bounded buffers, not Android's 78+32 MiB admission.
Its ordinary elapsed values are actual host sampling-relative floating times; missed
rows use planned elapsed and estimated UTC. Original integer receipt/intake domains
and sensor exposure precision are not available and must not be claimed in analysis.
Sixteen pending binary planes are about 10.62 MB before simultaneous stack copies,
compression/hash/read buffers and the four-frame handoff. Manifest/timeline indexes
grow with duration. Neither directory fsync nor physical power-loss certification is
provided. Corrupt/missing committed chunks fail; uncommitted/orphan chunks expose no
measurements. Recorded `stored=true` means accepted reference, not necessarily a
manifest-committed matrix. The committed counter remains separate.

### Historical evidence and current offline result

The repository's2026-09-30 report records real5/10 Hz runs with no writer drops but
many invalid/no-new gaps;25 Hz had46 writer drops. Its original live bundles were
later unavailable, so those claims are historical aggregate evidence, not a newly
rechecked recording. Existing physical Android LMTR readback verifies preserved
bytes/playback, not current Linux acquisition continuity.

This campaign uses the unchanged real sampler/writer for 30 wall-clock seconds per
rate. A synthetic25 Hz gate stub alternates two sanitized fixture matrices and
injects unavailable intervals; there is no acquisition, UI, liveness or camera test.
No sampler clock, queue, compressor or Stop implementation is patched. A bounded
scalar observer surrounds `ChunkWriter.commit`; it delegates unchanged. NPZ's
compression level and alternating inputs differ from the Android experiment, so
cross-table times/file sizes are not matched platform benchmarks.
The host caller allows 30 s to join Stop, without changing the Desktop 10 s default;
all measured joins return within 0.5 s. This is not a new production timeout.

| Requested Hz | Requested slots | Saved planes | Unavailable/no-new rows | Writer drops | Bytes | Stop ms |
|---|---:|---:|---:|---:|---:|---:|
| 5 | 150 | 142 | 8 /0 | 0 | 25,202,788 | 407.8 |
| 10 | 300 | 284 | 16 /0 | 0 | 50,561,861 | 367.4 |
| 25 | 750 | 544 | 33 /59 | 114 | 97,023,227 | 489.7 |

Every requested slot is accounted for. All bundles finalize, every saved raw/Float32
plane equals its synthetic original, point/ROI values match and sources stay unchanged.
Point values alternate 21.837799/36.524605 °C and centered ROI means
21.928430/36.541167 °C because these are replayed fixtures, **not** a new warm-target
or temporal thermometry experiment. Writer16-frame commit p95 is about 365.1 /350.7
/365.2 ms; sampled handoff maximum 1/3/4. At 25 Hz, 544/30 is about 18.13 retained
planes/s relative to the requested source window, including commits during drain;
it is neither instantaneous ACK throughput nor 25 valid/s. Do not retry or erase
this negative case.

Playback currently supports temporal navigation and point/ROI inspection of the
current saved frame. A reproducible offline analysis can iterate frames and export
time/point/ROI/validity/status rows; a persistent plotted analysis/export workflow
needs separate product work. Never bridge gaps with an invented interpolated series.

### Coexistence is the safe near-term route

Frozen wire §6.3 explicitly refuses faithful baseline-v1 conversion of historical
multi-file NPZ datasets with only sampling-relative/estimated clocks. Hashing the
manifest alone, treating chunks as up to 16 independent parents, inventing a ZIP
source or converting estimated elapsed times into genuine intake witnesses is not
faithful conversion. R7 needs the accepted future dataset-provenance/qualified-import
review; this study implements no converter. Direct future Linux **new** LMTR writing
could use real receipt/intake witnesses and the same guarded semantics without that
historical conversion problem, but it is a new recorder integration/performance gate,
not an automatic extension of the current offline Python reader.

## Decision comparison and recommended sequence

| Route | Existing strength | Necessary next software work | Main integrity/memory risk | Relative effort /scientific potential |
|---|---|---|---|---|
| A Analysis | accepted default; complete matrix/context/mask semantics; guarded common writer | integrate profile-aware preparation only after parity/bounds review | acquisition/graphics owners and validation allocations remain; physical RSS unknown | small adapter change, substantial physical gate; best Android default |
| B explicit 5/10 Hz | frozen v1 already represents schedule and qualified gaps | selected-counter contract, monotonic idle/Stop ledger, slot-preserving overload witnesses, UI/segment policy | silent slot/counter loss if naively filtering callbacks; acquisition still25 Hz | moderate concurrency work; strongest Android route with A |
| C legacy Linux 5 Hz | recorder, playback and historical live experience already exist | current hardware qualification; reproducible offline point/ROI series; optionally authoritative input seam | UI conflation, older timing/provenance, transport absent, buffers larger | lowest route to an independent study on a suitable Linux host; keep legacy format explicit |
| C new LMTR writing | reader/reference tools exist | new bounded writer/backend/adapters and qualified timing; not a legacy converter | must earn both validation/durability/performance guarantees | greater effort; not prerequisite for reading legacy scientific sequences |

1. **Android software first:** retain Analysis default, integrate the proven
   profile-aware fact/binary split behind separate parity/admission review. Keep
   Full unchanged. Complete the explicit 5 Hz selected-source/slot ledger and overload
   witness design, including timer/admission/Stop/clock-regression/rate-change tests.
   Preserve the current capacities and 78+32 MiB; no forced GC or reduced presentation.
2. **Independent Linux option:** qualify the existing 5 Hz recorder with normal
   preview and authoritative source/Qt/sampler counters. Use legacy playback directly;
   add an immutable offline time/point/ROI report before demanding common-format
   conversion. Ten Hz is a separate option,25 Hz remains unqualified.
3. **Next authorized physical window:** only after explicit Pixel/HT availability,
   build/pin the exact chosen candidate/APK. Choose Analysis 5 Hz explicitly (or Full
   as a separately named experiment), normal presentation/acquisition and ordinary
   radiometric initialization. Check heap/native/RSS/system memory, storage, battery,
   thermal and permissions; keep all current protections. Exactly one ten-second
   Start, no retry, normal Stop or honest safety stop. Preserve original file, oracle,
   APK/source hashes, acquisition/selection/availability/drop/ACK/valid counts, receipt
   age/lateness, queue and seal times, bounded Java/native/graphics/RSS/GC points,
   first failure and terminal reason. Independently read in Python and Desktop;
   verify complete matrices/masks/context, scheduled coverage, original role hashes,
   point/ROI chronology and source immutability. No physical run is authorized here.
4. **Only then consider 30–60 s:** ten seconds alone is insufficient. Require an
   explained RSS trajectory with reserve under normal presentation, no unexplained
   loss, correct periodic coverage through unavailability/Stop, drain/finalization
   within declared bounds and independently verified usable data. If guards stop
   the shorter run or writer backlog grows, investigate first. Do not increase queues,
   cut margins or relabel gaps. Full 25 reliability remains a separate later goal.

## Reproduction and limits

All new Kotlin files are under `lmtr-field-support/src/hostTest`; Python study tools
are explicitly offline. Build with the repository's JDK 17/Gradle cache:

```bash
./gradlew --offline -p tools/lmtr-r3-host \
  :core:test :lmtr-contract:test :lmtr-recorder:test :camera-recording:test \
  :lmtr-field-support:test :lmtr-field-support:writeTestRuntimeClasspath \
  :lmtr-contract:writeRuntimeClasspath
java -Xms64m -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.OfflineProfileStudyProbe eager "$NEW_PRIVATE_EAGER"
# Run profile/profile/eager in new processes/directories after freezing the same runtime.
java -Xms64m -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.OfflineCadenceStudyProbe "$NEW_PRIVATE_CADENCE"
python3 tools/temporal_sampling_study.py "$NEW_PRIVATE_SAMPLING"
python3 -m unittest discover -s tools/tests -v
"$DESKTOP_VENV/bin/python" tools/desktop_temporal_study.py \
  --desktop "$PINNED_DESKTOP" --output "$NEW_PRIVATE_DESKTOP" --seconds 30
```

All outputs must be new private paths; preserve unsuccessful outputs. The checker expects the documented
ABBA/cadence/sampling directory layout (see its CLI). These are disposable engineering
files, never canonical corpus generation or production/UI integration.
Do not interpret a host schema/test/readback pass as ART, physical timing, steady
memory, filesystem power-loss or calibrated physical accuracy validation.

## Follow-up implementation boundary

The [experimental 5 Hz implementation](ANALYSIS_FIVE_HZ.md) supersedes the disposable
profile adapter, without changing this study's measurements or private evidence.
The historical `OfflineProfileStudyProbe eager` command above requires the frozen
`4229456` runtime; current source deliberately refuses an eager bypass of its explicit
profile guard. Use the new candidate's qualification commands for new work.
