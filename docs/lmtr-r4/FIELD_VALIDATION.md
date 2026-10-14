# R4 isolated physical-field preparation

**Phase A: prepared; conditional unattended physical validation is owner-authorized.**
Real Pixel 8 / HT-301 results must still be recorded separately from host/ART evidence.
[PR #16](https://github.com/LounaMaili/LMThermal/pull/16) remains draft and
[R4 #9](https://github.com/LounaMaili/LMThermal/issues/9) remains open. The accepted
[wire v1](../LMTR_WIRE_V1.md) and 51 pinned canonical fixtures are unchanged.
No Desktop, R5/R6/R7, production recording UI or extra acquisition owner is added.

## Executable route and boundaries

`lmtr-field` builds the separate **LMThermal R4 field test** APK, package
`org.lmthermal.field`, arm64-v8a/min26/target35. It reuses the product camera,
thermometry, Compose preview, palettes and ROI source files and the unchanged
native transport build. The product `app` has **no dependency** on recorder/field
modules. Its coordinator only gains an optional default-null lifetime injection;
HT observations additionally carry existing session-reason/gate-duration facts.
Neither change sends controls or changes initialization, parsing or thermometry.

```bash
./gradlew :lmtr-field:assembleDebug :lmtr-field:lintDebug
# After prerequisite checks under the owner authorization, through already-paired wireless ADB:
adb -s <paired-wireless-serial> install -r lmtr-field/build/outputs/apk/debug/lmtr-field-debug.apk
adb -s <paired-wireless-serial> shell am start -n org.lmthermal.field/org.lmthermal.field.FieldActivity
```

The launcher performs discovery only. **Connect/Open**, USB/camera authorization
and **Initialize radiometric** remain explicit existing actions; the owner authorizes their unattended execution only after all prerequisites pass. Opening,
Start, Stop, baseline collection and recorder timers never initialize the camera.
Close other camera apps first; do not run two USB camera applications simultaneously.
There is no USB-attach auto-launcher, service, destination picker, share route or
recording feature in the production APK. ADB visibility is not physical authorization.

`FieldSourceRoute` tees the existing authoritative single-consumer hub before UI
conflation; it never opens transport. Baseline observes this same route without an
engine. `CameraRecorderBinding` receives a guarded tee only after explicit Start.
Epoch/expected-source checks reject old captured callbacks and replaced sources.
Start during an unready session is allowed as an explicit diagnostic: current
unavailable observations retain truthful reasons/evidence; no last-good temperature
is substituted. Later readiness is still produced only by explicit initialization.

Rotation keeps the retained ViewModel/owner; Close, detach, ordinary background,
screen off, disposal and source failure cut off recording and drain off UI under
the existing lifetime policy. Stop test ends recording without closing acquisition.
No automatic reopen/resume or new initialization occurs. Delayed footer/close cannot
be certified by a timeout. A Stop timeout retains files/worker ownership, refuses a
new run and requires local inspection. A failed writer result is not permission to
reuse a source owner/oracle: both worker threads must exit. Each deferred offer binds
its original run clock/writer/storage counters so it cannot write into a later run. Failed/incomplete sources are never deleted,
repaired in place or resumed. Read-only full reopen follows Stop. Metrics-writer creation/append/close failure
requests an honest interrupted Stop; failed/missing readback blocks another longer
run until inspection. Baseline uses conservative Full storage preflight despite
writing no LMTR payload; this is an upper-bound admission choice.

## Mandatory clock audit

| Witness | Actual source | Meaning / qualification |
|---|---|---|
| HT receipt | `SystemClock.elapsedRealtimeNanos()` immediately after native read | Host receipt, **not** sensor capture or native callback arrival timestamp |
| Recorder intake, origin and timer ticks | Explicit same Android API in `FieldClock` | Same boot epoch and suspend-inclusive elapsed clock; no default `System.nanoTime` used by the physical engine |
| Source domain UUID | Existing per-camera-session receipt UUID | Retained unchanged in stream/receipt facts |
| Recorder domain UUID | Fresh per-field-run UUID | Explicitly correlated by identical Android API/boot semantics, not by UUID equality or guessed offset |
| Recorder timeline | Genuine serialized intake minus genuine HEADER origin | No source receipt is substituted; late original receipt may precede origin |
| Source ordinal | Existing authoritative-gate ordinal | Separate from host UVC callback sequence; neither is a device counter |
| Sensor timestamp/device sequence/UTC anchor | Unknown/not exposed | No sensor time, UTC accuracy, resolution or correlation manufactured |
| Finalization UTC | Existing engine unknown UTC record | No wall-clock label becomes an LMTR UTC anchor |

Android elapsed realtime includes deep sleep; timer scheduling may be delayed while
execution is suspended. On resume, due work remains due and uses original intake.
No inferred frames/gaps fill that time. Screen-off also triggers the existing source
cutoff policy. Source regressions/domain changes during active intake fail the run; new baseline
callbacks after cutoff do not relabel an already interrupted run. process recreation
creates a new run/file/domain, never appends to an old recording. Tests cover these
rules, source timing before origin, and fresh-process IDs. Synthetic host probes use
one explicit `System.nanoTime` domain; they do not establish a correlation between
that API and Android elapsed realtime. Actual API26/API35 ART exercises the Android
clock separately. No normative contract amendment is made.

## Conservative finite admission

MiB = 1048576. All profiles use codec 1 (existing RFC1950 DEFLATE encoder), **4 MiB
preferred chunks, 4 MiB preparation queue, 4 MiB writer queue**, 3 MiB negotiated
maximum HT observation, 1 MiB control reserve and 128 MiB declared memory budget.
The existing reservation formula totals **78 MiB (81,788,928 bytes)**, including
queues, two in-flight observations, encoding/validation scratch and control work.
This is logical admission accounting, **not** measured process heap/RSS or an
indefinite runtime bound. No capacity is increased and no role is downgraded.

Rates assume 25 delivered observations/s, incompressible retained bytes and 32 KiB
per-observation metadata/oracle allowance. Full counts the native plane once as an
exact transport view, not a second stored raw image. Headroom is **256 MiB** beyond
the run cap. Known insufficient free storage refuses Start; unavailable StatFs
capacity is reported unknown, not fabricated. Runtime file/oracle/trace cap failures
stop/preserve evidence; no compression ratio is promised.

| 146-second profile | Payload/frame | Estimated B/s | Run cap (includes 78 MiB reserve) | Required free incl. headroom |
|---|---:|---:|---:|---:|
| Analysis | 442368 | 11878400 | 1816035328 | 2084470784 |
| Native | 663552 | 17408000 | 2623356928 | 2891792384 |
| Full | 666624 | 17484800 | 2634569728 | 2903005184 |

The 20-second Full smoke cap is 431,484,928 bytes; required free 699,920,384 bytes.
Each run has a new UUID directory. Budget/storage/thermal conditions must be checked
again before each sustained run. Capacity can change after Start. Successful local
footer sync does not certify physical power-loss durability, directory persistence,
uninstall survival, SAF or remote publication. No such backend is implemented here.

## Metrics and current limitations

Private `samples.jsonl` streams one bounded sample/s; fixed distributions retain
at most 8192 values and explicitly count omitted values. Long profiles reset
measurement aggregates after the actual first sample at/after 20 seconds; the marker
records actual warmup elapsed time. Files/source oracles retain **all** observations,
including warmup. Acquisition/preview aggregates close at the Stop request, before heavy terminal
readback/hashing; codec/seal/drain timing remains active for finalization.
Counters/snapshots labeled whole-run must not be compared as if
restricted to the measured approximately 126-second interval.

- Native host callback received/replaced/malformed statistics and reported callback
  FPS, separately from authoritative-gate interval/FPS and valid/unavailable/held/
  invalid observations. Session `held_image` reason is retained, never inferred from
  apparent identical pixels. Native callback **per-arrival interval distribution
  is not exposed by the current transport**; gate intervals are not relabeled UVC.
- Preview publication FPS and collection-age p50/p95/max, separately from acquisition.
  These are receipt-to-publication/collection estimates, not display scanout or
  optical latency. Presentation remains conflated and is not recording authority.
- Admission/acknowledged/pending and preparation/writer losses, source loss reasons
  and unknown counts; queue/in-flight/pending snapshot/peaks unchanged.
- CHUNK encode+validate time, footer append/sync time, total seal time, body sizes and
  checkpoint intervals. Separate per-block **codec-call wall time and stored/decoded
  ratio** use optional engine observers. Observer exceptions cannot poison a commit;
  regression proves the observer path emits byte-identical files.
- Acquisition-thread CPU elapsed counters and recorder-thread `/proc` CPU ticks,
  with OS scale not guessed. Thread disappearance/sampling gaps remain limitations;
  these counters do not isolate parser/thermometry CPU. Gate wall time is separate.
- Sampled Java used heap, native allocated heap, RSS/PSS peaks, first/last RSS,
  sample span, GC runtime counters and available thermal-zone reading. Missing OS
  facts stay unknown. Logical reservations and sampled process memory are separate;
  endpoint trend is not proof of a plateau or an indefinite bound.
- Explicit Stop request window, drain/engine terminal reopen, backend close and full
  read-only reopen timing/classification. On earlier source failure, the later Stop
  request window is not mislabeled the original source cutoff. Private full reports
  retain terminal/recovery/error facts; completion alone is not acceptance.

R2 supplies a **historical matched comparison**, not automatic acceptance:
roughly 25 FPS, zero preparation/writer losses in selected finite configurations;
4 MiB DEFLATE Analysis/Native/Full preview p95 approximately 109/109/117 ms and seal
p95 approximately 75/79/93 ms, 20 s warmup +126 s measured. The new field route
includes a pre-transform hashing oracle, different writer/metadata and measurement
instrumentation. Report actual deltas/conditions; investigate cadence, freshness,
queue/memory trend or role-integrity regression rather than converting proximity
into a pass. Missing precise callback intervals/thread/thermal facts are explicit
remaining instrumentation limitations, not invented measurements.

## Independent bytes, privacy and extraction

Each explicit run lives in app-private `files/r4-field/<run UUID>/`:
`capture.lmtr` (except baseline), `oracle.jsonl`, preflight, measured-window marker,
private samples/report and sorted SHA-256/byte inventory. Private wall-clock labels
are unqualified local naming evidence, not normative timestamps. Originals survive
failed tests/Stop; no auto-delete or automatic GitHub upload is implemented. The
app disables backup/device transfer. Keep originals on Fedora outside Git, preferably
under ignored `.local-tools/validation/`, before uninstalling the field APK.

The oracle hashes **original owner** Float32 raw bits, existing validity masks,
unmasked native image bytes and original transport before invoking adapter.freeze.
It does not call RecordingProfiles/LMTR encode or thermometry. It retains same-source
session/ordinal/host callback/receipt facts, independent native/transport slice
comparison and genuine pre-offer/pre-materialization intake bounds. Its bounded
hashing/JSONL work runs on the charged preparation worker, and is part of measured
field cost. Rejected preparation offers do not materialize; explicit overload gaps
must account for them. Writer losses may have oracle rows but no payload, and are
checked as represented loss. Recovered-prefix parity does not certify pending tail.

Calibration/context values are cross-checked against adapter metadata, **not an
independent reconstruction of calibration interpretation**. They are retained for
review; binary parity must not be generalized to independent thermometry correctness.
This oracle cannot prove every pre-native-buffer callback was delivered: module loss
signals and native counters retain their own authority/unknown boundaries.

After authorized Stop (or preserve incomplete output after failure):

```bash
python3 tools/pull_lmtr_r4_field.py --operator-authorized \
  --serial <paired-wireless-serial> --run-id <UUID> \
  --destination <new-private-Fedora-directory>
python3 tools/check_lmtr_r4_field.py <new-private-Fedora-directory> \
  --report <new-private-Fedora-directory>/python-readback.json
# Add --recover for a preserved incomplete file; never rewrite source bytes.
```

Extraction uses fixed filenames/validated UUID, explicit serial, `run-as`, streaming
64 KiB reads, a 3 GiB aggregate refusal cap and exclusive new destination. It neither
pairs nor opens USB. Completed device inventory is compared with copied bytes; its
absence means incomplete evidence, not success. Stop first so files are immutable.
Unfinished oracle lines/uncertain Stop require diagnosis, not implicit repair.

Python independently validates framing/integrity/indexes/END or recovery prefix,
original Float32/mask/raw/transport hashes and lengths, views, profile roles,
module/session/stream/source sequences, clocks and intake bounds, unavailable
observations, adapter context transitions and represented writer gaps. It hashes
recording bytes before/after. Full arrays/files/oracles are never loaded wholesale.
Only deliberately sanitized aggregate numerical verdicts/run IDs/hashes may be
published; raw files, per-frame JSONL, identifiers, USB paths and private timestamps
must stay local. Do not upload the entire private report or trace.

## Camera-free Phase A verification

[Phase A host receipt](field-preparation-validation.json) records 398 JVM and 64+8
Python tests, three original-owner profile readbacks, build/lint and unchanged corpus
checks. [Earlier host readbacks](field-host-initial-readbacks.json) remain separate.
The [executable preflight output](field-preflight-plan.json) uses unknown capacity;
it does not query or assert available Pixel storage. [ART checkpoints](field-art-checkpoints.json)
preserve the passing 79/80-test runs and failed API26 run; final current-code execution is [run 38453472955](https://github.com/LounaMaili/LMThermal/actions/runs/38453472955),
**80 tests per API26/API35** plus all independent readbacks. The tested code is
`f90db853e9eee854eb8c562d09c4f4592c663db5`; the arm64 field UI itself remains physically unexecuted.

`lmtr-field-support` is a JVM test-only helper module shared by the field APK and
isolated ART suite. Regressions cover no Start on Connect, no camera actions from
Start/Stop, unready→available observations, Stop during continuing acquisition,
every lifetime cutoff, retained rotation, captured old callbacks, resource refusal,
clock domains/regressions/suspend advances/process identities, fixed metric bounds,
actual `ht301` registry ID and byte-identical optional timing observers and writer failure while a deferred owner
still unwinds. The latter prevents premature run/resource reuse.

Existing 39 engine +17 bridge regressions additionally cover delayed footer sync,
source/writer/storage/error/Stop races, memory pressure, same-device owner replacement,
sticky unrepresented loss, no-END prefixes and read-only recovery. Their existing
assertions are retained. Field-specific existing-room outputs exercise all three
profiles, unavailable observations and Full views; eight Python hostile-oracle tests
reject incorrect original hashes/sequences/clocks/intake/view evidence and validate
unchanged damaged-END prefix recovery. These are finite host/ART tests, not physical
field behavior or a production UI lifecycle certification.

```bash
./gradlew :core:test :lmtr-contract:test :lmtr-recorder:test :camera-recording:test \
  :lmtr-field-support:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug \
  :lmtr-field:assembleDebug :lmtr-field:lintDebug
./gradlew -p tools/lmtr-r3-host :lmtr-field-support:writeTestRuntimeClasspath
java -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.FieldPreflightCli unknown
java -Xmx256m -cp "$(cat lmtr-field-support/build/test-runtime-classpath.txt)" \
  org.lmthermal.field.FieldOracleProbe <new-private-engineering-output-directory>
# Per profile: check_lmtr_r4_field.py as above.
LMTR_FIELD_TEST_ROOT=<engineering-output-directory> \
  python3 -m unittest discover -s tools -p test_lmtr_r4_field.py -v
```

The existing camera-disabled API26/API35 GitHub ART route remains emulator-only,
qemu-guarded and separate from the arm64 native field APK. It adds the actual Android
elapsedRealtime witness test and the same helper regressions/three source-oracle
readbacks. A built instrumentation APK is not a runtime pass; receipts separately
identify executed exact-SHA jobs. No physical ADB/Pixel/camera was used in Phase A.

## Conditional unattended Phase B authorization

The owner explicitly authorized non-destructive unattended validation on 2026-10-14.
No separate routine go-ahead is required. Before each run verify online paired Pixel 8,
attached HT-301, existing CAMERA/USB grants, unlocked foreground, explicitly authorized
non-sensitive scene, battery/storage/thermal health, compatible clocks and admissible
memory. Missing prerequisites mean skip physical work and continue Phase A; never pair,
bypass a lock, synthesize a permission grant or wait indefinitely for the owner.

The owner may grant normal permissions before becoming unavailable. Installation is not
a permission grant; the owner confirmed normal Connect/permissions and the authorized
scene for this session. The actual execution receipt remains the authority for results.

The dedicated Activity applies `FLAG_KEEP_SCREEN_ON` only while a bounded test runs,
clears it on Stop/refusal/finalization/disposal/onStop, and changes no device setting.
App-private `files/r4-admission/<UUID>.json` retains actual sampled health/heap and plan
before admission, including refusals that create no LMTR. It is not a completed-run receipt;
the private extraction tool for completed runs does not implicitly include these files.
One-second read-only probes stop on battery <30%, Android thermal status >=SEVERE (3),
free storage <256 MiB, Android low-memory, Java/system headroom <32 MiB or sampled RSS
increment >128 MiB from admission. Start additionally requires 78 MiB logical reservation
plus that 32 MiB headroom, known RSS/storage/thermal/battery, current permission/unlock
and a currently valid ready measurement. API26 lacks authoritative thermal status and
therefore cannot authorize unattended physical tests through this policy.

Three seconds without callbacks or valid measurements, regressed/future clock witnesses,
five consecutive approximately one-second samples below half nominal callback cadence,
or queue/in-flight/control/pending bounds stop and preserve the run. Pending scratch is
bounded by preferred chunk + negotiated maximum observation; it includes entry metadata.
These conservative refusal thresholds are not performance acceptance thresholds or proof
of an infinite memory bound. A safety stop blocks further runs until diagnosis. Existing
source/lifecycle/clock and independent original-role checks remain necessary; sampling
cannot certify every instantaneous resource excursion or anticipate a blocking sync.

Bounded sequence (never automatically chain a failed run):

1. Install **the exact reviewed field debug APK** above, not a production recorder.
   Close other camera apps. Existing wireless pairing only; camera USB occupies the
   port. New package may request normal CAMERA and USB access after explicit Connect.
2. Originals stay app-private then in a new private Fedora directory. No automatic
   publication. Allow roughly **2.9 GB free per sustained run** by conservative
   preflight; check actual remaining free storage before each profile. Retaining
   three uncompressed upper-bound runs plus baseline/smoke could consume about 8 GB;
   actual compression is unknown until measured. Pull/preserve files before uninstall.
3. Explicitly Connect/Open and inspect a usable display/unsettled preview. Initialize
   radiometric once when normal session logic permits/requires it. Do not replug or
   resend controls simply for recording. An unsettled reopen may require a normal
   documented fresh display start; coordinate any needed physical reconnect.
4. **Smoke 20s first.** Stop automatically or tap Stop test. Copy/review original
   bytes, clocks, roles, preflight, counters and recovery. Do not proceed if wrong.
5. Then **Baseline 146s without recorder**, Analysis 146s, Native 146s and Full 146s,
   each including actual 20s warmup then ~126s measured. Same scene/light movement,
   screen/power/storage/thermal conditions as practical. Wait for Stop/read-only check
   and inspect each run before the next; do not auto-chain a failing configuration.
6. Bounded additional short runs: Stop/repeated Stop while camera continues; rotate; ordinary
   Home/background; explicit Close and rapid same-device reopen. Check exactly one
   owner/release, current unavailable values and no implicit initialization. No
   unbounded service or background acquisition is tested.
7. No physical disconnect, power cycle, wiring/USB fault injection or destructive
   filesystem/power-loss tests. Software interruption/recovery only where supported.
8. **Safe stop:** tap Stop test, wait for checked status; Close releases camera.
   Timeout/failure: keep originals/app state, diagnose/pull privately, do not claim
   saved pending frames or erase incomplete files. If app/process dies, preserve
   app-owned files and inspect read-only after explicit operator action.

Do not infer physical results or source parity from preparation or device availability.
Actual sampled device preflight facts and run results require separate receipts. Remaining R4 gates are actual authorized HT-301/Pixel smoke,
matched profile cadence/freshness/process memory/lifecycle/recovery and review of
instrumentation limitations. Optional backend/Windows recorder tests remain separately
scoped; R6 export and physical power-loss assurances are not borrowed from R3/R2.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
