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
  - **LMThermal Exchange Format (`.lmtx`)** — accepted still-only v1.0 contract; Android coherent still export is implemented; Desktop import/interoperability remain pending (see [canonical specification](LMTX_FORMAT_V1.md) and [producer guide](ANDROID_LMTX_EXPORT.md)).
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

### LMThermal Exchange Format (`.lmtx`)

[LMTX_FORMAT_V1.md](LMTX_FORMAT_V1.md) is the **Accepted v1.0 canonical implementation
specification**, including the complete manifest, binary encodings, validity,
provenance, analysis, integrity and compatibility rules. It defines still captures
only and preserves independent support for legacy Desktop capture/recording formats.
[Issue #4](https://github.com/LounaMaili/LMThermal/issues/4) remains the decision/review
history. [Android export #5](https://github.com/LounaMaili/LMThermal/issues/5) and
[Desktop import #1](https://github.com/LounaMaili/LMThermal-Desktop/issues/1) are
unblocked by acceptance and remain open. Android export is implemented with
interoperability pending; Desktop import is unimplemented.

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
- Simultaneous acquisition from multiple cameras
- Real-time streaming over network

Support for multiple camera models is an application goal, using integrated modules
in one APK and one active session. Only HT-301 is validated real hardware today;
the 160×120 preview-only simulator demonstrates the architecture, not compatibility
with another camera. Dynamic externally installed plugins are out of scope.

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

Exports and recording remain future milestones. Native rectangular ROI analysis and touch/Celsius presentation are
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

## Integrated camera-module foundation (2026-10-03)

Discovery, pure identification/registry, explicit opening, single-session ownership
and shared presentation are separate. Unknown or ambiguous identities produce
structured errors without opening any driver. Module detection sends no controls.
Replacement awaits prior release; detach/background clear current data. Lifecycle
recreation cannot initialize radiometry automatically.

The common contract provides stable module/model identity, capabilities, lifecycle,
structured statuses, actual native geometry, optional preview/measurement and
supported actions. It requires no USB, UVC, raw14, trailer or calibration format.
Matrix size, bitmaps, fitted-image mapping, aspect ratio, cursor and high/low follow
supplied geometry. Temperature controls/initialization/inspection are capability-gated.
HT-301's immutable richer evidence and validated protocol/numerical implementation
are adapted behind its module boundary; no numerical or camera-coordinate changes.

The 160×120 simulated module is preview-only and never matches real USB hardware.
Android resource bindings map stable status/error/palette identities to labels;
complete English/French common/module namespaces implement the localization boundary.
Contracts, deferred legacy file moves, ownership/data lifetime, supported-model limits
and module addition procedure: [CAMERA_MODULE_ARCHITECTURE.md](CAMERA_MODULE_ARCHITECTURE.md).

## Android native rectangular ROI

**Point** preserves native tap/drag cursor inspection. **ROI** creates/replaces one
rectangle by dragging; the first and last touched pixel cells are both included,
with a minimum 1×1 selection. Model bounds are strict native half-open
`[x1,x2) × [y1,y2)` and must fit `NativeImageGeometry`. Initial outside/letterbox
touches are ignored; a drag starting inside limits its departing endpoint to the
image edge. **Clear ROI** removes the selection. Outline edges use the same fitted
native mapper as cursor/extrema, without rotating/resampling source measurements.
ROI inspection reserves readout/legend space before drawing; changing statistics or
temporarily unavailable values do not move the viewport/controls during the gesture.

Min/Max/Mean and valid/total counts come from the current authoritative Float32
Celsius plane. Optional validity bytes are exactly 0 (invalid) / 1 (valid), with
no mask meaning all valid. Invalid cells never contribute; valid cells must be
finite. Native row-major traversal chooses the first extrema tie and accumulates
the unrounded mean in Float64. Zero-valid regions have counts without numbers or
coordinates. These are [LMTX v1 §§7/10](LMTX_FORMAT_V1.md) semantics, used by the
[Android still exporter](ANDROID_LMTX_EXPORT.md).

The compatible module/device/geometry selection persists across frames, but a gap,
close or detach clears numerical results. Source or geometry replacement clears
the selection instead of reinterpreting coordinates. Analysis runs in a separate
bounded latest-request worker; palette/range never supply measurement values.
English/French labels, locale-aware readings and valid-count plurals are complete.
HT-301 ROI values inherit the existing native-equivalent physical-accuracy warning.
See [presentation architecture](ANDROID_CELSIUS_PRESENTATION.md#native-rectangular-roi)
and [validation evidence](ANDROID_VALIDATION.md).

## Android internationalization (2026-10-03)

System is the default; explicit French (`fr`) and English (`en`) are selectable through
the in-app Language menu. AndroidX AppCompat owns API 26–32 storage; Android 13+ platform
per-app language settings are authoritative and agree with the picker. AppCompatActivity
retains Compose, with the minimum compatible no-action-bar XML theme. AGP generates the
locale configuration with English fallback and filters supported product languages.

All 87 product string keys have French translations, positional format parity and
common/module namespaces. Visible status/error/accessibility and debug prose are resources;
core/module IDs, diagnostics and numeric data are unchanged. Display locale controls
Celsius number formatting, never measurement units or calculations. Unsupported languages
fall back to complete English. Debug pseudolocales and both-orientation layout checks are
test support, not production language choices.

Language/configuration changes release the camera through existing onStop handling.
Returning performs read-only discovery; Connect/Open and radiometric initialization remain
explicit. No USB fd, readiness or stale measurement is retained through locale recreation.
Wrapping action rows keep longer labels reachable. Resource tests and the future language/
module translation procedure: [ANDROID_LOCALIZATION.md](ANDROID_LOCALIZATION.md).
Real-device results and the issue #2 acceptance review: ANDROID_VALIDATION.md.

## Android coherent still export

Android #5 now provides a camera-independent immutable LMTX v1 acquisition snapshot,
module-owned optional evidence, exact Float32/mask serialization, matrix-derived
analysis and separate frozen presentation. A bounded worker privately finalizes/
checks the archive before new-document SAF publication and finalized URI sharing.
Display/unsettled/transient previews truthfully omit Celsius; closed sources cannot
capture. No controls, acquisition, native coordinate or thermometry semantics change.
See [ANDROID_LMTX_EXPORT.md](ANDROID_LMTX_EXPORT.md) for privacy, local limits,
lifecycle/provider guarantees and shared fixtures. Desktop #1 and cross-platform
interoperability are pending; issue #5 remains open.
