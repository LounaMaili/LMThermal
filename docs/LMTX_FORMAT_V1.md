# LMThermal Exchange Format v1.0

| Property | Value |
|---|---|
| Format name | LMThermal Exchange Format |
| Extension | `.lmtx` |
| Format ID | `lmthermal-exchange` |
| Schema version | 1.0 (`major=1`, `minor=0`) |
| Status | **Accepted** |
| Scope | Still captures only; no sequence/video schema in v1.0 |

## Authority and implementation status

This Git-tracked document is the **canonical implementation specification** for
LMThermal Exchange Format v1.0. It contains the complete normative contract accepted
in [LMThermal issue #4](https://github.com/LounaMaili/LMThermal/issues/4), based on
`v1.0-review-1` plus the [explicit owner acceptance and JSON-value-preservation
clarification](https://github.com/LounaMaili/LMThermal/issues/4#issuecomment-5972856073).
Issue #4 remains the architecture/decision/review history.

MUST, MUST NOT, SHOULD and MAY define normative contract requirements. Acceptance
and publication of this specification do not claim that implementations exist.
[Android export #5](https://github.com/LounaMaili/LMThermal/issues/5) and
[Desktop import #1](https://github.com/LounaMaili/LMThermal-Desktop/issues/1) are
unblocked with respect to the format dependency, but remain open and unimplemented.
This documentation task adds no producer, importer, fixtures or application changes.

The accepted owner clarification is included in §3 and applied in §11: unknown
optional JSON fields preserve semantic JSON values with sufficient numeric precision;
original number spellings need not be retained. Binary payload bytes remain exact
where the contract requires it.

Implementations must not silently change v1 semantics. Incompatible changes follow
the versioning rules in §12; compatible minor additions preserve existing meanings
and required semantics.

## Contents

1. [Identity, scope and layers](#1-identity-scope-and-layers)
2. [ZIP profile, paths and ceilings](#2-zip-profile-paths-and-ceilings)
3. [JSON and primitive rules](#3-json-and-primitive-rules)
4. [Required manifest skeleton](#4-required-manifest-skeleton)
5. [Clock records](#5-clock-records)
6. [Payload descriptors and canonical bytes](#6-payload-descriptors-and-canonical-bytes)
7. [Temperature mask, inspection and deterministic statistics](#7-temperature-mask-inspection-and-deterministic-statistics)
8. [Provenance, evidence and privacy](#8-provenance-evidence-and-privacy)
9. [Module extensions and required features](#9-module-extensions-and-required-features)
10. [Embedded analysis and presentation](#10-embedded-analysis-and-presentation)
11. [Immutability and derived-file lineage](#11-immutability-and-derived-file-lineage)
12. [Publication, integrity errors and compatibility](#12-publication-integrity-errors-and-compatibility)
13. [Canonical small masked example](#13-canonical-small-masked-example-synthetic-not-a-camera-fixture)
14. [Minimum conformance plan and implementation gate](#14-minimum-conformance-plan-and-implementation-gate)

## 1. Identity, scope and layers

- Product: `LMThermal Exchange Format`; extension: `.lmtx`; content ID: `lmthermal-exchange`; proposed MIME: `application/vnd.lmthermal.exchange+zip`. MIME registration is not claimed. `application/zip` MAY be a transport fallback. Readers MUST inspect archive/manifest identity; a filename/MIME alone proves neither format nor validity.
- Schema `major=1`, `minor=0`; `kind=still`. No sequence/video schema is defined. Future sequences may reuse geometry, descriptors, extensions, provenance and integrity but need separately reviewed timing/chunk/gap/recovery semantics.
- Required source identity/native geometry is independent of USB/UVC, camera brand, transport availability and raw encoding. HT-301 is the only currently hardware-validated commercial module; simulated/alternate geometry cases do not establish another real driver.
- Source/measurement, optional analysis and optional presentation are separate. Loading is camera-free and MUST NOT open a source, send controls, infer initialization, or recompute thermometry. Saved temperatures are authoritative for ordinary analysis; previews never supply temperatures.
- A capture MUST have at least one nonempty source payload: temperature plane, native-sample plane, preview, visible-source image or identified opaque acquisition evidence. Metadata/extension-only files are not captures. A capture without temperatures MAY be valid. Opaque-only captures MUST report that generic analysis is unavailable, not invent temperatures. A visible-source image has its own explicit geometry; it is not implicitly registered to thermal pixels.

## 2. ZIP profile, paths and ceilings

An archive MUST use an ordinary single-volume ZIP with a complete end record and central directory. Members are regular data files only. STORED (method 0) and DEFLATE (method 8) are permitted; no other compression, encryption, split/multidisk, ZIP64 records/extra fields, self-extracting preamble, executable semantics, links, directory entries or recursively interpreted nested archives are permitted. A nested archive MAY be retained as opaque module evidence and MUST NOT be opened recursively. Producers SHOULD omit ZIP comments and identifying OS metadata. DOS ZIP timestamps are non-authoritative; acquisition/creation clocks come only from the manifest.

Readers MUST cross-check local/central names, methods, sizes, CRC and offsets using ZIP rules, including valid non-ZIP64 data descriptors when present. Truncated, overlapping, out-of-range or contradictory records are errors. Extra-field records MUST be length-checked; unknown non-semantic ZIP metadata MAY be ignored, never executed or used as a second path/measurement authority. Link/special-file/executable attributes MUST be rejected. ZIP mechanics follow the [PKWARE ZIP specification](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT); the restrictions here define the narrower LMTX profile.

| Owner-accepted ceiling (inclusive) | Exact bytes |
|---|---:|
| Uncompressed `manifest.json` | 1,048,576 (1 MiB) |
| Any uncompressed member, including opaque evidence | 134,217,728 (128 MiB) |
| Complete archive, including headers/directory | 268,435,456 (256 MiB) |
| Sum of all uncompressed members, including manifest | 536,870,912 (512 MiB) |

These values are internally consistent: each member fits below the total budget; compressed members can together exceed the on-disk ceiling without exceeding the decompressed ceiling. All four conditions apply simultaneously. They are safety ceilings, not preallocation targets; none is increased here.

Additional structural bounds accepted for v1.0: at most **256 members including manifest**; central-directory bytes at most **2 MiB**; member names at most **240 ASCII bytes**; each decoded JSON document at most **1 MiB**, nesting depth at most **32** containers, at most **65,536 total object properties/array elements**, each decoded string at most **16,384 UTF-8 bytes**. A primary grid/image MUST have at most **33,554,432 pixels** (the 128 MiB float32-plane ceiling). A decoded preview/visible image MUST fit **128 MiB** and the same pixel bound. Dtype/channel products remain subject to exact member-byte bounds. Implementations MAY report a stricter local resource limit explicitly; they MUST NOT silently truncate, downsample numeric data or call a conformant archive corrupt solely because they cannot allocate it.

All names MUST be lowercase ASCII relative paths. Each slash-separated component matches `[a-z0-9][a-z0-9._-]*`, contains no `..` substring and ends in neither a dot nor a space. Reject absolute/drive/UNC paths, backslashes, NUL, empty/dot/dot-dot components and Windows reserved basenames (`con`, `prn`, `aux`, `nul`, `com1`–`com9`, `lpt1`–`lpt9`, including those before a filename extension). Only root `manifest.json` or paths under `data/`, `preview/`, `extensions/` are allowed. Reject duplicate names, case-fold collisions and parent/file conflicts. Lowercase ASCII avoids Unicode normalization aliases; implementations MUST reject collisions before following descriptors. Readers SHOULD read members directly, not extract arbitrary paths. No external paths, URLs or executable references are followed.

Readers MUST enforce actual decompressed byte counts per member and in total, not just advertised ZIP sizes. Validate bounded directory/metadata first; hash/validate payloads with bounded buffers where practical. A high compression ratio alone is not invalid: uniform masks/matrices compress strongly. There is **no mandatory ratio cutoff** in this profile; actual-byte/allocation budgets, streaming counters and explicit local CPU/resource refusal prevent unchecked decompression. A ratio heuristic MAY report a local resource refusal, never bypass these ceilings or fabricate data.

## 3. JSON and primitive rules

`manifest.json` is a single UTF-8 JSON object with no BOM, duplicate keys, comments, trailing data, non-finite JSON numbers or numeric overflow. Known-field types are exact: booleans are not integers; optional fields are omitted when absent, not silently replaced with zero/false/null. Unknown optional fields are permitted only within the same structural/resource limits. They cannot override known-field invariants.

- `UUID`: canonical lowercase hexadecimal `8-4-4-4-12` representation of a 128-bit UUID, non-nil. Producers MUST allocate a fresh capture UUID. No particular UUID generation version is mandated.
- IDs/references: nonempty stable untranslated ASCII identifiers, at most 128 bytes and matching `[a-z0-9][a-z0-9._-]*` without a `..` substring; module IDs retain their current `[a-z0-9][a-z0-9.-]*` syntax. Namespaced extension IDs use lowercase dot-separated components, e.g. `org.lmthermal.camera.ht301`. IDs are not filesystem paths or localized labels. Model IDs and application/module versions are nonempty bounded strings, not invented values when unavailable.
- SHA-256: 64 lowercase hexadecimal digits over the exact uncompressed member bytes. Whole-archive hashes use every byte of the complete file. ZIP CRC is additionally checked but does not replace SHA-256.
- Known integer fields are JSON integers in `0..2,147,483,647` unless a stricter bound is stated; dimensions, counts, version numbers and coordinates are checked before conversion/allocation. Negative values are not allowed for these known fields. Unknown JSON numbers must still be finite, and their value must be preserved faithfully if copied. Monotonic nanoseconds and optional frame sequence are **decimal strings** matching `0|[1-9][0-9]*`, limited to unsigned 64-bit range, to avoid loss through JSON number conversion. They are not UTC or cross-runtime identities.
- UTC strings use `YYYY-MM-DDTHH:mm:ss[.fraction]Z`, valid calendar date/time with 1–9 fractional digits if present and seconds 00–59. An unavailable/unrepresentable source time is omitted with truthful clock status; an original time token can remain opaque module evidence. No timezone inference or locale-dependent numeric encoding is allowed. Leap-second source tokens outside this canonical subset remain original evidence with a truthful unavailable/unreliable time record, never a silently altered UTC value.



**Accepted owner clarification — unknown optional JSON fields:** preservation means **semantic JSON-value preservation using sufficient numeric precision**. It does **not** require retaining the original lexical spelling of a JSON number (`1`, `1.0`, `1e0`, etc.). Array order, strings, booleans, null and JSON numeric values must retain their meaning; insufficient precision must not round a preserved number to a different value. This does not change known-field type/validation rules. **Exact byte preservation continues to apply to binary payload members where the contract requires it.**

## 4. Required manifest skeleton

Every row marked required MUST exist. Payload IDs are unique; all references must resolve to declared members/objects with the expected role and geometry. The following field tables plus cross-field/binary rules are the schema definition; generic JSON-schema validation alone cannot establish archive integrity.

| Root field | Required/type | Meaning |
|---|---|---|
| `format` | string, required | Exactly `lmthermal-exchange`. |
| `schema_version` | object, required | Integer `major=1`, nonnegative integer `minor`; producer for this profile writes `minor=0`. |
| `kind` | string, required | Exactly `still`. |
| `content_class` | string, required | `radiometric`, `native_samples`, `visual` or `opaque_evidence`; truthful classification by the recognized source payloads below. |
| `capture_id` | UUID, required | Identity of this immutable file/capture. A saved derivative receives a new ID. |
| `complete` | boolean, required | Exactly `true` in a published file; structure/hashes must still validate. |
| `required_features` | unique string array, required | At most 64 IDs; interpretation requirements, never remote code/URLs. |
| `producer` | object, required | `application_id` and `version`, optional `build_id`; creator of this file. |
| `creation_time` | ClockRecord, required | When this capture/export/derivative was created, with honest clock quality. |
| `source` | object, required | `module_id`, `model_id`, `origin`; optional actual `module_version`. `origin` is `device`, `simulated`, `imported_legacy` or `unknown`. No assumed USB identity. |
| `geometry` | NativeGeometry, required | Primary source grid, known positive dimensions, native reference. |
| `acquisition` | object, required | Required `time: ClockRecord`; optional actual `sequence`, `receipt: ReceiptRecord`, `producer` (original acquisition app), and `settings_extension_ids`. |
| `capabilities` | object, required | Six facts below, each `supported`, `unsupported` or `unknown`. Capability ≠ this-frame availability. |
| `availability` | object, required | Current payload availability/reasons, six roles below. |
| `measurement` | object, required | Explicit temperature status and, when present, validity/provenance references. |
| `payloads` | descriptor array, required | 1–255 descriptors, one for **every non-manifest archive member**; no undeclared extras. |
| `extensions` | descriptor array, optional | Unique namespace IDs/schema versions and optional metadata/blob references. Empty/omitted allowed. |
| `analysis` | object, optional | Native points/shapes/annotations with creator/time and optional valid-pixel statistics. |
| `presentation` | object, optional | Display-only settings; never an input to measurement. |
| `lineage` | object, conditional | Required for a v1 deliberately saved analysis/presentation derivative (§11). |

`NativeGeometry` has required positive integers `width_px`, `height_px`, constant `coordinate_space="native"`, `origin="top_left"`, `x_direction="right"`, `y_direction="down"`, `matrix_order="row_major"`, `orientation="source_native"`. Dimensions/product obey §2. This is a pixel reference, not a claim of compass/world/phone orientation. Numeric arrays use `[y,x]`, row offset `y*width+x`; pixel coordinates refer to centers `(x+0.5,y+0.5)` and must be in bounds. Optional literal center is `(floor(width/2),floor(height/2))`; camera summary center is not inferred from it.

`content_class` is `radiometric` when an authoritative temperature plane exists (its validity still gates every reading); otherwise `native_samples` when a native plane exists, otherwise `visual` when preview/visible imagery exists, otherwise `opaque_evidence` when only acquisition bytes exist. `opaque_evidence` MUST explicitly be presented as not generically analyzable. Extension-only/unknown-role-only data do not satisfy a source capture.

`capabilities` MUST include `preview`, `temperature`, `native_samples`, `acquisition_payload`, `calibration_settings`, `visible_image`. Each is a truthful tri-state fact; absent knowledge is `unknown`. This wire inventory does not require expanding today's Android `CameraCapabilities` flags or advertising speculative UI actions.

`availability` has the same six keys. Each value is `{ "status": "present" | "absent", "reason": <optional stable identifier> }`; `reason` is required for absent. Suggested reasons: `unsupported`, `not_exposed`, `not_available_for_frame`, `withheld_by_policy`, `unknown`. Present must have matching descriptors/extension references: preview/temperature/native/acquisition/visible map to those payload roles; calibration/settings maps to declared `acquisition.settings_extension_ids`, each resolving to an extension with actual metadata/blob evidence. Temperature present iff `measurement.status=available`; mask presence is subordinate to that temperature plane, not a seventh capability. Unsupported capability cannot claim present data; unknown capability may have demonstrably present data without inventing a general device promise. Module version absent means not provided; it MUST NOT be guessed from the application version.

`measurement.status` is `available` or `unavailable`. Unavailable requires a stable `reason` and MUST NOT reference a temperature/mask payload, extrema, provenance that implies a current temperature, or old readings. Temperature-specific provenance is attached to an actual available plane, not fabricated for preview-only output. Available requires `temperature_payload_id`, `validity` (`all_valid`, `partially_valid`, `no_valid_pixels`), `provenance` and optional `mask_payload_id`, `extrema`. At least one valid pixel is required for a measurable capture; `no_valid_pixels` with a mask can be preserved as explicit evidence, with no numerical readings; analysis may retain only zero-valid counts. Here `available` means the plane exists, not that a pixel is measurable. Global archive validity and valid-pixel availability are different.

## 5. Clock records

`ClockRecord` requires `status` (`known`, `unreliable`, `unknown`) and `clock_source` (stable identifier, e.g. `device_clock`, `host_wall_clock`, `unknown`). `utc` is required only for known; MAY be present for unreliable **only if it is a genuinely available reading**; MUST be absent for unknown. `reason` is required for unreliable/unknown. Optional `uncertainty_ns` is a nonnegative decimal string when genuinely known; do not invent clock precision/uncertainty.

`acquisition.time` describes the frame/acquisition event, not the save dialog or export completion. If the source does not supply or establish its acquisition UTC, status remains unknown even when host creation time is known. A host receipt timestamp is separate evidence and MUST NOT be substituted as a known sensor acquisition time. `creation_time` can be known independently. A derived file preserves acquisition clocks and adds its own creation/analysis clocks.

`ReceiptRecord` requires `clock_domain_id: UUID`, `monotonic_ns: decimal string`; optional `utc: ClockRecord` records an actually paired receipt clock. A UTC receipt clock is independent of the monotonic number and does not convert it. The domain identifies the originating runtime's monotonic epoch; a new runtime/epoch gets a new domain. Values cannot be compared between domains or converted to UTC without separately recorded evidence. Monotonic receipt is not a wall clock and optional sequence is module-observed, not fabricated from export count.

## 6. Payload descriptors and canonical bytes

V1.0 permits at most one authoritative temperature descriptor and one temperature-validity descriptor; if present they must be the current measurement references, with no orphan mask. Multiple native/image/evidence descriptors are identified individually, never selected by an implicit camera fallback.

Every descriptor requires `id`, `member`, `role`, `media_type`, `byte_length` and `sha256`; `byte_length` is the exact positive uncompressed length within §2. `member` follows §2 paths. `manifest.json` has no inventory entry or internal self-hash. IDs and member paths are unique. Presence of a descriptor makes that member mandatory and hash-checked, even for an optional role. No consumer may silently ignore its corruption.

| Role | Additional required fields and semantics |
|---|---|
| `temperature` | `encoding="ieee754"`, `dtype="f32"`, `byte_order="little"`, `shape=[height,width]`, `order="row_major"`, `coordinate_space="native"`, `unit="Cel"`; media type `application/octet-stream`. |
| `temperature_validity` | `encoding="validity.u8"`, `dtype="u8"`, `byte_order="not_applicable"`, same shape/order/native space; one byte per temperature pixel. |
| `native_samples` | Namespaced `encoding` (e.g. `ht301.raw14`), allowlisted dtype, explicit byte order, shape `[height,width]` or `[height,width,channels]`, row-major/native space. Channels are 1–16, last axis fastest. No Celsius unit inference. |
| `acquisition` | Opaque bytes; stable `encoding`, media type; no thermometry/executable semantics. Optional capture frame relationship is extension metadata. |
| `preview` | `media_type=image/png` or `image/jpeg`; `image={width_px,height_px,coordinate_space:"native",orientation:"stored_pixels"}`. In v1.0 native previews cover the full primary grid at its exact dimensions without hidden crop/rotation/mirroring. Optional visual overlays must be identified as presentation, never measurement. |
| `visible_image` | PNG/JPEG; `image` declares own positive dimensions, `coordinate_space` distinct from `native`, `orientation="stored_pixels"`. No registration/overlay on primary native data is inferred. |
| `extension_json` | `application/json`, finite UTF-8 object under §2/§3; associated namespace/schema lives in `extensions`. |
| `extension_blob` | Opaque namespaced evidence/media/encoding; no recursive processing or execution. |

Allowlisted native scalar dtypes: `u8`, `u16`, `u32`, `i16`, `i32`, `f32`, `f64` with 1/2/4/2/4/4/8 bytes respectively. Multibyte samples MUST be little-endian; u8 has `not_applicable` byte order. Signed integers use two's complement; floats use IEEE-754. Native floats may preserve sensor bit patterns (including undefined evidence) but are not a temperature plane and cannot bypass measurement validity. Packed/proprietary samples outside this representation remain opaque acquisition/extension evidence; no required native plane is fabricated.

Temperature, validity, native-sample and acquisition members MUST be under `data/`; image roles under `preview/`; extension members under their namespace path.

For typed planes, byte length MUST equal the overflow-checked product of shape dimensions × item size. No padding, transposition, resampling, object dtype, pickle, compression inside the canonical binary plane or mandatory NumPy header is permitted. Numeric source bytes MUST round-trip exactly. A reader may use NumPy internally; Android needs only ordinary byte/float/ZIP/JSON facilities.

For images, decoded dimensions MUST match descriptors; require bounded decoding. Native preview orientation is stored pixels, not automatic EXIF rotation; producers MUST normalize/remove non-identity orientation metadata. Visible images likewise use described stored-pixel geometry. Optional unknown future role/encoding metadata may remain opaque within the inventory/bounds, only when no required feature depends on understanding it. Unknown roles cannot satisfy the required source-payload condition of v1.0 by themselves.

## 7. Temperature mask, inspection and deterministic statistics

The canonical validity mask is **unpacked uint8**, exactly one byte per native temperature pixel in the same row-major order. `1` means valid; `0` means invalid; any other byte is malformed. No bitpacking, inverted convention, third status, sentinel or implicit NaN validity is permitted.

- No mask means all temperature pixels are valid and `measurement.validity=all_valid`.
- With a mask, validity MUST match its count: all ones → `all_valid`; mixed → `partially_valid`; all zeroes → `no_valid_pixels`.
- **All valid temperatures MUST be finite.** Invalid cells MUST use the canonical float32 positive-zero bytes `00 00 00 00`. That filler is serialization only, never a reading; the mask is the sole authority for invalidity. NaN, Infinity, sentinel values or negative zero at invalid cells are nonconformant. A real valid 0 °C sample remains valid when its mask byte is 1.
- Clicking/inspecting an invalid pixel MUST return unavailable, no Celsius fallback. Extrema, ROI means and other analysis MUST exclude invalid pixels. No-valid regions have no min/max/mean, no extrema coordinates and no stale substitute.
- Full-plane or rectangle statistics contain required `temperature_payload_id` matching the current authoritative plane, integer `pixel_count` and `valid_pixel_count` plus `method="finite-valid-row-major-f64-v1"`, `unit="Cel"`. `pixel_count` counts geometry; `valid_pixel_count` counts only valid cells. With zero valid cells, `min`, `max`, `mean`, `min_xy`, `max_xy` MUST be absent. Otherwise these five fields are required and finite, with valid in-bounds `min_xy`/`max_xy` integer arrays `[x,y]`.
- Min/max compare saved float32 values over valid cells. Ties take the first valid pixel in native row-major order, including signed-zero numeric ties. Mean uses a float64 accumulator initialized to positive zero, adds valid float32 samples promoted exactly to float64 in row-major order, then divides by valid count; round-to-nearest/ties-to-even, no fast-math reassociation or smoothing. Serialization MAY round a scalar only within the verification tolerance below. Optional other statistics must name their algorithm/unit/count separately; standard deviation is not silently standardized here.
- Stored core extrema, point values and ROI statistics MUST agree with mask/matrix and deterministic locations/counts. Numeric verification tolerance: `abs(saved-calculated) <= max(1e-4, 1e-6*abs(calculated))` in Celsius; counts/coordinates are exact. Tolerance does not authorize changing the binary matrix. Core point/extrema mean **matrix-derived** values; device-reported summaries remain separately named module observations.

`measurement.extrema`, when saved, is the Statistics object for the full grid; it references the current `temperature_payload_id`. Statistics and cached point temperatures require a declared temperature plane; unavailable temperature permits geometry/annotations only. All statistics counts are exact nonnegative integers within the grid/slice count. Analysis points can optionally retain `temperature_c`; it must agree with a valid matrix pixel within tolerance. Invalid pixels retain position/annotation only, not a stored reading. HT-301 MAY omit the mask for its fully valid matrix. Ordinary load MUST NOT rebuild LUTs or replace stored temperatures from raw/calibration evidence.

## 8. Provenance, evidence and privacy

Available `measurement.provenance` requires `kind` (`native_equivalent`, `device_reported`, `simulated`, `derived`, `unknown`) and `physical_accuracy` (`not_independently_validated`, `independently_validated`, `unknown`, `not_applicable`); optional `algorithm={id,version}` only when available. A physical-validation claim MUST cite bounded evidence metadata/reference IDs, not a confidence inferred from plausible numbers. Readers present provenance as a claim/evidence status, not authenticated calibration. No network link is followed automatically.

HT-301 `native_equivalent` data MUST retain `physical_accuracy=not_independently_validated` and the exact warning:

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

The warning MUST be saved as `measurement.provenance.warning`. Localized UI may additionally explain it, without changing machine IDs/numbers or implying calibrated accuracy. A file hash proves corruption detection, not trusted origin or calibration. Authenticity/signatures/encryption are not defined by v1.

Producers SHOULD retain, when available, authoritative temperatures, native samples, measurement-describing calibration/settings and truthful provenance/validity by default. Omitted available evidence MUST have an availability reason, not an invented value. Exact original acquisition/transport is optional and MUST be separately described; a temperatures-only capture is radiometric without it. Preview is recommended for usability but optional when numeric data are analyzable. Preview-only sources may save their preview as the actual source payload; opaque-only evidence is explicit non-generic analysis evidence.

GPS/location, serials, USB paths, LAN/network identity, host paths and private diagnostics MUST be absent by default; explicitly consented legitimate metadata is optional namespaced metadata. Credentials/tokens MUST never be exported. Raw/native/transport/preview imagery can disclose the scene; documentation and later localized export UI MUST explain richer evidence retention. Export retention choices do not silently alter already-saved originals or claim data a module does not supply.

## 9. Module extensions and required features

An extension descriptor requires `id` (namespace), `schema_version={major,minor}` (positive major/nonnegative minor integers) and optional `metadata_payload_id`, `blob_payload_ids` (unique references). Extension version fields use the same integer bounds as the root version. Metadata and blob references cannot claim membership in two different namespaces; each extension-role payload must belong to exactly one descriptor. At least metadata or blob evidence must exist; referenced members must have the corresponding extension role under `extensions/<namespace>/`. Optional fields MAY extend its finite bounded metadata. Extensions cannot override core identity, geometry, temperature bytes, validity or units.

Unknown optional extensions MUST be structurally bounded and hash-verified but never executed. They MAY be ignored for analysis. Understanding a required payload's extension/encoding MUST be declared in `required_features`; an unsupported reader MUST reject explicitly before exposing measurements. Namespace lookup is local declarative support, never downloaded/dynamic plugin code.

The v1.0 core feature registry is `core.still`, `core.temperature-f32le`, `core.validity-u8`, `core.native-coordinates`, `core.analysis-v1`. A full v1.0 consumer understands these features; `core.still` and `core.native-coordinates` MUST be declared on every file. Add `core.temperature-f32le` when a temperature plane exists and `core.validity-u8` when a mask exists. `core.analysis-v1` MAY be required when supported analysis is essential to interpretation. Optional analysis otherwise may be ignored. Unknown required IDs, core versions/kinds or required encodings fail cleanly. Other extension feature IDs are namespaced and locally defined by their independent schema.

HT-301 namespace is `org.lmthermal.camera.ht301`; its schema version is explicit, never implied by root major. When the native-plane encoding is `ht301.raw14`, it specifically denotes unmasked original uint16 little-endian words in `[288,384]` with every complete word below `0x4000`; display/mixed words must not be mislabeled or high-bit-masked into this encoding. These are HT encoding constraints, not generic geometry or bit-depth requirements. Its evidence may include exact transport layout, raw14 encoding, parsed parameters/calibration, supported host range/lens/shutter assumptions, trailer center/high/low, literal-center observation and lookup trace/algorithm provenance. Existing validated meanings are preserved from the research documentation; no center-region algorithm or new thermometry is invented here. If provided, the temperature-describing settings are preserved as actual evidence, not defaults guessed by a reader. An HT-specific extension's detailed API/metadata evolution does not become a mandatory universal camera field.

Future Android issue #5 MUST design a **module-owned immutable export snapshot/evidence interface**: generic creator accepts owned descriptors/data; modules supply optional complete native planes/acquisition/calibration evidence. Common persistence MUST NOT cast to HT-301, add protocol fields to `ThermalMeasurement`, or serialize UI diagnostic maps. Exact API design is deferred to that implementation issue after this contract is accepted; no new application interface is implemented now.

## 10. Embedded analysis and presentation

Optional `analysis` contains required `producer` (same Producer type), `creation_time` (ClockRecord), and optional `points`, `shapes`, `annotations` arrays. Item IDs are unique within analysis, and each item declares `coordinate_space="native"` unless an explicitly declared separate image space is referenced. Numeric point/ROI analysis uses native temperature coordinates only; visible-image annotations cannot silently become thermal coordinates.

- Point: required `id`, `coordinate_space`, integer `x_px`, `y_px`; optional `label`, `temperature_c` validated under §7. In-bounds position can survive unavailable temperature; no fake value.
- Rectangle shape: required `id`, `type="rectangle"`, `coordinate_space="native"`, `bounds={x1_px,y1_px,x2_px,y2_px}`, `interval="half_open"`. Integers satisfy `0<=x1<x2<=width`, `0<=y1<y2<=height`. Optional `label`, `statistics` follow §7 for exactly that slice and mask.
- Annotation: required `id`, `text`, declared coordinate space; optional anchor `{x_px,y_px}` or valid `target_id` referencing a point/shape. Text is bounded plain text, not markup/executable code or an external file. Every non-native coordinate-space ID must be declared by a visible-image descriptor; descriptors sharing a space must have identical dimensions/orientation. Point positions/anchors must fit that space, and optional Celsius point values/statistics are permitted only in `native`. Unknown optional metadata is preserved as data.
- Future shapes: `type` is an extensible identifier with `geometry` and declared coordinate space. V1.0 does not define polygon/circle inclusion/statistics algorithms. Unknown optional shapes may be ignored/preserved; they MUST NOT be guessed/rasterized as rectangles. A shape essential to interpretation needs a required feature. New optional descriptors need not break major 1.

Optional `presentation` has `palette_id`, `range_mode` (`auto`, `manual`), optional `effective_bounds={min,max,unit:"Cel"}`, and optional `transform={rotation_degrees,mirror_x,mirror_y}`. Rotation is clockwise 0/90/180/270 **first**, then horizontal/vertical mirror in the rotated display axes. Omitted transform is identity. Interaction must invert that same transform back to native coordinates. With a supplied range mode, effective bounds are required when valid temperatures exist; finite `min<max`. If `effective_bounds` is present, `range_mode` MUST also be present; a temperature-unavailable capture MUST NOT contain a Celsius range pretending to be current measurement. An Auto record describes its saved effective range, not a mandate to recompute/alter source values. Invalid pixels have a visibly distinct unavailable rendering, never the valid color for filler 0 °C.

Core palette IDs: `white_hot`, `black_hot`, `inferno`, `iron_like`, `turbo`. A reader that does not recognize an optional palette MUST use `white_hot` (monotonic grayscale), disclose fallback, and retain the original ID. Lack of temperature suppresses Celsius legend/readings regardless of palette metadata; an image preview remains visual evidence. Optional palette/range/transform fields cannot change raw/native/temperature bytes, mask, calibration/provenance or numerical statistics. Cross-platform bit-exact palette tables are not part of this storage contract; exact numeric source preservation is.

## 11. Immutability and derived-file lineage

Published captures are immutable. Analysis edits initially live in memory. Explicitly saving analysis/presentation in v1.0 MUST create a **new `.lmtx`**, never overwrite its parent or introduce a sidecar.

`lineage` requires `operation="analysis"`, `parents` (one direct parent in v1.0) with `capture_id: UUID` and `archive_sha256` over the parent's **complete original archive bytes**, plus `preserved_payloads`, an array of `{parent_payload_id,payload_id}` objects mapping each parent payload ID to its child payload ID. Both columns are unique; every parent payload is mapped exactly once, every child reference exists and its meaning/bytes are preserved. Parent paths/serials are not identity. A parent need not be available to read the child; missing parent is not corruption of otherwise complete child data. If supplied later, UUID/hash must match before claiming it is that parent.

The child MUST:

1. Allocate a new capture UUID, record its current producer/version and creation clock; record analysis producer/time when known. Preserve source module/model/module-version, native geometry, acquisition-time/receipt/sequence, capability/evidence facts and measurement/calibration provenance. Preserve original acquisition producer using `acquisition.producer` (copy the parent's existing value or its creator when it was the acquisition producer; if genuinely unknown, omit rather than guess).
2. Preserve **every parent payload's uncompressed bytes and descriptor meaning**, including temperatures/mask/native/transport and unknown optional extension members. Recompression is allowed; SHA/length of each preserved member must remain identical. Members may be kept at the same safe path/ID; any remapping must be explicit and every known reference adjusted. New analysis/presentation is embedded in the new manifest; numeric source, mask and measurement status cannot change.
3. Preserve unknown optional root/descriptor/extension/analysis fields and extension metadata as bounded semantic JSON values using sufficient numeric precision (§3), or exact member bytes where required, without assuming their module-specific meaning. JSON numeric lexical spelling need not be retained. Do not silently drop them. If safe faithful preservation cannot be achieved under limits/references, refuse derived save explicitly and keep in-memory analysis; copying a complete archive is not a justification to execute content. This stricter derivation rule implements preservation wherever feasible with an honest failure boundary.
4. Retain the parent's required features. Do not declare a derived capture independently interpreted if a required parent feature was unsupported. The child need not embed the whole parent archive; the parent whole-file hash plus exact payload mapping provides lineage without a circular self-hash.

Explicit thermometry **reprocessing** is separate from ordinary load/analysis and is not standardized as a lineage operation by v1.0. Original evidence is retained to make later reviewed reprocessing possible. A future reprocessing contract must identify algorithm/settings, preserve original evidence and produce a new derivative; it must never masquerade as an analysis save that silently changed the authoritative matrix.

## 12. Publication, integrity errors and compatibility

Producers MUST finish a complete archive privately before reporting success: frozen coherent snapshot, payload bytes/hash inventory, finalized `complete=true` manifest, ZIP directory/end record, close and flush/sync where supported. Publish with no overwrite and atomic visibility where the backend provides it. Temporary files are not completed captures; `complete=true` alone does not make a truncated archive readable. Readers MUST verify every declared member before exposing measurement data, with bounded passes/lazy storage as appropriate. Failure never revives a previous frame as the newly loaded measurement.

Local filesystem publication uses platform-supported exclusive/atomic operations, not an assumption of Unix hard-link support on Windows. Durability/parent-directory sync guarantees MUST be documented separately from normal atomic visibility. Android SAF/cloud documents have provider-dependent operations ([Android storage guidance](https://developer.android.com/training/data-storage/shared/documents-files)); this contract does not assume atomic rename across providers. Stage/validate privately, copy to a newly created destination, report/share success only after close; report/delete a partial destination where permission/provider support permits. A reader rejects incomplete destination bytes. No blanket power-loss or cloud-transaction guarantee is claimed.

Required error classes for later implementations: `unsupported_format`, `unsupported_major`, `unsupported_kind`, `unsupported_required_feature`, `invalid_manifest`, `invalid_container`, `unsafe_path`, `missing_payload`, `integrity_mismatch`, `invalid_payload`, `resource_limit`. User-visible text is localized, machine error IDs are stable. An unsupported required extension is not guessed; a hash/malformed payload is not downgraded to a mere unavailable reading.

Major 1 minor revisions may only add compatible optional fields/features. They MUST NOT change existing field meaning, dtype, unit, coordinate/validity/statistics or required semantics. A major-1 consumer accepts a newer minor only after validating understood fields and every declared required feature. Unknown bounded optional fields can be ignored for analysis and retained for derivatives. Unknown major/required features fail explicitly. In-memory adaptation is preferred; Desktop MUST retain long-term read support for published stable v1 files, rather than rewriting originals into the latest schema.

Existing `lmthermal-radiometric-capture` integer v1 and `lmthermal-radiometric-recording` integer v1 remain independently supported historical formats with their current strict loaders, fixtures, metadata/optional-transport and completion/recovery rules. They are not relabeled as LMTX. Future Desktop adapters normalize each legacy input or exchange capture into an owned optional-temperature/native-evidence offline model for point/ROI/palette/export. No exchange recording scope, relaxation of old validators, new hardware behavior or Windows camera-driver claim is introduced.

## 13. Canonical small masked example (synthetic, not a camera fixture)

This example is an invented 2×2 source to demonstrate serialization/mask rules, not evidence of a supported commercial camera or physical temperature accuracy. Required source payloads are the 16-byte float32 matrix `[20,0,31,24]` and mask bytes `[1,0,1,1]` in row-major order. Exact hex and SHA values appear below; there is no preview/native/transport requirement.

```json
{
  "format": "lmthermal-exchange",
  "schema_version": {
    "major": 1,
    "minor": 0
  },
  "kind": "still",
  "content_class": "radiometric",
  "capture_id": "dba2f0a6-ecb2-4a92-9049-f07f0e094b72",
  "complete": true,
  "required_features": [
    "core.still",
    "core.native-coordinates",
    "core.temperature-f32le",
    "core.validity-u8"
  ],
  "producer": {
    "application_id": "org.lmthermal.specification-example",
    "version": "1.0-review-1"
  },
  "creation_time": {
    "status": "unknown",
    "clock_source": "unknown",
    "reason": "synthetic_example_has_no_event_time"
  },
  "source": {
    "module_id": "synthetic.contract-example",
    "model_id": "invented-2x2-mask-example",
    "origin": "simulated"
  },
  "geometry": {
    "width_px": 2,
    "height_px": 2,
    "coordinate_space": "native",
    "origin": "top_left",
    "x_direction": "right",
    "y_direction": "down",
    "matrix_order": "row_major",
    "orientation": "source_native"
  },
  "acquisition": {
    "time": {
      "status": "unknown",
      "clock_source": "unknown",
      "reason": "synthetic_example_has_no_acquisition"
    }
  },
  "capabilities": {
    "preview": "unsupported",
    "temperature": "supported",
    "native_samples": "unsupported",
    "acquisition_payload": "unsupported",
    "calibration_settings": "unsupported",
    "visible_image": "unsupported"
  },
  "availability": {
    "preview": {
      "status": "absent",
      "reason": "unsupported"
    },
    "temperature": {
      "status": "present"
    },
    "native_samples": {
      "status": "absent",
      "reason": "unsupported"
    },
    "acquisition_payload": {
      "status": "absent",
      "reason": "unsupported"
    },
    "calibration_settings": {
      "status": "absent",
      "reason": "unsupported"
    },
    "visible_image": {
      "status": "absent",
      "reason": "unsupported"
    }
  },
  "measurement": {
    "status": "available",
    "temperature_payload_id": "temperature",
    "validity": "partially_valid",
    "mask_payload_id": "validity",
    "provenance": {
      "kind": "simulated",
      "physical_accuracy": "not_applicable"
    },
    "extrema": {
      "temperature_payload_id": "temperature",
      "pixel_count": 4,
      "valid_pixel_count": 3,
      "method": "finite-valid-row-major-f64-v1",
      "unit": "Cel",
      "min": 20,
      "max": 31,
      "mean": 25,
      "min_xy": [
        0,
        0
      ],
      "max_xy": [
        0,
        1
      ]
    }
  },
  "payloads": [
    {
      "id": "temperature",
      "member": "data/temperature.f32le",
      "role": "temperature",
      "media_type": "application/octet-stream",
      "byte_length": 16,
      "sha256": "2840d544ef96d01aa388ad89cabc70e860c16b48949aab03e7e7b5ee2ea8699a",
      "encoding": "ieee754",
      "dtype": "f32",
      "byte_order": "little",
      "shape": [
        2,
        2
      ],
      "order": "row_major",
      "coordinate_space": "native",
      "unit": "Cel"
    },
    {
      "id": "validity",
      "member": "data/validity.u8",
      "role": "temperature_validity",
      "media_type": "application/octet-stream",
      "byte_length": 4,
      "sha256": "52a5c4a10657220cac05c63adfa923c7771c55d868a58ee360eb3d1511985c3e",
      "encoding": "validity.u8",
      "dtype": "u8",
      "byte_order": "not_applicable",
      "shape": [
        2,
        2
      ],
      "order": "row_major",
      "coordinate_space": "native"
    }
  ]
}
```

Temperature bytes: `0000a041000000000000f8410000c041`; mask bytes: `01000101`. The invalid `(1,0)` has filler 0, not a 0 °C reading. Global/full-rectangle pixel count is 4, valid count 3, min 20 at `(0,0)`, max 31 at `(0,1)`, mean 25. The two timestamps are explicitly unknown rather than export-time substitutes. The profile/schema minor is 0; a newer compatible minor may add optional bounded fields without changing these meanings.

## 14. Minimum conformance plan and implementation gate

These are **planned shared fixtures and tests, not newly produced real-capture fixtures or validated implementations**. Exact byte/hash preservation is required for source matrices and opaque members; deliberate mask canonicalization occurs only when originally producing a new capture, not when loading or saving its analysis derivative.

| Planned case | Required result |
|---|---|
| HT-301 rich capture | Preserve authoritative native-equivalent matrix, raw samples, calibration/settings, distinct trailer/literal observations, optional transport and accuracy warning; no lookup recomputation. |
| Temperatures-only | Valid measurable file with no native/transport/preview dependency. |
| Preview-only 160×120 | Valid non-radiometric source; no Celsius readings/legend or fake matrix. |
| Alternate geometry/native encoding | Respect declared geometry/encoding, never HT dimensions/bit masks. |
| Mixed validity mask | Ignore invalid center/extrema/ROI pixels; filler never appears as a reading; exact count/tie/mean rules. |
| All-invalid region/frame, valid 0 °C | No-valid analysis has absent numbers; a valid zero remains measurable. |
| Missing optional evidence | Omitted descriptor/status absent is valid; declared missing payload is an error. |
| Unknown optional field/extension/shape/palette | Bounded validate/preserve; ignore unsupported analysis, disclosed White hot fallback; no execution. |
| Unsupported required feature | Explicit failure before temperatures are exposed. |
| Compatible newer minor / unsupported major | Optional addition accepted without new semantics; unsupported major fails. |
| SHA mismatch | Integrity error even if bytes look plausible or role is optional. |
| Malformed/truncated ZIP/JSON | Reject duplicate keys, descriptor mismatch, invalid numbers/records/completion; no stale substitution. |
| Unsafe/duplicate/case-colliding paths | Reject before path following/allocation; include Windows reserved names/links/absolute paths. |
| Encrypted/ZIP64/unsupported methods | Explicit unsupported/invalid-container error; no fallback parsing. |
| Excessive decompression/allocation | Enforce each accepted ceiling, actual-byte budget and overflow limits; high-ratio legal uniform data remains supported below limits. |
| Exact-boundary sizes/resource ceilings | Limits are inclusive; one-byte-over is rejected; lower local allocation limits are reported honestly. |
| Clock cases | Known/unreliable/unknown remain distinct; no UTC inferred from export/monotonic epoch; independent creation/receipt clocks. |
| Analysis derivative | New UUID/full-parent-file hash, exact source bytes/unknown extension preservation, no overwrite/sidecar. |
| Presentation changes | Palette/range/transform cannot alter mask/matrix/natives/provenance/valid-pixel statistics. |
| Publication interruption/failure | Partial SAF/local destinations are never success; completed files validate; guarantees remain backend-specific. |
| Legacy readers | Both independent Desktop integer-v1 readers/regression fixtures retain current semantics. |

Eventual interoperability requires an Android producer, Desktop Linux consumer and Desktop Windows **offline** consumer against one shared corpus and real Android exports. Windows format validation does not establish Windows camera-driver compatibility. No current hardware/build run is claimed for this specification task. The exact immutable Android snapshot API remains issue #5 work, and actual conformance fixtures/readers/writers are now unblocked for future work under the accepted contract; none is started here.
