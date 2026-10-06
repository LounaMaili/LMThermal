#!/usr/bin/env python3
"""Measure lossless codecs on saved HT-301 data; not a recording implementation.

Input preparation uses the existing Desktop reference without opening a camera.
Only aggregate sizes/timings are written: no frames, images or private paths.
Use short saved selections; this in-memory benchmark is not a bounded recorder.
Run with PYTHONDONTWRITEBYTECODE=1 to keep the audited Desktop tree read-only.
"""

import argparse
import ctypes
import ctypes.util
from datetime import datetime, timezone
import hashlib
import json
import platform
import statistics
import sys
import time
import zlib
from pathlib import Path


class Zstandard:
    """Use the installed reference C library without adding an app dependency."""

    def __init__(self):
        """Bind C pointer/size types explicitly so 64-bit sizes are not truncated."""
        library = ctypes.util.find_library("zstd")
        if not library:
            raise RuntimeError("Install libzstd for this analysis tool only")
        self.lib = ctypes.CDLL(library)
        self.lib.ZSTD_compressBound.argtypes = [ctypes.c_size_t]
        self.lib.ZSTD_compressBound.restype = ctypes.c_size_t
        self.lib.ZSTD_compress.argtypes = [ctypes.c_void_p, ctypes.c_size_t,
                                          ctypes.c_void_p, ctypes.c_size_t, ctypes.c_int]
        self.lib.ZSTD_compress.restype = ctypes.c_size_t
        self.lib.ZSTD_decompress.argtypes = [ctypes.c_void_p, ctypes.c_size_t,
                                            ctypes.c_void_p, ctypes.c_size_t]
        self.lib.ZSTD_decompress.restype = ctypes.c_size_t
        self.lib.ZSTD_isError.argtypes = [ctypes.c_size_t]
        self.lib.ZSTD_isError.restype = ctypes.c_uint
        self.lib.ZSTD_versionString.restype = ctypes.c_char_p
        self.version = self.lib.ZSTD_versionString().decode("ascii")

    def checked(self, result):
        """Stop on a codec error instead of reporting a misleading size."""
        if self.lib.ZSTD_isError(result):
            raise RuntimeError("Zstandard codec error")
        return result

    def compress(self, data, level):
        """Include C/Python buffer allocation and copies in the measured cost."""
        capacity = self.lib.ZSTD_compressBound(len(data))
        destination = ctypes.create_string_buffer(capacity)
        size = self.checked(self.lib.ZSTD_compress(destination, capacity,
                                                   data, len(data), level))
        return destination.raw[:size]

    def decompress(self, data, expected):
        """Use the known original length; this is not an untrusted-file parser."""
        destination = ctypes.create_string_buffer(expected)
        size = self.checked(self.lib.ZSTD_decompress(destination, expected,
                                                     data, len(data)))
        if size != expected:
            raise RuntimeError("Unexpected decoded size")
        return destination.raw[:size]


def copied(data):
    """Give STORED a real memory-copy cost rather than timing a no-op."""
    return memoryview(data).tobytes()


def measure(blocks, compress, decompress, repeats):
    """Warm up, verify exact bytes, then time complete passes over one dataset."""
    encoded = [compress(block) for block in blocks]
    for block, packed in zip(blocks, encoded):
        if decompress(packed, len(block)) != block:
            raise AssertionError("Lossless roundtrip failed")
    compression_wall, compression_cpu = [], []
    decompression_wall, decompression_cpu = [], []
    for _ in range(repeats):
        cpu, wall = time.process_time(), time.perf_counter()
        for block in blocks:
            compress(block)
        compression_wall.append(time.perf_counter() - wall)
        compression_cpu.append(time.process_time() - cpu)
        cpu, wall = time.process_time(), time.perf_counter()
        for block, packed in zip(blocks, encoded):
            decompress(packed, len(block))
        decompression_wall.append(time.perf_counter() - wall)
        decompression_cpu.append(time.process_time() - cpu)
    original, stored = sum(map(len, blocks)), sum(map(len, encoded))
    return {
        "uncompressed_bytes": original, "stored_bytes": stored,
        "block_count": len(blocks), "max_block_bytes": max(map(len, blocks)),
        "ratio_uncompressed_over_stored": original / stored,
        "storage_reduction_percent": 100 * (1 - stored / original),
        "compress_MB_per_s": original / 1e6 / statistics.median(compression_wall),
        "decompress_MB_per_s": original / 1e6 / statistics.median(decompression_wall),
        "compress_cpu_ms_per_pass": 1000 * statistics.median(compression_cpu),
        "decompress_cpu_ms_per_pass": 1000 * statistics.median(decompression_cpu),
        "compress_wall_ms_per_pass": 1000 * statistics.median(compression_wall),
        "decompress_wall_ms_per_pass": 1000 * statistics.median(decompression_wall),
        "roundtrip": "byte-exact",
    }


def prepare(directory, start, stop, omit_repeats, temperature_matrix, np):
    """Prepare role-major bytes from genuine frames, excluding settling frames."""
    paths = sorted(directory.glob("frame-*.raw"))[start:stop]
    if not paths:
        raise ValueError("Empty selection")
    fingerprints, image_fingerprints = {}, []
    planes = {"temperature_f32le": [], "native_u16le": [], "acquisition": []}
    for path in paths:
        raw = path.read_bytes()
        fingerprints[path] = hashlib.sha256(raw).digest()
        if len(raw) != 224256:
            raise ValueError("Not an exact HT-301 transport frame")
        image = raw[:221184]
        words = np.frombuffer(image, dtype="<u2")
        if words.max() >= 0x4000:
            raise ValueError("Non-raw14 frame in selected input")
        image_digest = hashlib.sha256(image).digest()
        if omit_repeats and image_fingerprints and image_digest == image_fingerprints[-1]:
            continue
        matrix = temperature_matrix(raw)
        if matrix.shape != (288, 384) or not np.isfinite(matrix).all():
            raise ValueError("Unsupported reference temperature matrix")
        planes["temperature_f32le"].append(matrix.astype("<f4", copy=False).tobytes())
        planes["native_u16le"].append(image)
        planes["acquisition"].append(raw)
        image_fingerprints.append(image_digest)
    return planes, fingerprints, len(set(image_fingerprints))


def main():
    """Report reproducible codec measurements without generating format fixtures."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--desktop", type=Path, required=True)
    parser.add_argument("--dataset", action="append", required=True,
                        help="Public label=local directory containing frame-*.raw")
    parser.add_argument("--selection", action="append", default=[],
                        help="Label=start:stop in sorted frame list; stop is exclusive")
    parser.add_argument("--omit-consecutive-repeats", action="append", default=[],
                        help="Dataset label: remove adjacent held image repetitions for sensitivity analysis")
    parser.add_argument("--chunk-frames", nargs="+", type=int, default=[1, 8, 25, 50])
    parser.add_argument("--repeats", type=int, default=5)
    parser.add_argument("--cpu-description", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.repeats < 1 or any(n < 1 for n in args.chunk_frames):
        parser.error("Positive repeats/chunk sizes are required")
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(args.desktop.resolve()))
    import numpy as np
    from native_equivalent_thermometry import temperature_matrix

    zstd = Zstandard()
    codecs = {
        "stored": (copied, lambda packed, expected: copied(packed)),
        "deflate-1": (lambda data: zlib.compress(data, 1),
                      lambda packed, expected: zlib.decompress(packed)),
        "deflate-6": (lambda data: zlib.compress(data, 6),
                      lambda packed, expected: zlib.decompress(packed)),
        "zstd-1": (lambda data: zstd.compress(data, 1), zstd.decompress),
        "zstd-3": (lambda data: zstd.compress(data, 3), zstd.decompress),
    }
    selections = {}
    for specification in args.selection:
        label, bounds = specification.split("=", 1)
        selections[label] = tuple(map(int, bounds.split(":")))
    report = {
        "study_date": datetime.now(timezone.utc).date().isoformat(), "kind": "codec-analysis-only",
        "environment": {"architecture": platform.machine(),
                        "cpu": args.cpu_description, "python": platform.python_version(),
                        "numpy": np.__version__, "zlib": zlib.ZLIB_RUNTIME_VERSION,
                        "zstandard": zstd.version},
        "method": {"repeats": args.repeats, "statistic": "median after one warmup",
                   "units": "decimal MB/s; ratio = original/compressed",
                   "layout": "consecutive selected full frames, one independent stream per role/chunk",
                   "deflate_wrapper": "zlib RFC1950 wrapping RFC1951",
                   "zstd": "one reference frame per block, no dictionary or preprocessing",
                   "excluded": ["reference thermometry preparation", "disk/provider I/O",
                                "JSON/index/hashes", "preview", "optional validity mask"],
                   "included": "codec calls, allocation and Python/C byte copies",
                   "scope": "short live raw sequences; offline-derived reference temperatures"},
        "datasets": [], "results": [],
    }
    for specification in args.dataset:
        label, location = specification.split("=", 1)
        start, stop = selections.get(label, (0, None))
        omit_repeats = label in args.omit_consecutive_repeats
        planes, fingerprints, unique = prepare(Path(location), start, stop, omit_repeats,
                                               temperature_matrix, np)
        count = len(planes["acquisition"])
        report["datasets"].append({"label": label, "frames": count,
                                   "source_frames": len(fingerprints),
                                   "omitted_consecutive_repeats": len(fingerprints) - count,
                                   "unique_native_images": unique,
                                   "selected_sorted_indices": [start, stop],
                                   "per_frame_bytes": {k: len(v[0]) for k, v in planes.items()}})
        for role, frames in planes.items():
            for group in args.chunk_frames:
                blocks = [b"".join(frames[i:i + group]) for i in range(0, count, group)]
                for codec, (compress, decompress) in codecs.items():
                    result = measure(blocks, compress, decompress, args.repeats)
                    report["results"].append(dict(dataset=label, role=role,
                                                   chunk_frames=group, codec=codec, **result))
            print(f"Measured {label}: {role}", flush=True)
        for path, digest in fingerprints.items():
            if hashlib.sha256(path.read_bytes()).digest() != digest:
                raise AssertionError("Source changed during analysis")
    report["validation"] = {"source_files_unchanged": True,
                            "all_selected_frames_raw14_finite": True,
                            "all_codec_roundtrips_byte_exact": True}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Finished {len(report['results'])} codec/role/chunk/dataset cases", flush=True)


if __name__ == "__main__":
    main()
