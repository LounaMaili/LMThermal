# Common radiometric recording — review draft 1

**Status: Review draft, 2026-10-06. Not an accepted contract.**

This document proposes the next Android/Linux/Windows interchange milestone.
Requirements expressed as MUST/SHOULD below are proposed requirements for owner
review, not changes to an existing format. No common recorder, reader, UI or
conformance fixture is implemented by this milestone. Binary framing must be
frozen in a subsequent reviewed specification before implementation.

The [Accepted LMTX v1.0 still contract](LMTX_FORMAT_V1.md) remains unchanged.
Its format ID is `lmthermal-exchange`, extension `.lmtx`, and `kind=still` only.
Existing Android → Linux/Windows still interoperability is complete; see the
[Desktop evidence](https://github.com/LounaMaili/LMThermal-Desktop/blob/main/docs/LMTX_IMPORT.md).

For HT-301 evidence and displays retain:

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## 1. Scope and invariant

A recording MUST preserve the **complete primary native thermal grid for every
recorded measurement frame**, at its original dimensions and pixel coordinates.
Every preservation profile has this guarantee. Selecting a point/ROI MUST NOT
crop, downsample, rotate, mirror, quantize or discard pixels in the measurement
plane. Palette images and screenshots MUST NOT substitute for numerical data.

Source temperature Float32 bytes, validity, requested native samples and source
evidence are lossless. Optional rendered preview/video may be lossy only as an
explicitly separate visual stream. A preview-only recording is permitted, clearly
classified without invented temperatures or a radiometric profile claim.

The schema is not defined by HT-301/raw14/UVC/384×288. HT-301 is the storage and
benchmark case study. The primary grid is fixed within a stream; a geometry or
module replacement starts a new stream/session segment, not a reinterpretation
of previous frames. Future visible-light streams have their own geometry, clocks
and association evidence; coincidence is not an assumed pixel registration.

## 2. Audit of existing work

Audit bases: Android `63544124a538dee04035555b83e5f060e6015dd0`, Desktop
`b0afdf3b42b6f55c2ae3d926f0d8c9df09255746`. Desktop was audited read-only.

| Existing component | Reuse conceptually | Boundary for the common recording |
|---|---|---|
| [LMTX v1 still](LMTX_FORMAT_V1.md) | Owned full matrices, f32le/mask, native coordinates, deterministic statistics, capability vs availability, provenance, extensions, integrity and immutability | Still-only ZIP; no ZIP64; whole-archive limits are inappropriate for long recordings. No v1 schema changes |
| [Android camera modules](CAMERA_MODULE_ARCHITECTURE.md) | Generic geometry/optional measurements, one owner, immutable source data, module-owned evidence | Current `CameraCapabilities` action/preview flags do not declare all recording evidence. Future retention declarations need a distinct seam |
| Android `LmtxCapture`, `CaptureFreeze`, `ExportEvidenceProvider` | Coherent owned snapshots; common writer consumes module-owned descriptors, not an HT cast; repeatable exact bytes | Still preparation and complete private ZIP copy are not a per-frame recorder API. HT still currently stores both native and transport; leave v1 unchanged |
| [Android still export](ANDROID_LMTX_EXPORT.md) | Publication verification, no stale readings, independent frozen artifact, privacy | SAF still-copy success does not establish long-recording append durability or no-copy publication |
| [Android camera lifetime](ANDROID_CAMERA_LIFETIME.md) | Same owner across rotation/tracked destination transaction, explicit Close, bounded awaited release | Ordinary Home/screen-off/locale/detach/error still release. Initial recording should seal/interrupt accordingly; background recording needs its own milestone |
| [Desktop recording v1](https://github.com/LounaMaili/LMThermal-Desktop/blob/main/docs/RADIOMETRIC_RECORDING_FORMAT.md), `radiometric_recorder.py`, `radiometric_recording.py` | Bounded immutable handoff, off-thread compression, explicit overload/gaps, full matrix retention, commit-before-counting | HT-specific 384×288 raw14 + f32, no complete transport; directory/CSV/NPZ; 16 frames/chunk, four-frame queue. Per-frame settings/scalars, floating times and estimated UTC at missed deadlines are not the new time model |
| Desktop publication | Temporary chunk flush/fsync, manifest commit after chunk publication | Hard-link chunk publication + manifest replacement rely on local filesystem semantics. Directory durability is not a universal power-loss guarantee. Do not port to SAF |
| [Desktop playback](https://github.com/LounaMaili/LMThermal-Desktop/blob/main/docs/RADIOMETRIC_PLAYBACK.md), `radiometric_playback.py`, `playback_worker.py` | Stored Celsius authority, blank gaps, bounded two-chunk cache, elapsed-time playback, current-frame point/ROI and presentation | Current loader reads full manifest/timeline and validates all chunks before opening. New reader needs paged indexes/lazy integrity for multi-hour files; no HT-only shape or mandatory raw14 |
| Desktop still/offline/LMTX import | Optional-temperature offline model, exact import, valid-only ROI, palette controls independent of source | Keep strict legacy and accepted LMTX loaders; add a future sequence adapter, not validator relaxation |

The genuine historical 5/10/25 Hz long recordings formerly in `/tmp` are no
longer available. Their aggregate reports survive, but cannot support compression
claims. Existing playback demos repeat sanitized fixtures with simulated times
and gaps. The compression study instead uses 75 room and 23 hand **live raw
frames** that survive separately; see [study and limitations](RECORDING_COMPRESSION_STUDY.md).

## 3. Format identity and relationship to stills

| Option | Assessment |
|---|---|
| LMTX v2 | Possible major break, but couples still and recording evolution/container migration; unnecessary to retire a successful v1 still contract |
| Add `kind=recording` to LMTX v1 | Rejected: changes the accepted still-only contract and risks existing reader assumptions |
| Shared exchange family, separate recording ID and container generation | **Recommended**: reuse numerical/evidence semantics while evolving recording independently |

Provisional identity: **LMThermal Recording**, `.lmtr`,
`format=lmthermal-recording`, `schema_version={major:1,minor:0}`,
`kind=recording`, separate container generation 1 and distinct non-ZIP magic.
These names/version are proposals, not a published stable format.

Existing LMTX v1 readers MUST reject this unsupported container/format without
interpreting a sequence as a still. Published `.lmtx` v1 stills and historical
Desktop formats retain long-term read support. A sequence minor version may add
optional fields, not alter coordinates, numerical encodings, validity or time
meaning. Incompatible changes need a new major/container generation as applicable.
Unknown required features/codecs fail explicitly before numerical interpretation;
unknown bounded optional JSON is retained semantically with sufficient precision,
not necessarily its number spelling. Uninterpreted retained binaries remain exact.

The header declares the feature/codec envelope. Each independently seekable chunk
repeats its required interpretation features with its context. Readers validate
both scopes before exposing that chunk; a lazy seek or recovery path cannot avoid
the required-feature check. Stable errors should distinguish `unsupported_format`,
`unsupported_major`, `unsupported_required_feature`, `invalid_container`,
`invalid_metadata`, `invalid_payload`, `integrity_mismatch`, `resource_limit` and
`storage_failure`; recovery status is separate from those errors.

## 4. Evidence and capability model

| Role | Common meaning | Availability |
|---|---|---|
| `temperature` | Authoritative complete primary numerical grid; current proposal f32le Celsius | Optional, only a current supported measurement |
| `validity` | Explicit per-pixel availability for that temperature plane | Canonical u8 0/1; absent means every pixel valid |
| `native_samples` | Module-native sensor/SDK samples, with encoding, dtype, shape, byte order and module namespace | Optional; not universally raw14 or necessarily reconstructible from transport |
| `acquisition` | Exact lower-level received device/frame/packet blob identified by the module | Optional; may be opaque, packet-assembled or unavailable |
| `context` | Calibration/settings/environment/algorithm/provenance needed to interpret the recorded observation | Known fields plus bounded namespaced extensions; partial/unknown fields are explicit |
| `preview` / future `visible` | Visual evidence with independent descriptors and geometry | Optional; never numerical authority |

A future module-owned recording-evidence snapshot should expose immutable owned
measurement bytes and the **same observation's** optional evidence/context. The
recorder MUST NOT copy unrelated latest USB data, call thermometry again, or
extract module bytes inside shared UI. Copy/reference lifetime is explicit: an
acquisition buffer recycled by the camera is not a safe asynchronous reference.
Reuse the still evidence abstraction where useful, without looping still exports.

Modules declare role support `yes/no/partial/unknown`, encodings, relationship
types, geometry, estimated/max bytes per observation and interpretation context
availability. Claims are stable facts for a stream/segment, not guesses from
model names. Each frame separately describes **actual** availability and an absent
reason. Support is not proof that a specific frame was obtained or retained.
Module/calibration changes are explicit stream/context events.

## 5. User preservation policy

| Provisional UI choice | Retained evidence for applicable frames |
|---|---|
| **Analysis** (recommended default) | Complete temperature, validity, truthful timing, provenance, available interpretation context, gaps and optional analysis/presentation; native/acquisition intentionally omitted |
| **Native** | Analysis + actual module-native samples, with their encoding and relationships |
| **Full / Research** | Analysis + maximum available native/acquisition/context/extensions and useful bounded uninterpreted source evidence |

All applicable interpretation settings available to the module are included even
in Analysis; physical source bytes and extra uninterpreted evidence distinguish
Full. Unknown calibration is not fabricated. Native/Full may also retain evidence
on frames whose temperature is unavailable; availability still remains explicit.

Expose only choices adding meaningful available evidence. Camera A with native
and transport offers all three. Camera B with native but no extra acquisition or
research evidence offers Analysis/Native, not a misleading Full duplicate.
Camera C with temperature only offers Analysis. A module with no native but useful
acquisition can offer Analysis/Full. Full is the module's actual maximum, not a
promise that every camera can export transport. The UI explains omitted/unavailable
levels. A preview-only source offers a distinct visual recording choice.

Record requested policy, resolved role inventory, module support and per-frame
retention. Descriptors, not the profile label, are authoritative. A temporarily
missing requested role has an explicit per-frame reason; there is no silent
profile downgrade. A persistent capability/rate/profile change starts a declared
segment after user acknowledgement; no overwrite of prior policy. Bounded writer
overload becomes a dropped slot, not an automatic cheaper profile.

Recommend simple levels initially, with storage estimates before Start. Advanced
manual role selection can follow later; it MUST NOT deselect/crop the full
temperature plane for a recorded measurement or remove required validity/context.

## 6. Logical content model

This table is the proposed schema structure, not a finalized wire JSON Schema.

| Object | Fields/meaning |
|---|---|
| Recording header | UUID; format/schema/container versions; required features; producer ID/version; creation clock status; privacy choices; requested/resolved preservation policy; stream declarations; supported resource envelope |
| Stream | ID, role, module/model and optional consented device identity; native width/height/coordinate convention; content class; capabilities/encodings; source sequence domain; clock domains; nominal rate if known; recording sampling policy |
| Session/segment | ID; module/settings/rate/policy change; explicit start/stop reason; timeline anchor; clock-domain UUID; observed first/last source sequence where known |
| Frame/slot | Monotonic slot ID, segment/stream ID, requested schedule if configured; nullable device sequence and host observation sequence; dense recorded-frame ordinal only when stored; receipt/acquisition/relative times with clock IDs; frame/measurement status and reason; context ID; payload descriptors; optional module summaries |
| Payload descriptor | ID; role; stored block reference + offset/length or a bounded view; codec; uncompressed size; logical-byte SHA-256; encoding ID; dtype/endian/shape/layout/units; module namespace and relationship IDs |
| Context state/event | Immutable state ID; complete known calibration/settings/environment and algorithm identity with unknown/partial reasons; module extension versions; effective slot/observation; frame context reference |
| Gap event | Slot/range, reason, observed boundaries, source sequence evidence if known; expected count only where defensible; no replacement payload or invented acquisition time |
| Presentation events | Effective slot/relative time, stream ID, stable palette ID, auto/locked range, visual transform; not applied to source bytes |
| Analysis events | Stable track IDs; native point/half-open ROI; introduction/move/resize/clear intervals; annotations; optional derived statistics with algorithm/version/source references |
| Final/checkpoint summary | Status; committed counts by stream/role; gaps/drop counts; observed span; completion/stop/error reason; optional end UTC with clock quality; paged index root and integrity binding |

The primary temperature layout is `[height,width]`, row-major x-fastest, pixel
centres `(x,y)` in native coordinates. Bounds and exact byte lengths are verified
before allocation. No HT centre is mandatory. Device-provided summaries remain
separate from matrix extrema/literal centre. For HT-301, trailer centre is a
separate index observation and MUST NOT be relabeled pixel `(192,144)`.

### Canonical numerical data

Temperature is IEEE-754 binary32 little-endian Celsius. Valid pixels MUST be
finite and preserve their original bits (including valid negative zero). A present
mask has one u8 byte per pixel, exactly 0 or 1. Invalid pixels serialize as positive
zero; absence of the mask means all pixels valid. No NaN/sentinel substitutes for
validity. Zero-valid frames retain the full plane+mask but no fabricated extrema.
Normal playback MUST use stored temperatures without re-running vendor thermometry.

Native samples identify `encoding_id`, module namespace/version, dtype, endian,
shape and layout. Examples include raw14/u16le, TLinear/Y16 or vendor samples;
their numerical units and valid bit ranges belong to the module's encoding.
Opaque acquisition has an exact length/hash and module encoding, not a guessed
thermal matrix dtype. Separate distinct native/acquisition payloads remain allowed.

Deterministic point/ROI statistics follow the still semantics: valid pixels only,
row-major iteration, source Float32 extrema, first row-major tie, Float64 sum/mean,
valid/total counts and absent min/max/mean when valid count is zero. Cached
statistics are non-authoritative and include algorithm/source references.

### Restricted payload views — recommended

Permit **one contiguous exact byte view** of a materialized payload from the
**same frame and chunk**, not a view of another view. Each descriptor names a
physical parent, offset, length, exact expected shape/dtype/endian and logical-byte
hash. Enforce `offset >= 0`, checked `offset+length <= parent.length`, shape-byte
agreement and dtype alignment. Zero-copy in memory is optional; exact bytes are not.

No nested slices, strides, discontiguous packets, byte swaps, decompression-as-view,
cross-frame/cross-chunk pointers or arithmetic transforms. Decoding a compressed
physical block precedes slicing; offsets are in its uncompressed frame payload.
Temperature and mask remain canonical materialized roles. Views are a declared
required feature; consumers without it return `unsupported_required_feature`.

HT-301 Full can physically store Celsius + 224256-byte acquisition and expose
`native_samples` as offset 0, length 221184, shape `[288,384]`, dtype `u16le`,
encoding `org.lmthermal.ht301.raw14`. Logical native hash validates selected bytes;
the parent hash covers the whole transport. A corrupt parent invalidates its
views too, with an integrity error, not a quietly unavailable reading. Extraction
returns exact original samples; no high-bit masking. Other modules may have no
such relationship and retain independent payloads. Still LMTX v1 is unchanged.

## 7. Time, ordering and loss

Use checked nonnegative integers in `0..2^63-1` for sequence numbers, nanoseconds,
counts, lengths and offsets. JSON stores those large integers as canonical decimal
strings; binary lengths/offsets are little-endian u64 with values above signed
Long range rejected. Kotlin Long/Python int then agree without double precision
loss. Small dimensions/version fields can be JSON integers under their limits.

Keep four separate concepts:

1. Camera/acquisition timestamp and sequence, only if the module supplies them,
   with units, clock domain and quality. Unknown remains absent with a reason.
2. Host monotonic receipt nanoseconds with domain UUID. Never compare domains
   across reboot/recording restart as if one continuous device clock.
3. Recording-relative nanoseconds derived from a recorded monotonic anchor in
   that domain; scheduled deadlines are a separate field, not receipt times.
4. Creation/finalization/optional acquisition UTC with known/unreliable/unknown
   status, source and uncertainty when available. Export time is not acquisition
   UTC; wall clock corrections do not reorder playback.

Record a host observation counter separately from vendor sequence. A sampler
slot counter counts requested observations; recorded ordinal counts retained
frames. Default acquisition-following or a requested lower-rate sampler is
declared explicitly. Nominal 25 FPS does not prove a missing camera frame or
guarantee 25 valid measurements/second. Missed scheduled slots can have exact
schedule time, but no invented observed/device time. Unknown-count interruptions
record observed boundaries with unknown count rather than extrapolated frames.

Required distinct outcomes:

| Outcome/reason | Representation and playback |
|---|---|
| Valid stored measurement | Complete temperature/mask, current context and any retained evidence |
| Measurement unavailable | Observed frame, explicit state/reason (display, unsupported settings, transient, invalid or held); optional actual native/acquisition/preview; no Celsius |
| Acquisition drop | Known source sequence gap or explicit module signal; count/timing only as supported by evidence |
| Writer drop / missed sampler deadline | Explicit requested slots with reason; no prior-frame matrix substitution; counter separate from acquisition loss |
| Session interruption | Segment-ending event with detach/background/error etc.; unknown unobserved duration/count remains unknown |
| Storage/write failure | Stop/error event if writable; otherwise recoverable committed prefix with incomplete tail; do not claim known termination time |

Held frames follow module validity/liveness policy; identical pixels alone are
not universally a camera failure. HT session readiness/rejection remains the
validated existing policy. Playback never fills a gap with the preceding matrix.
Missing/corrupt **promised** payload is an integrity error, not a normal drop.

## 8. Settings, analysis and presentation timelines

Settings are immutable complete states with IDs and effective observation/slot:
state A at 0, B at 812, C at 4201. Every measurement references one unambiguous
state. Include module/algorithm versions, calibration availability and host state
needed for provenance. Never deduplicate approximately equal floats or discard
changing FPA/trailer calibration inputs. Dynamic per-frame observations are
separate from stable configuration, or create a new exact state when appropriate.

Deduplicate states/events within a chunk, and reuse IDs across chunks. Each chunk
includes bounded complete snapshots of every context needed to interpret its
frames, even if a previous chunk had that state. This small repetition buys
self-contained seeking/recovery without a long pointer chain or losing meaning
when an earlier chunk is corrupt. Semantic JSON preservation uses sufficient
numeric precision; unknown values are not rounded through Float64 accidentally.

Optional analysis supports multiple ROI/points, global intervals, tracks created
at frame N, moves/resizes/clears and annotations. Geometry is native and half-open
for rectangles. No per-frame ROI-statistics requirement; Desktop can deterministically
compute new regions and temperature/time graphs from complete stored matrices.
Tracking algorithms are derived analysis with their identity/confidence, not new
measurement facts. Missing frames yield missing graph samples.

Presentation state events encode palette/range/visual transform changes once at
their effective slot. They do not change temperature, native bytes, ROI coordinates
or statistics. Auto range is reproducible from valid source pixels when requested.

Finalized source recordings are immutable. Later Desktop analyses use an independent
analysis project referencing recording UUID and committed integrity root/file hash;
its exact sidecar format is future scope. Do not edit the original or force a full
multi-GB copy merely to add a ROI. A recalculated sequence is a **new** derived
recording with new UUID, parent identity/hash, source intervals and algorithm/settings
lineage, retaining the parent as authority for originally stored temperatures.
Extracted `.lmtx` stills must follow Accepted v1 still/derivative rules; never imply
transport preservation when the chosen recording profile omitted it.

## 9. HT-301 byte budgets and compression

Exact case-study grid: 110592 pixels; Float32 Celsius 442368 bytes; native u16
221184; acquisition 224256; extra transport 3072 bytes. At nominal 25 FPS:

| Physically retained bytes | Bytes/frame | MB/s | MB/min = 1 min | GB/10 min | GB/hour = 1 h |
|---|---:|---:|---:|---:|---:|
| Celsius | 442368 | 11.059200 | 663.552 | 6.635520 | 39.813120 |
| Native u16 | 221184 | 5.529600 | 331.776 | 3.317760 | 19.906560 |
| Acquisition | 224256 | 5.606400 | 336.384 | 3.363840 | 20.183040 |
| Celsius + native | 663552 | 16.588800 | 995.328 | 9.953280 | 59.719680 |
| Celsius + acquisition (native view) | 666624 | 16.665600 | 999.936 | 9.999360 | 59.996160 |
| Celsius + native + acquisition (duplicated) | 887808 | 22.195200 | 1331.712 | 13.317120 | 79.902720 |

All MB/GB above are **decimal** (10^6/10^9 bytes). One hour Celsius is 37.078857
GiB; Celsius+native 55.618286 GiB; Celsius+acquisition 55.875778 GiB; all three
74.415207 GiB (GiB = 2^30). Rates exclude JSON/index/hashes, previews and masks,
assume every nominal frame retained, and are uncompressed, not promised throughput.
An explicit full u8 mask adds 110592 bytes/frame, 2.7648 MB/s, 9.95328 GB/hour;
all-valid frames may omit it with explicit all-valid semantics.

Native-only/acquisition-only rows isolate evidence storage costs; they are not
recommended radiometric profiles that discard an available temperature plane.

Transport is 1.388889% larger than native alone; extra bytes are 1.369863% of
transport. HT Full using the native view saves 221184 bytes/frame, **19.90656
GB/hour**, compared with physical duplication (24.913495% of all-three bytes).
Compared with Celsius+native it costs just 3072 bytes/frame: 0.462963% extra,
0.27648 GB/hour. These ratios do not apply to another camera's packet protocol.

[Measured compression results](RECORDING_COMPRESSION_STUDY.md) and the aggregate
[machine-readable report](analysis/recording-compression-20261006.json) cover
STORED, DEFLATE levels 1/6 and Zstandard levels 1/3 on full roles and 1/8/25/50
frame groups. No lossy preprocessing, sample quantization or temporal extrapolation
was used. Short scenes and offline-produced reference Celsius planes limit the
conclusions; Android sustained power/CPU/provider performance is **not measured**.

## 10. Container comparison

| Candidate | Streaming/SAF and multi-GB | Recovery/indexing/memory | Portability/inspectability | Decision |
|---|---|---|---|---|
| Chunked ZIP64 | Sequential member writing possible; ZIP64 required for size; SAF still has independent durability limitations | Standard central directory/final close is not a commit journal. Repeated directories or local-header recovery need custom rules; member count/index memory needs bounds | Familiar unzip tools, Java/Python support; seek usually uses finalized directory. Zstd ZIP support varies by runtime | Viable portable **export**, not recommended acquisition authority |
| Directory + bounded chunk files + journal; later pack | Excellent local append/fsync; many SAF documents/provider operations are expensive/non-atomic | Isolates chunks, explicit journal needed; portable final packing often creates a second full copy; large filename/index counts | Human-inspectable and close to Desktop legacy; Windows local rules workable, provider directory rules differ | Useful prototype/spool, not mandatory final interchange |
| SQLite with blobs or external binary chunks | Robust local transactional metadata; normal database random I/O/locking does not map to arbitrary SAF streams | Paged indexing strong; WAL/journal/checkpoint and external-file atomicity must be managed; not simply one finalized blob stream | SQLite available Android/Python; inspection good; compression still explicit; WAL companions complicate copying | Good optional **private catalogue/cache**, not common recording container |
| Custom append-only chunked binary file | One forward-written stream with 64-bit checked offsets; no rewrite/rename/seek required by writer | Independently committed bounded chunks; per-chunk indices + paged rolling/final roots; incomplete tail recoverable | Kotlin/Python plus standard hash/DEFLATE; requires a small inspection tool and exact cross-platform framing tests | **Recommended**, subject to reviewed framing/recovery spike |
| Matroska/media container | Excellent visual streams/timestamps | Private numerical tracks, calibration, exact masks/views and journal integrity would need substantial custom conventions | Extra media/native dependency; ordinary video tools cannot validate radiometry | Later rendered video export, not primary numerical storage |

ZIP64/unseekable writing are documented by [Python ZIP documentation](https://docs.python.org/3/library/zipfile.html).
SQLite WAL uses additional files and host-local shared coordination; see
[SQLite WAL](https://sqlite.org/wal.html). These facts do not imply provider-level
durability. The recommendation is a design judgment based on the requirements.

## 11. Proposed container, codecs and chunk policy

Separate outer container generation from JSON schema. Each record has a fixed,
bounded header identifying type/version/ordinal/total length, a bounded body,
and fixed footer repeating length/ordinal with checksum and commit marker.
Typed records: HEADER, CHUNK, INDEX_PAGE, CHECKPOINT, END. Check sums reject torn
framing; SHA-256 binds metadata and stored payload bytes, plus logical payload
hashes establish exact decoded data. These checks detect damage, not authenticity.
The byte constants/header widths/checksum coverage/magic escape policy need the
framing annex and adversarial fixtures before stable publication.

CHUNK metadata holds frame-slot descriptors, complete required contexts, timelines
and a local index; role-major independent binary blocks contain consecutive full
frame bytes. Variable-length acquisition uses checked per-frame offset/lengths.
Masks are separate. Physical blocks are each compressed independently; a view
does not add another block. Codec flags/descriptors and required features are
resolved before decoding. There is no shared dictionary dependency across chunks.

Baseline supported codecs: STORED and zlib-wrapped RFC1951 DEFLATE (wrapper
RFC1950 stated explicitly). Compression may choose STORED when compressed bytes
would be larger. Initial portable recommendation is DEFLATE level 1; Zstd level 3
is a promising optional performance candidate, gated on Android/Windows dependency,
decoder-window, license and sustained-device validation. If accepted, Zstd is a
separately required feature, not silently substituted for DEFLATE. Its reference
C implementation supports Android NDK integration and BSD licensing as a technically
reasonable route; no application dependency is added now. See [Zstandard](https://github.com/facebook/zstd).

Seal a chunk at the first of **1 second of observed timeline**, **32 frame/slot
entries**, or **16 MiB aggregate uncompressed bytes** (including metadata). Flush
earlier on context/resource limits or Stop. Gap runs may compact known scheduled
ranges but not invented observations. Low-rate recording therefore commits each
second rather than retaining many seconds of uncommitted evidence. A single large
frame may exceed the preferred byte target but must fit the hard 64 MiB chunk/frame
budgets; it forms a singleton chunk. These are draft bounds, not promised Android
heap usage. Very high-rate modules may make subsecond chunks.

At HT 25 FPS, one second is 10.546875 MiB Analysis, 15.820313 MiB Native,
15.893555 MiB Full with a native view, before metadata. Full with masks reaches
18.530273 MiB and will seal earlier. Physical duplicate Full would be 21.166992
MiB; a 16 MiB cap would force smaller chunks. Several seconds increase seek latency,
memory and lost-tail/corruption radius; the study does not justify that as default.

Compression is off acquisition/UI threads. Writer owns bounded chunks; queued
frames are bounded **by bytes**, not just count. Suggested HT starting budget:
two 16 MiB chunk buffers plus a 4 MiB handoff and codec scratch, aiming below
64 MiB incremental recorder memory. Avoid extra stack/copy duplication; benchmark
peak heap explicitly. Larger generic singleton chunks must negotiate a local
resource budget or refuse the profile before Start. Decoder holds at most two
normal chunks, decoding only needed roles; limits alone are not memory guarantees.

## 12. Indexing, seeking and integrity

Use a **hybrid paged index**:

- Per-chunk bounded slot/time/role-offset index makes a chunk independently usable.
- Append immutable index pages with up to 256 entries per page; a paged tree maps
  stream/segment and slot/time ranges to chunk offsets and integrity commitments.
  Update a bounded path/root by appending pages, never replacing old pages.
- CHECKPOINT at most every 32 chunks records the current tree root, committed
  counts, last chunk and previous checkpoint pointer/hash. END references the
  current root and complete stop summary; no whole-recording rewrite or monolithic
  array of every frame. Finalization costs pending chunk + bounded tree updates.
- Every record footer permits checked backward traversal. Normal open reads END
  and bounded pages lazily. Binary search loads O(tree depth) pages and at most
  one selected chunk; no full payload integrity scan at startup. Verify its
  stored/logical hashes before exposing a numerical frame. Cache pages/chunks
  under a byte budget, not total duration.

Offsets are backwards references to committed records within the same recording;
check recording UUID, record type, ordinal, bounds, cycles and hashes. Index leaf
and root ordering/ranges must agree with actual chunk metadata. A forged index
cannot authorize another file/URI or skip unsupported required features.
Missing END, a torn final index or checkpoint can fall back to the preceding valid
checkpoint and bounded record traversal of the suffix. A bounded scan can locate
the previous footer after a torn record of at most the hard record-size limit;
candidate magic alone is never sufficient evidence of a valid record.

Rebuilding a lost/corrupt **index** from valid chunk metadata may require a one-time
full metadata scan, cancellable and surfaced as recovery. Save a disposable local
side index bound to source identity/size/integrity; never modify the source or trust
it as authority. No promise of instant recovery from arbitrary interior corruption.
A damaged committed chunk is an integrity failure with localized affected range;
an explicit salvage mode may expose verified other chunks, marked incomplete/
recovered, but must not silently present the original as intact. Unsupported
required semantics cannot be bypassed by recovery. Ordinary playback does not
repeatedly scan tens of GB; deliberate whole-file verification is separate.

## 13. Commit and recovery model

| State | Definition |
|---|---|
| Pending | Owned observations not yet protected by a complete committed chunk; UI counters distinguish them |
| Committed (structural) | Entire chunk, descriptors/hashes and commit footer are present and validated; reader may verify logical bytes on demand |
| Durable commit | Structural commit plus successful durability barrier under the chosen local/provider storage guarantee; UI must distinguish acknowledged stream writes from durable local writes |
| Incomplete chunk | Torn/missing footer, short block or incomplete metadata; never partially exposed as a valid measurement |
| Completed recording | Valid END + committed prefix/root/summary; every advertised stored frame resolves; normal Stop or explicitly recorded graceful interruption reason |
| Recovered recording | Verified committed prefix/segments opened without a valid final END, with recovery status, last verified boundary and unknown tail count/end time unless evidenced |

Local durable writer: write complete body, flush and synchronize it, append commit
footer, flush/synchronize again, then count durable frames. This ordering limits
torn-write ambiguity; actual power-loss behavior depends on storage honouring
barriers. Failure at a barrier is not success. A newly created local file needs
the platform's directory/file-entry persistence handling as well. No SAF rename,
hard link or atomic replacement assumption is made.

| Event | Required future behavior |
|---|---|
| User Stop | Stop accepting, drain bounded handoff, seal partial chunk, append index/root/END and synchronize; report completed only on success |
| USB detach, ordinary Home/screen-off, locale release or recoverable module interruption | Stop recording and seal what can be sealed; explicit interruption reason; no automatic camera initialization/resume. A new acquisition uses a new segment/recording according to the approved policy |
| Writer error/storage full | Stop intake promptly; append failure summary only if safe space remains. Otherwise preserve prior commits and report incomplete; failed pending samples not counted retained |
| Crash/process kill/battery loss | No trustworthy Stop/end timestamp. Open only previously verified commits; ignore uncommitted tail and report unknown loss after last commit |
| Torn final chunk/index/END | Recover previous commit/root; never infer previous-image replacements; index loss is distinct from payload damage |

Do not resume writing a recovered source in place in the initial implementation.
Recovery is read-only; optional repaired portable copy/derived continuation uses
new identity and lineage. Previously committed chunks can be analysed without
rewriting the original multi-GB file or finalizing it in place. Exact loss reasons
for a killed process cannot be guaranteed if no event reached durable storage.

## 14. Android storage and publication

SAF identifies providers, not POSIX filesystems. Exclusive `w` descriptors can be
pipes; `rw` implies seeking, but modes/provider behavior differ. Persisted URI
grants do not establish durable byte writes or known free space. See
[ContentResolver](https://developer.android.com/reference/android/content/ContentResolver)
and [SAF access](https://developer.android.com/training/data-storage/shared/documents-files).

| Strategy | Benefit | Limitation / recommendation |
|---|---|---|
| App-private durable recording, then copy to chosen provider | Strongest controllable local commit/recovery, reliable free-space query | Copy may need a second tens-of-GB file/time. Offer optional export/backup, not the only way to use the result |
| App-private canonical recording exposed read-only through an app DocumentsProvider/share URI | **No mandatory second full copy** to publish/access finalized bytes; private catalogue/recovery controlled | URI access is app-owned, not an independent backup. App uninstall/data deletion removes it; recipients must read before access expiry. A future provider needs explicit permission/lifetime tests |
| Direct recording to tested local SAF destination | One physical final file; forward writer needs no container seek/rename | Preflight capacity/access/append/reopen/seek/sync and interruption tests; provider durability may be weaker/unknown. Seek helps later reads but is not proof of crash durability |
| Hybrid bounded private pending-chunk spool + direct destination | Smooths stalls, bounds duplicate storage to a few chunks; preserves last not-yet-acknowledged data | Cannot restore *all* previous commits if destination itself loses bytes. Keep journal/integrity receipts; disclose storage assurance. It is not a substitute for durable primary storage |
| Remote/pipe-only provider | Sequential export possible | No guaranteed random read/reopen/durable commits/free-space; do not offer as the initial durable field-recording destination. Export a finalized local recording separately |

Recommend initial **private durable canonical file + read-only no-copy access**,
with optional independent-copy export. Next storage milestone can enable direct
local-provider recording only after capability/recovery validation. The container
supports a forward stream even when a particular storage backend cannot guarantee
recovery. Completed source access is separate from provider-copy completion; copying
or failure never alters the source. Do not announce successful durable publication
until final close/barrier and integrity/readback appropriate to the backend succeed.
This differs deliberately from still export's mandatory private archive preparation
and subsequent SAF copy, without changing that implemented still behavior.

Rotation/tracked UI transactions should retain the same owner and pending recording
under the current lifecycle architecture; not reopen or resend camera controls.
Initial recording remains foreground-scoped. Background acquisition/services need
separate product/Android lifecycle work, not a container requirement.

### Capacity estimates

Module descriptors supply typical/maximum bytes per role/frame plus nominal or
observed rate. Sum **physical** bytes after exact view deduplication; include masks,
variable acquisition/context, index overhead, codec worst-case expansion and pending
spool. Calculate decimal MB/min and remaining seconds as usable capacity divided
by estimated byte rate. Display rate assumption, raw worst case and rolling
measured compressed estimate separately. Compression ratios are scene-dependent;
do not promise the benchmark ratio or round unknown provider free space into zero.

Reserve at least two maximum pending chunks plus bounded index/END/error metadata
and an OS margin appropriate to the backend. Preflight insufficient **known** space
blocks Start; ongoing reserve breach triggers graceful stop before exhaustion when
possible. Unknown free space is labelled and requires an explicit warning rather
than fabricated remaining time. Optional intended-duration checks can use the raw
upper estimate. Storage-full failure remains possible despite estimates.

## 15. Proposed resource/security envelope

These are concrete review-draft ceilings. Producers declare smaller local limits;
readers can refuse a supported file with `resource_limit` rather than overallocate.
Large recordings have no still-like 256 MiB whole-file ceiling.

| Item | Proposed ceiling/validation |
|---|---|
| File offsets/lengths/counters | `2^63-1`; checked sums/products/ranges and actual file size before seek/allocation |
| Primary frame geometry | Positive dimensions each <=16384; product <=4194304 pixels (16 MiB f32 temperature); exact shape byte count |
| Native samples | Supported dtype, <=16 channels; exact shape/length; aggregate frame payload <=64 MiB |
| Physical payload block / chunk | <=64 MiB aggregate uncompressed chunk including metadata; <=65 MiB stored record including framing/expansion; preferred target 16 MiB |
| Metadata | <=1 MiB UTF-8 per header/chunk/state set; JSON depth <=32; <=65536 total values; duplicate keys/nonfinite numbers rejected |
| Index page / checkpoint / END | <=512 KiB each; <=256 entries/fanout, tree depth <=8; no full-index allocation; checked nonoverlapping ranges and backwards references |
| Chunk frame/slot entries | <=1024 hard limit; preferred <=32; bounded compact gap ranges use checked counters |
| Stream/analysis tracks | <=16 streams (one primary thermal per segment initially), <=64 analysis tracks, <=64 presentation tracks; events flush under metadata limit |
| Payload descriptors/contexts/extensions | <=64 descriptors per frame; <=64 active context states per chunk; <=32 extension namespaces, <=256 KiB combined extension JSON per chunk (within metadata ceiling) |
| IDs/text/paths | IDs <=256 UTF-8 bytes, free text <=16384 bytes; no filesystem/URI lookup from payload IDs. Future auxiliary paths <=240 bytes with still-compatible traversal/reserved-name protections |
| Inflate | Output capped by verified expected length and hard block/chunk budget; reject short/trailing/concatenated/unconsumed data where not declared. DEFLATE 32 KiB window; optional Zstd window <=8 MiB and no external dictionaries |

Limits compose: many individually legal payloads MUST still fit frame/chunk/metadata
budgets. Unknown optional binaries count towards physical budgets. Codec bombs,
wraparound, negative lengths, reference cycles, aliases, contradictory masks,
unsupported required extensions and malicious sparse indexes fail explicitly.
No arbitrary extraction paths, external URLs, executable metadata, pickle or
codec-driven unbounded allocation. JSON numeric precision is bounded by text
limits but cannot be silently reduced. Privacy defaults omit host paths, USB
serial, GPS, owner identity and network/ADB details; consented fields are declared.
Source imagery itself can be sensitive; Full is additional evidence, not anonymization.
Integrity is neither authentication nor independent physical calibration.

## 16. Desktop and historical coexistence

Future `OfflineSequence` adapters normalize independently validated inputs into
geometry, immutable frame availability, exact temperature/mask, optional native/
acquisition/context, integer timeline and provenance. Common playback can then
scrub/step, inspect points, create **new** ROI, track regions, graph min/max/mean,
rerender palettes/ranges, export selected stills, later render visual video and
compare recordings. Comparison needs explicit time/geometry/registration alignment;
different source clocks/scenes are not presumed aligned.

Do not build the common parser by reusing NumPy NPZ serialization in Android.
Keep `.lmthermal` directory `lmthermal-radiometric-recording` integer v1 and
`lmthermal-radiometric-capture` integer v1 readable under existing validators.
Do not relabel, rewrite or auto-convert originals. Explicit future conversion
preserves actual source capabilities: legacy recording has no full transport;
estimated historical UTC is tagged as such rather than upgraded to acquisition
truth. Existing still `.lmtx` loader remains independently versioned and strict.

## 17. Shared future conformance corpus

Design only; **no sequence fixture bytes are introduced here**. Freeze one corpus
in Git after the wire contract is accepted; Android producer and Linux/Windows
reader use the same bytes, hashes and expected JSON. Synthetic data is clearly
labelled; sanitized real evidence has consent/provenance and preserved spatial
structure stated explicitly. Keep resource-stress generation deterministic and
streamed rather than committing multi-GB blobs.

| Case | Required assertions |
|---|---|
| Temperature-only | Complete grids, exact f32 bits, absent native/acquisition, meaningful Analysis profile |
| Native without acquisition | Generic non-raw14 encoding/dtype/geometry, Native profile; no invented transport |
| Maximal independent evidence | Distinct temperature/native/acquisition exact bytes; support vs actual availability |
| HT native view | Exact bounded same-frame slice/hash; dedup count; no nested/cross-chunk view; reject overflow/out-of-bounds/parent damage |
| Alternate geometry | No 384×288 assumptions, secondary visible geometry independent |
| Partial/zero validity | 0/1 mask, invalid +0, valid -0, valid-only statistics/ties; no NaN validity |
| Calibration/settings change | Complete states from frame N, per-frame dynamic observations, exact precision and chunk-local context closure |
| Acquisition/writer drops, unavailable/held/transient | Truthful slot/sequence/time domains, blank measurements, no previous matrix substitution |
| Multi-chunk and presentation/analysis events | New/moved/multiple ROI/points after capture; palette independence; contexts resolve on random seek |
| Interrupted last chunk | No footer/partial payload/torn index/END; previous commits readable, unknown tail/end time retained |
| Recovered recording | Prefix/root fallback, explicit recovery state, immutable source, deterministic reported verified ranges |
| Unknown optional JSON/binary | Precise semantic JSON preservation and exact binary hashes; roundtrip derivative semantics |
| Unsupported required feature/major/codec | Stable explicit rejection, including when reached through recovered suffix |
| Corrupt committed chunk/hash/index | Integrity error distinct from gap; explicit salvage only; broken final index alone recoverable |
| Preview-only | No Celsius/thermal profile claim; optional rendered content never substituted as measurements |
| Synthetic large | >4 GiB offsets, many index pages/chunks, sparse seeks, bounded heap, cancellation, no entire-index/image allocation |
| Storage/lifecycle failure matrix | Process kill/torn writes/full storage/provider errors; Stop/drain, single owner across rotation, no automatic initialization |

Conformance gates: byte-for-byte temperature/mask/native/acquisition parity, view
extraction parity, deterministic statistics, Linux/Windows source immutability,
Android allocation/backpressure, dependency/codec interoperability, index repair,
power-loss fault simulation and real supported storage backend interruption tests.
A fixture proves format semantics, not camera accuracy or all-provider durability.

## 18. Decisions, roadmap and proposed issues

Product decisions still requiring owner review:

| Decision | Recommendation |
|---|---|
| Default preservation | Analysis: normal full-frame offline analysis with lower storage; remember the last explicit choice per module and show the current level before Start |
| Manual payload selection | Defer initially; later advanced option constrained by required measurement/validity/context invariants |
| Maximum-level wording | Explicit **Full / Research**, explaining actual retained roles and no improved physical-accuracy claim |
| Space/warning gate | Block demonstrably insufficient known capacity; show raw upper estimate + observed estimate, warn explicitly when capacity/durability is unknown; avoid treating uncertain compressed estimates as guarantees |

Container, bounded views, index strategy and codecs are technical recommendations,
not low-level choices delegated to the owner. Their wire precision and backend
assurance require focused validation. Name/extension remain provisional until the
format review is accepted. Do not start production recording implementation from
this draft alone.

Implementation order and **proposed issue bodies** are in
[RECORDING_IMPLEMENTATION_PLAN.md](RECORDING_IMPLEMENTATION_PLAN.md):

1. Review/accept common semantics; freeze byte framing, required features and corpus.
2. Validate codecs/storage/framing with bounded disposable prototypes and fault tests.
3. Add generic immutable evidence/retention capabilities and bounded Android recorder.
4. Add paged strict Desktop reader and common offline sequence adapter on Linux/Windows.
5. Add lifecycle/storage/publication UI and cross-platform real acquisition acceptance.
6. Later analysis timelines/graphs/tracking and explicit legacy conversion.

The unexplained blank initial camera connection sometimes requiring USB replug is
an **independent recording-start reliability issue**. Start must truthfully report
whether current measurements/evidence exist. No new format field, automatic
initialization, replug workaround or camera fix is introduced here.
