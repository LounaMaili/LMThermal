# Changelog

All notable changes to LMThermal will be documented in this file.

## [Unreleased]

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
