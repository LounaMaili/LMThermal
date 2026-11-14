# Offline follow-up and opt-in tiered field memory diagnostic

## Status and evidence boundaries

This is an isolated software follow-up to the
[reference sidecar](SYNCHRONIZED_MEMORY_DIAGNOSTIC.md) at
`a2f42df8e8e71a1bcd36a5943023723e8ef446b0`. That original branch/code and its
historical HotSpot receipt remain intact. The already completed private physical A/B
campaign is read-only input; its originals, device identifiers, observations,
time series, APKs and logs are **not published here**. The new branch contains no
physical recording, product UI, native/USB change or recorder optimization.

No ADB discovery, device/camera access, installation, emulator, ART or Actions
execution is authorized or performed in this software phase. This candidate is
default off and belongs only to `org.lmthermal.field`, not the product APK.
Local host/Robolectric tests, synthetic LMTR outputs and builds do not qualify
its Android cost or a ten-second recording. [Host receipt](tiered-memory-validation.json).

The RSS guard and actual Start reference, 78 MiB reservation plus 32 MiB margin,
queues, journal, both complete validations, original evidence, durable ACK/loss
and finalization rules are unchanged. There is no forced GC, favorable-memory
endpoint selection, hidden source throttling or suppression of normal preview.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## What the preserved observations can establish

The host-only `tools/analyze_r4_memory_intervals.py` reads the original finite
sidecar through the independent reader. It preserves each domain's original
begin/end interval, known/unknown values and status. The full derived analysis
and all physical numbers remain private.

For adjacent collection counters, a positive difference identifies a possible
change interval from the **beginning of the earlier counter probe** to the
**end of the later probe**. The unknown read instants lie inside those brackets.
It does not assign a midpoint or a precise GC time. For each change, nearby
Java/VmRSS/PSS/native/graphics readings keep their own brackets and are labeled
before, after or overlapping that conservative interval. A within-row change
does not invent two Java readings or a numeric before/after delta from one read.
Missing counts and clock failures remain unqualified; regressions are explicit.
Counter statuses remain attached to each change: a known scalar retained by a
failed probe is not silently promoted to a qualified counter observation.

GC-associated decreases can coexist with ongoing allocation, native ownership
changes and page-accounting changes. A subsequent sample is not a retained-live
heap, a post-GC floor or a plateau. The normal camera/presentation workload
continues between all readings. Published source/render/ROI aliases are not all
roots, and equal wrappers must not be charged as separate planes.

The external A/B observers retain their separate host brackets and Android
uptime headers. The off arm has no Runtime occupancy/headroom or complete ART
counter series. It cannot acquire those missing values from the on arm. Fixed
order, different collection phases and auxiliary observer effects prevent a
causal diagnostic-only RSS/PSS subtraction. Graphics residency and native
capacities remain unattributed. No new RSS policy follows from this analysis.

## Why an additional plan is justified

The physical reference identifies Android `Debug.getMemoryInfo` as the dominant
observed domain wall cost. It reads PSS and **all** Android summary categories,
not graphics alone. Reference mode keeps the original domain-call pattern and
v1 export and remains the default plan. The shared candidate class now includes
plan dispatch and one extra reference; its cost is disclosed below, not assumed
identical to the old binary. A new explicit `MEMORY_ARM_TIERED` field action
selects this alternative:

| Domain | Reference | Tiered |
|---|---|---|
| GC before/after, Runtime Java, proc VmRSS | Every row | Every row |
| Native allocator, published roles, recorder snapshot | Every row | Every row |
| Android MemoryInfo: PSS + summary categories | Every row | Rows 0, 5, 10, … |
| Unexposed native capacities | Explicit unsupported | Explicit unsupported |

The existing one-second **delay after each completed row** and maximum 60-row
controller window are unchanged. Five rows are not exactly five seconds; actual
intervals include read cost and scheduling. No catch-up tasks, timer queue,
extra platform worker, platform timeout or separate graphics history is added.
The sampler never changes the camera's 25 Hz acquisition or Analysis's explicit
5 Hz selection policy. It skips a diagnostic call, not a source observation.

Twenty uninterrupted rows request four MemoryInfo calls instead of twenty;
this is an exact call-count reduction, not a measured Android speedup. An
arithmetic projection of omitted reference spans remains labeled as a projection:
new GC/scheduling/platform behavior need not match the old trace. Real Android
CPU, incremental allocations, RSS and contention effects are still unmeasured.

## Diagnostic export compatibility and unknowns

This is a version of the experimental **sidecar**, not a change to LMTR v1.
The original constructor and `MEMORY_ARM` retain reference plan/export
`r4-memory-probes-1`, seven original status ordinals, and no schedule member.
A deterministic six-row export is byte-identical against the prior runtime.
The existing physical v1 file remains readable with identical independent output.

Only the tiered plan exports `r4-memory-probes-2`, adding:

```json
"schedule": {
  "id": "android-memory-every-five-rows",
  "android_memory_every_rows": 5
}
```

Its status table appends `NOT_SCHEDULED` (ordinal 7); previous ordinals remain
unchanged. On a skipped MemoryInfo opportunity, that status is set but both
timestamps and **every value** are null. The platform probe and diagnostic clock
are not called for that domain. A due failed/partial read keeps its actual
brackets and partial values, and is not retried on an early row. Later skipped
rows never inherit it. Stop before a domain remains distinct: no status/times
or values were collected. An actual probe cannot manufacture NOT_SCHEDULED.

The independent Python reader accepts both schemas and rejects a false schedule,
an unannounced call, a skipped due row, fabricated skip timestamps, carried
values, missing Stop witnesses, changed schema/charge or an incompatible status
table. Unknown sparse PSS/graphics values are never interpolated. Existing
clock/error/Stop/source-replacement rules still apply.

## Storage, ownership, errors and cost

The same first-64-row storage uses 92,168 bytes of primitive array backing plus
the existing 1,152-byte proc parser. The immutable enum plan adds one reference
per window and shared class metadata, not payload arrays. Measured constructor
allocation and caller costs are in the host receipt. Arrays, headers, enum/class
initialization, controller/coroutines, platform MemoryInfo, runtime strings and
native/debug/thread allocation are not a total ART residency bound.

Arming still preallocates before recorder admission. There is no new callback in
source, writer or ACK. The existing short role/event and engine snapshot monitor
reads are retained; their Android contention under a recorder is not bounded by
the preview observation or host tests. No payload/matrix history, strong source
binding, new pool or per-stage memory owner is stored. Logical queue charges
cannot be summed with published roles into a unique-live-byte estimate.

Only actual collector completion releases the token-checked process lease.
Stop is nonblocking and does not unblock a stuck platform call; another collector
and complete export remain refused while that call owns the lease. Existing
error, OOM, cancellation, capacity, clock and source-change witnesses remain
explicit. Diagnostic failures cannot issue source/recorder Stop, change ACKs or
relabel losses. Process-wide OOM/native failure is not proved harmless by tests.

Export is still post-quiescence/post-recorder-drain, streaming to an exclusive
private partial followed by flush/sync/rename. Partial export failures and the
in-memory results are retained. Process death before export can lose diagnostics;
there is no new crash-durable trace or power-loss claim.

## Controlled host verification

The [receipt](tiered-memory-validation.json) records exact sources, commands,
test counts, preserved setup errors, host results and artifact hashes.

The final checks pass **589 JVM + 21 Robolectric tests**, **121 Python tests**,
both app/field builds and lint (zero errors; five/six warnings). The product APK
contains no tiered diagnostic symbol; the separate field APK contains it and was
not installed. All eight virtual Analysis artifacts and source-oracle files are
byte-identical across modes for each codec. All six existing profile/codec
artifacts and all 51 canonical hashes are unchanged.

- Existing and new tests cover errors/partial reads, cancellation/OOM, source
  replacement, blocked Stop/leases, stale release, contention, saturation,
  clock failure and failed export/result preservation.
- The real recorder isolation test compares identical files for both codecs
  with diagnostics off, reference, failing, tiered and tiered-failing. A blocked
  diagnostic remains independent from normal recorder Stop/finalization.
- `TieredRecorderInteropProbe` exercises eight full-size noncanonical Analysis
  cases with virtual ten-second deadlines, 250 acquired events, 50 selected
  observations (one explicitly unavailable) and 200 intentional nonselections.
  It drains selected work: this is not a throughput/backlog or Android RSS test.
  Independent Python checks source-oracle parity and source immutability.
- The existing six profile/codec cases and 51 canonical fixtures retain their
  original bytes. No canonical corpus is regenerated.

`TieredMemoryCostProbe` uses two clearly separate host platforms. `linux` has
real proc/HotSpot probes and unsupported Android MemoryInfo. `synthetic` adds a
declared fixed CPU workload plus 8 KiB scratch per MemoryInfo call; it is not
calibrated to Android or a MemoryInfo implementation. Both use the predeclared
fresh-process order off → reference → tiered → tiered → reference → off, four
warmup and eight measured 20-operation batches, fixed heap, no GC requests and
no selected rerun. Both outer controls and adverse/no-improvement results stay
in the receipt. Wall includes scheduling and unchanged fixed work; caller
allocation excludes worker/platform/native allocation. Constructor cost is
measured separately. New host gains cannot be reported as Android gains.

The retained cost results are deliberately mixed:

| Platform / arm | Mode | Median wall µs/operation | Median caller bytes/operation |
|---|---|---:|---:|
| Linux B | Reference | 92.049 | 4536.0 |
| Linux C | Tiered | 600.227 | 1081.6 |
| Linux D | Tiered | 82.833 | 4497.6 |
| Linux E | Reference | 83.566 | 4536.0 |
| Synthetic B | Reference | 415.395 | 9440.0 |
| Synthetic C | Tiered | 205.915 | 6161.6 |
| Synthetic D | Tiered | 732.051 | 5580.4 |
| Synthetic E | Reference | 425.284 | 9440.0 |

Outer off-arm wall medians are 36.309–36.633 µs/operation and allocate zero
measured caller bytes. All active arms construct the window with 92,360 caller
bytes. A separate same-driver fresh-process old/new/new/old constructor check
measures 92,352/92,360/92,360/92,352 bytes: **eight additional HotSpot bytes**,
not an ART layout/RSS bound. There is no additional primitive backing or pool.

MemoryInfo call counts are exactly 240 reference versus 48 tiered across each
complete 12×20-operation arm. Wall latency does **not** improve consistently:
Linux C and synthetic D are materially slower. Raw batches also change caller
allocation profiles. The final diagnostic batches show no GC-count change, but
CPU/JIT/scheduler traces do not identify a cause. Neither those slower results
nor compiler-profile differences are discarded or retried. The robust benefit
is fewer requested expensive-domain calls and truthful sparse data; no stable
host latency gain, fixed per-sample allocation saving or Android improvement is
established. Runtime overhead qualification remains pending.

## Future ten-second recorder protocol — not authorization

1. Obtain separate explicit hardware and single-Start authorization after review
   of this offline candidate. Verify exclusive availability, non-sensitive scene,
   exact reviewed sources/APK/hash/signer and normal permissions/resource checks.
   There is no access, pairing, installation or physical operation in this phase.
2. Use normal explicit Connect and validated initialization to genuine READY.
   Fix and record normal palette/range/ROI/orientation/scene settings. The
   operator checks movement, changing temperatures and responsive controls, then
   explicitly consents to the single Start and observes its complete duration.
3. Predeclare the diagnostic plan and normal auxiliary observers; do not switch
   plans during a recording. Arm before unchanged admission, retain startup cost
   and use the actual Start safety witness. Do not wait/select a favorable GC or
   RSS endpoint. Keep the complete campaign within the finite collector window.
4. Request only **Analysis / 5 Hz / DEFLATE-1 / ten seconds**, using the existing
   `Analysis 5 Hz / 10s (test)` control, not the generic profile or sixty-second
   controls. Diagnostic actions are `org.lmthermal.field.MEMORY_ARM_TIERED`,
   `MEMORY_STOP` and `MEMORY_SAVE` under the same package prefix; they never
   start a recorder. These are instructions for a later authorized session only.
   Analysis retains
   selected authoritative Float32/validity/context and availability facts; native
   samples and full transport evidence are absent by explicit profile choice.
   Acquisition/thermometry/normal presentation remain at their normal cadence.
   Any admission refusal or safety interruption is a result; no automatic retry.
5. Retain all original safety-health/Start/Stop/deadline, source, periodic ledger,
   original-owner oracle and writer diagnostic files. For every actual sidecar
   row keep ART allocated/freed/count/time, Java max/occupied/headroom, VmRSS,
   allocator sizes, source epoch/alias scalars and recorder run origin/state,
   accepted/ACK/loss, preparation/writer queue/in-flight, pending/journal,
   chunk/checkpoint counters. PSS/graphics are known only on scheduled actual
   reads. Existing recorder run origin distinguishes it from a source epoch.
6. These are separate intervals and logical charges, not recorder-only
   allocation or peak-live memory. Source/presentation continue allocating.
   CHUNK encoding/validation scratch, hidden coroutine/Bitmap/UVC/GPU roots and
   backend/kernel residency remain unmeasured. Writer timing intervals and
   actual guard probes retain their original scopes, not sidecar timestamps.
   **The existing recorder field sampler also calls `FieldMetrics.sample()`**,
   which performs its own `Debug.getMemoryInfo`, proc/runtime/native/GC and
   thread-CPU observations. It remains unchanged and is absent from the earlier
   no-recorder A/B comparison. Tiering the new sidecar therefore does not tier
   every process MemoryInfo call or measure its total recorder-time overhead.
   Its original sequential values/row anchor are not retroactively bracketed.
   Existing field-metrics failure/Stop policy is also unchanged; the new sidecar
   has no authority over that policy and its own failures cannot trigger it.
7. Stop normally or retain the honest guard cutoff. Stop diagnostics, await real
   recorder drain and collector quiescence, then export. A blocked probe keeps
   its lease and incomplete state: do not force replacement, bypass or export.
   Preserve original hashes, independent Python/LMTR/oracle and Desktop checks,
   then normal camera Close/zero-owner verification and phone release.

Judge admission, duration/cutoff, data integrity, represented losses, diagnostic
quality/cost and operator responsiveness separately. A finalized file or a
successful sidecar is not loss-free throughput, steady RSS or physical accuracy.
Neither a new ten-second trial nor any 30–60-second qualification is automatic.
