# Experimental field memory probes with independent time brackets

## Status and limits

This is an **opt-in diagnostic candidate for `org.lmthermal.field`**, based on
`758287118c9ed9bfa5c856c60f33299f66ec616e`. It is not product instrumentation,
a new RSS policy, R4 acceptance or authority to use hardware. No ADB, phone,
camera, emulator, ART, installation or Actions execution is part of this work.
The [offline RSS study](RSS_OFFLINE_INVESTIGATION.md) and all original physical
evidence remain unchanged. [Validation receipt](synchronized-memory-validation.json).

The recorder, its 78 MiB reservation / 32 MiB independent margin, relative RSS
guard/reference, both complete CHUNK validations, capacities, journal, source
clocks, periodic ledger, durable ACK and loss/finalization rules are unchanged.
No diagnostic value is an input to those decisions. There is no forced GC,
presentation suppression, viewport reduction, source control, new frame queue,
pool, payload history or automatic Connect/initialization/recording.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## What is actually observed

`BracketedMemoryWindow` stores nine sequential domains. **Each** has monotonic
begin/end nanoseconds, a fixed status and at most 16 scalar values. The complete
row has its own begin/end, window generation, sequence, Stop-during-collection
flag and GC-counter relation. These are intervals, not simultaneous snapshots.

| Domain | Android field implementation | Limits |
|---|---|---|
| GC before/after | Four `Debug.getRuntimeStat` counters: allocated/freed bytes, collection count/time | Counters can be absent; collection can overlap any read. Equal counts do not certify an atomic or post-GC floor. |
| Java | Runtime maximum, total-minus-free occupancy and headroom | Occupancy includes garbage; reads can straddle GC. Inconsistent negative/out-of-range tuples stay partial. |
| RSS | Bounded `/proc/self/status` VmRSS parser | Exact KiB conversion; unknown/partial/truncated/duplicate/overflow reads never become zero. |
| Android memory | `Debug.getMemoryInfo`, total PSS and seven raw `summary.*` categories | Category accounting differs from Runtime occupancy, allocator bytes and VmRSS; values are not unique owners. |
| Native allocator | `Debug.getNativeHeapAllocatedSize`, free and heap size | Three sequential reads; not all graphics, mappings, driver/kernel storage or UVC ownership. |
| Published roles | Source epoch brackets, source/rendered/ROI sequences, pairwise known aliases, Bitmap/legend allocation bytes, nominal Celsius-plane data bytes, existing callback counters and two event sequences | Brief local reference reads only; no matrix/raw/getPixels access or retained owner. In-flight coroutine roots and actual render-local generation remain unknown. |
| Recorder | Existing snapshot counters and logical queue/in-flight/pending/journal charges, ACK/loss/chunk/checkpoint counts, run origin/state | Existing short engine monitor only; logical charges are not live heap. Encoding/validation scratch and backend/kernel residency are not exposed by this snapshot. |
| Native capacities | Explicit `UNSUPPORTED` | Current public NativeUvc API exposes counters, not transfer/assembly/latest capacities. No new native handle or product hook is added. |

`summary.java-heap`, `summary.native-heap`, `summary.graphics`, `summary.stack`,
`summary.code`, `summary.private-other` and `summary.system` retain their API
identities; only units are converted to bytes. They are deliberately **not**
named per-owner PSS or RSS. See the [Android MemoryInfo API](https://developer.android.com/reference/android/os/Debug.MemoryInfo)
and [AOSP implementation](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/os/Debug.java).
No subtraction or sum of these readings supplies an unmeasured native/graphics
owner. Historical dumpsys observations are not retroactively relabeled or aligned.

The actual existing safety `health`/decision remains in the original recorder
trace. It is not replaced by this sidecar's later independent probes. Its old
unbracketed values do not acquire artificial timestamps from the new data.

## Owners, aliases, generations and release

The old host-only `MemoryClaims` records declared stage holds, can double-count
aliases and cannot prove source lifetimes from incomplete events. It is **not
promoted into a live-memory estimator**. The old last-64-row `MemoryProbeRing`
also cannot preserve the beginning of a longer window. The new candidate instead
preserves the first 64 rows and refuses further collection with an explicit
capacity flag. No original sample is overwritten.

| Observed role | Identifier / release boundary | Interpretation |
|---|---|---|
| Published camera measurement | Existing camera presentation epoch plus source sequence | Read only within the role probe; never retained across suspension. Replacement/Close/reopen during the probe clears ambiguous role values while keeping both epoch witnesses. |
| Rendered/ROI measurement | Current published sequence and pairwise reference equality | Known HT wrappers are normalized to their same immutable evidence object. Generic wrappers can share hidden storage; `false` equality does not prove separate underlying planes. No unique-byte total is computed. |
| Bitmap / legend | Current published allocation byte count | No recycling, copying or root registration. Unpublished/replaced/native/GPU owners remain unknown. |
| Active/pending event sequence | Two already existing `PresentationCounters` scalars | Event bookkeeping is not generation-bound; cancellation can leave it stale. These are explicitly not proven in-flight owners. |
| Recorder work | Existing scalar snapshot | No preparation/writer/pending source object is held in the ring. A local engine reference exists only during its short snapshot call; workers own/release real payloads as before. |
| Diagnostic collector | Process-wide monotonic scalar lease | Only actual job completion releases it. Stop/cancellation does not release a blocked platform read. A stale completion cannot release a successor. Exhaustion refuses diagnostics instead of wrapping tokens. |

`AndroidMemoryProbe` retains a **weak** model binding, reusable scalar parsing
scratch, Runtime and MemoryInfo. Its controller has a separate SupervisorJob,
not the recorder/source parent scope; a blocked platform read must not keep sibling
source jobs alive through that parent. During a role/snapshot read, ordinary local
strong references are necessary and end when that call returns. No claim of a
hard ART scheduling/monitor latency bound follows from this design.

Foreground loss, critical memory notification and model disposal request diagnostic
Stop independently. The existing source/recorder actions still run as before.
Cancelled lazy jobs also release their lease. A genuinely blocked read cannot
be forcibly made quiescent: it retains one bounded diagnostic window and the
process lease, refuses replacement, and cannot be exported as complete.

## Storage, error isolation and accounting

- Fixed schema: 177 Long slots per row, 64 rows, one 177-slot working row and
  one 16-slot probe scratch: **92168 bytes of primitive array backing**. The finite proc parser
  adds **1152 bytes**. The 128 KiB assertion is a **window-array ceiling**,
  not a total Android heap/RSS guarantee.
- Add array/object headers, enum/string metadata, atomics/locks, WeakReference,
  MemoryInfo, coroutine/dispatcher state, InputStream and platform transient
  allocations. There is no dedicated new thread per sample. Android native/debug
  allocation and page commitment are unmeasured. A fresh IO-pool worker can still
  add stack/runtime costs; HotSpot construction bytes do not bound these.
- Arming occurs before any recorder Start and allocates persistent storage first.
  Existing admission therefore sees that occupancy; no reservation/margin is
  reduced or queue enlarged. Preallocation does not guarantee page residency or
  zero later allocations. This is accounting, **not** safe physical admission.
- One attached window per controller and one active collector per process. During
  export/handoff, local references and uncollected older arrays can still coexist;
  this is not a total retained-heap or page-residency bound.
  The platform reads use no acquisition/writer callback hook. The only added
  existing-observer synchronization is a one-Hz two-scalar read under the old
  `PresentationCounters` monitor; no new callback lock or history is introduced.
- Each probe scratch is cleared before reading. Known partial values can survive
  a failing read with `FAILED`; unsupported values stay unknown. Clock failures,
  source changes, cancellation, exceptions, OOM and contention are explicit and
  retain no Throwable/message/stack history. Diagnostic failures never call
  recorder/source Stop or alter ACK/loss bookkeeping. Process-wide OOM/native
  failure cannot be proven harmless by JVM exception tests.
- Stop only latches cancellation; it never joins a probe/worker or waits for its
  monitor. Already read domains and a partial final row are preserved. Contending
  sample attempts are counted, not queued. No new data/control journal is added.
- Export requires collector quiescence and the operator's existing recorder/drain
  completion check. It streams scalars without copying/encoding the entire ring,
  to an exclusive app-private `.partial` file, then renames after flush/sync.
  A failed export preserves the partial and the closed in-memory rows. It never
  opens LMTR. Export is not permitted during the controlled recording protocol;
  do not race a new Start against Save. No independent-backup or power-loss claim.
- This first candidate buffers observations in memory during acquisition to avoid
  adding per-sample filesystem work. Process death before Save can lose them.
  That limitation must be accepted or addressed separately before a trial needing
  crash-durable diagnostics. It is not concealed by a valid LMTR END.

## Activation and readback (future authorized session only)

The field APK uses explicit `org.lmthermal.field.MEMORY_ARM`, `MEMORY_STOP` and
`MEMORY_SAVE` activity actions. They are handled in the existing field Activity;
no product manifest or UI is changed. Use single-top delivery to the already
visible Activity. Do not invoke these actions now: **this task authorizes no ADB
or physical operation**. Arm never connects/initializes/starts a recorder. It
refuses during existing work or while a previous unsaved window is owned.

The collector takes at most 60 rows with a one-second delay after each completed
sample, so it cannot sample faster than one Hz. Actual intervals include probe
cost; no exact one-Hz/deadline guarantee is fabricated. A blocked platform call
has no forced wall-time termination. Stop is separate from recorder Stop.
Wait for termination, normal recorder drainage, then Save before closing the app.
New diagnostic state does not resize/hide the normal Compose preview or add UI
controls. Status is available under the `LMThermalMemory` log tag and in the
sidecar metadata; readback distinguishes partial/error observations.

Each unique private directory is `files/r4-synchronized-memory/<uuid>/` and the
completed file is `memory.jsonl`. Header `generation` is the diagnostic lease,
not a source epoch or LMTR clock domain. The format is `r4-memory-probes-1`,
**not LMTR v1**, with exact decimal-string counters and null unknowns. The
independent bounded Python reader checks schema/charge/width, precision, sequence,
brackets, GC relation, unsupported/partial data and source immutability:

```bash
PYTHONPATH=tools python3 tools/r4_memory_probes.py /path/to/memory.jsonl \
  --output /path/to/new-readback.json
PYTHONPATH=tools python3 -m unittest tools.tests.test_r4_memory_probes
```

## Controlled software verification and remaining costs

The [receipt](synchronized-memory-validation.json) pins exact counts, runtime,
APK/hash, raw benchmark hashes, independent readbacks and immutable-source checks.
JVM tests exercise partial/unsupported/overflow input, GC advance/regression,
source replacement, aliases, concurrent readers, saturated storage, cancellation,
blocked reads, lazy cancellation, stale leases, export failure and actual recorder
Stop/finalization. Fixed-clock Full/STORED and Full/DEFLATE files are identical
with diagnostics disabled, active and throwing. A blocked diagnostic worker does
not prevent recorder finalization. This is not ART scheduling conformance.

The final candidate passes **579 host JVM + 21 Robolectric tests**, **109 Python
tests**, the field APK build and app/field lint (zero errors; five/six warnings).
All 51 canonical fixtures and six full-size profile/codec files match their prior
bytes; independent Python verifies the latter and both 16-row host scalar exports.
The protected Start/Stop/sampling/deadline/finish and Compose blocks are unchanged.
Reverification of 689 files in historical manifests finds no source change.

The private 11776361-byte field APK has SHA-256
`ff600d6e771dbb0f28cc9441f3b551cbce2fd5b5622c020abd4161f2a2100094`.
Package/DEX checks confirm the separate arm64 field application and diagnostic
classes; it has **not** been installed or run. Failed setup evidence is kept:
the first new isolation test declared only codec1 while requesting codec0, and
the unchanged engine correctly refused it. The test HEADER was fixed without
relaxing assertions. An initial runtime copy stopped at absent empty Gradle
Java/resources directories before any benchmark; its partial copy is preserved.

`SynchronizedMemoryCostProbe` compares a fixed CPU workload off/on in four fresh
sequential JDK17 processes **off A → on B → on C → off D**, four warmups and
eight measured batches of 16 operations. Linux proc/HotSpot counters are real;
Android Debug, graphics, native allocator and real module roles are explicitly
unsupported in that probe. Construction allocation is measured separately from
per-sample caller allocation/wall time. Sampling is accelerated, not a physical
cadence or whole-pipeline FPS experiment. No forced GC or favorable RSS endpoint.

`MemoryEventContentionProbe` separately compares the existing event producer with
and without the new two-scalar getter under deliberate high contention. Wall
includes thread start/join; worker allocation excludes thread creation. This
stressor is not one-Hz Android cost or an upper latency bound. Fresh ABBA arms
and all results are retained, without retries selected for favorable outcomes.

| Host ABBA arm | Fixed work + optional sample, median ns/operation | Median allocated bytes/operation | Window construction allocated bytes |
|---|---:|---:|---:|
| Off A | 36573.19 | 0 | 0 |
| On B | 96826.91 | 4536 | 92352 |
| On C | 98135.63 | 4536 | 92352 |
| Off D | 36383.84 | 0 | 0 |

The measured window constructor excludes the already created Linux probe/parser,
Android MemoryInfo, platform state and controller/coroutine costs. Its median wall
time is 51.38/48.84 microseconds in the two on arms. Per-operation time includes
the unchanged CPU work; its observed roughly 60-microsecond increment is not an
Android estimate. Instrumentation is not allocation-free.

High-contention wall medians for 30000 producer events / 10000 reader iterations
are 2.133 ms (off A), 4.772 ms (on B), 4.458 ms (on C) and 3.412 ms (off D).
Reader allocation is zero and producer allocation is 1680000 bytes in all arms,
but the added monitor competition has a real observed latency cost. Do not hide
that result or extrapolate its ratio to one-Hz Android sampling.

**ART/OS overhead and simultaneous retained memory are not qualified.** The
Android integration stays experimental, default off, outside the product. No
new physical recorder admission or recording duration is accepted. The first
hardware review must evaluate actual observer allocation, delay, pool/stack
growth and graphics/native visibility before permitting co-sampling a recorder.

## Two future physical controls — proposals, not authorization

1. **Normal preview without a recorder.** Obtain fresh exclusive Pixel/HT-301
   availability, non-sensitive scene, normal permissions, exact reviewed APK/hash
   and existing battery/thermal/storage/memory prerequisites. Use normal explicit
   Connect/initialization and actual READY frames. The operator confirms movement,
   temperatures and UI responsiveness, then watches the complete window. Fix and
   record normal palette/range/ROI/scene choices in advance; probes change none.
   Compare predeclared probe-off/on preview windows with identical load, retaining
   every original trace/log and all begin/end domains. The first finite on window
   has at most 60 rows. Predeclare a diagnostic coverage target of at least three
   observed natural collection-counter increments; this is not a recorder safety
   threshold. Count actual GC observations; if too few occur, report
   insufficient GC coverage, not an automatic extension or forced collection.
   Save after probe Stop/quiescence. Do not choose a favorable endpoint as Start.
   If numeric off-window memory data use an existing read-only observer, run that
   same observer/startup/settings policy in the on window too and retain its
   separate timing. Such an off arm is not zero-observer overhead. Do not compare
   different historical profilers or subtract unpaired GC/heap domains as a
   recorder-only or causal instrumentation RSS delta.
2. **Separate Analysis / 5 Hz / DEFLATE-1 / 10 s proposal.** Only after review of
   the first control and observer overhead, obtain distinct explicit authorization
   and operator before/during Start observation. Arm the exact reviewed sidecar
   before unchanged admission; preserve its startup cost and actual RSS reference.
   No hidden profile/cadence/preview change, GC or retry. Retain all original safety,
   source/periodic/writer/ACK/loss/clock/oracle data plus the sidecar. Any guard Stop
   ends the trial honestly; do not fabricate unopened slots. Stop probes, await
   recorder and collector ownership resolution, Save, hash originals and perform
   independent Python/LMTR and Desktop checks before closing. Distinguish actual
   cutoff, integrity, drainage, observer failures and human usability.

Unobserved native capacities, unexposed in-flight owners, root reachability and
graphics attribution remain unknown even after these probes. Neither this
candidate nor a successful diagnostic export qualifies a new RSS policy,
30–60-second recording, indefinite operation, loss-free Full at 25 Hz, physical
power-loss behavior or absolute thermometric accuracy.
