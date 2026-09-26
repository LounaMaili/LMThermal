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
| Confirmed thermal image area | 384 × 288 pixels (rows 0–287) |
| Non-image trailer | 4 transport rows (bytes 221184–224255) |
| Documented parameter block | Bytes 223742–224255 (last 514 bytes of trailer) |
| Color Space | sRGB |
| Transfer Function | sRGB |
| YCbCr Encoding | ITU-R 601 |

### Camera Controls (V4L2)

| Control | ID | Type | Range |
|---------|-----|------|-------|
| focus_absolute | `0x009a090a` | int | 0 – 65535 (default: 0) |
| zoom_absolute | `0x009a090d` | int | 0 – 65535 (default: 0) |

No extended/proprietary V4L2 controls are exposed by the driver.

### Output-type control experiment (2026-09-26)

ThermViewer 2.0.23(ot)'s HT-301 branch maps output type `0` to
`zoom_absolute=32773` and type `1` to `32772`. Its startup also begins native
thermometry, sends settings, and refreshes the shutter. The precise firmware
meaning of either output type is unresolved. A Linux test of **only**
`zoom_absolute=32773` succeeded at the V4L2 API and read back as `32773`, but
after 20 settling frames, 0% of the 384 × 288 image words fit the native
`0..16383` lookup. The post-control image word range across three frames was
32778–33010, still consistent with `0x8000 + Y` display words. Calibration
fields and the image's spatial variation remained present. This rules out a
single-command transition under the tested conditions; it does not rule out
the full Android startup path. See
[APPLICATION_COMPARISON.md](APPLICATION_COMPARISON.md).

### Temperature Data Access

**Measurement audit (2026-09-26):** Live OpenCV/V4L2 captures confirm the
transport is `(292, 384, 2)` and 224256 bytes, but only rows 0–287 contain
the thermal image. Row 288 contains sparse binary data, rows 289–290 are
zero-filled, and row 291 contains parameters and device identifiers. Excluding
only the final 514 bytes leaves 2558 bytes of non-image trailer in image
statistics. The Y value maximum of one complete transport frame was 255 in
row 291; the true 288-row image maximum was 243. The original claim that all
bytes before 223742 are image data is incorrect.

The field at parameter offset 356 was 35.99200058 in 158 of 160 live frames
despite transport-center Y varying from 115 to 125. Two frames had zeroed
fields. The native lookup builder reads an identical coefficient at frame
byte 223498; both saved fixtures duplicate all five coefficients from
223494–223513 at parameter offsets 352–371. Thus field 356 is a copied
calibration coefficient, not the app's live center output. The app instead
maps a 16-bit center index at frame byte 221208 through its lookup. See
[NATIVE_CALL_CHAIN.md](NATIVE_CALL_CHAIN.md) and the desktop repository's
`docs/MEASUREMENT_AUDIT.md`.

A later end-to-end diagnostic observed transport-center Y = 201 and image
mean Y = 142.72 with the same field 356 value.

**UPDATE (2026-04-18):** The P2 Pro approach (YUYV → uint16 reinterpretation) does **NOT** work on the HT-301.

- Earlier captures appeared to be noise; later 2026-09-26 captures produced a recognizable thermal image without a vendor initialization sequence
- The P2 Pro temperature formula gives unrealistic values (240°C center for ambient)
- A vendor initialization requirement is not established by the current captures

The raw stream is accessible via OpenCV:
```python
camera = cv2.VideoCapture("/dev/video2", cv2.CAP_V4L2)
camera.set(cv2.CAP_PROP_CONVERT_RGB, 0)
ret, frame = camera.read()  # shape (292, 384, 2) uint8
```

Recent captures contain a usable 288-row thermal picture without an explicit
vendor initialization step. Their image words are `0x8000 + Y`; the app's
thermometry lookup accepts only 14-bit indices (`0..16383`). A usable display
stream therefore does not demonstrate radiometric input. The controls needed
to obtain compatible image words remain to be established, even after the
single ThermViewer output-type-zero control experiment above.

Future USB traces may help identify the camera mode controls. Initialization
is not required for the observed picture, but may be required for the native
radiometric calculation.

### Vendor USB Commands (secondary approach)

Vendor commands were initially investigated. The default Linux YUYV stream
contains a visible image and trailer settings, but its image words exceed the
native thermometry lookup range. Camera configuration may therefore be needed
to obtain radiometric image words. The commands below remain unverified on
HT-301:

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

The P2 Pro `y16_preview_start` command is documented elsewhere, but its
behavior on HT-301 has not been tested. No Y16-to-Celsius conversion is
validated for this camera.

**Status**: Not yet tested on HT-301 — commands identified from P2 Pro reverse engineering. Need to validate compatibility.

### UVC Extension Unit

USB descriptor analysis reveals a **UVC Extension Unit** (`0x24 0x05`) in the device descriptor. This is the standard mechanism for vendor-specific functionality within the UVC framework. The extension unit handles proprietary commands (temperature data, configuration).

### Tested & Confirmed

- ✅ Camera detected natively by Linux kernel (Archlinux, kernel 6.19.11)
- ✅ `/dev/videoX` accessible via V4L2
- ✅ Video stream viewable in `ffplay`, `vlc`, etc.
- ✅ Single format: YUYV 384×292 @ 25fps (no other resolutions or formats available)
- ✅ USB capture shows vendor communication on control endpoint
- ✅ Unconverted YUYV image and parameter-like trailer accessible via `CAP_PROP_CONVERT_RGB=0`; Celsius conversion is not validated
- ❌ P2 Pro formula `T(°C) = uint16 / 64 - 273.15` produced unrealistic values on HT-301 and is not validated for this camera
- ✅ Frame read via OpenCV + V4L2 backend

### Not Yet Tested

- ⬜ Accuracy validation (compare with known temperature source)
- ⬜ Palette/color scheme commands (vendor commands)
- ⬜ Emissivity and distance parameter control
- ⬜ Compatibility of P2 Pro vendor commands with HT-301

### Reference: Similar Projects

- [gopher-p2pro-ir](https://github.com/nicholasgasior/gopher-p2pro-ir) — Go library for InfiRay P2 Pro, documents the vendor command protocol
- [thermviewer.com](https://thermviewer.com/) — Original (abandoned) Android app for HT-301
