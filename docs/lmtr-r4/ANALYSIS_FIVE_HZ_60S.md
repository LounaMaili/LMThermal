# Experimental Analysis 5 Hz / 60-second qualification preparation

**Software preparation; no new physical qualification is implied.** The owner requested
one explicitly supervised 60-second experiment, rather than the earlier 30-second
proposal. The [first ten-second result](ANALYSIS_FIVE_HZ_PHYSICAL.md), its exact
implementation `d1b8c8917961a162270e58510c1eeb39baa94f04`, Desktop
`26b1785226b15dc17f977d36c2ca42eb79feeac7`, private originals and historical
failures remain unchanged. The production Android application has no recording UI.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Absolute deadline and genuine Stop witnesses

The separate field APK retains **Analysis 5 Hz / 10s (test)** and adds
**Analysis 5 Hz / 60s (test)**. Only those two durations are accepted for this
explicit periodic action. Profile is Analysis, period is 200000000 ns and codec is
DEFLATE-1 in the field path; there is no automatic profile/cadence fallback.

`FieldPeriodicDuration` checks the arithmetic for target `HEADER origin + duration`.
Both origin and current readings are Android `elapsedRealtimeNanos`, the already
qualified source-receipt domain. `RecorderEngine` accepts an optional
`RecordingStopDeadline` only with an explicit periodic policy. Existing callers
without that option retain their prior clock/wake behavior and artifact bytes.

The intake lock serializes deadline expiry, admission, loss and Stop. A real intake
reading at or after the target closes intake **before** that offer is selected or
materialized. An observation already admitted before the target still drains and
can be acknowledged after Stop. A prior manual Stop, source interruption or fatal
failure remains authoritative; deadline notification cannot turn it into success.

Three paths observe expiry: normal intake, the existing workers' bounded wakeups,
and one cancellable coroutine on the field's existing IO dispatcher. The coroutine
recomputes time remaining against the absolute target on each wake. It does not
accumulate health-sampling work into a succession of one-second sleeps. Its
notification captures the original engine, metric owner and per-run timing/failure
witnesses. Targeted unsubscribe cannot stop a successor recording even on the same
camera. Ordinary Stop, cleanup and ViewModel disposal cancel the notification.
Both jobs are published before either starts, so a fast safety refusal cannot
finish before its deadline job is visible for cancellation.

These are soft scheduling mechanisms, **not a hard real-time timer**. The engine
records its actual first serialized expiry/cutoff; it never substitutes the requested
target. `duration_deadline` identifies deadline closure, not human Stop. A delayed
cutoff can honestly include an empty partial final slot under the existing Stop
convention. For example, exact cutoff at 60 s without a boundary admission covers
slots 0–299; actual cutoff at 60.025 s covers 0–300, with the last empty slot explicitly
missed. Do not rewrite that event as 300 complete slots or exact 60-second execution.

## Reporting, ownership and unchanged protections

Private preflight records the requested absolute target. The final report separates:

- target, first observed expiry, actual engine cutoff, clock qualification and overshoot;
- first field Stop-request time, its call duration and scalar camera-counter brackets;
- the last one-Hz sampled counters, separately named rather than presented as Stop totals;
- source-intake callbacks, intentional nonselections, selected observations, missed slots,
  available/unavailable measurements, durable ACKs and represented losses;
- encoding/validation/append/drain, terminal status/reason, memory/GC and preview counters.

Counter brackets are source-epoch checked; replacement makes them unknown. They are
neither exact sensor exposure boundaries nor guaranteed identical to the engine cutoff.
If a worker/intake observes expiry first, the field notification can be later. These
two witnesses must not be conflated. Deadline-notification failure is bounded scalar
diagnostic evidence and stops the original engine; it does not request a retry.

All 60 seconds are measured; the older long diagnostic's 20-second metric reset does
not apply to this action. Source acquisition, thermometry and normal Compose remain
at their normal cadence. Sampling changes only explicit recording selection.

The additional state is bounded scalar data and one cancellable coroutine, without
a dedicated timer thread, payload owner or queue. Scalar state uses the existing
periodic metadata allowance. Scheduling uses the existing IO dispatcher; its possible
pooled-worker/stack and runtime-object costs are not an ART peak-memory measurement.
Keep 78 MiB reservation + 32 MiB margin, admission RSS + 128 MiB guard, 4 MiB prep/
writer/preferred CHUNK, 1 MiB journal and 3 MiB maximum HT offer. Both full independent
validations, ACK after synchronized footer, loss accounting and refusal of END after
unrepresented loss/invalid clocks are unchanged. No GC is forced and no preview
treatment is disabled. A virtual/JVM success does not certify ART scheduling or RSS.

## Camera-free evidence

The [preparation receipt](analysis-five-hz-60s-preparation.json) pins source hashes,
test results and actual scope. New deterministic tests cover 10/60-second boundaries,
late/early wakes, idle slots, manual/interrupted Stop, blocked preparation/footer,
concurrent Stop/admission/expiry, invalid clocks, cancellation and successor ownership.

Six noncanonical full-size fixture cases cover 10 s exact, 60 s exact and 60 s plus
25 ms notification delay for each of STORED/DEFLATE-1. They use virtual monotonic
time and original-owner oracles; no physical throughput or RSS claim follows.
The independent checker verifies every decision/slot, original Float32/context/
availability/clock evidence, complete Kotlin/Python integrity and Desktop point/ROI
values. It admits up to 4096 source-decision oracle rows while retaining 512-owner,
64 KiB line and 8 MiB aggregate diagnostic bounds. These are verification-input
bounds, not new wire limits or recorder capacities.

The final preparation passed **536 JVM test executions** (234 core, 74 contract,
82 recorder, 61 camera-recording, 85 field-support; some shared regressions run in
multiple modules), **92 Python tests** and **272 Desktop Linux offscreen tests**.
Both APK builds/lint pass with zero errors and the retained five app/six field
warnings. All six new full-size cases pass independent Python/Desktop readback;
six fixed acquisition-following profile/codec files remain byte-identical to both
the earlier implementation and historical eager reference. Cross-language checks
also pass 51 original cases, 33 rewrites, 43 JSON vectors and 29 hostile combinations.
The 51 canonical hashes and 99 preserved files of the first Analysis physical run
were reverified unchanged. None of these checks is a new physical or ART test.

One old Stop/admission race fixture could overload its unchanged preparation queue
before reaching the intended race. The retained failing output identifies
`preparation_overload`; the fixture now drains the first offer before racing the
second against Stop. Its assertions and production limits are unchanged. Original
overload/exhaustion tests remain enabled.

```bash
./gradlew --offline -p tools/lmtr-r3-host :core:test :lmtr-contract:test \
  :lmtr-recorder:test :camera-recording:test :lmtr-field-support:test \
  :lmtr-field-support:writeTestRuntimeClasspath
java -Xms64m -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.AnalysisDeadlineInteropProbe "$NEW_PRIVATE_CASES"
"$DESKTOP_PYTHON" tools/check_lmtr_analysis_five_hz.py "$NEW_PRIVATE_CASES" \
  --desktop "$PINNED_DESKTOP" --expected-cases 6 --output "$NEW_PRIVATE_READBACK"
./gradlew --offline :lmtr-field:assembleDebug :lmtr-field:lintDebug \
  :app:assembleDebug :app:lintDebug
```

Use a working existing Desktop environment and new private output paths. Do not
regenerate the 51 canonical fixtures. Source SHA/file parity, test scope and private
runtime hashes must be preserved independently of later rebuilds.

## Two mandatory human gates and one future physical Start

1. **Before any ADB discovery or connection**, obtain fresh confirmation that Pixel 8
   and HT-301 are available for this session. Use existing wireless pairing and the
   AGENTS.md discovery procedure; do not touch other applications or their data.
2. Pin source commit and field APK SHA-256. Install only `org.lmthermal.field` with
   data-preserving replacement and verify the installed APK's bytes. No uninstall,
   data reset, emulator, Actions or automatic initialization. Request ordinary
   unlock/permission/replug intervention only if actually necessary.
3. Connect normally; verify actual source state and explicit validated radiometric
   initialization. Require current frame-backed READY, ordinary Compose preview,
   non-sensitive scene and unchanged battery/thermal/storage/heap/system/RSS gates.
4. **Before Start**, explicitly invite the operator to watch the Pixel, move a hand
   or mildly warm object, check live image/temperature/UI reaction and report delay,
   freeze or lag. Await an explicit reply both ready to observe and consenting to
   **Start 60 seconds**. Silence is not consent. Investigate an already reported
   anomaly before proceeding.
5. Warn immediately before the single Start. Use Analysis/5 Hz/DEFLATE-1 and the
   normal preview/thermometry without changing load during the minute. Honor every
   safety stop; no second Start, favorable retry, Full or 120-second experiment.
6. Ask explicitly afterward what the operator observed and during which intervals.
   Store that answer separately from preview publication/age counters. Unobserved
   intervals are unknown; human impressions are not measured optical latency.
7. Preserve unique private originals, APK/LMTR/oracle/inventory hashes and all
   adverse results. Independently read the entire file in Python and pinned Linux
   Desktop: exact Float32/mask/context/source/clock/timeline, points, ROIs, palettes,
   navigation, gap/unavailable behavior and source immutability. Recovery of a prefix
   is not completed END or successful duration.

Report duration, protections, slot coverage, losses, integrity/finalization, observed
preview usability and Desktop usability as **seven distinct gates**. Around 300
requested slots are expected according to actual boundaries, not a promise of 300
available saved matrices. A passing minute would not qualify indefinite memory,
generic cameras, Full at 25 Hz, absolute calibration or a subsequent longer run.
