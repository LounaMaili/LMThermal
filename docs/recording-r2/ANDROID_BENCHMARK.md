# R2 Pixel 8 sustained recording evidence

The historical failed runs below remain intact. The [measured continuation](CONTINUATION_RESULTS.md) records the later bounded candidate and its separate acceptance status.

**Historical Gate A FAILED.** The original configurations below are not approved.
The separate continuation now passes the tested STORED/4 MiB DEFLATE throughput
and freshness workloads; R2 remains open for real Windows validation. Historical
tables and failure evidence below are unchanged.

Pixel 8, Android 17/API 37, arm64-v8a, real HT-301 in an explicitly initialized ready session. Test-only consumers/writers; production acquisition, thermometry, controls, coordinates and UI were unchanged. Each complete stage followed the predeclared 20 s warmup and approximately 120 s observation. The operator was asked to move a hand/object during the stages and reported severe visible-image latency. Distinct image fingerprints are reported, but are not a calibrated target or a per-stage scene annotation. Private scene bytes are preserved outside Git; reports contain numeric evidence only.

## Baselines and revision history

- First matrix: `c1b5c17`, valid ready baseline 25.0049 acquisition FPS. Standalone codecs ran before profiles, confounding native allocator-cache memory. The operator reported severe image lag while controls responded. This failed result is retained.
- Repeat: `eb7af2a`, exact-evidence helper/JSON overhead reduced; codec study moved after profiles. Its initial baseline was interrupted by BACKGROUND release and explicit reconnect/initialization. Its 10.8648 FPS whole-run average is excluded as a matched uninterrupted baseline. All nine recording stages completed; the operator again reported severe image delay.
- Predeclared post-matrix baseline: `2ddc541`, 122.941 s, 24.9957 acquisition FPS, 3,039 valid observations, 34 unavailable states. Observed completed renders 7.9794 FPS; displayed-source age p95 350 ms, maximum 582 ms. Different order/thermal history limits direct comparison with the repeat. It is not relabeled as an initial baseline.
- Final metadata admission correction charges serialized contexts and pending gap closures; JVM bounds tests pass. It has **not** passed another sustained live matrix. The following measurements belong to their recorded revisions.

## Completed profile matrices

Saved measurements below are derived from physical role bytes / exact per-frame bytes (Analysis 442368; Native 663552; Full 666624, no masks in these measured valid observations). Committed entry counts include explicit gaps and are not saved matrix counts. Writer drops are distinct from unavailable/source-sequence gaps. Effective saved FPS is an overloaded-run average, not a guaranteed sustainable source rate.

### First matrix

[All numeric observations](reports/android-first-sustained.json), including interval quantiles, settings/state counters, allocation lower bounds, memory endpoints, battery/thermal endpoints and commit counts.

| Profile / codec | Seconds | Acquisition FPS | Accepted | Saved matrices | Saved FPS | Writer drops | Payload MB/s | File MB/s | Stored/input | Framing MB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| analysis-stored | 123.640 | 25.000 | 3088 | 1865 | 15.08 | 1223 | 6.67 | 6.71 | 1.000 | 4.057 |
| native-stored | 124.079 | 25.000 | 3095 | 1566 | 12.62 | 1529 | 8.37 | 8.41 | 1.000 | 3.833 |
| full-stored | 124.190 | 25.002 | 3094 | 1404 | 11.31 | 1690 | 7.54 | 7.57 | 1.000 | 3.751 |
| analysis-deflate-1 | 124.499 | 24.996 | 3080 | 1653 | 13.28 | 1427 | 5.87 | 1.48 | 0.247 | 3.657 |
| native-deflate-1 | 128.006 | 24.983 | 3098 | 1359 | 10.62 | 1739 | 7.04 | 2.02 | 0.282 | 3.453 |
| full-deflate-1 | 124.497 | 24.997 | 3072 | 1235 | 9.92 | 1837 | 6.61 | 1.90 | 0.283 | 3.376 |
| analysis-zstd-3 | 125.164 | 24.999 | 3072 | 1592 | 12.72 | 1480 | 5.63 | 1.26 | 0.218 | 3.577 |
| native-zstd-3 | 125.205 | 24.999 | 3063 | 1343 | 10.73 | 1720 | 7.12 | 1.84 | 0.255 | 3.382 |
| full-zstd-3 | 124.987 | 25.003 | 3000 | 1097 | 8.78 | 1903 | 5.85 | 1.52 | 0.256 | 3.147 |

All nine stages finalized with no writer failure. This proves bounded finalization with explicit loss, not lossless source-rate recording.

### Repeat matrix

[All numeric observations](reports/android-repeat-sustained.json), including interval quantiles, settings/state counters, allocation lower bounds, memory endpoints, battery/thermal endpoints and commit counts.

| Profile / codec | Seconds | Acquisition FPS | Accepted | Saved matrices | Saved FPS | Writer drops | Payload MB/s | File MB/s | Stored/input | Framing MB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| analysis-stored | 124.840 | 25.000 | 3117 | 2339 | 18.74 | 778 | 8.29 | 8.33 | 1.000 | 4.991 |
| native-stored | 125.415 | 24.997 | 3104 | 1910 | 15.23 | 1194 | 10.11 | 10.14 | 1.000 | 4.595 |
| full-stored | 125.080 | 25.000 | 3067 | 1605 | 12.83 | 1462 | 8.55 | 8.59 | 1.000 | 4.280 |
| analysis-deflate-1 | 125.376 | 24.997 | 3102 | 2005 | 15.99 | 1097 | 7.07 | 1.77 | 0.245 | 4.348 |
| native-deflate-1 | 124.240 | 25.000 | 3071 | 1714 | 13.80 | 1357 | 9.15 | 2.69 | 0.290 | 4.187 |
| full-deflate-1 | 124.532 | 24.998 | 3085 | 1517 | 12.18 | 1568 | 8.12 | 2.39 | 0.291 | 4.060 |
| analysis-zstd-3 | 124.951 | 25.002 | 3107 | 2209 | 17.68 | 898 | 7.82 | 1.77 | 0.221 | 4.751 |
| native-zstd-3 | 125.062 | 25.004 | 3101 | 1767 | 14.13 | 1334 | 9.38 | 2.48 | 0.261 | 4.304 |
| full-zstd-3 | 124.303 | 24.995 | 3086 | 1636 | 13.16 | 1450 | 8.77 | 2.31 | 0.259 | 4.328 |

All nine stages finalized with no writer failure. This proves bounded finalization with explicit loss, not lossless source-rate recording.

## Repeat latency, memory and CPU

Whole-process memory is sampled every 2 s; columns are separate observed maxima, not necessarily simultaneous. Neither endpoint sampling nor file/queue bounds proves a 64 MiB incremental-memory gate. First-study allocator history and the excluded interrupted repeat baseline further prevent a clean steady-memory comparison. No true steady-state allocation/median was measured.

| Run | Render FPS | Display age p95 / max ms | Queue MiB / max depth | Java / native / RSS peak MiB | Freeze ms/accepted | Codec CPU s | Process CPU s | Stop ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| analysis-stored | 3.11 | 3140 / 5239 | 3.80 / 10 | 222.7 / 164.3 / 585.9 | 14.16 | 0.56 | 398.45 | 687 |
| native-stored | 1.21 | 9484 / 15860 | 3.80 / 7 | 242.8 / 183.4 / 554.1 | 15.62 | 1.05 | 408.44 | 970 |
| full-stored | 0.80 | 18255 / 22035 | 3.39 / 7 | 237.2 / 217.7 / 540.6 | 16.59 | 0.93 | 413.59 | 665 |
| analysis-deflate-1 | 0.84 | 18965 / 23560 | 3.80 / 13 | 245.8 / 168.3 / 485.9 | 15.61 | 11.54 | 422.82 | 1085 |
| native-deflate-1 | 1.08 | 15813 / 19984 | 3.80 / 10 | 244.6 / 200.4 / 537.9 | 15.86 | 15.82 | 415.40 | 1086 |
| full-deflate-1 | 1.27 | 5238 / 8044 | 3.39 / 8 | 246.8 / 206.8 / 529.4 | 15.77 | 14.01 | 411.44 | 715 |
| analysis-zstd-3 | 1.30 | 11424 / 17693 | 3.80 / 12 | 255.9 / 165.5 / 483.4 | 15.12 | 7.40 | 421.11 | 849 |
| native-zstd-3 | 1.10 | 11538 / 17800 | 3.80 / 9 | 238.1 / 176.9 / 521.3 | 16.10 | 11.50 | 418.77 | 1211 |
| full-zstd-3 | 1.58 | 4255 / 10049 | 3.39 / 8 | 254.4 / 221.7 / 505.5 | 15.70 | 9.79 | 408.78 | 457 |

The repeat had no malformed-frame counter increments during its nine stages; acquisition replacements ranged 3–60 per stage. UI heartbeat remained responsive, but completed render rate/source age and operator feedback demonstrate unusable image latency. A responsive button/heartbeat is not a successful thermal-view gate. Backpressure stayed bounded and drops were explicit; no previous-frame filler was generated.

## Interpretation and remaining measurements

- Full dedup saves 221184 physical native bytes per valid frame (5.5296 MB/s at 25 FPS), while independently owned native evidence is still copied/compared at intake. It does not remove every allocation.
- Observed chunks remained below 16 MiB in these runs (maximum 16,728,537 bytes); final metadata charge/descriptor closure can overshoot the preferred target within hard bounds. Large singleton/geometry tests require their own admission policy.
- Latest StateFlow conflates source updates. `sequence_unobserved`, replacement and unavailable counters disclose that boundary; callback counts are not recording counts. The status-string `held_or_transient_states` counter does not fully classify underlying HT-held reasons, so zero does not prove no held frames.
- [Isolated thermometry replay](reports/thermometry-cost.json): 50 evaluations from ten owned Full frames, all Float32 byte-exact, mean thread CPU 14.393390 ms and wall 14.477452 ms. Whole-process CPU columns cannot isolate acquisition, thermometry, rendering or recording cost. No thermometry recomputation was added to the live writer.
- Codec allocation numbers are explicit input/output lower bounds, not allocator-profiler peaks. Memory endpoints and RSS sampling miss instantaneous peaks. A profiler-backed copy/allocation and incremental/steady-memory study is still required.
- Battery current, percentage, temperature and Android thermal status are whole-device evidence, not app-only power/energy. No causal app-only throttling claim follows.
- The initial blank connection historically requiring replug remains separate; no camera/session workaround was introduced. The interrupted baseline is documented rather than hidden.
- Hardware/live Stop and normal detach passed bounded finalization. [Detach](reports/android-first-detach.json): 1037 ms, no post-detach measurement or reinitialization. Software sync/kill tests do not certify physical power-loss durability.

The evidence supports further test-only profiling/admission work and lower-rate experiments, not a silent profile downgrade, ROI crop or production recording release. Real Windows and the failed live/memory gates remain prerequisites to R2 completion.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
