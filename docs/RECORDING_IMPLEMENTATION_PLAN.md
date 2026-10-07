# Common recording implementation roadmap and issues

**R1a product/semantic direction accepted, 2026-10-07. Wire contract provisional.**
R1–R7 issues track the product work. R2 has isolated disposable prototypes and
[evidence](recording-r2/README.md); no production recorder/reader/UI or R3 corpus is started.

Design: [COMMON_RECORDING_DESIGN.md](COMMON_RECORDING_DESIGN.md).
Evidence: [RECORDING_COMPRESSION_STUDY.md](RECORDING_COMPRESSION_STUDY.md).
Owner decisions and normative candidate: [LMTR_FORMAT_CANDIDATE.md](LMTR_FORMAT_CANDIDATE.md).
Feasibility gates: [RECORDING_R2_VALIDATION.md](RECORDING_R2_VALIDATION.md).
The accepted [LMTX v1 still specification](LMTX_FORMAT_V1.md) and completed
Android #5 / Desktop #1 interoperability remain separate. Issue #4 keeps the
accepted still decision history; it is not silently expanded into sequences.

## GitHub issue registry

All seven issues remain open. R1a is complete, but R1 must stay open for R1b.
The native GitHub blocking graph uses R2 → R1 (R1b), so R2 is not blocked by the
already-completed R1a stage. This avoids a circular R1/R2 issue dependency.

| Stage | Tracking issue | Current gate |
|---|---|---|
| R1 | [LMThermal #6](https://github.com/LounaMaili/LMThermal/issues/6) | R1a complete; R1b awaits R2 |
| R2 | [LMThermal #7](https://github.com/LounaMaili/LMThermal/issues/7) | Ready for separately authorized feasibility; not run here |
| R3 | [LMThermal #8](https://github.com/LounaMaili/LMThermal/issues/8) | Blocked by R2 and R1b acceptance in R1 |
| R4 | [LMThermal #9](https://github.com/LounaMaili/LMThermal/issues/9) | Blocked by R3 and its prior gates |
| R5 | [LMThermal-Desktop #2](https://github.com/LounaMaili/LMThermal-Desktop/issues/2) | Development blocked by R3; real R4 output also required for interoperability completion |
| R6 | [LMThermal #10](https://github.com/LounaMaili/LMThermal/issues/10) | Blocked by both R4 and R5 |
| R7 | [LMThermal-Desktop #3](https://github.com/LounaMaili/LMThermal-Desktop/issues/3) | Later; blocked by R6 |

## Gate order

```text
R1a owner-approved product/semantic direction [complete]
  └── R2 framing, codec and storage feasibility
        └── R1b focused owner wire review / final freeze [R1 remains open]
              └── R3 canonical corpus / pure contract primitives
                    ├── R4 Android acquisition recorder
                    └── R5 Linux/Windows reader and offline adapter
                          └── R6 [requires BOTH R4 and R5]
                                └── R7 later analysis / explicit legacy conversion
```

R2 may refine technical recommendations and feeds the explicit R1b review. Do not
treat benchmark success or document merge as owner acceptance of the wire contract.
Schema/framing changes during prototyping must not masquerade as released v1.
Small disposable fault-test prototypes in R2 are not a production recording feature.
The first product milestone should be foreground-scoped; background recording
requires its own lifecycle/service decision and validation.

## Owner product decisions — accepted for this stage

| Owner decision | Accepted outcome | Constraint |
|---|---|---|
| Default evidence level | Analysis; clearly present choice on first recording for a module, remember the last explicit per-module choice and show it before Start | Analysis does not imply native/source preservation |
| Advanced manual role selection | No individual payload checkboxes initially; expert mode deferred | Later mode cannot omit full temperature, validity, required context or truthful timing/gaps, or permit ROI source crop |
| Maximum evidence label | Full / Research, describing actual module roles and meaningful added evidence | No better calibration/resolution/accuracy claim; unavailable when no useful additional evidence |
| Storage warnings/gates | Compact approximate rates/space/time where known; meaningful low-space warnings. Block clearly insufficient known startup+reserve space. Unknown capacity: concise warning and allowed Start. Refusal: stop, preserve prior commits and report full/failure | No guarantees from compressed estimates or invented duration; thresholds remain UI policy; pending frames are never called saved |

These four decisions were explicitly supplied by the owner on 2026-10-07; no repeat
product answer is required now. Container/chunk/index/resource mechanics, restricted
views and codecs remain candidate technical choices requiring R2 proof and R1b
review. `.lmtr` / `lmthermal-recording` are not stable published identifiers yet.

## R1 — Freeze common radiometric recording semantics and later wire contract

**Repository:** LMThermal. **Status:** R1a complete; keep open for R1b, which awaits R2.

Scope: record the owner-approved [R1a normative candidate](LMTR_FORMAT_CANDIDATE.md)
and product/semantic decisions separately from exact mechanics. Keep LMTX v1
unchanged. R1b later reviews R2's evidence packet and proposed identifiers/version,
JSON grammar, features/views, magic/header/footer/checksum/hash/commit/index/END
layout, codec IDs/set, limits and backend-specific assurance before final wire
freeze. A prototype cannot silently weaken the accepted semantic invariants.

Acceptance:

- [x] Explicit owner product decisions and semantic direction recorded as R1a;
  full grid, roles, truthful retention, exact Celsius, losslessness, clocks/gaps,
  recovery goals, source immutability and legacy coexistence are distinguished.
- [x] Restricted views and identifiers/mechanics remain candidate/provisional;
  R2 gates and later R1b acceptance are explicit; no production work authorized.
- [ ] R2 evidence packet reviewed, with failures/untested configurations and
  semantic consequences resolved explicitly rather than hidden.
- [ ] R1b owner acceptance recorded for the precise full wire contract and chosen
  mandatory codec set/backend assurance; specification/version/identifiers frozen.
- [ ] Resource/feature/index/corruption semantics precise enough to freeze R3 corpus
  bytes; still and historical readers remain independent. Only then close R1.

## R2 — Validate recording framing, codecs and Android storage/recovery

**Repository:** LMThermal, with Desktop read-only or a separate focused validation
branch if required. **Status:** the authorized R2 feasibility task has executed.
The [R2 execution packet](recording-r2/README.md) now records
prototypes, measurements and outstanding gates. R2 remains open and returns
evidence to R1b before stable release.

Scope: controlled prototypes/benchmarks and provider fault tests, using owned
current measurements and saved frames. Compare STORED/DEFLATE-1/Zstd-3 on genuine
changing scenes, masks, settings changes and alternate geometry. Validate the
draft one-second/16 MiB preferred chunk, draft 64 MiB hard budget and byte-bounded
handoff against actual Android peak heap and 25 FPS acquisition/thermometry cost.
These constants remain provisional. No new thermometry algorithm or unsafe USB fault.

Acceptance:

All applicable baseline gates A–F and the exit packet in
[RECORDING_R2_VALIDATION.md](RECORDING_R2_VALIDATION.md) must pass. This includes
real ~25 FPS Android Analysis/Native/Full tests, byte-exact codecs, forward chunks/
paged indexes, faults, adversarial views and backend-specific storage proofs.

- Record codec ratio, wall/CPU, allocation, sustained throughput, throttling/power
  and Linux/Windows decoder parity; exact role bytes survive every roundtrip.
- Pin any Zstd dependency/license and decoder window/dictionary restrictions;
  optional feature negotiation verified, no silent unavailable-codec downgrade.
- Exercise local commit barriers, torn body/footer/index/END, storage-full and
  killed-process recovery. Previously committed chunks remain accessible; no
  claim that software simulation alone proves hardware power-loss durability.
- SAF test matrix distinguishes local seekable destinations, unknown durability,
  pipes/remote providers, URI permission loss, cancellation, failed close/readback.
- Verify no mandatory full second copy for canonical app-owned access; document
  backup/uninstall implications and guarantees of direct/hybrid storage honestly.
- Validate unknown-capacity Start/warning and known insufficient startup+reserve
  policy independently of backend durability; stop/refusal never counts pending saved.
- Propose exact framing/index/constants and conservative device budgets, with
  mandatory codec decision and supported backend assurance. Return to R1b;
  do not declare the wire contract or corpus frozen as an R2 side effect.

## R3 — Build shared .lmtr conformance corpus and pure contract primitives

**Repository:** LMThermal (canonical corpus); Desktop consumes the **same bytes**
through its own focused branch. **Status:** blocked; R2 baseline gates and explicit
R1b final wire acceptance are required. R1a alone does not unblock canonical bytes.

Scope: pure Kotlin/JVM contract primitives and independent Python framing checks,
not Android camera/UI work. Corpus covers every case in design §17, including
native-only non-raw14, temperature-only, maximal distinct evidence, exact native
view if accepted in R1b, alternate geometry, zero/partial validity, calibration
changes, all gap types, multi-chunk/index recovery, unknown optional JSON/binary,
unsupported required features, corrupt hashes and preview-only. Synthetic-large generation
must be deterministic and streamed, not a huge checked-in file.

Acceptance:

- Same serialized bytes/hashes expected on Kotlin, Linux and Windows; temperature
  bits, masks, accepted sample views, context precision and deterministic stats agree.
- Sparse >4 GiB offsets and multi-hour metadata indexes work within bounded heap;
  no whole-sequence load, allocation from unverified lengths, path/URI extraction
  or silent integrity-error-to-gap conversion.
- Recovery never alters originals, fabricates tail count/end time, or bypasses
  unsupported required semantics.
- Document fixture synthetic/real provenance and privacy, with deliberate small
  sanitized real frames only when needed and authorized.

## R4 — Implement Android generic radiometric recorder

**Repository:** LMThermal. **Status:** blocked; R3, R1b and R2's selected storage/
memory/codec assurance must be satisfied before production implementation.

Scope: a separate module-owned recording retention/evidence contract; common
recorder consumes immutable owned current observations. Analysis/Native/Full
resolve from actual support, with original descriptors and explicit unavailable
reasons. Write lossless chunk streams, exact complete temperature/mask/native/
acquisition, context snapshots/events, integer clocks, slot/source/recorded
sequences and gap/drop records. HT Full uses bounded native views only if R1b
accepts that candidate feature; another module must not inherit that assumption.

Acceptance:

- No camera protocol/thermometry changes, automatic initialization, display-word
  masking, matrix recomputation or cropped ROI recording.
- Acquisition/UI never waits for compression; bounded bytes, explicit writer
  overload, pending versus durable counters, graceful drain and error stop.
- Start/rate/profile resolution is truthful for temperature-only, native,
  acquisition and preview-only sources; no hidden downgrade or fake evidence.
- Complete exact settings/calibration changes; no approximate dedup of dynamic
  inputs or opaque fields. Chunk-local interpretation remains self-contained.
- Simulated Stop/detach/crash/full-storage recovery passes before field tests;
  no automatic in-place append to a recovered source.

## R5 — Implement Desktop .lmtr reader/playback on Linux and Windows

**Repository:** LMThermal-Desktop. **Status:** blocked on R3 and final R1b contract;
real Android output from R4 is additionally required for interoperability completion.

Scope: independent strict `.lmtr` reader with paged lazy indexes, bounded role
decode/cache, required-feature checks, explicit integrity errors, read-only recovery
and a common offline sequence model. Retain strict legacy `.lmthermal` and LMTX
still loaders without relabeling or automatic conversion. Playback uses stored
Celsius and no camera discovery/import requirement on Windows.

Acceptance:

- Linux and real Windows consume common corpus and Android production bytes with
  exact Float32/mask/native/acquisition/view parity and immutable source hashes.
- Scrub/step, blank gaps, point inspection, new inspection ROI, rerender palettes/
  ranges, frame/PNG export and meaningful time navigation without thermometry rerun.
- Full-grid post-acquisition ROI statistics are independent of Android's ROI;
  unavailable measurements expose no stale cursor or ROI values.
- Corrupt final index recovery works, corrupt promised payload is an integrity
  failure, and malicious bounds/unknown required codecs are rejected explicitly.
- Legacy recording/playback/capture/LMTX regression suites remain passing on both
  platforms. No implicit upgrade/rewrite of source formats.

## R6 — Add Android recording product UI/storage and cross-platform interoperability

**Repository:** LMThermal, coordinating a separate Desktop validation branch.
**Status:** blocked on both R4 and R5 plus selected R2 backend assurance. The four
owner product decisions are resolved; they are not an outstanding blocker.

Scope: understandable recording choices, rate/duration/space estimates, explicit
Start/Stop and pending/committed/drop status, canonical durable app-owned storage,
read-only no-copy access and optional independent export. Direct local SAF recording
is enabled only for validated backends; pipes/remote destinations are not silently
called durable. Recordings and prepared immutable stills remain separate artifacts.

Acceptance:

- Rotate/retained UI transitions do not create another camera owner/control
  sequence; foreground exit/screen-off/locale/detach/error follows the approved
  stop/seal policy. No background service implied by a tracked destination picker.
- Storage estimates include requested physical roles after view dedup, masks,
  overhead, worst-case compression, reserve and unknown provider capacity.
- First-module preservation choice is visible; remembered explicit per-module choice
  stays visible before Start. No low-level payload checkboxes initially. Unknown
  capacity warns concisely and allows Start on supported storage; known insufficient
  startup+reserve blocks. Normal operation uses compact status, not repeated nagging.
- No mandatory second full copy for finalized canonical access; independent export
  success requires destination verification and keeps source unchanged. Consent,
  privacy, access lifetime and uninstall/backup limitations are visible.
- Real changing-scene recordings at agreed rates and lengths remain responsive;
  explicit overload/transient/gap evidence is preserved. Desktop Linux/Windows
  replay exact data, create new ROI and preserve all source bytes.
- Retain **Native-equivalent temperatures; absolute physical accuracy not yet
  independently validated.** No assumed hand temperature as calibration evidence.

## R7 — Add later time-series analysis and explicit legacy conversion

**Repository:** LMThermal-Desktop first; shared semantic review in LMThermal.
**Status:** later/blocked on R6; separate future analysis-project/lineage contract
if persisted. No automatic legacy rewrite or new acceptance of that sidecar now.

Scope: multiple ROI/point/annotation timelines, optional tracking, valid-only time
graphs, explicit time/geometry alignment between recordings, visual video rendering
and opt-in legacy conversion. This is later product work, not required to prove the
first common recording's full-grid authority. Persist analysis independently of
large immutable sources; new recalculated recordings retain source lineage.

Acceptance: no mandatory cached per-frame ROI stats, no gaps filled with previous
values, no invented transport/time accuracy in legacy conversions, explicit derived
algorithm/source identity, no source rewrite, and still export follows Accepted
LMTX v1 rather than silently adding recording semantics.

## Independent reliability follow-up

The previously reported blank initial connection sometimes requiring replug remains
unexplained. It is a separate camera/session start reliability investigation;
no fix, protocol assumption or format accommodation is part of R1 or this branch.
Future recording acceptance must report this boundary if it prevents a usable
current source. Format correctness cannot certify a connection that produces no
current measurements.
