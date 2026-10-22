#!/usr/bin/env python3
"""Host-only metadata allocation attribution, not LMTR corpus generation or admission proof."""
import argparse
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import subprocess

from lmtr_v1 import dumps

ROOT = Path(__file__).resolve().parents[1]


def inputs():
    """Exercise bounded cardinality/text/precision/depth; these are JSON, not complete LMTRs."""
    nested = {'leaf': 'x' * 16384}
    for _ in range(31):
        nested = {'nested': nested}
    return {
        'dense-items': {'optional': [0] * 65535},
        'many-properties': {f'k{i:05}': None for i in range(60000)},
        'large-strings': {'optional': ['x' * 16384] * 63},
        # Exactly1024 canonical token bytes, with no Decimal context rounding.
        'long-decimals': {'optional': [Decimal('1.' + ('123456789' * 114)[:1022])] * 900},
        'maximum-depth': nested,
    }


def run(output, java_home, classpath_file):
    """Use a new private directory, bounded heap and the real existing Kotlin JSON implementation."""
    output.mkdir(parents=True, exist_ok=False)
    source = ROOT / 'tools/lmtr-r4-metadata-cost/MetadataCost.java'
    classpath = classpath_file.read_text().strip()
    paths = []
    hashes = {}
    for name, value in inputs().items():
        encoded = dumps(value)
        assert len(encoded) <= 1048576
        path = output / (name + '.json')
        path.write_bytes(encoded)
        paths.append(path)
        hashes[name] = hashlib.sha256(encoded).hexdigest()
    classes = output / 'classes'
    classes.mkdir()
    subprocess.run([str(java_home / 'bin/javac'), '-cp', classpath, '-d', str(classes), str(source)], check=True)
    result = subprocess.run([str(java_home / 'bin/java'), '-Xmx256m', '-cp', str(classes) + os.pathsep + classpath,
                             'MetadataCost', *map(str, paths)], check=True, text=True, capture_output=True)
    (output / 'raw-results.jsonl').write_text(result.stdout)
    rows = [json.loads(line) for line in result.stdout.splitlines()]
    assert len(rows) == 5
    report = dict(status='passed', scope='host_parse_plus_canonical_encode_allocation_not_peak_live_or_ART',
                  forced_gc=False, samples=8, warmups=4, heap_cap_bytes=256 * 1048576,
                  java_probe_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  kotlin_json_source_sha256=hashlib.sha256((ROOT / 'lmtr-contract/src/main/kotlin/org/lmthermal/lmtr/LmtrJson.kt').read_bytes()).hexdigest(),
                  input_sha256=hashes, cases=rows,
                  limitations=['synthetic_JSON_not_complete_CHUNK_schema', 'allocated_volume_not_simultaneously_live',
                               'not_generic_16x_metadata_object_size_bound', 'no_physical_admission_or_runtime_policy_change'])
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def main():
    """Require explicit host JDK and preserve all earlier outputs rather than overwriting."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, default=ROOT / 'lmtr-contract/build/runtime-classpath.txt')
    args = parser.parse_args()
    print(json.dumps(run(args.output, args.java_home, args.classpath_file)))


if __name__ == '__main__':
    main()
