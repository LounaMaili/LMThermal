#!/usr/bin/env python3
"""Export Desktop presentation tables/normalization, using Desktop's virtual environment.

No camera is accessed. Only existing sanitized matrix fixtures are reused. RGB lookup
samples are numerical palette data, not a runtime OpenCV/Python dependency on Android.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys
import cv2
import numpy as np

REPO = Path(__file__).resolve().parents[1]
DESKTOP = REPO.parent / 'LMThermal-Desktop'
sys.path.insert(0, str(DESKTOP))
from celsius_palette import PALETTES, auto_range, normalized_levels, palette_rgb


def export():
    """Export exact 256-color ARGB maps and full-image normalization references."""
    output = REPO / 'core/src/test/resources/presentation'
    output.mkdir(parents=True, exist_ok=True)
    tables = {}
    for name in PALETTES:
        rgb = palette_rgb(np.arange(256, dtype=np.uint8).reshape(1, 256), name).reshape(256, 3)
        argb = np.array([(255 << 24) | (int(r) << 16) | (int(g) << 8) | int(b) for r, g, b in rgb], dtype='<u4')
        tables[name] = argb
    (output / 'palettes.argb').write_bytes(b''.join(table.tobytes() for table in tables.values()))
    source = ['package org.lmthermal.core', '', '/** Generated from Desktop OpenCV '+cv2.__version__+'; see tools/generate_presentation_goldens.py. */', 'internal object PaletteTables {']
    for field, name in [('inferno', 'Inferno'), ('hot', 'Iron-like'), ('turbo', 'Turbo')]:
        source.append('    val '+field+' = intArrayOf(')
        numbers = [str(struct.unpack('<i', struct.pack('<I', int(v)))[0]) for v in tables[name]]
        for i in range(0, 256, 8): source.append('        '+', '.join(numbers[i:i+8])+',')
        source.append('    )')
    source.append('}')
    (REPO / 'core/src/main/kotlin/org/lmthermal/core/PaletteTables.kt').write_text('\n'.join(source)+'\n')
    bounds = {}
    for name in ['radiometric-room-settled', 'warm-hand-settled']:
        matrix = np.frombuffer((REPO / 'core/src/test/resources/thermometry' / (name+'.matrix.f32')).read_bytes(), dtype='<f4').reshape(288, 384)
        auto = auto_range(matrix)
        bounds[name] = {'lower': auto.lower, 'upper': auto.upper}
        for mode, lower, upper in [('auto', auto.lower, auto.upper), ('locked', 25.0, 45.0)]:
            (output / (name+'.'+mode+'.levels')).write_bytes(normalized_levels(matrix, lower, upper).tobytes())
    # Also pin synthetic percentile cases independent of real scene inputs.
    cases = {'linear': np.tile(np.linspace(10, 40, 384, dtype=np.float32), (288, 1)),
             'constant': np.full((288, 384), 25, dtype=np.float32),
             'near-constant': np.tile(np.linspace(25, 25.01, 384, dtype=np.float32), (288, 1))}
    cases['outliers'] = cases['linear'].copy(); cases['outliers'][0, :2] = [-1000, 1000]
    for name, matrix in cases.items():
        result = auto_range(matrix); bounds[name] = {'lower': result.lower, 'upper': result.upper}
    (output / 'ranges.properties').write_text(''.join(f'{name}.{side}={number}\n' for name, result in bounds.items() for side, number in result.items()))
    manifest = {'desktop_source_sha256': hashlib.sha256((DESKTOP/'celsius_palette.py').read_bytes()).hexdigest(),
                'opencv': cv2.__version__, 'numpy': np.__version__, 'palette_order': list(PALETTES), 'files': {}}
    for path in sorted(output.iterdir()):
        if path.suffix in ['.argb', '.levels', '.properties']:
            manifest['files'][path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    (output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')


if __name__ == '__main__':
    export()
