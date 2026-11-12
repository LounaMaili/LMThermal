# Phase3 R4 prototype results and review boundary

Status: ISOLATED EXPERIMENT; no integration into PR16/17, production branch or main.
Base facf86f121d68f796dff7a4e6957c8069d8dd546. No hosted run, ART, emulator, ADB,
phone or camera access. [Design/architecture comparison](LOSS_RUNS_DESIGN.md) was
written before the prototype; [next ART protocol](PHASE3_ART_PLAN.md) is not execution.

## Complete journal invariant chain

| Boundary | Required invariant and current proof |
|---|---|
|Intake|Existing lock orders genuine monotonic witnesses, session/sequence checks and Stop cutoff. No transform or deferred source borrowing added|
|Reservation|Each original overload still reserves32768 bytes;32 outstanding losses exactly fill1MiB. A33rd still sets sticky unrepresented loss; no capacity or margin increase|
|Preparation|Original order preserved. The queued writer tail alone may extend under the same lock; other stream/session/category/qualified facts or an intervening observation prevents extension|
|Ownership transfer|Dequeue under that lock ends all run mutation. Fixed primitive witnesses remain, never payloads, source callbacks or Throwable graphs|
|CHUNK|Count/endpoints cover exactly every missing host observation; first intake witness remains timeline/deadline authority. Optional extension retains every original individual witness|
|Release|Release run.count original reservations only after its gap enters pending. Counter zero is NOT durability. No gap receives an observation ACK|
|Validation|Complete encode-side validation and independently repeated append-side validation remain. Both per-CHUNK call counts are checked|
|Durability|Only the existing backend owns append/sync. Body sync and footer sync precede a successful return; uncertainty never permits retry/repair END|
|ACK|Only successful durable append callbacks advance acknowledged observation counts. Queue admission, preparation, run formation and seal are not ACK|
|Interruption/recovery|A structurally complete record may be found after lost ACK return. Independent recovery reports that fact separately, never retrospective live success|
|END|Sticky unrepresented loss, allocation failure or uncertain append keeps completion false/END absent. Fully represented known losses can complete honestly|

## Results, both codecs separately checked

All measurements below use the controlled software clock/backend from phase2,
not elapsed ART throughput. Each completed case retains17 observations and32 known
missing identities, with exact original Float32/native/acquisition hashes.

| Case (same result per codec) | Reference CHUNKs | Prototype CHUNKs | Prototype known missing | END |
|---|---:|---:|---:|---|
|Fresh backlog|4|3|32|completed|
|Aged backlog|50|19|32|completed|
|Aged +33rd loss|49|18|32, plus unknown tail|absent|
|Final gap append fails before base backend|48|17|0, unknown tail|absent|

The32 aged gap-only chunks become one gap containing the exact32-member range and
32 original witnesses. Observation CHUNKs are not arbitrarily batched or delayed.
This removes31 fixed-cost encode/dual-validation/append operations in that scenario
(62% fewer total CHUNKs), without proving faster service on ART. It **does not solve
reserve exhaustion while the backend remains blocked**: the33rd loss still fails.

The grouped append is a different atomic commit unit. If that entire final range
fails, none of its32 losses is in the recoverable prefix; the reference had31 earlier
singleton gaps already committed. Both outcomes are truthful and incomplete. This
larger last-unit exposure is a concrete tradeoff, not hidden as equal crash coverage.

Six source-provided gaps with stream/session/category/qualification changes, including
one unknown-count interruption, remain six distinct exact facts. They are intentionally
ineligible even when their shape appears mergeable. Helper tests also refuse nonadjacent
sequences, decreasing witnesses, range overflow/capacity and incompatible definitions.
Stop is exercised concurrently by four callers while append is blocked; it cannot
force acknowledgment, reopen admission or bypass the draining/END rules.

Four boundary files (two codecs, before/after durable append) show live ACK=0. Before
append, recovery finds0 observations; after both syncs but before successful return,
it finds1 structurally committed observation. No END appears. Python independently
compares that original payload as well as the complete/prefix summaries.

## Evidence and regressions

`tools/check_lmtr_r4_loss_runs.py` independently verifies16 reference/prototype
complete/incomplete cases,4 ACK-boundary files and all6 qualified events. It checks
all retained payload hashes/lengths, exact lost sequences and run witnesses, chronology,
original qualified facts/session references, full/prefix integrity and before/after
source hashes. These are disposable engineering sources, not canonical R3 bytes.

Default-OFF and trace-ON reference runs are byte-identical under the controlled
clock. A throwing diagnostic observer also leaves bytes/success unchanged, with
explicit error counts. Fixed-prefix saturation, unavailable timestamps, clock errors
and Long-limit counters are covered without wrapping or claiming complete traces.
All51 canonical members and the frozen schema remain unchanged. The existing general
engine readback tool produces **14** outcomes; this recount corrects the earlier
phase2 summary's undercount of12, without changing its original files or conclusions.

The initial prototype failure was a schema0.0 experimental extension, correctly
refused by the unmodified Version grammar (major>=1). It was corrected to prototype
extension schema1.0, not by loosening a validator/assertion. All failed logs persist.
The first Android build selected an incomplete global SDK and entered automatic NDK
preparation; only this task's build client was stopped. The already-provisioned
project-local SDK was then used offline successfully. No shared daemon/device was
stopped and no Fedora configuration was changed.

Final checks:480 JVM executions (75 contract,80 recorder,31 adapter,60 field,
234 core),64 Python reference tests and14 existing engine readback outcomes pass.
All22 baseline file hashes (eight backlog plus14 interoperability) match phase2.
The diagnostic app/test APKs build offline with lint0 errors/two existing harness
warnings (DataExtractionRules, MissingApplicationIcon). Build is not ART execution.
Source hashes, APK receipts and sanitized readback are in
[phase3-validation.json](phase3-validation.json). Historical private artifacts and
all early failures remain unchanged.

## Memory and concurrency risks

Run primitive storage is32 longs (256 bytes) per run, bounded by the unchanged32
outstanding per-loss reservations plus one dequeued run:<=8448 primitive bytes including one dequeued writer-owned run, plus object headers/references.
The new nullable Work field also costs object space, even with the experiment OFF.
Serialized witness JSON, parse objects and temporary stream definitions have separate
lifetimes; coalescing does not prove proportionally less allocation or occupied heap.
The unchanged16×control allowance is conservative engineering coverage, NOT a new
measured ART admission proof. No queue/78+32MiB change or forced GC.

The optional flow collector defaults to32768 primitive bytes (256 rows); prepared
ART diagnostics select262144 bytes (2048 rows), plus objects and bounded post-run JSON.
It uses a separate same-domain clock. The collector never calls the engine, stores
payloads or performs I/O during recording. Lock order is engine→collector; report only
after worker termination. Observers that block/reenter violate the diagnostic contract;
exception isolation does not certify recovery from global VM exhaustion. Diagnostic
memory/CPU must be measured, never subtracted from safety margins.

## Decisions before any integration or next ART

- Accept or reject this narrow queued-loss-run scope and its optional experimental
  witness namespace. No required-feature or v1 contract change is proposed.
- Review the larger atomic gap-range exposure on append failure. A smaller run cap
  would trade fewer failures per unit against more fixed cost; no tuning was done.
- Review source visibility/metadata/privacy treatment of original monotonic witnesses.
- Authorize a new, predeclared diagnostics-overhead and reference/prototype ART budget.
  No host result establishes an ART speedup, lossless Full25Hz or physical admission.
- Separately review any broader high-water termination or assembly/validation redesign.
  None was implemented; both complete validators and current deadlines remain intact.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
