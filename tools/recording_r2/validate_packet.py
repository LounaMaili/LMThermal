#!/usr/bin/env python3
"""Run unchanged Android-written noncanonical R2 bytes on Linux or real Windows."""
import argparse
import hashlib
import json
import pathlib
import struct
import sys
from reader import Reader, Zstd, file_sha, demand, validate


def synthetic_check(chunk):
    for entry, roles in zip(chunk['entries'], chunk['payloads']):
        sequence = int(entry['sequence'])
        if entry['reason'] is not None:
            demand(not roles); continue
        n = int(entry['width']) * int(entry['height']); mask = roles.get('validity')
        expected_c = b''.join(struct.pack('<f', 0.0 if mask is not None and mask[i] == 0 else 18.0 + ((i+sequence) % 600) / 32.0) for i in range(n))
        demand(roles['temperature'] == expected_c, 'independent_float32_parity')
        if 'native' in roles:
            raw = b''.join(struct.pack('<H', 5000 + (i+sequence) % 2000) for i in range(n))
            demand(roles['native'] == raw, 'independent_native_parity')
            if 'acquisition' in roles:
                size = 224256 if n == 384*288 else len(raw)+64
                demand(roles['acquisition'] == raw + bytes(size-len(raw)), 'independent_transport_parity')
        context = chunk['metadata']['contexts'][int(entry['context'])]
        demand(context['provenance'] == 'synthetic-r2-not-camera' and int(context['epoch']) == sequence//30, 'context_parity')


def run(directory, codec=None):
    directory = pathlib.Path(directory); manifest = json.loads((directory/'packet.json').read_text(encoding='utf-8'))
    demand(manifest['artifact'] == 'noncanonical-r2-synthetic-packet')
    rows = []
    for case in manifest['cases']:
        path = directory/case['file']; before = file_sha(path); demand(before == case['sha256'], 'packet_source_hash')
        error = None; result = None
        try:
            result = validate(path, codec)
            with Reader(path, codec) as reader:
                for chunk in reader.chunks(): synthetic_check(chunk)
                demand(reader.seek(3) is not None, 'lazy_seek')
        except ValueError as failure: error = str(failure)
        if case['expect'] == 'integrity_failure': demand(error is not None and 'integrity' in error, 'expected_integrity_failure')
        else:
            demand(error is None, error)
            demand(result['complete'] == (case['expect'] == 'complete'), 'completion_state')
            demand(result['frames'] == case['frames'] and result['gaps'] == case['gaps'], 'frame_gap_count')
        demand(before == file_sha(path), 'source_mutated')
        rows.append(dict(file=case['file'], expect=case['expect'], passed=True, source_sha256=before, result=result, error=error))
    return dict(platform=sys.platform, python=sys.version.split()[0], artifact='noncanonical-r2',
                zstd_version=codec.version if codec else None, cases=rows, passed=True, source_immutable=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('directory')
    parser.add_argument('--zstd', action='store_true'); parser.add_argument('--zstd-library'); parser.add_argument('--output')
    args = parser.parse_args(); codec = Zstd(args.zstd_library) if args.zstd or args.zstd_library else None
    result = run(args.directory, codec); text = json.dumps(result, indent=2)
    if args.output: pathlib.Path(args.output).write_text(text+'\n', encoding='utf-8')
    print(text)
if __name__ == '__main__': main()
