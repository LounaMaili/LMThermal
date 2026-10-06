# Android evidence-gated radiometric session

> Current extension (2026-10-03): the separate Kotlin finite-LUT/current-frame
> measurement gate and separate Celsius/touch presentation are implemented and
> validated. The original milestone account below describes its earlier scope.
> Session controls, timings and liveness criteria are unchanged. See
> [ANDROID_THERMOMETRY.md](ANDROID_THERMOMETRY.md) and
> [ANDROID_CELSIUS_PRESENTATION.md](ANDROID_CELSIUS_PRESENTATION.md).

Milestone: `feat/android-radiometric-session`, based on foundation main
`f6d19dbde4d031991691cfbc6cb1d757e00f1556`. Current frame-acceptance child:
`fix/android-radiometric-frame-acceptance`, based on Zoom audit tip `3df0766`.

## Hardware acceptance status

The original port's readback blocker is superseded by the frame-acceptance child branch.
A deliberately single-command Pixel 8 test reproduced DISPLAY→raw14 after an exact
32772 transfer, without GET_CUR. The subsequent fresh-display full sequence reached RADIOMETRIC_READY in 6.312 seconds.
See ANDROID_VALIDATION.md for per-stage counters and operator confirmation.
No LUT, Celsius or independent physical-accuracy claim is made.

## Ownership and explicit action

`core/RadiometricSession` is a plain JVM event-driven component. It receives immutable
frames/inspections, a monotonic clock and a narrow `RadiometricControl` interface.
It owns session decisions, independently of acquisition, preview normalization and Compose.
`Ht301CameraController` (originally `CameraController`) owns one fresh protocol session
per open connection on the existing serialized I/O worker. The native callback and
StateFlow still each retain only the latest payload/state. Application-level module
selection and awaited source release use the separate
[camera session owner](CAMERA_MODULE_ARCHITECTURE.md); the HT-301 protocol gates below
are unchanged.

**Connect / Open**, attach, launch, foreground, recreation and raw14 discovery never send
setting writes. Only **Initialize radiometric** submits a generation-bound request; duplicate
requests cannot restart an active sequence. The button is available only for an observed
display stream outside an active sequence. An error can be explicitly retried if display
returns, but the fresh baseline/frame-evidence gates still apply. Otherwise close/reconnect and
observe actual camera state. There is no reset command or assumed rollback.

## Native camera controls

`NativeUvcTransport` implements both acquisition and the separate semantic control interface.
Three enum operations map to named constants, never arbitrary selectors/bytes from UI:

| Operation | Zoom Absolute uint16 | Little-endian payload |
|---|---:|---|
| SELECT_RAW14 | 32772 / 0x8004 | 04 80 |
| SELECT_NORMAL_RANGE | 32800 / 0x8020 | 20 80 |
| SHUTTER_REFRESH | 32768 / 0x8000 | 00 80 |

JNI uses the **same authorized `uvc_device_handle_t`** as streaming. The standard camera
terminal zoom selector is `UVC_CT_ZOOM_ABSOLUTE_CONTROL` (0x0b), `wValue=0x0b00`;
`wIndex=(descriptor terminal ID << 8) | descriptor control-interface number`.
SET_CUR uses 0x21/0x01, GET_CUR uses 0xa1/0x81. Two-byte unsigned values retain bit 15.
Every write must complete with exactly two transferred bytes. This establishes **control
transfer completed**, not firmware acknowledgment. The portable `RadiometricControl`
interface has no GET method. Direct GET_CUR observations remain available only through
the separate debug inventory. Invalid/short/failed SET results stop all later commands.

This reproduces pinned libuvc `ctrl-gen.c` zoom requests using `libusb_control_transfer`.
The bridge checks an **exact two-byte** transfer and uses a **1000 ms** timeout. Upstream
helpers use unlimited timeout and return a zero-byte transfer as numeric zero, which can
be mistaken for `UVC_SUCCESS`; neither ambiguity is suitable for a cancellable session.
The pinned internal libuvc handle layout supplies the USB handle/interface. No vendor
request, extension unit, second device handle or format conversion is introduced.
libusb handles synchronous controls while its event thread continues streaming callbacks.
Open/read/stats/controls/close are serialized on the source worker, never the UI thread.

After a write, native transport clears its pending pre-control payload. The consumed sequence
is captured atomically with the payload and exposed separately from the newest callback
sequence. Fifteen subsequent receipts remain mandatory; clearing a slot alone proves no
camera effect. Source bytes and all four trailer rows remain unchanged.

## Desktop-derived stages

Oracle: `LMThermal-Desktop/radiometric_session.py::HT301RadiometricSession`, plus its tests.
Desktop supplies the timing and frame-gate oracle below. Android intentionally replaces
its V4L2 control-value checks with exact SET completion plus frame-observed response:

1. On explicit request, qualify **three fresh consecutive DISPLAY frames** within 150 receipts.
   Invalid frames reset baseline; any raw14 baseline aborts. No control-value baseline is required.
2. Wait at least **500 ms after baseline qualification**, then complete an exact SET **32772**.
3. Discard **15 receipts**, then require **two consecutive distinct valid raw14 images** within
   another 150 receipts. Settings/calibration/index rejection blocks the stage.
4. Wait at least **600 ms after stage verification**, then complete an exact SET **32800**.
   Repeat the same 15-receipt/two-image gate and establish known normal-range host state.
5. Wait at least **500 ms after range verification**, then complete an exact SET **32768**.
6. Enter SHUTTER_TRANSIENT; discard **75 structurally valid raw14 frames**. Invalid/display
   frames do not advance this count. The 75th accepted discard is still transient evidence.
7. Require **five consecutive changing valid raw14 frames with consistent summary extrema**.
   Image-only SHA-256 equality rejects held images; trailer-only changes cannot create liveness.
   Invalid, calibration-rejected, summary-mismatched and held frames reset the live streak.
   Initial post-shutter evaluation is bounded by **225 receipts** (150+75).

Timing is implemented through monotonic deadlines while acquisition/preview continues,
not sleeps on the Android main thread. Frames consumed during a minimum-delay window
cannot count as stage/readiness evidence. Actual command intervals exceed these minima
because each stage additionally waits for frame evidence. No extra post-shutter sleep is
invented: the 75-frame/liveness gates provide the settling policy.

## State and frame contracts

Classification stays DISPLAY / RAW14 / INVALID. RAW14 with rejected calibration/settings
remains a candidate observation; it cannot qualify readiness. Session states are DISCONNECTED,
DISPLAY_STREAM, SWITCHING_TO_RAW14, RAW14_UNSETTLED, SHUTTER_TRANSIENT,
RADIOMETRIC_READY and ERROR. On open into raw14, host range is unverified, no writes occur,
and the session stays conservatively unsettled even with many changing valid images.

A held/invalid frame after readiness demotes to RAW14_UNSETTLED; five subsequent acceptable
changing frames can restore readiness. Returning from ready to display is an explicit lost-mode
error. High/low summary coordinates must identify actual image extrema; the trailer center
is not asserted equal to `(192,144)`.

**Android RADIOMETRIC_READY qualifies structural and liveness evidence only.** Desktop also
calls `make_measurement` on every qualifying frame to reject undefined LUT outputs. That
additional **thermometry validity gate is deliberately deferred** here because this milestone
forbids the LUT/Celsius port. Android does not claim numerical measurement parity or expose
any temperature. The future measurement layer must check every selected LUT entry before
publishing readings, even when session readiness is true. This is the explicit scope boundary,
not a statement that all structurally valid indices necessarily have defined temperatures.

## Cancellation and diagnostics

Each source has an immutable generation and ownership predicate. Rotation and the tracked
SAF picker retain the same source/generation; they cannot manufacture readiness or enqueue controls.
The [generic UI lifetime policy](ANDROID_CAMERA_LIFETIME.md) decides actual ownership termination.
Close/background/detach
invalidates ownership immediately, clears the request and presentation, cancels the worker,
and releases the UVC handle before the Android descriptor. Checks around every control
prevent later commands once cancellation is observed. A transfer already in flight
may finish within its timeout; cancellation neither rewinds camera state nor replays controls.
Reopen creates a new session and observes actual frames. Old generations cannot qualify it.

`LMThermalSession` emits structured JSON with monotonic times, generation, state/stage,
mode, consumed sequence, requested commands/actual transfer lengths, baseline/discard/shutter/live/rejection/
held/malformed counts and image word ranges. Held counts include structurally valid repeated
images during stages and shutter, not only held rejections after settling. `LMThermal` keeps
periodic callback FPS/replacement/size diagnostics. Neither logs raw payloads, image digests,
serials or private scene contents. Automatic app-private debug frame saving has been removed.
No user logging/capture feature is added.

The JVM suite uses sanitized foundation fixtures and a 111-frame shutter trace generated
by executing Desktop defaults: 75 shutter discards, 31 held images and five changing valid
images. Every state/rejection in that trace is compared against Android. It exercises the
LUT-defined fixture domain; numerical parity over other frames remains a later milestone.

For build/install commands see [CONFIGURATION.md](CONFIGURATION.md). For real-device numeric
results and scope limits see [ANDROID_VALIDATION.md](ANDROID_VALIDATION.md).

## Zoom control semantics diagnostic (2026-10-02)

The GET-only Android inventory and Linux cache audit are recorded in
[ZOOM_CONTROL_SEMANTICS.md](ZOOM_CONTROL_SEMANTICS.md). Ordered Android queries show CURRENT=1 after stream open, 3 after INFO,
65535 after MAX, and 0 after zero-valued queries. CURRENT is not a reliable
command-state acknowledgment on this tested path. Fresh Linux V4L2 and Desktop
reads both return 0; the privileged direct transfer failed with libusb ERROR_IO
(USBFS EBUSY) without claiming an interface or detaching uvcvideo. A control-only
trace found no physical Zoom transaction for one repeated V4L2 GET returning zero.
Those observations were GET-only. This child branch removes baseline-zero and
post-write GET equality gates following a controlled single-32772 confirmation.
V4L2 control state is distinct from a physical USB response; expected frame evidence
still gates each later command.

## Restricted single-command diagnostic

Debug builds offer **Test raw14 transition (32772)** on an open DISPLAY stream.
It requalifies three fresh frames, waits the existing first-stage minimum, issues
only `04 80`, validates exact SET length, clears the pending pre-control payload,
discards 15 receipts and requires two distinct structurally valid raw14 images.
It has no GET/range/shutter operation. One instance accepts only one explicit request;
concurrent full-session/inventory work is excluded. Failures/cancellation never replay.
Successful completion leaves the ordinary session **RAW14_UNSETTLED**, not ready.

Bounded debug-only numeric evidence survives ADB/logcat loss in app-private
`files/raw14-transition.jsonl` and `files/radiometric-session.jsonl`. Each explicit
experiment replaces its own report. These are developer diagnostics, not a user
recording/export feature; no image payloads, hashes or private scene structure are saved.

```bash
adb shell run-as org.lmthermal.app cat files/raw14-transition.jsonl
adb shell run-as org.lmthermal.app cat files/radiometric-session.jsonl
```

Single-command evidence: baseline sequences 125–127, words 32803–32985;
SET `0x21/0x01`, `0x0b00/0x0100`, `04 80`, exact 2 bytes. First valid raw14:
sequence 142, 111 ms after transfer completion, words 5311–5825. After 15 receipts,
sequences 156–157 qualified two distinct valid images (final 5312–5828). All complete
frames retained 224256 / 221184 / 3072 bytes. No 32800/32768 or GET was issued.
This is cross-platform confirmation of the existing command, not a new discovery.

## Full Pixel 8 acceptance

A fresh display connection qualified sequences 2385–2387 (32777–33008).
All three SETs completed with exactly two bytes. First valid raw14 after 32772:
sequence 2402, 116 ms after the logged transfer return, 5355–5834. The first stage
verified sequences 2416–2417 after 15 receipt discards. The normal-range stage
verified 2448–2449 after another 15; transient malformed/mixed frames were rejected.
Shutter then discarded 75 valid frames, followed by five changing summary-consistent
frames. Ready event: sequence 2542, **6.312 s** after initialization request.
Final layout 224256 / 221184 / 3072; ready image words 5421–5819; callback FPS 25.

The operator confirmed readiness, responsive recognizable preview, and Close/Open
into existing raw14 returning **RAW14_UNSETTLED** without initialization. No Zoom GET
controlled this run. The separate native-equivalent LUT validity gate remains deferred.
Host receipt/event timings are not sensor exposure timestamps or physical calibration.
