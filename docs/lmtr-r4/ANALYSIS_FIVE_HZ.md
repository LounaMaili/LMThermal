# Experimental R4 Analysis at explicitly chosen 5 Hz

**Candidate implementation, not physical/ART acceptance or production recording UI.**
This work follows the [offline study](TEMPORAL_RECORDING_STUDY.md) pinned to
`42294567307884b5e27c79077fab2acaad8d50db`. Its historical receipts/prototypes remain
evidence for that commit. The [frozen LMTR v1 contract](../LMTR_WIRE_V1.md), canonical
corpus and earlier physical failures are unchanged. Analysis remains the accepted
product default; this candidate requires an explicit profile and sampling choice.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Profile-aware immutable preparation

`Ht301RecordingAdapter(identity, preparationProfile)` now requires the explicit
profile before creating offers. One fact/context builder serves Analysis/Native/Full.
Analysis never creates raw/native/transport owners merely to discard them later;
the full original Float32 plane, current availability/reasons, context/settings and
native-equivalent provenance remain. Native retains its requested raw plane; Full
retains its exact original raw and transport/view evidence. No automatic fallback
or new mask/temperature is manufactured. Existing HT gate rejection is preserved;
nonfinite settings do not become a usable plane. Generic original masks, all-invalid
planes and signed Float32 bits remain supported by the common path. An all-valid
nonfinite plane is refused, not normalized into a plausible measurement.

The former host-only `OfflineProfileAdapter` copy is removed. Historical eager
performance comparisons use the pinned old runtime, not a bypass of current profile
checks. The common binding refuses a mismatched profile before subscribing. Its
guarded `prepareAdapter` can choose a same-source immutable adapter only before Start,
without opening/closing a camera or changing an active/draining reservation. A failed result
can precede preparation-worker exit; the binding retains the reservation/adapter until
both workers actually exit. A blocked-preparation/failed-writer regression verifies this
release gate, independently of future completion.

## Explicit clock and counter contract at the intake boundary

`FiveHertzSampling(startMonotonicNs)` fixes the period to **200000000 ns**. It is
opt-in on `RecorderEngine`; acquisition-following behavior remains the default API
path. The experimental periodic implementation admits one stream, with a reliable
host-monotonic receipt domain identical to HEADER's ns clock. A matching UUID is an
explicit caller assertion backed by the actual clock API, never an inferred mapping
between two clocks. Unknown mapping/future receipt/regression is refused. For the
field candidate both readings are Android `elapsedRealtimeNanos`; nanoTime and
sensor exposure timestamps must not be substituted. Units do not prove precision.

`RecordingSourceWitness` contains only bounded original clock/receipt/callback/
availability scalars. Deferred materialization verifies them against source facts.
All received raw ordinals must remain contiguous, including intentionally unselected
callbacks; the first actual ordinal is arbitrary. Selected observations receive a
separate dense counter. The optional `org.lmthermal.recorder.periodic-v1` extension
declares this domain and preserves the original acquisition ordinal, host callback
sequence, original receipt and source-reported availability. Device sequence remains
unknown when unavailable. No sensor sequence is inferred from 25 FPS.

`acquired_callbacks` means callbacks received by the active recorder intake, not all
camera callbacks since connection. Field camera statistics can include callbacks
before recorder subscription and must be reported separately. Sampling does not
change acquisition, thermometry, normal presentation or source-module readiness.

## Windows, detection, Stop and source interruption

Let S be the actual policy Start and P=200000000 ns. Slot k opens at S+kP and covers
**[S+kP, S+(k+1)P)**. The first callback admitted in that window whose original
receipt is at/after its opening is selected. Other callbacks are intentionally not
selected. An old receipt delivered late cannot fill the new window; its nonselection
is counted separately. A selected unavailable measurement is an Observation with
its exact reason and no temperature, not a missing-event Gap.

Closed empty windows become `missed_schedule`, with exact slot/deadline ranges and
the actual detection/intake witness. The planned deadline never replaces receipt
or intake. One coalesced range represents all newly closed empty windows; no catch-up
matrix, interpolation, last-good image or FPS-based source loss is created.

Both existing workers' idle wakeups can detect windows independently of callbacks;
admission and Stop also advance the same ledger under the existing intake lock.
There is no additional timer thread, stack or timer-message queue. Detection is a
soft scheduling operation: if both workers are busy, detection can be late, with
its actual witness retained. Backend sync is not made durable by a timer. A blocked
writer does not prevent the intake/Stop path from accounting elapsed windows.

Stop is serialized with admission. Windows opening strictly before cutoff T are
closed, including a partially elapsed final window. A slot opening exactly at T is
excluded **unless an admission at that same clock reading was already ordered before
Stop**. Stop never retracts such an admission. Ties retain actual journal order.

| Example | Accounted windows |
|---|---|
| Stop at S, no admission | zero; a closure-only segment preserves the zero-count policy |
| Stop at S+P, no boundary admission | slot 0 only |
| Admission at S+P ordered before Stop at S+P | slots 0 and 1 |
| Stop at S+1.1 s | slots 0..5; empty slot 5 is explicitly missed at Stop |
| Ten seconds with no boundary admission | slots 0..49; not an invented slot 50 |

Detach/background/source replacement closes the old recording explicitly; there is
no automatic source/profile/clock restart. A source loss signal remains independent
from scheduled windows and does not acquire a guessed camera-frame count. Rotation
retains the existing source/recording when the existing UI lifetime policy retains
it; stale callbacks cannot enter a successor recording. Ordinary background retains
the explicit-reconnect policy.

## Losses, acknowledgements and closure evidence

Selected-slot witnesses survive both preparation and writer overload as scalar-only
owners. The original materializer/source arrays are not retained by a loss notice.
Each such gap preserves its selected counter, original ordinal, slot, planned deadline
and source-reported availability; it contains no temperature/measurement payload.
Deferred source-evidence failures are distinct from ordinary idle windows. Unknown
or unjournaled delivered loss poisons END. Clock regression/unavailability cannot be
clamped into a successful periodic timeline; the verified prefix remains recoverable.

For a successfully closed producer ledger:

```text
qualified acquired callbacks = selected + intentionally not selected
planned slots = selected + missed scheduled slots
selected = stored observations + preparation losses + writer losses
           + any separately journaled invalid-evidence losses
stored observations = available planes + observations with absent measurements
```

Full planes with no valid pixels are available planes with a mask, not readable
temperatures. Source-reported availability is not proof of a valid committed plane;
both complete validations still have to pass. Explicit source-loss counts use a
different evidence basis and must not be added to requested slots to infer FPS.

The segment-end extension stores cumulative producer decisions, including initial/
terminal nonselection and the actual cutoff. It deliberately does **not** claim that
unsynced final pending observations are durable ACKs. ACK counters advance only in
the existing successful CHUNK-footer callback; the public periodic snapshot separates
available/unavailable ACKs and acknowledged missed/preparation/writer gaps. END's
validated summary is the durable aggregate after drain and final sync.

A clock fault after the last footer preserves that actual ACK but refuses END.
A committed producer closure followed by missing END is still an incomplete file
with unknown tail. Neither a producer flag, a JUnit pass nor a readable prefix is
promoted to completed recording or no-loss throughput acceptance. The first bounded
periodic failure reason remains latched independently of the engine's terminal reason.

## Resource and implementation bounds

The existing **4 MiB preparation queue, 4 MiB writer queue, 1 MiB control journal,
3 MiB maximum HT offer, 4 MiB preferred chunk and 78 MiB reservation plus 32 MiB
headroom** remain. The 32-control-notice exhaustion boundary is tested without
raising capacity. Both independent complete CHUNK validations, durable footer ACK,
Stop/drain, corruption/recovery and refusal of END after unrepresented loss remain.
No forced GC, hidden presentation, source-cadence adaptation or reduced Full evidence.

Periodic state retains bounded scalar counters and two canonical stream byte owners,
not a source/latest-frame cache or an AST history. Original stream bytes are capped
at 8192, annotated stream bytes at 16384; scalar objects have a 4 KiB allowance and
terminal JSON a 4 KiB bound. Their charge **plus** the engine's configured stream
definitions must fit the existing static stream/termination allowance of one-quarter
of control bytes. This uses neither an extra queue nor an increased reservation.
Per-offer witnesses remain inside existing charged source/control allowances; final
entry metadata is charged and checked by the normal encoder/hard limits.

These are conservative logical bounds, not measured ART/RSS ceilings. Source arrays,
the source's 25 Hz thermometry and normal Compose/graphics/native owners still exist.
Lower cumulative Java allocations do not establish an equal RSS reduction.

## Offline qualification and reproducibility

The current [machine-readable receipt](analysis-five-hz-validation.json) pins exact
source/test evidence. Kotlin deterministic tests cover ten virtual seconds at 25 Hz,
availability/masks/nonfinite refusal, idle/late callbacks, Stop boundaries, timer/
admission races, blocked preparation/backend, original control exhaustion, source
replacement/lifetime, incomplete END recovery and allocation/clock failures. Existing
corruption, independent-validation and canonical-corpus tests remain enabled.

`AnalysisFiveHzInteropProbe` emits 24 noncanonical cases (both STORED/DEFLATE-1),
original-owner JSONL and offered-event oracles. Inputs are explicitly simulated
owners from unchanged sanitized fixtures, plus explicit synthetic 3×2 mask/refusal
vectors. Virtual seconds qualify journal semantics, not real elapsed throughput.
`tools/check_lmtr_analysis_five_hz.py` independently verifies all represented source
hashes, exact slots/ordinals/clocks, producer/ACK counts and unknown tails, then uses
the existing Desktop reader for full-plane point/ROI parity. Its private scalar CSV
is engineering verification output, not a new product/export contract.

```bash
./gradlew --offline -p tools/lmtr-r3-host :core:test :lmtr-contract:test \
  :lmtr-recorder:test :camera-recording:test :lmtr-field-support:test \
  :lmtr-field-support:writeTestRuntimeClasspath
java -Xms64m -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.AnalysisFiveHzInteropProbe "$NEW_PRIVATE_OUTPUT" "$SANITIZED_WARM_FIXTURE"
"$DESKTOP_PYTHON" tools/check_lmtr_analysis_five_hz.py "$NEW_PRIVATE_OUTPUT" \
  --desktop "$PINNED_DESKTOP" --output "$NEW_PRIVATE_READBACK"
```

Every output must be a new private destination. Preserve failed cases and input
hashes. Controlled host comparisons use pinned frozen runtimes, fixed source/heap/
order and no concurrent benchmark load. No code-only or physical throughput claim
is inferred by comparing Analysis 5 Hz with Full 25 Hz: both profile and selection
policy intentionally differ. Full acquisition-following file parity is a separate
exact-byte check against the old reference.

The final offline gates passed: **504 JVM test executions** (234 core, 74 contract,
74 recorder, 52 camera-recording and 70 field-support; shared regressions execute in
multiple modules), **88 Python tests** (64 reference, eight historical sampling,
eight field-oracle and eight periodic-field negative tests), and **272 Desktop Linux
tests**, including eight new timing checks. Both APKs build and lint offline with
zero errors (five app/six field warnings retained). Independent checks retain 51
canonical members, 33 plan-byte rewrites, 43 JSON vectors, 29 corruption combinations,
three cancellation probes, 14 original R4 cases, 24 new periodic cases and 28
comparison files. These are separate scopes, not an inflated unique-test total.

## Controlled HotSpot results and costs

The predeclared fresh-process ABBA comparison used JDK 17.0.20.1, `-Xms64m
-Xmx256m`, four warmup samples then eight samples of six preparations per profile.
The old arms use the unchanged frozen `4229456` runtime; candidate arms use the
current frozen runtime. No concurrent benchmark/test load or forced GC was used.
All six profile/codec container bytes match across all four arms (including Full).
These finite examples do not prove arbitrary-source equivalence or physical speed.

| Profile | Old allocated bytes/preparation | Candidate bytes/preparation | Change | Old/candidate wall time, ms |
|---|---:|---:|---:|---:|
| Analysis | 2,197,024 | 1,307,168 | -40.5% | 1.369 / 1.320 |
| Native | 2,207,480 | 1,787,512 | -19.0% | 0.979 / 0.997 |
| Full | 2,207,496 | approximately 2,246,605 | +1.8% | 0.919 / 1.112 |

Values are medians of the two corresponding process medians, per observation.
**Full has a measured preparation cost increase**, including about 21% wall time
in this narrow host experiment; no claim of Full performance improvement follows.
The new source-witness verification decodes immutable facts; its separate cost was
not isolated, and JIT/GC/process variation remains. Full's bytes and selected facts
match, while current Full physical throughput remains unqualified. Analysis's
allocation reduction is cumulative caller-thread preparation work, not retained
heap, process RSS or an Android admission guarantee.

A separate single finite pair kept 50 producer-only baseline callbacks and then
200 source callbacks at requested 25 Hz per codec, with independent source owners
and the same fixture/JDK/heap. The old Full run saved 200/200; the explicit Analysis
5 Hz candidate selected/saved 40/200, with 160 intentional nonselections, **zero
preparation/writer losses and zero missed slots**, for both codecs. The source
window lasted approximately 7.962 s; 40 planned slots closed truthfully at Stop.
This is separate from the ten-second virtual qualification. Original source-interval
p95 was 40.71/41.09 ms (Full STORED/DEFLATE), versus 40.64/40.57 ms for Analysis;
producer-only baseline medians were approximately 40 ms. No device cadence is inferred.

Writer queue peaks were 3,577,063 / 1,788,534 bytes for the old Full pair and
449,065 / 449,063 for Analysis. Chunks decreased from 50 to eight per codec;
Stop/drain was 145/167 ms versus 57/53 ms. Occupied-heap sampled peaks were
105.2/111.8 MB versus 48.5/54.8 MB. These are **occupied heap at samples, not live
retained memory or RSS**; natural GC timing, profile and recording policy all change.
This pair is not a code-only optimization benchmark or physical sustained-load gate.
The receipt retains exact queue, seal/append, memory/GC, interval and source-hash data.

The bounded `--field` readback mode also verifies the materialized-owner oracle
without confusing dense selected counters with original acquisition ordinals. It
checks actual receipt/intake, slots, exact Float32/context/unavailability, represented
writer loss and complete/recovered scope. That oracle does not observe unselected
callbacks: its cumulative nonselection values remain producer evidence. Only the
virtual offered-event checker independently reconstructs every selection decision.

```bash
# Future authorized field output, or the finite real-paced synthetic candidate:
python3 tools/check_lmtr_analysis_five_hz.py "$PRIVATE_FIELD_RUN" --field \
  --output "$NEW_PRIVATE_FIELD_READBACK"  # add --recover only for an incomplete file
LMTR_PERIODIC_TEST_ROOT="$VIRTUAL_CASE_ROOT" \
  python3 tools/test_lmtr_analysis_five_hz.py -v
```

## Desktop visibility and temporal-analysis proposal

The existing reader already preserves new periodic files; it needs no wire/parser
change. The isolated companion presentation shows requested policy, original intake/
receipt, slot/range, absence and gap category, with indexed stored-plane counts
distinct from observation records. It keeps recovery/unknown tail and verification
scope visible. Native coordinates, masks, payloads, point/ROI arithmetic and timelines
are unchanged. Displayed ns digits are original integers, not sensor-precision proof.

A future explicit analysis export should be a derived sidecar, outside the protected
source. Proposed rows retain source SHA/recording ID/entry ID, native point/half-open
ROI geometry, original clock IDs/ticks/quality and actual intake, requested period/
slot/deadline, kind/status/reason/count, mask-valid/total counts and available point/
ROI min/max/mean. Segment events and unknown source losses stay explicit. Gaps,
unavailable/no-valid pixels and unknown tails have empty readings, never interpolated
values or fabricated UTC. Preserve units, algorithm/statistics method and physical-
accuracy warning. This is an owner-review proposal, not R7, a legacy converter or
a new LMTR mandatory schema; no product export UI/backend is implemented here.

## Prepared first physical ten-second protocol — NOT executed

No Pixel/HT-301/ADB/emulator/Actions is authorized in this software campaign.
After a new explicit availability confirmation **and specific test authorization**:

1. Pin this candidate's source SHA, the separately built field APK SHA-256 and the
   Desktop reader SHA. Build/install only the separate `org.lmthermal.field` APK;
   production `org.lmthermal.app` still has no recorder. Do not uninstall/reset data.
2. Use the existing authorized wireless connection and ordinary camera permissions.
   Normal Connect remains read-only; supported radiometric initialization is explicit.
   Require current frame-backed ready state and normal Compose preview, a nonsensitive
   scene, known battery/thermal/storage/Java/system/RSS facts and unchanged safety gates.
3. Choose the separate **Analysis 5 Hz /10s (test)** action exactly once. It selects
   the profile-aware adapter before admission, uses the actual shared elapsed-realtime
   clock domain and enables the fixed periodic policy. Other field actions remain
   acquisition-following; do not substitute the old Full smoke or assume a fallback.
   Storage estimates remain conservatively based on 25 source FPS; no admission margin
   is reduced for 5 Hz. The existing RSS guard and bounded keep-screen-on stay in use.
4. Preserve every admission witness, actual requested/cutoff/drain time, original
   file/oracle, periodic producer snapshot, source/camera counts, frame availability,
   losses, synced ACKs, heap/native/graphics/RSS/GC observations, queues, terminal
   reason and immutable source hashes. Expect roughly 50 requested slots, **not** a
   promise of 50 available saved planes. Stop normally or honor the existing safety stop;
   no second Start or favorable retry is implied.
5. Independently check Python and Desktop, all source/slot identities, Float32/mask/
   context parity and point/ROI chronology, file integrity and unknown-tail behavior.
   Use the bounded `--field` readback above for periodic field owners; the older
   acquisition-following checker deliberately expects original wire counters.
   Explain RSS trajectory and losses before proposing 30–60 s. Ten seconds alone is
   not proof of steady memory, indefinitely sustained recording or Full 25 reliability.

The software candidate/field action still needs real Android/ART and physical
qualification. No phone discovery, camera access, install, initialization, physical
recording, hosted CI, release or main/PR merge occurred as part of this campaign.

## Subsequent physical follow-up — separate scope

The [first authorized physical result](ANALYSIS_FIVE_HZ_PHYSICAL.md) uses this exact
implementation at `d1b8c8917961a162270e58510c1eeb39baa94f04`. One ten-second request
completed at 10.644236008 s with 54 selected/saved measurements and no represented
losses. Its unmodified guards, timing overshoot, sampled-memory scope and unobserved
operator preview are recorded separately. It does not turn this earlier offline
campaign into ART/physical evidence, complete R4, or authorize 30/60 seconds.
