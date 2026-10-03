# Android native-equivalent thermometry

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Scope and architecture

The Kotlin `core/` engine ports the existing Desktop
`native_equivalent_thermometry.py` operation-for-operation. It supports only native
width 384, host range 120, lens 68, shutter fix 1.5 and full raw indices 0..16383.
Unsupported configurations fail explicitly. No new camera commands or empirically
fitted conversion are introduced. JNI remains acquisition/control infrastructure.

`Ht301Frame` owns the exact transport → `FrameParameters.decode` validates inputs →
`NativeEquivalentThermometry.buildLookup` builds a traced 16384-entry Float table →
`measure` produces `RadiometricMeasurement` with owned raw14 and Celsius arrays.
Returned arrays are copies; the source frame is immutable. Native matrix indexing
is always `y*384+x`, without rotation/mirroring. Only the first 288 rows are image.
Sequence and monotonic receipt metadata accompany the measurement.

`MeasurementGate` is stateless. `RADIOMETRIC_READY` remains the established structural/
liveness session state. Only its **current frame**, with consistent trailer extrema
and finite thermometry, produces a measurement. Display, invalid, held/demoted,
unknown-raw14 and disconnected states produce null. A thermometry failure clears
Celsius without changing session controls/state. Close/error also clear the UI
measurement immediately, protected by existing generation/publication ownership.
There is no last-good-temperature fallback.

Preview normalization still uses raw/display words only and cannot feed the LUT.
Compose displays current numeric center/matrix/high/low outputs and scales high/low
coordinate markers; it performs no thermometry. Trailer center is separate from
literal pixel `(192,144)`. No center-region algorithm is inferred.

## Trailer inputs

| Absolute byte | Meaning / representation |
|---|---|
| 221186 | FPA uint16 input, `20-(word-7800)/36` |
| 223488 | Lookup-base uint16, subtract GetFix and wrap uint16 |
| 223490 | Distinct calibration-temperature uint16, `word/10-273.15f` |
| 223494, 223498, 223502, 223506, 223510 | Five float32 calibration coefficients c0..c4 |
| 223742 + 0/4/8/12/16 | Float32 correction/reflected/ambient/humidity/emissivity |
| 223742 + 20 | Stored distance uint16; lens 68 uses three times this value |
| 224094..224113 | Exact 20-byte calibration copy checked against 223494..223513 |
| 221208 | Trailer center raw index |
| 221192 / 221188,221190 | High index / native x,y |
| 221198 / 221194,221196 | Low index / native x,y |

Settings must be finite; c0/emissivity/distance positive, humidity nonnegative.
Copied calibration must match byte-for-byte. No new semantics are assigned to the
other duplicate settings. `measure` uses Desktop **make_measurement** summary rules:
center/high/low indices in range, extrema coordinates within image, indices equal
image extrema and actual pixels at their coordinates. It does not substitute image
extrema when trailer data is invalid. The unrelated three additional native-search
summary slots are not exposed by this measurement model.

## Arithmetic boundaries

Kotlin Float operations preserve each native/NumPy scalar rounding boundary. JVM 17
strict floating-point expressions are not algebraically simplified or fused here.
`Math.exp`, `Math.pow` and `Math.sqrt` take Double and are narrowed to Float at the
same points as Desktop. In particular:

- CalcFixRaw polynomial, square roots, exponents, inverse and radiation stages retain
  Float boundaries; exponential combinations use Double with promoted **Float** 1.9/-0.9.
- InitTempParam b is `c1*c1 / (c0*(4f*c0))`, not the square of rounded a.
- LUT square root minus init_a occurs in Double, then narrows before GetTempEvn.
- Fourth-power input is the rounded Float calibrated+Kelvin; both pow results narrow.
- Short-distance factor is promoted Float `distance*.85f` plus Double 1.125;
  final multiply/divide/add is Double and narrows once. At effective distance >=60,
  the factor is Double 52.125 with division by 100.0 retained.
- Correction is added once as Float to LUT-selected pixels and summaries.

Negative-domain entries stay NaN, without clamp/interpolation/zero replacement.
Every selected pixel/summary must produce a finite value, including after correction.
Finite inputs that overflow intermediate arithmetic therefore cannot fabricate a
measurement. Unused NaN entries are expected and do not alone reject a frame.

For `radiometric-initial`: FPA 7180→37.222221375, calibration 3105→37.350006104,
GetFix 127, adjusted base 5873, init_a 66.528648376, init_b 4426.061035156,
linear 1.090986490, constant 1806.561279297, effective distance 3.
CalcFixRaw water/transmission/inverse/radiation are
10.296338081 / 0.988622308 / 1.032151580 / 246149488.
Tests compare the **bits**, rather than these rounded decimal descriptions.

## Golden evidence and reproducibility

Artifacts and all SHA-256 values are recorded in
[`core/src/test/resources/thermometry/manifest.json`](../core/src/test/resources/thermometry/manifest.json).
The exact Desktop oracle source hash is
`b5231e4799368bbba880978d3ab304bd8fdc19acf8c9cafb0355411d99e687f6`.
Provenance/scene treatment are in the adjacent fixture README and Desktop fixture README.
Raw files are original sanitized fixtures, not newly captured private frames.

| Fixture | Raw SHA-256 | Finite LUT entries | Reference |
|---|---|---:|---|
| radiometric-initial | aae1437a8e5d1f0f7dce03d9c14687545ad27bcc0ea3362d4664b5a5ba337815 | 13264 | Executed official x86_64 |
| radiometric-room-first | 8e5eb648c7dd632aae7810e89b86633ef79a5cf490b33ff364171cd0c0e80747 | 13079 | Executed official x86_64 |
| radiometric-room-range | 02c9ef0090da53e322265a36e92bbc53717433951941732838404633db96242d | 13081 | Executed official x86_64 |
| radiometric-room-shutter-held | fc1de1c0993e05c851f5aa80032d4b61d66677a2510f44c511030bc60426e2da | 13199 | Executed official x86_64 |
| radiometric-room-settled | fde6a4b803b68fcea07ab7b428f4769d22e896f01055969f43102bdc9270f5d6 | 13169 | Executed official x86_64 |
| warm-hand-settled | c31fda649ec08740e6ec2744dec037006c87b96ccfa685ca44d453615e50f4ae | 13249 | Desktop derived |

Five existing executed-native tables are verified before export: all finite bits
and NaN patterns match Desktop. NaN payload/sign is deliberately not compared;
undefined semantics/pattern are the invariant. The initial shuffled image is LUT-only;
held/early-stage fixtures do not imply settled readiness. Two synthetic cases derive
from settled-room in memory: stored distance 20 (effective 60) and base word 0
(wrapped base 65404, zero finite entries). Their full goldens also match exactly.

All **16384 entries per case**, every listed intermediate, NaN pattern and finite count
match Kotlin JVM. Two full matrices, **110592 pixels each**, match exactly including
all input words, matrix extrema, separate centers and trailer extrema/coordinates.
Finite differing-entry count **0**, maximum absolute error **0**, maximum ULP **0**.
Pixel 8 Android 17/arm64 ART repeated all eight tables and both matrices with the
same exact results (two instrumentation tests). No tolerance was relaxed.
This proves parity for these inputs/runtime versions, not all possible libm inputs,
other Android devices/ABIs, or the manufacturer's original ARM APK execution.

Settled room: matrix 20.813261..30.346817 °C; trailer center index5169/21.813532,
literal center index5170/21.837799, high5537 at(0,54), low5128 at(173,117).
Hand: matrix27.748077..37.379337 °C; trailer center5740/36.482746,
literal center5742/36.524605, high5783 at(191,211), low5340 at(329,83).
These are native-equivalent outputs, not independent target temperature standards.

```bash
../LMThermal-Desktop/.venv/bin/python tools/generate_thermometry_goldens.py
./gradlew :core:test :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Test dependencies AndroidX Test runner 1.6.2/JUnit extension1.2.1 and golden assets
are confined to the instrumentation APK. The product APK has no fixture assets,
Python, native APK thermometry library or new numerical dependency.

## Live integration and performance

A fresh LUT is built for every measurement frame. There is **no lookup cache** and
no assumption that calibration/settings remain constant. Numerical evidence reports
measurement evaluation time, which includes inspection, input decoding, LUT, matrix
and summaries; it is not an isolated LUT microbenchmark. Work stays on the serialized
I/O worker, with existing bounded/latest-frame handoff. Live timing/results are in
[ANDROID_VALIDATION.md](ANDROID_VALIDATION.md). If profiling later justifies caching,
the key must cover every exact affecting input/configuration and correction semantics.

Only bounded debug numeric reports are persisted after explicit initialization;
no scene payloads/images/hashes are recorded. No camera controls/timings/discards/
liveness criteria changed in this milestone. Touch measurements, ROI, Celsius
palettes and export/recording remain future work. Absolute physical accuracy still
requires independently measured surface targets with controlled environmental inputs.
