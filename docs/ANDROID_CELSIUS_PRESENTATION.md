# Android Celsius presentation and touch inspection

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Data path and validity

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

Auto uses NumPy linear 2nd/98th percentiles of all 384×288 finite Celsius values.
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

The immutable matrix remains `temperature[y*384+x]`, x 0..383/y 0..287. No transpose,
rotation or mirroring is applied. Portrait and landscape use different layouts,
with one mapping implementation. Preferred final viewing orientation remains a
presentation decision; changing phone orientation retains the existing lifecycle
release/reopen policy rather than keeping USB ownership through Activity backgrounding.

## Bounded worker and observability

A `combine`/`collectLatest` worker conflates camera/settings requests. It permits one
render in flight and replaces obsolete requests. Completion checks current camera
snapshot and settings before publication; disposal cancels work and clears output.
Acquisition never waits for Compose. Valid measurements skip redundant acquisition-
side grayscale rendering. Transport, session, thermometry arithmetic and fresh LUT
construction remain unchanged.

Debug-only, bounded `presentation.jsonl` records numeric render timings, settings,
range, cursor samples, callback FPS, acquisition replacements and completed renders
rejected as superseded. This last counter excludes coroutine-cancelled requests;
it is not a total dropped-render count. No image payloads or scene hashes are saved.
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

ROI, export, logging, recording, alternate lens/range support and independently
measured physical validation remain later milestones.
