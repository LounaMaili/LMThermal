#!/usr/bin/env python3
"""Operator-authorized streaming extraction of one bounded app-private field run, never pairing/opening USB."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

FILES = ('capture.lmtr', 'oracle.jsonl', 'preflight.json', 'samples.jsonl',
         'private-report.json', 'inventory.json', 'measured-window.json')
MAX_BYTES = 3 * 1024**3


def main():
    """Require explicit authorization; filenames are fixed and no shell interpolation is used."""
    parser = argparse.ArgumentParser()
    parser.add_argument('--operator-authorized', action='store_true', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--destination', type=Path, required=True)
    args = parser.parse_args()
    assert re.fullmatch(r'[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}', args.run_id)
    assert not args.destination.exists(), 'Refuse overwriting prior evidence'
    args.destination.mkdir(parents=True)
    inventory = []
    total = 0
    for name in FILES:
        source = f'files/r4-field/{args.run_id}/{name}'
        exists = subprocess.run(['adb', '-s', args.serial, 'shell', 'run-as', 'org.lmthermal.field', 'test', '-f', source], capture_output=True)
        if exists.returncode:
            continue
        process = subprocess.Popen(['adb', '-s', args.serial, 'exec-out', 'run-as', 'org.lmthermal.field', 'cat', source], stdout=subprocess.PIPE)
        digest = hashlib.sha256()
        target = args.destination / name
        size = 0
        try:
            with target.open('xb') as stream:
                while block := process.stdout.read(65536):
                    size += len(block)
                    total += len(block)
                    if total > MAX_BYTES:
                        raise RuntimeError('Extraction exceeded finite-run 3 GiB cap; retain partial evidence')
                    stream.write(block)
                    digest.update(block)
            assert process.wait() == 0, 'ADB extraction failed; retained partial source copy'
        finally:
            process.stdout.close()
            if process.poll() is None:
                process.terminate()
                process.wait()
        inventory.append(dict(file=name, bytes=size, sha256=digest.hexdigest()))
    # Device inventory was written only after Stop/reopen; missing means incomplete acquisition, not success.
    saved = args.destination / 'inventory.json'
    if saved.exists():
        expected = {row['file']: row for row in json.loads(saved.read_text())['files']}
        for row in inventory:
            if row['file'] in expected:
                assert row['sha256'] == expected[row['file']]['sha256']
                assert row['bytes'] == int(expected[row['file']]['bytes'])
    (args.destination / 'fedora-inventory.json').write_text(json.dumps(dict(files=inventory, device_inventory_present=saved.exists()), indent=2) + '\n')
    print('Private extraction retained. Run independent check_lmtr_r4_field.py before publishing sanitized statistics.')


if __name__ == '__main__':
    main()
