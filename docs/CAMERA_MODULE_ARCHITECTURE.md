# Integrated camera modules

## Scope

The Android application supports multiple integrated camera modules in one APK.
**Support for multiple camera models is an application goal; simultaneous acquisition
from multiple cameras is out of scope.** Only one session owns an acquisition source
at a time. Externally installed/dynamic plugins are also out of scope.

The only validated commercial camera remains the Infiray HT-301 / T3-317-13,
USB `1514:0001`. A generated 160×120 preview source proves the shared boundary; it
is a test module, not evidence of compatibility with another commercial camera.
No FLIR or other real-model compatibility is claimed.

## Responsibilities and selection

```text
Discovery identities (Android USB, or an injected test/SDK provider)
  → pure CameraModuleRegistry probe of every identity/module pair
  → None / Selected / Ambiguous
  → explicit Connect and candidate-bound permission
  → CameraSessionOwner: await release, then open exactly one selected module
  → CameraSessionState: preview + optional measurement + structured status
  → capability-driven screen and independent CelsiusPresenter
```

`core/.../camera/CameraContract.kt` defines stable module/model IDs, discovery
identity, capabilities, factory/session contracts, statuses, previews and measurements.
`CameraDeviceIdentity` does not require USB. `UsbCameraIdentity` is one optional
implementation; USB discovery is presently the production discovery provider.
The Android coordinator's injected identity provider exercises non-USB sources.

`CameraModuleDefinition.probe` is **pure**. It cannot request permissions, open a
handle, start a stream, initialize thermometry or send a camera control. Model IDs
and `CameraModuleId` are machine identifiers, never translated display labels.
`CameraModule.open` is invoked only after explicit Connect and any required Android
authorization. It opens a read-only aiming stream, not a measurement session.

`CameraModuleRegistry` rejects duplicate module IDs and evaluates every candidate.
It returns:

| Selection | Result |
|---|---|
| No devices | `CAMERA_NOT_FOUND`; no factory |
| No supported identities | `CAMERA_UNSUPPORTED`; no fallback/factory |
| Exactly one supported device/module pair | Explicitly open that pair |
| More than one pair | `CAMERA_MATCH_AMBIGUOUS`; no arbitrary first match |

Two supported cameras are also ambiguous. Device choice/multiple simultaneous streams
are not implemented. Unrelated unsupported USB peripherals do not prevent a unique
supported camera from being selected.

`app/.../camera/AndroidCameraModule.kt` is the composition root: it registers the
HT-301 and simulated factories with Android resource/evidence bindings. Shared
screen/presenter code does not branch on a camera name. This composition root may
import drivers; other shared presentation code must not import their protocol types.

## Small capability model

| Capability | HT-301 | Simulated 160×120 preview |
|---|---|---|
| Preview | Yes | Yes |
| Temperature measurement | Yes, current-frame gate | No |
| Explicit measurement initialization | Yes | No |
| Touch temperature inspection | Yes, valid measurement only | No |

`CameraUiPolicy` is used by both the shared screen and tests. A preview-only source
has no Celsius controls, initialization button, cursor temperature or extrema.
Current action availability is separate from static capability: a temporarily
unavailable action cannot reach the source merely because the model supports it.
Only Connect, Close and Initialize measurement are defined at this stage. No
speculative manual NUC, settings, visible-light or recording capability is advertised.

## Session ownership and lifecycle

`CameraSessionOwner<P>` owns the sole `CameraSession<P>` and observation job.
Opening a replacement synchronously clears old visible data, then serializes
**awaited old close before new open** with a mutex. A generation token prevents
obsolete opening, actions or source publications from reviving data. If release
fails, ownership is retained and the new factory is blocked until release succeeds.
Cancellation cannot abandon an already-owned source halfway through release.

Matching detach and Android background clear preview/measurement immediately and
release the session. Unrelated detach cannot close it. Foreground, attach, discovery,
permission completion for a cancelled candidate, and Activity recreation cannot
initialize radiometry. Foreground merely discovers; reopen requires Connect.
Permission is bound to the exact pending identity/module and rechecked before opening.

The Android ViewModel owns the coordinator/presenter. Its disposal awaits source
release before cancelling the owner scope. HT-301 shutdown awaits the existing
serialized worker's `finally` native close, including an already-cancelled detach
worker. Acquisition and parsing remain off the UI thread. Native pending frames,
module state and presentation requests remain bounded/latest-only.

The common snapshot contains module metadata, device identity, lifecycle,
capabilities, current geometry, status/error, optional preview/measurement,
supported actions and transport-independent counters. It contains no UVC format,
control value, trailer layout, calibration block or mandatory raw plane.

## Geometry and data lifetime

`NativeImageGeometry(width,height)` requires positive dimensions and a pixel count
representable as an Int. `NativePixel` rejects negative coordinates; the geometry
validates upper bounds, constructs valid pixels and maps row-major offsets.
`ImageCoordinateMapper` receives source geometry explicitly. Fit letterboxing,
half-open bounds, floored touch cells and marker pixel centers are unchanged.
Tests cover 384×288, 160×120, 200×50 and 96×128 in wide/tall viewports.

`CameraPreview<P>` includes geometry, sequence and monotonic receipt milliseconds.
P is an owned pixel plane in JVM tests and a Bitmap on Android. Published previews
are read-only to consumers: do not mutate/recycle a bitmap retained by a snapshot.
The simulator creates a fresh owned plane/bitmap every publication. Close removes
the current preview; a retained previous snapshot remains safe to read.

`ThermalMeasurement` is optional and declares:

- geometry, sequence, monotonic receipt time and sensor row-major coordinate system;
- validity and provenance (module ID, model ID, temperature kind);
- copy-only Celsius matrix access, per-coordinate temperature and matrix extrema;
- explicit high/low points in that geometry;
- optional namespaced native sample evidence, with **no required raw encoding**.

`ThermalMeasurement.validityMask()` optionally supplies a copied native 0/1 byte mask;
its default null preserves today's all-valid modules. Generic rectangular ROI analysis
uses this seam and the authoritative Celsius plane, with strict half-open native bounds,
valid-only statistics and no protocol/color dependency. Source identity/dimensions gate
selection compatibility. See [ROI architecture](ANDROID_CELSIUS_PRESENTATION.md#native-rectangular-roi).

`OwnedThermalMeasurement` owns a finite matrix matching `geometry.pixelCount` and
computes its actual extrema. `Ht301ThermalMeasurement` adapts the existing immutable
`RadiometricMeasurement`, retaining the original richer evidence object. The owner
rejects mismatched module/model provenance, capabilities, preview/measurement geometry,
invalid measurements or out-of-bounds extrema before shared consumers see them.
Module implementations must preserve immutable/read-only ownership after publication.

The shared Celsius renderer validates the supplied geometry, uses matrix data and
changes colors only. Bitmap dimensions, aspect ratio, touch and extrema all follow
current geometry. The presenter publishes a coherent completed measurement/bitmap;
loss of a measurement clears colors, legend and readings. Switching modules clears
the retained cursor coordinate. Same-module reopen can retain the coordinate but
cannot retain its old temperature. A native sample is optional for cursor inspection.

## HT-301 adapter and intentionally retained code

`core/.../camera/ht301/Ht301ModuleProfile.kt` owns exact identity/capabilities and the
rich measurement adapter. `app/.../camera/ht301/` owns the targeted existing controller,
session-state adapter, resource bindings and DEBUG diagnostic panel.

The validated implementation is adapted, not re-derived. These files intentionally
remain at their prior locations to avoid risky namespace/JNI/golden churn:

- JVM `Ht301Frame`, inspection/preview, `FrameParameters`, Zoom/session models,
  `RadiometricSession`, `Raw14Transition`, `NativeEquivalentThermometry` and
  `RadiometricMeasurement` remain in `org.lmthermal.core`. They are HT-301 internals,
  not the common module contract.
- Android `UvcTransport`, `NativeUvcTransport`, JNI C++ and existing thermometry/numeric
  diagnostics retain their locations/bindings. The HT-301 controller is their owner;
  another module is not required to use them.
- `LatestFrameState` was extracted from the frame file without behavior changes so
  common ownership does not import a frame/parser type.

The controller now binds the selected exact UsbDevice instead of taking an arbitrary
first match. Its protocol algorithm, native acquisition and thermometry are preserved:
224256 transport bytes, 288+4 rows, full-word classification, explicit
`32772 → 32800 → 32768`, stage receipt discards, shutter/liveness gates, conservative
existing-raw14 reopen, float/LUT arithmetic and native 384×288 coordinates.
The module adapts readiness/status; it does not infer readiness or physical calibration.
Original raw/calibration/trailer/lookup evidence remains available in HT-specific
diagnostics. Trailer center is still distinct from literal `(192,144)`.

## Structured messages and localization boundary

Common `CameraStatusCode`/`CameraErrorCode` enums and machine-fact parameters identify
not found, unsupported, ambiguous, permission, opening/stream failure, unavailable
measurement, invalid module data and unsupported action. Exceptions remain technical
English logs; `exception.message` is not the common user-visible contract.
`Ht301SessionStatus` implements the namespaced `ModuleStatusIdentifier` extension.
The common UI does not interpret its protocol codes. Registered `CameraUiBindings`
maps those states to Android resources and supplies optional module diagnostics.

Resource conventions in `app/src/main/res/values/strings.xml`:

| Purpose | Prefix/examples |
|---|---|
| Common camera actions/status/errors | `camera_connect`, `camera_status_*`, `camera_error_*` |
| Common measurement/presentation | `measurement_*`, `palette_*` |
| HT-301 labels/status/diagnostics | `camera_ht301_*` |
| Simulated source | `camera_simulated_*` |
| Future module | `camera_<stable_module_namespace>_*` |

Core palette enums contain identity/table behavior only. Android supplies labels.
Default-English resources contain positional formatting arguments; future translations
reuse keys/argument types. Model/module IDs, enum codes, JSON keys, fixture formats and
numeric evidence stay untranslated. Existing diagnostic palette aliases are retained
for compatibility, with a new stable `palette_id`. Reports also add `module_id`,
`camera_model`, native dimensions and capability facts. HT-specific legacy `raw14`,
trailer/literal-center values and physical-accuracy warning are preserved by bindings.

The localization milestone completes French/English resources, platform app-language
selection/storage and legacy debug-label migration. Every supported module must translate
its namespace alongside common resources; machine IDs and numerical evidence stay stable.
Locale recreation releases the active session through the same ownership policy and does
not auto-open or initialize. See [ANDROID_LOCALIZATION.md](ANDROID_LOCALIZATION.md) for
fallback, formatting, generated LocaleConfig, tests and future-language procedure.

## Adding another module

Future exchange persistence follows the **Accepted v1.0 canonical specification** in
[LMTX_FORMAT_V1.md](LMTX_FORMAT_V1.md), including actual module identity/geometry,
optional temperatures/native evidence, capability/availability and namespaced
extensions. Its module-owned immutable export snapshot/evidence interface is future
[Android issue #5](https://github.com/LounaMaili/LMThermal/issues/5) work; no persistence
interface is implemented here. [Issue #4](https://github.com/LounaMaili/LMThermal/issues/4)
retains the decision/review history. Implementations follow the contract's versioning
rules without silently changing v1 semantics.

1. Research/validate the actual device/SDK and licenses before claiming support.
2. Supply a stable identity and pure probe; use another discovery provider if not USB.
3. Implement `CameraModule`/awaited `CameraSession.close` inside its own package. Keep
   transport, parsing, controls, calibration and thermometry there.
4. Publish actual geometry and capabilities; omit measurement if unavailable. For
   temperatures publish valid owned native data with truthful provenance. Preserve
   richer evidence internally and use optional sample encoding IDs where useful.
5. Register its factory/resource binding at the composition root, with namespaced
   strings/statuses and complete translations for each supported language. Shared UI
   should need neither a new model-name branch nor copied screen code. A new generic
   action requires deliberate contract/UI review.
6. Test exact/unknown/ambiguous selection, alternate geometry, ownership/cancellation,
   unsupported actions, invalid data, and independent numerical references.
7. Validate that new real camera on hardware and repeat HT-301 regression. Update the
   supported-model matrix and evidence; never infer compatibility from simulation.

## Checks and review boundary

```bash
python3 tools/check_camera_module_boundaries.py
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
git diff --check
```

Use the cached JDK/SDK setup in [CONFIGURATION.md](CONFIGURATION.md) and wireless ADB
in AGENTS.md. The boundary check rejects HT-specific types/dimensions in shared
contracts, presenter/screen, geometry/renderer and simulator. The composition root
and documented legacy module internals are excluded. It supplements behavioral tests.
JVM tests exercise registry, capabilities, geometry, ownership/provenance/errors,
simulation and the existing full numerical/color goldens. Pixel instrumentation
exercises the alternate bitmap source and generic Celsius presenter without USB,
alongside all existing ART numerical/presentation parity checks.

Real HT-301 regression and the issue #1 acceptance checklist are recorded in
[ANDROID_VALIDATION.md](ANDROID_VALIDATION.md). Issue #1 is ready for review only
after those hardware checks pass. Issue #2 acceptance is recorded separately in the
localization validation section;
no other commercial driver or simultaneous acquisition is added.
