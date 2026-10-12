# LMThermal Recording v1 — complete wire draft

**PROPOSED R1b CONTRACT — NOT YET OWNER ACCEPTED OR FROZEN.**

| Property | Proposed value |
|---|---|
| Review identifier / date | `r1b-review-1` / 2026-10-12 |
| Family / extension / format / kind | LMThermal Recording / `.lmtr` / `lmthermal-recording` / `recording` |
| Container / schema / envelope | Container 1.0 / schema 1.0 / envelope revision 1 |
| Status | **DRAFT**; issue [#6](https://github.com/LounaMaili/LMThermal/issues/6) remains open |
| Evidence base | Main `ed0e3338a72bba76a267e4701125f6f89b3f93e3`; preserved R2 `747b89c8a0072603a73deb81db5cdbef0ed900a1` |

## 1. Authority, rule origins and review boundary

MUST/MUST NOT/SHOULD/MAY below describe the **proposed** contract, not owner
acceptance. Every numbered section identifies its rule origins:

- **[A]** inherited accepted [R1a semantics](LMTR_FORMAT_CANDIDATE.md), including
  complete fields, exact Celsius/evidence, truthful clocks/gaps, immutable sources,
  profile/storage product direction and legacy coexistence.
- **[E]** R2-tested mechanism or measured configuration, with its scoped limits in
  the [completion review](recording-r2/COMPLETION_REVIEW.md). Selecting it as a
  release obligation still requires R1b acceptance.
- **[P]** new R1b proposal; not established by R2 cross-platform/performance tests.
  Untagged grammar details within a tagged subsection inherit its tag. Mixed
  sections explicitly separate inherited semantics from new representations.

R2 [#7](https://github.com/LounaMaili/LMThermal/issues/7) is closed for the tested
HT-301/Pixel 8 baseline. PRs #11/#12 are integrated. No released LMTR bytes exist.
R2 `.r2proto` identifiers and packet bytes remain **noncanonical experimental
history**. New release magic, JSON grammar, multi-stream closure and idle timing
below are **not** asserted byte-compatible with or platform-tested by R2.
[Owner decisions and unresolved risks](LMTR_R1B_OWNER_DECISIONS.md) accompany this
complete recommended draft. They are acceptance questions for later review; no
operator/device is required to prepare it. R3 fixtures and production writers,
readers, Android/SAF UI and Desktop changes remain blocked.

The [Accepted still LMTX v1](LMTX_FORMAT_V1.md) is unchanged. Its ZIP profile,
unsigned-64 clock fields, still limits and derivative rules are not replaced by
this separately versioned recording contract. Historical Desktop directory/NPZ/CSV
formats retain their own strict loaders; no relabel, rewrite or automatic conversion.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## 2. Identity and compatibility [P]

A reader MUST inspect bytes and HEADER, not trust extension/MIME. Proposed MIME
`application/vnd.lmthermal.recording` is unregistered; an octet-stream transport
fallback is permissible. A file begins at byte zero with `LMTRREC1`; no ZIP,
preamble, encryption, executable loading, external payload URI or nested-container
interpretation is defined. Opaque nested evidence remains opaque bytes.

Every record has revision 1. HEADER declares container `{major:1,minor:0}` and
schema `{major:1,minor:0}`. A v1 writer emits these literal versions. A baseline
reader rejects other majors/revisions. Higher minor versions MAY be accepted only
when the same envelope/known fields retain meaning and all required features and
codecs are understood. Unknown optional JSON remains bounded data with semantic
preservation; unknown required semantics reject before exposing any measurement,
including recovery. Unknown record types, nonzero flags/reserved fields and unknown
codecs are **not skippable optional content** in envelope 1.

A compatible minor may add optional JSON/extension facts without redefining an
existing field. A new required feature may make a later-minor file explicitly
unsupported to an older reader; it cannot cause silent reinterpretation. Changing
units, masks, coordinates, time meaning, integrity domains or existing mandatory
interpretation requires a new schema major; incompatible framing requires a new
container generation/magic and revision. Reserved IDs are not experimental space.
No silent change to accepted R1a invariants is authorized by this draft.

## 3. Primitives, JSON and serialization [A preservation; E bounds; P canonical spelling]

### 3.1 Types and structural grammar

The normative object grammar is §6–§12 plus binary/cross-field rules. The companion
[JSON Schema](lmtr-r1b/schema.json) expresses local shapes; it cannot establish
binary integrity, reference closure or actual bounds. The prose wins if a defect
in the companion is found; report such a defect before implementation. Object
properties are required unless explicitly marked `?`. Optional absent fields are
omitted; `T | null` means a required field accepting JSON null. No other nullability
or default is implicit. Unknown optional properties are allowed on all objects
under the same limits, except that a **known** field illegal in a union variant
MUST be rejected, not treated as an unknown extension.

| Alias | Exact definition |
|---|---|
| `D` | JSON string `0|[1-9][0-9]*`, numeric value 0..9223372036854775807; no plus, leading zero, decimal or exponent |
| `D+` | `D` excluding zero |
| `I` | JSON integer token 0..2147483647; boolean/fraction/exponent tokens are not this type |
| `Version` | `{major:I,minor:I}`, major 1..65535, minor 0..65535 |
| `Id` | 1..128 ASCII bytes, `[a-z0-9][a-z0-9._-]*`, no `..` substring; not a path |
| `Namespace` | `Id` with at least two dot-separated components, each `[a-z0-9][a-z0-9_-]*` |
| `UUID` | Non-nil lowercase hexadecimal 8-4-4-4-12; new recording/session/clock identity as applicable |
| `Hash` | 64 lowercase hexadecimal digits, SHA-256; integrity, not authenticity |
| `Text` | Unicode scalar string, <=16384 UTF-8 bytes; not executable/HTML/URL authority |
| `JsonValue` | Bounded null/boolean/string/exact finite decimal/object/array, with no meaning guessed by generic code |
| `Features` | Sorted unique array of <=64 `Id` strings, ASCII order |
| `Utc` | Valid `YYYY-MM-DDTHH:mm:ss[.1..9digits]Z`, seconds 00..59; no timezone or leap-second inference |
| `Geometry` | `{width_px:I,height_px:I,coordinate_space:"native",origin:"top_left",x_direction:"right",y_direction:"down",matrix_order:"row_major",orientation:"source_native"}`; positive dimensions and §4 bounds |
| `Producer` | `{application_id:Namespace,version:Text,build_id?:Text}`; nonempty version/build when present |
| `Reason` | `Id`, stable untranslated cause; explanatory `detail?:Text` may accompany a reason where explicitly allowed |

JSON follows [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259), with stricter UTF-8,
no BOM, duplicate decoded keys, lone surrogates, comments, trailing text, NaN or
infinity. Root is exactly one object. Depth counts root container as 1, maximum 32.
Total properties plus array elements <=65536; decoded strings <=16384 UTF-8 bytes.
Decimal tokens <=1024 ASCII bytes, <=1024 significant digits and adjusted decimal
exponent -4096..4096; zero has exponent 0. These new explicit decimal limits permit
exact binary32/64 evidence and bounded high-precision optional metadata. Reject
out-of-limit numbers before allocating expanded decimal strings. Parse optional
numbers with arbitrary decimal precision (e.g. BigDecimal/Decimal), not Float64.

Unknown optional JSON MUST preserve its **semantic value with sufficient numeric
precision**, including array order; original `1`/`1.0`/`1e0` spelling is not required.
Binary payload bytes remain exact. A reader may ignore an optional extension for
analysis but MUST retain it if exporting/reprocessing retained evidence, or refuse
that operation honestly. Required extensions cannot be bypassed through a generic
fallback. No namespace lookup downloads code or follows links.

### 3.2 Proposed deterministic writer JSON

For identical semantic metadata, writers MUST emit: UTF-8 without BOM/newline or
insignificant whitespace; object keys sorted by Unicode scalar value; arrays in
specified order; `true`, `false`, `null`; escape quote/backslash, encode controls
U+0000..001F as lowercase `\u00xx`, and emit other scalar characters directly.
No slash escaping or Unicode normalization. Decoded keys determine duplicate checks.

Canonical decimal rendering: exact zero, including JSON negative zero, is `0`.
For a nonzero value strip trailing coefficient zeros without rounding, let `E` be
its most-significant-digit base-10 exponent. For -6 <= E <=20 use plain decimal,
no leading zeros other than `0.`, no trailing fractional zeros/dot. Otherwise use
one leading digit, optional `.` plus remaining significant digits, lowercase `e`,
and signed exponent with no plus or leading zeros. Preserve a negative sign for
nonzero values. Known `I` fields always render as integer tokens. `D` stays a string.
Examples: `1.0 -> 1`, `1e0 -> 1`, `-0.0 -> 0`, `0.0000001 -> 1e-7`,
`1230000000000000000000 -> 1.23e21`; **no binary floating rounding**.
Readers accept bounded RFC JSON spellings satisfying known-field types; hashes bind
actual stored bytes, never a reserialized interpretation. Canonical JSON is a new
proposal, not RFC 8785's binary64 number model and not a tested R2 byte promise.

A v1 primary temperature plane is Float32. A source that cannot preserve its
original numerical authority at that precision needs a separately reviewed future
feature/version or explicitly labelled reprocessing result; the writer never
silently quantizes a higher-precision source to fit v1. Native/opaque original
encodings may retain such evidence independently, without inventing a f32 source.
Source Float32 bytes (§10) are canonical independently of JSON. DEFLATE streams can
legitimately differ by encoder/library version; equal semantic inputs do not imply
one unique whole-file SHA (UUIDs/times/codec choices also vary). Future R3 will pin
particular vectors/encoder provenance; consumers must recover exact logical bytes,
not demand that independent compressed streams match. No compressed reference
vectors are generated in this drafting task.

## 4. Safety limits and refusal [E starting bounds; P complete generic profile]

All inclusive limits apply simultaneously; MiB = 1048576 bytes. All additions,
products, range ends, offsets/counts and dtype products use checked arithmetic.
Validate lengths against actual file size before seek, and decoded budgets before
decompression. A stricter local budget returns `local_resource_limit`, not corruption.
Never silently crop/downsample/change preservation to fit a budget.

| Limit | Proposed exact bound |
|---|---:|
| File size/offsets/times/counters | signed-64 positive domain, <=9223372036854775807 |
| Record including header/body/footer | 68157440 (65 MiB) |
| CHUNK metadata bytes | 1048576 (1 MiB) |
| HEADER JSON bytes | 1048576 |
| INDEX_PAGE/CHECKPOINT/END JSON bytes each | 524288 (512 KiB) |
| Sum decoded physical blocks + metadata + metadata-length prefix | 67108864 (64 MiB) per CHUNK |
| Entries/blocks/contexts/segment closures per CHUNK | 1024 / 256 / 1024 / 64 |
| Stream slots / active stream definitions | 64 / 64 |
| Logical descriptors per observation | 64 |
| Dimension / primary pixel product | each 1..16384 / <=4194304 |
| Numeric native/visual channels | 1..16 |
| INDEX_PAGE fanout / depth levels | 256 / 8 (depth 0..7) |
| Committed chunks between checkpoints | <=32 |
| JSON properties/elements/depth/string bytes | 65536 / 32 / 16384 (per §3) |
| Normal page cache policy | At most two pages per level; byte budget <=8 MiB |
| Fast recovery scan/suffix | <=68157512 tail bytes, <=1024 predecessor records and <=64 suffix chunks |

The 64 MiB CHUNK bound now explicitly includes the four-byte metadata-length prefix
as well as metadata. This four-byte accounting refinement is **[P]**, slightly
stricter than the R2 metadata+blocks ceiling, not a change to old artifacts. Expanded
visual images additionally obey width/height/pixel/channel bounds before display.
No compression-ratio cutoff is mandatory; actual byte/CPU/allocation budgets govern.
Unknown optional fields/binaries never bypass limits. No file is loaded wholesale.
A recording may have zero observation payloads (early Stop) but must declare at least
one stream and explain completion/interruption; do not fabricate a still capture.

## 5. Record envelope and commit [E layout/integrity; P release magic]

### 5.1 Header, footer and references

No alignment, padding or extra record bytes. All unsigned binary integers are LE,
accepted only in `D`'s numeric domain. Total = 40 + body_length + 72. Record ordinal
starts at 0 and increases by exactly 1; records are contiguous in ordinal order.
HEADER is only type 1 at offset/ordinal 0. END is only type 5 and last; no append
or duplicate HEADER/END after finalization. Previous offset equals the immediately
preceding record start, not an arbitrary earlier commit; first sentinel is
9223372036854775807. Its value cannot be an actual record start under file bounds.

| Header byte offset | Width | Field |
|---:|---:|---|
| 0 | 8 | ASCII `LMTRREC1` (hex `4c4d545252454331`) |
| 8 | 2 | envelope revision 1 |
| 10 | 2 | type: HEADER=1, CHUNK=2, INDEX_PAGE=3, CHECKPOINT=4, END=5 |
| 12 | 4 | flags=0 |
| 16 | 8 | record ordinal |
| 24 | 8 | body_length |
| 32 | 8 | previous_record_offset |

| Footer byte offset | Width | Field |
|---:|---:|---|
| 0 | 8 | ASCII `LMTRCMT1` (hex `4c4d5452434d5431`) |
| 8 | 8 | total record length including 112 envelope bytes |
| 16 | 8 | repeated ordinal |
| 24 | 8 | repeated previous offset |
| 32 | 32 | raw SHA-256 digest of exact stored body bytes |
| 64 | 4 | IEEE CRC32(header[0:40] concatenated with footer[0:64]), LE |
| 68 | 4 | reserved zero |

CRC32 uses reflected polynomial 0xedb88320, initial/final XOR 0xffffffff, equivalent
to zlib/binascii CRC32 with their conventional zero API initial value. Verify every
repetition, CRC, body length/actual bytes and digest before treating a record as valid.
Footer marker alone is never a commit proof. CRC/SHA detect corruption, not origin,
malicious replacement or calibrated physical accuracy. Authentication/encryption
are outside v1.

`Ref` is `{offset:D,length:D,ordinal:D,first:D|null,last:D|null,hash:Hash}`.
`hash = SHA256(exact header[0:40] || raw body SHA256 digest)` (72 hash-input bytes).
`first/last` are **global entry ID bounds**, not timestamps or per-device sequences.
Null/null only represents an empty tree/checkpoint/END where specified. References
cannot name a URI/another file and must end before the referring record begins,
have lower ordinals, agree with referenced length/type/body/range and use checked
arithmetic. Root references refer to INDEX_PAGE; leaf references to CHUNK. References
to CHECKPOINT use its indexed first/last bounds; `last_chunk` is a CHUNK reference.

### 5.2 Writer acknowledgement and file lifetime

Write complete header/body forward; flush/sync body; append complete footer;
flush/sync footer; only then advance acknowledged committed counts. Queue admission,
prepared matrices, codec output and sealing are not commits. Index/checkpoint/END
use the same order. Failed sync/close never reports successful Stop, even if a
forensic reopening finds intact bytes. Storage assurance depends on the filesystem
and APIs honoring barriers; no physical power-loss certification is implied.
New local file-entry persistence must use/document platform-supported directory
barriers where available, or disclose that limitation. The writer never seeks
backwards, patches a header/index, truncates commits or relies on SAF rename/hard
links. Stop drains bounded pending work, writes bounded index state and END; no
whole-file payload rewrite or mandatory backup copy.

Structural footer validity and producer acknowledgement are different evidence.
A reader can discover structurally complete unacknowledged bytes after a failure;
it labels them recovered, never invents a prior live success. END cannot prove that
the writer's subsequent backend close succeeded (§13).

## 6. HEADER, features and stream declarations [A meaning; P complete grammar]

The type-1 body is canonical JSON, without a CHUNK length prefix.

```text
Header = {
  format: "lmthermal-recording", kind: "recording",
  container_version: Version(1,0), schema_version: Version(1,0),
  recording_id: UUID, producer: Producer, creation_time: UtcRecord,
  required_features: Features, codecs: sorted unique [0|1] (1..2 items),
  timeline: TimelineDomain, streams: [StreamSlot] (1..64),
  privacy: Privacy, lineage?: Lineage, extensions?: Extensions
}
TimelineDomain = {clock_domain_id:UUID, origin_monotonic_ns:D,
                  resolution_ns:D+|null, source:"host_monotonic"}
StreamSlot = {id:Id, purpose:"thermal"|"visible"|"auxiliary"|"opaque",
              label?:Text, extensions?:Extensions}
Privacy = {sensitive_metadata:"omitted"|"consented", scene_content:true|false,
           consented_namespaces:[Namespace] (0..64 sorted unique)}
Extension = {schema_version:Version, data:JsonValue, required_feature:Id|null}
Extensions = object mapping unique Namespace keys to Extension (0..64)
UtcRecord = {status:"known"|"unreliable"|"unknown", clock_source:Id,
             utc?:Utc, uncertainty_ns?:D, reason?:Reason}
```

Stream IDs are unique and sorted. Slots declare logical purposes, **not** fixed
HT geometry, module or physical synchronization. Every active source's actual
configuration appears in chunk-local segment closure (§7). A replacement may use
the same logical slot in a new segment, never reinterpret an older observation.
The fixed 64-slot inventory may include a future inactive visible slot. Adding an
undeclared logical slot requires a new recording; no HEADER patch or unbounded list.
Multiple slots/clock domains are a format capability, not a claim that today's app
supports simultaneous physical cameras. No pixel registration/synchronization is
inferred from timestamps, a common session label or similar dimensions.

Every HEADER and CHUNK requires `org.lmthermal.lmtr.core-v1`; baseline readers
understand its framing, temperature/mask/native-coordinate, opaque retention,
clock/gap and index grammar plus codecs 0/1. A file using any view additionally
requires `org.lmthermal.lmtr.contiguous-native-view-v1` in HEADER and each affected
CHUNK. HEADER is a superset of every later required feature/codec; predeclared
unused features/codecs are allowed, undeclared later ones reject. Each non-null
extension `required_feature` must be in its containing CHUNK's required features
(or HEADER for a header extension), and in HEADER. Optional unknown extensions
are never executed; semantics essential to measurement cannot be optional.
An unknown native encoding may remain opaque retained evidence when no required
feature demands interpretation; its generic declared dtype/shape/hash still validate.

UtcRecord known requires `utc` and forbids `reason`; unreliable requires `reason`
and may contain a genuine reading; unknown requires `reason` and forbids `utc`.
An uncertainty is present only when known, not substituted with zero. Creation
UTC is unrelated to sensor UTC. Privacy defaults are the emitted `omitted` state:
no GPS, serial, USB path, network/host identity or private diagnostics unless actual
consent is recorded for that namespace. Credentials/tokens MUST never be retained.
Raw/transport/visual evidence may disclose the scene even without identity metadata.

`Lineage` is optional and newly proposed:
`{operation:"conversion"|"reprocessing"|"continuation", parents:[Parent](1..16),
 algorithm:EvidenceValue, extensions?:Extensions}`;
`Parent={format_id:Id,recording_id:UUID|null,file_sha256:Hash,file_bytes:D+}`.
Continuation observations are new source data and do not invent parent entry IDs.
Conversion requiring unavailable genuine recorder-clock witnesses is not representable
by this v1 timeline: refuse that operation, preserve the legacy original and require
an explicitly reviewed future conversion feature rather than invented intake times.
Hashes cover the complete **frozen original** bytes, including a retained incomplete
tail, not a guessed future final file. Unknown legacy recording identity stays null.
Conversion/reprocessing observations require `parent_entry` of type `ParentEntry={parent_index:I,entry_id:D|null}`;
`parent_index` resolves to the header's parents; `parent_entry` is forbidden without
lineage, required on conversion/reprocessing observations and absent on genuinely
new continuation observations; null legacy entry IDs require
namespaced original-reference evidence. Reprocessing requires known algorithm/settings
and new recording UUID; it never silently replaces parent Celsius. Parent absence
on disk does not make an otherwise complete child corrupt. No external file is
followed automatically. Post-capture analysis projects/sidecars are outside this
wire profile; source files remain immutable and are not copied solely to add ROI.

## 7. Segment/clock/context closure [A preservation; P representation]

A CHUNK contains complete definitions for every segment/context its entries use;
no lookup in an earlier chunk is necessary. Segments introduce actual configurations
and clock boundaries. Module, geometry, source session, clock domain or persistent
preservation/sampling policy replacement starts a new segment. Context-only dynamic
calibration/settings change may use a new context without ending the segment.
Do not approximately deduplicate calibration/FPA/trailer observations.

```text
Segment = {id:Hash, start_entry:D, start_timeline_ns:D, reason:Reason,
           streams:[SegmentStream](1..64 sorted by stream_id), extensions?:Extensions}
SegmentStream = {
  stream_id:Id, session_id:UUID, source:Source, geometry:Geometry|null,
  capabilities:Capabilities, encodings:[EncodingOffer](0..64), clocks:[ClockDomain](0..64),
  utc_anchors:[UtcAnchor](0..64), policy:Policy, sampling:Sampling, extensions?:Extensions
}
Source = {module_id:Id, model_id:Text|null, module_version:Text|null,
          origin:"device"|"simulated"|"imported_legacy"|"unknown"}
Capabilities = {temperature:Support, native_samples:Support, acquisition:Support,
                preview:Support, visible:Support, calibration_settings:Support}
Support = "supported"|"partial"|"unsupported"|"unknown"
EncodingOffer = {role:PayloadRole, encoding_id:Namespace, extensions?:Extensions}
ClockDomain = {id:UUID, kind:"host_monotonic"|"sensor_counter", unit:"ticks",
               tick_period_ns:{numerator:D+,denominator:D+}|null, resolution_ticks:D+|null,
               quality:"known"|"unreliable"|"unknown", extensions?:Extensions}
Stamp = {clock_domain_id:UUID,ticks:D}
UtcAnchor = {stamp:Stamp,utc:UtcRecord,uncertainty_ns:D|null}
Policy = {level:"analysis"|"native"|"full"|"visual",
          requested_roles:[PayloadRole](sorted unique), choice:"explicit"|"remembered_explicit"}
Sampling = {mode:"acquisition"|"periodic", period_ns:D+|null, origin_timeline_ns:D|null}
Context = {id:Hash,value:ContextValue}
ContextValue = {source:Source, algorithm:EvidenceValue, calibration:EvidenceValue,
               settings:EvidenceValue, environment:EvidenceValue, extensions?:Extensions}
EvidenceValue = {status:"known"|"partial"|"unknown"|"unavailable"|"unsupported",
                 value?:JsonValue,reason?:Reason}
```

Segment ID is SHA-256 of §3.2 canonical JSON of its complete definition excluding
only `id`; `start_entry` prevents a closed segment being reused at a new position.
Verify this hash independently in every closure. It avoids a duration-sized UUID
history map: retain only the active definition during streaming validation.
Context ID is SHA-256 of §3.2 canonical JSON bytes of `value`, computed once from
owned metadata, not guessed from approximate numerical equality. A local list
contains unique IDs; references use those IDs. Every closure list is sorted by ID.
A context's source MUST exactly match the applicable SegmentStream source. Same
segment ID requires the same immutable definition whenever repeated; readers reject
contradictions they encounter, and full metadata validation verifies all repetitions.
Context hashes permit independent closure validation without retaining a whole-file
state dictionary. This new canonical-hash cost is not covered by R2 performance proof.

EvidenceValue known requires `value` and no reason; partial requires both; all other
states require reason and forbid value. Unknown is lack of knowledge, unsupported
is a known capability absence, unavailable is transient access/observation absence.
Available original floating calibration/settings/environment values MUST preserve
their source bits: exact decimal value, or `{type:"f32le"|"f64le",bits:<8|16 lowercase
hex digits of LE bytes>}` inside the module's JsonValue. Do not use a rounded UI
string as interpretation evidence. Unknown source numeric types stay explicitly
unknown with original evidence when available. This newly proposed typed representation
can retain finite/nonfinite module facts; it cannot introduce nonfinite JSON numbers.
Opaque calibration payloads can additionally retain original bytes; no default coefficients are
invented. Interpretation context available to the module must be retained in every
radiometric level. Large opaque calibration evidence is an entry payload, not an
external URI. A no-longer-available blob has a truthful retention reason.

Periodic Sampling requires period/origin; acquisition requires both null. Nominal
frame rate may be optional extension evidence; it creates no scheduled slots by
itself. Policy roles cannot omit temperature/required validity/context when an
available radiometric plane is retained. Native adds actual native evidence; Full
requests meaningful maximum module evidence; no hidden downgrade. `visual` is for
nonradiometric sources, not a way to discard available measurements. Analysis
default/visible first-use choice/remembered explicit per-module choice and unknown-
capacity behavior remain accepted product policy, not an implemented recording UI.

Geometry is non-null for temperature/native/visual grid interpretation, null only
for genuinely opaque-only streams. Primary geometry is fixed within a segment.
A preview/visible payload may have independent geometry, declared by its descriptor;
it does not inherit thermal registration. Every actual payload role/encoding (except opaque module_extension extras) must
match an EncodingOffer for that segment; a newly offered source encoding starts a
new segment. Null model/version means genuinely unknown, not a guessed label.
No current commercial camera support is
implied by a generic schema example.

Clock definitions and anchors must resolve locally and be internally consistent.
A null tick_period_ns means genuinely unknown conversion frequency. Known quality
requires a known period/domain; unreliable/unknown quality may retain actual ticks
without converting them. A stamp with unknown/unreliable mapping uses unreliable
TimeEvidence and a reason, not invented nanoseconds. Receipt host clocks use actual
host units (ns uses period 1/1). Resolution null never becomes precision zero.
A clock UUID is an actual domain, not a device serial; reused domains retain unit,
period and origin meaning. Overflowing/nonrepresentable source ticks are retained
as opaque module facts and marked unknown in the common clock model, never clipped.
Sensor resets/wraps require a fresh domain/segment before comparison. UTC anchors
are actual paired observations; uncertainty null is unknown, not exact. Do not
extrapolate reliable UTC across a clock reset or infer sensor UTC from host receipt.

## 8. Entries, chronology and gaps [A meaning; P exact fields]

Each CHUNK has 1..1024 entries. Global `entry_id` begins at 0 and advances exactly
by 1 across chunks. It counts **serialized entries**, including segment events and
compressed gap events; not vendor frames, expected slots or committed measurements.
`timeline_ns` is the observed recorder-intake/event-witness host monotonic time minus
HEADER origin, checked nonnegative and nondecreasing. It is **not** sensor time,
commit time or a missing frame's fabricated receipt. Timestamp ties retain entry
order. No clamping/rounding to force order: stop/refuse an inconsistent producer.
This deliberate generic event timeline differs from R2's directly mapped receipt
relative time; retain original receipt separately. Late stream arrivals retain
older source times without reordering the append log or claiming synchronization.

`Entry = Observation | Gap | SegmentStart | SegmentEnd`.
Every variant shares `entry_id:D, timeline_ns:D, segment_id:Hash` and optional
`extensions:Extensions`. For observation/gap, required `stream_id:Id` resolves to
a SegmentStream. A segment_start/end instead has no stream_id and applies to the
segment's declared streams. Known fields from another variant are forbidden.

```text
Observation = {
  kind:"observation", entry_id:D, timeline_ns:D, segment_id:Hash, stream_id:Id,
  observation_sequence:D, device_sequence:D|null,
  receipt:TimeEvidence, acquisition:TimeEvidence, scheduled:ScheduledRange|null,
  context_id:Hash, measurement:Measurement, retention:Retention,
  payloads:[Payload](0..64), analysis?:Analysis, presentation?:Presentation,
  parent_entry?:ParentEntry, extensions?:Extensions
}
TimeEvidence = {status:"known"|"unreliable"|"unknown"|"unsupported"|"unavailable",
                stamp:Stamp|null,utc:UtcRecord,reason?:Reason}
ScheduledRange = {first_slot:D,last_slot:D,first_deadline_ns:D,last_deadline_ns:D}
Gap = {
  kind:"gap", entry_id:D,timeline_ns:D,segment_id:Hash,stream_id:Id,
  category:"source_loss"|"preparation_overload"|"writer_overload"|"missed_schedule"|"interruption",
  reason:Reason, count:D+|null, evidence:GapEvidence,
  scheduled:ScheduledRange|null, device_first:D|null,device_last:D|null,
  observation_first:D|null,observation_last:D|null,extensions?:Extensions
}
GapEvidence = {basis:"device_sequence"|"host_observation"|"declared_schedule"|"explicit_signal"|"unknown",
               before:Stamp|null,after:Stamp|null}
SegmentStart = {kind:"segment_start",entry_id:D,timeline_ns:D,segment_id:Hash,extensions?:Extensions}
SegmentEnd = {kind:"segment_end",entry_id:D,timeline_ns:D,segment_id:Hash,
              reason:Reason,extensions?:Extensions}
```

A segment starts with one segment_start matching its `start_entry/time`, then zero
or more entries, and at most one segment_end. Completed recordings close every
started segment before END. No earlier segment receives later entries after its
end or a successor start; one active global segment at a time, possibly many
logical streams. Repeated closure in a later chunk does not emit another start.
A crash may leave the last segment open, truthfully incomplete. The complete source
session ID lives per stream, so logical multi-stream data may share a physical
session or represent different ones, without implicit registration.

Observation_sequence strictly increases within (segment,stream); starting value
is arbitrary actual host observation-counter evidence, not forced vendor zero.
Skipped observed counters require known host-observation gaps; unknowable vendor
loss does not get guessed. Device_sequence stays null when absent. A true device
sequence reset starts a new segment/domain; do not treat decrease as overflow.
Scheduled ranges obey first<=last, declared periodic period and checked deadline
formula `origin_timeline_ns + slot*period_ns`. Observation scheduled first=last.
Acquisition-following entries have scheduled=null. Source acquisition is independent
of sampling: unused genuine frames are not fabricated missing vendor sequences.

Known TimeEvidence requires a stamp or known UTC; unreliable requires genuine
available reading(s) and a reason; unknown/unsupported/unavailable require stamp=null,
unknown UTC and reason. Receipt stamps refer only to host-monotonic domains;
acquisition stamps may refer to genuine sensor domains. R2 HT-301 host receipt
was milliseconds expressed in ns, so resolution remains 1000000 ns, **not** a
proven nanosecond sensor clock. A gap's timeline is detection/intake, not a substitute
receipt/acquisition time. Gap before/after stamps are actual bounds in their own
domains and never create a midpoint UTC.

Gap count is known only with supporting explicit signal/range/schedule. Non-null
range endpoints must both exist, be ordered and imply `count=last-first+1`; all
independent supplied counts must agree. Null count is allowed only with no exact
range/schedule count. Reason remains distinct by category. Intentionally sampled
out frames are not recorder overload. Multiple causes cannot be merged into one
range. Coalesced known loss ranges preserve exact identities; unknown interruption
duration/count stays null. Gaps have **no payloads/measurement/context/readings**.
Observed unavailable frames are Observations, not missing-event Gaps.

## 9. Measurement and retention [A authority; P representation]

```text
MeasurementAvailable = {status:"available",temperature_payload_id:Id,
                         validity:"all_valid"|"partially_valid"|"no_valid_pixels",
                         mask_payload_id?:Id,provenance:Provenance,statistics?:Statistics}
MeasurementAbsent = {status:"unavailable"|"unsupported"|"unknown",reason:Reason}
Provenance = {kind:"native_equivalent"|"device_reported"|"simulated"|"derived"|"unknown",
              physical_accuracy:"not_independently_validated"|"independently_validated"|"unknown"|"not_applicable",
              algorithm?:{id:Namespace,version:Text},warning?:Text,validation_evidence?:JsonValue}
Retention = {temperature:Availability,native_samples:Availability,acquisition:Availability,
             preview:Availability,visible:Availability,calibration_settings:Availability}
Availability = {status:"present"|"unsupported"|"unavailable"|"unknown"|"omitted",reason?:Reason}
```

`Measurement = MeasurementAvailable | MeasurementAbsent`.
Present forbids reason and must match actual payload(s) (calibration/settings matches
known/partial Context values or identified opaque calibration extension evidence).
Every other status requires reason and forbids a matching payload. Unsupported
capability cannot claim present; unknown/partial support may have actual present
facts. Omitted is an intentional policy decision, not unknown/unavailable. Native
and acquisition in Analysis can be omitted; requested available evidence cannot be
silently omitted. Temperature/validity cannot be withheld when a current radiometric
measurement is recorded. At most one temperature/mask/native/acquisition/preview/
visible role each per observation; multiple evidence extras use module_extension
IDs. Modules needing multiple primary grids use different stream slots.

Available means a complete plane exists, not that every cell is measurable.
Partially/no-valid requires a mask with corresponding exact counts; all_valid may
omit a mask or include an all-ones mask. Absent measurement forbids temperature,
mask, measurement provenance/statistics and Celsius annotations. All-invalid planes
retain full grid/mask but expose no temperatures. Other actual native/acquisition/
visual evidence may remain present. Unsupported/unknown/unavailable are separate
from intentional omission. No last-good values, image-derived temperatures or
thermometry recomputation during normal load/playback.

HT-301 native_equivalent provenance MUST retain
`physical_accuracy="not_independently_validated"` and the exact warning above.
Other physical-validation claims require bounded actual evidence metadata; hashes
are not calibration/authenticity. Matrix-derived high/low and literal center are
independent of module summaries. HT trailer center is not assumed to be `(192,144)`.
Source summaries belong to namespaced extension data, not core matrix Statistics.

## 10. CHUNK body, payloads and canonical numerical data

**[E]** length-prefix/role blocks/lossless bytes/restricted views; **[A]** complete
matrix/mask/ordinary playback authority; **[P]** generalized descriptors and ordering.

CHUNK body = u32 LE metadata-byte-length + canonical UTF-8 JSON + stored blocks.
No padding. Metadata length is actual UTF-8 bytes, <=1 MiB; total decoded physical
blocks plus metadata plus four prefix bytes <=64 MiB; stored total envelope <=65 MiB.

```text
Chunk = {recording_id:UUID,required_features:Features,segments:[Segment](1..64),
         contexts:[Context](0..1024),entries:[Entry](1..1024),blocks:[Block](0..256)}
PayloadRole = "temperature"|"validity"|"native_samples"|"acquisition"|"preview"|"visible"|"module_extension"
Block = {id:Id,role:PayloadRole,encoding_id:Namespace,dtype:Dtype,byte_order:ByteOrder,
         offset:D,stored:D+,decoded:D+,codec:I(0..1),hash:Hash}
Dtype = "u8"|"i8"|"u16"|"i16"|"u32"|"i32"|"u64"|"i64"|"f32"|"f64"|"bytes"
ByteOrder = "little"|"big"|"not_applicable"
PayloadBase = {id:Id,role:PayloadRole,encoding_id:Namespace,dtype:Dtype,
               byte_order:ByteOrder,shape:[I](1..3),layout:"row_major"|"opaque",
               unit:Text|null,geometry:Geometry|null,length:D+,hash:Hash,
               lossiness:"lossless"|"lossy"|"unknown",namespace?:Namespace,extensions?:Extensions}
Materialized = PayloadBase + {kind:"materialized",block_id:Id,offset:D}
View = PayloadBase + {kind:"view",parent_payload_id:Id,offset:D}
```

`Payload = Materialized | View`.
Recording_id matches HEADER. Closure IDs are unique/sorted; list references resolve
before payload exposure. Every listed closure is used by an entry; future/outside
segment definitions and unreferenced data are rejected. Each active entry references
a local segment, and every observation a local context. Segment events need no context.
No earlier source/module/schema state is silently inherited.

Physical blocks are role-major groups keyed by `(role,encoding_id,dtype,byte_order)`.
Role order is the seven alternatives above; encoding sorts ASCII; dtype order is
its listed alternatives; byte-order order is little/big/not_applicable. Writers
use exactly one nonempty block for each materialized group; no block for views.
IDs are `b0`, `b1`, ... in that group order. Blocks are contiguous in the stored-block
area, first offset 0, each next offset = previous offset+stored; last ends at body
end. A gap/segment-event-only chunk has zero blocks, no trailing bytes.

Within a group concatenate complete logical payloads in entry_id order, then
payload ID ASCII order. Materialized offsets refer to the **decoded** group;
nonoverlapping slices cover it exactly, with no unreferenced bytes/dedup aliases.
Block role/encoding/dtype/byte_order match each slice. Every logical hash is over
exact decoded slice bytes; block hash over the complete decoded group; body hash
covers actual prefix/JSON/compressed blocks. Verify all three integrity layers.
Core role payload IDs equal their role strings; module-extension payloads use unique
other Ids and mandatory namespace. Observation payloads sort by role order then ID.
Shape products are positive/checked; numeric item widths are 1/2/4/8 bytes by dtype.
Bytes dtype is `[length]`, opaque layout, null geometry/unit, not_applicable byte
order; byte-length D must also fit I/record limits. Numeric layouts are row-major,
unit nullable, geometry non-null, `[height,width]` or `[height,width,channels]` and
exact item-width product. Single-byte dtypes use not_applicable; multibyte natives
may use little/big **without** a byte swap. Numeric native NaN bit patterns remain
source evidence; temperature has stricter rules below. Baseline readers may retain
unknown encoding bytes without claiming module interpretation.

Temperature is materialized `f32/little/[h,w]/row_major/unit="Cel"/lossless`, encoding
`org.lmthermal.temperature.f32le-v1`, geometry identical to its SegmentStream.
Validity, when present, is materialized `u8/not_applicable/[h,w]/row_major/unit=null/
lossless`, encoding `org.lmthermal.validity.u8-v1`, same geometry and one byte/cell.
Temperature source bits are IEEE-754 binary32 LE, full original grid, x fastest.
Valid cells are finite and **preserve original bits, including valid negative zero**.
Mask bytes are exactly 0 or 1. Invalid temperature cells are exactly positive zero
`00 00 00 00`. A missing mask means every cell valid; reject invalid values rather
than using NaN/sentinels. No-valid means all-zero mask, full +0 plane, no readings.
Native/acquisition evidence is exact and lossless. Temperature/validity/native/
acquisition cannot be marked lossy. No palette/ROI crop, transposition, quantization,
high-bit masking or thermometry recomputation can supply these roles.

Visual raw encodings are `org.lmthermal.visual.gray8-v1`, `...rgb8-v1` and `...rgba8-v1`,
u8 not_applicable row-major, `[h,w]`, `[h,w,3]`, `[h,w,4]` respectively; RGB channels
are sRGB code values and RGBA alpha is straight/unassociated. Independent source
geometry is explicit. Other encoded preview/visible bytes use bytes dtype and a
namespaced encoding, plus `extensions` identifying their own image dimensions;
baseline readers may show unavailable if they lack its optional local decoder.
Lossy visual retention must be marked lossy, and never becomes numerical authority.
Decoded display allocations obey §4, no recursive/external code loading.

### 10.1 Restricted views

Only native_samples may be a view, only into that **same observation's** materialized
acquisition parent. Parent must be in the same CHUNK; offset/length are relative
to the parent's logical decoded bytes, checked nonnegative/end<=parent length.
No view-of-view, nesting, cross-stream/observation/chunk reference, strides, transforms,
byte swap or decompression operation disguised as a slice. Numeric dtype alignment
and exact shape/length/endian are verified. Verify parent's full logical hash and
block/body integrity **before** exposing the independently hashed native slice.
A corrupt parent is integrity failure for the view too, not an unavailable reading.
The writer must compare the slice with independently owned native evidence before
advertising it. Physical zero-copy is optional; exact preserved bytes are mandatory.

HT-301 example: acquisition 224256 bytes, native view offset 0/length 221184,
shape `[288,384]`, u16/little, encoding `org.lmthermal.ht301.raw14`, every complete
word <0x4000. All four trailer rows stay outside the native image; no bit masking.
This is a module extension rule, not a universal shape/bit-depth/USB obligation.
Other modules retain independent materialized native/acquisition data when distinct.

### 10.2 Statistics, analysis and presentation [A arithmetic; P embedding]

`Statistics={temperature_payload_id:Id,method:"finite-valid-row-major-f64-v1",
 unit:"Cel",pixel_count:I,valid_pixel_count:I,min?:number,max?:number,mean?:number,
 min_xy?:[I,I],max_xy?:[I,I]}`. Counts are exact for the full plane or named ROI.
Zero valid requires all five result fields absent; otherwise all are required,
finite, with valid in-bounds coordinates. Min/max compare actual saved Float32;
ties keep first native row-major valid cell, including signed-zero numeric ties.
Float64 sum starts +0, adds exactly promoted valid Float32 in sequential row-major
order then divides by count, round-nearest/ties-even; no fast-math reassociation.
Cache values agree within still-compatible `max(1e-4,1e-6*abs(calculated))` Celsius;
counts/locations exact. Tolerance never permits changing matrix bytes. Cached
statistics are matrix-derived/non-authoritative, not source/trailer summaries.

```text
Analysis = {producer:Producer,creation_time:UtcRecord,
            points:[Point](0..128),rectangles:[Rectangle](0..128),annotations:[Annotation](0..128)}
Point = {id:Id,coordinate_space:"native",x_px:I,y_px:I,label?:Text,temperature_c?:number}
Rectangle = {id:Id,coordinate_space:"native",bounds:{x1_px:I,y1_px:I,x2_px:I,y2_px:I},
             interval:"half_open",label?:Text,statistics?:Statistics}
Annotation = {id:Id,text:Text,target_id:Id|null}
Presentation = {palette_id:Id,range_mode:"auto"|"locked"|"none",
                effective_bounds:{min:number,max:number,unit:"Cel"}|null,
                transform:{rotation_degrees:0|90|180|270,mirror_x:boolean,mirror_y:boolean}}
```

Analysis is an optional **complete snapshot on that observation**, not a sticky
ROI carried across gaps. IDs unique across its three lists; annotation target
resolves locally or is null. Points are in bounds; rectangle uses native half-open
`0<=x1<x2<=width`, likewise y. Temperature/Statistics can only reference a currently
available plane and valid pixels; unavailable entries may retain geometry/labels
but no Celsius caches. A snapshot may explicitly clear lists; no snapshot means no
recorded analysis claim. Cross-observation tracks/interval projects/future diagnostic
structures live in namespaced extensions, never guessed from repeating IDs.

Palette IDs are white_hot, black_hot, inferno, iron_like, turbo or optional future Id.
Unknown optional palette falls back to white_hot with disclosure and retained ID.
Auto/locked requires actual measurable pixels and finite min<max bounds; none has
null bounds and is required for unavailable/no-valid entries. Rotate clockwise
first, then mirror in rotated display axes; reverse-map interactions to native
coordinates. Invalid cells have an unavailable color; +0 filler is not valid 0 C.
Palettes, bounds, annotations and transforms never change primary matrices/ROIs.
Later inspection is memory/project state; finalized recording bytes stay immutable.

## 11. Codecs [E baseline; P registry]

| ID | Proposed meaning | Obligation |
|---:|---|---|
| 0 | STORED bytes, stored=decoded | Mandatory baseline decoder |
| 1 | One RFC1950 zlib-wrapped RFC1951 DEFLATE stream | Mandatory baseline decoder; level 1 recommended writer policy |
| 2 | Reserved for a possible future Zstd extension; **unassigned in v1.0** | Reject if used; no production adoption |
| 3..65535 | Reserved/unassigned | Reject if used |

Codec1 requires CM=8, CINFO<=7 (window<=32768 bytes), valid FCHECK, FDICT=0,
valid Adler32, exact decoded length, single end-of-stream and complete consumption
of stored input. No gzip, raw-DEFLATE, external dictionary, concatenation or trailing
input. Enforce decoded budgets during streaming, not after unchecked expansion.
Writers may choose codec0 before writing a block when DEFLATE expands it; this is
lossless storage choice, not a payload/profile downgrade. Declare every used codec
in HEADER. Level 1 is measured recommendation, not a decoder restriction or promise
of identical encoded streams. Definitions: [RFC1950](https://www.rfc-editor.org/rfc/rfc1950)
and [RFC1951](https://www.rfc-editor.org/rfc/rfc1951).

R2 reference Zstd 1.5.7 optional parity is successful experimental evidence, not
sustained product acceptance. This draft recommends reserving ID2 without defining
its production grammar now. Later owner-approved minor extension could bind it to
an explicit required feature, one checksummed frame, no dictionaries and bounded
window; it requires its own complete grammar/vectors/runtime evidence. Baseline
v1.0 rejects it even in recovery, never silently substitutes or drops the role.
This recommendation differs explicitly from annex's possible optional codec2.

## 12. Paged index, checkpoints and END [E topology; P generic summaries/time index]

All three bodies are canonical JSON, no CHUNK prefix. Their bodies are <=512 KiB.
Every reference checks §5; never allocate/load the whole index just to open a file.

```text
IndexChild = {record:Ref,time_first_ns:D,time_last_ns:D,stream_ids:[Id](sorted unique)}
IndexPage = {depth:I(0..7),children:[IndexChild](1..256),
             first:D,last:D,time_first_ns:D,time_last_ns:D,
             stream_ids:[Id](sorted unique),chunks:D+,entries:D+}
RoleBytes = {temperature:D,validity:D,native_samples:D,acquisition:D,preview:D,visible:D,module_extension:D}
StreamSummary = {stream_id:Id,observations:D,temperature_planes:D,measurable_planes:D,
                 gap_events:D,known_missing_observations:D,unknown_gap_events:D,logical_bytes:RoleBytes}
Summary = {chunks:D,entries:D,observations:D,temperature_planes:D,measurable_planes:D,
           gap_events:D,known_missing_observations:D,unknown_gap_events:D,
           physical_payload_bytes:D,logical_bytes:RoleBytes,streams:[StreamSummary](1..64),
           first_entry:D|null,last_entry:D|null,first_timeline_ns:D|null,last_timeline_ns:D|null}
Checkpoint = {recording_id:UUID,root:Ref|null,last_chunk:Ref|null,
              previous_checkpoint:Ref|null,summary:Summary}
End = {recording_id:UUID,root:Ref|null,checkpoint:Ref,
       summary:Summary,status:"completed"|"interrupted"|"failed",
       reason:Reason,finalization_time:UtcRecord,error:StorageError|null}
StorageError = {code:"storage_full"|"storage_failure"|"source_failure"|"local_resource_limit",
                detail?:Text}
```

Depth0 children reference CHUNK; higher children reference INDEX_PAGE exactly one
level lower. Children cover ordered/disjoint global entry ranges without holes
between adjacent CHUNK entry IDs. first/last/time bounds and stream_ids are exact
unions of children (time boundary ties allowed). An entry for a stream or a segment
event involving that stream contributes it to stream_ids. `chunks/entries` are
exact child sums; CHUNK contributes entries.length and one chunk. No duplicate
leaf reachability. Ref first/last and actual child bounds agree. Tree cannot cycle
because every reference ends before the referring page and has lower ordinal.
A page seals early when its exact JSON size would exceed 512 KiB, even with fewer
than 256 children; overflow/depth exhaustion refuses further writing explicitly.
These limits are not an instruction to preallocate maximum-size trees.

Writers append rolling pages and immutable snapshots of incomplete paths, keep
<=256 pending refs per level and never reinsert snapshot leaves as new chunks.
A checkpoint root indexes every acknowledged CHUNK exactly once, not INDEX_PAGE/
CHECKPOINT/END records. Zero chunks means root/last_chunk null; summary entries and
all observation/gap/byte counters zero with first/last/time null. Nonempty summary
first_entry=0, last_entry=entries-1 and truthful timeline range. Every HEADER stream
has one sorted StreamSummary (unused streams zero). Sum stream counters equals
aggregate counters except entries (which also counts segment events). Known missing
observations adds exact Gap.count (including declared missed sampling slots, not
an inferred count of sensor acquisitions); unknown gap events counts null counts. Observation
unavailability is not a missing observation. Temperature_planes counts present
planes including no-valid; measurable_planes requires >=1 valid pixel. Physical
payload bytes sum block.decoded, excluding metadata; logical bytes sum every payload
length including views, so Full's dedup does not hide logical native retention.
Counts are acknowledged committed CHUNK entries only, not accepted/pending work.

CHECKPOINT occurs after <=32 CHUNK commits. A fresh CHECKPOINT before END covers
all drained final chunks and points to the prior checkpoint (null for the first).
END root and summary equal its checkpoint. completed has user/normal Stop reason,
error=null; interrupted names detach/background/locale/module/session interruption,
error=null; failed requires a StorageError and explicit reason. Every started
segment must have a committed end event; if exhaustion prevents this, do **not**
write a misleading final END: retain incomplete prefix. Finalization UTC is truthful
export/Stop time, never a fabricated last acquisition UTC. No extra data after END.

Normal open validates HEADER, actual terminal envelope/END/checkpoint and bounded
root, then seeks lazily by entry ID or monotone recorder timeline through <=8 pages.
A stream-specific seek filters explicit stream_ids; a source's own sensor clock
is not the common timeline. Verify actual child metadata ranges at traversal, body/
block/logical payload integrity before exposing the selected measurement. Cache
at most two pages per level under 8 MiB; decoded chunk cache is separately negotiated,
not proportional to duration. A full streaming integrity/metadata pass checks every
leaf, counter, repetition and payload; lazy open cannot certify unvisited bytes.
Report `integrity_scope=opened_records|selected_chunks|full` separately from an
END's **declared** completion. No ordinary image/readings come from unverified bytes.

END's final bytes may survive a later failed backend close; it declares successful
append/sync up to END, not an external close acknowledgement. Live UI reports success
only after close and reopen checks (§15). A forensic reader must not infer that a
previous producer reported success merely from intact END bytes.

## 13. Read-only recovery and errors [A no fabricated data; E bounded fast path; P full classification]

A recovery reader MUST validate HEADER and negotiate its required features/codecs
first. HEADER corruption/unsupported interpretation is not bypassable. Initial
normal open inspects the true final footer; missing/torn END invokes bounded recovery.
No in-place resume, truncation, append, source mutation or fabricated terminal time.

Fast recovery: inspect at most the last **68157512 bytes**, identify candidates by
footer marker then validate header/CRC/repeated fields/actual length/body hash.
At most 1024 candidate envelopes are tried; exhaustion reports recovery_limit.
From a verified record follow checked contiguous predecessor offsets/ordinals,
<=1024 predecessor records, to a valid checkpoint or HEADER. Validate at most 64
suffix CHUNKs and required closure; combine checkpoint prefix and suffix without
reinserting already indexed leaves. One damaged checkpoint interval plus the
current interval is the supported fast case, not arbitrary interior damage.
A failed INDEX_PAGE/CHECKPOINT/END body is rebuildable navigation metadata; framing
must still be trusted before following its previous link. If framing is damaged,
search only inside the allowed tail window for the preceding verified record.
Never trust a guessed length or magic match. Local CPU/I/O limits may refuse fast
recovery explicitly rather than repeatedly hashing adversarial candidate bodies.

A referenced CHUNK with a complete promised footer/body or decoded/logical integrity
mismatch is `committed_payload_integrity`, not a torn uncommitted tail or frame gap.
A final incomplete record without valid footer exposes no partial planes. Metadata
invalid in a committed CHUNK is invalid_metadata, not a missing observation. Known
bad payloads halt normal playback for their affected range; no old-frame substitution.
A separate explicitly requested salvage scan may expose independently verified
other ranges, marked incomplete/integrity-damaged. It never claims the source intact.

Recovery result is external reader state:
`{status:recovered|integrity_damaged|recovery_limit,verified_prefix_bytes:D,
 last_verified_entry:D|null,last_verified_timeline_ns:D|null,declared_end_status:Text|null,
 unknown_tail:true,integrity_scope:Text,reason:Reason}`. Structural byte prefix and
last verified measurement can differ (an index/footer contains no new measurement).
No absent END yields successful completion/final acquisition time. Even a valid
END cannot conceal known committed corruption. Support unknown-tail count/time;
source failure reason is only known when a committed event reports it.

If fast bounds are exceeded, return `recovery_limit`, offer a separately requested
cancellable full metadata/integrity scan with bounded working memory, and never
silently extend the fast scope. An optional rebuilt index is a disposable external
cache bound to source UUID, frozen size and integrity fingerprints; not source
repair, canonical truth or mandatory full recording duplication.

### Stable error classes and validation order

| Error | Meaning / action |
|---|---|
| unsupported_format / unsupported_major / unsupported_envelope | Identity/version unsupported; do not misinterpret as still/legacy |
| unsupported_required_feature / unsupported_codec | Required interpretation absent; reject even recovery |
| invalid_container | Framing/order/type/flags/reserved/reference/type contradictions |
| invalid_metadata | JSON/type/closure/time/counter/schema contradiction |
| invalid_payload | Shape/dtype/mask/nonfinite value/view/form contradiction |
| committed_payload_integrity | Promised stored/decoded/logical bytes/hash corrupt |
| resource_limit | File violates a contract hard bound |
| local_resource_limit | Valid bounds exceed local allocation/CPU/I/O/admission budget |
| recovery_limit | Explicit fast recovery range/traversal/candidate limit reached |
| storage_full / storage_failure / source_failure | Writer/export outcome, not a valid-frame gap |

Validate envelope identity/revision/bounds/CRC before trusting offsets; HEADER body
hash before metadata; JSON structural/type limits before feature negotiation;
CHUNK closure/features/budget then complete stored-body hash before decompression;
codec ending/decoded count, block hashes, logical hashes, masks/numerical rules and
reference/summary consistency. Never expose a partially validated measurement.
Multiple failures may be reported; a primary error from an earlier applicable stage
must not be disguised as a benign gap. Index damage/recovery status remains distinct
from corrupt promised source bytes. No checksum is an authenticity guarantee.

## 14. Chunk configuration, admission and actual idle flushing

**[E]** 4 MiB preferred HT-301/DEFLATE and separate 4 MiB queues; **[P]** generic
admission, wall-clock deadlines and time checkpoints; **[A]** bounded truthful loss.

The wire hard limits in §4 are different from writer engineering settings. Recommend
HT-301 preferred decoded physical target **4194304 bytes**, <=32 entries and <=1 s
observed recorder timeline. Count/byte/time thresholds seal **before** the next
entry would exceed a configured threshold; a singleton meeting/exceeding preferred
seals immediately if it fits every hard/local limit. Charge exact metadata/context
and prefix to hard bounds; a preferred byte target may be approximate while closure
is assembled, but hard limits must be checked before append. Stop/context pressure
can seal smaller chunks. No fabricated frames are used to reach a threshold.

Initial HT producer uses separate preparation and writer queues capped **4194304
charged bytes each**; owned payloads plus serialized context/gap metadata count.
One in-flight item per worker and one separately bounded <=1 MiB overload-gap
closure are accounted, not hidden as uncharged queue growth. Queue capacity is
not a format rule: other modules need an explicitly declared configuration/admission
budget. No on-the-fly growth or reduced source cadence to pretend overload passed.

A conformant observation larger than preferred uses a singleton, never splitting
its primary measurement across chunks. Before Start compute its **actual maximum**
requested owned payload/metadata size and peak-live reservation: source retention,
both queues and in-flight items, chunk assembly, metadata/Decimal objects, encoder
input/output/worst-case expansion/window, hashes, decode/display caches and safety
margin. Refuse unknown/unbounded or insufficient negotiated budgets with
local_resource_limit. If one observation exceeds a configured queue item capacity,
refuse that profile/configuration before Start; do not bypass charging, silently
raise a 4 MiB queue or crop/downsample. A separately benchmarked, explicitly chosen
larger-module configuration may negotiate different queues/preferences within hard
limits. File validity is not device support. Streaming/read-on-demand decoding
should precede larger blanket caches; readers may explicitly refuse local resources.

The measured 64 MiB incremental-memory target is **not** a universal heap ceiling.
R2's finite samples miss short peaks and include process-baseline variability;
its sparse large singleton used ~229.6 MB JVM heap. New generic/canonical JSON,
context hashes, mixed streams/visuals, timer/checkpoint policy and larger configurations
need future performance tests; R2's passing throughput is not silently transferred.

### Recommended monotonic deadline policy [P, untested]

1. When a complete prepared entry first enters a pending chunk, retain its actual
   intake-monotonic witness from timeline+origin. Schedule sealing at that witness
   **+1000000000 ns**, even if no later observation arrives. An already expired
   deadline requests immediate sealing. A monotonic timer/writer control signal,
   independent of USB callbacks, schedules existing complete entries only.
2. New arrivals do not move that first deadline. The worker serializes sealing with
   admission; no concurrent mutation of a chunk. Empty chunks have no timer. A timer
   cancellation cannot cancel a required Stop. Wall-clock/UTC adjustments are irrelevant.
3. The deadline requests sealing, **not** END and not a hard disk-ack latency promise.
   Codec/sync/scheduling can delay acknowledgement; pending counts/backlog remain
   truthful. Full queues produce explicit preparation/writer-overload gaps, not
   blocking acquisition. Timers do not recover source observations never received.
4. Checkpoint after <=32 commits, or **5000000000 ns after the first committed CHUNK
   not covered by a checkpoint**, whichever requests earlier. No repeated idle
   empty checkpoints. A timer can snapshot only committed index state while a later
   chunk remains pending. Stop seals/drains, closes segments, checkpoints and writes END.
5. At low rate, one real frame can commit after its deadline without awaiting the
   next frame. At idle, the committed active prefix is recoverable but no finished
   END is invented. Explicit detach/Stop ends the session/segment where possible;
   absence of callbacks alone is not proof of missing vendor frames or UTC duration.

Alternative arrival-only R2 policy minimizes timer work but leaves the final frame
pending indefinitely; timer-from-last-arrival can continually defer a busy stream;
frequent forced END breaks append lifetime. Recommend first-entry deadline plus
bounded time checkpoint: reduced idle exposure, modest metadata/sync overhead,
explicit scheduling/energy tradeoff. Owner approval and future timer races, provider
latency/power and real writer validation are required. No timer implementation here.

## 15. Storage, access, failure and publication [A product policy; E scoped assurance; P release profile]

Initial canonical recording is an **app-owned, non-cache local file** with documented
file/directory persistence capabilities and §5 sync/ack order. Catalogue discovery
includes unfinished files and read-only recovery; do not delete a valuable incomplete
recording merely because no END exists. External read-only access can use a local
file/descriptor without mandatory full duplication. A future URI provider/share
mechanism has its own permission/lifetime validation; none is implemented here.

Successful live Stop requires drain/END sync, successful backend close and read-only
reopen/terminal validation. Readback may validate terminal metadata lazily but must
not claim full source integrity without full streaming validation. An immutable
local canonical source is not rewritten by export, ROI, palettes or inspection.
App data clear/uninstall can remove private files; private storage and a granted
URI are **not independent backup**. Retention/deletion/backup choices must be visible
product policy, not a silent archive field that prevents OS removal.

Optional independent SAF export copies a frozen finalized source to a **new**
destination. Report success only after exact byte count, successful close, reopened
readback and whole-file SHA match. Cancellation/refusal/short write/failed close/hash
mismatch leave source unchanged, report failure, and remove only the app's own new
partial destination when the provider safely permits it. Never overwrite unrelated
existing content or label a partial destination a verified backup. Direct SAF,
remote/pipe canonical recording and multi-GB export assurances are outside demonstrated
R2 support; add provider-specific evidence before claiming them. A small local
Downloads success is not a universal persistence guarantee.

Unknown capacity on an otherwise supported backend: label unknown, concise warning,
**allow Start**. Known insufficient startup+reserve: block with explanation. Estimates
use disclosed conservative/observed rates, not promised compressed duration. Helpful
compact low-space warning thresholds are implementation/UI policy, not immutable
wire constants. Write refusal stops intake promptly, preserves prior commits, safely
seals/finalizes only what fits, distinguishes known exhaustion from generic failure,
and never counts pending observations saved. A failed END/close remains failure even
if later forensic bytes look complete. No mandatory second full copy just to inspect.

## 16. Illustrative scenarios and future conformance [P examples; A/E expectations]

[Illustrative JSON cases](lmtr-r1b/examples.json) are **NONCANONICAL R1b REVIEW DATA**,
not files to publish as accepted `.lmtr`, not camera captures and not R3 fixtures.
They provide concrete HEADER/CHUNK metadata, symbolic record plans and synthetic
logical-byte recipes with exact
expected actions. No binary container, concrete index/footer offsets or canonical fixture is generated.
A complete illustration denotes the intended HEADER/CHUNK/index/checkpoint/END plan;
future writers construct its references from actual framing, as §5/§12 specify.
The host checker verifies local
schemas/references/numeric examples/arithmetic and explicitly lists its limits.

| Case | Required reader/preservation outcome |
|---|---|
| 1 temperature-only complete | Use full stored f32/mask; native/acquisition omitted/unsupported truthful; no thermometry |
| 2 HT Analysis | Exact Celsius/context; omitted native/transport; physical warning retained |
| 3 HT Native | Celsius + original unmasked u16 native; no invented acquisition |
| 4 HT Full view | Celsius + full transport; verify parent then independently hashed slice; no physical duplicate native |
| 5 unavailable observation | Keep actual clock/context/reason and optional evidence; no Celsius/last-good caches |
| 6 recorder overload | Gap with exact observed range/count/detection bound; no filler plane |
| 7 preview-only | Visual plane without radiometric policy/temperature/provenance claim |
| 8 alternate geometry | Generic 13x7/full field, own encoding and no HT assumptions |
| 9 module/session replacement | Fresh segment/session and optional clock domain; previous geometry immutable |
| 10 unknown optional extension | Preserve precise semantic JSON and exact optional blob bytes; optional analysis may ignore |
| 11 unsupported required feature | Explicit failure before any numerical exposure, including recovered paths |
| 12 missing END | Read-only verified prefix, unknown tail/end time, no complete flag invented |
| 13 committed corruption | Integrity error for affected bytes, never a routine gap; salvage only explicit |
| 14 independent visible stream | Different native geometry/late receipt retained; common intake chronology is not registration or sensor synchronization |

After **explicit owner acceptance**, R3 must add same-byte Kotlin/Python/native-Windows
vectors: all examples, decimal canonicalization/precision, signed zero/masks/ties,
clock resets/multi-stream late arrivals, all descriptors/view attacks, exact codec
endings, unknown features/minors, metadata/overflow limits, paged seek/large offsets,
checkpoint/recovery damage, timer/Stop races, capacity/refusal/close/readback cases
and immutable source/lineage. The checked examples are not a substituted canonical
corpus or implementation acceptance. Compare uncompressed source bytes exactly,
statistics under defined tolerance, declared/verified integrity separately, and no
GUI/physical calibration conclusions. No Android/Pixel/USB test adds meaningful
verification of this documentation-only grammar, so none is required here.

## 17. Rule provenance, changes from R2 and unresolved approval

| Rule group | Origin | What still needs acceptance/validation |
|---|---|---|
| Full grids, exact Celsius/evidence, validity, no recomputation, truthful gaps, immutable sources, storage choices, coexistence | A | Retained without weakening; new grammar must implement them |
| 40/72 envelope, LE/checked range, CRC/SHA domains, sync-before-ack, blocks/index topology and bounded recovery | E | Release identity/grammar selection; no physical power-loss claim |
| STORED/DEFLATE decoding and 4 MiB HT recommendation | E | Mandatory registry/policy selection, new writer performance |
| Magic/version/flags, canonical precise JSON/context IDs, generic slots/segments, intake timeline, feature names | P | Owner architectural review and future R3/runtime vectors |
| Generic block grouping, metadata/stream limits, four-prefix-byte bound, summaries/time indexes | P | Review size/allocation/index limits with real generic modules |
| ID2 reserved/unassigned Zstd | P | Owner deferral strategy; optional R2 parity is not production support |
| First-entry monotonic seal deadline and five-second checkpoint | P | Owner idle/energy choice; future races/backends/performance |
| Admission/refusal, local canonical support/export scope and lineage | A goals + P exact policy | Explicit local budgets and supported backend tests |

No release magic, generic schema, timer or canonical JSON performance is retrospectively
called R2-tested. The [decision/ambiguity register](LMTR_R1B_OWNER_DECISIONS.md) records
concrete recommendations and alternatives, not hidden TODO meanings in the grammar.
Before freeze: owner accepts or requests changes to the complete review revision,
all companion/prose contradictions are resolved, chosen decisions are incorporated,
and the accepted Git-tracked specification/version is identified. Then—and only
then—authorize R3 and downstream implementation. This draft PR remains unmerged.

### External syntax references

- [RFC8259 JSON](https://www.rfc-editor.org/rfc/rfc8259): underlying JSON syntax;
  this profile adds exact-precision/canonical-writer/structural constraints.
- [RFC1950](https://www.rfc-editor.org/rfc/rfc1950) and
  [RFC1951](https://www.rfc-editor.org/rfc/rfc1951): codec1 wrapper and DEFLATE.
- [RFC3339](https://www.rfc-editor.org/rfc/rfc3339): UTC syntax foundation; the strict
  UTC subset/unknown semantics here remain proposed, not inferred clock accuracy.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
