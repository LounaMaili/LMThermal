# LMThermal Recording — normative candidate, R1a

**R1a product/semantic direction: owner-accepted, 2026-10-07.**
**Complete wire contract: provisional; not published or frozen.**

R2 now has [isolated prototypes and an evidence packet](recording-r2/README.md).
The [exact experimental framing annex](recording-r2/FRAMING_PROPOSAL.md) is a proposal
for R1b, not acceptance. Historical failed live matrices remain reported; the
[measured continuation](recording-r2/CONTINUATION_RESULTS.md) and
[native Windows packet validation](recording-r2/WINDOWS_VALIDATION.md) now satisfy
the recommended baseline gates. R2 is ready for completion review, with memory
sampling/backend assurance limits explicit. R1b owner wire approval remains separate.

Authority: the owner explicitly supplied the decisions and semantic acceptance
scope on 2026-10-07. This document records that scope; it does not infer acceptance
of every recommendation in the [architecture draft](COMMON_RECORDING_DESIGN.md).
MUST/MUST NOT below apply to the accepted semantic direction. Sections explicitly
marked **candidate** or **provisional** remain subject to R2 evidence and R1b review.
There is no permission to implement a production recorder/parser in this task.

The [roadmap](RECORDING_IMPLEMENTATION_PLAN.md) tracks R1–R7.
[R1 #6](https://github.com/LounaMaili/LMThermal/issues/6) records the owner decisions
and remains open:
R1a is complete, R2 feasibility follows, and **R1b owner review must accept the
actual wire contract before the R3 corpus becomes canonical**. An architecture
merge, successful prototype or desktop benchmark is not final wire acceptance.

## 1. Status and authority boundaries

| Area | Status |
|---|---|
| Owner product choices in §2 | Accepted for the next design/development stage |
| Full-grid, roles, retention, stored-temperature authority, losslessness, clocks/gaps, immutable source and coexistence in §§3–6 | Accepted semantic direction (R1a) |
| Previously committed valid chunks remain recoverable; pending frames are never claimed saved; storage failure stops intake | Accepted recovery goals, with backend assurance to be established by R2 |
| HT-301 deduplication in §7 | Preferred module-specific direction; restricted views remain a candidate feature pending R2 |
| `.lmtr`, `lmthermal-recording`, `kind=recording`, recording schema/container generation | Separate recording identity is the accepted direction; literal identifiers/version are provisional until final review |
| Precise JSON field layout, binary encodings/framing, checksum/index/commit mechanics, codecs and numerical resource ceilings | Candidate technical details, not frozen |
| Android sustained throughput, power/memory and storage-backend durability | Not established by the desktop codec study; R2 must provide evidence |

The accepted [LMTX v1.0 still contract](LMTX_FORMAT_V1.md) is unchanged. It remains
`lmthermal-exchange`, `.lmtx`, `kind=still`, with its own canonical specification.
The recording candidate MUST NOT be represented as an accepted extension of that
still contract. Still issue #4 retains its existing design/decision history.

## 2. Explicit owner product decisions

### 2.1 Analysis default and remembered choice

The default suggested radiometric preservation level is **Analysis**. It preserves
the complete temperature field and required validity, interpretation context,
provenance and truthful timeline/gaps. It MUST NOT imply native/raw/acquisition
preservation when those roles are omitted.

Before Start, the selected level MUST be clearly visible. On the first recording
for a camera module, the product MUST present the preservation choice rather than
silently hiding it. An automatically suggested default is not already an explicit
remembered choice. Remember the user's **last explicit selection per camera module**,
not a global choice or an inferred USB-device identity. Returning users MAY reuse
that selection, while still seeing it before Start. If it is no longer meaningful
for current capabilities, expose the change and obtain a suitable choice; do not
silently downgrade or invent retained roles. Exact interaction/layout is later UI work.

### 2.2 Initial levels; manual mode deferred

Initial UI levels are conceptually Analysis, Native and Full / Research. Individual
low-level payload checkboxes MUST NOT be exposed in the initial recording UI.
Expert selection MAY be added later, but MUST still preserve complete temperature
planes for radiometric measurements, validity, required interpretation context and
truthful timing/gaps. It MUST NOT permit ROI cropping of the measurement source.

### 2.3 Full / Research naming and meaning

Use the provisional user-facing label **Full / Research** for maximum meaningful
module evidence: reverse engineering, algorithm research, future reinterpretation
and reproducibility. Describe the actual roles supplied by the selected module.
This level MUST NOT claim better physical calibration, higher thermal resolution
or more accurate temperatures. A module with no meaningful extra evidence beyond
Analysis/Native MUST NOT offer Full / Research.

### 2.4 Helpful storage assistance and failure policy

Before Start, where reliable information exists, show the selected profile,
approximate physical/compressed rate, available space and estimated remaining
recording time. Conservative/raw estimates and rolling observed compressed rates
MAY both inform the display; scene-dependent compression MUST NOT be guaranteed.

During normal recording, a compact storage/remaining-time indicator is sufficient.
Warn when storage becomes meaningfully low; do not repeatedly nag during normal
operation. Exact warning thresholds are implementation/UI policy, **not frozen wire
semantics**. Intended-duration forecasts do not promise successful completion.

| Capacity/write condition | Accepted product behavior |
|---|---|
| Known, clearly insufficient for safe startup plus required reserve/chunks | Block Start and explain the insufficiency |
| Reliable remaining capacity unavailable | Show **unknown**, provide a concise warning, and allow recording on an otherwise supported backend. Do not block solely because capacity is unknown or fabricate a remaining duration |
| Known low remaining capacity during recording | Warn appropriately and attempt graceful stop before exhaustion where possible; thresholds are later policy |
| Exhaustion or backend write refusal | Stop intake; preserve prior valid committed chunks; safely seal/finalize what remains possible; report storage full if established, otherwise storage failure |

Never count pending/uncommitted frames as saved. Graceful partial preservation is
the goal, not prevention of every out-of-space event. Capacity knowledge is separate
from backend functionality/durability: unknown space is not an unsupported-backend
test, and a known capacity does not prove reliable commits. Backend support and
assurance must be justified in R2 without treating all SAF providers alike.

## 3. Full-field measurement and generic evidence

Every recorded radiometric measurement frame MUST retain its complete primary
native thermal grid. ROI, points and annotations are optional analysis; palettes,
ranges and visual rotation are presentation. Neither can crop, downsample, replace
or mutate the source measurements. The full field remains available for entirely
new Desktop ROI after acquisition.

The common model distinguishes these roles independently of camera/vendor:

| Role | Semantic authority |
|---|---|
| Temperature | Complete numerical measurement grid when available; stored Celsius is authoritative for ordinary playback/analysis |
| Validity | Explicit availability of measurement pixels; invalid/missing data cannot acquire apparent temperatures from display values or sentinels |
| Native samples | Optional module-native samples with encoding/dtype/endian/shape/module meaning; not universally raw14 |
| Acquisition/source evidence | Optional exact lower-level device/transport/frame/packet bytes; no invented transport when inaccessible |
| Structured context | Available required calibration/settings/environment, algorithm identity and provenance, with explicit unknown/partial facts |
| Visual streams | Optional preview/thumbnail/future visible imagery with independent geometry and availability; not numerical authority |

Stored source Float32 Celsius values MUST be preserved exactly where applicable.
Ordinary playback MUST NOT require thermometry recomputation. Primary temperatures,
validity, requested native/source bytes and required context are lossless; lossy
preview/video is permitted only as explicitly separate visual data. Exact finite
values, masks, row-major/native coordinates and deterministic statistics remain
aligned with still semantics; the precise recording encoding/field grammar is
part of the candidate to be finalized, not a modification of LMTX v1.

Capability facts describe what a module can supply; each observation describes what
was actually available and retained. Temperature-only, temperature+native,
temperature+acquisition, all-three and truthful preview-only modules are supported
conceptually. A preview-only recording MUST NOT invent Celsius or claim radiometric
measurement. No HT-301 shape, trailer or native bit range is universally mandatory.

## 4. Preservation levels and actual retention

| Level | Semantic contents |
|---|---|
| Analysis | Complete current temperature measurements, validity, required available interpretation context/provenance, truthful timing and gaps; optional analysis/presentation. Native/acquisition may be omitted |
| Native | Analysis plus actual native samples for applicable frames |
| Full / Research | Analysis plus maximum available native/acquisition/context/extensions and useful bounded uninterpreted source evidence |

Expose only levels adding meaningful evidence for that module. The file's actual
role descriptors MUST be authoritative; a profile label is product policy, not
proof of contents. Missing requested evidence has an explicit reason and is not
manufactured. No hidden profile downgrade or automatic ROI-only recording is
allowed. Evidence MUST be coherent with its original observation and owned safely
for asynchronous use, rather than paired with unrelated latest camera buffers.

Each measurement MUST have unambiguous relevant interpretation state. Stable
calibration/settings can use state/event references rather than needless per-frame
copies; real dynamic calibration inputs MUST NOT be discarded or approximately
deduplicated. Generic and module-specific context both remain representable.
Exact state/chunk closure and extension grammar are candidate schema details.

## 5. Clocks, availability and gaps

Keep distinct integer-based acquisition clocks (when genuinely known), host
monotonic receipt, recording-relative timeline and creation/finalization UTC.
Representations MUST preserve precision across Kotlin/Python; float-rounded counters
or invented acquisition UTC are not acceptable. Clock domains/quality and unknown
values remain explicit. JSON number/string representation and binary widths are
candidate wire decisions for R2/R1b.

Distinguish source/observation sequence, expected/scheduled slots where known and
actually recorded frames. Nominal FPS is not proof of a missing vendor sequence
or a promise that all frames were valid. Missing counts/timestamps stay unknown
unless supported by observations or a declared sampling schedule.

At minimum distinguish valid measurement, observed-but-unavailable/invalid
measurement and missing/dropped observation. Report acquisition loss, application/
writer loss, transient/held rejection and session interruption separately where
the evidence supports it. Missing frames MUST NOT be filled with the previous
matrix, and storage errors MUST NOT be disguised as valid measurements.

## 6. Recovery, immutability and coexistence

The architecture MUST bound pending work and isolate complete committed chunks
so prior valid commits can remain readable after process termination, torn tail,
detach, write failure or storage exhaustion. A corrupt final index MUST NOT by
itself require losing otherwise valid committed chunks. Stop/finalization MUST NOT
require rewriting all payload bytes of a multi-GB source.

Pending means not yet safely committed; incomplete tail data cannot be advertised
as saved measurements. Recovery reports verified boundaries and unknown tail/end
facts honestly, rather than guessing a missing END timestamp. Exact commit markers,
barriers, checksum coverage, recovery scanning and backend durability guarantees
remain provisional until tested. Software checks cannot prove every provider or
physical storage device will honor persistence barriers.

Finalized source recordings are immutable. Later ROI/presentation/derived analysis
MUST NOT rewrite them. Recalculated measurements are new derived results with
explicit lineage, not silent replacement of originally stored authoritative Celsius.

LMTX still v1, legacy Desktop still and `lmthermal-radiometric-recording` integer
v1 MUST remain independently readable; no automatic relabel/rewrite. A future
shared offline sequence adapter does not relax original format validators.

## 7. Preferred HT-301 optimization and candidate views

For HT-301 only, complete acquisition transport is 224256 bytes, containing the
221184-byte native raw14 image plus 3072 additional bytes. Preferred Full / Research
physical retention is temperature + complete transport, with native samples exposed
as an exact view when the candidate feature passes R2. This avoids **19.90656 decimal
GB/hour** of uncompressed duplicate native bytes at nominal 25 FPS. The calculation
and [existing desktop study](RECORDING_COMPRESSION_STUDY.md) are unchanged.

**Restricted views remain a candidate semantic feature, not an accepted mandatory
format feature.** The candidate constraints are same frame/chunk, physical
materialized parent, one contiguous offset/length, no nested views/strides/cross-frame
references/transforms, exact length/shape/dtype/endian, independent logical hash,
and checked bounds before exposure. R2 must prove safe serialization, extraction,
corruption handling and Android/Desktop implementation practicality before R1b
accepts the feature and its wire representation.

Other modules need not use views. Independent native and acquisition payloads
remain valid when they contain distinct data; no universal transport-subsumes-native
assumption is made. Existing LMTX still storage is not changed to this optimization.

## 8. Identity and wire details deliberately provisional

Prefer a separate recording family: `.lmtr`, `format=lmthermal-recording`,
`kind=recording`. Literal identifiers and final schema/container version are **not
published stable identifiers**. Long append-only recordings differ fundamentally
from stills in container, limits, index, recovery and publication. Existing still
readers MUST NOT misinterpret them as still captures.

The architecture's append-only design, preferred ~1-second/16 MiB chunks, draft
hard bounds, checkpoint/index tree and codec recommendations are inputs to R2,
not frozen wire constants. The following require R2 evidence and R1b acceptance:

- Magic bytes; exact record/header/footer layout and length encoding.
- Checksum fields and coverage, hashes/framing binding, commit marker encoding.
- Exact index-page/root/checkpoint/END binary encoding and recovery traversal.
- Codec numeric IDs, wrappers, dependency/window policy and final mandatory set.
- Chunk framing/resource constants and Android bounded-memory budgets.
- Exact recording JSON schema, required-feature/extension declarations and IDs.
- Backend-specific synchronization/publication/durability claims.

R2's [validation gates](RECORDING_R2_VALIDATION.md) must produce a reproducible
evidence packet and revised candidate. R1b then reviews the actual byte/JSON
contract, chosen codec set, supported backend assurance, provisional identifiers
and any semantic consequences of feasibility findings. Changed R1a invariants
require explicit renewed owner review, not silent prototype-driven weakening.

No remaining choice among the four supplied product decisions needs another owner
answer in this task. R1b final wire acceptance remains a planned future owner action.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
