# Single physical Analysis 5 Hz / 60-second request — RSS interruption

## Result and exact provenance

**The 60-second duration was not qualified.** One authorized Start requested Analysis,
5 Hz, DEFLATE-1 and 60 seconds. The unchanged sampled RSS protection stopped intake
at **3.279581138 s**. All **17 opened slots / 17 selected observations** were eventually
acknowledged, with no missed slots, preparation/writer/source losses or unavailable
measurements. Full Kotlin, independent Python and Linux Desktop integrity checks pass
on a valid file with **END `interrupted`**, not `completed`.

The tested Android code is `c8d5d5cc1c5154ca3cbf62ab4ad6322d0eb8f23f` on
`codex/r4-analysis-five-hz-60s`. The separate `org.lmthermal.field` APK is 11751802
bytes, SHA-256 `9275b53e7fb8c8302ffa055be1bce92529e7c51c1b94ed89797e8f8a3512b0aa`.
The already installed APK had the same signer; `install -r` preserved data, and
the installed bytes pulled back from Android match exactly. Production Android
code/application, camera protocol, thermometry and LMTR v1 were not changed.

The unmodified Desktop reference is `26b1785226b15dc17f977d36c2ca42eb79feeac7`.
The [software preparation](ANALYSIS_FIVE_HZ_60S.md) and its
[receipt](analysis-five-hz-60s-preparation.json) describe the optional genuine-witness
deadline and passed host gates. The [physical receipt](analysis-five-hz-60s-physical-validation.json)
retains exact counters, admission, interval scopes, reader results and original hashes.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Fresh authorization, explicit initialization and operator gates

The owner freshly confirmed Pixel 8/HT-301 availability and a non-sensitive scene
before any ADB operation. The existing paired wireless connection worked directly;
no pairing reset, ADB daemon restart, security bypass or other-application operation
was needed. The device was Pixel 8, Android 17/API 37; Android enumerated Infiray
T3-317-13, VID `1514`, PID `0001`.

The first read-only connection was already raw14/unsettled, approximately 25 FPS,
with words around 5085–5885. It was not promoted to READY. Normal Close released
that owner, and the operator performed the specifically requested physical replug.
The next normal Connect produced DISPLAY frames: exactly 224256 bytes, `0x80YY`
words, approximately 25 FPS. One explicit normal initialization then ran the
validated 32772 → 32800 → 32768 sequence, 75 shutter-discard frames and five live
frames. Current frames and UI confirmed RADIOMETRIC_READY and available thermometry.
Initialization counters, including held/malformed transition frames, are outside
the recording window and must not be relabeled recording losses.

Before Start the operator was explicitly invited to watch the Pixel, move a hand
or warm object and check image/temperatures/UI reaction. The reply explicitly
confirmed a usable preview and authorized Start 60 seconds with continued observation.
A second message warned immediately before the single Start. There was one input
attempt and exactly one new recording directory; no other field action or retry.

Afterward the operator confirmed that the **entire short beginning was observed
and the preview/UI were responsive**. This applies to the approximately 3.28-second
recording, not a minute of recording. It is a human observation, not an optical
latency measurement. Normal Inferno/Auto Compose and thermometry remained enabled;
there was no GC request, hidden presentation or changed profile/cadence.

Approximately 248.8 s of ordinary READY setup/operator wait preceded policy Start.
This is an uncontrolled comparison variable, not a matched warmup or favorable
GC-snapshot selection. The periodic action has no 20-second metric reset.

## Admission and actual RSS stop

| Admission fact | Value |
|---|---:|
| Requested profile / codec / duration / period | Analysis / DEFLATE-1 / 60 s / 200000000 ns |
| Java maximum / occupied / headroom | 268435456 / 41270272 / 227165184 bytes |
| Logical reservation / extra Java-system margin | 81788928 (78 MiB) / 33554432 (32 MiB) bytes |
| System available / low-memory | 1253756928 bytes / false |
| Available / required free storage | 90676609024 / 1062928384 bytes |
| Finite file/oracle/trace cap | 794492928 bytes |
| Battery / thermal status | 47% / 0 |
| Initial RSS | 301993984 bytes |
| Unchanged RSS increment guard | 134217728 bytes (128 MiB) |
| Actual RSS threshold | **436211712 bytes** |

Admission passed before the engine was created. The pre-creation admission witness
retains `recording_created=false` by design; that is not the later run outcome.
Preparation/writer/preferred CHUNK remain 4 MiB, journal 1 MiB and maximum HT offer
3 MiB. Both full independent validations, synchronized-footer ACK, exact loss
accounting and refusal of END after unrepresented loss remain unchanged.

Four health predicates observed RSS **314155008 → 426303488 → 362545152 →
493776896 bytes**. The last value exceeds the 436211712-byte threshold and correctly
triggers `field_safety_rss_increment_exceeded`. Java headroom at that predicate was
70915072 bytes, above the separate runtime 32 MiB threshold; this was neither an
OOM nor a Java-headroom refusal. Thermal/low-memory/camera/liveness facts remained
acceptable at the samples.

The later diagnostic sample peaks at RSS 499560448, PSS 449430528, occupied Java
202460160 and native allocator 66405760 bytes. These are different sampling instants
from the health predicate. The final row's elapsed JSON timestamp is 3.329313071 s,
after the health check and cutoff, not the exact instant RSS first crossed the limit.
The protection is sampled, not a continuous upper RSS bound.

Across the first/last diagnostic samples, spanning 3.192452232 s, ART reports
323055616 cumulative allocated bytes, 173768704 freed bytes and one natural GC
with 50 ms reported GC-time increase. Occupancy/RSS drop at the third sample and
rise again. These observations do not identify retained-live objects, a leak, or
the split between Java owners, native UVC/thermometry, graphics/Bitmap/Compose,
validation scratch and allocator/page retention. Four samples cannot establish
steady memory. The logical reservation is not an observed RSS ceiling.

## Actual timing, counters and writer work

| Witness | Result |
|---|---:|
| Requested duration | 60 s |
| Engine cutoff relative to genuine HEADER origin | **3279581138 ns** |
| First field Stop-request elapsed | 3281383221 ns |
| Stop-request call wall time | 351522 ns |
| Report elapsed including drain/close/full reopen | 4513822186 ns |
| Stop/drain/close/reopen interval | 1184142293 ns |
| Full Kotlin reopen | 733734254 ns |
| Planned / selected / acknowledged / available | 17 / 17 / 17 / 17 |
| Unavailable / missed / preparation / writer / source loss | 0 / 0 / 0 / 0 / 0 |
| Periodic intake callbacks / intentional nonselection | 79 / 62 |
| Late-receipt nonselections, included in 62 | 16 |
| Gate callbacks / valid | 82 / 82 |
| Recording-window invalid / held / replaced / malformed | 0 / 0 / 0 / 0 |
| Gate FPS / preview-publication FPS | approximately 24.99 / 25.30 |
| CHUNKs / entries / checkpoints | 4 / 19 / 1 |

The 82 gate/native-bracket callbacks include setup/subscription boundary differences
from 79 periodic intakes. They are not interchangeable counters. The ledger correctly
accounts only slots 0–16 opened before the actual safety cutoff; the remaining
unopened portion of the requested minute is not fabricated as lost measurements.
There were 11 durable ACKs at the final health sample and 17 after drain: accepted
pending owners were not claimed saved prematurely.

The deadline target was never reached: first expiry witness is null and the terminal
reason is the safety stop. The diagnostic's zero *positive overshoot* cannot validate
the physical 60-second deadline. The actual engine cutoff precedes the later field
Stop-request bracket; neither timestamp is rewritten.

For four CHUNKs, inclusive encode/first-validation wall time has median 235.929 ms
and maximum 383.453 ms; inclusive append/second-validation/footer-sync median is
132.512 ms and maximum 203.864 ms. Seal encode+append maximum is 587.317 ms.
DEFLATE's codec-only reported subset maximum is 19.198 ms. These overlapping scopes
must not be added or called pure filesystem/compression bottleneck attribution.
Writer queue peak is 1347189 bytes and pending peak 2699358 bytes; preparation
charged peak is 3145728 bytes. No capacity was increased. Four short-run samples
do not establish sustained writer performance.

Software preview age has median 57.83 ms, p95 73.06 ms and maximum 84.97 ms over 82
publications. This is source-to-publication software age, not measured screen/motion
latency. It complements the operator's short-window observation without replacing it.

## File integrity, Desktop and preservation

The original 2729959-byte LMTR has SHA-256
`a780e306c1044c87e4360bf8727822c162ab9e5d43d6ebfec0f57a984c19ce47`.
It contains 17 original Float32 planes (7520256 logical temperature bytes), all valid,
with full context/source/clock/ordinal/slot evidence and no gaps. Explicit Analysis
omits native/transport payloads; it does not provide raw reprocessing data.

- Kotlin strict full reopen and independent Python full/original-owner/periodic
  readback agree on valid **interrupted** END and known tail. Source Float32 bits,
  context, availability, receipt/intake and identities match. Producer aggregate
  nonselection counts remain producer evidence; a materialized-owner oracle does
  not independently observe every unselected callback.
- The exact Desktop reference verifies all 17 planes, 68 exact points and 68
  sequential-binary64 ROI calculations, plus every palette without data mutation.
  An automated Linux Qt offscreen window passes resize, point/ROI, Auto/Locked,
  clear, next/previous/time seek, play/pause, blank segment boundaries and normal
  worker shutdown. This is not a new human Desktop visual-usability test. There
  were no real gaps to inspect; generic gap/unavailable regressions remain separate.
- All six closed Android originals match Fedora hashes before and after consumer
  validation and normal camera release. Both source sessions were released once;
  final active camera-owner count is zero. No further phone operation is needed.
- Originals, device archive, APKs, logs, oracles, private Desktop screenshot/checks,
  operator replies and manifests stay in a new private directory. No thermal image,
  matrix, oracle or recording is published. An initial read-only hash command failed
  before copy because of remote-shell argument grouping; the corrected direct-path
  read and failure explanation are retained, without changing any original.

The field UI says “Diagnostic/read-only check failed” because a safety refusal latches
its action. `diagnostic_failure`, recorder error and first periodic failure are null;
Kotlin readback itself passed. Do not translate that generic UI verdict into corrupt
LMTR, or translate a valid interrupted file into successful duration qualification.

## Seven gates and next decision

| Gate | Conclusion |
|---|---|
| 60-second duration | **Not reached**; early RSS stop |
| Memory protection | Honored sampled interruption; steady memory **not qualified** |
| Periodic coverage | All 17 actually opened slots accounted |
| Unexplained losses | None in this short prefix |
| Integrity/finalization | Full valid interrupted END |
| Operator preview | Responsive during fully observed short beginning only |
| Desktop | Exact data and automated inspection pass; no new human UI check |

The [previous ten-second success](ANALYSIS_FIVE_HZ_PHYSICAL.md) remains valid. This is
not a matched code-only comparison: its admission RSS was 388730880 bytes (threshold
522948608), occupied Java 118651904 and READY setup about 232 s, versus 301993984,
41270272 and about 248.8 s here. Its health/diagnostic RSS maxima were 465272832 /
477184000, versus 493776896 / 499560448 here. Scene, heap/GC phase, runtime ownership
and ordinary setup differ. No general RSS improvement or causal regression is proven.

Before another duration experiment, review the preserved occupied/native/RSS/GC
trajectory and conservative simultaneous-owner accounting. A separate engineering
plan should distinguish normal preview/acquisition from recording preparation,
validation and native/graphics residency, and address baseline/GC-phase dependence
without forcing GC, selecting a favorable snapshot or raising safety limits. Any
new physical protocol requires separate authorization and the same operator gates.
No automatic retry, 30/60/120-second extension, Full run, CI, merge or R4 closure is
authorized by this result.
