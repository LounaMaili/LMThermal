# First bounded physical R4 recording attempt

## Scope and exact tested source

On 2026-11-13 the owner authorized one 10–20-second ordinary physical attempt,
confirmed an unlocked Pixel and a non-sensitive scene, and authorized wireless ADB.
The separate `org.lmthermal.field` APK was built from
`069664710f1812d16ba52923da027b01d9aecbcb`, based on the published
`fc5fae1fb5ae3783efbd3300dae4cdd40c6e1ff1` foundation. Pending first-failure and
writer-instrumentation worktrees were not imported or changed. The only pre-test
behavior change was a named ten-second Full button, with a resource-boundary test.
The installed APK was pulled back and matched SHA-256
`ddf6f78a1dc049bbae4da2af67de30c6ed88150a7f7c16f2c6109e72caaca56e`.
Normal reinstall preserved app data and signing identity; the product APK was untouched.

This is one failed smoke with useful verified physical bytes, **not a completed
ten-second recording or throughput acceptance**. No second Start, downgrade,
forced GC, preview suppression, camera replug, ART run or hosted CI was performed.

## Admission and acquisition observations

Already-paired wireless ADB, Pixel 8 / Android 17 / API 37 / arm64 and HT-301
VID `1514`, PID `0001` were verified. Opening was read-only and showed display
words 32768–33007. One explicit initialization completed exact two-byte SETs
32772 → 32800 → 32768, reached ready in 6.235 seconds, discarded 75 shutter
frames and then qualified five changing frames. The first ready raw range was
5220–5674. Initialization's cumulative rejected/held/malformed counters are not
recording losses and must not be added to the following counters.

The single actual Start witness recorded:

| Quantity | Value |
|---|---:|
| Profile / codec / requested duration | Full / DEFLATE-1 / 10 seconds |
| Requested delivery | Every authoritative delivered observation; nominal 25 FPS |
| Java maximum / occupied / available headroom | 268435456 / 49007616 / 219427840 bytes |
| Logical reservation / additional margin | 81788928 (78 MiB) / 33554432 (32 MiB) bytes |
| Process RSS at admission | 318513152 bytes |
| Available system memory / low-memory flag | 1266135040 bytes / false |
| Battery / Android thermal status | 73% / 0 |
| Free storage / required free storage | 90886938624 / 525072384 bytes |
| Preparation / writer queue / preferred chunk | 4 / 4 / 4 MiB |
| Maximum observation / control reserve | 3 / 1 MiB |

Admission passed using the existing Runtime witness and policy, not a later favorable
snapshot. Logical reservation is not measured total RSS or a proof of simultaneous
ART live-heap bounds. Historical refusals remain valid separate observations.
Normal Compose Inferno/Auto preview stayed enabled. No ROI or native coordinates
were changed to improve admission.

## Actual result: interrupted by diagnostic sample serialization

The first sample failed before writing its first JSONL row. The preserved runtime
witness reports exception class `LmtrFailure`, reason `field_metrics_failed`, and
no safety refusal. It does not retain the exception code/stack; that limitation
must not be retrospectively filled in as runtime evidence.

Code inspection and a deterministic JVM reproduction identify a raw `Double`
`callback_fps` in the sample map passed to strict `LmtrJson.encode`. That encoder
intentionally rejects Float/Double. Aggregate rate reports already used explicit
finite `BigDecimal` conversion, but this separate per-sample path did not.
The follow-up field-only fix projects callback facts through `fieldCallbackSample`,
using the existing decimal helper. Regression tests exercise the actual projection,
nonintegral/integral/initial rates, unchanged counters, canonical round-trip, rejection
of raw Double, and rejection of nonfinite rates. The encoder, wire, recorder,
acknowledgement/loss rules, memory limits and camera code remain unchanged.
**The corrected APK is host-built/linted but was not installed or physically rerun.**

| Preserved result | Value |
|---|---:|
| Serialized Stop-request elapsed time from field run origin | 138655965 ns |
| Final report duration, including drain/close/reopen | 759678182 ns |
| Gate deliveries / valid measurements observed by field diagnostics | 4 / 4 |
| Engine accepted / acknowledged | 1 / 1 |
| Preparation / writer drops | 0 / 0 |
| Known / unknown source-loss signals | 0 / 0 |
| Held / invalid observations in this short source window | 0 / 0 |
| CHUNKs / entries / gap events | 1 / 3 / 0 |
| Terminal state | Finalized with END `interrupted`, reason `field_metrics_failed` |
| Trace rows written | 0 |

The diagnostics start before engine subscription, and Stop also cuts that subscription;
gate callbacks and accepted observations have different boundaries. Do not infer
three silently lost recorder offers, four saved frames, or a measured save rate from
these counts. The three intervals report about 24.99 FPS over only about 0.12 seconds;
that is **not a matched baseline or sustained recording cadence**. One sampled memory
point and three preview ages do not establish steady memory or usable recording latency.

Normal camera Close after extraction released the single owner once, leaving zero
active owners. No device fault or automatic reinitialization was induced.

## Original bytes and independent readback

The original app-private run and a new private Fedora copy are retained; nothing was
repaired, resumed, overwritten or uploaded. All six copied device files were hashed
again on-device after readback and match the retained copies. The historical evidence
and previous installed Desktop remain separate. Local preservation is not an
independent/off-machine backup. Numeric verdicts and hashes below are sanitized;
scene, oracle, device identifiers and per-frame logs stay outside Git.

- `capture.lmtr`: 201601 bytes, SHA-256
  `9432aba49701659ad1cad331f0c097d0f96791ad65e486f0e89d4957bb65d384`.
- `oracle.jsonl`: 4916 bytes, SHA-256
  `fc496f29adb5e022e493551282588c10b81cb1cc9062eb6779e501cdac8c0e19`.
- Independent Python strict full check: valid interrupted END, no unknown tail,
  one observation, one same-observation native/transport view. All original-owner
  hashes, lengths, clock/intake witnesses, metadata and retained roles agree.
- Full retained bytes: 442368 temperature Float32 + 224256 original transport;
  the 221184-byte native plane is an exact view, not a second physical copy.
  No validity-mask payload is present; all 110592 stored temperature values are finite.
- Corrected Desktop source and installed Linux binary: full check agrees with Python;
  exact Float32/native/transport hashes match the original oracle. Normal opening
  shows the real image and `END interrupted`, not an empty segment declaration.
  Point/ROI, palette/locked range, resize, separate PNG and event navigation pass
  automated native-window checks with unchanged source bytes. This is not manual
  operator or multi-frame movement validation.
- Raw range 5195–5604; stored matrix 20.803895950317383–30.107088088989258 °C.
  Low `(104,155)`, high `(206,258)`. Literal center `(192,144)` is raw 5217,
  21.32840919494629 °C; no trailer-center equivalence is inferred.
- Native ROI `[160,224) × [120,168)` has mean 21.258046589791775 °C;
  `[160,224) × [240,280)` has mean 27.916669765859844 °C. These are stored-scene
  comparisons, not independently measured physical targets.

The [sanitized receipt](first-physical-attempt-validation.json) separates the
physical tested source from the later untested-on-hardware diagnostic correction.

## Software validation and next gate

After the field correction: 444 passing JVM test executions across core (234),
contract (73), recorder (50), bridge (20), field-support (46, including shared
regressions) and app (21); field APK build/lint pass. Counts are module executions,
not a claim of 444 unique tests. Independent Python passes 64 reader and eight
original-owner/hostile-oracle tests. Desktop passes 260 tests in source and the
GLIBC 2.35 build root, 102 unchanged canonical outcomes, eleven frozen offline
window cases, three normal native opens and the real-source native interaction check.
All 51 canonical file sizes/hashes remain pinned.

An extra historical whole-corpus regeneration command did not pass under current
Python 3.15.2 or the Desktop Python 3.13.18: the original generation manifest pins
Python 3.14.8 and zlib 1.3.1.zlib-ng. Its runtime-specific manifest cannot match those
runtimes; no claim is made that every regenerated member was independently compared.
Preserve that negative result separately from passing reader conformance/hash checks;
do not rewrite or repin canonical files to make it green.

The one-attempt authorization is consumed. Before any further physical test, obtain
new authorization, preserve these originals, install a separately hashed corrected
field APK, verify normal preview/explicit ready state and repeat all resource gates.
First qualify a bounded ten-second retry of the diagnostic correction; do not jump
to 30–60 seconds. Only a fully verified short sequence can justify preparing the
longer protocol. R4 physical duration, multi-frame exact readback, liveness/steady
memory and sustained cadence remain pending. No longer recording was executed.
Issue #9 remains open; no production recording/R6, merge or CI is implied.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
