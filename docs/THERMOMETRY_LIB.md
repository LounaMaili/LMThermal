# libthermometry.so — Reverse Engineering

> **Measurement status (2026-09-26):** The disassembled arithmetic below is
> useful, but the native caller argument mapping is not established. In
> particular, there is no evidence yet that `GetTempEvn` receives an 8-bit Y
> pixel, Celsius environment temperature, or `gain × emissivity`. That desktop
> hypothesis produced −55.10 °C where frame field 356 was 35.992. Field 356
> itself stayed constant across changing live images and is not a validated
> center thermometer. Only transport rows 0–287 are thermal image; rows
> 288–291 are non-image trailer. Do not use this document as an implemented
> per-pixel Celsius conversion.

## Overview

Library: `libthermometry.so` (x86_64, Android/Bionic)
Size: ~2.6KB code section
Dependencies: `pow`, `exp`, `sqrt`, `sqrtf` (libm)

## Exported Functions

| Function | Offset | Purpose |
|----------|--------|---------|
| `GetTempEvn` | 0x850 | Environment temperature calculation |
| `GetFix` | 0x8c0 | Fixed-point correction |
| `InitTempParam` | 0x900 | Initialize temperature parameters |
| `CalcFixRaw` | 0x940 | Full raw→temperature calibration |
| `thermometrySearch` | external | Frame-level temp extraction |
| `thermometryT` | external | Per-pixel temperature calculation |
| `thermometryT4Line` | external | Line-based temp calculation |
| `thermFix` | external | Emissivity correction |

## Constants (.rodata section)

### Float64 (double) constants at 0x29a0

| Address | Value | Usage |
|---------|-------|-------|
| 0x29a0 | **4.0** | pow exponent — Stefan-Boltzmann T⁴ |
| 0x29a8 | **0.25** | pow exponent — 4th root |
| 0x29b0 | 1.9 | CalcFixRaw coefficient |
| 0x29b8 | -0.9 | CalcFixRaw coefficient |
| 0x29c0 | 52.125 | CalcFixRaw coefficient |
| 0x29c8 | 100.0 | CalcFixRaw coefficient |
| 0x29d0 | 1.125 | CalcFixRaw coefficient |
| 0x29d8 | 15.875 | CalcFixRaw coefficient |

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
| 0x2a00 | 0.000278 | CalcFixRaw polynomial c₂ |
| 0x2a04 | 6.86e-7 | CalcFixRaw polynomial c₃ |
| 0x2a08 | -0.00228 | CalcFixRaw exp coefficient |
| 0x2a0c | 0.00657 | CalcFixRaw exp coefficient |
| 0x2a10 | -0.00667 | CalcFixRaw coefficient |
| 0x2a14 | 0.01262 | CalcFixRaw coefficient |
| 0x2a18 | **1.0** | Unity constant |
| 0x2a1c | **36.0** | CalcFixRaw parameter |
| 0x2a20 | **20.0** | CalcFixRaw parameter |
| 0x2a24 | **37.682** | CalcFixRaw parameter |
| 0x2a28 | **33.8** | CalcFixRaw parameter |
| 0x2a2c | **10.0** | CalcFixRaw parameter |
| 0x2a30 | **15.875** | CalcFixRaw parameter |
| 0x2a34 | **100.0** | CalcFixRaw parameter |
| 0x2a38 | **0.85** | CalcFixRaw parameter |
| 0x2a3c | **1.125** | CalcFixRaw parameter |
| 0x2a40 | **60.0** | CalcFixRaw parameter |
| 0x2a44 | **3.0** | CalcFixRaw parameter |
| 0x2a48 | **2731.5** | Java short conversion factor |
| 0x2a50 | **0x80000000** | Sign flip mask (xorps) |

## Decoded Functions

### GetTempEvn(float a, float env_temp, float b) → float

**Disassembly (x86_64):**
```asm
; xmm0 = a, xmm1 = env_temp, xmm2 = b
addss  xmm0, [0x29e8]          ; xmm0 = a + 273.15
movss  [rsp+8], xmm1           ; save env_temp
movsd  xmm1, [0x29a0]          ; xmm1 = 4.0 (double)
movss  [rsp+c], xmm2           ; save b
cvtss2sd xmm0                  ; → double
call   pow                     ; pow(a + 273.15, 4.0)
cvtsd2ss xmm0                  ; → float
movss  xmm2, [rsp+c]           ; restore b
movsd  xmm1, [0x29a8]          ; xmm1 = 0.25 (double)
subss  xmm0, [rsp+8]           ; xmm0 = pow(a+273.15, 4) - env_temp
mulss  xmm2, xmm0              ; xmm2 = b * (pow(a+273.15, 4) - env_temp)
cvtss2sd xmm2 → xmm0           ; → double
call   pow                     ; pow(b * (...), 0.25)
cvtsd2ss xmm0                  ; → float
subss  xmm0, [0x29e8]          ; result - 273.15
ret
```

**Python equivalent:**
```python
import math

def GetTempEvn(a: float, env_temp: float, b: float) -> float:
    """
    Trace the decoded native arithmetic; input and output semantics unverified.
    
    Parameters:
        a: Native input of currently unknown origin and units
        env_temp: Native subtraction term of currently unknown units
        b: Native multiplier of currently unknown composition
    
    Returns:
        Native result, not yet validated as a Celsius measurement for Y pixels
    """
    val = math.pow(a + 273.15, 4.0)    # Native fourth-power term
    val = val - env_temp                  # Native subtraction term; units unknown
    val = b * val                         # Native multiplier; composition unknown
    result = math.pow(val, 0.25)         # Native fourth-root term
    return result - 273.15               # Native subtraction
```

This Python expression shows the algebra. The native function rounds some
intermediate results to float32, so it is not a bit-exact numerical clone.

**Possible physical interpretation (not yet verified):**
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
    Reproduce decoded parameter arithmetic; caller inputs remain unknown.
    
    Parameters:
        x: Native input of currently unknown role
        y: Native input of currently unknown role
    
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

This is the most complex function — full multi-parameter calibration.

**Arguments:** Register locations are partially identified, but the source,
units, and semantic role of `t`, `c`, `d`, `e`, `f` and the four outputs remain
unverified. Earlier guesses that they correspond to Y, emissivity, distance,
shutter correction, and calibrated Celsius must not be treated as facts.

**Step 1: Cubic polynomial → exponential**
```python
# Polynomial coefficients from .rodata
P = 1.5587 + 0.06939 * t - 0.000278 * t**2 + 6.86e-7 * t**3
step1 = c * math.exp(P)
```

**Step 2: Complex correction chain with sqrt, exp**
```python
# Multiple sqrt/exp passes with sign-flipped corrections
# The role and ordering of these corrections remain unverified.
sqrt_out1 = math.sqrt(step1)
sqrt_d = math.sqrt(d)

# Correction pass 1 (coefficients 0x2a08, 0x2a0c)
corr1 = math.exp((-sqrt_d) * (sqrt_out1 * (-0.00228) + 0.00657))

# Correction pass 2 (coefficients 0x2a10, 0x2a14)
corr2 = math.exp((-sqrt_d) * (sqrt_out1 * (-0.00667) + 0.01262))

# Combined with double constants
step2 = corr2 * 100.0 + corr1 * 1.9   # approximate, uses 0x29b8 and 0x29c8

# Additional terms and their argument mapping require a full register trace.
```

**Note:** The full CalcFixRaw involves ~6 exp/sqrt/pow calls with interleaved corrections. The complete Python implementation requires careful tracing of every register. The key insight is:

1. A candidate input enters a cubic polynomial and `exp()`.
2. Later `sqrt()`, `exp()`, `pow(x,4)`, and `pow(x,0.25)` operations are partially traced.
3. Their complete ordering, caller inputs, outputs, and physical interpretation remain unknown.

## Frame Parameter Structure

The last 514 bytes of each YUYV frame (384×292, 224,256 bytes total) contain:

```
Offset in params | Value example | Description
-----------------|---------------|-------------
[0]              | 0.0           | Mode/flags
[4]              | 25.0          | Environment temperature (°C)
[8]              | 25.0          | Environment temperature 2 (°C)
[12]             | 0.45          | Emissivity
[16]             | 0.98          | Distance factor
[20]             | 1             | Active flag
[24-351]         | mixed         | Undecoded; includes device identifier bytes in captured frames
[352]            | ~0.27         | Candidate gain field; role unverified
[356]            | ~36.0         | Candidate center-temperature field; live interpretation unverified
[360]            | ~0.00004     | Undecoded field
[364]            | ~0.006        | Candidate offset factor; role unverified
[368]            | ~0.82         | Candidate calibration factor; role unverified
[376]            | 25.0          | Env temp (repeat)
[380]            | 25.0          | Env temp (repeat)
[384]            | 0.45          | Emissivity (repeat)
[388]            | 0.98          | Distance factor (repeat)
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
  ├─ bytes 0-221,183: YUYV thermal image data (288 rows)
  ├─ bytes 221,184-223,741: non-image trailer
  └─ bytes 223,742-224,255: documented parameter block (514 bytes)
      ├─ Env temperature, emissivity, distance factor
      ├─ Candidate gain and calibration fields
      └─ Field 356: candidate center temperature, not validated live
```

## Practical Usage

For current diagnostics, decode field 356 at frame offset 224098 while
labelling it an unverified candidate. The empirical `0.2143 × Y − 3.14`
mapping and the `GetTempEvn(Y, env_temp, gain × emissivity)` mapping are not
validated and must not be presented as temperatures. Full per-pixel
thermometry requires the native call chain and a controlled calibration check.
