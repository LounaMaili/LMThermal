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
| 0 | 223,742 | Image data (YUYV, 288 usable rows × 384 pixels) |
| 223,742 | 514 | Temperature parameters |

### Temperature Parameters (514 bytes, float32 LE)

| Byte offset | Value | Description |
|-------------|-------|-------------|
| 0 | 0.0 | Unknown (mode?) |
| 4 | 25.0 | Environment temperature 1 |
| 8 | 25.0 | Environment temperature 2 |
| 12 | 0.45 | Emissivity |
| 16 | 0.98 | Distance factor |
| 20 | 1 | Flag (active?) |
| 24-351 | 0 | Reserved / unused |
| 352 | ~0.27 | Gain value (auto-adaptive) |
| 356 | ~36.0 | **Measured temperature at center (°C)** |
| 360 | varies | Additional measurement |
| 364 | ~0.006 | Offset factor |
| 368 | ~0.82 | Calibration factor |
| 376 | 25.0 | Env temp (repeat) |
| 380 | 25.0 | Env temp (repeat) |
| 384 | 0.45 | Emissivity (repeat) |
| 388 | 0.98 | Distance factor (repeat) |

### Key Discovery

The camera **embeds calculated temperature values in every frame**. Offset [356] contains the center-point temperature in °C. This means:
- Temperature calculation happens inside the camera firmware
- No vendor USB commands needed for basic temperature reading
- Each pixel's Y value maps to a temperature via auto-gain calibration
- Per-pixel temperature = `thermometryT(Y_value, params)` — the native lib formula

### Per-Pixel Temperature

The Y channel (luminance) of the YUYV frame represents thermal intensity:
- Y=0 → coldest in scene
- Y=255 → hottest in scene
- The camera auto-adjusts gain/offset per scene
- Empirical calibration: `T ≈ 0.2143 × Y - 3.14` (approximate, varies with auto-gain)
- For exact per-pixel: need `thermometryT()` from `libthermometry.so` or reverse the formula

## Key Finding

The temperature data is embedded **in the video stream**, not retrieved via separate USB commands. The native library `libthermometry.so` contains the proprietary calibration formula that converts raw pixel values to °C.

**Next steps:**
- Reverse engineer `thermometryT()` from `libthermometry.so` (small library, ~2.6KB code)
- Or: use the library directly via JNI/FFI on Linux
- Or: capture USB traffic while app runs to identify `SetParameter` commands for calibration data
