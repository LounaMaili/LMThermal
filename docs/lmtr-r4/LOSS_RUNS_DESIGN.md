# Phase3 isolated loss-run prototype: invariants before implementation

Status: EXPERIMENTAL, not accepted for PR16/17 or a product recorder. Base facf86f.
Frozen v1, all51 corpus bytes, two full validations,78+32MiB and queue/journal budgets
remain unchanged. This design is intentionally narrower than general gap coalescing.

## Ownership / safety proof obligations

1. Source admission and sequence checking stay serialized under the existing lock.
   Every generated overload still consumes one32768-byte reservation.32 remain legal;
   the33rd still sets sticky unrepresented loss and prohibits END. No capacity increase.
2. Only the preparation owner may extend the writer deque's final *queued* loss Work,
   under that same lock. Once the writer removes it, extension is impossible. No
   observation, in-flight work, source-reported qualified loss or earlier entry changes.
3. Compatibility requires equal stream ID and exact immutable stream/session closure,
   equal generated preparation/writer-overload category/reason, consecutive host
   observation sequences and nondecreasing original witnesses. Never cross another
   Work, source configuration/session, category, or explicit/unknown evidence boundary.
4. A run holds at most32 losses and a fixed32-element LongArray. The existing reserve
   remains charged PER LOSS, not per run. At most32 outstanding generated losses fit
   the1MiB reserve. Extra primitive witness storage is<=8448 bytes including one dequeued writer-owned run, plus bounded object
   overhead, covered by the existing conservative control-object allowance; this is
   not ART live-heap proof. No payload owner is retained by the run.
5. One immutable wire gap retains exact first/last/count and the FIRST original intake
   witness. Every individual original witness remains in an optional namespaced experimental
   extension (`org.lmthermal.diagnostic.loss-witnesses`, schema1.0, required_feature=null).
   This is unaccepted optional experimental evidence (the existing Version grammar starts at major1), not a new required feature or
   canonical fixture. Generic readers can still verify exact known missing identities.
6. Do not reset the first-entry deadline to merge time or last witness. No extra wait
   for more losses. Aggregation occurs only while preparation is already queueing;
   an already-expired resulting entry still seals immediately at the writer.
7. Release exactly run.count reservations when its complete gap enters pending.
   This is not ACK. Both complete validations and body/footer sync still precede
   durable ACK; no ACK is fabricated for missing observations. Failed append leaves
   an incomplete prefix even if the control counter is zero.
8. Stop has the same intake cutoff and drains only admitted bounded work. Coalescing
   never reopens admission. OOM/incomplete evidence still poisons END. Recovery may
   find structurally complete unacknowledged records, separately from live ACK.

## Candidate comparison

| Candidate | Ownership/races/allocation | Durability/interruptions/limits | Benefit / remaining proof |
|---|---|---|---|
|Bounded compatible queued loss runs (chosen experiment)|Extend only locked deque tail, fixed32 witnesses; never enlarge capacity|Original reservation per loss; first witness/deadline; optional exact witness evidence; same ACK/END|Fewer gap entries/CHUNKs after stall. Does NOT prevent saturation while stalled. Need deterministic and future ART tests|
|Drain/batch expired distinct entries into one CHUNK|Writer owns assembly; potentially larger simultaneous metadata|Naive batching conflicts with immediate sealing of expired entries in §14; must not move witnesses|Not implemented. Requires separate policy analysis/owner review before relaxing behavior|
|Stop at high-water before unrepresentable loss|Admission/Stop atomically latched; termination control already reserved|Can refuse future recorder intake, but cannot erase an already delivered loss. Source/recorder cutoff must be explicit|No throughput fix; changes lifetime/service policy. Proposal only, never silently call success|
|Lower complete-validation fixed cost|Keep two independently executed validators, no cross-pass trust/cache; fixed registry data only|No uncertain-tail retry; same input hashes and full checks|Measure validation vs JSON/sync first; prior allocation optimizations have no proven ART speedup|

This is not a structural journal rewrite: keep both workers, queues, per-loss charges,
serialized backend and loss range grammar. Any broader dynamic range capacity, removal
of original witness evidence or validation-boundary change needs a new review.
