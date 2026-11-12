# Android product workspace

## Audit and proposal

| Existing area | Assessment before this change | Product treatment |
| --- | --- | --- |
| Display/raw14/ready/error | Validated module state and invalidation rules | Keep unchanged; primary Live panel |
| Point, ROI, palettes, ranges | Working; crowded controls and gesture-only selection | Dedicated Measurements panel, center/full-grid alternatives |
| Immutable LMTX freeze/SAF/Share | Implemented independently of camera lifetime | Dedicated Captures panel; same exporter |
| Frame counts, module diagnostics | Useful technical validation, little everyday value | Advanced Diagnostics panel |
| App language | Implemented en/fr and system picker | Settings panel, existing locale lifecycle |
| Common LMTR production recording | Not integrated/qualified | Explicit unavailable text; no Start button |
| Gallery/compare, alternate camera modules | Future product work | No implied availability |

The first implementation keeps camera imagery primary. A horizontally scrollable
section strip selects controls; the image/legend occupy an independent, weighted stage.
In portrait controls scroll below it, in landscape beside it. Section/readout changes
cannot resize the image allocation during a drag. Each panel owns its scroll state.
Section selection survives configuration recreation; only presentation is changed.
A long status/title may still wrap on very small or large-font screens; the controls
remain scrollable. This is not a claim of full screen-reader/operator qualification.

Measurements retain source/module/geometry and coherent-result checks. Literal center
means geometry `(width/2,height/2)`, never the trailer center algorithm. Full ROI means
the existing half-open native rectangle. Both use the presenter's existing selection
API and require a current inspectable measurement. Thermal matrices and coordinates
are never rotated/reinterpreted. No navigation action opens a camera or initializes it.
Captures remain immutable prepared LMTX artifacts through rotation/SAF as documented in
[the lifetime policy](ANDROID_CAMERA_LIFETIME.md). Camera errors remain visible in Live;
no automatic retry or reinitialization is introduced.

## Presentation allocation analysis

`CelsiusRenderer` already owns a completed IntArray. Previously `argb()` made a defensive
copy immediately before `Bitmap.createBitmap`, which itself copies pixels. The new
synchronized one-shot `takeArgb()` transfers that completed display buffer; later
access fails and no second consumer can take ownership. Public `argb()` remains defensive
before transfer. A 384×288 render avoids one 442,368-byte IntArray copy by construction,
not a measured Pixel heap reduction. Source Float32 values/validity, palette goldens,
Auto percentiles and arithmetic remain identical. Auto validates the matrix once before
its existing full-sort calculation. Bitmap/legend wrappers are remembered by Bitmap
identity. Lifecycle-aware StateFlow collection affects UI subscriptions only.

Acquisition FPS, callback FPS, render duration, completed bitmap publication and visible
screen latency are different metrics. Existing latest-pending/one-in-flight and synchronous
owner-generation guards remain intact. Matrix copies in the generic immutable measurement
interface and ROI worker remain; changing their ownership contract would need a separate
investigation. No recorder, buffer capacity, memory admission, USB or thermometry change
is included. No physical fluidity, GC or latency improvement is claimed.

Release `NumericEvidence` now returns before JSON serialization/logcat when disabled;
debug file/log diagnostics remain bounded. Caller-created argument maps can still allocate.
No telemetry service, network permission or automatic error upload is added.

## Host evidence and remaining gates

Run with the documented JDK/SDK/cache:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 tools/check_android_localization.py --self-test
python3 tools/check_camera_module_boundaries.py
```

Core tests cover palette goldens, exact source values and exclusive/concurrent display
ownership. Robolectric executes actual Compose viewport tests in portrait/landscape,
French resources and 200% font scale, plus disabled evidence/privacy and existing lifecycle,
export and presenter tests. The updated instrumentation recipe compiles; it has NOT run
on ART or a physical phone. Test-only Compose manifest/artifacts never enter release.

Future authorized device work must check real touch targets/TalkBack, small devices,
locale/rotation/SAF retention, hand/background native alignment, error states and visible
latency with matched callback/render/publish measurements. No Pixel/HT-301/ADB access is
part of this phase. Historical evidence is not relabeled as product-UI validation.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

Host checkpoint: 234 core JVM tests and 27 app local/Robolectric tests pass (261 total,
no failures/skips), plus 10 localization-check self-tests and 120-key en/fr parity.
Debug application, instrumentation APK and lint compile/pass. The small display-buffer
transfer is independently tested here; no recorder prototype is imported.

The full-image shortcut, like a drag, passes its captured source key and geometry to
the existing ROI admission guard. A camera-free regression rejects a superseded device
or geometry and accepts a matching current source; no source replacement changes protocol.
