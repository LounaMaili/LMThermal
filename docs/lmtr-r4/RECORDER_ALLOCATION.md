# R4 incremental recorder allocation engineering

Status: complete-owner host checkpoint; final ART pending. **Physical Pixel8/HT-301 access is prohibited
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

## Complete ownership accounting checkpoint

The writer queue now charges `PreparedObservation.ownedBytes`: every retained payload,
its descriptor and resolved stream/context/facts JSON. It no longer charges the deferred
source's3 MiB freeze allowance after that source closure is absent from `Work`. The
preparation charge stays3 MiB; both queues stay4 MiB. Multiple prepared owners fitting
4 MiB are allowed, not an enlargement of that capacity. Object/header/slack costs remain
separate. Shared JSON may be overcharged; it is never assumed free.

Resolved retention/policy JSON can grow beyond input metadata. A deliberately tiny
writer budget now truthfully emits a writer-overload gap in that case. Singleton and
loss-prefix test fixtures negotiate the complete prepared owner rather than accidentally
omitting that JSON. An assertion about exact queued bytes is exercised with a blocked
backend and four complete prepared owners; overload behavior stays explicit.

Pending CHUNK accounting also includes payload descriptors, context and segment closure,
not merely binary lengths and entry JSON. Shared closure is conservatively charged more
than once. Without this change,32 tiny payloads could retain large optional descriptors
while the pending counter stayed small, then fail only at final metadata encoding.
An adversarial80 KiB optional descriptor now seals whole negotiated singleton entries;
no optional value is dropped. Preferred size remains a target; wire hard limits and
both complete checks remain mandatory.

The host final replay retains200/200 observations for each codec with zero preparation/
writer drops. Max prepared writer queue is3577068 B (STORED) and2682807 B (DEFLATE),
versus unchanged4194304 B capacity. Peak pending charge is about3.581 MB. These are
accounting counters, not measured peak reachable heap. Recorded inputs/settings/context,
Float32/native/transport evidence and native views pass independent source-oracle checks.
No causal uniform GC/CPU improvement is inferred from sequential runs.

### Oracle overhead and fixed scratch

The first API35 emulator replay at `bd744fa` reached a stopped/refused admission before
the original harness saved a detailed result. The preserved file independently recovers
39 observations and106 represented gaps (74 preparation,32 writer), with unknown tail
and no END. This is a failed finite replay, not a passing25 Hz gate; the precise terminal
cause was not retained. The new harness persists refusal, terminal result and snapshot
before assertions and does not launch another configuration after unresolved memory or
engine failure. Prefix readback is explicitly recovery, never complete-file acceptance.

The independent field oracle previously called `MessageDigest.update` once per Float32
pixel (110592 calls/frame). It now batches original bits into one call-local4096-byte
scratch (108 full updates/frame), retaining the original matrix copy and independence
from recorder-produced payloads. Extra scratch is4092 bytes versus the old4-byte cell,
not a new full-plane buffer or shared pool. Independent ByteBuffer/hash vectors cover
empty/partial/full batches, signed zero, infinities, NaN payloads and source immutability.
This optimizes test/oracle overhead, not native thermometry or product presentation.

## Conservative lifetime conclusion and architectural recommendation

There is now a defensible bound on the admitted **serialized/binary owner charges**:
preparation queued/in flight, prepared writer queued/in flight, complete pending closure,
entry count, metadata/record hard limits, codec/decode budget and rolling index limits.
The two validation calls do not overlap their decoded outputs, but dead bytes may still
occupy the heap. Loss journal reservations are independent; exhaustion remains sticky
and cannot produce END. Sampled process memory is not added to native/PSS peaks as if
all were simultaneous Java objects.

This is **not yet a complete Java peak-live/admission proof**. Remaining gaps include:

- Actual simultaneous source/controller/Compose/graphics roots versus reclaimable garbage.
- Freeze/oracle temporary overlap and JVM/ART collection timing.
- JSON map/string/BigDecimal expansion and index/frontier object/header overhead.
- Encoder concatenation, BAOS growth/backing/output and native zlib lifetime, not solely
  logical decoded lengths. A default codec may return valid bytes without preprovisioning
  every later application allocation.

The110 MiB instantaneous gate is an intentionally conservative refusal heuristic. It
can reject garbage-heavy healthy preview, but these host/emulator observations do not
justify replacing it with a lower arbitrary number.78 MiB +32 MiB, queues, profiles,
source evidence and runtime guards remain unchanged. No claim that256 MiB is inherently
insufficient follows from occupied-heap samples.

| Alternative | Decision for this checkpoint |
|---|---|
| Remove redundant validation/codec-input arrays | Implemented; same bytes/checks, measurable allocation reduction |
| Charge actual prepared and complete pending ownership | Implemented with bounds/overflow-loss regressions; queues unchanged |
| Bounded oracle scratch | Implemented, exclusive call-local4 KiB; exact original bits |
| Shared image/codec scratch pool | Deferred: requires exclusive lease/release and explicit expansion/failure caps; a pool alone adds permanent live memory |
| Smaller preferred CHUNK | Not adopted:4 MiB remains tested baseline; could increase closure/index work and does not bound JSON/UI roots |
| HT-specific smaller maximum observation/reservation | Not adopted: malformed/unavailable observations and transient freeze costs must remain covered; no generic module assumption |
| Staged actual arena provisioning | Recommended next engineering step after a restricted HT allocation ledger; provisioning cannot authorize future UI allocations or favorable-GC retry |
| largeHeap/second process/hidden preview | Not introduced; separate owner architectural review if a complete ledger still cannot fit |

**Recommendation:** keep R4 admission blocked. Next implement a restricted, explicit HT
allocation ledger with a writer-owned bounded encode/decode arena and a separate metadata
object budget; prove exclusive sequential reuse across both validations, failure cleanup
and original-owner parity. Reassess simultaneous source/UI live roots independently.
Only that evidence can justify a staged provisioned admission change. Do not use an
emulator no-drop replay or lower allocation volume as a substitute for this proof.

## Future physical gate (not authorized in this session)

A future owner must explicitly release the Pixel from LMarchery and reauthorize physical
LMThermal access; online ADB alone is insufficient. First finish the conservative combined
memory model and validate the exact pushed runtime on isolated ART. Then require normal
unlocked foreground access, attached HT-301, valid normal permissions, newly verified
non-sensitive scene, battery/thermal/storage/system-memory checks and normal Compose.
Do not alter security settings, force GC or retry Start for a favorable sample.

Under that new authorization only: retain a matched preview baseline, evaluate/persist
normal admission once, and stop on refusal. If admitted, one20-second Full file plus its
pre-transform source oracle, explicit Stop/Close and independent Python full byte/mask/
native/acquisition/context/intake/loss/immutability validation are required. Measure real
cadence/freshness, queues, sealing and memory separately. No extended profiles follow
without separate authorization. This session creates **only synthetic fixture LMTRs**.
