# Restricted HT-301 Full allocation ledger

Status: implementation-backed serialized/binary bounds and measured synthetic allocation,
**not a complete simultaneous Java-heap bound**. Scope is the existing384×288 HT-301
normal-range adapter, Full evidence, one preparation worker and one writer. Do not apply
these geometry/role assumptions to another module. Frozen wire/corpus and production
camera/thermometry/presentation code are unchanged.

[Engineering history and validation](RECORDER_ALLOCATION.md) and
[exact aggregate receipts](recorder-allocation-validation.json) preserve earlier failures.
The baseline is `cc87d2b9dbf810c3a17a50d9590ae57c3b9eae25`.

## Units and accounting domains

All numerical sizes below are **array/serialized bytes**, excluding object headers,
allocator alignment, collection tables, temporary boxed numbers and native allocations.
MiB =1,048,576 bytes. Queue counters are admission charges, not allocated arenas.

- W=384, H=288, N=110,592.
- T=224,256: complete transport including four non-image rows.
- I=442,368: the existing Int raw-index plane, not packed u16 evidence.
- F=442,368: authoritative Float32 plane.
- R=221,184: packed native u16 image evidence.
- Ready source array owners T+I+F=1,108,992 (not including LUT/calibration/UI).
- Full prepared payload owners F+R+T=887,808. No validity plane is manufactured for
  HT's all-valid current measurement. Unavailable frames retain truthful absent roles.
- Full physical decoded/stored groups F+T=666,624 per ready observation when the
  declared same-observation native view is verified; R is still charged in pending.
- O=3 MiB declared HT deferred-offer maximum; Qp=Qw=P=4 MiB, control K=1 MiB.
  O is not a bound on the total temporary allocations during freezing.

## Simultaneous owner ledger

| Stage | Bound / concrete owners | Lifetime and unresolved overhead |
|---|---|---|
| USB source and current gate/controller | One ready acquired owner has T+I+F array bytes; transport is already owned, never a lease into a recycled UVC buffer | Camera callback, source flow, controller and UI may share one owner or retain different generations; no proven global generation count |
| UI / presenter / ROI | Presenter has one active + one conflated pending request and published snapshot; bitmap and transfer ARGB are separate presentation objects | Async flow/controller snapshots and ROI can retain additional source generations. Native Bitmap/graphics allocations are not Java array bytes; historical PSS is not a bound |
| Preparation queue | Charged <=Qp; fixed HT offers cost O, so at most one queued heavy HT source | Loss-only offers shed heavy source closures and consume the separate journal. Retained source may also be current UI data: do not sum shared source twice |
| Preparation in flight | One source plus copied transport, source matrix, encoded Float32 and defensive payload owners, packed native copy/owner | Conservative array-overlap inventory: source1,108,992 + transport448,512 + matrix1,327,104 + native442,368 =3,326,976; includes mutually reclaimable temporaries without assuming GC. Context/descriptor JSON and oracle are extra |
| Original-source oracle (field only) | An independent source matrix copy F; call-local4,096-byte hash scratch; bounded original-hash/context JSONL | Runs before transformation on preparation worker. Original source remains owned. No recorder-output-derived binary oracle; context interpretation is only adapter cross-check |
| Prepared writer queue | Sum complete `PreparedObservation.ownedBytes` <=Qw: payloads + descriptors + resolved stream/context/facts bytes | Prepared Work has no deferred source closure. Approximately0.895 MB per fixture Full ready entry; policy expansion can exceed a too-small configured cap and becomes an explicit writer gap |
| Writer in flight | One complete prepared owner separately from Qw; checked against writer capacity before enqueue | It can coexist with prior pending CHUNK sealing. Source offer's O charge is not reused as actual prepared size |
| Pending entries | C(e)=sum(payload+descriptor bytes)+encoded entry+encoded active segment+encoded context; <=32 entries; ordinarily sum C<=P | A whole singleton may exceed P and is sealed immediately. General serialized upper bound with O=3 MiB and three <=1 MiB canonical JSON closures is <=6 MiB. This deliberately loose bound does not claim6 MiB of ART heap. Typical saved HT four-ready-entry charge is about3.58 MB |
| Encoder source concatenation | One decoded group's exact byte array; previous encoded groups remain owned until final body assembly | New group cannot alias prepared source owners. STORED retains this private concatenation instead of copying it; group ordering/views remain unchanged |
| Bounded DEFLATE output | One call-local segmented workspace: capacity <=C, each segment <=65,536, plus exact independent E-byte result | C=min(validationBudget/2, remaining record bytes minus envelope/prefix). C is explicit, fixed for that invocation; output overflow refuses. Workspace+result <=2C and <2E+65,536; previous groups/raw concatenation are extra. No permanent arena or shared pool |
| Final body and metadata | Exact4+J+sum(E) body array plus stored groups and writer metadata AST | J<=1 MiB; decoded+4+J<=64 MiB; full record<=68,157,440 B. These are format limits, not admission recommendations or preferred size |
| Validation1 (encode) | Private windows borrow STORED body or own one decoded array per DEFLATE group; no logical/view copies | All groups stay reachable during the call; payload coverage/hash/raw14/mask/filler/statistics/closure remain checked. Return is metadata only; public readers still copy owned logical arrays |
| Validation2 (append) | Same full validation with independent call-local decoded owners | Sequential with validation1, not concurrent live outputs. First pass garbage may still occupy heap: never subtract it on an assumption about next GC |
| Journal/index/checkpoint | K control charge,32 KiB reservations per ordered loss/terminal evidence; header initial-stream inventory <=K/4; index fanout<=256, depth<=8, page<=512 KiB | Rolling references, not historical image arrays. Page snapshots, canonicalization and summary objects allocate; overhead/temporary snapshots still require margin. Exhausted known-loss evidence is sticky and forbids END |
| Stop/drain/failure | Admission cutoff under intake lock, preparation exits, writer drains ordered work, clears pending and backend closes; read-only terminal reopen follows | Slow sync cannot be made durable by timeout. OOM cleanup is best effort, clears heavy queues, stops intake and forbids repair END. No actual exhausted-process survival guarantee |
| Native outside Java | Deflater/Inflater native state per synchronous call, Bitmap/graphics/UVC/native library state | Deflater always `end()` in finally; native amount is not certified by Java counters. RSS/PSS/native snapshots overlap and must not be added as independent maxima |

The deferred source and prepared/queued/pending owner may be the **same** payload object
at a handoff, and are not duplicate physical arrays. Charges can conservatively overlap.
Conversely, garbage awaiting normal GC is still occupied memory even after ownership ends.
The ledger intentionally does not turn serialized accounting into a Java object-size claim.

## Metadata domain and allocation uncertainty

Before writer enqueue, source stream+context+facts <=16,384 canonical bytes; complete
observation bytes including descriptors must fit the declared O. Resolved prepared JSON
is separately charged. Each pending closure's actual encoded bytes are charged before
retention, and final metadata must obey all frozen R3 bounds:

- J<=1,048,576 UTF-8 bytes, depth<=32, <=65,536 properties+elements.
- Decoded string<=16,384 UTF-8 bytes; decimal token/canonical spelling/coefficient<=1,024
  bytes/digits with accepted exponent limits; exact BigDecimal semantics.
- Descriptor optional values are preserved, not discarded to squeeze into a budget.

These give finite parser cardinality and text limits, **not measured worst ART bytes per
node/BigDecimal/String/HashMap**. Multiple metadata trees/strings can coexist in freeze,
canonical encode, encoder placement and both validators. Unknown optional content can
be object-dense; an observed small HT context is not a generic maximum. No new metadata
semantic limit is added to the wire. R4's local capacities can refuse legal-but-too-large
input honestly; a near1 MiB descriptor that no longer fits CHUNK closure cannot get a
footer/END by silently omitting fields. Exact16 KiB boundary + one-byte-over refusal
and retained optional high-precision decimals are regression tested under both codecs.

## Arena evaluation and retained alternative

A permanent4 MiB raw arena plus4 MiB encoded arena would add8 MiB reachable heap even
for STORED/idle and still require owned final body/reader outputs and metadata trees.
It would not cover negotiated singleton/native/codec expansion or maximum wire groups.
Provisioning maximum wire buffers instead would be much larger. Sequential reuse across
both validators also needs a private window/lease interface to preserve all-group views;
a public borrowed-array reader shortcut is not acceptable.

The retained alternative bounds **only recorder encoder output growth**, using direct
Deflater writes into fresh capped64 KiB segments. It has no cross-call ownership or
release race. Each result is a separate exact-length owner; all temporary references
clear and Deflater closes on normal return, allocation failure or cancellation-like
exception. Existing worker failure/Stop cleanup remains authoritative. An injected
allocator is trusted test infrastructure: it must provide new exclusive arrays; the
normal implementation always uses fresh ByteArray. No source aliases enter this path.

Against the unchanged public R3 codec, all tested compressed bytes match exactly,
including seeded incompressible groups that expand, segment boundaries and all six
complete HT fixture artifacts. This is a narrow allocation improvement, not a generic
bounded-metadata arena or a reason to lower78+32 MiB.

## Admission decision and next architecture gate

**Keep the110 MiB instantaneous headroom requirement.** The logical78 MiB formula is
unchanged. Sampled allocated/occupied/native/PSS values, host no-drop finite replay and
emulator correct overload handling cannot establish concurrent physical UI/source live
roots or an OOM-free future. No forced GC, favorable retry, preview suppression, queue
growth, Full downgrade or `largeHeap` is authorized as an admission workaround.

Next obtain a separately bounded owner census/retained-object attribution for the exact
source/controller/Compose graph and metadata trees while normal UI remains active.
Agree a device-specific object/allocator uncertainty allowance before changing policy.
If that complete live-set plus transient reserve cannot fit the normal process heap,
consider a separately reviewed storage representation/ownership redesign, not a smaller
admission constant. Any reuse of immutable source arrays needs its own lifetime proof.

Only then use the already documented conditional single20-second Full smoke procedure,
normal permissions, verified non-sensitive scene and battery/thermal/storage/system
checks. Persist the one admission witness; stop on refusal without a lucky-snapshot
retry. Exact Android original-source Python readback is mandatory. No new physical
recording, Pixel camera-free ART execution or real preview-memory diagnostic is claimed
by this resumed host/emulator checkpoint.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Additional source/presentation roots found during the code audit

These are code roles, not a summed owner census. Counting each wrapper as a new full
matrix would double-count shared measurements; treating every wrapper as the same
current generation would miss asynchronously retained older frames.

- `Ht301CameraController` receives a Java T-byte `transport.read()` array, and
  `Ht301Frame.parse` makes the immutable T-byte copy. The input Java array can overlap
  the processing iteration. The JNI replacement slot is a separate native vector;
  libuvc/USB buffers and its reserved capacity are outside Java accounting.
- `NativeEquivalentThermometry.measure` builds a16,384-entry lookup (65,536 array bytes)
  during computation. The returned `RadiometricMeasurement` retains only its trace,
  source frame, indices and Float32 matrix, not that lookup array. It still contributes
  transient allocation/garbage. No native arithmetic or ownership code is changed here.
- Controller `LatestFrameState`, `Ht301ModuleSession.stateIn`, `CameraSessionOwner`'s
  latest state and their mapping/collector coroutine locals can refer to separate
  generations. `CelsiusPresenter` also has combine input caches/locals, one conflated
  pending request, one active render and a published snapshot. No matrix copies are
  implied merely by each reference.
- Auto rendering copies the source Float32 matrix F, creates a sorted F-byte copy,
  and creates F bytes of display ARGB; Locked omits the sorted copy. These temporary
  array payloads are3F or2F respectively, before object/sort/runtime overhead. The
  256-entry legend adds1,024 bytes, then Android Bitmaps copy pixel data into separate
  backing. Old published/displayed bitmaps can overlap the new bitmap. Compose image,
  gesture and collected-state lifetimes are not certified by the presenter channel cap.
- `RoiPresenter` has combine caches, a collectLatest request/computation and its latest
  result retaining the displayed measurement. HT's explicit immutable ROI provider
  avoids another full-plane copy; generic-module fallback still copies and must not be
  charged as this restricted HT optimization. Cancellation is cooperative, not proof
  of immediate release or GC.
- The dedicated field screen has no capture exporter model attached to ThermalScreen;
  a production still-export capture is not silently included in this restricted scope.
  Field source-oracle writes and bounded distributions add their own allocations.
- `JournalState` retains original canonical clock evidence across segments, with a
  local4,096-clock cap, not one growing per image. A fixed single HT session has one
  receipt clock; module/session replacement changes that scope. `SummaryAccumulator`
  retains only declared stream/role counters. Index pages and clock JSON still need
  explicit object/header allowance; their cardinality caps do not certify16 MiB live
  heap under arbitrary modules.

A specific next improvement to evaluate is **direct exact Float32 packing into a new
private recorder payload owner**, avoiding the existing matrix-copy -> encoding-array
-> defensive-owner triple. It needs an immutable-source read interface and a constructor
that cannot expose/reuse its owned buffer, plus raw-bit/source-original parity and
failure tests. This is an allocation design recommendation, not implemented behavior
or permission to lower admission before the separate UI/root/object analysis.

## Coverage map and reproducible host checks

This is a composed test matrix, **not every adversary repeated across every full-size
profile**. Source/thread/byte semantics can be tested deterministically with small
modules; acquisition-size allocation/codec behavior needs the actual full-size fixture.

| Scenario | Evidence / scope |
|---|---|
| Analysis, Native, Full × both codecs | `HtMemoryProbe`: six full-size fixture files,6 observations each; one unsettled +5 available; whole-file baseline parity and independent original-source Python |
| Requested25 Hz + concurrent writer | `HtRecorderStressProbe`: matched producer baseline,200 Full offers per codec; immutable full-size source/gate/oracle; host no drops; emulator overload retained separately |
| Compressible/incompressible expansion and boundaries | `RecordingDeflateTest` + `RecorderCodecProbe`: deterministic binary patterns through1,769,472 bytes, exact original codec output; not real acquisition |
| Producer/writer mismatch and queue saturation | Existing deterministic `RecorderEngineTest` blocked preparation/backend, explicit prep/writer gaps and exact capacity accounting; small synthetic modules, not a new full-size burst/cadence proof |
| Maximum metadata / optional fields | New exact16 KiB source boundary and near1 MiB descriptor closure rejection, both codecs; existing80 KiB descriptor pending singleton test |
| OOM and cancellation-like workspace failure | Each output segment/final result allocation injected; independent subsequent call unchanged; existing engine preparation/partial-append/late-preparation failure tests preserve prefix and sticky loss |
| Timer/Stop/slow sync/lifecycle/source errors | Existing engine and bridge races; acknowledgement never precedes footer sync; Stop timeout is not completion; no physical detach fault is induced |
| Complete and incomplete recovery |14 independent engine interop files plus original-source full-size readbacks;51 immutable R3 corpus +64 Python tests; no canonical files regenerated |
| Physical simultaneous UI + recorder memory/freshness | PENDING; previous real refusals and preview-only UI observations remain historical |

Build with the repository JDK17/SDK and Gradle environment from CONFIGURATION.md:

```bash
./gradlew :core:test :lmtr-contract:test :lmtr-recorder:test \
  :camera-recording:test :lmtr-field-support:test :app:testDebugUnitTest \
  :app:assembleDebug :app:lintDebug :lmtr-field:assembleDebug :lmtr-field:lintDebug \
  :lmtr-art:assembleDebug :lmtr-art:assembleDebugAndroidTest \
  :lmtr-recorder:writeTestRuntimeClasspath :lmtr-field-support:writeTestRuntimeClasspath
java -Xmx256m -cp "$(cat lmtr-recorder/build/test-runtime-classpath.txt)" \
  org.lmthermal.recorder.RecorderCodecProbe /new/private/codec-cost.json
java -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.HtMemoryProbe /new/private/memory
java -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.HtRecorderStressProbe /new/private/stress
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_r4_field.py \
  /new/private/memory/full-1 --report /new/private/full-readback.json
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/lmtr_v1/tests -v
git diff --check
```

Use unique private output directories. Copy runtime classpath entries before probing if
another build might replace JARs/classes. Do not run the emulator-only ART script against
a physical device; GitHub-hosted camera-disabled API26/API35 jobs provide that route.
Retain failed verdicts and original files even when an independent recovery succeeds.

## Subsequent authorized physical diagnostic

The [actual Pixel 8 / Android 17 / API 37 live memory window](PIXEL_LIVE_MEMORY.md) completed with normal rendering and one explicit HT session. It is a later checkpoint than the historical receipts above:528 valid frames, about25 FPS,4/20 observational Java-headroom policy failures and no recorder Start/LMTR. Read-only visible-role and host-only metadata probes do not establish safe admission;78+32 MiB and the physical recording gate remain unchanged.
