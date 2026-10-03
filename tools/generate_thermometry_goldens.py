#!/usr/bin/env python3
"""Regenerate development-only Kotlin parity data using the sibling Desktop oracle.

Run with Desktop's .venv Python. Native LUT references are compared bitwise before
export; no APK libraries or private captures enter the Android application.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys

import numpy as np

REPO = Path(__file__).resolve().parents[1]
DESKTOP = REPO.parent / 'LMThermal-Desktop'
sys.path.insert(0, str(DESKTOP))
from native_equivalent_thermometry import build_lookup, temperature_matrix
from radiometric_session import make_measurement

OUTPUT = REPO / 'core/src/test/resources/thermometry'
NAMES = ('radiometric-initial', 'radiometric-room-first', 'radiometric-room-range',
         'radiometric-room-shutter-held', 'radiometric-room-settled', 'warm-hand-settled')


def properties(trace):
    """Save exact float32 bit patterns, not decimal values rounded for humans."""
    values = {}
    for key, value in trace.items():
        if key == 'calc_fix_raw':
            for name, number in zip(('water', 'transmission', 'inverse', 'radiation'), value):
                values[name] = struct.unpack('<I', struct.pack('<f', number))[0]
        elif isinstance(value, int):
            values[key] = value
        else:
            values[key] = struct.unpack('<I', struct.pack('<f', value))[0]
    return values


def generate():
    """Six original frames and two explicitly synthetic arithmetic branch cases."""
    OUTPUT.mkdir(parents=True, exist_ok=True)
    manifest = {'oracle': 'LMThermal-Desktop/native_equivalent_thermometry.py',
                'source_sha256': hashlib.sha256((DESKTOP / 'native_equivalent_thermometry.py').read_bytes()).hexdigest(),
                'artifacts': {}, 'native_comparisons': {}}
    raw_settled = (DESKTOP / 'tests/fixtures/radiometric-room-settled.raw').read_bytes()
    cases = {name: (DESKTOP / 'tests/fixtures' / (name + '.raw')).read_bytes() for name in NAMES}
    for name, offset, value in [('synthetic-long-distance', 223762, 20), ('synthetic-base-wrap', 223488, 0)]:
        raw = bytearray(raw_settled)
        struct.pack_into('<H', raw, offset, value)
        cases[name] = bytes(raw)
    for name, raw in cases.items():
        # Settled room already exists in the foundation fixtures, so do not duplicate it.
        if name in NAMES and name != 'radiometric-room-settled':
            (OUTPUT / (name + '.raw')).write_bytes(raw)
        lut, trace = build_lookup(raw)
        native = DESKTOP / 'tests/fixtures' / (name + '-native-lut.npy')
        if native.exists():
            reference = np.load(native, allow_pickle=False)
            finite = np.isfinite(lut)
            assert np.array_equal(np.isnan(lut), np.isnan(reference))
            assert np.array_equal(lut[finite].view('u4'), reference[finite].view('u4'))
            manifest['native_comparisons'][name] = {'entries': 16384, 'finite': int(finite.sum()),
                'differing_finite_bits': 0, 'nan_pattern_equal': True,
                'npy_sha256': hashlib.sha256(native.read_bytes()).hexdigest()}
        (OUTPUT / (name + '.lut.f32')).write_bytes(lut.astype('<f4').tobytes())
        values = properties(trace)
        if name in ('radiometric-room-settled', 'warm-hand-settled'):
            matrix = temperature_matrix(raw, lut)
            measurement = make_measurement(raw, 0)
            (OUTPUT / (name + '.matrix.f32')).write_bytes(matrix.astype('<f4').tobytes())
            for field in ('trailer_center_index', 'literal_center_index', 'high_index', 'low_index',
                          'image_word_min', 'image_word_max'):
                values[field] = getattr(measurement, field)
            for field in ('trailer_center_c', 'literal_center_c', 'high_c', 'low_c'):
                values[field] = struct.unpack('<I', struct.pack('<f', getattr(measurement, field)))[0]
            values['matrix_min'] = int(matrix.min().view('u4'))
            values['matrix_max'] = int(matrix.max().view('u4'))
            values['high_x'], values['high_y'] = measurement.high_xy
            values['low_x'], values['low_y'] = measurement.low_xy
        (OUTPUT / (name + '.properties')).write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
    for path in sorted(OUTPUT.iterdir()):
        if path.suffix in ('.raw', '.f32', '.properties'):
            manifest['artifacts'][path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    manifest['artifacts']['../fixtures/radiometric-room-settled.raw'] = hashlib.sha256(raw_settled).hexdigest()
    (OUTPUT / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    generate()
