# R4 targeted presentation performance and real UI investigation

Status: measured byte-preserving allocation/CPU improvements; **corrected real UI
probe completed, safe recorder admission not established**. Baseline is
`e98ce49cd8e899e718a48b45b17092edf1fc99b7` (runtime8e928b2).
[Exact receipts](presentation-validation.json) retain failures separately from passes.
Previous [heap investigation](LIVE_HEAP.md) and private evidence remain unchanged.

## Exact percentile experiment: rejected

A median-of-three three-way order selection found the four required2nd/98th ranks,
with `Float.compare` signed-zero ordering and bounded-partition fallback. Independent
old-sort checks preserved Double bounds and normalized levels, including ties/zeros,
monotonic/reverse/plateau/extreme finite planes and nonfinite rejection. It was slower:

| Room range p50 | Original full sort | Rejected selection |
|---|---:|---:|
| Host wall |1.752 ms|3.667 ms|
| Pixel ART wall |8.965 ms|32.718 ms|

Ordered planes regressed more; fallback also increased allocations. The candidate
source and measurements remain private, with its source hash and full numerical
results in the public receipt. **The full sort remains in production.** No approximate
histogram, percentile definition, interpolation, signed-zero ordering or native
thermometry operation changed. Renderer Auto now validates once rather than scanning
once in effectiveRange and again in autoRange; each public entry still validates.

## Retained optimizations and ownership

- `ImmutableRoiProvider` is an explicit optional capability, separate from
  `ThermalMeasurement`. HT computes shared complete-plane-validated row-major statistics
  inside its private immutable measurement. Generic modules retain copy/mask fallback.
  Delegated wrappers overriding matrix/mask do not implicitly inherit another owner's
  capability. Source/session/geometry gates and row-major tie/mean semantics remain.
- `CelsiusRender.takeArgb()` transfers the newly generated pixel array exactly once,
  clearing the render's reference under synchronization. Existing unconsumed argb()
  remains a defensive copy; argb()/takeArgb() after transfer refuse. Source matrix/raw
  arrays are never transferred. Android Bitmap copies the transferred array; actual
  ART tests mutate it after construction and verify bitmap pixels unchanged. No pooling
  or premature recycling occurs; asynchronous UI consumes the bitmap, not that array.
- Optional null-by-default presenter callbacks collect bounded field-only counters.
  Product rendering stays always enabled with unchanged conflation, source epoch and
  publication guards. Field suppression invalidates only presentation, never source
  readiness/thermometry, and requires new current data before restoring output.

### Paired measurements

Eight warmups per version;32 samples/version with alternating order on equivalent
full-size room/random/ordered/plateau planes. Output comparison is outside timing.
Host thread CPU/allocation counters and wall p50/p95/max are recorded. ART process
GC counters include instrumentation/runtime overhead; no deliberate GC. The test
reference reconstructs e98ce49 data operations, not an entire old application; small
metadata/counter overhead is not subtracted. Neither benchmark is live preview FPS.

| Room operation | Before → after host p50 | Before → after Pixel ART p50 |
|---|---|---|
| Auto range |1.756 →1.749 ms|9.014 →9.077 ms|
| Render incl. display array handoff |2.532 →2.326 ms|32.451 →26.505 ms|
| Full ROI |0.368 →0.196 ms|6.886 →6.655 ms|

Range timing is effectively unchanged; no range speedup is claimed. Render allocates
about1,769,768 →1,327,464 bytes/frame in the host benchmark. The eliminated ARGB
array payload is442,368 bytes; small reference/object overhead differs. HT ROI
allocates442,728 →368 bytes per call; generic-module fallback remains442,728.
Pixel ROI CPU gain is small/inconclusive; its proven benefit is eliminating a full
plane copy. Preserve maxima, GC effects and synthetic-case variations in the receipt.
Original Float32 samples, min/max/mean, full palette pixels, native coordinates,
source bytes and both complete recorder validations remain unchanged.

## Actual Pixel/HT-301 Compose session (no recorder)

Paired wireless ADB, normal permissions, unlocked foreground, battery/thermal/storage
and a non-sensitive wall/objects scene were verified privately. Read-only Connect
observed DISPLAY at approximately25 callback FPS. One explicit official
32772 →32800 →32768 session reached ready; no new controls followed.

The four planned windows were Locked/no ROI, Auto/no ROI, Auto/full ROI and field-only
suppression:3 s transition warmup then10 samples at1 Hz each. The first window
collected10 rows, then failed writing its summary because the strict LMTR JSON encoder
correctly rejects Double. This failure is retained as **incomplete**, not a four-window
pass. Field diagnostic rates/ratios now explicitly convert to finite BigDecimal; the
strict encoder and wire contract remain unchanged. Tests verify the original rejection.

The first window's overlapping sample interval observes about24.8 successful render
publications/s and25.0 started renders/s; final render p50/p95/max29.608/40.449/45.875 ms.
It records240 completions,239 publications and one rejected completion. Warmup/window
boundary work can cross counters. These are real Compose-worker publications, not
LCD/photons latency or sensor timestamps. Full preview-age/thermometry distributions
were not saved because the summary failed; no distribution is invented from sparse logs.

Follow-up read-only20-sample health windows in the **same session**, with normal Auto
then a visible large ROI, had minimum Java headroom71,114,560 and82,071,360 bytes.
**7/20 and4/20 respectively fail the unchanged110 MiB policy evaluation.** This is
not an actual attempted recorder admission or a favorable-GC trial. Natural-GC changes
are retained; no GC was requested. Weak-owner observations include reclaimable generations
and cannot prove a global live-heap bound/leak. The suppression phase did not execute.
Normal Close released the one opened owner once, zero active owners.

### Corrected controlled repeat

The owner physically reconnected the camera and unlocked the Pixel. Normal permissions,
DISPLAY/non-sensitive scene, thermal status0, battery55% and about4.3 GB storage were
verified. The corrected APK21e1965f completed all four windows with one explicit normal
initialization and no extra controls/recorder/forced GC. Every original failed row/log/
screenshot remains private and unchanged; the repeat uses a different run directory.

| Real configuration | Gate FPS | Preview age p50/p95/max ms | Render p50/p95 ms | Minimum Java headroom / samples below110 MiB |
|---|---:|---|---|---|
| Locked, no ROI |25.003|54.48 /67.65 /78.73|30.80 /40.54|106,053,440 B /1 of10|
| Auto, no ROI |24.999|58.05 /70.85 /92.20|32.16 /43.48|97,406,784 B /2 of10|
| Auto, full ROI |25.006|55.32 /71.37 /88.41|30.88 /41.70|85,151,552 B /1 of10|
| Presentation suppressed |25.000|none (zero publications)|none|154,406,720 B /0 of10|

Normal windows each published all requested completed renders (264 /265 /263), with
no counted pending replacement/rejected completion/source loss/invalid/held observation
in their measured windows. Rates are approximately25 publications/s; the legacy
preview count divided by source first/last receipt span is biased upward by window
boundary counts. Exact phase-elapsed rates and counts are in the receipt, not presented
as more precise product guarantees. The full ROI calculation p50/p95/max is7.27 /15.81 /
21.62 ms. Gate thermometry p50 is17.61 /17.73 /16.61 ms normally and19.67 ms suppressed;
no acquisition CPU improvement from suppressing presentation is inferred.

Suppressed presentation retains genuine thermometry/acquisition but performs no render/
ROI or preview publication. Its native allocated/RSS/PSS sampled peaks are22.73 /
375.03 /316.58 MB versus up to51.82 /450.20 /398.04 MB in normal windows. These are
separate, sequential phase peaks, not additive live allocations or a controlled causal
memory decomposition. Native bitmap storage is not separately isolated from other native
allocations. Health Runtime occupied/headroom and the later PSS/native memory probe can
straddle natural GC; their Java peaks differ and are explicitly separate in the receipt.

Each normal window observes four natural GCs (three suppressed); process allocated-byte
rates over the first/last counter sample interval are recorded separately from whole-phase
elapsed time. No GC is forced. Suppressed weak-census zero means no render/ROI observer
events, not zero live source owners.

Weak census, natural GC counts and process allocation deltas remain observations with
profiler overhead, not proof of all reachable owners. The probe samples the last128 HT
measurement owners only. No heap dump was required. Normal Close again released the
single owner once; renderer settings/ROI were restored before Close. Suppressed-mode
headroom is **not** permission to hide preview to admit a product recording.

## Admission and remaining gates

No budget/queue change:78 MiB estimated recorder reservation +32 MiB margin, two4 MiB
queues, original profiles and evidence. Existing source/presenter weak counts and finite
cost reductions do not establish a conservative combined peak-live/allocator bound.
The real headroom witnesses retain refusal; **no20-second physical LMTR or source oracle**
was created, and no sustained profile followed. Six host files remain entirely identical
to the previous checkpoint; independent original-owner Python checks cover fixture files
on host/Pixel, not a new physical recording.

Do not conclude256 MiB is inherently insufficient solely from garbage-inclusive occupancy.
Next work: complete the corrected four-window profile, bound source/presentation ownership
and transient allocator overlap against incremental recorder buffers. If that proof fails,
compare bounded scratch/lifetime changes and explicitly reviewed architecture options;
largeHeap, a second process, hidden preview suppression or post-GC Start are not automatic
solutions. Only then the conditional Full smoke and matched sustained/lifecycle/recovery.

| R4 gate | Current verdict |
|---|---|
| Exact presentation/ROI/source-fixture parity | PASS for tested cases |
| Lower allocation churn / room render CPU | Observed; finite, not live-heap bound |
| Exact selector replacement | REJECTED performance regression |
| Real Compose investigation | Completed finite corrected windows; original failed summary retained |
| Safe simultaneous recording admission | PENDING; real policy witnesses still refuse |
| Physical Full smoke / original-source parity / sustained profiles | NOT EXECUTED |

417 JVM,64 Python,23 final camera-free Pixel ART tests plus13 actual presenter/ROI tests
and3 report-fix ART tests pass; final API26/API35 receipts are recorded separately.51
canonical fixtures remain unchanged. Build/lint, module/localization/reference/diff
checks pass. PR #16 stays DRAFT/unmerged; #9 stays OPEN; Desktop/R5/R6 remain untouched.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
