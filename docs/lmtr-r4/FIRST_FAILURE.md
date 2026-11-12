# R4 first-failure diagnostic

Status: local, camera-free changes based on `fc5fae1fb5ae3783efbd3300dae4cdd40c6e1ff1`.
No commit, publication, PR/issue update, ART replay or physical test is implied.
This is diagnostic hardening, **not a correction or reclassification of
the historical API35 replay failure**. The preserved failure remains a failure.

## Observed masking paths

The earlier `RecorderEngine` could replace `reason`/`failure` on every failed
`terminateLocked` call while draining. In particular:

1. A source materialization/profile exception attempts to reserve a loss entry.
   Reservation exhaustion can replace its diagnostic before the source catch returns.
2. Writer-overload reservation failure throws into the outer preparation catch,
   replacing `loss_journal_exhausted` with `preparation_failed`.
3. Sticky `unrepresentedLoss` correctly refuses END, but the writer catch then exposes
   `write_or_validation_failure` and an `IllegalStateException`. Queues and control
   counters are cleared, obscuring the earlier resource context.
4. Allocation failure replaces terminal fields and clears queues. Backend cleanup can
   encounter a secondary exception before a result is published.

These paths are reproduced with latches/fixed clocks. They explain loss of diagnostic
information; they do not establish which first event occurred on the old API35 run.

## Single-assignment witness and ownership

`RecorderFirstFailure` is one slot allocated before workers start. Its private setters
are initialized by the engine under the existing lock; capture ignores later writes.
Only after initialization is the same object published in `RecorderSnapshot.firstFailure`
and `RecorderResult.firstFailure`. It has no engine/source back-reference, Throwable,
message, stack, array, collection, payload, or duration-sized history. Enums load during
slot construction. Capture uses primitive fields and existing bounded string references;
no witness allocation or additional clock read is performed on an OOM path.

The fixed vocabulary records stage, local causal code and exception category. A bounded
`LmtrFailure.code` or explicit source-failure reason token is retained separately as
`detailCode`; it is not inferred from an exception message. Detail codes accept only
1–64 lowercase ASCII letters/digits/underscores; prose and oversize tokens become null.
The optional stream ID is separately limited to64 characters, otherwise null.
Counters freeze before queue cleanup: accepted/acknowledged, both drop counts,
queued/in-flight/pending/control bytes, plus the sticky-loss flag at capture.

Observation sequence, record ordinal and record type use `-1` when unavailable;
field JSON represents these as null. A CHUNK may cover multiple older entries while
another Work triggers sealing, so CHUNK faults do **not** claim that triggering
observation's identity. Actual backend append faults retain the supplied record type
and ordinal. That boundary includes body/footer/sync; it does not identify which
filesystem sub-operation failed. `lastIntakeWitnessNs` is the last existing intake
witness, **not** a measured failure timestamp or necessarily the failed item's time.

The witness means **first failure observed under the engine lock**, not omniscient
causal ordering between concurrent workers. An exception waiting for the lock can be
recorded after another worker's failure. Catastrophic heap exhaustion/process death
can still prevent reporting/completion; injected OOM tests do not certify survival.
The fixed slot/vocabulary and writer scalar context add bounded startup state. Their
exact ART heap cost and scheduling impact have not been measured in this session.

## Initial cause, consequences and terminal reason

- Represented preparation/writer overload remains a qualified gap and increments its
  existing counter. It does not alone create a failure witness or failed termination.
- The first termination/safety failure sets the witness once. Preparation, writer and
  explicit source-loss reserve failures have distinct stages.
- `snapshot.unrepresentedLoss` is the current sticky safety state; the nested witness
  retains its value **at capture**. They may differ after a secondary failure.
- Existing `result.status`, `reason`, `failure` and finalization behavior remain separate
  terminal diagnostics. Later resource failure can still upgrade termination; it cannot
  replace the witness. A fully represented source error can still finalize honestly
  with failed END. An unrepresented loss still cannot certify END.
- Late callbacks after finalization cannot invent a first failure for a successful run.

`fieldSnapshot` adds `first_failure` and `unrepresented_loss` to local diagnostic JSON.
The existing finite replay persists that snapshot before its assertions, so a future
run will retain the witness without removing any assertion or launching another codec
following an unresolved failure. These fields are **not LMTR wire metadata**. Existing
legacy error strings are unchanged; the new witness never retains their exception graph.

## Deterministic coverage and a test-input defect

Tests cover source failure followed by preparation/cleanup errors; concurrent/repeated
Stop; invalid source before failed loss reservation; preparation and writer reserve
exhaustion; represented overload; late/abandoned preparation; preparation/writer OOM;
a later OOM after an earlier source failure; append/torn-body/reported-sync failures
followed by close failure; standalone close failure; contract payload rejection; honest
fully represented failed END; idle interruption; and callbacks after successful Stop.
Snapshot/result retain the same witness object across cleanup. Field tests check exact
JSON meaning and that the slot contains only scalars/enums/bounded strings.

The strengthened old writer-overload test exposed an unrelated test-input defect:
its maximum observation size was calculated from observation0, but observation10 has
longer sequence/receipt JSON. It was silently refused, and the old test checked
`finalized` without requiring a successful status. The new witness reproduces
`ADMISSION / UNSUPPORTED_OR_OVERSIZE_OBSERVATION`, sequence10. The test now negotiates
all eleven offered owners, keeps a single prepared-owner writer capacity, asserts every
admission, and requires no failure witness for honestly represented overload.
Production capacities, margins and assertions were not changed. This test defect does
not explain the old full-size API35 replay.

[Local validation receipt](first-failure-validation.json) distinguishes executed host
checks, retained intermediate failures, byte comparisons and unexecuted Android tests.
The frozen contract/corpus and both complete CHUNK validations are unchanged. Binary
comparison covers14 deterministic engine files (including represented/unrepresented
failures) and six full-size saved-fixture profile/codec files. The latter uses existing
fixed identities and clocks, not current camera frames or physical admission evidence.

## Proposed next instrumented test — not executed

After owner review and separate publication/runtime authorization:

1. Build a review-pinned APK/test APK from the exact approved diff. Use only an isolated
   API35 emulator with the existing configuration, never the Pixel/HT-301 for this gate.
2. Run only `fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs`, retaining its
   existing200-offer/codec bounds, baseline, memory guard, queue capacities and assertions.
   If STORED fails, preserve the failure and existing skip of DEFLATE; do not retry for
   a favorable outcome or run the full matrix automatically.
3. Retain the source SHA, instrumentation/XML/logcat, `run-result.json`, `stress.json`,
   first witness, current sticky loss, terminal reason, file and original-owner oracle.
   Do not interpret the last cleared counters as the counters at first failure.
4. Independently read any committed prefix with Python in recovery mode, verify original
   owner hashes and source immutability, and confirm no fabricated END. Use the witness
   to select the next investigation; throughput, OOM, source-validation and reserve
   exhaustion remain different hypotheses until observed.

No admission-policy, camera, thermometry, format or R5/R6 work follows automatically.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Controlled campaign follow-up (2026-11-12)

Run43242970885 reproduced reserve-exhaustion failure in four predefined fresh-process
reference/candidate arms. The witness was `WRITER_LOSS_RESERVE / LOSS_JOURNAL_EXHAUSTED`,
control1048576 bytes, sticky unrepresented loss, before the terminal END-guard exception.
The immutable witness retained11/12 acknowledgments at capture while the eventual
verified incomplete prefix contained16/17 observations after draining. These are
separate moments, not contradictory counters. END remained absent and DEFLATE was
not started. All four independent prefix readbacks match the source oracle.

This establishes the observed mechanism in the new run, not retroactive certainty
about the initial event of historical39782301329. The earlier passing isolated trials
remain distinct. See [campaign results](campaign-results.json), the
[metadata candidate](METADATA_DIGEST_CANDIDATE.md) and [memory boundary](CAMPAIGN_MEMORY.md).
No safety assertion, capacity or completion definition was relaxed.
