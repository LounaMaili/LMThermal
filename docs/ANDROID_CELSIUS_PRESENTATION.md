# Android Celsius presentation and touch inspection

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Data path and validity

The shared renderer/presenter now consumes the optional generic `ThermalMeasurement`
contract and its explicit `NativeImageGeometry`. The following native raw14/trailer
facts describe the unchanged HT-301 adapter. Other modules need neither raw samples
nor thermometry. Architecture and ownership:
[CAMERA_MODULE_ARCHITECTURE.md](CAMERA_MODULE_ARCHITECTURE.md).

The measurement path remains full native raw14 → native-equivalent LUT → Celsius
matrix. Presentation consumes an owned matrix copy; it cannot change image words,
calibration, the LUT, session gates, controls or measurement extrema.

`CelsiusRenderer` in plain JVM core produces ARGB pixels and an effective Celsius
range. `CelsiusPresenter` creates the Android bitmap/legend off the UI and camera
workers. One completed measurement/bitmap/range snapshot is published together.
Touch readings and high/low overlays use that same displayed measurement; incoming
completed renders refresh the selected native coordinate's reading.

The live camera's unavailable measurement overrides a previous render immediately.
Display mode uses the existing grayscale Y aiming preview; unsettled raw14 uses
independent raw normalization. Neither exposes a Celsius legend or reading. Invalid,
closed or disconnected states clear measurement colors/readings. The selected native
coordinate can remain stored, but its crosshair and Celsius are unavailable until a
new valid rendered measurement exists. There is no last-good-Celsius fallback.

## Palettes and ranges

Palettes match `LMThermal-Desktop/celsius_palette.py`:

| UI palette | Color source |
|---|---|
| White hot | Level replicated to RGB |
| Black hot | Inverted level replicated to RGB |
| Inferno | Desktop OpenCV Inferno 256-entry RGB table |
| Iron-like | Desktop OpenCV HOT 256-entry RGB table |
| Turbo | Desktop OpenCV Turbo 256-entry RGB table |

The pinned oracle used OpenCV 5.0.0 and NumPy 2.5.3. Android embeds only the numerical
color tables and has no Python/OpenCV runtime dependency. BGR-to-RGB conversion is
performed during export, before packing ARGB.

Auto uses NumPy linear 2nd/98th percentiles of all finite Celsius values in the supplied
geometry (384×288 for HT-301).
Float32 endpoint subtraction and Double interpolation preserve the Desktop rounding
boundaries. If the percentile span is below 1 °C, bounds expand symmetrically to 1 °C
around their midpoint. Outliers remain measurements even when their colors clip.

Locked bounds are exact finite user values with minimum < maximum; default 25–45 °C.
Invalid text/NaN/infinity/reversed bounds leave current settings unchanged. Decimal
comma is accepted. Both modes normalize in Double, clip levels to 0..255 and round
ties to even, matching Desktop. Only display colors clip; raw14 and Celsius remain
unchanged. The horizontal legend uses the same table with five Celsius ticks.

Controls apply to the current ViewModel lifetime; no preference persistence or
orientation setting is introduced.

## Native coordinate mapping

`ImageCoordinateMapper` defines the centered rectangle used by `ContentScale.Fit`,
including letterbox margins in any positive viewport. Touch positions within the
half-open rectangle are floored into native pixel cells. Outside positions and
nonfinite positions are ignored, not clamped to an edge. Marker mapping uses pixel
centers through the same rectangle.

Tap selects a pixel; dragging updates it while the finger moves over image content.
The white/black crosshair persists at that native coordinate across live frames.
The reading is `source.pixel(x,y)` and `measurement.temperature(x,y)`, never a bitmap
sample. Red high and cyan low markers use measurement trailer extrema coordinates
and this same mapper. Trailer center remains separate from literal `(192,144)` in
optional diagnostics; no center-region calculation is inferred.

Generic indexing is `temperature[y*geometry.width+x]`; the immutable HT-301 matrix
remains `temperature[y*384+x]`, x 0..383/y 0..287. No transpose,
rotation or mirroring is applied. Portrait and landscape use different layouts,
with one mapping implementation. Preferred final viewing orientation remains a
presentation decision. Changing phone orientation retains the live source and presenter
choices under the [generic lifecycle policy](ANDROID_CAMERA_LIFETIME.md); native
coordinates and measurement state are unchanged.

## Bounded worker and observability

A small `combine` collector offers camera/settings requests to one conflated
pending slot. A separate worker completes one render at a time, then takes the
newest pending source. It does not cancel a useful in-flight render merely because
a newer valid frame arrived. Under continuous source updates, the former
`collectLatest` plus exact source-identity publication guard could suppress every
completion and leave a seconds-old image visible. A controlled 100 ms render /
10 ms source-arrival regression reproduced zero publications before the correction.

Module/device/geometry/settings changes and unavailable/closed/error states invalidate
publication with a generation guard. Completion also checks current valid streaming
state and nondecreasing source sequence/receipt time; disposal cancels and clears output.
One in-flight plus one pending request bounds display work; recording must use its
own explicit retention/drop policy. Acquisition never waits for Compose. Valid
measurements skip acquisition-side grayscale. Transport/session/thermometry are
unchanged. This scheduling regression passes on the JVM Android runtime; sustained
Pixel recording-pressure freshness still requires the continuation live tests.

Debug-only, bounded `presentation.jsonl` records numeric render timings, settings,
range, cursor samples, callback FPS, acquisition replacements and completed renders
rejected as superseded. This counter records invalidated renders skipped at publication/consumption;
it does not count every pending request replaced in the conflated slot. No image payloads or scene hashes are saved.
Render timing includes core range/color work, bitmap construction and dispatch wait;
thermometry evaluation is sampled separately. Hardware results and timing distributions
are in [ANDROID_VALIDATION.md](ANDROID_VALIDATION.md).

## Reference tests and regeneration

```bash
../LMThermal-Desktop/.venv/bin/python tools/generate_presentation_goldens.py
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Use the JDK/SDK setup in [CONFIGURATION.md](CONFIGURATION.md). Presentation manifest
and reference provenance are under `core/src/test/resources/presentation/`; existing
sanitized settled-room/hand thermometry matrices are reused, with no new live image
fixtures. References pin oracle source/library versions and SHA-256 hashes.

JVM tests cover all viewport pixels, bounds/letterboxes, cursor refresh, measurement
immutability, exact percentile references, range validation/rounding, 256 colors per
palette and all full-image pixels for two matrices × two range modes × five palettes.
Pixel instrumentation repeats full color parity/mapping and exercises the actual
latest render worker's settings replacement and unavailable/disposal cancellation.
Test fixtures/dependencies are excluded from the product APK.

The module foundation adds alternate-resolution mapping/rendering tests and Android
160×120 simulation/optional-sample presentation tests. Core palette enums contain
stable IDs, while Android resource bindings supply visible labels. Diagnostic palette
aliases remain stable and reports add module/model/geometry/capability metadata.

Export, logging, recording, alternate lens/range support and independently
measured physical validation remain later milestones.

## Native rectangular ROI

`NativeRect` and `RoiStatistics` in JVM core depend on module-provided
`NativeImageGeometry`, not a camera protocol or bitmap. Bounds are strictly
`[x1,x2) × [y1,y2)`; empty/negative/overflowing/out-of-bounds model geometry is
rejected, never silently clamped. Drag endpoint conversion includes both native
pixel cells and normalizes reversed drags. Initial letterbox touches do nothing;
only an active drag starting inside may limit its endpoint to the image edge.
The fitted mapper maps ROI **cell edges**, while point/extrema markers map centers.

The statistics engine takes a Celsius `FloatArray`, geometry, rectangle and optional
`ByteArray` validity mask. Null means all valid; only 0/1 bytes are accepted. It checks
the whole plane/mask and rejects nonfinite valid values. Invalid internal values are
ignored, including filler; the [LMTX exporter](ANDROID_LMTX_EXPORT.md) applies the
exact serialized positive-zero rule. Min/max retain source Float32 values, ties retain the first
valid native row-major pixel, and the mean uses a positive-zero Float64 accumulator
with sequential row-major additions/division and no internal presentation rounding.
No-valid regions have counts only, with absent min/max/mean/extrema coordinates.
The method matches `finite-valid-row-major-f64-v1` in [LMTX v1 §7](LMTX_FORMAT_V1.md).

Existing HT-301 measurements retain their all-valid representation. The generic
`ThermalMeasurement.validityMask()` seam defaults to null and allows a future module
to supply an owned 0/1 mask for ROI analysis. The engine/worker are tested with masks;
this does not claim a new partial-valid real camera. LMTX export supports synthetic masks.

**Point** keeps the persistent cursor and its current-frame reading. **ROI** enables
create/replace by drag; **Clear ROI** removes it. There are no resize handles or
annotation editor. ROI geometry is separate from transient gesture state and temperature
results, and its outline uses a display-only black/yellow stroke. A compatible selection
can remain visible on an unsettled aiming preview, but its readings are unavailable.
ROI mode reserves font-scaled size/readings/count slots before drawing starts. Legend
space is also reserved during ROI inspection; unavailable frames show neither old
numbers nor an old legend. Text slots allow bounded wrapping, so new selections,
changing values, clear and temporary gaps do not reflow the viewport or nearby controls
during a gesture. Longer text can ellipsize while its complete content remains in text
semantics. This prevents the original readout insertion from moving controls and
resizing/cancelling a landscape drag.
Replacement module/model/device identity or dimensions clears the rectangle. Language
recreation retains the existing camera-release/explicit-reopen policy; preserving a
selection never retains USB ownership or readiness.
In-progress gestures carry their original source identity and geometry, so replacement
before recomposition cannot select a rectangle on the successor source.

`CelsiusPresenter.roi` owns `RoiPresenter`, a separate single-worker `collectLatest`
analysis path. Inputs are source compatibility, the displayed authoritative measurement
and selection; palette/range are absent. A re-render of the same measurement reuses
analysis. Each new measurement refreshes it off the UI/acquisition threads, with one
matrix copy and no per-pixel object allocation. Publication rejects superseded results;
the screen also checks current measurement availability and exact displayed-frame/result
identity. Close/detach/invalid/transient states cannot display a last-good ROI temperature.
Preview-only modules cannot select/publish Celsius ROI statistics.

Min/Max/Mean, rectangle size and valid/total counts use English/French resources and
locale formatting/plurals. ROI values inherit measurement provenance and HT-301's
unchanged warning: **Native-equivalent temperatures; absolute physical accuracy not yet
independently validated.** No measurement comes from palette levels, legend or screenshots.
The model maps deliberately to LMTX native half-open rectangles/statistics; this milestone
originally added no persistence. The subsequent [LMTX still exporter](ANDROID_LMTX_EXPORT.md)
freezes the same source/ROI/presentation without changing accepted format semantics.

Debug-only bounded `roi.jsonl` records native bounds, counts/statistics, frame sequence,
provenance kind, calculation time and callback FPS. It contains no scene payloads.
JVM tests cover three geometries, drag/mapping, masks/counts/ties/Double means and
presentation independence. Pixel tests exercise the actual worker lifecycle, source
replacement, masks, gesture controls in all locale/orientation layouts and representative
small/full-frame engine timings. Real-device evidence is in ANDROID_VALIDATION.md.

## Localized presentation

Celsius ticks/readings and editable range bounds use the configured display locale.
French uses decimal commas; the strict range parser accepts displayed/comma/dot separators.
Formatting is outside core: matrix, percentiles, colors, extrema, JSON numbers and native
coordinates are unchanged. The System/French/English selector uses Android app locales;
Locale recreation releases the camera and requires explicit reopen, so no old rendered
Celsius is carried into a new locale session. Wrapping action rows accommodate longer labels.
See ANDROID_LOCALIZATION.md and its camera-free synthetic layout/number tests.
