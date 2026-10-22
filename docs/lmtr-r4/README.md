# R4 camera-free recorder foundation

**Engineering work in progress, not full R4 acceptance or a recording product.**
Branch: `feat/lmtr-r4-recorder-foundation`, based on R3 integration
`8eb4aceaf8bb36a8061c932b3121f898b81b4af3`. [R4 #9](https://github.com/LounaMaili/LMThermal/issues/9)
stays open. No R5/R6 or Desktop application work is included.

## Integration and authorities

R3 PR #14 was merged at the above SHA and #8 was closed for finite contract conformance.
The exact reviewed head was `fb857b658db3761d2a45b1669c5be98df591e3fd`.
[Final Windows run 38393731476](https://github.com/LounaMaili/LMThermal/actions/runs/38393731476)
validates that head: 71 JVM, 64 Python, 51 corpus cases per reader and native NTFS
sparse/full integrity. Earlier committed receipts on earlier SHAs remain history.
The [accepted wire](../LMTR_WIRE_V1.md), acceptance commit
`8c343cd6cf1ca11eff4f3bbaa5c2b10dfbbf645f`, schema and 51 pinned fixtures are unchanged.
R2 branch/history remains at `747b89c8a0072603a73deb81db5cdbef0ed900a1`.

A separate [Windows CI follow-up PR #15](https://github.com/LounaMaili/LMThermal/pull/15)
maintains main/PR regressions. It is not part of the already accepted R3 merge.

## Architecture audit and new API

`CameraContract.kt` exposes copy-only `ThermalMeasurement.matrix()`/validity and optional
scalar native samples; it does not expose complete transport, module contexts, recording
session identities or every original clock. `CelsiusPresenter` deliberately conflates
presentation requests and uses owner-generation guards. Recording must receive observations
from the module's authoritative measurement path **before** presentation conflation.
`Ht301ExportEvidence` proves richer module evidence exists for still export; it is not a
universal recording adapter. The optional acquisition/lifetime extension described in
[ART integration](ART_INTEGRATION.md) preserves the existing single camera owner and
CameraUiLifetime policy. Its guarded notifications do not change camera controls.
The [R2 review](../recording-r2/COMPLETION_REVIEW.md) is finite feasibility evidence;
its disposable recorder, SAF probes and JNI/Zstd dependencies are not imported.

The new pure Kotlin `lmtr-recorder` module depends only on `lmtr-contract`. It is included
in host builds but **not linked into the Android app**. The separate `camera-recording`
module is linked only by tests. Product acquisition exposes an inert optional channel;
no product subscription, recorder button, automatic initialization or acquisition-owner
change is enabled.

| API | Ownership and meaning |
|---|---|
| `RecordingObservation` | Module-supplied stream/session/source/geometry/encodings/clocks, context, original sequence/device counter, receipt/acquisition/schedule, measurement availability/provenance and owned payloads. Recorder assigns genuine intake witness; it never substitutes receipt time. |
| `RecordingOffer` | Charged immutable source owner with deferred materialization on the existing preparation worker; callback performs no image copy/hash/codec/I/O. Rejected overload never freezes the retained owner. |
| `RecordingPayload` | Copies bytes while the module still owns its buffer. Copy-only access; lossless original Float32/mask/native/transport bytes, arbitrary source dimensions/encodings. Optional view offset requires independently supplied matching native bytes. |
| `FrozenJson` | Canonical owned JSON bytes; read access reparses independent values. Unknown semantic Decimal values survive. Binary Float32/Float64 evidence stays binary, not converted through JSON. |
| `RecordingLoss` | Qualified source loss with original ranges/count/evidence or explicit unknown reason; no FPS-derived counts. |
| `RecordingProfiles` | Explicit Analysis/Native/Full/visual resolution from supported/partial capabilities. Available temperatures and context are retained; visual cannot discard radiometry. Requested absent evidence requires explicit module availability/reason. |
| `RecorderLimits` | Required positive negotiated maximum observation size, independent byte queues, preferred size, control and memory reservations; known insufficient startup storage refuses, unknown capacity is not certified. |
| `RecorderEngine` | Two asynchronous workers, bounded ordered intake/writer queues and one file owner. Admission uses pre-owned data and does no image copy/hash/compression/I/O. |
| `RecorderBackend` | Append acknowledgment means body/footer sync returned; exclusive local file implementation and fault injection. No SAF, remote provider or publication/export guarantee. |

The wire-shaped metadata objects are intentionally explicit; frozen schema/closure validation
provides their grammar. Module adapters must supply facts rather than guess missing clocks,
calibration, source settings or buffer lifetime. The current engine accepts acquisition-mode
sampling and can preserve qualified schedule metadata; a production periodic sampler is
not implemented. A skipped known observation requires `admitLoss`, otherwise admission
refuses instead of inventing a gap. Slot purpose/inventory is immutable; an undeclared stream
is refused. Session/geometry/encoding/capability/policy/clock changes close the global segment
and start another; context-only changes get a new canonical context ID.

## Reused writer and actual engine behavior

`LmtrAppendSession` extracts the existing R3 append/index/checkpoint mechanics; the
synthetic `LmtrWriter.writePlan` uses the same component. Recipe generation stays in the
fixture writer. R4 assembles actual source payloads into role/encoding/dtype/byte-order
block groups and validates each CHUNK before any promised footer. Index fanout, rolling
frontier, body limits, codecs, hashes and terminal grammar remain the accepted R3 rules.
The shared writer owns its header, hides mutable journal/index state and becomes unusable
on any failed operation, including partial journal-validation failure.

- Default engineering queues are 4 MiB **each**, with one separately charged in-flight
  item per worker. The preferred CHUNK target is 4 MiB of conservatively charged pending
  ownership, including descriptors/context/segment and native evidence later encoded as
  views; decoded physical data can be smaller. It is not a camera geometry or universal
  file limit. No queue increases, crops or evidence downgrades on overload.
- Preparation and writer overload become ordered, known-count gaps. A reserved bounded
  journal carries losses and Stop independently of the data queues. Exhaustion stops intake;
  without enough loss evidence the engine preserves an incomplete file and cannot claim END.
  The unrepresented-loss flag is sticky across later failures/Stop. Unexpected worker
  exit marks abandoned items/queues; idle interruption and fully journaled errors
  remain honestly finalizable as failed END when no evidence is lost.
- Pending observations are distinct from `acknowledged`: only successful CHUNK-footer sync
  increases that counter, before subsequent index/checkpoint I/O. A later navigation failure
  preserves that actual acknowledgment while refusing finalization. A failed pending chunk does not become saved data.
- A pending chunk has at most 32 entries. First admitted prepared entry retains its original
  intake witness; +1 second causes sealing even with an idle source or late preparation.
  Later arrivals do not reset that deadline. Writer wakes are coalesced, not a growing timer queue.
- Checkpoints are required by 32 successful chunks or +5 seconds from the first uncovered
  successful CHUNK acknowledgment. No timer starts at admission; no empty idle checkpoint loop.
- Stop closes intake under the same lock as admission and captures a cutoff witness. First
  nonfailure termination reason wins; a resource failure can upgrade it. Admitted work drains,
  segment closes, then CHUNK/checkpoint/END commits, backend close and **read-only terminal
  reopening** occur. `RecorderResult.finalized` denotes completed finalization; `status` separately
  reports completed/interrupted/failed END. Reopen scope is `opened_records`, not a whole-file
  integrity certification. Independent full readback is a separate validation step.
- `awaitStop(timeoutMs)` bounds the caller's wait; timeout returns no completion. Queued work
  is finite, but a blocking filesystem operation has no wall-time guarantee. A timer/Stop cannot
  certify disk latency, directory persistence or physical power loss.
- Any uncertain write/sync, close or validation failure preserves the file, halts appends and
  publishes no successful finalization. It never appends a guessed repair END. Read-only R3
  recovery can verify the committed prefix with an explicitly unknown tail.

## Resources and present limitations

`maxObservationBytes` must be explicitly negotiated before Start, including metadata and
owned evidence; unknown/unbounded maxima are not an admissible configuration. Both queue
capacities must accommodate that negotiated maximum. The writer separately charges all
resolved prepared payload/descriptor/stream/context/facts bytes; policy/retention JSON can
grow, and insufficient writer capacity produces an explicit gap. Object/allocator overhead
is not a measured byte counter. A complete pending entry exceeding the preferred target
remains whole in an immediately sealed singleton. The local reservation estimates queued and in-flight
bytes, 12× the larger of preferred chunk size and negotiated observation maximum for assembly/codec/validation scratch and 16× the control
budget for closure/parsed-object headroom. These conservative engineering estimates **are
not a proof of ART heap/RSS**. Canonical JSON/record/decoded hard limits are enforced by R3.
The current local maximum owned observation is 32 MiB, separately from preferred target and the v1 hard ceiling. Observation stream/context/facts are additionally capped at 16 KiB; initial stream inventory
is capped at one quarter of the control budget. These are disclosed local refusals, not v1
schema changes. Control entries charge 32 KiB each with a maximum 1 MiB reserved journal.
A worker exception releases queued references; a backend failure retains the incomplete source.

The R2 4 MiB recommendation is justified only on tested HT-301/Pixel8 finite runs. Its
sampled memory peaks, baseline subtraction and backend-specific limitations remain intact.
No R4 physical-camera throughput/freshness, ART/RSS plateau, indefinite cadence or
storage durability has been measured; finite emulator runtime is documented below. Full-grid copies are safe but need real device cost measurements.

## Validation

Run without Android SDK/camera:

```bash
./gradlew -p tools/lmtr-r3-host :lmtr-contract:test :lmtr-recorder:test \
  :lmtr-contract:writeRuntimeClasspath :lmtr-contract:writeTestRuntimeClasspath \
  :lmtr-recorder:writeTestRuntimeClasspath :camera-recording:test \
  :camera-recording:writeTestRuntimeClasspath
python3 tools/check_lmtr_r4.py --report /tmp/lmtr-r4-interop.json
python3 tools/check_lmtr_conformance.py --report /tmp/lmtr-r3-interop.json
python3 -W error -m unittest discover -s tools/lmtr_v1/tests -t . -v
./gradlew :core:test :app:assembleDebug :app:lintDebug
git diff --check
```

39 recorder JVM, 17 source/lifecycle JVM, 71 R3 JVM, 232 existing core, 21 app JVM
and 64 Python tests pass with zero failures/skips; Android build/lint and ART packaging pass.
The current runtime/evidence receipt is [validation summary](validation-summary.json);
[original foundation summary](foundation-validation-summary.json) remains historical.
[Host summary](validation-summary.json), [R3 regression receipt](r3-regression-interop.json) (its old pending labels are tool checkpoint history), and [independent engine readback](interop-validation.json)
record exact coverage. Simulated non-USB modules cover temperature-only, preview-only,
independent native/acquisition, same-observation verified views, partial mask/+0 filler/valid
−0 Float32, dynamic context, alternate geometry/session and multiple slots. Latches/fake clocks
exercise idle/late preparation, 32-commit checkpoints, admission/Stop/timer races, both overloads,
loss-reserve exhaustion, source loss/detach, delayed/footer failures, low memory/storage,
malformed masks, torn tails, close failure and poisoned writer ownership.

The independent Python reader fully checks actual engine outputs, compares original source
payload hashes, context/session/stream/sequence, Kotlin full summaries and source immutability.
A torn output and a post-ack navigation failure are recovered with `full_verified_prefix` and unknown tail. These are disposable
R4 engineering files, **not new canonical R3 fixtures**. Fake-clock probe inputs are deterministic;
this finite suite is not exhaustive scheduling/model-checking or backend certification.

### Source/lifecycle and actual ART evidence

See [ART integration](ART_INTEGRATION.md) for the module channel, deferred HT-301
adapter, optional owner notifications, explicit Start/Stop, current runtime receipt
and retained failures. The channel is published after the existing gate and before UI
StateFlow, not from Celsius rendering. Old source epochs and late owner-open callbacks
cannot reattach after Close. Optional observer exceptions cannot suppress camera release.

[Source validation](SOURCE_VALIDATION.md) records finite 25 Hz non-USB source stress,
independent preview/palette/ROI cadence, queue peaks, actual chunk/checkpoint acknowledgments,
Stop, HT fixture copy cost and independent Python readback. These are host/emulator
engineering outputs, not new canonical fixtures or Pixel/HT throughput evidence.

Remaining R4 gates:

1. Opt-in physical HT-301/Pixel8 adapter and lifecycle smoke tests with an explicit Start;
   no normal recording button until relevant gates pass.
2. Matched-baseline live cadence, retained valid measurements, both recorder drop classes,
   source-loss qualification, preview freshness, chunk latency, queue peaks, clean-process
   memory and Stop/recovery under failures.
3. Independent full Python readback of physical Android source bits, masks/settings/context.
4. Recorder native Windows portability and applicable local backend tests, independently
   of already accepted R3 Windows conformance and isolated CI PR #15.

No hardware test occurred in this task; no physical-device ADB or camera was opened.
ART/emulator tests do not certify physical accuracy, live responsiveness or ART memory
reservations. R4 #9 and draft PR #16 stay open; R5/R6 have not started. SAF/provider export,
publication/directory durability and power-loss assurance remain separate backend duties.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**


The separate [field harness and operator protocol](FIELD_VALIDATION.md) prepares explicit physical validation without adding a production recorder. Phase A evidence is separate from the [conditional real-Pixel attempt](field-unattended-validation.json): explicit initialization succeeded but smoke admission was refused for Java headroom; no physical LMTR was recorded and the remaining field gates are pending.

[Memory follow-up](MEMORY_ADMISSION.md) reproduces the ready-state refusal while retaining all admission factors; [exact runtime receipts](memory-admission-validation.json) distinguish actual Pixel heap diagnostics from fixture/ART bytes. No physical LMTR is created.

[Pipeline allocation/reclaimability follow-up](LIVE_HEAP.md) removes proven source-side temporary copies with unchanged Float32 and entire fixture artifact bytes. Admission remains unchanged and physical acceptance pending; finite replay is not a live-camera/presenter guarantee.

[Targeted Celsius/ROI performance](PRESENTATION_PERFORMANCE.md) records exact parity, a rejected selector, single-owner pixels/private ROI computation and real Compose headroom witnesses. R4 physical recording admission remains unresolved.

## Incremental recorder follow-up

[Recorder allocation engineering](RECORDER_ALLOCATION.md) separates actual buffer lifetimes from logical admission. The previous checkpoint excluded physical device access. Resumed conditional authorization and the still-pending safe admission/physical gates are recorded there.
