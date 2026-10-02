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

Next required step: resolve direct USB GET_CUR versus Desktop V4L2 baseline/readback semantics
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
