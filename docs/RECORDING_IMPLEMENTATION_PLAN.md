# Common recording implementation roadmap and proposed issues

**Review draft, 2026-10-06. No implementation issue is created or accepted by
this document. No recorder/reader/UI implementation is started.**

Design: [COMMON_RECORDING_DESIGN.md](COMMON_RECORDING_DESIGN.md).
Evidence: [RECORDING_COMPRESSION_STUDY.md](RECORDING_COMPRESSION_STUDY.md).
The accepted [LMTX v1 still specification](LMTX_FORMAT_V1.md) and completed
Android #5 / Desktop #1 interoperability remain separate. Issue #4 keeps the
accepted still decision history; it is not silently expanded into sequences.

## Gate order

```text
R1 owner review / frozen recording semantics
  └── R2 framing, codec and storage feasibility
        └── R3 shared conformance corpus / pure contract primitives
              ├── R4 Android acquisition recorder
              └── R5 Linux/Windows reader and offline adapter
                    └── R6 Android product storage/UI + real cross-platform acceptance
                          └── R7 later analysis / explicit legacy conversion
```

R2 may refine technical recommendations and feed another R1 review. Do not treat
benchmark success or document merge as owner acceptance of a normative contract.
Schema/framing changes during prototyping must not masquerade as released v1.
Small disposable fault-test prototypes in R2 are not a production recording feature.
The first product milestone should be foreground-scoped; background recording
requires its own lifecycle/service decision and validation.

## Product choices for review

| Owner decision | Recommended outcome | Reason |
|---|---|---|
| Default evidence level | Analysis, with explicit remembered per-module user choice shown before Start | Complete numerical field remains available without charging every user for research evidence |
| Advanced manual role selection | Defer from first UI; allow later within mandatory full-temperature/validity/context constraints | Simple understandable levels cover the current cases; manual complexity does not relax measurement invariants |
| Maximum evidence label | Full / Research, with actual module role summary | Makes extra preservation purposeful; does not imply better physical temperature calibration |
| Storage warnings/gates | Block known insufficient capacity; show worst-case and rolling estimates, explicit unknown-capacity/durability warning | A compression ratio is not a guaranteed recording duration; unknown provider facts must stay unknown |

No owner answers are assumed here. Container/chunk/index/resource limits and
technical codecs are evaluated implementation decisions, with explicit feasibility
gates, rather than additional low-level questions for the owner. Provisional name
`.lmtr` / `lmthermal-recording` becomes stable only in the accepted specification.

## Proposed R1 — Review and freeze the common radiometric recording contract

**Repository:** LMThermal. **Blockers:** owner review of this draft and product choices.

Scope: commit the accepted recording specification as a separate canonical document
after explicit owner approval. Keep LMTX still v1 unchanged. Freeze format ID,
extension, schema/container generation, role/capability/availability semantics,
full-grid numerical authority, bounded views, timelines, recovery and error classes.
The reviewed binary framing annex must define exact magic, endian/header/footer
layout, hashes/checksum coverage, offsets, codec wrapper IDs, required features,
record type ordering, index pages/root, commit markers and END semantics.

Acceptance:

- Explicit owner acceptance recorded, with decisions and residual constraints;
  draft wording is not called Accepted prematurely.
- Complete finite Float32/mask semantics, exact bytes, no ROI crop/downsample,
  no timestamp invention, bounded same-frame physical-parent views.
- Resource ceilings compose; hostile lengths, JSON precision, required features,
  index corruption and partial chunks have unambiguous outcomes.
- Published stills and historical Desktop formats stay independently readable;
  no old reader interprets a recording as a still.
- No production writer/UI work bundled into the contract decision.

## Proposed R2 — Validate framing, codecs and Android storage feasibility

**Repository:** LMThermal, with Desktop read-only or a separate focused validation
branch if required. **Blocker:** agreed R1 semantics; technical results may reopen
the draft before a stable wire release.

Scope: controlled prototypes/benchmarks and provider fault tests, using owned saved
frames. Compare STORED/DEFLATE-1/Zstd-3 on genuine changing scenes, masks, settings
changes and alternate geometry. Validate a one-second/16 MiB preferred chunk,
64 MiB hard budget and byte-bounded handoff against actual Android peak heap and
25 FPS acquisition/thermometry cost. No new thermometry algorithm or unsafe USB fault.

Acceptance:

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
- Finalize technical framing/index constants and conservative device budgets;
  update the reviewed draft before stable corpus bytes are frozen.

## Proposed R3 — Add shared recording conformance corpus and pure primitives

**Repository:** LMThermal (canonical corpus); Desktop consumes the **same bytes**
through its own focused branch. **Blockers:** R1 accepted + R2 wire/codec decisions.

Scope: pure Kotlin/JVM contract primitives and independent Python framing checks,
not Android camera/UI work. Corpus covers every case in design §17, including
native-only non-raw14, temperature-only, maximal distinct evidence, exact native
view, alternate geometry, zero/partial validity, calibration changes, all gap
types, multi-chunk/index recovery, unknown optional JSON/binary, unsupported
required features, corrupt hashes and preview-only. Synthetic-large generation
must be deterministic and streamed, not a huge checked-in file.

Acceptance:

- Same serialized bytes/hashes expected on Kotlin, Linux and Windows; temperature
  bits, masks, sample views, context precision and deterministic stats agree.
- Sparse >4 GiB offsets and multi-hour metadata indexes work within bounded heap;
  no whole-sequence load, allocation from unverified lengths, path/URI extraction
  or silent integrity-error-to-gap conversion.
- Recovery never alters originals, fabricates tail count/end time, or bypasses
  unsupported required semantics.
- Document fixture synthetic/real provenance and privacy, with deliberate small
  sanitized real frames only when needed and authorized.

## Proposed R4 — Implement generic Android evidence retention and bounded recorder

**Repository:** LMThermal. **Blockers:** R3; R2 local storage assurance/budgets.

Scope: a separate module-owned recording retention/evidence contract; common
recorder consumes immutable owned current observations. Analysis/Native/Full
resolve from actual support, with original descriptors and explicit unavailable
reasons. Write lossless chunk streams, exact complete temperature/mask/native/
acquisition, context snapshots/events, integer clocks, slot/source/recorded
sequences and gap/drop records. HT Full may use the accepted bounded native view;
another module must not inherit that assumption.

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

## Proposed R5 — Implement common recording reader and offline sequence adapter

**Repository:** LMThermal-Desktop. **Blocker:** R3; real Android output from R4 for
interoperability completion (development can use the shared corpus).

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

## Proposed R6 — Android recording controls, publication and real interoperability

**Repository:** LMThermal, coordinating a separate Desktop validation branch.
**Blockers:** R4/R5; selected product decisions and storage backend assurance.

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
- No mandatory second full copy for finalized canonical access; independent export
  success requires destination verification and keeps source unchanged. Consent,
  privacy, access lifetime and uninstall/backup limitations are visible.
- Real changing-scene recordings at agreed rates and lengths remain responsive;
  explicit overload/transient/gap evidence is preserved. Desktop Linux/Windows
  replay exact data, create new ROI and preserve all source bytes.
- Retain **Native-equivalent temperatures; absolute physical accuracy not yet
  independently validated.** No assumed hand temperature as calibration evidence.

## Proposed R7 — Later time-dependent analysis and explicit legacy conversion

**Repository:** LMThermal-Desktop first; shared semantic review in LMThermal.
**Blocker:** R6; separate future analysis-project/lineage contract if persisted.

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
