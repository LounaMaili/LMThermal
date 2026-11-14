# First physical R4 Analysis at explicit 5 Hz — finite qualification

## Scope and exact provenance

On 2026-11-14 the owner authorized one Analysis/5 Hz/DEFLATE-1 field recording,
requesting ten seconds, with the available Pixel 8 and HT-301. **This one run passed
finite duration, selected-observation retention and independent file-integrity gates.**
It does not complete R4, qualify Full at 25 Hz, establish an indefinite memory bound,
or validate absolute thermometric accuracy. No second Start or longer test occurred.

The exact clean Android implementation was
`d1b8c8917961a162270e58510c1eeb39baa94f04` on
`codex/r4-analysis-five-hz`; the unmodified Desktop reader was
`26b1785226b15dc17f977d36c2ca42eb79feeac7` on
`codex/desktop-periodic-recording-evidence`. These implementation branches remain
unchanged. This follow-up is documentation only, on separate report branches.

The field APK was rebuilt with offline `:lmtr-field:assembleDebug` and
`:lmtr-field:lintDebug` (passed; existing warnings retained). Its 11789361 bytes
have SHA-256 `552803b67c3ffc1b182ba05ee1134d88b4bd21f389f2a4b11f8b118aace59f3c`.
Gradle reported 61 actionable tasks (three executed, 58 up-to-date); the artifact
was byte-identical to the pinned offline candidate. The existing field package had
the same signing identity. Non-destructive `install -r`
preserved data, and the APK pulled back from `org.lmthermal.field` matched exactly.
The Android production application was not changed, installed or enabled for recording.

Existing wireless ADB worked directly without mDNS discovery, pairing or security
changes. The sandbox could not reach the host ADB socket; authorized host execution
used the already paired connection. No ADB reset, reboot, other-application operation,
GitHub Actions, emulator, merge or release occurred. The tested device was Pixel 8,
Android 17/API 37; Android enumerated Infiray T3-317-13 VID `1514`, PID `0001`.

[The curated receipt](analysis-five-hz-physical-validation.json) retains exact counters,
timing, admission, sampled memory and validator scope. Raw scenes, screenshots,
per-entry temperatures, oracles, device identifiers and original recordings remain private.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Fresh source readiness and one explicit request

Normal Connect/Open first produced genuine DISPLAY transport: 224256 bytes, image
words 32821–33015 in the initial logged sample, around 25 FPS. Opening sent no
initialization controls. A single explicit normal initialization then completed
32772 → 32800 → 32768, each as a successful two-byte SET. The session reached READY
in 6.355 s, after 75 shutter-discard frames and five subsequent live frames.
Existing startup/transition rejection counters are distinct from the recording window.

A current valid Celsius preview, source liveness and a non-sensitive wall/object
scene were observed before Start. Normal Inferno/Auto Compose rendering and thermometry
remained enabled. Approximately 232 seconds of normal ready preview elapsed during
preparation before policy Start; this is an important comparison confounder, not a
matched warmup or a deliberately selected favorable heap snapshot. No GC was forced.

The separate **Analysis 5 Hz / 10s (test)** action was requested exactly once. Its
normal in-app admission witness passed before creating the engine. The witness's
`recording_created=false` explicitly describes that pre-creation point, not the later
run outcome. No fallback, queue/budget change, hidden preview or camera-cadence change
was used. The operator later answered **"Pas regardé"** about the preview during the
run: manual visual responsiveness is therefore **unobserved**, not passed.

## Admission and unchanged safety policy

| Actual admission quantity | Value |
|---|---:|
| Profile / codec / requested duration | Analysis / DEFLATE-1 / 10 s |
| Period | exactly 200000000 ns |
| Java maximum / occupied / headroom | 268435456 / 118651904 / 149783552 bytes |
| Logical reservation / additional heap-system margin | 81788928 (78 MiB) / 33554432 (32 MiB) bytes |
| System available / low-memory | 721035264 bytes / false |
| Initial RSS | 388730880 bytes |
| Battery / thermal status | 72% / 0 |
| Available / required free storage | 90622615552 / 469008384 bytes |
| Finite file/oracle/trace cap | 200572928 bytes |
| Prep queue / writer queue / preferred CHUNK | 4 / 4 / 4 MiB |
| Maximum offer / control journal | 3 / 1 MiB |

The unchanged runtime RSS threshold was admission RSS plus 128 MiB:
**388730880 + 134217728 = 522948608 bytes**. The largest sampled *health predicate*
RSS was 465272832 bytes; the later diagnostic RSS sample peaked at 477184000 bytes.
These are different sampling instants, not interchangeable guard inputs. Minimum
sampled health Java headroom was 91898880 bytes; system available memory stayed at
least 646270976 bytes. Sampled thermal status remained 0 and low-memory flags false.
No admission refusal, safety stop or forced collection occurred.

## Actual duration, slots and population boundaries

| Witness | Result |
|---|---:|
| Requested duration | 10 s |
| Field Stop-request elapsed time | 10644236008 ns (10.644236008 s) |
| Engine policy cutoff relative to HEADER | 10644411382 ns |
| Report elapsed including drain/close/full reopen | 13590357632 ns |
| Final status / reason | END `completed` / `user_stop` |
| Planned / selected / acknowledged slots | 54 / 54 / 54 |
| Available / unavailable selected measurements | 54 / 0 |
| Missed schedule / preparation loss / writer loss | 0 / 0 / 0 |
| Periodic intake callbacks / intentional nonselection | 264 / 210 |
| Late-receipt nonselections, included in those 210 | 12 |
| Gate callbacks / gate-valid measurements | 266 / 266 |
| Known source losses / unknown-loss signals / held / invalid | 0 / 0 / 0 / 0 |
| CHUNKs / timeline entries | 11 / 56 |

The existing automatic field timer requested normal Stop; `user_stop` is the
recorder's default reason for that call, not evidence of a manual operator Stop.
The existing sampler checks health and then delays approximately one second; its
work is not subtracted from the delay. This soft timer exceeded the requested ten
seconds by 0.644236008 s. **This was not an exact ten-second cutoff.** No duration
or assertion was changed after the result. The actual periodic window covers slots
0–53, including its partial final slot; no artificial requirement for 50 matrices
was imposed. The initial slot was already partly elapsed before the first source
receipt. Genuine receipts/intakes remain unchanged rather than rewritten to deadlines.

Reconciliation is `264 = 54 + 210`, `54 planned = 54 selected + 0 missed`, and
`54 selected = 54 durable observations + 0 preparation/writer losses`. Intentional
nonselection is not a recorded loss. The 12 old-receipt callbacks could not fill the
current window; another fresh callback filled each slot. The materialized-owner
oracle independently verifies stored source identity, bytes, clocks and slots;
**it does not independently recount all 210 unselected callbacks**. That aggregate
is producer evidence, corroborated by its internally consistent closure/snapshot.

The gate count includes two setup-boundary callbacks before periodic subscription.
Separately, `camera_received_delta=239` is the difference at the last approximately
1 Hz sample (9.641 s), not a Stop-time snapshot. It excludes the last roughly one
second and must not be presented as a complete-window count or unexplained loss.
The final native callback diagnostic counters stayed unchanged for replacements
and malformed frames across the recording window.

Gate delivery cadence was 24.9996347412 FPS. Stored intake intervals had median
200.938 ms, p95 207.084 ms, maximum 208.976 ms and minimum 116.712 ms (the initial
partial-window boundary). Their 53-interval rate was 5.038067/s, consistent with
finite boundaries, not a change to the exact declared 200 ms policy. Final ACKs can
occur during drain; observation counts divided by the intake window are not ACK-latency
measurements. Both complete CHUNK validations and synced-footer ACK rules remained.

## Sampled memory, GC and writer cost

Ten samples span 9.493188481 s, not the entire finalization interval. Sampled values:

| Metric | Minimum | Maximum | Last |
|---|---:|---:|---:|
| Runtime occupied Java bytes | 33885184 | 184650752 | 76319744 |
| Debug native allocated bytes | 26025056 | 65942336 | 40946160 |
| Process RSS bytes | 293457920 | 477184000 | 347119616 |
| Process PSS bytes | 249116672 | 427072512 | 300583936 |

Whole-process ART counters increased by 993923072 allocated bytes, 1049509888 freed
bytes, five GC collections and 362 ms GC time, with no new blocking-GC count. This
is approximately 104.7 MB/s of sampled cumulative allocation, **not retained live
heap or writer-only allocation**. The trace alternates high occupied/RSS samples
with natural collections. Post-GC sampling phase varies, and neither a plateau nor
all peaks are established. Graphics/Java/native classifications in the one separate
pre-Start `dumpsys meminfo` snapshot are not a continuous attribution experiment.

Prep queued/in-flight charges peaked at 3145728/3145728 bytes; writer queued/in-flight
at 898134/449068 bytes; pending at 2699365 bytes. Control usage drained to zero.
Encode including the first validation had p50/p95/max 222.367/276.870/363.956 ms;
append including the second validation and sync had 129.283/149.606/161.108 ms.
Codec-call p95/max was 24.996/25.708 ms. These inclusive/distribution measures must
not be subtracted to invent exclusive costs or prove causality. Stop through drain,
backend close and full reopen took 2.946291831 s after the cutoff request.

## Immutable originals and independent consumers

The original LMTR is **6225492 bytes**, SHA-256:
`9708b17ffe387e49373229ae70dd52351a565c34ae65da857d462fcbefcdb0e9`.
It contains 54 all-valid finite Float32 planes (23887872 logical temperature bytes),
no gaps, and a validated completed END with no unknown tail. Native/transport data
and a validity-mask payload are absent by the explicitly chosen Analysis policy;
this is not Full evidence or a claim that omitted raw data can be reconstructed.
All 54 original temperature hashes and all 54 selected original raw-plane hashes
are distinct. That establishes changing selected source bytes, not operator-observed
motion or an independent physical-temperature reference.

- The unchanged Kotlin full reopen and independent Python strict full/owner/slot
  checker passed. Original Float32 hashes, context, availability/reasons, source
  identities, selected counters, original ordinals and actual clocks agree.
- The exact Desktop reader fully verified all 54 planes. Four native points and
  four ROIs per plane passed 216 exact point and 216 sequential-binary64 ROI checks.
  All palettes per frame preserved measurements.
- An automated Linux Qt offscreen window passed initial open, point/ROI, resize,
  locked/automatic range, palette changes, next/previous/time seek, play/pause,
  blank segment endpoints and normal worker shutdown. This was not an operator
  Desktop usability test. There were no physical gaps to exercise; generic gap
  behavior remains covered separately by the pinned regression corpus. Nine general
  UI and eight periodic-evidence regression tests also passed again without hardware.
- The first private Desktop helper expected a Python `unknown_tail` key on a complete
  report. Its script/assertion was corrected to require `valid`/`completed`/`full`;
  the original script and failure log remain preserved. No reader, source bytes,
  recorder assertion or physical run was changed/retried.
- All six original device files matched Fedora SHA-256 after consumer validation.
  The first and second historical physical LMTR hashes also remain unchanged.
  The camera finally released once through normal Back/background, with zero active
  owners. No further phone operations or recording were needed.

APKs, original inventories/oracles, logs, private desktop checks, screenshots and
manifests are preserved in a new mode-0700 local evidence directory. This is local
same-filesystem preservation, not an independent backup or an atomic snapshot.
No original was repaired, overwritten, resumed or published.

## Comparison and next separately authorized qualification

The old [Full attempt](SECOND_PHYSICAL_ATTEMPT.md) stopped for RSS at 4.308975303 s:
45 retained observations, two preparation losses and 58 writer losses. The new
Analysis/5 Hz run reached its requested duration and retained every selected
observation, while deliberately not selecting 210 other delivered callbacks.

**This is not a matched code-only benchmark.** Profile, recorded cadence, warmup,
scene/time, heap/GC phase, initial RSS (297185280 versus 388730880 bytes) and system
memory differ. The new diagnostic RSS peak is higher in absolute terms (477184000
versus 448307200 bytes), despite passing its own unchanged increment guard. Neither
a general RSS reduction nor loss-free Full at 25 Hz follows from this success.

A **30-second Analysis/5 Hz/DEFLATE-1 proposal** is now reasonable for owner review,
not execution. Before that run:

1. Review a small test-only change exposing an explicit bounded 30-second periodic
   action: the exact tested APK deliberately allows only ten seconds. Do not use its
   acquisition-following 146-second buttons or silently alter profile/cadence.
2. Review an absolute monotonic Stop deadline and its races/settling accounting, or
   explicitly accept/report the current soft-timer overshoot. Preserve original
   receipts, final-slot accounting and Stop/drain semantics; no rewritten timestamps.
3. Retain 78+32 MiB, the 128 MiB RSS increment guard, both validations, all queue/
   journal/CHUNK limits, normal preview/thermometry and every existing health stop.
4. Obtain fresh hardware/scene authorization for exactly one run, preserve roughly
   150 slots according to actual boundaries, and verify complete data/availability,
   losses, clocks, raw intake counters, GC/native/graphics/RSS and independent readers.
   Treat last-sampled counters separately from true Stop-time totals.
5. Stop on any guard and retain failures without retries. Thirty seconds still does
   not establish indefinite memory safety, generic-camera support, Full performance,
   calibrated temperature accuracy or permission for 60 seconds.

No 30/60-second run, production integration, R4 closure or further hardware experiment
is authorized or performed by this report.
