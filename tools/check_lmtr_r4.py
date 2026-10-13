#!/usr/bin/env python3
"""Independent R4 engine readback; disposable outputs, never canonical corpus generation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
from lmtr_v1 import Reader, loads

ROOT = Path(__file__).resolve().parents[1]


def validate(java, classpath, directory):
    """Produce actual queued-engine files, check original source hashes and complete reader semantics."""
    output = directory / 'generated'
    subprocess.run([java, '-Xmx256m', '-cp', classpath,
                    'org.lmthermal.recorder.RecordingInteropProbe', str(output)], check=True)
    return readback(output)


def readback(output):
    """Verify either host- or ART-produced synthetic files with the independent reader."""
    manifest = loads((output / 'source-manifest.json').read_bytes())
    rows = []
    for case in manifest['cases']:
        path = output / case['file']
        before = hashlib.sha256(path.read_bytes()).hexdigest()
        assert before == case['sha256']
        with Reader(path) as reader:
            report = reader.check(full=True, recover=case['name'] in ('torn-tail', 'navigation-failure'))
            for field in ('status', 'integrity_scope', 'summary', 'logical_hashes', 'declared_end_status'):
                assert report.get(field) == case['kotlin'].get(field), (case['name'], field)
            if case['name'] not in ('torn-tail', 'navigation-failure'):
                originals = iter(case['expected'])
                observed = 0
                # Full validation above establishes chronology. Inspect actual independent decoded bytes.
                at = 0
                while at < reader.size:
                    record = reader.record(at)
                    if record['kind'] == 2:
                        meta, decoded = reader.decode(record)
                        segments = {s['id']: s for s in meta['segments']}
                        for entry in meta['entries']:
                            if entry['kind'] != 'observation':
                                continue
                            source = next(originals)
                            assert entry['stream_id'] == source['stream_id']
                            assert entry['observation_sequence'] == source['sequence']
                            assert entry['context_id'] == source['context_hash']
                            stream = next(s for s in segments[entry['segment_id']]['streams'] if s['stream_id'] == source['stream_id'])
                            assert stream['session_id'] == source['session_id']
                            assert {p['role'] for p in entry['payloads']} == set(source['payloads'])
                            for p in entry['payloads']:
                                expected = source['payloads'][p['role']]
                                key = entry['entry_id'] + ':' + p['id']
                                assert p['length'] == expected['length']
                                assert decoded['logical_hashes'][key] == expected['hash']
                            observed += 1
                    at += record['length']
                assert next(originals, None) is None
            else:
                observed = int(report['summary']['observations'])
                assert report['unknown_tail'] and report['declared_end_status'] is None
        assert hashlib.sha256(path.read_bytes()).hexdigest() == before
        rows.append(dict(name=case['name'], sha256=before, observations=observed,
                         status=report['status'], integrity_scope=report['integrity_scope'], source_unchanged=True))
    return dict(status='passed', cases=rows, generated_scope='noncanonical R4 engineering outputs',
                accuracy_warning='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.')


def main():
    """Keep reports persistent when requested; generated binaries stay outside Git."""
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', default=str(Path(os.environ.get('JAVA_HOME', '')) / 'bin/java'))
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--existing', type=Path, help='Read actual synthetic ART output; do not invoke the JVM')
    args = parser.parse_args()
    if args.existing:
        report = readback(args.existing)
    else:
        classpath = (ROOT / 'lmtr-recorder/build/test-runtime-classpath.txt').read_text().strip()
        with tempfile.TemporaryDirectory(prefix='lmtr-r4-interop-') as temp:
            report = validate(args.java, classpath, Path(temp))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
