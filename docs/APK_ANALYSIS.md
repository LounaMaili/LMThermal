# LMThermal — APK Reverse Engineering

## Source: HT-301ThermCameraViewerX V6.4 (2024-12-04)

Downloaded from HTI manufacturer website.

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
   - `thermometryT()` — main temperature calculation (uses `exp`, `pow`, `sqrt` → complex calibration curve)
   - `thermometrySearch()` — search for temperature points
   - `thermometryT4Line()` — line measurement
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
- `nativeGetByteArrayTemperaturePara(nativePtr, index)` — reads calibration parameters from device via USB
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

### Temperature Parameters (514 bytes, float32 LE)

| Byte offset | Value | Description |
|-------------|-------|-------------|
| 0 | 0.0 | Unknown (mode?) |
| 4 | 25.0 | Environment temperature 1 |
| 8 | 25.0 | Environment temperature 2 |
| 12 | 0.45 | Emissivity |
| 16 | 0.98 | Distance factor |
| 20 | 1 | Flag (active?) |
| 24-351 | mixed | Undecoded, including device identifier bytes in captured frames |
| 352 | ~0.27 | Candidate gain field; interpretation unverified |
| 356 | ~36.0 | Candidate center-temperature field; observed constant across changing image frames, so its live meaning is unverified |
| 360 | ~0.00004 | Undecoded field |
| 364 | ~0.006 | Candidate offset factor; role unverified |
| 368 | ~0.82 | Candidate calibration factor; role unverified |
| 376 | 25.0 | Env temp (repeat) |
| 380 | 25.0 | Env temp (repeat) |
| 384 | 0.45 | Emissivity (repeat) |
| 388 | 0.98 | Distance factor (repeat) |

### Key Discovery

The camera embeds fields that decode as plausible numeric parameters. Field
356 was previously identified as center temperature, but it stayed exactly
35.99200058 through substantially changing image data in the 2026-09-26
capture. The location of a live center-temperature value, if one exists in
this stream, is still unknown. The native per-pixel call chain and mapping
from Y and frame parameters also remain unverified.

### Per-Pixel Temperature

The Y channel in rows 0–287 represents display brightness in the thermal
picture. Its mapping to Celsius, including any scene-dependent gain or offset,
has not been validated. The earlier empirical `T ≈ 0.2143 × Y - 3.14`
formula is only a rough visual guess and must not be used for measurements.
The complete native `thermometryT()` call chain is still needed.

## Key Finding

Parameter-like data is embedded in the video stream. The native library
contains thermometry functions, but their full input mapping and the source
of any live temperature output remain to be established.

**Next steps:**
- Reverse engineer `thermometryT()` from `libthermometry.so` (small library, ~2.6KB code)
- Or: use the library directly via JNI/FFI on Linux
- Or: capture USB traffic while app runs to identify `SetParameter` commands for calibration data
