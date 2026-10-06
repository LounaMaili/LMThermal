# Recording compression study — 2026-10-06

**Offline analysis evidence for [common recording review draft](COMMON_RECORDING_DESIGN.md),
not a recorder implementation or an Android storage/performance validation.**

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Inputs and provenance

Existing live HT-301 raw acquisitions were read without opening the camera:

- **Room**: `LMThermal-Research/analysis/warm-target-20260927/settled-room/`,
  `report.json`, `captures/A_baseline/frame-000.raw` through `frame-074.raw`.
  75 complete raw14 transport frames, **45 distinct image planes**. Frames 25–55
  are a 31-image identical run; 30 are repeats. This is room/ceiling evidence,
  not a hand-target test and not 75 distinct ready measurements.
- **Hand**: `LMThermal-Research/analysis/warm-target-20260929/hand-run-01/`,
  `validation-live-23.json`, `captures/E_post_shutter_stability/frame-035.raw`
  through `frame-057.raw`. The report records operator-confirmed hand presence,
  no independent temperature reference, 23 distinct images and a stable selected
  raw14 window. The surrounding run later lost raw14 compatibility; only this
  selected window is used.
- `room-with-held` retains all 75 received frames as a **compression sensitivity
  case**, not as a recommendation to record held frames as valid temperatures.
  `room-changing` removes consecutive duplicate images, preserving original order:
  frames 0–25 and 56–74, 45 total. This is a noncontiguous subset with an omitted
  held interval; grouping it by count does **not** establish a continuous 25 FPS
  timeline. `hand` contains all 23 selected consecutive distinct images.

No source capture/image/temperature plane was added to Git. Private paths and
identities are absent from the aggregate report. Source files were SHA-256 checked
before/after; all input files stayed unchanged. Spatial data were not cropped,
rotated, masked to raw14, smoothed, quantized or otherwise made easier to compress.
Historical long 5/10/25 Hz recorder bundles were lost; surviving fixture-derived
playback demos are not real temporal compression evidence and were not benchmarked.

## Method and reproducibility

[Analysis-only helper](../tools/benchmark_recording_compression.py) and
[complete aggregate JSON](analysis/recording-compression-20261006.json) contain
180 dataset/role/group/codec cases. Each role is prepared independently:

- Celsius: 442368 bytes/frame, f32le `[288,384]`, generated **offline** using the
  existing Desktop `native_equivalent_thermometry.temperature_matrix` at audit
  commit `b0afdf3b42b6f55c2ae3d926f0d8c9df09255746`, including each frame's
  calibration and the supported startup host state. These are reference-produced
  planes for codec input, not original Android-exported Float32 bytes or a new
  validation of thermometry/physical accuracy. Preparation cost is excluded.
- Native: exact first 221184 image bytes, u16le; every complete word <0x4000.
- Acquisition: all 224256 original bytes, including trailer.

Groups contain up to 1/8/25/50 consecutive **selected** full frames, concatenated
role-major. The last group may be short. For the 23-frame hand selection, 25 and
50 both mean one 23-frame block; no actual two-second hand experiment is implied.
No cross-role compression, dictionaries, shuffle/delta filters or lossy conversion.
DEFLATE uses Python zlib's RFC1950 wrapper around RFC1951; Zstd uses independent
reference frames. STORED measures a real memory copy.

Five complete-pass timings after one warmup; median wall and process CPU times.
Throughput is uncompressed input/output MB divided by wall time, **decimal MB/s**.
Ratio = original bytes / compressed bytes, reduction = 1 − compressed/original.
Every block was decoded and checked byte-for-byte against its original. Compression,
allocation and Python/C buffer copies are included; I/O, SAF, thermometry, hashes,
JSON/indexes, masks and preview are excluded. These are **codec write-side/read-side
throughputs**, not measured disk/provider write/read speeds.

Environment: x86_64 AMD Ryzen 9 5950X, single-thread calls; Python 3.14.7, NumPy
2.5.3, zlib runtime `1.3.1.zlib-ng`, reference Zstandard 1.5.7. One desktop run is
not a sustained phone, power, thermal-throttling or Windows-library benchmark.
Zstd ctypes buffer copies differ from zlib's binding, so compare the reported
end-to-end helper costs, not theoretical isolated C codec speed.

Example (substitute local paths; use Desktop's existing virtual environment):

```bash
PYTHONDONTWRITEBYTECODE=1 ../LMThermal-Desktop/.venv/bin/python \
  tools/benchmark_recording_compression.py \
  --desktop ../LMThermal-Desktop \
  --dataset room-with-held=/path/to/room/captures/A_baseline \
  --dataset room-changing=/path/to/room/captures/A_baseline \
  --dataset hand=/path/to/hand/captures/E_post_shutter_stability \
  --selection room-with-held=0:75 --selection room-changing=0:75 \
  --selection hand=35:58 --omit-consecutive-repeats room-changing \
  --cpu-description 'describe CPU; single-thread codec calls' \
  --output /path/outside/source/aggregate.json
```

This helper needs NumPy and an installed reference `libzstd` only for analysis;
no new Android/application dependency is introduced. Smaller sample/chunk selections
can be supplied for a smoke check. Local original inputs are not distributed by this
branch, so the provenance/count/selection and all numerical aggregate results are
tracked while those private input files remain a prerequisite for full reproduction.

## Per-role results, up to 25 selected frames/block

CPU below is process milliseconds per selected frame, derived from median full-pass
CPU divided by frame count. Full-pass CPU/wall times and exact compressed byte counts
remain in JSON. STORED is a memory-bandwidth baseline, not a plausible storage rate.

### room-changing (45 frames)

| Role | Codec | Ratio | Reduction % | Compress MB/s | Decode MB/s | Compress CPU ms/frame | Decode CPU ms/frame |
|---|---|---:|---:|---:|---:|---:|---:|
| Celsius | stored | 1.000 | 0.00 | 33871.1 | 30850.2 | 0.013 | 0.014 |
| Celsius | deflate-1 | 4.449 | 77.52 | 247.0 | 1070.7 | 1.782 | 0.410 |
| Celsius | deflate-6 | 5.360 | 81.34 | 49.9 | 1777.1 | 8.833 | 0.248 |
| Celsius | zstd-1 | 4.469 | 77.62 | 406.3 | 977.9 | 1.083 | 0.450 |
| Celsius | zstd-3 | 4.846 | 79.36 | 367.9 | 1065.5 | 1.196 | 0.413 |
| Native u16 | stored | 1.000 | 0.00 | 49531.6 | 46716.3 | 0.004 | 0.005 |
| Native u16 | deflate-1 | 2.859 | 65.02 | 186.8 | 892.3 | 1.178 | 0.247 |
| Native u16 | deflate-6 | 3.058 | 67.30 | 36.1 | 951.6 | 6.094 | 0.231 |
| Native u16 | zstd-1 | 2.762 | 63.79 | 337.4 | 1139.6 | 0.652 | 0.193 |
| Native u16 | zstd-3 | 3.163 | 68.38 | 248.1 | 1096.3 | 0.887 | 0.201 |
| Acquisition | stored | 1.000 | 0.00 | 47196.8 | 45615.9 | 0.005 | 0.005 |
| Acquisition | deflate-1 | 2.885 | 65.34 | 189.3 | 891.8 | 1.178 | 0.250 |
| Acquisition | deflate-6 | 3.088 | 67.62 | 36.8 | 963.2 | 6.072 | 0.232 |
| Acquisition | zstd-1 | 2.790 | 64.15 | 352.7 | 1171.1 | 0.633 | 0.190 |
| Acquisition | zstd-3 | 3.202 | 68.77 | 247.6 | 1072.3 | 0.902 | 0.208 |

### hand (23 frames)

| Role | Codec | Ratio | Reduction % | Compress MB/s | Decode MB/s | Compress CPU ms/frame | Decode CPU ms/frame |
|---|---|---:|---:|---:|---:|---:|---:|
| Celsius | stored | 1.000 | 0.00 | 32839.1 | 28358.7 | 0.014 | 0.016 |
| Celsius | deflate-1 | 3.574 | 72.02 | 210.9 | 969.5 | 2.087 | 0.454 |
| Celsius | deflate-6 | 4.268 | 76.57 | 50.7 | 1486.4 | 8.691 | 0.296 |
| Celsius | zstd-1 | 3.465 | 71.14 | 383.1 | 1040.7 | 1.148 | 0.423 |
| Celsius | zstd-3 | 3.911 | 74.43 | 316.5 | 1129.4 | 1.391 | 0.388 |
| Native u16 | stored | 1.000 | 0.00 | 48473.4 | 48427.2 | 0.005 | 0.005 |
| Native u16 | deflate-1 | 2.341 | 57.28 | 165.5 | 762.0 | 1.331 | 0.289 |
| Native u16 | deflate-6 | 2.488 | 59.80 | 42.5 | 803.7 | 5.172 | 0.274 |
| Native u16 | zstd-1 | 2.244 | 55.44 | 345.5 | 1136.5 | 0.637 | 0.193 |
| Native u16 | zstd-3 | 2.636 | 62.06 | 202.7 | 1007.9 | 1.085 | 0.219 |
| Acquisition | stored | 1.000 | 0.00 | 39194.0 | 39557.7 | 0.006 | 0.006 |
| Acquisition | deflate-1 | 2.365 | 57.72 | 166.9 | 742.1 | 1.337 | 0.300 |
| Acquisition | deflate-6 | 2.513 | 60.20 | 43.2 | 816.7 | 5.172 | 0.274 |
| Acquisition | zstd-1 | 2.268 | 55.92 | 351.2 | 1162.4 | 0.636 | 0.192 |
| Acquisition | zstd-3 | 2.672 | 62.57 | 205.9 | 1010.0 | 1.077 | 0.217 |

## Independent frames versus groups

The table is **compressed bytes saved compared with the same codec at one
frame/block**, not total reduction from raw input. Negative values mean larger.
Across the two non-repeating inputs, DEFLATE-1 grouping changes size by less than
0.3%; its 32 KiB window cannot generally reference a whole earlier large plane.
Zstd-3 gains are real but modest once the held run is removed; native improvements
are not monotonic with chunk size. Longer chunks are not automatically better.

| Dataset | Role | Codec | 8 frames | 25 frames | 50 frames |
|---|---|---|---:|---:|---:|
| room-changing | Celsius | deflate-1 | 0.10% | 0.11% | 0.11% |
| room-changing | Celsius | zstd-3 | 1.69% | 1.95% | 2.02% |
| room-changing | Native u16 | deflate-1 | -0.00% | -0.00% | 0.00% |
| room-changing | Native u16 | zstd-3 | 6.55% | 5.23% | 6.98% |
| room-changing | Acquisition | deflate-1 | 0.02% | 0.03% | 0.03% |
| room-changing | Acquisition | zstd-3 | 6.79% | 5.52% | 7.27% |
| hand | Celsius | deflate-1 | 0.09% | 0.12% | 0.12% |
| hand | Celsius | zstd-3 | 2.96% | 3.78% | 3.78% |
| hand | Native u16 | deflate-1 | 0.26% | 0.27% | 0.27% |
| hand | Native u16 | zstd-3 | 5.80% | 6.45% | 6.45% |
| hand | Acquisition | deflate-1 | 0.20% | 0.21% | 0.21% |
| hand | Acquisition | zstd-3 | 5.98% | 6.68% | 6.68% |
| room-with-held | Celsius | deflate-1 | 0.10% | 0.11% | 0.11% |
| room-with-held | Celsius | zstd-3 | 24.27% | 25.23% | 25.86% |
| room-with-held | Native u16 | deflate-1 | 0.00% | 0.00% | 0.00% |
| room-with-held | Native u16 | zstd-3 | 40.02% | 41.85% | 42.93% |
| room-with-held | Acquisition | deflate-1 | 0.02% | 0.03% | 0.03% |
| room-with-held | Acquisition | zstd-3 | 40.17% | 42.03% | 43.11% |

The held room input substantially amplifies Zstd's apparent temporal gain. It
must not be generalized to changing valid scenes. The recommended one-second/
byte-capped chunks balance commits, memory, seeking and corruption radius; this
study does not justify several-second chunks for compression alone. Masks and
variable JSON may reduce the number of frames fitting a real chunk.

## Measured compressed examples and nominal-rate scaling

For the two non-repeating inputs, sum independently compressed physical roles at
up to 25 selected frames/block. Full is Celsius + acquisition, with native exposed
as an exact view and **not** compressed/stored a second time. These are measured
sample sizes scaled to hypothetical continuous 25 FPS of the **same size distribution**,
not observed minute/hour captures, guarantees or camera/Android recording rates.
Metadata, masks and storage framing add overhead.

| Input | Codec | Profile | Compressed bytes/selected frame | MB/s at 25 FPS | MB/1 min | GB/10 min | GB/1 h |
|---|---|---|---:|---:|---:|---:|---:|
| room-changing | deflate-1 | Analysis | 99426.3 | 2.4857 | 149.139 | 1.4914 | 8.9484 |
| room-changing | deflate-1 | Native | 176801.9 | 4.4200 | 265.203 | 2.6520 | 15.9122 |
| room-changing | deflate-1 | Full (native view) | 177152.0 | 4.4288 | 265.728 | 2.6573 | 15.9437 |
| room-changing | zstd-3 | Analysis | 91283.9 | 2.2821 | 136.926 | 1.3693 | 8.2156 |
| room-changing | zstd-3 | Native | 161211.3 | 4.0303 | 241.817 | 2.4182 | 14.5090 |
| room-changing | zstd-3 | Full (native view) | 161317.8 | 4.0329 | 241.977 | 2.4198 | 14.5186 |
| hand | deflate-1 | Analysis | 123784.5 | 3.0946 | 185.677 | 1.8568 | 11.1406 |
| hand | deflate-1 | Native | 218274.2 | 5.4569 | 327.411 | 3.2741 | 19.6447 |
| hand | deflate-1 | Full (native view) | 218603.3 | 5.4651 | 327.905 | 3.2790 | 19.6743 |
| hand | zstd-3 | Analysis | 113113.7 | 2.8278 | 169.670 | 1.6967 | 10.1802 |
| hand | zstd-3 | Native | 197024.0 | 4.9256 | 295.536 | 2.9554 | 17.7322 |
| hand | zstd-3 | Full (native view) | 197054.8 | 4.9264 | 295.582 | 2.9558 | 17.7349 |

## Recommendation and unresolved performance gate

- DEFLATE-1 + STORED fallback is a reasonable first portable baseline. DEFLATE-6
  saves some bytes but costs several times more CPU in this helper.
- Optional Zstd-3 is technically reasonable through the reference NDK C library,
  with pinned dependency/license/window limits and a separately required feature.
  On these changing inputs it saves additional bytes over DEFLATE-1 and has lower
  helper CPU cost, but the room's repeated-frame gain must not drive a universal
  estimate. Reference [Zstandard](https://github.com/facebook/zstd) is a lossless
  portable codec, not an Android platform API dependency already in the app.
- Do not freeze Zstd as a mandatory/default phone codec before Android sustained
  throughput/peak-memory/battery/decoder-window and Linux/Windows parity validation.
  No benchmarks here certify 25 FPS acquisition + thermometry + compression on
  the Pixel or another phone.
- HT transport's extra trailer compresses well; exact native-view dedup is preferable
  to compressing duplicate native/transport roles independently. Another module
  needs its own evidence layout and measurements.
- Current data are short, mostly stationary scenes. Follow-up must cover motion,
  wider temperature ranges, dynamic calibration, partial masks, higher-resolution
  modules, low space and realistic long provider writes. Primary Celsius/native/
  validity/context remain lossless in every case.

## Validation performed

180 cases × one warmup/byte-exact roundtrip × five timed compression/decompression
passes. 75 source room files + 23 source hand files remained unchanged; all selected
native words were raw14 and reference temperature planes finite. The room sensitivity
subset intentionally reuses those source files, not 45 newly captured observations.
No camera controls, new capture, codec-driven source rewrite or physical calibration
claim was made. Full machine results retain all STORED/DEFLATE/Zstd/group variants.
