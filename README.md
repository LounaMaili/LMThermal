# LMThermal

> **Measurement status (2026-10-02):** Real HT-301 frames confirm 288 thermal
> image rows followed by four non-image trailer rows. The last 514 bytes are
> only part of that trailer. Native disassembly identifies field 356 as a
> duplicated calibration coefficient and locates the app's live center index
> in row 288. A clean official sequence produces true 14-bit image words
> starting with `zoom_absolute=32772`; full ThermViewer type-0 startup keeps
> display words. The reconstructed lookup matches executed official x86_64
> native arithmetic on raw fixtures. Independent temperature accuracy remains
> unvalidated. Desktop now exposes native-equivalent values; this Android foundation
> implements explicit radiometric initialization using exact SET completion and observed
> frame behavior; structural/live hardware acceptance passed on Pixel 8. Kotlin now reproduces the native-equivalent normal-range LUT and matrix; live measurement is separately gated. See
> [initialization evidence](docs/RADIOMETRIC_INITIALIZATION.md),
> [native call chain](docs/NATIVE_CALL_CHAIN.md), and
> [application comparison](docs/APPLICATION_COMPARISON.md). Desktop capture
> reports, sanitized fixtures and regression tables are maintained separately.

LMThermal is the native Android application for the **Infiray HT-301 / T3-317-13**.
It also preserves the authoritative protocol and thermometry research.
[LMThermal-Desktop](https://github.com/LounaMaili/LMThermal-Desktop) is the executable
Python/PyQt reference, diagnostic tool and fixture generator, not the final product.

## Android milestone

The first mobile foundation is a small Kotlin/Compose application in this repository:
USB Host authorization → native unconverted UVC acquisition → exact frame inspection
→ latest-frame grayscale aiming preview. The explicit radiometric session is implemented;
acceptance uses frame evidence rather than unreliable Zoom reads. The plain-JVM thermometry
engine has full golden LUT/matrix parity; a current ready frame must also pass finite-LUT
validation before numeric temperatures and high/low markers are shown. See
[thermometry design and parity](docs/ANDROID_THERMOMETRY.md), [session architecture](docs/ANDROID_RADIOMETRIC_SESSION.md),
[foundation architecture](docs/ANDROID_FOUNDATION.md),
[build configuration](docs/CONFIGURATION.md), and [real-device results](docs/ANDROID_VALIDATION.md).

```bash
./gradlew :core:test :app:assembleDebug :app:lintDebug
adb -s <paired-wireless-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 and the SDK/NDK packages documented in CONFIGURATION.md are required.
Connect the HT-301 through USB Host/OTG, launch LMThermal, tap **Connect / Open**,
and grant Android camera/USB permissions. Opening is read-only. From a display stream,
tap **Initialize radiometric** to qualify the baseline and run the validated sequence.
Readiness requires structural/liveness evidence. Measurement additionally requires valid current-frame thermometry. **Close** releases the stream; backgrounding
also releases it. Reopening remains explicit. Wireless ADB follows AGENTS.md.

Debug builds also provide **Test raw14 transition (32772)**, a one-shot frame-gated
experiment that cannot send range/shutter or claim readiness, and **Read zoom inventory**,
a GET-only diagnostic independent
of initialization. Request/descriptor evidence and unresolved semantics are documented
in [the Zoom audit](docs/ZOOM_CONTROL_SEMANTICS.md).

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

Android test-only parity uses AndroidX Test runner 1.6.2 / JUnit extension 1.2.1.
`./gradlew :app:connectedDebugAndroidTest` executes all golden tables/matrices on the phone
without opening USB. No golden data or test dependencies ship in the product APK.

## Supported camera contract

| Property | Value |
|---|---|
| Camera | Infiray HT-301 / T3-317-13 |
| USB identity | VID 0x1514, PID 0x0001 |
| Transport | UVC YUYV 384×292 at 25 FPS, exactly 224256 bytes |
| Native image | 384×288, first 221184 bytes |
| Non-image trailer | Last four rows, 3072 bytes retained intact |
| Native coordinates | x 0..383, y 0..287, unaffected by phone orientation |
| Display words | 0x80YY; aiming brightness only |
| Raw14 words | Complete uint16 values below 0x4000, never high-bit masked |

## Découvertes clés

### Architecture du flux de données

```
USB Camera (UVC/YUYV 384×292 @ 25fps)
  │
  ├─ Bytes 0-221,183 : Thermal image (288 rows)
  │   ├─ Default/type 0: YUYV display bytes, words 0x80YY
  │   └─ After 32772/type 1: full uint16 native lookup indices (<16384)
  │
  ├─ Bytes 221,184-223,741 : données non-image
  └─ Bytes 223,742-224,255 : bloc de paramètres (514 bytes)
      ├─ Correction, reflected and ambient temperatures, humidity, emissivity, distance
      ├─ Copies of five calibration coefficients from the earlier trailer
      └─ Field 356: copied calibration coefficient, not live center temperature
```

### Formule de température (GetTempEvn)

The native caller provides a lookup-derived value, a radiation term, and an
inverse correction factor. This formula does not accept an 8-bit Y pixel:

```python
def get_temp_evn(a, env_term, b):
    """Decoded arithmetic; a is lookup-derived, not a display Y byte."""
    val = (a + 273.15) ** 4.0 - env_term
    val = b * val
    return val ** 0.25 - 273.15
```

### Paramètres embarqués (514 bytes, fin de chaque frame)

| Offset | Exemple | Description |
|--------|---------|-------------|
| 4 | 25.0 | Reflected temperature |
| 8 | 25.0 | Ambient temperature |
| 12 | 0.45 | Humidity |
| 16 | 0.98 | Emissivity |
| 20 | 1 | Distance (uint16) |
| 352 | ~0.27 | Copy of calibration coefficient at byte 223494 |
| **356** | **~36.0** | **Copy of calibration coefficient at byte 223498** |
| 364 | ~0.006 | Copy of calibration coefficient at byte 223506 |
| 368 | ~0.82 | Copy of calibration coefficient at byte 223510 |

## Product roadmap

### Completed research/reference work

- [x] Linux discovery, unconverted capture, image/trailer separation and parameter extraction.
- [x] Official/ThermViewer APK comparisons and normal-path native arithmetic reconstruction.
- [x] Observed raw14 transition and evidence-gated Desktop radiometric session.
- [x] Desktop preview, native-equivalent matrix/cursor readings, Celsius palettes/range lock,
  still capture, ROI, offline inspection, time-series logging, bounded sequence recording/playback.
- [x] Wireless ADB plus Pixel 8 USB Host/HT-301 development path validated.
- [ ] Independent physical temperature calibration accuracy validation.

### Mobile product milestones

- [x] Kotlin/Compose project, Android USB permission and original UVC payload transport.
- [x] JVM parser/golden fixtures, bounded preview architecture and basic diagnostics.
- [x] Evidence-gated radiometric session: exact SET completion plus frame evidence; single-32772 and full Pixel 8 sequence passed, including conservative raw14 reopen.
- [x] Float32/native-equivalent LUT and measurement parity port for width 384/range 120/lens 68/shutter fix 1.5.
- [ ] Touch measurements, Celsius palette/range lock and ROI.
- [ ] Still export, time series, recording/playback and comparison.
- [ ] Release licensing, packaging and distribution.

Real-device acceptance evidence for the first foundation is tracked separately in
[ANDROID_VALIDATION.md](docs/ANDROID_VALIDATION.md). No fixture-only result can substitute
for the original full-payload Android camera test.

## Stack

- Android Kotlin 2.1.20, Compose/AndroidX, Gradle wrapper 8.11.1, AGP 8.9.2, JDK 17.
- compileSdk/targetSdk 35, minSdk 26; initial arm64-v8a APK supports the test Pixel 8.
- Native libusb 1.0.29 (LGPL-2.1-or-later), pinned libuvc 0.0.8 snapshot (BSD-3-Clause), NDK 28.
- Plain JVM `core/` holds immutable frame/parser and preview logic; no Python embedded.
- Third-party licenses and exact source pins: [third_party/README.md](third_party/README.md).
- Python analysis tools and historical prototypes remain research/reference material;
  Capstone 5.0.9, pyelftools 0.33 and Androguard 4.1.4 are analysis-only dependencies.

## Documentation

- [`docs/RADIOMETRIC_INITIALIZATION.md`](docs/RADIOMETRIC_INITIALIZATION.md) — Staged replay, byte encoding, ARM frame path and evidence limits

- [`docs/HARDWARE.md`](docs/HARDWARE.md) — Documentation technique du matériel
- [`docs/SPECIFICATION.md`](docs/SPECIFICATION.md) — Spécification des fonctionnalités
- [`docs/APK_ANALYSIS.md`](docs/APK_ANALYSIS.md) — Analyse du reverse engineering de l'APK
- [`docs/THERMOMETRY_LIB.md`](docs/THERMOMETRY_LIB.md) — Documentation de `libthermometry.so` (formules décodées)

## Références

- [Protocole InfiRay P2 Pro (reverse-engineered)](https://github.com/nicholasgasior/gopher-p2pro-ir) — commandes vendor USB documentées (ne s'applique pas directement au HT-301)
- [ftobler/infiray_p2_pro_python](https://github.com/ftobler/infiray_p2_pro_python) — approche P2 Pro (non compatible HT-301)
- [ThermViewer](https://thermviewer.com/) — ancienne application (abandonnée)
- [HTI HT-301](https://hti-instrument.com/collections/infrared-thermal-imager/products/ht-301-mobile-phone-thermal-imager) — matériel

## Licence

À déterminer.
