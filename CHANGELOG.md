# Changelog

All notable changes to LMThermal will be documented in this file.

## [Unreleased]

### Added (Android USB/UVC foundation — 2026-10-02)
- Added the native Kotlin/Compose Android product in this repository, with a plain JVM core and pinned Gradle wrapper/toolchain.
- Added Android camera/USB authorization, VID/PID-specific discovery, a read-only NDK libusb/libuvc transport with unconverted full-payload callbacks, and explicit close/reopen lifecycle handling.
- Ported Desktop's exact frame/image/trailer boundaries and display/raw14 candidate inspection without high-bit masking or thermometry. Preview normalization is independent and display-only.
- Added sanitized Desktop golden fixtures, ownership/classification tests and bounded latest-value tests; documented architecture, acquisition choices, dependency licenses and configuration.
- Added app-private debug payload evidence capture for verifying trailer preservation; unsanitized camera bytes are excluded from Git. Real-device acceptance is recorded in docs/ANDROID_VALIDATION.md.

### Fixed (Android foundation review — 2026-10-02)
- Kept attached device identity/authorization in diagnostics across normal stream close.
- Serialized frame publication with close/reset so an in-flight worker cannot restore a stale preview after release; added a concurrent regression.
- Selected the first valid exact debug payload for acquisition evidence, excluding size-correct mixed startup frames.

### Changed (Android product direction — 2026-10-02)
- Updated the README roadmap and specification to identify Android as the product and Desktop as the executable reference, diagnostic and fixture source.
- Deferred camera setting writes, radiometric readiness and Celsius/LUT porting to later focused milestones.

### Added (Desktop measurement foundation — 2026-09-29)
- Documented the reusable normal-range radiometric session, evidence-gated post-shutter readiness and read-only handling of raw14 streams with unknown host state. These are software acceptance rules built on the completed camera findings, not a new hardware protocol claim.
- Specified the separate OpenCV diagnostic aiming preview, display-only raw14 normalization and strict separation from native-equivalent thermometry. Independent physical accuracy validation remains outstanding.

### Added (Operator-confirmed hand discrimination — 2026-09-29)
- Documented 23 distinct live raw14 hand/background frames with an 8.596 °C mean ROI difference, consistent extrema coordinates, and the observed trailer/literal-center mismatch; no physical accuracy claim is made.
- Recorded a second 31-frame held image interval after the initial 75-frame post-shutter discard and one malformed frame, requiring liveness checks beyond a fixed settling count.

### Added (Shutter and matrix validation — 2026-09-29)
- Documented the roughly 1.3-second held-frame shutter interval, a setup-specific minimum 75-frame post-shutter discard, and a later 75-frame window of distinct raw14 images with spatially consistent trailer extrema.
- Recorded the experimental full temperature-matrix interface and the boundary between native arithmetic equivalence, relative scene discrimination, and independent physical accuracy.

### Added (Full radiometric initialization — 2026-09-27)
- Traced the ThermViewer ARMv7 UVC copy through the temperature callback and confirmed that both native searches reject full uint16 words >=0x4000 without pixel masking or flag stripping.
- Decoded all thermometry parameter byte commands and Handler delays; separated native host range/shutter/LUT state from device writes through standard UVC zoom absolute.
- Documented the observed `32772` raw14 transition and complete official replay, plus the separately reset ThermViewer type-0 replay that changed emissivity but kept display words.
- Documented the standalone desktop lookup and execution against the official x86_64 ELF, with matching full reference tables and center/high/low outputs. Independent physical accuracy remains unresolved.
- Added `docs/RADIOMETRIC_INITIALIZATION.md`, updated both APK comparisons and maintained original interrupted-session evidence without treating lost temporary reports as surviving artifacts.

### Corrected (Native interpretation — 2026-09-27)
- Distinguished byte 221186's FPA transform from byte 223490's `word/10-273.15` calibration input, correcting the former erroneous ~150 °C interpretation.
- Identified default native lens 68, its threefold stored-distance multiplier and its specific final correction branch.
- Identified ThermViewer's spot-0 center override, extra correction at byte 223514 and ten-float output in type 0; output type depends on `images_form_camera`.
- Corrected host-only refresh versus SDK device shutter naming, ThermViewer's float distance versus official uint16 distance, and stale hardware/specification/measurement-status statements. End-of-task workflow requirements remain intact.

### Added (Radiometric-mode comparison — 2026-09-26)
- Inventoried the newly supplied ThermViewer 2.0.23(ot) APK, its USB filters, ABIs, ARMv7 libraries, and distinct HT-301 UVC/thermometry path.
- Added `docs/APPLICATION_COMPARISON.md` with ordered startup controls, explicit evidence status, and a controlled Linux before/after experiment.
- Traced ThermViewer output type 0 to `zoom_absolute=32773`, output type 1 to `32772`, range commands to `32800/32801`, and shutter refresh to `32768` on its HT-301 path.
- Confirmed experimentally that `32773` alone did not turn current Linux `0x80YY` image words into 14-bit indices; the full app path remains unverified.

### Added (Native thermometry audit — 2026-09-26)
- Inventoried the only available HT-301 APK, five ABI sets of native libraries, decompiled Java temperature bridge, and 20 legacy scripts without changing research originals.
- Traced the Android path through `thermometryT4Line` and `thermometrySearch`, including a 16,384-entry lookup, 384 × 288 pixel array, trailer summary readings, and Java center/high/low positions.
- Reconstructed `CalcFixRaw` normal-path arithmetic and mapped its five inputs to the app's ambient, humidity, distance, emissivity, and reflected-temperature settings.
- Added `docs/NATIVE_CALL_CHAIN.md` and `docs/RESEARCH_INVENTORY.md` with binary hashes, evidence, and remaining initialization questions.

### Corrected (Native thermometry audit — 2026-09-26)
- Identified block fields 352–371 as copies of five preceding trailer calibration floats; field 356 is a calibration coefficient copy, while the app maps a live center index at frame byte 221208 through its lookup.
- Corrected parameter labels: offsets 4/8/12/16/20 are reflected temperature, ambient temperature, humidity, emissivity, and uint16 distance.
- Corrected the historical `CalcFixRaw` expression: its second exponential has a negative ~0.9 multiplier, not 100.0.
- Found that the saved V4L2 image words are `0x8000 + Y` and exceed the native 14-bit lookup range; a visible image does not establish radiometric input or validated Celsius measurements.

### Changed
- Added persistent agent workflow instructions requiring documentation/changelog maintenance and pushing completed task branches to GitHub for review.

### Corrected (Measurement audit — 2026-09-26)
- Confirmed that only the first 288 rows of the 292-row transport are thermal image; 2558 non-image bytes precede the 514-byte parameter block.
- Reclassified parameter field 356 as an unverified center-temperature candidate after it stayed constant across changing live image data.
- Rejected the desktop prototype's `gain × emissivity` thermometry input mapping; it gave −55.10 °C at one center while field 356 was 35.992.
- Corrected the historical claim that the P2 Pro `uint16/64 - 273.15` conversion was validated on HT-301.

Historical discoveries below record earlier conclusions; the corrections above supersede contradictory temperature and initialization claims.

### Added
- Project repository created
- README with project plan (6 phases)
- `docs/HARDWARE.md` — hardware technical documentation (USB IDs, video stream, vendor commands)
- `docs/SPECIFICATION.md` — application feature specification
- `REPO_RULES.md` — repository contribution rules
- `CHANGELOG.md` — this file

### Discovered
- YUYV stream accessible via OpenCV (`CAP_PROP_CONVERT_RGB=0` + `CAP_V4L2` backend)
- Frame shape: (292, 384, 2) uint8 — but appears as sensor noise without initialization
- P2 Pro formula (`uint16/64 - 273.15`) gives unrealistic values on HT-301 (not the same camera)
- Vendor USB writes accepted (2-4 bytes) but reads always timeout (protocol differs from P2 Pro)
- Reference project: [ftobler/infiray_p2_pro_python](https://github.com/ftobler/infiray_p2_pro_python)
- **Key finding**: HT-301 requires vendor command initialization sequence before valid thermal output
- **Blocker**: need USB traffic capture from Android app to discover initialization commands
- **Breakthrough**: decompiled APK (HT-301ThermCameraViewerX V6.4) — temperature is in video stream
- `libthermometry.so` converts raw pixels to °C via `thermometryT()` (uses exp/pow/sqrt)
- Temperature thread processes frames: `startTemp()` → `temperature_thread_func` → `do_temperature_callback`
- Calibration: `GetTempEvn()`, `InitTempParam()`, `CalcFixRaw()`
- Java conversion: `short = celsius * 10.0 + 2731.0`
- Created `docs/APK_ANALYSIS.md` with full architecture analysis

### Discovered (Session 2 — 2026-04-18)
- **Frame structure decoded**: last 514 bytes of each frame contain temperature parameters
- **Parameters**: env_temp (25°C), emissivity (0.45), distance_factor (0.98), gain, center temperature
- **Center temperature**: pre-calculated by camera firmware, available at offset 356 in params
- **GetTempEvn() fully decoded**: uses Stefan-Boltzmann law (T⁴ / ⁴√T) with env_temp correction
  - Formula: `T = pow(b * (pow(raw + 273.15, 4.0) - env_temp), 0.25) - 273.15`
- **InitTempParam() decoded**: computes calibration `a = y/(2x)`, `b = (y/2x)²`
- **CalcFixRaw() partially decoded**: cubic polynomial → exp(), then sqrt/exp correction chain
  - Uses polynomial: `P(t) = 1.5587 + 0.06939t - 0.000278t² + 6.86e-7t³`
- **All .rodata constants extracted**: 27 constants decoded (float32 and float64)
- **Empirical calibration validated**: face ~35°C, wall ~20°C (room at 20°C)

### Added (Session 2 — 2026-04-18)
- `docs/THERMOMETRY_LIB.md` — complete reverse engineering documentation of libthermometry.so
- `prototype/thermal_capture.py` — Python prototype with frame capture, param extraction, temp calculation
- Live view mode with temperature overlay and click-to-measure
