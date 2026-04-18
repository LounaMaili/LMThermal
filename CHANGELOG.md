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
- Raw temperature data is directly in the YUYV video stream (no vendor commands needed)
- Temperature conversion: `T(°C) = uint16_value / 64.0 - 273.15`
- OpenCV with `CAP_PROP_CONVERT_RGB=0` + `CAP_V4L2` backend gives raw uint16 frame
- Frame shape: (292, 384, 2) uint8 → reinterpreted as (292, 384) uint16
- Vendor USB commands (0x41/0x45 write, 0xC1/0x44 read) — writes accepted, reads timeout (may need different params)
- Reference project: [ftobler/infiray_p2_pro_python](https://github.com/ftobler/infiray_p2_pro_python)
