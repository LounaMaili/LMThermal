# R2 feasibility and fault-validation gates

**Authoritative R2 acceptance checklist. Execution evidence is separate.**

The authorized feasibility branch now contains [isolated R2 prototypes/reports](recording-r2/README.md).
This checklist remains the acceptance gate; a prototype or prepared Windows packet
does not imply completion. R1b wire freeze and R3 remain gated.

[R1a semantics and owner decisions](LMTR_FORMAT_CANDIDATE.md) are accepted direction.
Wire details and backend assurance remain provisional. This checklist defines what
R2 must prove before **R1b owner review**, final wire freeze and a canonical R3
corpus. The [desktop compression study](RECORDING_COMPRESSION_STUDY.md) is unchanged
input evidence, not Android sustained performance or provider durability evidence.

Tracking: [R2 #7](https://github.com/LounaMaili/LMThermal/issues/7) returns evidence
to [R1 #6](https://github.com/LounaMaili/LMThermal/issues/6) for R1b. R2 has no
remaining R1a blocker; R1b and all canonical/production successors remain gated.

## Entry and execution boundary

R2 is ready for a separately authorized feasibility task after R1a documentation
and issue links are reviewable. It may build bounded disposable codec/framing/
storage prototypes; it does not ship a production recorder, parser or UI. Camera
protocol, thermometry, native coordinates and Accepted LMTX v1 stay unchanged.
Use current coherent owned measurements; do not run thermometry again to compress
them or hide gaps. No unsafe USB fault injection or claim of physical calibration.

Before running, commit a test plan identifying prototype revision, device/Android
version, scene/capture provenance, codecs/backends, warmup and sustained durations,
local memory/queue budgets, target cadence and instrumentation overhead. Durations
must establish sustained behavior rather than a short desktop burst. Insufficient
thermal/power evidence is reported as incomplete, not passed. Test-plan thresholds
are engineering gates, not frozen recording-file semantics.

## Gate A — Actual Android HT-301 throughput by preservation level

Measure a ready baseline without compression, then **Analysis, Native and Full /
Research** at the real source's approximately 25 FPS where evidence exists. Resolve
capabilities/actual roles explicitly; Full uses candidate dedup only when proven.

Report for baseline and every tested profile:

- Observation/callback/acquisition FPS and frame-interval distribution, distinct/
  held/invalid/transient counts, duration and accepted-measurement count.
- Thermometry worker wall/CPU impact, without changing its algorithm or gating.
- Compressor wall throughput (uncompressed and stored bytes/s), process/thread CPU
  per frame/chunk, codec level/window and actual compression reduction.
- Steady and peak heap/native/RSS where measurable; buffer allocations/copies,
  negotiated hard bounds, queue bytes/depth, backlog trend and writer drops.
- UI/acquisition responsiveness and sustained thermal throttling/power observations;
  unavailable instrumentation and limitations are explicit.

Pass: source acquisition remains comparable to its measured ready baseline,
no unbounded queue/heap growth or blocking compression on acquisition/UI threads,
and the tested writer/backend can sustain its advertised rate without writer
overload in a controlled stable-input run. Source-invalid/held gaps are reported
separately from writer loss; nominal 25 FPS is not 25 guaranteed valid measurements.
If a profile cannot sustain the requested rate within bounded resources, report
its measured supported limit or fail that configuration; do not silently downgrade
evidence, crop matrices or conceal drops to pass. Quantify any acquisition reduction
and investigate before recommending a default codec/profile implementation.

## Gate B — Lossless codecs and cross-platform bytes

Compare at minimum **STORED, DEFLATE-1, and Zstd-3 if deployable**, on the device,
with changing scenes as well as stable scenes, context changes and masks. Compare
independent frames and bounded multi-frame groups. Separately measure temperature,
native, acquisition and profile physical-byte sums. Do not use held/repeated images
as representative changing-scene throughput/size evidence.

Pass: every retained original Float32 bit, validity byte and native/acquisition byte
roundtrips exactly; ratios, wall/CPU and peak allocation are measured rather than
estimated from desktop results. Linux and real Windows decode the same prototype
bytes. Record framing/index/context overhead separately from codec payload size.
Temporary prototype vectors are explicitly noncanonical; R3 has not begun.

Zstd decision must include exact Android dependency/version/license, NDK/ABI/APK
packaging, decoder-window/dictionary enforcement, sustained CPU/power/heap results
and Linux/Windows library compatibility. If its benefit/cost is not justified,
recommend DEFLATE baseline. Numeric codec IDs and the mandatory codec set remain
provisional until R1b; a successful optional Zstd experiment is not automatic adoption.

## Gate C — Forward-only chunks and indexing

Prototype forward-only append with the architecture's preferred ~1-second/16 MiB
byte target and bounded hard ceilings. Include ordinary/short/final chunks,
singleton larger frames within hard bounds, metadata/context closure, masks and
known gap runs. Test actual maximum allocations, not only declared limits.

Pass requires:

- Complete commit footer/checksum/hash closure distinguishable from a torn body
  or footer; pending, committed and durable counters remain separate.
- Per-chunk offsets, checkpoint/index pages and final root support lazy seeking
  without whole-sequence payload/index allocation or final whole-file rewrite.
- Missing END or damaged final index/checkpoint permits validated previous commits
  to be located; ambiguity is a failure, not permission to trust a magic-byte match.
- Synthetic >4 GiB offsets and many pages/chunks exercise checked Kotlin/Python
  arithmetic and bounded cached navigation without committing a huge fixture.
- State/extension/features resolve for each independently sought chunk, including
  recovered suffixes. Unsupported required features cannot be bypassed by recovery.

Pass produces a proposed exact framing annex: magic, byte layout, length/checksum/
hash coverage, commit encoding, codec IDs, index/END semantics and justified limits.
That annex remains a candidate for R1b, not a released format or canonical corpus.

## Gate D — Failure/recovery matrix

For each case, preserve the source, record the injection boundary, reopen read-only,
compare the verified recovered range/bytes against the commit journal, and report
pending loss separately. Repeat at body/footer/index boundaries rather than testing
one convenient truncation point.

| Failure | Required outcome |
|---|---|
| Prototype process kill | Prior valid commits readable; no invented finalization time/count; no implicit in-place writer resume |
| Torn chunk body / commit footer | Incomplete pending tail not exposed; earlier valid commits survive |
| Torn checkpoint/index, corrupt final index, missing END | Valid chunk data recoverable via prior checkpoint/validated traversal; rebuildable index is distinct from corrupt promised payload |
| Corrupt committed payload/hash | Explicit integrity failure for that range, not a normal gap or silently intact recording |
| Storage full / provider refusal / close/sync failure | Stop intake, preserve prior valid committed chunks, safely seal only what is possible, report established full versus other storage failure; never claim pending frames saved |
| USB detach / session interruption | Bounded graceful drain/seal where possible; truthful stop/interruption reason; no automatic initialization/reopen or stale-frame filler |
| User Stop | Bounded drain, partial chunk seal, checkpoint/END and tested backend close; successful completion only when actually finalized |

Simulated torn writes/process kill cover relevant battery-loss failure patterns,
but do not prove physical power-loss behavior of storage hardware. R2 must document
that assurance boundary rather than deliberately power-cycling the operator's phone
or claiming an all-provider battery-loss guarantee from software tests.

## Gate E — Candidate views and adversarial bounds

If recommending views for the frozen contract, test HT acquisition's exact native
slice and a synthetic alternate geometry. Compare extracted bytes/logical hashes
with independently owned native bytes and quantify saved physical bytes/memory.

Reject before exposure: negative/overflowing/out-of-bounds offset/length, shape/dtype/
endian inconsistency, nonmaterialized parent, nested/strided/transformed/cross-frame/
cross-chunk references, logical hash mismatch and parent corruption. No native
high-bit masking or arbitrary packet reconstruction disguised as a view.
Also test malicious lengths/decompression output/windows, metadata precision/depth,
index cycles/ranges and unsupported required extension/codec handling.

Pass: a bounded implementation on Android and Python/Windows can retain/extract
exact bytes without duplicate physical HT image storage and without weakening
corruption checks. If proof fails, revise this candidate feature and return the
semantic/storage consequence to R1b; do not promote it merely because it saves space.

## Gate F — Actual storage backends and publication

Test app-private canonical storage, read-only/no-copy access, optional independent
export, and a selected local SAF provider where practical. Record provider identity
class and actual descriptor/access behavior, capability/permission/free-space
knowledge, seek/append/reopen/sync/readback behavior, errors and recovery results.
Do not generalize one local provider to remote/pipe-only/all SAF providers.

Pass requires:

- Canonical app-owned commits/recovery verified, with no mandatory second full copy
  just to access finalized bytes read-only. Access lifetime, backup and uninstall
  behavior disclosed; a URI is not an independent backup.
- Independent export success verified against finalized source bytes; failed/cancelled
  destination operations leave the source immutable and do not claim success.
- Unknown-capacity path displays unknown, gives a concise warning and **allows Start**
  on an otherwise supported backend. No made-up time estimate or capacity-only block.
- Known insufficient startup+reserve blocks with explanation; meaningful low-space
  warning is distinct from compact ordinary status, without repeated normal nagging.
- Direct/hybrid prototypes distinguish accepted stream writes from durable commits,
  with tested backend-specific assurances. A tiny spool cannot guarantee recovery
  if an untrusted provider loses all previously acknowledged bytes.
- Storage refusal preserves prior commits and produces truthful partial/failure
  results. POSIX rename/hard links/random seek are not assumed for arbitrary SAF.

If a relevant provider test cannot be performed, identify the untested configuration
and do not recommend/support that assurance yet. Unknown *capacity alone* is not a
reason to refuse a backend already found otherwise usable.

## Exit packet and R1b gate

Publish sanitized aggregate reports and prototype revision references, keeping
private images/device identifiers/paths out of Git unless deliberately sanitized.
Include baseline/profile tables, codec exactness, memory/backpressure, injected
failure outcomes, recovered ranges, backend capability/assurance matrix, view/bounds
tests and Linux/Windows decode results. Mark incomplete gates explicitly.

R2 completion requires all gates applicable to the recommended baseline to pass;
unsupported optional codec/backend configurations may be excluded with reasons.
Return a precise revised candidate with chosen wire/codec/limit/backend proposal.
R1b must explicitly approve it, including any departure from R1a. Only then may
R3 freeze the canonical corpus and R4–R6 progress through their dependencies into
production implementation. The [R2 evidence packet](recording-r2/README.md) records executed and incomplete gates separately.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
