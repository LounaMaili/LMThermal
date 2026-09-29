# libthermometry.so — Reverse Engineering

> **Measurement status (2026-09-26):** The x86_64 APK caller now establishes
> the native input mapping and the 16,384-entry raw-index lookup; see
> [NATIVE_CALL_CHAIN.md](NATIVE_CALL_CHAIN.md). A staged Linux replay now yields
> true 14-bit image words,
> and the standalone desktop lookup matches execution of the official x86_64
> native library. Physical temperature accuracy remains unvalidated; see
> [RADIOMETRIC_INITIALIZATION.md](RADIOMETRIC_INITIALIZATION.md). Field 356 is
> a duplicate calibration coefficient, not an established live center temperature.

> **Second APK scope (2026-09-26):** ThermViewer 2.0.23(ot) packages a
> different ARMv7 `libthermometry.so`. Its HT-301 UVC bridge imports the same
> `thermometryT4Line`/`thermometrySearch` names, and its `InitTempParam` ARMv7
> function is byte-identical to the official version. This document's
> detailed arithmetic and offsets describe the **official HTI APK**; they
> have not been validated against ThermViewer's native output. See
> [APPLICATION_COMPARISON.md](APPLICATION_COMPARISON.md).

## Overview

Library: `libthermometry.so` (x86_64, Android/Bionic)
Size: 18,224 bytes (ELF file); see the research inventory for hashes
Dependencies: `pow`, `exp`, `sqrt`, `sqrtf` (libm)

## Exported Functions

| Function | Offset | Purpose |
|----------|--------|---------|
| `GetTempEvn` | 0x850 | Environment temperature calculation |
| `GetFix` | 0x8c0 | Fixed-point correction |
| `InitTempParam` | 0x900 | Initialize temperature parameters |
| `CalcFixRaw` | 0x940 | Environmental and emissivity correction factors |
| `thermometrySearch` | 0x2040 | Map trailer summaries and image words through lookup |
| `thermometryT` | 0xc10 | Alternate exported lookup builder; no APK import found |
| `thermometryT4Line` | 0x1500 | Lookup builder used by the APK |
| `thermFix` | external | Emissivity correction |

## Constants (.rodata section)

### Float64 (double) constants at 0x29a0

| Address | Value | Usage |
|---------|-------|-------|
| 0x29a0 | **4.0** | pow exponent — Stefan-Boltzmann T⁴ |
| 0x29a8 | **0.25** | pow exponent — 4th root |
| 0x29b0 | 1.9 | CalcFixRaw coefficient |
| 0x29b8 | -0.9 | CalcFixRaw coefficient |
| 0x29c0 | 52.125 | Lookup final correction branch |
| 0x29c8 | 100.0 | Lookup final correction branch |
| 0x29d0 | 1.125 | Lookup final correction branch |
| 0x29d8 | 15.875 | `distanceFix` branch |

### Float32 constants

| Address | Value | Usage |
|---------|-------|-------|
| 0x29e4 | 1.8375 | |
| 0x29e8 | **273.15** | Kelvin ↔ Celsius conversion |
| 0x29ec | 7.05 | |
| 0x29f0 | **390.0** | Max temperature limit |
| 0x29f4 | **4.0** | Same as double 0x29a0 |
| 0x29f8 | **0.06939** | CalcFixRaw polynomial c₁ |
| 0x29fc | **1.5587** | CalcFixRaw polynomial c₀ |
| 0x2a00 | 0.00027816 | CalcFixRaw polynomial c₂ |
| 0x2a04 | 6.8455e-7 | CalcFixRaw polynomial c₃ |
| 0x2a08 | -0.00228 | CalcFixRaw exp coefficient |
| 0x2a0c | 0.00657 | CalcFixRaw exp coefficient |
| 0x2a10 | -0.00667 | CalcFixRaw coefficient |
| 0x2a14 | 0.01262 | CalcFixRaw coefficient |
| 0x2a18 | **1.0** | Unity constant |
| 0x2a1c | **36.0** | 384-wide lookup input transform |
| 0x2a20 | **20.0** | Lookup input transform |
| 0x2a24 | **37.682** | 256-wide lookup input transform |
| 0x2a28 | **33.8** | 640-wide lookup input transform |
| 0x2a2c | **10.0** | 640-wide lookup input transform |
| 0x2a30 | **15.875** | Lookup final correction branch |
| 0x2a34 | **100.0** | Lookup final correction branch |
| 0x2a38 | **0.85** | Lookup final correction branch |
| 0x2a3c | **1.125** | Lookup final correction branch |
| 0x2a40 | **60.0** | Distance threshold for lookup correction |
| 0x2a44 | **3.0** | `distanceFix` branch |
| 0x2a48 | **2731.5** | `thermFix` constant; unrelated to Java's `+2731` export step |
| 0x2a50 | **0x80000000** | Sign flip mask (xorps) |

## Decoded Functions

### GetTempEvn(float a, float radiation_term, float inverse_factor) → float

**Disassembly (x86_64):**
```asm
; xmm0 = a, xmm1 = radiation_term, xmm2 = inverse_factor
addss  xmm0, [0x29e8]          ; xmm0 = a + 273.15
movss  [rsp+8], xmm1           ; save radiation_term
movsd  xmm1, [0x29a0]          ; xmm1 = 4.0 (double)
movss  [rsp+c], xmm2           ; save b
cvtss2sd xmm0                  ; → double
call   pow                     ; pow(a + 273.15, 4.0)
cvtsd2ss xmm0                  ; → float
movss  xmm2, [rsp+c]           ; restore b
movsd  xmm1, [0x29a8]          ; xmm1 = 0.25 (double)
subss  xmm0, [rsp+8]           ; xmm0 = pow(a+273.15, 4) - radiation_term
mulss  xmm2, xmm0              ; xmm2 = inverse_factor * prior result
cvtss2sd xmm2 → xmm0           ; → double
call   pow                     ; pow(b * (...), 0.25)
cvtsd2ss xmm0                  ; → float
subss  xmm0, [0x29e8]          ; result - 273.15
ret
```

**Python equivalent:**
```python
import math

def GetTempEvn(a: float, radiation_term: float, inverse_factor: float) -> float:
    """
    Trace decoded arithmetic, without float32-exact rounding.
    
    Parameters:
        a: Lookup-derived calibrated value, not an 8-bit Y pixel
        radiation_term: Fourth output of CalcFixRaw
        inverse_factor: Third output of CalcFixRaw
    
    Returns:
        Native result, not yet validated against a live app reading
    """
    val = math.pow(a + 273.15, 4.0)    # Native fourth-power term
    val = val - radiation_term            # Native subtraction term
    val = inverse_factor * val            # Native multiplier
    result = math.pow(val, 0.25)         # Native fourth-root term
    return result - 273.15               # Native subtraction
```

This Python expression shows the algebra. The native function rounds some
intermediate results to float32, so it is not a bit-exact numerical clone.

**Physical interpretation inferred from the Java parameter labels:**
The fourth power and fourth root resemble Stefan-Boltzmann correction. The
arithmetic:
1. Adds 273.15 to its first input
2. Raises to the fourth power
3. Subtracts the second input
4. Multiplies by the third input
5. Takes the fourth root
6. Subtracts 273.15

### InitTempParam(float x, float y, float *out_a, float *out_b)

**Disassembly:**
```asm
; xmm0 = x, xmm1 = y, rdi = out_a, rsi = out_b
movss  xmm2, xmm0        ; xmm2 = x
movss  xmm3, xmm1        ; xmm3 = y
addss  xmm2, xmm0        ; xmm2 = 2x
mulss  xmm1, xmm1        ; xmm1 = y²
divss  xmm3, xmm2        ; xmm3 = y / 2x
movss  xmm2, [0x29f4]    ; xmm2 = 4.0
mulss  xmm2, xmm0        ; xmm2 = 4x
movss  [rdi], xmm3       ; *out_a = y / 2x
mulss  xmm0, xmm2        ; xmm0 = x * 4x = 4x²
divss  xmm1, xmm0        ; xmm1 = y² / (4x²)
movss  [rsi], xmm1       ; *out_b = y² / (4x²)
ret
```

**Python equivalent:**
```python
def InitTempParam(x: float, y: float) -> tuple[float, float]:
    """
    Reproduce decoded parameter arithmetic from trailer coefficients.
    
    Parameters:
        x: Float coefficient at frame byte 223494
        y: Float coefficient at frame byte 223498
    
    Returns:
        (a, b) where:
        a = y / (2 * x)
        b = y² / (4 * x²)  = (y / 2x)² = a²
    """
    a = y / (2.0 * x)
    b = (y * y) / (4.0 * x * x)
    return a, b
```

### CalcFixRaw(t, c, d, e, f, *out1, *out2, *out3, *out4)

The x86_64 register trace is complete for the normal finite-number path.
`thermometryT4Line` loads `t=ambient temperature` (block offset 8),
`c=humidity` (12), `d=effective distance` (default host lens 68 multiplies the
uint16 at 20 by three), `e=emissivity` (16), and
`f=reflected temperature` (4). These names come from the Java setting
decoder. The native code uses float32 intermediates, double `exp`/`pow`, and
fallback `sqrtf` calls for exceptional inputs; the pseudocode is algebraic,
not bit-exact.

```python
P = 1.5587 + 0.06939*t - 0.00027816*t*t + 6.8455e-7*t*t*t
out1 = c * exp(P)                                # 0x9c3 -> [rdi] at 0x9ec
root_d = sqrt(d)
root_1 = sqrt(out1)
q1 = exp(-root_d * (-0.002276*root_1 + 0.006569))
q2 = exp(-root_d * (-0.006670*root_1 + 0.012620))
out2 = 1.9*q1 - 0.9*q2                         # [rsi] at 0xabf
out3 = 1.0 / (out2 * e)                        # [rdx] at 0xacd
out4 = ((1.0 - out2) * (t + 273.15)**4
        + (1.0 - e) * out2 * (f + 273.15)**4) # [rcx] at 0xb46
```

The old note incorrectly gave `q2` a positive factor of 100.0; `0x29b8`
is approximately **−0.9**. The value 100.0 at `0x29c8` is not used by this
function's normal path. `out2` is consistent with a transmission factor,
but that physical label still needs validation against native output.
The lookup builder consumes `out3` and `out4` as the third and second
arguments to `GetTempEvn`, respectively. `out1` and `out2` are retained as
intermediates in the caller's stack and are not fed directly into each pixel.

## Frame Parameter Structure

The last 514 bytes of each YUYV frame (384×292, 224,256 bytes total) contain:

```
Offset in params | Value example | Description
-----------------|---------------|-------------
[0]              | 0.0           | Correction setting
[4]              | 25.0          | Reflected temperature
[8]              | 25.0          | Ambient temperature
[12]             | 0.45          | Humidity
[16]             | 0.98          | Emissivity
[20]             | 1             | Distance, uint16
[24-351]         | mixed         | Undecoded; includes device identifier bytes in captured frames
[352]            | ~0.27         | Copy of calibration coefficient at byte 223494
[356]            | ~36.0         | Copy of calibration coefficient at byte 223498
[360]            | ~0.00004     | Copy of calibration coefficient at byte 223502
[364]            | ~0.006        | Copy of calibration coefficient at byte 223506
[368]            | ~0.82         | Copy of calibration coefficient at byte 223510
[376]            | 25.0          | Reflected temperature (repeat)
[380]            | 25.0          | Ambient temperature (repeat)
[384]            | 0.45          | Humidity (repeat)
[388]            | 0.98          | Emissivity (repeat)
```

**Frame offset calculation** (from `getByteArrayTemperaturePara` disassembly):
```
For width=384 (0x180), height in field+0x34, width in field+0x38:
  offset = (height*3 - 3) * 256 + 254
  For height=292: offset = (876-3)*256 + 254 = 223,742
```

## Data Flow

```
USB Camera (UVC/YUYV 384×292@25fps)
  │
  ├─ bytes 0-221,183: display YUYV or raw14 image words after 32772 (288 rows)
  ├─ bytes 221,184-223,741: non-image trailer
  └─ bytes 223,742-224,255: documented parameter block (514 bytes)
      ├─ Correction, reflected and ambient temperatures, humidity, emissivity, distance
      ├─ Copies of calibration coefficients from the earlier trailer
      └─ Field 356: duplicated calibration coefficient, not live center
```

## Practical Usage

For current diagnostics, decode field 356 at frame offset 224098 as a copied
calibration coefficient. The empirical `0.2143 × Y − 3.14` mapping and
`GetTempEvn(Y, env_temp, gain × emissivity)` mapping are invalidated. The
native call chain is documented, and the staged raw fixtures now fit its lookup
range. `experimental_thermometry.py` in the desktop repository reproduces the
16384-entry range-120/lens-68/shutter-fix-1.5 branch with explicit float32
rounding. Its native reference harness binds the inspected official x86_64
ELF to host libm without Android initialization. Saved reference tables let
ordinary tests compare every entry without bundling the APK.

The corrected lookup inputs are byte **221186** for the FPA transform and
byte **223490** for `word/10-273.15`; these are separate quantities. See the
corrected equation in [NATIVE_CALL_CHAIN.md](NATIVE_CALL_CHAIN.md). LUT entries
outside the function's real domain remain NaN, just as native code produces;
selected image/summary indices must map to finite entries. Matching APK
arithmetic does not establish calibrated temperature accuracy, Android ARM
bit identity, or support for untested ranges/lenses.
