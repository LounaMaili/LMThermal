# LMThermal — Hardware Technical Documentation

## Device: Infiray HT-301 (T3-317-13)

### Identification USB

| Field | Value |
|-------|-------|
| Vendor ID (VID) | `0x1514` (Infiray) |
| Product ID (PID) | `0x0001` |
| Device Name | T3-317-13 |
| Serial | 176295 (example, per-device) |
| Driver | `uvcvideo` (native Linux kernel) |

### USB Interfaces

The camera exposes **3 interfaces** via UVC (USB Video Class):

#### Interface 0 — Video Control
- Class: `0x0E` (Video), Subclass: `0x01` (Boot/Control)
- Standard UVC control interface for camera settings

#### Interface 1 — Video Streaming (alt 0 & 1)
- Class: `0x0E` (Video), Subclass: `0x02` (Streaming)
- **Alt setting 0**: No endpoints (idle)
- **Alt setting 1**: Isochronous endpoint `0x81` (IN), max packet size 780 bytes

### V4L2 Devices (Linux)

When connected, the kernel creates:

| Device | Function | Format |
|--------|----------|--------|
| `/dev/videoX` (1st) | Video Capture | YUYV 4:2:2, 384×292, 25 fps |
| `/dev/videoY` (2nd) | Metadata Capture | UVCH (UVC Payload Header Metadata), 10240 bytes buffer |

### Video Stream Details

| Property | Value |
|----------|-------|
| Resolution | 384 × 292 pixels |
| Pixel Format | `YUYV` (YUYV 4:2:2) |
| Bytes per Line | 768 |
| Frame Size | 224,256 bytes |
| Frame Rate | 25 fps |
| Color Space | sRGB |
| Transfer Function | sRGB |
| YCbCr Encoding | ITU-R 601 |

### Camera Controls (V4L2)

| Control | ID | Type | Range |
|---------|-----|------|-------|
| focus_absolute | `0x009a090a` | int | 0 – 65535 (default: 0) |
| zoom_absolute | `0x009a090d` | int | 0 – 65535 (default: 0) |

No extended/proprietary V4L2 controls are exposed by the driver.

### Temperature Data Access

**UPDATE (2026-04-18):** The raw temperature data **IS directly accessible** in the YUYV video stream. No vendor commands needed.

By capturing the stream with `cv2.CAP_PROP_CONVERT_RGB = 0` (disable OpenCV auto-conversion), the YUYV frame can be reinterpreted as uint16 values:

```
Frame shape: (292, 384, 2), dtype: uint8
→ reinterpret as uint16 → shape (292, 384)
```

#### Temperature Conversion (P2 Pro formula)

```
T(°C) = raw_uint16 / 64.0 - 273.15
```

- Range: approximately **-273°C to 751°C** (full 16-bit dynamic range)
- Typical ambient temperatures fall in a narrow band of this range
- Source: [infiray_p2_pro_python](https://github.com/ftobler/infiray_p2_pro_python)

#### How to Read

```python
camera = cv2.VideoCapture("/dev/video2", cv2.CAP_V4L2)
camera.set(cv2.CAP_PROP_CONVERT_RGB, 0)
ret, frame = camera.read()
raw16 = frame.view(np.uint16).reshape(292, 384)
temp_c = raw16.astype(np.float64) / 64.0 - 273.15
```

### Vendor USB Commands (secondary approach)

Vendor commands were initially investigated but the YUYV stream contains all needed data.
These commands may still be useful for camera configuration (palette, emissivity, etc.):

#### Protocol (reverse-engineered from InfiRay P2 Pro)

| Direction | bmRequestType | bRequest | wValue | Function |
|-----------|--------------|----------|--------|----------|
| Write | `0x41` | `0x45` | `0x78` | Send command |
| Read | `0xC1` | `0x44` | `0x78` | Read response |

#### Known Commands

| Command | Code | Description |
|---------|------|-------------|
| `y16_preview_start` | `0x010A` | Activate 16-bit raw temperature mode |
| `y16_preview_stop` | `0x020A` | Deactivate raw mode |
| `preview_start` | `0xC10F` | Start standard color preview |
| `preview_stop` | `0x020F` | Stop preview |
| `pseudo_color` | `0x8409` | Set color palette |
| `prop_tpd_params` | `0x8514` | Set emissivity, distance, etc. |
| `cur_vtemp` | `0x8B0D` | Read current sensor temperature |

#### Y16 Mode

When `y16_preview_start` is activated, the camera switches to a 16-bit raw output mode where each pixel represents a temperature value. The conversion from raw Y16 to °C needs to be validated experimentally (likely: `T(°C) = raw_value * scale_factor + offset`, typical for microbolometer sensors).

**Status**: Not yet tested on HT-301 — commands identified from P2 Pro reverse engineering. Need to validate compatibility.

### UVC Extension Unit

USB descriptor analysis reveals a **UVC Extension Unit** (`0x24 0x05`) in the device descriptor. This is the standard mechanism for vendor-specific functionality within the UVC framework. The extension unit handles proprietary commands (temperature data, configuration).

### Tested & Confirmed

- ✅ Camera detected natively by Linux kernel (Archlinux, kernel 6.19.11)
- ✅ `/dev/videoX` accessible via V4L2
- ✅ Video stream viewable in `ffplay`, `vlc`, etc.
- ✅ Single format: YUYV 384×292 @ 25fps (no other resolutions or formats available)
- ✅ USB capture shows vendor communication on control endpoint
- ✅ Raw temperature data accessible directly in YUYV stream via `CAP_PROP_CONVERT_RGB=0`
- ✅ Temperature conversion formula validated: `T(°C) = uint16 / 64 - 273.15`
- ✅ Frame read via OpenCV + V4L2 backend

### Not Yet Tested

- ⬜ Accuracy validation (compare with known temperature source)
- ⬜ Palette/color scheme commands (vendor commands)
- ⬜ Emissivity and distance parameter control
- ⬜ Compatibility of P2 Pro vendor commands with HT-301

### Reference: Similar Projects

- [gopher-p2pro-ir](https://github.com/nicholasgasior/gopher-p2pro-ir) — Go library for InfiRay P2 Pro, documents the vendor command protocol
- [thermviewer.com](https://thermviewer.com/) — Original (abandoned) Android app for HT-301
