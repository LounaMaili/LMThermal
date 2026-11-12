# Prepared phase3 ART comparison — NOT AUTHORIZED TO EXECUTE

No new workflow/trigger or hosted run is added. Pixel/HT-301 remain unavailable.
Current diagnostic test APK compiles the existing single method with optional flags;
it is not installed/launched. Existing load, heap guards, queues, assertions and
50-baseline/200-per-codec finite source remain unchanged.

## Instrumentation available

`RecorderFlowTrace` preallocates fixed primitive rows; its chosen capacity and exact
primitive bytes, retained/omitted counts, clock failures and saturation flags are
reported. Engine observer exceptions are isolated and counted (Long.MAX_VALUE is
an at-least count). All unknown witnesses use null in JSON, never invented zero ages.

Markers include intake, preparation/writer dequeue, reservation/release delta,
pending-add, seal begin/validated encode, append begin, backend begin/durable return,
ACK, seal end, Stop and first recorded failure. Rows carry original oldest queued
preparation/writer witnesses, pending witness, current work witness, control/queue/
pending bytes, ACK count, entry composition, run count and sequence where available.
Queue ages exclude dequeued in-flight items; retained current-work markers describe
those separately. A truncated trace cannot reconstruct missing events.

The ART test explicitly uses a SEPARATE `elapsedRealtimeNanos` callback for diagnostics
and policy, in the same boot-time domain. `System.nanoTime` must not be silently mixed
with Android elapsed time (sleep offsets can differ). Host controlled tests inject a
read-only diagnostic view of their own clock; diagnostic reads never advance it.

Existing fine metrics retain complete-validation and encode/append inclusive costs.
`APPEND_BEGIN→BACKEND_BEGIN` includes second validation PLUS append-session bookkeeping;
`BACKEND_BEGIN→BACKEND_DURABLE_RETURN` includes framing/writes/body+footer sync. Neither
is mislabelled exclusive CPU validation or sync time. Individual body-sync/footer-sync
start/end timestamps remain **unavailable** without a separately reviewed low-level
diagnostic hook; no accepted R3 framing algorithm was edited to invent them.
ACK is marked only after successful backend return. Raw first_failure and terminal
reason remain distinct; a missing event in a saturated trace is not proof of absence.

Build-only preparation (JDK17, existing local SDK; no connected tasks):

```bash
./gradlew --offline -PlmtrWriterTimingOnly=true :lmtr-art:assembleDebug \
  :lmtr-art:assembleDebugAndroidTest :lmtr-art:lintDebug
```

This property supplies test friendship, not a test-selection guarantee. All test
sources remain packaged; the future instrumentation method filter below is mandatory.

## Exact future test selection and flags

Only:
`org.lmthermal.art.HtMemoryRuntimeTest#fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs`

Opt-in test-only arguments (defaults preserve reference behavior):

- `lmtrWriterMetrics=on|off` — existing fine metrics.
- `lmtrWriterFlowTrace=on|off` — new bounded trace, default off.
- `lmtrExperimentalLossRuns=on|off` — prototype, default off; on requires flow trace.

Per successful codec output adds `writer-flow.json` AFTER workers terminate, including
collector omissions/errors/domain and prototype flag. `run-result.json` and `stress.json`
retain configuration, first failure, counts, reason, memory guard and raw outcome.
A failure before reporting may omit trace/report files: collector must record that
absence honestly and retain original APKs/logs/incomplete files/oracles.

## Proposed two-stage budget (requires separate owner authorization)

1. Diagnostics attribution: four fresh-process arms, flow OFF/ON/ON/OFF, loss runs OFF,
   fine metrics ON throughout. Same APK/emulator/load. Preserve every arm, no retries.
2. Prototype comparison: four fresh-process arms, loss runs OFF/ON/ON/OFF, flow and fine
   metrics ON throughout. Same artifact bytes and200 observations requested per codec.

Both stages include each arm's matched50-observation producer-only baseline. Original
runs43056135998/43238655791 and fatal campaigns are historical references, not matched
controls. Stop a genuinely broken environment; never rerun opportunistically. If a
recorder failure prevents DEFLATE, retain that untested cell rather than substitute
another method, lower load or relaxed assertion. A later per-codec-isolated protocol
would itself need review and authorization.

Suggested environment: existing Ubuntu24.04 runner strategy, API35 x86_64 google_apis,
software camera disabled front/back, isolated emulator serial only. New exact-branch
activation and any run-request file must be reviewed separately. Keep contents:read,
pinned actions, step-level runner.temp results paths identical for preflight/collector/
always-upload, unique by run ID/attempt/arm. Do not push an existing historical CI branch.

## Preflight, collection and comparison

- Freeze reviewed source/test hashes, actual checkout SHA, workload/fixture hashes,
  built APK SHA-256, JDK/Gradle/SDK/AVD/system-image identity and enabled flags.
- Preallocate collector before workers; include added diagnostic memory in occupied
  heap/headroom observations. No forced GC, larger queue/margin or favorable snapshot.
- Preserve raw JUnit verdict/logcat/host logs, first_failure, terminal status, drops,
  loss identities, pending/in-flight/queued/ACK counts, complete or incomplete LMTR,
  oracle JSONL, metrics and GC/native/PSS samples. Always collect, including preflight
  errors; verify GitHub ZIP digest and keep original ZIP unchanged in private storage.
- Compare actual producer cadence to each matched baseline; retained observations/
  explicit losses, oldest ages, queue/control occupancy, grouped gap sizes, both full
  validation counts, inclusive costs, Stop time, memory and trace completeness.
- Independent Python checks use exact original payload/witness/qualification parity
  and full/full-prefix recovery; never equate JUnit success, finalized END, integrity,
  byte equality, throughput or physical calibration.

A: complete verified two-codec replay with no loss, cadence separately qualified.
B: useful recorder failure diagnostics and independently verified prefix/accounting.
C: infrastructure failure, missing source identity/evidence or inconclusive reproduction.
Even A would not explain the old full-suite failure or certify indefinite/physical use.
No R5/R6 activation, production merge or acceptance-criteria change follows automatically.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
