# LMThermal — Application Specification

> Staged Linux capture now produces native 14-bit indices and the standalone
> lookup matches execution of official x86_64 APK arithmetic. GUI temperature
> measurement remains gated on independent same-scene reference validation,
> supported settings and shutter/stability handling. See
> [RADIOMETRIC_INITIALIZATION.md](RADIOMETRIC_INITIALIZATION.md). The features
> below remain planned; experimental offline output is not a calibrated API.

## Current Linux measurement foundation

The sibling desktop repository now has a PyQt-independent
`HT301RadiometricSession` for the **observed** 384-wide, range-120, lens-68,
shutter-fix-1.5 path. From display mode with zoom readback 0 it sends only the confirmed standard
UVC zoom values `32772 -> 32800 -> 32768`, verifies raw14 after each stage,
then requires a minimum 75 valid post-shutter frames plus consecutive distinct
live frames with complete calibration, consistent trailer extrema and defined
lookup outputs. A fixed wait alone cannot establish readiness because held
images were observed after the 75-frame interval. Invalid or repeated frames
demote readiness. An already-raw14 stream of unknown host state stays
read-only rather than being assigned a presumed normal range.

The measurement object keeps the original full raw14 words, a native-equivalent
384 × 288 temperature matrix, settings/calibration inputs, summary extrema,
timestamps, and both trailer center and literal pixel `(192,144)` separately.
No high-bit masking, display-Y substitution or empirical correction is used.
The numerical lookup matches inspected official x86_64 arithmetic for this
branch, but its absolute physical accuracy is still unvalidated.

A lightweight OpenCV **diagnostic operator preview** shows display Y before
transition and contrast-normalized raw14 after it. It overlays session mode,
readiness, transient/invalid status, center crosshair, valid high/low markers
and optional ROI guides. Click inspection can show original raw index and
native-equivalent output only on a valid ready frame. Normalization is
display-only and cannot feed thermometry. This preview is not the planned
application UI, Celsius palette lock, or an accuracy validation.

Independent multi-target surface measurements remain future work. Their
absence does not prevent development of the structural measurement pipeline;
it does prevent claiming calibrated physical temperature accuracy.

## Target Platform

**Primary**: Android (USB Host API, connected via USB-C OTG)
**Development/Prototyping**: Linux desktop (Python, for protocol validation)

## Core Features

### 1. Live Thermal View
- Real-time thermal camera feed
- Selectable color palettes (ironbow, rainbow, grayscale, etc.)
- Adjustable emissivity and distance parameters

### 2. Temperature Measurement
- **Spot measurement**: tap/point to read temperature at any pixel
- **Area measurement**: draw rectangles, circles, or polygons to get min/max/average
- **Multiple points**: place several measurement markers simultaneously

### 3. Temperature Range Lock
- **Fixed min/max scale** — lock the color palette to a specific temperature range
- Ensures consistent, comparable colors across different captures
- This was the key missing feature from the manufacturer's app

### 4. Photo Capture
- Save still images with temperature overlay
- Export formats:
  - **RJPEG** (Radiometric JPEG) — standard format, readable by FLIR Tools, Thermimage, etc.
  - **TIFF radiometric** — for professional thermal analysis software
  - **PNG** — visual only (no embedded temperature data)
  - **CSV** — raw temperature matrix for custom analysis

### 5. Video Recording
- Record thermal video sequences
- Embed temperature data (each frame carries radiometric data)
- Export formats:
  - **Radiometric video** — TBD format (conteneur + raw frames)
  - **Standard MP4** — visual only
  - **TIFF sequence + JSON metadata** — for analysis

### 6. Capture Gallery & Comparison
- Browse saved captures (photos + videos)
- Side-by-side comparison of two captures
- Temperature difference overlay (ΔT between two images)
- Annotations (text, arrows, areas of interest)

### 7. Settings & Calibration
- Emissivity preset materials (concrete, metal, skin, etc.)
- Reflected apparent temperature
- Distance to target
- Unit selection (°C / °F)
- Auto or manual temperature range

## Export Formats

### RJPEG (Radiometric JPEG)
A standard JPEG file with embedded radiometric (temperature) data in EXIF/metadata chunks. This is the closest thing to a universal thermal image format:
- Readable by: FLIR Tools, Thermimage (R), PyThermimage (Python), various thermal analysis software
- Contains: visual image + full temperature matrix + measurement parameters

### TIFF Radiometric
Standard TIFF with floating-point pixel values representing temperatures.
- Widely supported in scientific software (ImageJ, MATLAB, etc.)

### CSV
Simple spreadsheet-compatible format: one row per pixel line, temperature values in °C.

## Non-Goals (for now)
- iOS support (no USB Host)
- Cloud storage / sync
- Multi-camera support
- Real-time streaming over network
