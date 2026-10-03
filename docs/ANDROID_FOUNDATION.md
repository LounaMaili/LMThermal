# Android USB/UVC foundation

> Current extension (2026-10-03): the separate Kotlin finite-LUT/current-frame
> measurement gate and separate Celsius/touch presentation are implemented and
> validated. The original milestone account below describes its earlier scope.
> Session controls, timings and liveness criteria are unchanged. See
> [ANDROID_THERMOMETRY.md](ANDROID_THERMOMETRY.md) and
> [ANDROID_CELSIUS_PRESENTATION.md](ANDROID_CELSIUS_PRESENTATION.md).

## Scope and architecture

LMThermal is the Android product repository. LMThermal-Desktop is the executable
reference and fixture source. This milestone is a **read-only aiming preview**;
raw14 candidates are not radiometric-ready measurements and no Celsius is shown.

```text
Android UsbManager permission → authorized UsbDeviceConnection descriptor
  → JNI / libusb / libuvc YUYV callback (384×292, no conversion)
  → one native pending payload → Kotlin worker → core Ht301Frame / inspect
  → independent PreviewRenderer → conflated StateFlow → Compose
```

- `app/`: Android permissions/discovery/lifecycle, JNI source, worker and minimal Compose UI.
- `core/`: plain JVM frame model, Desktop-equivalent inspector, preview normalization,
  USB status types and tested conflated latest-state handoff. No Android dependency.
- `third_party/`: pinned source snapshots and licensing/provenance.
- `core/src/test/resources/fixtures/`: two deliberate, unchanged sanitized Desktop frames.

Future camera-control, session, thermometry and measurement components must remain
separate from the source/renderer. The foundation transport initially had no setting write API. The subsequent
[explicit session milestone](ANDROID_RADIOMETRIC_SESSION.md) adds a separate restricted
control interface on the same handle. Opening remains read-only, performing standard
UVC probe/commit and interface negotiation;
it never sends radiometric initialization or manufacturer/vendor settings.

## Acquisition decision

Chosen: Android USB Host + NDK **libusb 1.0.29** + **libuvc 0.0.8 development snapshot**.
Exact source revisions/licenses are in [third_party/README.md](../third_party/README.md).
The upstream `uvc_wrap` API borrows UsbManager's already-authorized device fd.
Global usbfs enumeration is disabled, so unrooted Android access uses the standard
permission mechanism. libuvc owns its event-handler context and asynchronous transfers.

The raw `uvc_frame_t` callback exposes original frame data and `data_bytes`.
No YUYV-to-RGB conversion, camera-format reinterpretation, 288-row cropping or
trailer extraction occurs in native transport. JNI copies callback-owned memory
before upstream reuse. Wrong dimensions/format are counted and dropped; partial
or oversized payloads cannot be accepted as an exact frame. Kotlin then requires
exactly 224256 bytes. UVC packet headers are reassembled by libuvc, not part of the
transport-frame payload; the camera's four in-frame trailer rows remain intact.

Alternatives evaluated:

- Android framework USB bulk APIs alone are unsuitable for the camera's documented
  isochronous streaming endpoint (interface 1, alt 1, IN 0x81).
- Android UVC wrappers such as [AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera)
  add useful rendering/recording APIs but need an audit of their raw callback conversion,
  native forks and per-component licenses. They are unnecessary for this small exact-payload
  bridge. No wrapper code/dependency is adopted.
- Camera2/RGB-only callbacks cannot satisfy trailer preservation; a convincing RGB
  preview is insufficient evidence for acquisition correctness.

Upstream references: [libusb Android integration](https://github.com/libusb/libusb/blob/v1.0.29/android/README),
[libuvc fd ownership](https://github.com/libuvc/libuvc/blob/d07de4fa23905ee8cac06f5d24b2a03d42a3b363/src/device.c),
[Android USB Host](https://developer.android.com/develop/connectivity/usb/host).

## Frame parity and ownership

`Ht301Frame` owns a private full-frame copy. Constructor input and returned byte
arrays cannot mutate that storage. Native dimensions/coordinates stay 384×288,
x 0..383/y 0..287, regardless of Activity orientation.

- Transport: 384×292×2 = 224256 bytes.
- Image: first 221184 bytes, 384×288 full little-endian words.
- Trailer: remaining 3072 bytes (four rows), not just the last 514-byte parameter block.
- Display: **every** image word has `(word & 0xff00) == 0x8000`.
- Otherwise any word >=0x4000 makes the frame INVALID. No masking of high bits.
- With all image words below 0x4000: RAW14 candidate; finite settings, positive calibration
  coefficient 0/emissivity/distance, nonnegative humidity, calibration-copy equality and
  summary-index range are checked exactly as Desktop `inspect_frame`.
- Summary consistency additionally checks coordinates, actual image extrema and selected
  pixel values. A mismatch remains an observation, never an invented region/center algorithm.

Display preview uses each word's low Y byte. Raw14 preview uses independent 1st/99th
percentile grayscale normalization. Neither changes the source data. No preview pixel
is fed into measurement. Invalid image words show no preview; candidate calibration
rejections are visible in diagnostics and cannot establish temperature readiness.

## Concurrency and lifecycle

Native callback holds one bounded pending payload; a new frame replaces obsolete
presentation work. JNI read waits at most 250 ms. Kotlin parsing/normalization/bitmap
creation and native open/read/stats/close run on one serialized worker dispatcher.
StateFlow is conflated, not an unbounded queue. Acquisition FPS uses native callback
counts and elapsed monotonic time, independently of rendering rate. Replacement,
malformed and parser rejection counts are shown/logged.

Android CAMERA runtime authorization precedes the device-specific USB permission
(required for USB video on Android 9+). VID/PID-filtered discovery handles attached
at launch and attach notifications. Denial does not open the camera. An explicit
Connect/Open starts acquisition; attach/foreground does not start it automatically.
Detach cancels and clears stale presentation. A generation token prevents old work
from publishing after disconnect. Ownership checking and publication are serialized
with reset so an in-flight update cannot restore a stale final preview. Native close joins transfers/callbacks before
closing the borrowed Android connection. Returning from background or rotation
requires explicit Connect/Open; background releases the camera. Final ViewModel
release unregisters the receiver. No root, foreground service or wake lock is used.

## Build and supported platform

See [CONFIGURATION.md](CONFIGURATION.md). Compile/target SDK **35**, minimum **26**,
JDK 17, AGP 8.9.2, Gradle 8.11.1, Kotlin/Compose compiler 2.1.20, Compose BOM
2025.04.01, Activity 1.10.1, Lifecycle 2.8.7, coroutines 1.10.1, NDK 28.0.13004108,
CMake 3.22.1. This compatible, pinned toolchain does not require API 37 to compile
for the Pixel 8 running Android 17. The initial APK builds **arm64-v8a only**;
other ABIs/devices remain untested. NDK 28 provides 16 KiB shared-library alignment.

```bash
./gradlew :core:test :app:assembleDebug :app:lintDebug
adb -s <paired-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <paired-serial> shell am start -n org.lmthermal.app/.MainActivity
adb -s <paired-serial> logcat -s LMThermal
```

Use the wireless discovery/reconnection procedure in AGENTS.md, not a fixed port.

## Exact-payload validation evidence

The original foundation debug builds saved the first valid exact payload of each connection to app-private
`files/validation-frame.raw` solely to verify acquisition on the host. It may contain
camera identifiers/private scene content: never commit it or copy it into shared
storage as a fixture without deliberate sanitization. This is not a still export UI.
Retrieve with `adb exec-out run-as org.lmthermal.app cat files/validation-frame.raw`
into ignored/local storage, inspect against the Desktop parser, then remove it when
finished. Release builds did not save it. The radiometric-session milestone removes automatic
debug saving as well; its diagnostics contain numeric evidence only.

Real-device results are recorded in [ANDROID_VALIDATION.md](ANDROID_VALIDATION.md).
Fixture success alone does not prove Android transport preservation.

## Radiometric continuation

The explicit session implementation and tests are documented in
[ANDROID_RADIOMETRIC_SESSION.md](ANDROID_RADIOMETRIC_SESSION.md). Hardware acceptance is
blocked by a direct zoom GET_CUR baseline of 1 versus the required Desktop zero. Resolve
that readback boundary before declaring session parity or proceeding to thermometry.

Then port thermometry separately: extract the same offsets, preserve Float versus
Double operations/rounding and the 16384-entry LUT including undefined entries;
compare every entry/intermediate and summary/matrix outputs to Desktop golden tables
for range 120, lens 68, shutter fix 1.5. Keep byte 221186's FPA input distinct from
223490's calibration temperature and parameter field 356's duplicated coefficient.
Trailer center and literal `(192,144)` stay distinct. No empirical formula/constant
substitutions. See NATIVE_CALL_CHAIN.md and THERMOMETRY_LIB.md.

Future output must say: **Native-equivalent temperatures; absolute physical accuracy
not yet independently validated.** ROI, palette/range controls, exports, logging and
sequence playback are later milestones.
