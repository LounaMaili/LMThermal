# HT-301 native thermometry call chain

Evidence: HT-301ThermCameraViewerX 6.4.20241204, package `com.hti.Xtherm`.
APK SHA-256: `9079796632d4dac0733e81594d7bacaeaa912395bb1e84f1b077323106f21c45`.
The offsets below are x86_64 virtual addresses in `libthermometry.so` (SHA-256
`00fc62661ec6d407ecc8b2dd14a1d136701a386bd8f4b9e1ee342f8b42e7addd`)
and `libUVCCamera.so` (SHA-256
`fd86345f99fd3a922c020a6f5eed4464b1b7f890713ee43ef035f933fcdbdf81`).
The saved frame observations use the desktop repository's `scene-a.raw` and
`scene-b.raw`. Other ABIs are present but their instruction-level equivalence
has not been checked.

## Confirmed call path

```text
HomeActivity.startTemperaturing (on measurement selection)
  -> AbstractUVCCameraHandler.handleStartTemperaturing
  -> UVCCamera.startTemp -> nativeStartStopTemp(1) [libUVCCamera.so]
  -> UVCPreviewIR::startTemp -> temperature_thread_func -> do_temperature
  -> do_temperature_callback (0x17a40)
     if lookup needs refresh:
       -> thermometryT4Line (0x1500, libthermometry.so)
          -> InitTempParam (0x900)
          -> CalcFixRaw (0x940)
          -> GetFix (0x8c0)
          -> GetTempEvn (0x850), once per lookup entry
     -> thermometrySearch (0x2040, libthermometry.so)
     -> Java ITemperatureCallback.onReceiveTemperature(float[])
     -> UVCCameraTextureView.RenderThread temperature overlay / point readings
```

`libUVCCamera.so` imports `thermometryT4Line` and `thermometrySearch`; no
library in this APK imports the separately exported `thermometryT`. The
confirmed Android path uses `thermometryT4Line`, despite the old documentation
calling `thermometryT` the main function. The callback builds a 16,384-entry
float lookup table at native object offset `0x2c0` and uses it to convert
selected trailer readings and, in mode 4, image words. The Java float array has
`10 + width × (height - 4)` entries: `[0]` center temperature, `[1:3]` high
coordinates, `[3]` high temperature, `[4:6]` low coordinates, `[6]` low
temperature, `[7:10]` additional summary values, then row-major pixels from
`[10]`. Java's still export writes `short(Celsius*10 + 2731)` for pixel entries
and records dimensions `384 × 288`.

## Frame arguments and addresses

At `do_temperature_callback+0xe0`, the x86_64 caller passes width in `edi`,
height in `esi`, lookup output in `rdx`, and `rcx = frame +
(height - 4)*width*2`. For the observed 384 × 292 transport, `rcx` points to
byte `221184`, the start of row 288. The remaining register/stack arguments
include pointers to native object state, a shutter correction float, a range
integer, and a mode integer. The function contains width-specific branches;
all offsets here refer to its 384-wide branch.
`UVCPreviewIR::uvc_preview_frame_callback` (0x16880) copies `width*height*2`
bytes with `memcpy` into this frame buffer and swaps buffers; no pixel-word
conversion is visible between that copy and the thermometry callback. The
separate `libsimplePictureProcessing.so` is initialized for image rendering,
not called in this thermometry path.

| Relative to row 288 | Absolute byte | Native use | Saved-frame value |
|---:|---:|---|---:|
| `0x900` | 223488 | 16-bit lookup base | 6000 |
| `0x902` | 223490 | 16-bit input to a range-specific temperature transform | 3076 / 3117 |
| `0x906` | 223494 | float calibration coefficient passed to `InitTempParam` | 0.2705 |
| `0x90a` | 223498 | float calibration coefficient passed to `InitTempParam` | 35.992 |
| `0x90e`, `0x912`, `0x916` | 223502–223513 | three float polynomial coefficients | 0.00004, 0.0057, 0.8234 |
| `0x9fe` | 223742 | float correction | 0 |
| `0xa02` | 223746 | float reflected temperature | 25 |
| `0xa06` | 223750 | float ambient temperature | 25 |
| `0xa0a` | 223754 | float humidity | 0.45 |
| `0xa0e` | 223758 | float emissivity | 0.98 |
| `0xa12` | 223762 | uint16 distance | 1 |

The six parameter names in the last rows are confirmed by
`HomeActivity.getDeviceTempParameter`: it decodes the 128-byte return of
`nativeGetByteArrayTemperaturePara` at offsets 0, 4, 8, 12, 16 and 20, then
stores them using `setCorrection`, `setReflection`, `setAmbtemp`,
`setHumidity`, `setEmissivity`, and `setDistance`. The `getByteArrayTemperaturePara`
native function copies from frame byte 223742 for width 384 and overwrites
the last 16 bytes of its 128-byte result from an earlier trailer location.
It does not return the 514-byte trailing region in full.

`InitTempParam(x, y)` receives coefficients at 223494 and 223498, producing
`a = y/(2x)` and `b = y²/(4x²)`. `CalcFixRaw(t,c,d,e,f)` receives ambient
temperature, humidity, distance, emissivity, and reflected temperature in that
order. All five source mappings are confirmed by register tracing at
`thermometryT4Line+0x2a0` through `+0x2d8`; the physical units are inferred
from Java setting names, not from the arithmetic alone. `GetTempEvn` receives
a lookup-derived Celsius-like value, the radiation correction output of
`CalcFixRaw`, and its reciprocal factor output. It does **not** receive an
8-bit Y byte or `gain × emissivity`.

The x86_64 System V ABI passes `CalcFixRaw`'s five float inputs in
`xmm0..xmm4` and four float output pointers in `rdi`, `rsi`, `rdx`, `rcx`.
The caller stores those outputs in stack slots and passes output 4 in `xmm1`
and output 3 in `xmm2` to `GetTempEvn` (whose first argument is in `xmm0`).
`InitTempParam` takes its two input floats in `xmm0/xmm1` and output pointers
in `rdi/rsi`. Native `exp` and `pow` calls use double arguments and results;
several intermediate values are converted back to float32 before the next
stage. See [THERMOMETRY_LIB.md](THERMOMETRY_LIB.md) for the full normal-path
`CalcFixRaw` expression and constants.

For each lookup index `i = 0..16383`, the 384-wide branch calculates a
calibrated intermediate from `i - lookup_base`, coefficients at `0x906..0x916`,
and the two `InitTempParam` outputs; it then calls `GetTempEvn` and applies a
range/distance-dependent final correction. This is a lookup **over 14-bit raw
indices**, not a conversion of 8-bit display brightness.

The 384-wide finite-value branch can be written algebraically (native float32
rounding is omitted here). Let `u0/u1` be the 16-bit values at `0x900/0x902`,
`c0..c4` the floats at `0x906..0x916`, and `s` the native shutter-fix input:

```text
sensor_term = 20 - (u1 - 7800) / 36
fix = GetFix(range, 384, sensor_term)
base = uint16(u0 - fix)
init_a, init_b = InitTempParam(c0, c1)
linear = c2*sensor_term² + c3*sensor_term + c4
constant = c0*(sensor_term+s)² + c1*(sensor_term+s)
calibrated(i) = sqrt(((i-base)*linear + constant)/c0 + init_b) - init_a
corrected(i) = GetTempEvn(calibrated(i), CalcFixRaw.out4, CalcFixRaw.out3)
```

`GetFix(120,384,x)` returns `max(0,trunc(390-7.05*x))`; other range/width
combinations take different branches. After `corrected(i)`, native code adds
a further correction based on camera mode, distance, and ambient temperature.
For one branch with distance at most 60, that term is
`(corrected(i)-ambient)*(0.85*distance+1.125)/100`. This branch has not been
matched to an official-app setting or independently checked against its
output; the expression is a disassembly trace, not a calibrated API.

`thermometrySearch` reads selected 16-bit summary values at bytes
`221188..221212`. Specifically, the raw index at byte `221208` becomes
`float[0]`, the app's live center reading after lookup; bytes `221192` and
`221198` feed high and low lookup values. In mode 4 it also walks the first
`width × (height-4)` image words as little-endian uint16 and requires each
index to be at most `0x3fff`. It does not include the four trailer rows in
the pixel matrix.

## Why the saved Linux frames cannot validate Celsius

In the two saved frames, image words are `0x8000 + Y` (range 32768–33011 in
scene A, 32854–32910 in scene B), above the native lookup's `0x3fff` limit.
The old P2 Pro `uint16/64-273.15` mapping and direct `GetTempEvn(Y,...)`
mapping therefore use the wrong input representation. The trailer summary
indices are in range (`221208` is 5165 / 5255), but the calibration input at
`223490` is 3076 / 3117, which the 384-wide native transform maps to about
151.22 / 150.08 °C before shutter correction. This does not establish a
credible native reading for these uninitialized/default-mode fixtures.

The two frames duplicate all five coefficient floats at 223494–223513 in the
last 514-byte block at offsets 352–371. In particular, block field 356 is
bit-identical to the coefficient at byte 223498 (`35.99200058`). The native
lookup builder reads the earlier copy as an `InitTempParam` input. The Java
128-byte parameter reader never reaches field 356. This is strong evidence
that field 356 is a calibration coefficient copy, not a live center
thermometer. The live center path uses byte 221208 and the lookup table.

## Initialization and remaining evidence

`HomeActivity.startDevicePreview` schedules zoom-absolute control values
`32772`, `32800`, and `32768`; it also sets temperature range 120 and shutter
fix 1.5. Measurement start calls `nativeStartStopTemp(1)`, schedules a shutter
refresh and another `32768` control. These are confirmed Java calls, not a
decoded wire protocol. The exact camera mode transition and timing that yield
14-bit image words remain unknown. The existing `test_y16*.py` scripts tried
P2 Pro-style vendor commands but do not establish HT-301 compatibility.

The next discriminating experiment requires a working camera and a capture
before and after the official app's initialization (or a verified replay of
its controls), recording full frames and the Java/native output for the same
scene. Check whether image words drop below `0x4000`, whether the trailer
sensor-temperature word approaches the native calibration branch's expected
range, and whether lookup predictions agree with native center, high, low,
and pixel temperatures across multiple targets. Until those observations
exist, no Python Celsius matrix or measurement-accuracy claim is justified.
No `/dev/video*` device or `/dev/v4l/by-id` link was present during this
audit, so no live camera experiment was possible; the two existing sanitized
frames were used for the input-boundary checks.
