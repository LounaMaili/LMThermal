# LMThermal — Application Specification

> Staged capture produces native 14-bit indices and the reconstructed lookup
> matches official APK arithmetic. Android now exposes current-frame native-equivalent
> measurements, Celsius palettes and touch inspection, gated by supported settings,
> finite lookup values and shutter/liveness evidence. Independent physical accuracy
> remains unvalidated. See [RADIOMETRIC_INITIALIZATION.md](RADIOMETRIC_INITIALIZATION.md).
> The feature list is the product roadmap; completed milestones are described below.

## Android foundation milestone

The Android product lives in this repository (`app/` + JVM `core/`). The first
milestone implements VID/PID-filtered Android USB authorization, unconverted
384×292 YUYV acquisition through native libusb/libuvc, exact frame validation,
288+4 image/trailer separation, Desktop-equivalent display/raw14 inspection and
a latest-frame grayscale aiming preview. Acquisition/parsing run off the UI thread.
Explicit session controls and current-frame native-equivalent measurements are now available
as described below; opening remains read-only. Background/detach
release the stream; foreground/replug allow explicit reopen.

See [ANDROID_FOUNDATION.md](ANDROID_FOUNDATION.md) for ownership, lifecycle,
interfaces and future session/thermometry boundaries, and
[ANDROID_VALIDATION.md](ANDROID_VALIDATION.md) for real-device evidence.

## Current Linux measurement foundation

The sibling desktop repository now has a PyQt-independent
`HT301RadiometricSession` for the **observed** 384-wide, range-120, lens-68,
shutter-fix-1.5 path. From display mode with zoom readback 0 it sends only the confirmed standard
UVC zoom values `32772 -> 32800 -> 32768`, verifies raw14 after each stage,
then requires a minimum 75 valid post-shutter frames plus consecutive distinct
live frames with complete calibration, consistent trailer extrema and defined
lookup outputs. A fixed wait alone cannot establish readiness because held
images were observed after the 75-frame interval. Invalid or repeated frames
demote readiness. An already-raw14 stream of unknown host state stays
read-only rather than being assigned a presumed normal range.

The measurement object keeps the original full raw14 words, a native-equivalent
384 × 288 temperature matrix, settings/calibration inputs, summary extrema,
timestamps, and both trailer center and literal pixel `(192,144)` separately.
No high-bit masking, display-Y substitution or empirical correction is used.
The numerical lookup matches inspected official x86_64 arithmetic for this
branch, but its absolute physical accuracy is still unvalidated.

A lightweight OpenCV **diagnostic operator preview** shows display Y before
transition and contrast-normalized raw14 after it. It overlays session mode,
readiness, transient/invalid status, center crosshair, valid high/low markers
and optional ROI guides. Click inspection can show original raw index and
native-equivalent output only on a valid ready frame. Normalization is
display-only and cannot feed thermometry. This preview is not the planned
application UI, Celsius palette lock, or an accuracy validation.

Independent multi-target surface measurements remain future work. Their
absence does not prevent development of the structural measurement pipeline;
it does prevent claiming calibrated physical temperature accuracy.

## Target Platform

**Primary**: Android (USB Host API, connected via USB-C OTG)
**Development/Prototyping**: Linux desktop (Python, for protocol validation)

## Core Features

### 1. Live Thermal View
- Real-time thermal camera feed
- Selectable color palettes (ironbow, rainbow, grayscale, etc.)
- Adjustable emissivity and distance parameters

### 2. Temperature Measurement
- **Spot measurement**: tap/point to read temperature at any pixel
- **Area measurement**: draw rectangles, circles, or polygons to get min/max/average
- **Multiple points**: place several measurement markers simultaneously

### 3. Temperature Range Lock
- **Fixed min/max scale** — lock the color palette to a specific temperature range
- Ensures consistent, comparable colors across different captures
- This was the key missing feature from the manufacturer's app

### 4. Photo Capture
- Save still images with temperature overlay
- Export formats:
  - **RJPEG** (Radiometric JPEG) — standard format, readable by FLIR Tools, Thermimage, etc.
  - **TIFF radiometric** — for professional thermal analysis software
  - **PNG** — visual only (no embedded temperature data)
  - **CSV** — raw temperature matrix for custom analysis

### 5. Video Recording
- Record thermal video sequences
- Embed temperature data (each frame carries radiometric data)
- Export formats:
  - **Radiometric video** — TBD format (conteneur + raw frames)
  - **Standard MP4** — visual only
  - **TIFF sequence + JSON metadata** — for analysis

### 6. Capture Gallery & Comparison
- Browse saved captures (photos + videos)
- Side-by-side comparison of two captures
- Temperature difference overlay (ΔT between two images)
- Annotations (text, arrows, areas of interest)

### 7. Settings & Calibration
- Emissivity preset materials (concrete, metal, skin, etc.)
- Reflected apparent temperature
- Distance to target
- Unit selection (°C / °F)
- Auto or manual temperature range

## Export Formats

### RJPEG (Radiometric JPEG)
A standard JPEG file with embedded radiometric (temperature) data in EXIF/metadata chunks. This is the closest thing to a universal thermal image format:
- Readable by: FLIR Tools, Thermimage (R), PyThermimage (Python), various thermal analysis software
- Contains: visual image + full temperature matrix + measurement parameters

### TIFF Radiometric
Standard TIFF with floating-point pixel values representing temperatures.
- Widely supported in scientific software (ImageJ, MATLAB, etc.)

### CSV
Simple spreadsheet-compatible format: one row per pixel line, temperature values in °C.

## Non-Goals (for now)
- iOS support (no USB Host)
- Cloud storage / sync
- Multi-camera support
- Real-time streaming over network

## Android explicit radiometric session (2026-10-02)

Connect/Open remains read-only. Explicit Initialize radiometric qualifies three fresh display
frames, then executes the validated raw14/normal-range/shutter sequence with exact
SET transfer completion and observed frame evidence. GET_CUR does not control progression. Fifteen-receipt transition discards, two-image stage evidence, 75 valid shutter
discards and five changing valid summary-consistent images gate structural readiness.
Unknown pre-existing raw14 remains unsettled. Lifecycle cancellation stops later controls;
reopen never replays initialization. The separate finite-LUT measurement gate is now implemented
in core; session state/timings/liveness criteria are unchanged.
Architecture, timings and cancellation limits: [ANDROID_RADIOMETRIC_SESSION.md](ANDROID_RADIOMETRIC_SESSION.md).

The previous GET_CUR blocker is superseded following a controlled single-32772 Pixel 8
confirmation. Full-sequence structural/live hardware acceptance passed on Pixel 8; see ANDROID_VALIDATION.md.
Debug **Test raw14 transition (32772)** is a one-shot developer experiment, separate from
full initialization; it has no range/shutter/readback operation or readiness claim.

### Debug Zoom inventory

An explicit GET-only diagnostic on the existing worker/handle records descriptor-selected
Zoom queries and exact response lengths. It cannot select arbitrary controls or request SET.
Inventory results do not qualify readiness or change session gates. See
[ZOOM_CONTROL_SEMANTICS.md](ZOOM_CONTROL_SEMANTICS.md).

## Android normal-range thermometry milestone (2026-10-03)

Platform-independent `FrameParameters` → `NativeEquivalentThermometry` → owned
`RadiometricMeasurement` reproduces the Desktop normal branch with deliberate Float/Double
rounding. All 16384 LUT entries and full settled matrices have golden regressions.
Only width 384, host range 120, lens 68 and shutter fix 1.5 are supported.
Unsupported inputs, invalid copies/indices, inconsistent trailer extrema and observed
nonfinite lookup values produce no measurement. Undefined unused LUT entries remain NaN.

`RADIOMETRIC_READY` is structural/liveness readiness; `MeasurementGate` evaluates each
current frame separately. Held, invalid, not-ready and disconnected states clear Celsius.
The worker builds a fresh table each measurement frame; there is no calibration cache.
Raw preview normalization remains independent of measurement. Native coordinates and
image bytes are unchanged; presentation maps high/low markers to the fitted image.
Trailer center and literal `(192,144)` are displayed separately alongside matrix/high/low
outputs. Numeric developer evidence is bounded and contains no scene payloads.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

ROI, exports and recording remain future milestones. Touch/Celsius presentation is
implemented separately below. See
[ANDROID_THERMOMETRY.md](ANDROID_THERMOMETRY.md) and [ANDROID_VALIDATION.md](ANDROID_VALIDATION.md).

## Android Celsius presentation milestone (2026-10-03)

A valid current measurement feeds a separate bounded latest-request renderer. White
hot, Black hot, Inferno, Iron-like/OpenCV HOT and Turbo match Desktop color tables.
Auto uses linear 2nd/98th percentiles with a centered minimum 1 °C span. Locked bounds
are exact finite minimum < maximum (default 25–45 °C). Only display levels clip;
measurement arrays remain unchanged. Five legend ticks use the effective Celsius range.

Tap/drag inspection stores native `(x,y)` and reads original raw14/Celsius from the
coherent displayed measurement. A single centered `ContentScale.Fit` mapper handles
letterboxes, touch, cursor and red/cyan high/low markers; outside touches are ignored.
Coordinates remain 384×288 regardless of layout, without source rotation/mirroring.

Display/raw-unsettled use non-temperature grayscale aiming output. Measurement loss
clears Celsius colors, legend, cursor value and extrema overlays immediately; selected
coordinate can survive for the next valid measurement. Trailer/literal center remain
separate in diagnostics. Portrait/landscape layouts prioritize image/readings/controls;
developer controls remain debug-only inside optional diagnostics.

No UVC/session/thermometry behavior or LUT cache is added. Architecture, exact Desktop
references, worker counters and device results:
[ANDROID_CELSIUS_PRESENTATION.md](ANDROID_CELSIUS_PRESENTATION.md),
[ANDROID_VALIDATION.md](ANDROID_VALIDATION.md). Absolute physical accuracy remains unvalidated.
