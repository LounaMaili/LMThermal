# R4 incremental recorder allocation engineering

Status: host checkpoint; ART pending. **Physical Pixel8/HT-301 access is prohibited
for this session (Pixel reserved for LMarchery).** No local ADB command, pairing,
installation, configuration/file inspection or camera access is permitted. Existing
physical results remain historical, not a new simultaneous-preview/recorder validation.

[Exact receipts](recorder-allocation-validation.json) retain the baseline at
`4f88e8450068493e56eef1783a75658acb8fdd98`, before/after allocation and failed replays.
The frozen wire and51 canonical fixtures are unchanged. PR16 remains draft, issue9 open.

## What the78 MiB budget actually means

The unchanged logical formula is preparation4 MiB + writer4 MiB + two3 MiB in-flight
charges +12×max(preferred4 MiB, observation3 MiB) +16×control1 MiB =78 MiB.
The separate32 MiB margin makes the current instantaneous headroom gate110 MiB.
These are **admission accounting**, not preallocated arenas or a proof of a peak heap.

| Owner/stage | Retained binary owners / overlap |
|---|---|
| Current acquisition, controller, render/ROI, Android graphics | Outside incremental recorder charges; previous real UI sample is not a reachable-live upper bound |
| Deferred preparation queue |4 MiB charged; one HT offer at3 MiB retains one immutable source, not a borrowed USB lease |
| Preparation in flight | One immutable source plus freezing copies and original-owner oracle. Independent of writer work; transient copies are not bounded by the3 MiB charge alone |
| Prepared writer queue |4 MiB accounting; prepared observations own bytes/JSON and no source closure; the original implementation still charges3 MiB per item |
| Writer in flight | One prepared observation may overlap a preceding CHUNK seal; budget separately from queued and pending items |
| Pending CHUNK | Bound by preferred bytes/entry count/intake timer; includes native evidence retained even when encoded as a transport view |
| Encode | Grouped concatenation, codec output/BAOS backing growth,64 KiB codec scratch, accumulated stored groups, metadata and final body |
| Full validation1 then2 | Sequential, not simultaneous live results; dead allocations can remain occupied until natural GC. Both boundaries fully check all payload/closure semantics |
| Commit/index/checkpoint | Body through footer sync; bounded journal/frontier/pages and JSON expansion, not duration-sized image retention |
| Stop and independent readback | Pending owners cleared before terminal reopen; failed preparer must exit before a source/oracle can be reused |

A ready HT source has224256 transport +442368 indices +442368 Float32 bytes =1108992
array bytes, excluding headers, calibration/lookup objects and UI/graphics. Full prepared
payloads retain442368 temperature +221184 native +224256 transport =887808 bytes;
Full stored groups need only666624 bytes because the native plane is a verified view.
This distinction is module-specific, not a generic-camera maximum.

Freezing can overlap source1108992 + transport copy/owner448512 + matrix copy/encoding/
owner1327104 + native copy/owner442368 =3326976 array bytes. JSON, oracle hashing/copies
and allocation headers are additional. Shared owners must not be double-counted as
live objects, but unreachable temporaries must not be assumed collected at Start.

## Complete validation without retained payload copies

`LmtrChunk.validate` returns only verified metadata. Its private synchronous windows
share input or one owned decoded group; logical payloads/views do not get new arrays.
STORED windows borrow the call's body; DEFLATE consumes a bounded body range without
an input copy. `decode` uses exactly the same checks then returns independent public
payload copies, including independent native/parent arrays. No borrowed buffer escapes.
Hash, raw14, mask/+0 filler, nonfinite, statistics, ROI and feature checks remain complete.
The encoder and append each call full validation; neither boundary is bypassed.
The original conservative reader working-budget admission remains unchanged.

For a CHUNK with stored S, decoded D and logical (including views) L bytes, the old two
boundaries allocate2×(S+D+L) payload-array bytes; the new boundaries allocate2×D_deflate.
Metadata parsing/object/hash overhead remains; this is allocation volume, not live heap.
STORED encoding additionally retains its private concatenation directly, avoiding one
copy; the public codec keeps defensive copies. No shared pool/recycling is introduced.

| Largest saved Full CHUNK, one boundary | Baseline allocation p50 | Candidate allocation p50 |
|---|---:|---:|
| STORED |14849696 B|5097896 B|
| DEFLATE |12784512 B|7992048 B|

One identical probe runs against privately copied old/new classpaths, four warmups/eight
samples each, capped256 MiB host heap, no forced GC. Times vary; no uniform speedup
is claimed. All six complete profile/codec files are byte-identical. Finite samples
cannot prove generic JSON expansion, transient allocator or source/UI live-set bounds.

## Honest allocation failure and unfinished admission

Injected preparation and partial-append allocation failures mark unrepresented loss
sticky, release queued heavy references, stop intake and forbid a repair END. Existing
synchronized prefixes remain recoverable and immutable. Catching OutOfMemoryError is
best-effort cleanup, **not** a guarantee that an actually exhausted Android process can
allocate diagnostics or survive. No forced GC or automatic retry is added.

The first valid full-size host replay uses independent HT fixture owners, real gate,
adapter, original-owner oracle, codecs and both checks; requested25 Hz,200 offers/codec.
It preserves4 STORED and2 DEFLATE writer drops. Files honestly contain represented gaps;
this is **not a passing no-drop throughput configuration**. Investigate prepared-owner
charges before changing queue sizes. A first infrastructure run overlapped classpath
rebuild and failed class loading; preserve it separately, then use a copied classpath.

The110 MiB gate is conservative against a sample that includes garbage, and can refuse
healthy preview. It is not a calibrated peak predictor. These improvements alone do not
justify lowering78 MiB,32 MiB, enlarging queues, opportunistic post-GC Start, largeHeap,
a second process, hidden preview or omitted evidence. Safe simultaneous admission remains
pending, and physical validation is independently prohibited for this session.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
