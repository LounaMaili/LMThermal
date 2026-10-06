# Android foundation real-device validation — 2026-10-02

Branch: `feat/android-usb-uvc-foundation`, based on research main
`4d783f3ff3bb637e169208c85914ec14a7d2c988`.
Numeric evidence: [2026-10-02-android-smoke.json](diagnostics/2026-10-02-android-smoke.json).
Private payloads/screenshots remain in ignored local validation storage, not Git.

## Acceptance result

**Complete original trailer-preserving Android acquisition achieved.**

Test device: Pixel 8, Android 17/API 37, arm64-v8a. The HT-301 occupied its only
USB port in Host/OTG mode; all install/shell/log access used already-paired wireless
ADB. No root, new pairing or camera setting writes were used. Android enumerated
Infiray T3-317-13, VID 0x1514/PID 0x0001.

The application installed/launched and the operator granted normal Android
camera/USB authorization. Native UVC negotiation reported frame size **224256**,
payload transfer size **780**, frame interval **400000** (100 ns units, 25 FPS).
Unconverted callback metadata reported width **384**, height **292**, YUYV.
Startup yielded short/invalid payloads, which were rejected; they were never padded
or accepted just because a preview could be made. Subsequent usable frames were
exactly **224256** bytes.

The first run encountered an already-raw14 camera; no app-controlled transition
is claimed. Real RAW14 inspections had clear image high bits, valid calibration
and consistent trailer extrema. Retained early-run logs show 29 two-second
snapshots, mean acquisition cadence **25.004 FPS** (24.900–25.072), with 1471
native callbacks at the last snapshot, 8 malformed payloads and 5 overwritten
pending payloads. Another later raw run had additional malformed output; no
assumption about shutter or the cause of those transients is made.

Read-only reopen/replug also produced DISPLAY frames. The operator completed the
raw14 aiming/movement/responsiveness check and confirmed display preview usability.
Both mode renderers were exercised on hardware; no Celsius readings were shown.
Aiming preserves native orientation; no preferred final phone/sensor orientation
is inferred from this milestone.

## Full-frame proof

Two valid Android payloads were retrieved from app-private debug storage and
parsed by the executable Desktop reference, rather than inferred from RGB output:

| Evidence | First valid display payload | Final installed-build payload |
|---|---|---|
| Total bytes | 224256 | 224256 |
| Image bytes | 221184 | 221184 |
| Trailer bytes | 3072 (four rows) | 3072 (four rows) |
| Nonzero trailer bytes | 324 | 324 |
| Desktop classification | display | display |
| Image min/max | 32793 / 32993 | 32817 / 32924 |
| SHA-256 | `e07767b0a64aa70678989140c3321354f9fb06d17400af4bfc92be24f788573e` | `c3d5adc46912a28ec8880159f76d97ea9217fef3ec8788b1d23eb981bcceb6c4` |

At the established trailer offsets both contain correction 0, reflected/ambient
25, humidity about .45, emissivity about .98, distance 1, and the expected five
calibration floats. Repeated settings/calibration copies remain available. This
rules out an RGB-only or 288-row-cropped acquisition abstraction. Debug payloads
were not adopted as regression fixtures because they are unsanitized.

The long final acceptance-run APK SHA-256 is
`543b760f42f10ca5091562dd1c1db0d6bf6d1683084d76534aafe24deb0ed817`.
Final review then serialized publication with reset and added a concurrent close-race
regression. The resulting final debug APK is
`8c71a2fb26955a15d817d9a868f87c488aaf6da70f242e9d238a87e8a2254afa`;
the transport and parser behavior are unchanged. That build also streamed DISPLAY
at about 25 FPS and its normal Close button cleared the preview/counters and logged
release. A final diagnostic-label correction retains attached identity/permission
when closing the stream (closing is not physical detach). The resulting APK is
`6df78aa9222cc18ad0c19eeef183360e0623d1a14de99a53fd611c32c91b3d47`,
rebuilt/linted and installed; its label correction was not separately operator-tested
because the phone had locked. Acquisition/parser/native behavior did not change.
The JSON report records final-build cadence/counters separately from earlier runs.
This is a local debug artifact, not a signed release distribution.

## Lifecycle and bounded flow

- Background via normal Home action released native streaming (release logged).
  Foreground showed attached/granted, no old image/counters, and required explicit reopen.
- Operator physically unplugged HT-301 while streaming. Active Android USB enumeration
  disappeared, the app remained alive and displayed No HT-301 with empty preview/counters;
  native release was logged. Wireless ADB shell still returned its test echo.
- Operator replugged, granted access/opened when needed, and confirmed preview return.
  DISPLAY acquisition resumed near 25 FPS. No root or reset/control command was used.
- Final installed-build streaming and subsequent normal background release were logged.
- Native pending capacity is one payload; UI state is conflated. The JVM regression
  demonstrates that a slow consumer receives the newest snapshot instead of replaying
  10000 obsolete publications. Live replacement counts remained small, not a growing queue.

## Checks

- `./gradlew :core:test :app:assembleDebug :app:lintDebug`: passed.
- **20 JVM tests**, zero failures/errors/skips.
- Lint: zero errors, seven nonfatal warnings: four newer-dependency suggestions,
  ChromeOS x86_64 support, backup-attribute modernization and missing app icon.
  Dependencies are deliberately pinned; only arm64/Pixel 8 is in scope. Backup is
  disabled and extraction rules exclude private debug evidence.
- Complete staged/working diff whitespace check: passed. Upstream source/license bytes
  retain original whitespace via scoped `.gitattributes`; project-authored files are checked.

## Limits and next milestone

Permission denial and screen rotation/recreation were implemented but not specifically
operator-tested in this run. Other phones/ABIs, long-duration/overnight operation and
power-management behavior remain unvalidated. Exact-size startup transients can contain
invalid words, so size alone is never treated as validity. The debug evidence selector
was corrected to save the first **valid** exact frame rather than a mixed startup frame.

The camera's pre-existing raw14 state and later display observations do not establish
an Android initialization sequence. This branch has no camera setting-write interface,
shutter/readiness state machine, thermometry, measurement/ROI or export/recording UI.

Next: a focused explicit radiometric control/session port with standard UVC zoom
readbacks, Desktop-equivalent stage/discard/liveness gates, and fixture tests.
Thermometry/LUT parity is a subsequent separate step; absolute physical accuracy remains
unvalidated. Release licensing for project-authored code remains an owner decision.

## Radiometric-session validation attempt — 2026-10-02

Branch `feat/android-radiometric-session`, base `f6d19db`.
See [session architecture](ANDROID_RADIOMETRIC_SESSION.md) and the sanitized
[session report](diagnostics/2026-10-02-android-radiometric-session.json).

**Hardware acceptance is blocked at the baseline readback; no radiometric control was sent.**
The operator reconnected the camera, opened the app read-only and confirmed a usable display
preview. DISPLAY_STREAM was observed with exact 224256-byte frames, 221184-byte image and
3072-byte trailer. Explicit Initialize qualified three fresh consecutive display frames.
The standard direct USB Zoom Absolute GET_CUR returned **1**, while Desktop's required
baseline is **0**. ERROR followed immediately, before `32772`. A complete five-second physical
unplug/reconnect and explicit retry produced the same result. Repeated explicit retries were
read-only failures; no cached result was fabricated and the baseline requirement remains zero.

The final diagnostic build confirmed terminal **1**, control interface **0**, selector **11**,
request type **0xa1**, request **0x81**, wValue **0x0b00**, wIndex **0x0100**,
transferred length **2**, returned bytes **01 00**. This matches the pinned upstream libuvc
zoom request and unsigned little-endian decoding. It establishes this Android readback
observation; it does not establish why earlier Linux V4L2 readbacks were zero. Desktop uses
VIDIOC_G_CTRL, not a raw USB response. Different driver/device/session semantics remain an
investigation boundary. Do not replace actual GET_CUR with software-cached zero/sent values.

The latest recorded display connection had **1922 callbacks**, **1 replaced pending frame**,
**2 malformed payloads** and **2 parser rejections** at its last periodic snapshot. FPS before
and after the rejected request remained near 25 (exact aggregate statistics are in the JSON).
There was no control-induced stream starvation because no SET_CUR occurred. No during/after
radiometric-transition FPS, shutter transient or time-to-ready can be reported. The operator
confirmed display usability; raw14 session preview acceptance was not reached.

Normal Home/background released the final stream about **125 ms** after cancellation was
logged. Reopen/reconnect observed display and did not replay initialization. Early-stage
cancellation after a modifying command and existing-raw14 Test B are **not hardware-validated
in this task**, because the baseline prevents any modifying stage; their scripted regressions
pass. Foundation pre-existing-raw14 observations remain historical evidence only.

Checks: **51 JVM tests**, including 31 session regressions and the 111-observation executed
Desktop shutter oracle; debug build and lint pass. Lint retains the foundation's seven
nonfatal warnings, zero errors. The final review also clears session readiness on transport
errors; that exceptional UI path was not physically forced. No raw scene/payload was saved
by this milestone. No Celsius, LUT, measurement or accuracy support is claimed.

For that original milestone, the next required step was to resolve direct USB GET_CUR versus Desktop V4L2 baseline/readback semantics
with read-only evidence before revising either policy or testing the modifying sequence.
Only then complete radiometric hardware acceptance; LUT/thermometry porting follows later.

## Zoom control semantics diagnostic (2026-10-02)

The GET-only Android inventory and Linux cache audit are recorded in
[ZOOM_CONTROL_SEMANTICS.md](ZOOM_CONTROL_SEMANTICS.md). Ordered Android queries show CURRENT=1 after stream open, 3 after INFO,
65535 after MAX, and 0 after zero-valued queries. CURRENT is not a reliable
command-state acknowledgment on this tested path. Fresh Linux V4L2 and Desktop
reads both return 0; the privileged direct transfer failed with libusb ERROR_IO
(USBFS EBUSY) without claiming an interface or detaching uvcvideo. A control-only
trace found no physical Zoom transaction for one repeated V4L2 GET returning zero.
No Zoom SET was sent and
the existing session gates remain unchanged. V4L2 control state must be
distinguished from a proven physical USB response.

## Frame-observed radiometric acceptance — 2026-10-02

Child branch `fix/android-radiometric-frame-acceptance` continues from Zoom-audit tip
`3df07666fca5047aaea3136559f7163b973ac643`. This supersedes the original readback blocker.
Wireless ADB, Pixel 8 USB Host and the same HT-301 were used; no private frames were saved.
Numeric evidence: [sanitized report](diagnostics/2026-10-02-android-frame-acceptance.json).
The compact `frame_columns`/`frame_trace` arrays preserve all recorded numeric observations.

### Single-32772 experiment

The debug one-shot required a fresh three-DISPLAY-frame baseline (125, 126, 127),
32803–32985 image words, and issued only `32772`, bytes `04 80`.
Request `0x21/0x01`, selector 0x0b, terminal 1/interface 0, `0x0b00/0x0100`, exact
2 bytes. First valid raw14 was sequence 142, 111 ms after the core completion point,
5311–5825. Fifteen consumed receipts were discarded; distinct valid raw14 frames
156/157 qualified success, final 5312–5828. All complete frames retained the full
224256 / 221184 / 3072 layout. No 32800, 32768 or Zoom GET occurred. Native malformed
count was 2 before/after, diagnostic rejected/malformed/held were zero. Baseline FPS
25.024; the short completion window reported 23.928, then periodic callbacks returned
near 25. Operator confirmed success. This confirms existing Linux behavior across
platforms; it is not a new command discovery or radiometric-ready session.

### Revised acceptance contract

Baseline-zero and post-write GET equality requirements are removed, without
substituting 1 or another expected value. The session's control interface contains
SET completion only. Every SET must return exactly 2 bytes under current ownership.
Frame behavior separately gates each next command. Timings remain 500/600/500 ms;
transition discard 15, stage limit 150, two distinct valid images, 75 valid shutter
frames and five changing summary-consistent frames are retained. Unknown raw14
startup remains unsettled. Desktop's additional finite-LUT gate stays deferred.

### Full explicit sequence

DISPLAY baseline sequences **2385–2387**, words **32777–33008**, callback FPS **24.952**.
Exact control transfers (monotonic host milliseconds):

| Command | Bytes | Start / completion ms | Actual bytes |
|---|---|---|---:|
| 32772 | 04 80 | 538143222 / 538143224 | 2 |
| 32800 | 20 80 | 538144537 / 538144558 | 2 |
| 32768 | 00 80 | 538145699 / 538145700 | 2 |

- First valid raw14: sequence **2402**, **116 ms** after 32772 transfer return,
  words **5355–5834**. After 15 discards, 2416/2417 verified the first stage.
- First new structurally valid raw14 after range was **2437**; after 15 receipt
  discards, **2448/2449** verified the range stage (final **5145–5428**).
- Shutter: **75 valid settling frames**, then **five changing summary-consistent
  frames**; **30 held images** occurred within settling. Seventy-five alone did
  not establish readiness.
- Ready event: sequence **2542**, **6.312 s** after request. Ready raw range
  **5421–5819**, callback FPS **25**, full 224256 / 221184 / 3072 bytes preserved.
- Native malformed payloads **5 total** (**+3** during initialization); parser
  rejections **6 total** (**+4**). Session rejection counter **85 total** includes
  deliberate shutter settling and four awaiting-live observations. These counters
  overlap; they must not be added as independent dropped-frame totals. One pending
  frame replacement was already present at baseline and remained one at ready.

Frame events report processing outcome state; a deadline control can complete during
processing of the last pre-command observation. Post-command interpretation must use
sequences **after** the corresponding `control` event's sequence, plus discard counts,
rather than only event timestamp/state labels. No pre-command frame qualified a stage.
`state` is the core outcome; supplemental `session_state` is the last published UI
snapshot and can lag that outcome by one observation.
No private image/payload or digest is logged. Timing refers to host receipt/processing,
not sensor exposure or USB hardware timestamp precision.

The operator confirmed ready and recognizable/responsive preview. A normal Close/Open
without physical reconnect observed existing raw14 and stayed RAW14_UNSETTLED near
25 FPS, with no automatic initialization. Lifecycle interruption regression tests
stop future commands; no unsafe USB fault or mid-write physical detach was forced.

The final checks retain all frame regressions and the executed Desktop shutter-trace
oracle. Structural/live radiometric session acceptance now passes on this Pixel 8 /
HT-301 combination. No Celsius/LUT, numerical temperature parity or independent
physical accuracy is established by these tests.

Final checks: **66 JVM tests passed**, debug build passed, lint passed with zero
errors and seven inherited warnings, and `git diff --check` passed.

## Kotlin thermometry parity and live measurement — 2026-10-03

Base `main`: `d072a3faadb1c7940a84a3ab2a05aa67ef4f2a03`; focused branch
`feat/android-thermometry-parity`. Pixel 8 / Android 17 / arm64, wireless ADB plus
HT-301 USB Host. Pairing was retained; enabling/restarting Wireless Debugging restored
mDNS without new pairing. Product and instrumentation APKs were installed before testing.
No private scene payloads were saved. The report uses labelled compact frame/measurement columns (dotted names identify nested numerical trace fields). Numeric evidence is retained in
[2026-10-03-android-thermometry.json](diagnostics/2026-10-03-android-thermometry.json).

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

### Offline and Android-runtime numerical acceptance

Six existing sanitized fixtures (initial, first raw14, range, shutter-held, settled-room,
settled-hand) and two synthetic arithmetic cases each compare **all 16384 entries**.
The synthetic effective-distance-60 branch and uint16 base wrap are derived in memory.
Five real tables first match existing executed official x86_64 native reference tables;
hand/synthetic goldens come from Desktop. Intermediate float32 bits, finite count and
NaN pattern are checked. Full settled-room/hand matrices compare **110592 pixels each**,
all raw indices, min/max, literal/trailer centers and high/low values/coordinates.

JVM and actual Pixel 8 ART: **zero differing finite bits, zero maximum absolute error,
zero ULP difference, identical NaN pattern** for all tables and matrices. NaN payload/sign
bits are not a numerical invariant. Two camera-free instrumentation tests passed on the
phone before live integration testing. No libm tolerance was relaxed. This is Kotlin/ART
parity with the established references, not execution of the original APK ARM binary.
Fixture provenance, reference/source hashes, arithmetic boundaries and supported limits:
[ANDROID_THERMOMETRY.md](ANDROID_THERMOMETRY.md).

### Fresh display and explicit session

The operator physically reconnected the camera and confirmed DISPLAY with no temperatures.
Aiming remained grayscale, native 384×288, near 25 callbacks/s. Opening sent no control.
The operator tapped Initialize once. Existing sequence/timings/liveness were unchanged:

| Control | Actual bytes | Host start / completion ms |
|---|---:|---|
| 32772 | 2 | 606889388 / 606889391 |
| 32800 | 2 | 606890717 / 606890739 |
| 32768 | 2 | 606891902 / 606891904 |

Three fresh baseline frames spanned 32776–33014 display words. First stage discarded
15 receipts and qualified two changing raw14 images; range repeated this policy.
Shutter discarded **75 valid frames**, with **30 held images** observed, then required
five changing summary-consistent frames. Structural ready at sequence3808 in **6.347 s**;
measurement became available for that same frame, raw5491–5738, all selected values finite.
First matrix28.143755–33.471970 °C, trailer/literal center5521/28.806215 °C;
high5738 at(170,0), low5491 at(310,227). Readiness was not a fixed elapsed-frame assumption.

At ready: session rejected83 total (baseline2), held30, malformed4; native/parser
malformed/rejection counters4 total, +2 during initialization. Counters overlap and
include deliberate discards/awaiting-live evidence; they must not be added. Pending
replacements were1 before measurement; the cold first measurement increased them to3,
and they remained3 through the subsequent sampled ready run. Supplemental callback
counters can advance during measurement; the measurement `sequence` identifies its actual
source frame. Measurement `monotonic_ms` is receipt time, preceding computation.

### Qualitative live scene and performance

The operator confirmed a hand against cooler background, responsive numeric updates,
red high marker on the hand, cyan low marker on cooler background and responsive app.
A representative high-center sample at sequence4965:

| Quantity | Native-equivalent result |
|---|---|
| Raw range | 5455–6007 |
| Full matrix | 27.270960–38.948128 °C |
| Trailer center | index5966 / 38.124280 °C |
| Literal `(192,144)` | index5967 / 38.144432 °C |
| High | index6007 / 38.948128 °C at(118,52) |
| Low | index5455 / 27.270960 °C at(334,263) |

A later cool scene had much smaller/lower matrix range. All86 sampled available
measurements passed finite observed-index and summary consistency gates; inputs/trace
are in the report. Scene change is qualitative evidence only, with no assumed hand
reference temperature or absolute-accuracy conclusion.

A fresh lookup is built every frame: **no cache**. Full measurement evaluation includes
inspection, parameters, LUT, matrix and summaries. Cold first call **116.491 ms**;
85 later logged samples **10.203–21.189 ms**, median **14.676 ms**. These are sampled
worker timings, not isolated LUT benchmarks or guaranteed latency bounds. Callback rate
24.876–25.316 FPS; operator confirmed responsiveness. The cold start caused two pending
frame replacements, then no further replacements were observed in the sampled ready run.
Calibration/FPA inputs changed during streaming and were recomputed each frame.

### Stale-value/lifecycle acceptance

Operator confirmed Close removed Celsius and markers. Close/Open without reconnect or
initialization produced existing RAW14_UNSETTLED with temperatures unavailable; logs
confirm unavailable measurements, changing raw14 and no control replay. Final normal
Close cleared preview and readings; UI dump showed DISCONNECTED, zero frame size,
Temperatures unavailable and the warning. Worker-release logs confirm shutdown.
JVM regressions cover held/demoted, invalid calibration/LUT-selected NaN, missing frame
and disconnected gates without retaining previous Celsius. No unsafe USB fault or
live calibration corruption was injected, and no post-ready held interval was forced.

Final checks: **95 JVM tests passed**, **2 Pixel 8 instrumentation tests passed**,
debug build passed, lint **zero errors / nine warnings** (seven inherited plus two
newer-version notices for test-only dependencies), `git diff --check` passed.
Next milestone: native-coordinate touch inspection/Celsius presentation using this
measurement model; independent controlled surface-temperature accuracy remains separate.

## Celsius presentation and touch acceptance — 2026-10-03

Milestone `feat/android-celsius-presentation`, based on main
`526316fbc9db0a892d38b81f4b48768e63176c83`. Pixel 8 / Android 17 / arm64-v8a,
HT-301 through USB Host and the already-paired wireless ADB mDNS target. No new
pairing/manual connection was needed. The current debug APK was installed before
operator testing; full ART color/matrix parity also passed. Numeric-only preserved
hardware evidence is condensed in
[the presentation report](diagnostics/2026-10-03-android-celsius-presentation.json).
No new raw/image fixture or scene hash was captured for this milestone.

### Display, palettes and exact range lock

Operator confirmed usable grayscale after physical reconnect/Connect, without
Celsius value or legend. Explicit Initialize restored recognizable Inferno with a
Celsius legend. All five palettes—White hot, Black hot, Inferno, Iron-like and
Turbo—were exercised with a hand against cooler background. Operator confirmed
scene/high/low alignment, responsive interaction and colors changing without
palette-driven numeric jumps. Logs contain every palette, Auto scene adaptation,
and four Locked renders at exact **25–45 °C** while matrix ranges changed. Auto
later expanded a nearly uniform scene to the specified centered 1 °C minimum span.
These are presentation/relative-scene observations, not calibrated surface values.

Each saved fresh explicit session completed exactly two bytes for
`32772 → 32800 → 32768`, with the existing 15/15 transition receipt discards,
75 valid shutter frames and five live qualification images. First readiness times
were **6.345 / 6.355 / 6.351 s** in the palette, touch and recovery runs. First ready
raw ranges were **5261–5799 / 5262–5735 / 5320–5775**. No camera controls or session
acceptance rules changed in this presentation milestone. Temporary held-stream
readiness losses produced unavailable measurements and cleared the legend; changing
acceptable frames restored rendered Celsius. No USB faults were artificially forced.

### Touch, shared mapping and overlays

Operator confirmed tap/drag on center, corners/edges, hand and cooler background,
with cursor under finger, native bounds, aligned high/low and changing readings at
one retained coordinate. Fourteen saved touch events had owned raw14/Celsius values;
all coordinates were inside native x 0..383/y 0..287. Representative observations:

| Touch | Original raw14 | Native-equivalent Celsius |
|---|---:|---:|
| Hand `(113,186)` | 5766 | 36.916580 °C |
| Cooler background `(315,200)` | 5263 | 25.857199 °C |
| Selected `(132,96)` in warm scene | 5746 | 36.476513 °C |
| Same `(132,96)` in a later cool scene | 5289 | 26.895245 °C |

These samples are from different live frames, not an assumed reference-temperature
comparison. Exact corner/edge/letterbox and resized portrait/landscape mappings are
also covered by JVM/ART tests, including every pixel in four JVM viewports. Native
orientation remains unchanged; this task does not claim a separately tested live
phone-rotation workflow or a preferred final viewing orientation.

### Sampled performance and bounded flow

Presentation runs on an independent latest-request worker; valid measurements skip
redundant acquisition-side grayscale bitmap creation. No LUT cache was introduced.
Saved periodic/settings-change samples, rather than every frame, show:

| Quantity | Samples | Median | Observed min–max |
|---|---:|---:|---:|
| Celsius render, including bitmap/dispatch wait | 131 | 24.952 ms | 18.035–40.664 ms |
| Warmed full measurement evaluation | 129 | 18.674 ms | 10.092–26.913 ms |
| Callback FPS at ready render samples | 131 | 25.000 | 24.876–25.366 |

First cold measurement was **113.937 ms**. Thermometry includes inspection,
parameters, fresh LUT, matrix and summaries. Concurrent rendering increases sampled
cost compared with the prior thermometry-only run; these timings are not latency
bounds or isolated microbenchmarks. Operator confirmed responsive view/touch/controls.

Pending native replacements stayed **2 / 0 / 2** throughout the three sampled ready
runs; their respective native malformed totals stayed **2 / 4 / 5**. The final
completed-superseded-render counters were **46 / 21 / 18**. These count completed
renders rejected because inputs changed, excluding coroutine-cancelled requests;
they are not total presentation-drop counts. The bounded latest worker drops stale
presentation rather than blocking acquisition or growing a queue. Original native
callback counters and measurement sequence retain their independent meanings.

### Close, unsettled reopen and recovery

Operator confirmed Close removed Celsius colors, legend, cursor reading and extrema
markers. Reopen without physical reset or initialization produced RAW14_UNSETTLED
and `host_range_unverified`, with grayscale aiming preview. UI inspection showed
`(132,96) · temperature unavailable`, retained selection, no legend and no automatic
control replay. A fresh physical reconnect/explicit initialization restored current
Celsius colors, legend and inspection; numeric renders confirm the new ready session.
Final normal app closure logged unavailable presentation then `Stream released
generation=2`. No stale reading or legend survived unavailable state.

### Final checks and limits

- **116 JVM tests passed**, including 21 added presentation/mapper regressions.
- **6 Pixel 8 instrumentation tests passed**: original thermometry parity, full
  presentation color parity/mapping and actual render-worker replacement/cancellation.
- Two matrices × two range modes × five palettes: **2,211,840 pixels compared**,
  zero differences on both JVM and Pixel ART; all 256 entries of every palette match
  independently exported Desktop references. Measurement immutability is tested.
- Debug/test builds passed; lint **zero errors / nine existing warnings**;
  `git diff --check` passed. No runtime dependency was added.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
A hand/background check validates relative response and interaction only. Independently
measured surface targets remain necessary for absolute-accuracy assessment. Recommended
next product milestone is native-coordinate ROI statistics, retaining these mapping and
measurement-validity rules; exports/recording and final orientation presentation remain later work.

## Integrated camera-module regression — 2026-10-03

Branch: `refactor/camera-module-foundation`, based on
`3b743baaad8bbf5c3977b92de53a4b366300cddd`. Contracts and ownership are documented
in [CAMERA_MODULE_ARCHITECTURE.md](CAMERA_MODULE_ARCHITECTURE.md). Numeric-only
evidence, APK hash, settings/calibration, stages and comparison with the previous
accepted run are saved in
[2026-10-03-camera-module-foundation.json](diagnostics/2026-10-03-camera-module-foundation.json).

The existing partial module refactor, resource migration, tests and hardware reports
were preserved when resuming. No transport/session/thermometry implementation changed
after the accepted live regression. The **6.354-second run remains valid**; completing
documentation/checklist and rerunning automated tests did not require another physical
capture or initialization. No new image/raw fixture or scene hash was saved.

### Automated checks

- **147 JVM tests passed**: the original 116 regressions plus 31 registry, geometry,
  capabilities, simulation, ownership/release, provenance and structured-error tests.
- **Nine Pixel 8 instrumentation tests passed** on Android 17/arm64-v8a: the original
  six ART thermometry/color/renderer tests plus three module tests. The new tests open
  only a non-USB 160×120 simulator or synthetic matrix source, never the attached HT-301.
- Exact Desktop parity remains **131072 LUT entries** and **2211840 rendered pixels**
  compared on each runtime, with zero differences. No fixture or assertion was weakened.
- Debug APK/test APK and lint passed: **zero lint errors / nine existing warnings**.
  `:app:testDebugUnitTest` is included in the final checks; it has no app JVM test sources.
- The shared-boundary check passed for 12 sources. `git diff --check` passed.
  No runtime dependency, LUT cache or additional physical camera module was added.

### Read-only open, initialization and counters

Android enumerated Infiray/T3-317-13 VID/PID `1514:0001`. Registry selection bound
that exact identity. Operator confirmed grayscale DISPLAY preview, prompt movement,
usable aiming and no Celsius legend/readings. Logs show complete 224256-byte frames,
221184-byte image plus 3072-byte trailer and roughly 25 callback FPS. Discovery/open
sent no radiometric initialization control. Background released the stream normally.

Two distinct explicit display-baseline runs were preserved. The later run appeared
in the saved logs before completion of the operator checks; its physical reconnect
chronology is not inferred. Both have three fresh display baseline frames and exactly
these SET requests, each returning two bytes:

| Request | Bytes | Evidence gate |
|---|---|---|
| 32772 | `04 80` | Genuine raw14, 15 receipt discards and distinct valid stage frames |
| 32800 | `20 80` | Normal range, another 15 receipt discards and distinct valid stage frames |
| 32768 | `00 80` | Shutter settling: 75 valid frames, then five changing valid frames |

GET_CUR was not used or claimed as a readiness gate. There was no command added by
the module adapter, no bit masking, and no fixed-delay-only readiness.

| Observation | First explicit run | Later explicit run | Previous accepted runs |
|---|---:|---:|---|
| Time to ready | 6.271 s | 6.354 s | 6.345 / 6.355 / 6.351 s |
| Discarded frames | 105 (15+15+75) | 105 (15+15+75) | Same policy |
| Changing frames at ready | 5 | 5 | 5 |
| Session held / rejected at ready | 30 / 85 | 30 / 83 | Held 30; similar settling rejections |
| Session malformed at ready | 5 | 3 | Separate from native malformed |
| Native malformed at ready | 31 | 3 | Ready-stream totals 2 / 4 / 5 |
| Native replacements at ready | 26 | 0 | Ready-stream totals 2 / 0 / 2 |
| Replacements during sampled ready stream | 28, unchanged | 2, unchanged | 2 / 0 / 2 |
| Sampled render median | 24.895 ms | 25.045 ms | Combined 24.952 ms |
| Sampled warmed thermometry median | 18.368 ms | 17.904 ms | Combined 18.674 ms |

The first range transition produced a larger native malformed/replacement burst.
It did not recur in the later display-baseline run; ready-stream malformed counters
stayed fixed in both runs. Its **precise cause is unknown**. These observations do
not establish a persistent timing/liveness regression or justify a protocol change.
The unchanged gates rejected transient data and delayed measurement until valid
frames. Sampled timings include the same scopes as the previous report, not per-frame
deadlines or a claim that every callback was rendered.

First ready raw/matrix ranges were `5375..5472` / `27.440..29.614 °C` and
`5610..5932` / `32.685..39.402 °C` respectively. Full inputs and original trailer vs
literal-center values are retained in the report; no center-region algorithm is inferred.

### Presentation, touch and conservative reopen

Operator confirmed recognizable ready Celsius output; all five palettes; Locked
25–45 °C and return to Auto; aligned touch/high/low markers; warmer hand readings
than background; color-only effects of presentation controls; and responsive interaction.
The saved render records contain Inferno Auto/Locked plus one valid touch. Other
live palette changes are operator confirmation supported by exact JVM/ART goldens,
not claimed as five distinct persisted palette-key samples.

The recorded touch at native `(210,145)` used original raw14 `5841` and native-equivalent
`37.6327 °C`, with namespaced `ht301.raw14` sample evidence and the legacy `raw14` key.
Module/model/dimensions/capabilities and separate trailer/literal center are present
in numeric renders. The visible accuracy warning remains explicit.

Close cleared colors, legend, cursor temperature and high/low. Reopen without unplug
or initialization stayed `RAW14_UNSETTLED`, with a grayscale preview and no temperatures.
Ten sampled reopened measurements were unavailable. The bounded reopen log contains
zero initialization/SET events. Both close paths logged native stream release, and
no module-ownership errors were logged. Native coordinates remain 384×288. No separate
live phone-rotation acceptance or unsafe USB fault injection is claimed.

### Issue #1 acceptance checklist

All seven [issue #1](https://github.com/LounaMaili/LMThermal/issues/1) acceptance
criteria are **completed** and ready for review/closure; the issue remains open.

| Criterion | Status | Evidence |
|---|---|---|
| Contract/registry responsibilities, capabilities, states and data lifetime documented | Completed | Architecture document; `CameraContract`, registry and owner KDoc |
| HT-301 adapted without test/hardware regression | Completed | Original numerical/color parity retained; Pixel initialization, presentation and reopen regression above |
| Different simulated dimensions/capabilities prove common core/UI independence | Completed | 160×120 preview-only module, generic matrix/mapper tests, Pixel simulator/presenter tests and boundary check |
| Selection, unknown/ambiguous identities, release and sole active session verified | Completed | Pure registry tests; awaited replacement, cancellation/detach/background and release-failure tests; Pixel close/reopen |
| Future module addition documented without HT protocol edits or shared UI duplication | Completed | Module addition guide and composition-root/resource binding contract |
| README/roadmap/specification/architecture/changelog updated on a dedicated branch, precise simultaneous-acquisition exclusion | Completed | Updated documents and `refactor/camera-module-foundation` |
| Real-model support limits and hardware/research requirements explicit | Completed | Only HT-301 validated; simulator not commercial support; future-model guide |

Large legacy HT-specific source moves are intentionally deferred, as permitted by
the milestone. They do not expose HT protocol types through the common contract.
No second commercial driver or dynamic plugin is added.

At completion of the module foundation, issue #2 remained open. The next recommended
milestone was French/English resources,
language/system-locale policy and persisted selection, complete legacy debug-label
migration, plurals/fallback/pseudolocale tests and locale-recreation regression proving
no automatic controls. Stable IDs, structured messages and resource namespaces are
prepared here. Native ROI/product features remain separate work.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Android internationalization — 2026-10-03

Branch: `feat/android-internationalization`, based on
`5f604f6043d512af4551e64346ca5d58c4d29ce7`. Policy, implementation, storage and
future language/module instructions are in [ANDROID_LOCALIZATION.md](ANDROID_LOCALIZATION.md).
Sanitized numeric/public-device evidence is retained in
[2026-10-03-android-localization.json](diagnostics/2026-10-03-android-localization.json).
No scene, USB path, phone serial, network endpoint or raw fixture is included.

### Pixel language selection and platform integration

The Pixel 8 runs Android 17/API 37; its unchanged system locale is `fr-FR`.
The actual in-app menu was checked against Android's stored application list:

| Choice/action | Platform list | Visible picker/UI |
|---|---|---|
| System | Empty | Système; French UI follows the phone |
| French | `fr` | Français; French UI |
| Force-stop and relaunch | `fr` | French preference retained |
| English | `en` | English; English UI |
| Force-stop and relaunch | `en` | English preference retained |
| Return to System | Empty | Système; French UI follows the phone |

The System/explicit French transition was checked even though both resolve to French.
Android OS app-language Settings lists LMThermal and English/French. Selecting English
there produced application `en-FR` with system `fr-FR` unchanged; the in-app policy
recognizes regional English as English. Returning from Settings uses platform state,
not an independent preference. The operator completed that app-only Settings selection
after automatic approval review blocked a region-screen tap as potentially device-wide.
No device-wide language/region change was made.

The packaged debug manifest references AGP's generated LocaleConfig and the disabled,
non-exported AndroidX auto-storage service. Packaged debug languages are `en`, `fr`,
`en-XA`, `ar-XB`; generated release manifest/config lists **only `en`, `fr`**. Debug
pseudolocales are test options in OS Settings, never entries in the normal language menu.

### Physical locale-recreation safety

The HT-301 was already in raw14 from the accepted previous milestone. The operator
opened it without initialization, switched language and explicitly reconnected. They
confirmed that the camera disconnected on the language change and preview returned
only after Connect/Open. No physical reset or initialization was needed for this test.

The bounded new-run log contains two read-only stream probes and two normal stream
releases. The first cancellation-to-release took approximately **51 ms**, followed
by **4.230 s** until the next explicit open/probe. A later background cancellation
released in approximately **63 ms**; its cause is not inferred from the log.

Nine sampled complete frames were exactly 224256 bytes (image 221184, trailer 3072),
with native 384×288 geometry, raw words **5044..5598**, approximately **25 callback FPS**,
and exclusively `RAW14_UNSETTLED`. No readiness or valid Celsius measurement is inferred
from raw14 alone. There are **zero recorded initialization/Zoom SET events** in this
new-run log. The first stream's last sampled counters were received 409/replaced 4/
native malformed 6/session held 0/rejected 405; the reopen sample was 53/1/3/0/52.
These startup/ineligible-frame counts retain their existing meanings; they do not
establish a localization-related transport regression.

The release/open gap plus the operator confirmation demonstrates explicit reopening,
and camera-free Activity tests separately verify no automatic open/measurement after
locale recreation. No camera controls, calibration, parser/LUT, session gates, palette
tables, coordinate transforms or existing machine-report formats changed. Previous
explicit-initialization and numerical/presentation parity evidence is retained; it is
not claimed as a new full initialization run for localization.

### Automated checks and coverage limits

- **147 core JVM tests passed**, preserving every module, parser, session, full LUT/
  matrix and color/mapping regression.
- **Eight AppCompat/Robolectric tests passed** across simulated API 26 and 32. They
  exercise the actual XML theme, disabled/non-exported auto-storage metadata,
  unsupported German system fallback, French/English resource application and
  file-backed cold-static-state restoration. No physical Android 8–12 device is claimed.
- **22 Pixel instrumentation tests passed**, zero failures/errors/skips: the original
  nine numerical/renderer/module tests, 12 localization/resource/configuration tests
  and one synthetic-layout test covering eight locale/orientation combinations.
  Numerical parity retains **131072 LUT entries** and **2211840 rendered pixels**
  compared with zero differences on each runtime. Instrumentation never opens USB.
- Resource completeness/positional-parameter/namespace parity passed for **87 English
  and 87 French keys**; all eight deliberate checker failure/contract tests passed.
  Shared camera-module boundary check passed for 12 sources.
- `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`,
  `:app:connectedDebugAndroidTest`, generated release locale/manifest checks and
  `git diff --check` passed. Lint reports **zero errors / 11 warnings**, limited to
  dependency-version notices and the existing Chrome OS ABI/data-extraction/icon
  warning categories; no translation/resource error remains.

An earlier run made while the phone was locked failed three configuration/layout
assertions. Final tests await actual asynchronous locale/orientation application,
and the complete unlocked run passed. A targeted layout run retains synthetic
screenshots outside Gradle's automatic app/cache cleanup. The final debug app is
reinstalled after that cleanup; no production screen-awake or rotation policy is added.

Fallback tests include a simulated unsupported `de-DE` system with empty app locales,
Pixel configuration contexts and a deliberately absent French key only in the test APK.
Production French is complete. Decimal values, coordinates, counts, accents, special
characters and test-only singular/plural resources pass; current product count labels
need no invented plural sentence. HT-specific and actual preview-only simulator bindings
resolve through their own namespaces, with stable machine IDs/geometry/capabilities.

English, French, expanded `en-XA` and RTL `ar-XB` were checked in portrait and
landscape with the real screen and explicitly synthetic 160×120 measurements.
Initial/control screenshots for all eight combinations were reviewed after waiting
for completed rotation. Readings, palette/range controls and longer labels remain
readable; wrapping rows and scrolling keep Connect/Close, initialization, language and
diagnostics reachable. Locked range dialog/Cancel and return to Auto worked in every
combination. The synthetic selected value remained exactly **36.5 °C** at `(80,60)`;
no physical measurement or native orientation change is inferred. Pseudo-RTL reverses
test text as intended; it is not a supported production translation.

### Issue #2 acceptance checklist

All ten [issue #2](https://github.com/LounaMaili/LMThermal/issues/2) criteria are
**completed** and ready for review. The issue remains open; no branch is merged here.

| Criterion | Status | Evidence |
|---|---|---|
| Complete default resources and French/English UI | Completed | 87 English fallback and 87 French keys; actual Pixel UI |
| UI/status/error/accessibility strings externalized, no visible Compose/core literals | Completed | Resource audit, exhaustive mappings and structured HT debug facts; unchanged core |
| Default System and persisted explicit app language on supported versions | Completed | Pixel picker/force-stop/OS Settings; AppCompat library storage tests on simulated API 26/32 |
| Unsupported system language and missing translation fallback | Completed | German System/resource-context tests; deliberate omission only in test APK |
| Variables, plurals, accents and special characters | Completed | Positional parity, French decimal/coordinate/count tests and test-only one/other plurals |
| Longer French/pseudo layouts keep actions and readings usable | Completed | Eight portrait/landscape combinations, control/dialog assertions and stable screenshot review |
| Locale recreation preserves validity, closes camera and requires explicit reopening, without automatic initialization | Completed | Physical operator/log evidence with zero SET/init events, RAW14_UNSETTLED reopen and camera-free Activity tests |
| Lint and targeted resource key/parameter checks | Completed | Zero lint errors, 87/87 parity and eight checker tests |
| Short future language/camera-module translation guide | Completed | ANDROID_LOCALIZATION.md and module architecture guide |
| README/roadmap/specification/affected docs/changelog on focused branch | Completed | Updated documents and feat/android-internationalization |

Physical older-OS testing remains additional coverage, rather than an unimplemented
storage mechanism. Future product screens/modules/languages must follow the documented
resource/parameter/accessibility checks. No ROI/export/recording work is included.
Independent absolute temperature accuracy remains unresolved and unchanged by localization.

## Android native rectangular ROI — 2026-10-03

### Generic numerical and lifecycle coverage

The core engine uses module-provided geometry and strict native half-open rectangles,
`[x1,x2) × [y1,y2)`. Drag conversion includes both touched cells; model bounds are
rejected rather than clamped. Statistics use authoritative Float32 Celsius values,
first valid row-major extrema ties and sequential Float64 accumulation. Optional 0/1
validity masks exclude invalid filler; zero-valid regions expose counts with no numbers
or extrema coordinates. These match the accepted LMTX v1 statistics/rectangle semantics.
No serializer, exchange export, capture or new fixture is implemented.

- **186 core JVM tests passed**, including 39 new ROI regressions. Geometry coverage
  includes 384×288, 160×120 and 7×19; tests cover strict bounds, cell-edge mapping,
  reversed/edge drags, all/partial/single/zero-valid data, invalid masks, nonfinite valid
  values, ignored filler, Float64 accumulation, ties and the canonical masked LMTX example.
- **Eight compatibility unit tests passed** on simulated API 26/32.
- **30 Pixel 8 / Android 17 instrumentation tests passed**, with zero failures/errors/
  skips. Eight new ART tests cover current-frame refresh, unavailable/closed/detached
  data, source/device/geometry replacement, superseded gestures/results, disposal,
  zero-valid masks, preview-only restrictions and palette/range independence.
- English, French, expanded `en-XA` and RTL `ar-XB` gesture/layout checks passed in
  portrait and landscape. The actual screen selected `[40,121) × [30,91)` on synthetic
  160×120 data: 4941 valid pixels, min 24.25 °C, max 36.5 °C, mean approximately
  30.450617 °C. Point selection `(80,60)` survived ROI mode/clear. Reviewed screenshots
  show aligned outlines, readings and enabled Clear actions; wrapping/scrolling keeps
  controls reachable. Screenshots await Compose's completed draw after worker completion.
- Localization parity passed for **95 English / 95 French keys**, with **10 checker
  self-tests**. French's additional `many` plural form retains the default `other`
  argument contract. The generic camera-boundary checker passed for **14 shared sources**.
- Core tests, compatibility tests, debug build, lint and connected instrumentation
  passed. Lint has **zero errors / 11 existing warnings** (dependency updates and
  existing Chrome OS ABI/data-extraction/icon categories). `git diff --check` passed.

An earlier locked-screen run timed out on Activity locale/orientation changes. After
unlocking, the complete suite passed. A test also initially sampled the intermediate
1×1 pointer-down result; it now awaits the final rectangle/result identity and actual
Compose UI state. No production wake-lock, orientation or camera behavior was changed.
The debug app is reinstalled after Gradle's test-package cleanup.

The operator found a real layout reflow during ROI creation, rather than a temperature
jump. A held-pointer regression reproduced a **205-screen-pixel** downward shift of
the ROI control in English portrait. Readout insertion changed layout height; in
landscape it could also resize the mapper-keyed viewport and cancel a drag. Font-scaled
fixed text/count/legend slots now exist before selection and through unavailable data.
The regression holds pointer-down across a completed Compose draw, compares viewport
and control bounds before/after, completes the drag, and repeats those bounds checks
through a synthetic measurement gap in all eight locale/orientation combinations.
After reinstalling the final APK, the operator repeated live small/large, reversed
and edge drags and confirmed the image/controls remained fixed with responsive readings.
Palette and Auto/Locked checks, Clear ROI and return to aligned Point tap/drag inspection
also passed. Saved final-run presentation events include both Auto and Locked states;
same-frame numerical independence is separately asserted by the JVM/ART tests.

For the final conservative lifecycle check, the operator selected
`[111,244) × [86,200)` (133×114), closed and reopened without unplugging or
initializing. Logs and the reviewed screen confirmed RAW14_UNSETTLED, a grayscale
aiming preview and the retained rectangle, with null ROI statistics, no Celsius
legend and no temperature/extrema readings. Reopening did not request initialization.
The operator's final Close was followed by `Stream released` about 61 ms later;
wireless ADB remained available. Retaining geometry without temperatures is the
expected behavior until a new explicit session supplies valid measurements.

### ROI calculation cost

On Pixel 8 ART, a synthetic 384×288 Float32 plane was warmed for 100 calculations,
then measured for 500 calculations per rectangle:

| Rectangle | Median | 95th percentile |
|---|---:|---:|
| 64×48 | 6.086 ms | 6.145 ms |
| Full 384×288 | 6.666 ms | 6.716 ms |

These engine measurements include validation of the whole supplied plane, so small
regions still incur that scan. They exclude the worker's owned matrix copy and UI
rendering. Analysis runs on a separate bounded latest-request worker, without blocking
USB acquisition or the UI thread. Same-measurement palette/range changes reuse analysis;
new frames recompute it. No performance-based numerical approximation is introduced.

### Live HT-301 numerical checks

Wireless ADB remained available with the HT-301 attached through USB Host/OTG.
Read-only Connect/Open produced DISPLAY_STREAM near 25 FPS with no Celsius readings
or initialization writes. Explicit operator initialization completed the unchanged
32772 → 32800 → 32768 sequence, including shutter settling and changing-frame
qualification, reaching RADIOMETRIC_READY in **6.312 seconds** in the saved first run.
No ROI interaction sends a camera action.

Separate live samples showed the expected relative scene response:

| Region | Half-open native bounds | Valid/total | Min | Max | Mean |
|---|---|---:|---:|---:|---:|
| Visible palm | `[209,323) × [92,194)` | 11628/11628 | 32.60563 °C | 37.96377 °C | 37.202964 °C |
| Cooler background | `[50,95) × [44,69)` | 1125/1125 | 27.039772 °C | 27.342623 °C | 27.199540 °C |

The approximately **10.003 °C** mean difference is between separate frames/regions,
not an absolute-accuracy reference. The palm screenshot visibly contains a hand;
the later background screenshot contains cooler room pixels beside a warmer face.
No hand is claimed in that later screenshot. A live transient also produced null ROI
statistics before subsequent valid measurements restored them, retaining the rectangle.

The operator confirmed usable edge/corner containment and responsive large-region
interaction. A 96,460-pixel selection `[20,384) × [0,265)` produced nine saved stable
worker samples with median **17.697 ms**, while median acquisition callback cadence
remained **25 FPS**. Live small background samples had median **6.553 ms**. Worker
times include the owned matrix copy and concurrent rendering/scheduling, unlike the
isolated engine benchmark above. Callback cadence is acquisition evidence, not a
claim that every camera frame was displayed; the presentation retains bounded
superseded-frame handling. Numeric evidence is kept locally outside Git, with no
new scene fixture or export format.

All ROI values retain the warning: **Native-equivalent temperatures; absolute
physical accuracy not yet independently validated.** HT-301 acquisition, parsing,
session/control and thermometry implementations and the accepted LMTX specification
are unchanged.

## Android LMTX v1 export — 2026-10-04

Branch `feat/android-lmtx-export`, base `98c99e060577ec947c067ed4a0a74051e94dcb28`.
Normative contract unchanged: [LMTX_FORMAT_V1.md](LMTX_FORMAT_V1.md).
Implementation/storage/local-limit guide: [ANDROID_LMTX_EXPORT.md](ANDROID_LMTX_EXPORT.md).
Sanitized numerical report: [2026-10-04-android-lmtx-export.json](diagnostics/2026-10-04-android-lmtx-export.json).
New live scene files/proofs/paths/image hashes stay in ignored local validation storage.

### Camera-free regression

- **224 core JVM tests passed**, including 38 exchange/publication/conformance cases.
  Coverage includes exact Float32 bits (signed zero/subnormal/extremes), alternate
  geometry, masks/positive-zero filler/no-valid counts, half-open ROI statistics,
  immutable array ownership and truthful unavailable previews; required schema,
  clocks, SHA/CRC/inventory, unsafe/duplicate ZIP members, forbidden flags/attributes,
  local/central contradictions, JSON limits/types and optional precise JSON values.
- **Eight API 26/32 compatibility tests passed**; **38 Pixel 8/Android 17 tests passed**,
  zero failures/errors/skips. Eight export ART tests cover exact source bits,
  immutable ROI/presentation, actual preview-only simulator, stale-value omission,
  cancel/failure/close/share gates, bounded generation replacement and prepared-file
  survival through Activity recreation without automatic camera ownership.
- The real export controls are included in the synthetic English/French/en-XA/ar-XB
  portrait/landscape test. Save/prepare/cancel are reachable; sharing stays unavailable
  before success. Held ROI gestures retain fixed image/control positions.
- All **11 shared corpus archives** have pinned full-file hashes/sizes/outcomes:
  temperatures-only, canonical mask, zero-valid, 160×120 visual, 7×19, sanitized HT-rich,
  compatible newer minor/precise JSON, opaque optional extension/future shape/palette,
  unsupported major/required feature and stale SHA. No private new fixture is added.
- Debug build/lint pass (**zero lint errors**, 11 existing dependency warnings).
  Localization parity is **110 EN / 110 FR** keys; **10 checker self-tests** and the
  **24-source camera boundary audit** pass. Production CLI reopens the canonical mask
  and the live ready artifact camera-free. `git diff --check` passes.

A ZIP-negative test initially modified a DOS attribute instead of the intended Unix
execute bit; the test fixture was corrected. The expanded layout test initially
compared a deliberate scroll position with a pre-scroll position; export interaction
checks were moved after held-gesture assertions. The full final suite then passed.
No camera acquisition/session or thermometry arithmetic was changed for these fixes.

### Pixel 8 + HT-301 acquisition export

Already-paired wireless ADB remained usable with the camera on USB Host/OTG. The
operator explicitly opened the display stream and initialized via the established
32772 → 32800 → 32768 sequence. The scene/ROI, Turbo and Locked 25–45 °C were selected
before Save; normal view/ROI interactions remained responsive.

The controlled ready capture independently matches a ByteBuffer encoding of the
retained original current matrix: **442368 bytes, every Float32 bit preserved**.
Geometry/source are 384×288, `ht301`, `HT-301/T3-317-13`, origin device. The native plane
is exactly the first 221184 bytes of the original 224256-byte transport; complete raw
words span **5109–5625**, without high-bit masking. Available calibration/settings,
lookup trace, distinct trailer/literal centers and high/low observations are retained
in the hash-verified module extension. Core extrema remain matrix-derived.

| Frozen field | Result |
|---|---|
| Native half-open ROI | `[169,291) × [76,168)` |
| Valid / total cells | 11224 / 11224 |
| ROI min / max / mean | 35.854744 / 37.583725 / 36.80378652077781 °C |
| ROI min / max coordinates | `(173,76)` / `(196,149)` |
| Presentation | `turbo`, `manual`, 25–45 °C |
| Snapshot including PNG/debug source proof | 83.646 ms |
| Streaming serialization, sync and reopen verification | 572.063 ms |
| Final archive | 408638 bytes |
| Owned temperature/evidence/encoded preview | 982020 bytes |
| Callback FPS at capture | 24.925 |

An earlier ready save took 172.628 ms snapshot + 767.161 ms serialization/checking,
469969 bytes, callback FPS 25.024. These are two observed runs, not latency guarantees
or a peak-heap measurement. One worker/job and 8 KiB streaming buffers are used; no
whole-ZIP allocation or frame queue is introduced. Subsequent logged acquisition
continued near 25 FPS and the operator confirmed responsiveness during preparation.

**Export sent zero new controls.** The scoped pre/post logs contain only the three
explicit initialization SETs per deliberately initialized session; reopening and
exporting unknown raw14 did not send them. Common persistence has no transport/control
reference, and camera/session/controller implementations are unchanged.

### Publication, stale-value and lifecycle checks

The complete prepared capture was pulled before destination interaction. The operator
changed app language and orientation and selected a local DocumentsUI/Downloads SAF
destination. The saved destination is **byte-for-byte identical** to the retained
408638-byte prepared archive (same capture UUID, bits, ROI, palette/range and evidence),
without another Save gesture. Debug provider readback also reports equality after
successful close. The finalized Share chooser was exercised/dismissed; no file was
sent to an external recipient. Its narrow content-URI grant is also tested on ART.

Reopening without initialization showed **RAW14_UNSETTLED**, with no Celsius readings.
A 67541-byte visual-only save was reopened/verified: no temperature/native/acquisition/
calibration payload, old provenance/statistics or effective Celsius bounds. ROI geometry
and the palette identity may remain. Another unsettled visual save was 65585 bytes;
its SAF readback matched. A subsequent display-preview picker cancellation showed
**Export cancelled**, removed its private stage and left Share disabled; the operator
then closed the camera. Open/write/close/storage-full/delete failures are injected in
JVM/ART tests instead of filling the phone's storage.

**Lifecycle observation:** selecting/saving/cancelling a destination, rotation and
language changes all release the camera. Investigation traces this to the inherited
unconditional `MainActivity.onStop → leaveForeground → owner.background` policy;
there is no USB technical requirement for rotation/picker release. The prepared
ViewModel-owned artifact survives independently, which is verified here. This branch
preserves the conservative policy; live continuity across selected transitions is a
separate ownership/lifecycle follow-up. Returning requires explicit Connect/Open;
there is no implicit initialization or revival of stale Celsius data. Continuous
acquisition throughout external picker UI is therefore **not claimed**.

The pure checker/API rules and corpus were finalized after the live run without
changing HT data acquisition, measurement or rendering semantics; the final complete
JVM/compatibility/Pixel/build/lint suite passed again. The debug APK is reinstalled
following test-package cleanup, with the camera closed.

### Issue #5 acceptance status

| Criterion | Status / evidence |
|---|---|
| Accepted contract/canonical specification | Complete; normative document unchanged |
| Pure coherent owned snapshot, exact floats/evidence, clocks/provenance | Complete; JVM/ART/source-proof and live reopen |
| Simulator/non-HT geometry without fabricated temperatures | Complete; actual 160×120 simulator and 7×19 corpus |
| HT evidence and physical-accuracy warning | Complete; native/full transport/extension, distinct centers |
| No stale/partial success; failure/cancel/recreation safety | Complete; unavailable live captures, private/destination checks and injected failures |
| Bounded off-thread preparation and zero camera writes | Complete; streaming worker, code boundary audit, live logs/FPS |
| New-document local SAF/save/share behavior | Complete for local DocumentsUI plus injected provider failures; cloud atomicity/durability not promised |
| Shared conformance fixtures | Complete initial 11-case corpus |
| Android → Desktop exact interoperability | **Pending Desktop #1 implementation** |
| EN/FR/pseudolocales, regression, documentation/changelog | Complete |

Android #5 is **implementation-complete / interoperability-pending**, not closed.
Desktop #1 remains open/unimplemented. No Desktop product code is changed.
**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Camera lifecycle continuity — 2026-10-06

Branch `fix/camera-lifecycle-continuity`, based on Android main `1cbdd776`.
Pixel 8 / Android 17, wireless ADB, fresh HT-301 USB host connection. The operator
confirmed display aiming, ready responsiveness, both orientations and preserved ROI/
palette/range. The [event policy](ANDROID_CAMERA_LIFETIME.md) replaces the historical
unconditional stop release described in the export milestone above.

Sanitized [structured evidence](validation/android-camera-lifetime-2026-10-06.json)
contains counts/events without phone/USB identifiers or scene payloads. One retained
owner was observed; its active-session count never exceeded one.

- Display baseline used full 224256-byte frames with display words approximately
  32768–33023 and native callbacks near 25 FPS. Explicit initialization reached first
  ready in **6.396 s**. Exactly three SET transfers completed with two bytes each:
  **32772, 32800, 32768**. The stable initial raw14 frame was **5588–6259**. Readiness
  still required changing valid frames, not merely a settling counter.
- First picker: ready session **1** remained owned through launch, roughly 86 seconds
  in external DocumentsUI, and cancellation. Recorded received counts increased
  **4072 → 5840**; instantaneous callback samples were approximately 25 FPS. Prepared
  bytes were identical while covered. Cancellation removed staging and disabled Share;
  the operator confirmed the preview continued without reconnect.
- Portrait → landscape → portrait: same session **1**, **zero releases**, **zero extra
  opens/actions/SET writes**. Configuration stop/start pairs recorded counts **11235**
  and **13090**, with callbacks **25.024 / 24.900 FPS**. Selected ROI
  `[131,229) × [130,191)`, ROI mode, Turbo and Locked **25–45 °C** persisted. The
  286812-byte prepared ROI archive was identical after both recreations.
- Successful second destination transaction: same ready session **1**, received
  **15621 → 15721**, zero extra writes or release. Local Downloads publication closed
  successfully and debug SAF readback matched the finalized archive exactly. Share
  was not opened during this continuity test; its finalized-file grants/failure guards
  passed the existing instrumentation regressions.
- Close released session **1 once**, in **59 ms**. Explicit reopen created session
  **2**, correctly **RAW14_UNSETTLED**, with grayscale preview and no Celsius values.
  It sent no initialization. Physical detach then released session **2 once**, in
  **211 ms**, cleared the view and removed Android enumeration; wireless ADB stayed usable.
- Fresh display session **3** released once on Home/background, in **103 ms**.
  Returning required explicit Connect/Open; no preview/session automatically revived.
- Two observed app-locale changes released display sessions **4 / 5 once each**, in
  **107 / 97 ms** after release started. The operator confirmed reconnect remained
  required. No radiometric initialization followed. The finalized capture bytes stayed
  unchanged through Close, detach, background and locale releases.

Initial readiness counters were **105 discarded** (including **75 shutter frames**),
**30 held**, **83 rejected**, **2 native malformed**, and **5 qualifying live**.
Later normal stream liveness/invalid bursts continued to be rejected/recovered by the
unchanged gates; neither uninterrupted READY nor zero dropped frames is promised.
While external SAF covered the app, latest-buffer replacement increased as worker
throughput fell; callback samples remained near 25 FPS and return stayed responsive.
An already-raw explicit reopen also showed a short malformed-frame burst (17 counted)
without promoting readiness or leaking old Celsius. No unsafe USB faults were induced.

All **232 core JVM**, **10 Android JVM** (eight API 26/32 AppCompat compatibility plus
two Activity ownership tests), **39 Pixel instrumentation**, and **10 localization
checker self-tests** pass without skips/failures. Debug assembly/lint (zero errors;
four existing warnings), localization parity, module boundaries and `git diff --check`
pass. Fatal-error cleanup, stale callbacks, repeated Close/disposal and screen-off's
picker override are tested with injected generic sources; no physical fatal USB failure
was forced. Camera/thermometry/native transport/Accepted LMTX contract and export codec
files have no diff. No recording/sequence feature or new scene fixture is introduced.

### Final APK repeat-entry check

The final manifest uses `singleTask` so repeat launcher/USB entry cannot create another
camera composition root. A separate final-APK live run confirmed external DocumentsUI
was covering ready session 1. Re-entering LMThermal delivered the intent to the existing
Activity, cancelled SAF normally and retained the same owner/session: received counts
**649 → 1522**, callbacks **25.000 / 25.692 FPS**, opens 1, releases 0, explicit actions 1.
Actual SET logs remained exactly **32772, 32800, 32768** before and after. A subsequent
configuration recreation retained that session, with callbacks **25.024 FPS**. Close
released it once in **70 ms**; explicit reopen without replug received changing raw14
frames near 25 FPS in **RAW14_UNSETTLED**, with no Celsius or additional writes. Its
initial native malformed count stabilized at 40; this does not establish a new readiness.
Final Close released session 2 once in **76 ms**: this separate run ended with two opens,
two completed releases, zero active sessions and still only the three initialization SETs.

The operator also reported a blank initial Connect after the test/reinstall until a
fresh replug. Android event history shows CAMERA and USB permission dialogs; that brief
attempt contains no frame or transport-error log before task removal. Its cause is not
established by the retained evidence. The operator recalled prompts only at first connection
and could not recall the blank attempt's session state. The subsequent same-process
no-replug reopen above worked and was confirmed by the operator. Do not infer that
replug is required for every reopen, or claim the initial
permission/startup path is fully explained by this lifecycle test.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
