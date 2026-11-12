# Deterministic R4 backlog / control-journal investigation

## Scope and outcome

Camera-free host engineering from f55bb5d, after the five-run campaign. No new
Actions, ART, emulator, ADB or device access; no engine, clock policy,78+32MiB
memory policy,4MiB queues, validation, ACK, END or frozen wire change.

The new `RecorderBacklogProbe`/`RecorderBacklogTest` demonstrate a possible positive
feedback mechanism. They **do not establish the dominant cost or throughput of ART**.
Historical campaign files and failed results remain unchanged. Aggregate independent
readback: [backlog-results.json](backlog-results.json). Original binaries/oracles/traces
and all intermediate failures are retained privately, never canonical fixtures.

## Demonstrated mechanics

`RecorderEngine.reserveControlLocked` charges32768 bytes per loss, not its final
wire byte size. At1048576 bytes exactly32 outstanding loss reservations fit. That
state is not itself fatal. A33rd reservation sets sticky unrepresented loss and
records `WRITER_LOSS_RESERVE / LOSS_JOURNAL_EXHAUSTED`. END then refuses honestly.

Preparation preserves intake order and either passes the immutable observation to
the bounded writer or retains an explicit loss Work. The writer's `add` tests the
oldest pending intake witness against new work and the one-second limit. After
**each** Work, `writeLoop` compares the current clock with that oldest witness and
seals if expired. Thus:

1. Writer/backend stalls while preparation keeps observing source data.
2. Writer queue fills; each later writer rejection consumes one control reservation.
3. When the writer drains old work, an already-expired witness seals after just one
   event. Consecutive old losses can therefore become one-gap CHUNKs, independently
   of payload size. This is not a new source timestamp or synthetic observation.
4. Every small CHUNK still encodes/validates, performs the second independent complete
   append validation, envelopes, sync/ACK and periodic navigation work. Fixed cost is
   paid repeatedly even with no matrix payload, potentially extending backlog age.
5. Journal reservations release when the gap is accepted into pending, **before its
   append/sync is certified**. Occupancy zero is not durability. An injected append
   failure still leaves no END and only the independently verified earlier prefix.

Changing retained queue bytes or allocation volume does not by itself eliminate
this control-rate/validation/sync mechanism. The one-second deadline and checkpoint
rules are not waived by these experiments; no compliance with real wall-clock
service deadlines is claimed while a backend is deliberately blocked.

## Controlled experiments and exact scope

Two codecs (STORED/DEFLATE-1), generic simulated256×128 Full evidence,4MiB preferred
CHUNK,4MiB preparation and writer queues,3MiB negotiated owner,1MiB control. The
unchanged formula is78MiB plus the mandatory32MiB margin. This is not an HT-301
performance model, physical cadence or capacity acceptance.

Prime one durable observation, then block the second CHUNK **before backend append**.
Admit one source observation at a time, waiting for preparation to resolve, until
32 writer losses reserve exactly1MiB. Assert zero preparation losses and that blocked
bytes cannot acknowledge the second observation. Stop, optionally add two simulated
seconds, then unblock. A controlled clock advances50ms per attempted successful
backend record. These are injected durations, **not measured elapsed writer costs**.
The fixed256-row trace retains only scalars, not CHUNKs or payloads.

Results are the same for both codecs:

| Case | Retained observations | Known missing in file | Committed CHUNKs | END |
|---|---:|---:|---:|---|
| Fresh backlog /32 journal losses |17|32|4|completed|
| Aged backlog /32 journal losses |17|32|50|completed|
| Aged +33rd unrepresentable loss |17|32|49|absent, unknown tail|
| Aged, final gap append throws |17|31|48|absent, unknown tail|

Fresh emits one mixed and one gap-only CHUNK; aged emits32 gap-only CHUNKs. Complete
CHUNK validation calls grow4→50 for each independent validation boundary (12.5×),
without changing source count or retained data. The failed final gap has49 append
attempts but48 committed CHUNKs. Its first failure is `BACKEND_APPEND /
WRITE_OR_VALIDATION_FAILURE`, with control already zero. This injection happens
before the base backend: **not an observed operating-system fsync failure**. Earlier
private directory names containing `gap-sync-failure` are historical scenario labels.

All eight files pass independent Python full/full-prefix readback, exact original
Float32/native/acquisition hashes, contiguous entry IDs, nondecreasing intake times,
ordered unique loss sequences, truthful completion and unchanged source hashes.
Completed files cover every offered observation; incomplete files do not certify
their unknown tail. Kotlin/Python normalize only an omitted Python `unknown_tail`
on complete END versus Kotlin's explicit false; no wire evidence is changed.

## Reproduction (host only)

Use JDK17 and the existing Gradle wrapper; never run connected Android tasks:

```bash
./gradlew -p tools/lmtr-r3-host test :lmtr-recorder:writeTestRuntimeClasspath
java -Xmx256m -cp "$(cat lmtr-recorder/build/test-runtime-classpath.txt)" \
  org.lmthermal.recorder.RecorderBacklogProbe /new/private/backlog-directory
python3 tools/check_lmtr_r4_backlog.py /new/private/backlog-directory \
  --report /new/private/python-readback.json
```

Destinations must not exist; no old result is overwritten. JUnit disposable files
are cleaned after worker termination; the standalone probe's positive and negative
outputs persist. The final host suite passes459 executions (75 contract,71 recorder,
25 adapter,54 field,234 core), versus447 previously: four new tests are deliberately
shared across three modules. Independent Python64 tests,12 CI-helper tests,29 combined
hostile cases,51 canonical cross-language verdicts and12 existing engine interoperability
outcomes pass. Exact frozen §§2–16 and76 shape definitions remain unchanged.

Retained intermediate tooling failures: cross-module test helper used an internal
API (corrected to public owned bytes); standalone summary selector retained the old
scenario-field name after clarifying append vs sync (corrected). Neither was an
engine failure; their logs and the eight already readable files remain preserved.

## What remains hypothesis

The eight historical ART fatal arms directly prove reserve exhaustion, but do not
record per-event age/control-release/composition at sufficient resolution to prove
this cascade was their principal cause. Actual per-gap JSON cost, repeated validation,
index work, storage sync, scheduling/GC and host contention may all contribute.
A controlled clock establishes ordering/causality in this scenario, not a sustainable
rate or a physical memory-admission proof.

## Optimizations in PR17: recommendation

Keep the seven fixed compiled regexes and bounded lowercase metadata digest encoding
**isolated as allocation candidates**, not a throughput fix. Host cumulative current-
thread allocation fell397012688→338950216→333331440 bytes per100 validations. The final
unpaired wall sum remains3.8% slower than original. ART reference/candidate arms all
hit reserve exhaustion; no ART speedup is demonstrated and DEFLATE did not begin.
Seven retained Pattern graphs are a cost; fewer temporary Formatter graphs do not
prove less live heap/RSS. Neither candidate needs more mechanisms now. Retain exact
parity tests; do not merge on performance claims. Do not add another simultaneous
optimization until fixed cost and backlog age are attributed.

## Architectural options — proposed only, not implemented

1. Truthfully coalesce contiguous same-stream/session/category loss evidence into
   ranges, only with compatible evidence qualifications and exact sequence coverage.
   This can lower journal/metadata work, but changes admission/accounting/retention
   ownership and requires a separately reviewed bounded design and race tests.
2. Assemble already-available ordered work before a seal, without inventing or
   rewriting intake witnesses. This needs careful analysis against frozen first-entry
   deadlines; resetting age when work reaches the writer is not an allowed shortcut.
3. Separate bounded encode/validate/backend stages or optimize a specifically measured
   complete-validation cost. Added simultaneous buffers, ACK order and interruption
   recovery must be proven first; neither validation may simply be removed/cached away.
4. Explicitly refuse or terminate a capacity configuration that cannot meet service
   deadlines, with represented losses and honest recovery. This preserves evidence
   but does not deliver Full25Hz feasibility.

No larger queues/journal, weaker validation, Full downgrade, fake ACK, hidden loss,
END bypass or memory-policy change is proposed as a workaround.

## Future ART experiments, ordered by usefulness (not authorized/executed)

1. A bounded sampled age/composition/control-reservation trace at the writer/pending/
   journal boundaries, including release and sync/ACK timestamps. Preserve both codecs
   and raw failures even if DEFLATE is not reached; no automatic retry.
2. Predeclared matched synthetic software-backend experiments isolating sync latency
   from validation/metadata cost; include gap-only and mixed chunks at controlled age.
   Match APK, source, load, runtime and GC/heap context; old runs are historical controls.
3. Only after attribution, a separately approved architecture candidate with matched
   reference/candidate order and exact independent recovery/payload/ACK checks.
4. Revisit physical normal-Compose live ownership/admission separately, after specific
   authorization; neither host tests nor ART can certify the Pixel/HT-301 gate.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
