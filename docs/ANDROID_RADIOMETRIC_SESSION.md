# Android evidence-gated radiometric session

Milestone: `feat/android-radiometric-session`, based on foundation main
`f6d19dbde4d031991691cfbc6cb1d757e00f1556`.

## Hardware acceptance status

**Blocked:** direct USB Zoom Absolute GET_CUR is 1 after a physical reconnect; the
Desktop-derived baseline requires 0. The app fails before any write. Structural/session
logic and Desktop trace parity pass on fixtures, but hardware transition/readiness is not
validated. See ANDROID_VALIDATION.md. Do not merge as a completed radiometric milestone.

## Ownership and explicit action

`core/RadiometricSession` is a plain JVM event-driven component. It receives immutable
frames/inspections, a monotonic clock and a narrow `RadiometricControl` interface.
It owns session decisions, independently of acquisition, preview normalization and Compose.
`CameraController` owns one fresh session per open connection on the existing serialized
I/O worker. The native callback and StateFlow still each retain only the latest payload/state.

**Connect / Open**, attach, launch, foreground, recreation and raw14 discovery never send
setting writes. Only **Initialize radiometric** submits a generation-bound request; duplicate
requests cannot restart an active sequence. The button is available only for an observed
display stream outside an active sequence. An error can be explicitly retried if display
returns, but the fresh baseline/readback gates still apply. Otherwise close/reconnect and
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
Every write is followed by exact readback; failure/mismatch stops all later commands.

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
The exact current implementation, rather than approximate APK timeline wording, defines:

1. On explicit request, qualify **three fresh consecutive DISPLAY frames** within 150 receipts.
   Invalid frames reset baseline; any raw14 baseline aborts. Read current zoom and require **0**.
2. Wait at least **500 ms after baseline qualification**, then write/readback **32772**.
3. Discard **15 receipts**, then require **two consecutive distinct valid raw14 images** within
   another 150 receipts. Settings/calibration/index rejection blocks the stage.
4. Wait at least **600 ms after stage verification**, then write/readback **32800**.
   Repeat the same 15-receipt/two-image gate and establish known normal-range host state.
5. Wait at least **500 ms after range verification**, then write/readback **32768**.
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

Each source has an immutable generation and ownership predicate. Close/background/detach
invalidates ownership immediately, clears the request and presentation, cancels the worker,
and releases the UVC handle before the Android descriptor. Checks around every control
prevent later commands/readbacks once cancellation is observed. A transfer already in flight
may finish within its timeout; cancellation neither rewinds camera state nor replays controls.
Reopen creates a new session and observes actual frames. Old generations cannot qualify it.

`LMThermalSession` emits structured JSON with monotonic times, generation, state/stage,
mode, consumed sequence, requested/readback values, baseline/discard/shutter/live/rejection/
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
