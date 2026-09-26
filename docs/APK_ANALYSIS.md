# LMThermal — APK Reverse Engineering

## Source: HT-301ThermCameraViewerX V6.4 (2024-12-04)

Downloaded from HTI manufacturer website.

This file describes the official `com.hti.Xtherm` / `Hti Image` APK. A second
application, `com.bitera.ThermViewer` 2.0.23(ot), has now been inventoried.
It supports the HT-301 USB identity but has a distinct ARMv7 UVC bridge and
thermometry binary. Its output-type-zero HT-301 startup sends
`zoom_absolute=32773`, whereas this official app sends `32772` during preview
startup. Neither operation alone is proven to enable radiometric pixels; a
single-command Linux test of `32773` retained display words. The ordered
comparison and experiment are in
[APPLICATION_COMPARISON.md](APPLICATION_COMPARISON.md).

## Architecture

The app uses a layered architecture with native C/C++ libraries:

### Libraries

| Library | Purpose |
|---------|---------|
| `libusb100.so` | Low-level USB communication |
| `libuvc.so` | UVC protocol implementation |
| `libUVCCamera.so` | Main camera control + temperature processing |
| `libthermometry.so` | Temperature calculation (math: exp, pow, sqrt) |
| `libsimplePictureProcessing.so` | Image processing |
| `libjpeg-turbo1500.so` | JPEG encoding |

### Key Classes

- `com.serenegiant.usb.UVCCamera` — main camera wrapper, loads native libs
- `com.serenegiant.widget.UVCCameraTextureView` — renders thermal view + temperature overlay
- `com.serenegiant.usbcameracommon.AbstractUVCCameraHandler` — manages camera session

## Temperature Data Flow

1. **Video stream** captured via UVC (standard V4L2)
2. `nativeStartStopTemp(1)` starts a **dedicated temperature thread** (`temperature_thread_func`)
3. Thread waits on `pthread_cond_wait` for new frames
4. Raw frame bytes passed to `do_temperature_callback(JNIEnv*, byte[])`
5. **`libthermometry.so`** processes raw pixel data:
   - `thermometryT4Line()` — the app's 16,384-entry Celsius lookup builder
   - `thermometrySearch()` — maps trailer summary indices and image words through that lookup
   - `thermometryT()` — alternate exported builder, not imported by another APK library
   - `GetTempEvn()` — get environment temperature
   - `InitTempParam()` — initialize parameters
   - `CalcFixRaw()` — raw data correction
6. Results returned as `float[]` in °C
7. Java converts: `ShortTemperatureData[i] = (short)(temperatureData[i] * 10.0f + 2731.0f)`

### Temperature Conversion (Java side)
```java
// Celsius to internal format:
short internal = (short)(celsius * 10.0f + 2731.0f);

// Celsius to Fahrenheit (display):
fahrenheit = celsius * 1.8f + 32.0f;
```

### Temperature Parameters
- `nativeGetByteArrayTemperaturePara(nativePtr, length)` — copies 128 bytes from the frame trailer in the observed app call, with its last 16 bytes replaced from an earlier trailer location
- `nativeSetTempRange(nativePtr, range)` — sets temperature range (120°C or 400°C)
- `nativeWhenChangeTempPara()` — refresh after parameter change
- `nativeSetShutterFix(nativePtr, float)` — shutter correction
- `nativeSetCameraLens(nativePtr, int)` — lens type
- `nativeDistanceFix(nativePtr, distance, emissivity)` — distance correction
- `nativeThermFix(nativePtr, temp, emissivity)` — emissivity correction

## Frame Structure (HT-301, 384×292 YUYV)

Total frame size: 224,256 bytes (384 × 292 × 2)

| Offset | Size | Content |
|--------|------|----------|
| 0 | 221,184 | Thermal image data (YUYV, 288 usable rows × 384 pixels) |
| 221,184 | 2,558 | Non-image trailer before the documented parameter block |
| 223,742 | 514 | Documented parameter block, also part of the trailer |

The four final transport rows must not be searched for image extrema. This
boundary was confirmed with real frames on 2026-09-26. The prior table's
223742-byte image span included 2558 bytes of non-image data.

### Temperature Parameters (514 bytes, mixed fields with known float32 LE offsets)

| Byte offset | Value | Description |
|-------------|-------|-------------|
| 0 | 0.0 | Correction setting |
| 4 | 25.0 | Reflected temperature |
| 8 | 25.0 | Ambient temperature |
| 12 | 0.45 | Humidity |
| 16 | 0.98 | Emissivity |
| 20 | 1 | Distance (uint16) |
| 24-351 | mixed | Undecoded, including device identifier bytes in captured frames |
| 352 | ~0.27 | Copy of calibration coefficient at byte 223494 |
| 356 | ~36.0 | Copy of calibration coefficient at byte 223498; not the live center |
| 360 | ~0.00004 | Copy of calibration coefficient at byte 223502 |
| 364 | ~0.006 | Copy of calibration coefficient at byte 223506 |
| 368 | ~0.82 | Copy of calibration coefficient at byte 223510 |
| 376 | 25.0 | Reflected temperature (repeat) |
| 380 | 25.0 | Ambient temperature (repeat) |
| 384 | 0.45 | Humidity (repeat) |
| 388 | 0.98 | Emissivity (repeat) |

### Key Discovery

The app's Java code identifies the six user settings at block offsets 0–20.
Native code reads five calibration coefficients earlier in the trailer, at
223494–223513; the saved frames duplicate these at block offsets 352–371.
Thus field 356 is a calibration coefficient copy, not a live center reading.
`thermometrySearch` obtains a live center raw index at byte 221208 and maps it
through a native lookup. See [NATIVE_CALL_CHAIN.md](NATIVE_CALL_CHAIN.md).

### Per-Pixel Temperature

The saved image words are `0x8000 + Y`, exceeding the native lookup's 14-bit
index range. They are display brightness, not directly usable radiometric
indices. The earlier empirical `T ≈ 0.2143 × Y - 3.14` formula is invalid.
The official app calls `thermometryT4Line`, then `thermometrySearch`; a
controlled capture after app initialization is needed to determine how the
stream becomes compatible with that path.

## Key Finding

Parameter and calibration data are embedded in the video trailer. The native
functions' input mapping is now traced, and the app's center/high/low output
layout is known. The camera control sequence and native outputs needed to
validate a Python Celsius matrix remain unobserved.

**Next steps:**
- Capture full frames before and after official-app initialization and compare
  image words, trailer indices, and native callback temperatures.
- Decode the exact zoom-absolute/shutter command effect on radiometric mode;
  ThermViewer's `32773` alone was insufficient on Linux.
- Validate a float32-faithful lookup reconstruction against native outputs
  before implementing desktop Celsius measurements.
