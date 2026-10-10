# R2 Android codec evidence and recommendation

The original codec/performance evidence below is retained. See the [measured continuation](CONTINUATION_RESULTS.md) for the optimized framing/preparation path; original writer overload is not evidence that a codec itself is intrinsically too slow. Zstd product adoption remains deferred.

**Lossless byte benchmarks; no 25 FPS product configuration is approved.**

Reference source is the same 25 owned live Full measurements for every codec in a study.
One warmup/five passes for each role and group size, exact roundtrip every pass.
The first study ran before the sustained matrix and confounded native allocator-cache memory;
the repeated study runs after all profiles. Both raw reports are retained.

The full-physical standalone block is frame-major Celsius+transport, while the writer
uses independent role-major blocks. Its rate/ratio is not a substitute for writer throughput.
MB/s uses decimal MB. Ratio is stored/input (smaller is better).

## First Android study

| Frames | Role | Codec | Stored/input | Encode MB/s | Decode MB/s |
|---:|---|---|---:|---:|---:|
| 1 | temperature | stored | 1.000 | 2232.9 | 2893.9 |
| 1 | temperature | deflate-1 | 0.197 | 125.4 | 362.7 |
| 1 | temperature | zstd-3 | 0.182 | 244.0 | 516.0 |
| 1 | native | stored | 1.000 | 1845.2 | 2313.4 |
| 1 | native | deflate-1 | 0.305 | 93.4 | 300.7 |
| 1 | native | zstd-3 | 0.280 | 197.6 | 506.5 |
| 1 | acquisition | stored | 1.000 | 1981.4 | 2447.7 |
| 1 | acquisition | deflate-1 | 0.303 | 83.3 | 277.7 |
| 1 | acquisition | zstd-3 | 0.278 | 201.3 | 527.0 |
| 1 | full_physical | stored | 1.000 | 2052.3 | 2480.5 |
| 1 | full_physical | deflate-1 | 0.234 | 106.0 | 329.1 |
| 1 | full_physical | zstd-3 | 0.213 | 213.5 | 408.0 |
| 25 | temperature | stored | 1.000 | 1341.0 | 1831.6 |
| 25 | temperature | deflate-1 | 0.231 | 119.7 | 288.9 |
| 25 | temperature | zstd-3 | 0.212 | 211.6 | 353.6 |
| 25 | native | stored | 1.000 | 1817.1 | 2537.1 |
| 25 | native | deflate-1 | 0.358 | 100.9 | 262.7 |
| 25 | native | zstd-3 | 0.323 | 143.4 | 487.6 |
| 25 | acquisition | stored | 1.000 | 2532.0 | 3013.6 |
| 25 | acquisition | deflate-1 | 0.355 | 96.7 | 281.4 |
| 25 | acquisition | zstd-3 | 0.319 | 136.0 | 456.2 |
| 25 | full_physical | stored | 1.000 | 573.4 | 1833.2 |
| 25 | full_physical | deflate-1 | 0.274 | 112.4 | 292.3 |
| 25 | full_physical | zstd-3 | 0.253 | 178.0 | 294.6 |

[Raw first study](reports/android-first-codecs.json) includes wall/thread CPU, exact source
SHA, allocation lower bounds, before/after memory and all five passes. Before/after
samples are not instantaneous peak allocation; this remains a measurement limitation.

## Repeated Android study (after sustained profiles)

| Frames | Role | Codec | Stored/input | Encode MB/s | Decode MB/s | Encode / decode CPU ms |
|---:|---|---|---:|---:|---:|---:|
| 1 | temperature | stored | 1.000 | 1056.2 | 705.5 | 0.42 / 0.36 |
| 1 | temperature | deflate-1 | 0.220 | 46.6 | 127.7 | 9.39 / 3.44 |
| 1 | temperature | zstd-3 | 0.210 | 93.8 | 201.1 | 4.66 / 2.08 |
| 1 | native | stored | 1.000 | 928.0 | 1078.3 | 0.25 / 0.21 |
| 1 | native | deflate-1 | 0.345 | 37.5 | 106.8 | 5.82 / 2.06 |
| 1 | native | zstd-3 | 0.320 | 60.3 | 194.3 | 3.61 / 1.13 |
| 1 | acquisition | stored | 1.000 | 807.4 | 1056.3 | 0.29 / 0.22 |
| 1 | acquisition | deflate-1 | 0.342 | 36.4 | 101.6 | 6.09 / 2.15 |
| 1 | acquisition | zstd-3 | 0.317 | 52.7 | 193.2 | 4.20 / 1.16 |
| 1 | full_physical | stored | 1.000 | 961.2 | 1168.0 | 0.69 / 0.58 |
| 1 | full_physical | deflate-1 | 0.263 | 41.4 | 115.2 | 15.84 / 5.71 |
| 1 | full_physical | zstd-3 | 0.245 | 75.3 | 206.6 | 8.71 / 3.20 |
| 25 | temperature | stored | 1.000 | 993.8 | 344.8 | 11.02 / 30.26 |
| 25 | temperature | deflate-1 | 0.222 | 50.1 | 134.2 | 190.14 / 70.43 |
| 25 | temperature | zstd-3 | 0.200 | 79.4 | 140.4 | 133.38 / 70.52 |
| 25 | native | stored | 1.000 | 272.7 | 1099.8 | 19.79 / 4.85 |
| 25 | native | deflate-1 | 0.348 | 39.5 | 126.4 | 124.36 / 38.58 |
| 25 | native | zstd-3 | 0.299 | 74.5 | 304.7 | 73.66 / 17.98 |
| 25 | acquisition | stored | 1.000 | 1328.1 | 1465.2 | 4.16 / 3.79 |
| 25 | acquisition | deflate-1 | 0.345 | 62.6 | 185.8 | 86.14 / 29.29 |
| 25 | acquisition | zstd-3 | 0.295 | 80.7 | 300.6 | 67.27 / 18.50 |
| 25 | full_physical | stored | 1.000 | 1488.4 | 464.1 | 11.11 / 34.74 |
| 25 | full_physical | deflate-1 | 0.264 | 75.3 | 192.9 | 207.50 / 80.40 |
| 25 | full_physical | zstd-3 | 0.237 | 110.4 | 236.9 | 149.67 / 68.78 |

[Raw repeat study](reports/android-repeat-codecs.json): five measured passes on the identical source bytes per role/group; wall rates use mean wall duration. Before/after memory remains endpoint evidence, not peak allocation. First/repeated source scenes differ; comparisons between codecs within one study are fair, but ratios between studies are not a controlled codec-only change.

## Zstd deployment boundary

- Official reference 1.5.7, BSD-3-Clause option of the dual BSD/GPL license.
- Source archive SHA-256 `eb33e51f49a15e023950cd7825ca74a4a2b43db8354825ac24fc1b7ee09e6fa3`.
- NDK 28 test-only arm64-v8a/API26 JNI, static C++ runtime; no product runtime dependency.
- Test library 703,016 bytes; SHA-256 `96a99e650bd58cdb054177607bc359c4039618dba8bdff53c43cc805f9d1be31`.
- Original measured packet-test APK: 3,641,657 bytes; final regression/probe build:
  3,642,707 bytes, including the uncompressed 703,016-byte library entry.
  App APK has no R2/Zstd library. No inference about other ABIs.
- Encoder level3, windowLog23, checksum; decoder <=8 MiB window, dictionary ID0,
  one independent frame, exact expected size, no concatenation/trailing payload.
- Linux independent reference 1.5.7 passes the same Android packets. Real Windows
  baseline and optional Zstd DLL parity are still pending.

## Decision returned to R1b

Recommend **STORED and zlib-wrapped DEFLATE as mandatory decoder capabilities**;
DEFLATE-1 remains the conservative writer candidate, subject to the failed live gate.
Zstd shows faster standalone encoding and modest extra size reduction, but neither
that nor Desktop numbers overrides live overload/latency. **Defer product adoption**;
if later approved, require explicit feature negotiation for codec2, never silent fallback.
Unknown required codec rejects even during recovery. No mandatory Zstd recommendation.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
