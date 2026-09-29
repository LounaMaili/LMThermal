# LMThermal — Application Specification

> Staged Linux capture now produces native 14-bit indices and the standalone
> lookup matches execution of official x86_64 APK arithmetic. GUI temperature
> measurement remains gated on independent same-scene reference validation,
> supported settings and shutter/stability handling. See
> [RADIOMETRIC_INITIALIZATION.md](RADIOMETRIC_INITIALIZATION.md). The features
> below remain planned; experimental offline output is not a calibrated API.

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
