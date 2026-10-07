# R2 exact framing proposal for R1b

**Provisional engineering annex. Noncanonical R2 bytes, not LMTR v1 and not R3.**
Owner approval of R1b is required. R1a semantics and Accepted LMTX v1 are unchanged.
Implementation: `recording-r2/`; independent reader: `tools/recording_r2/reader.py`.

## Identity and status

Recommend the separate product family **LMThermal Recording**, extension `.lmtr`,
format ID `lmthermal-recording`, kind `recording`. These remain recommendations.
Current experiments deliberately use `.r2proto`, `artifact=noncanonical-r2`,
prototype revision 1. **Do not publish R2 magic as the final format by accident.**
R1b must choose final identity/version/magic, exact generic/module grammar and
required feature names using this tested layout. No production reader/writer exists.

## Exact tested record envelope

All integers below are little-endian. Offsets/lengths/ordinals are encoded u64,
but prototype implementations accept only nonnegative signed-64 range, use checked
addition/multiplication and reject larger values before allocation/seek.

| Header byte | Size | Meaning |
|---:|---:|---|
| 0 | 8 | ASCII `R2RECORD` |
| 8 | 2 | Envelope revision = 1 |
| 10 | 2 | Type: HEADER 1, CHUNK 2, INDEX_PAGE 3, CHECKPOINT 4, END 5 |
| 12 | 4 | Flags = 0; other values rejected |
| 16 | 8 | Record ordinal, starting at zero |
| 24 | 8 | Body length |
| 32 | 8 | Previous record offset; first record sentinel `0x7fffffffffffffff` |

Total record = 40-byte header + body + 72-byte footer. Maximum total 65 MiB.
HEADER appears only at offset/ordinal 0. Subsequent records point strictly backwards;
recovery checks contiguous previous-record end and ordinal+1. Unknown record types
are rejected in this experiment; no untested optional-record compatibility claim.

| Footer byte | Size | Meaning |
|---:|---:|---|
| 0 | 8 | ASCII `R2CMIT!!` |
| 8 | 8 | Complete record length |
| 16 | 8 | Repeated ordinal |
| 24 | 8 | Repeated previous offset |
| 32 | 32 | SHA-256 of stored body bytes |
| 64 | 4 | IEEE CRC32 over header bytes 0–39 followed by footer bytes 0–63 |
| 68 | 4 | Reserved zero |

Write header/body forward, sync body, append complete footer, sync footer, **then**
advance committed counters. Accepted queue items and pending chunks are not commits.
An unacknowledged sync/close is a failure, even if a later forensic reader finds
complete bytes. Software fsync/process-kill proof is conditional filesystem/API
assurance, never certification of physical power loss or arbitrary providers.

References carry decimal-string offset/length/ordinal/first/last and lowercase
SHA-256 `hash = SHA256(header || body_sha256)`; the referenced record must match all
framing/reference fields and body hash. SHA/CRC are integrity checks, not authenticity.
Source immutability is checked independently with whole-file SHA-256.

## HEADER and feature negotiation

Bounded UTF-8 JSON object: `artifact`, `revision`, `profile`, `required_features`,
`codecs`, `warning`. Every required feature and codec must be supported before
normal open **or recovery**. Full declares `contiguous-native-view`. Unsupported
required features cannot be bypassed by rebuilding an index.

The experiment implements full-frame radiometric roles. It does not pretend to
freeze the complete generic visual-stream/preview-only extension grammar. R1b must
resolve that grammar and clock-domain descriptors before a complete wire freeze.

## CHUNK body and exact payloads

Body: u32 metadata byte length, UTF-8 JSON metadata, concatenated independent stored
blocks. Metadata <=1 MiB, nesting <=32, <=65536 values, duplicate keys/nonfinite JSON
and malformed UTF-8 rejected. Semantic decimal precision is retained. Block lengths
and aggregate decoded bytes plus metadata are checked before decompression.

Metadata object:

- `profile`: ANALYSIS / NATIVE / FULL.
- `contexts`: chunk-local interpretation/provenance JSON objects.
- `entries`: ordered observations, at most 1024 under hard bounds.
- `blocks`: at most four distinct role-major blocks, contiguous nonoverlapping offsets
  relative to the stored-block area. Each descriptor has `role`, `offset`, `stored`,
  `decoded`, `codec`, decoded-byte SHA-256 `hash`.

Each entry carries `sequence`, `gap_end`, `receipt_ns` (nullable), `relative_ns`,
`width`, `height`, chunk-local `context` ordinal, nullable `reason`, and `payloads`.
Counters/times use exact decimal strings. Receipt/relative times in live R2 come
from Android host monotonic receipt with millisecond resolution; they are **not**
sensor acquisition time/UTC. Synthetic clocks are explicitly synthetic provenance.
A missing-sequence gap's timeline position is its detection bound, not an invented
missing-frame receipt. R1b must make clock quality/domains explicit in final grammar.

A valid observation has exactly one sequence, full temperature payload, null gap
reason. Missing/unavailable observations have a reason and no measurement payloads.
Known runs may use `sequence..gap_end`; unknown timestamps remain null. No filler.
Context/calibration/algorithm evidence resolves wholly within the sought chunk.

Materialized logical descriptor: `kind=materialized`, `block`, slice `offset`,
`length`, logical SHA-256 `hash`, `dtype`, `shape`, optional `encoding`. Slices cover
their role block exactly without overlap/unreferenced bytes. Source Float32 bit
patterns are preserved, little-endian row-major. Validity, when present, is one
u8 byte/pixel, 0/1; invalid temperature bytes are positive zero. Valid values finite.
Geometry <=16384 per axis and <=4194304 pixels is a **tested provisional ceiling**,
not a promise that every geometry fits the ordinary Android memory budget.

Analysis materializes temperature/optional validity/context. Native adds native.
HT Full materializes temperature/optional validity/224256-byte transport, **no native
block**. Its native descriptor is `kind=view`, `parent=acquisition`, `offset=0`,
`length=221184`, `dtype=u16le`, `shape=[288,384]`,
`encoding=org.lmthermal.ht301.raw14`, logical SHA-256. Parent is materialized in the
same entry/chunk. Check its full hash before exposing the native slice; compare that
slice with independently owned native source evidence. High bits are never masked.

Reject negative/overflow/OOB, wrong geometry/dtype/endian, nested/view-of-view,
cross-frame/chunk, stride/transform, corrupt parent/logical hash. Alternate synthetic
geometry exercises the generic slice rule without imposing HT assumptions globally.
Recommend restricted views only with successful Android/Linux/real-Windows evidence.

## Codec proposal

| ID | Experimental encoding | Candidate status |
|---:|---|---|
| 0 | STORED exact bytes | Mandatory baseline recommendation |
| 1 | RFC1950 zlib / RFC1951 DEFLATE, encoder level 1 | Conservative mandatory decoder recommendation, subject to sustained/parity gates |
| 2 | Reference Zstd 1.5.7, encoder level 3, independent frame/checksum, window <=8 MiB, dictionary ID 0 | Optional experimental candidate; no mandatory adoption |

All outputs have exact checked decoded lengths; trailing/concatenated input and
expansion beyond the bound rejected. Zstd details/actual device cost and adoption
belong to the codec evidence report. No external dictionaries. A decoder rejects
unsupported required codecs, including during recovery; it does not downgrade roles.

## Chunk policy and memory

Seal **before** adding the next entry when elapsed observed timeline reaches 1 s,
32 entries, or adding bytes would reach the preferred 16 MiB physical target. Seal
immediately for a singleton already meeting the target. Charge serialized contexts to preferred admission; complete descriptor/closure
overhead is bounded at final encoding, so the preferred target remains approximate.
Hard decoded physical+metadata <=64 MiB, record <=65 MiB. Final Stop seals a short
chunk. Context changes and gap/mask entries retain closure. Bounds precede allocation.

The final prototype has a 4 MiB nonblocking handoff charging payload plus serialized
context/queued-gap bytes and one writer thread. One separate pending overload-gap
closure is bounded to 1 MiB. The measured revisions charged only payload to the
queue; this final admission correction has not passed a new sustained live matrix.
Ordinary HT incremental-memory engineering target is 64 MiB; actual measured
heap/native/RSS and source-state conflation must be reported. Large singleton
success does not imply ordinary-budget support. R1b must approve a memory admission
policy for larger geometries; hard file limits alone cannot ensure memory safety.

## INDEX_PAGE / CHECKPOINT / END

INDEX_PAGE JSON <=512 KiB: `depth` 0..7 and 1..256 `children` references. Depth0
children are CHUNK; higher children are pages exactly one level lower. Reference
ranges are ordered/disjoint, match child observation bounds and page first/last.
All references end before the referencing page and have lower ordinals: no cycles.
Rolling buffers <=8×256 references. Snapshot partial pages are appended immutable;
they never reinsert duplicate entries into the rolling index. No whole-file/index
rewrite, whole-index load, or dependence on filesystem rename/hard links.

CHECKPOINT JSON: `root` nullable index reference, decimal `chunks`/`entries`,
`last_chunk` nullable reference, `previous_checkpoint` nullable reference. At most
32 chunks between checkpoints. END JSON: final `root`, truthful committed
`chunks`/`entries`, `reason`, final `checkpoint`. END is emitted only after graceful
drain/seal/checkpoint success. Stop reason can be user Stop or source interruption.
A missing END cannot invent final counts/time or successful finalization.

Normal open validates HEADER, terminal footer/END and bounded root page, then seeks
lazily through <=8 pages. Full integrity validation is separately streaming.
Recovery scans at most 65 MiB+72 bytes backwards for a verified footer, follows
<=1024 prior records, validates up to 64 suffix chunks (one damaged checkpoint plus
current interval), and resumes read-only navigation from a valid prior checkpoint.
Index damage is rebuildable; committed payload damage is an explicit integrity
failure, never a normal gap. Damage beyond those recovery bounds is an explicit
limit. Recovered files are never implicitly resumed/rewritten in place.

## Concrete R1b recommendation (requires owner acceptance)

| Decision | Proposed value / status |
|---|---|
| Family / extension / ID | LMThermal Recording / `.lmtr` / `lmthermal-recording`, recording kind |
| Final record magic | Recommend ASCII `LMTRREC1` (8 bytes); **not emitted by R2** |
| Final commit magic | Recommend ASCII `LMTRCMT1` (8 bytes); **not emitted by R2** |
| Envelope | Same 40-byte LE header / 72-byte footer and checked signed-64 acceptance range described above, revision 1 |
| Framing integrity | IEEE CRC32(header40 + footer64); body SHA-256 and reference SHA-256(header40 + body SHA); per-block and per-logical-payload SHA-256 |
| Commit | Complete footer after body sync, then footer sync before advancing live committed counters |
| Baseline codec IDs | Recommend STORED=0 and RFC1950-wrapped DEFLATE=1 mandatory decoder capabilities; writer level 1 as conservative candidate |
| Optional codec ID | Zstd=2 only as explicit required feature for recordings that use it; defer product adoption pending real Windows and live gate success |
| Preferred / hard chunk | Earliest 1 s / 32 entries / 16 MiB; aggregate decoded+metadata 64 MiB, record 65 MiB; metadata 1 MiB |
| Index | Bounded JSON pages, 256 children, depth 8, page 512 KiB, backwards ordinal/offset links |
| Checkpoint | Every 32 committed chunks, immutable root plus previous checkpoint; Stop seals final short chunk |
| END | Truthful committed entries/chunks, final root/checkpoint/reason, emitted only after successful drain/seal; absent END never implies completion |
| Views | Retain only independently hashed same-frame/same-chunk contiguous native into a materialized acquisition parent |
| Android budget | 4 MiB nonblocking owned handoff; **64 MiB ordinary incremental writer target remains unmet/unproven**, not a supported-configuration claim |
| Initial canonical backend | App-private local file with tested sync/read-only reopening; optional verified SAF export; no direct arbitrary-provider recording guarantee |
| Recovery | Verified committed prefix, bounded tail search and suffix traversal above; explicit integrity failure for promised payload corruption |

The magic replacement/format identity are proposed release spellings, not a change to
R2 artifact bytes. Complete generic/module JSON schemas, clock-domain declarations,
required-feature names and interoperability vectors still need R1b review. Relative
time in this prototype is host monotonic receipt relative to run origin, not sensor
UTC; gaps have no invented receipt/acquisition clock. No R1a preservation invariant
is relaxed. The failed live throughput/responsiveness gate blocks recommending any
prototype configuration as a 25 FPS product default. This packet is ready for review
of evidence, **not** for owner wire freeze or production implementation.

## Initial backend recommendation and R1b decisions

App-owned local files with actual free-space query and tested sync/close/read-only
reopen are the candidate canonical backend. Optional SAF independent export requires
successful close and backend-appropriate readback. Capacity unknown warns/allows an
otherwise supported sink; known insufficient startup+reserve blocks. Provider
failure stops intake and preserves verified earlier local commits. A provider URI
is access, not an independent backup; uninstall/data-clear/cache eviction/backup
policies must be stated. Tested local SAF behavior is specific to that provider.

R1b owner approval still needs final identity/magic/version, complete generic and
module JSON grammar/clock domains, codec set/IDs, restricted views, numerical limits,
large-geometry memory admission, checkpoint/recovery envelope, supported canonical
backends and their documented assurance. No accepted R1a semantic invariant is
relaxed. Missing measurements or platform gates remain blockers, not approvals.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
