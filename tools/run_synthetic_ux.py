#!/usr/bin/env python3
"""Run the debug principal-app UX recipe exclusively on an already connected API 36 emulator-5554."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import socket
import struct
import subprocess
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
SERIAL = 'emulator-5554'
PACKAGE = 'org.lmthermal.app'
TEST = f'{PACKAGE}.SyntheticDemoDeviceTest'


def adb(*args: str, timeout: int = 180) -> bytes:
    """Never discover/connect other devices or start/restart the ADB server."""
    return subprocess.run(['adb', '-s', SERIAL, *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=timeout).stdout


def validate_png(data: bytes) -> None:
    """exec-out can return an error as bytes; verify complete PNG chunks before saving screenshot evidence."""
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError('Not a PNG screenshot')
    offset = 8
    while offset + 12 <= len(data):
        size = struct.unpack_from('>I', data, offset)[0]
        end = offset + size + 12
        if end > len(data):
            break
        chunk = data[offset + 4:end - 4]
        if zlib.crc32(chunk) != struct.unpack_from('>I', data, end - 4)[0]:
            raise ValueError('PNG CRC mismatch')
        if chunk[:4] == b'IEND' and end == len(data):
            return
        offset = end
    raise ValueError('Incomplete PNG screenshot')


def collect(locale: str, destination: Path) -> list[dict]:
    """Only copy this recipe's bounded private artifacts, never unrelated app or AVD files."""
    names = adb('shell', 'run-as', PACKAGE, 'ls', 'files/ux-synthetic').decode().splitlines()
    artifacts = []
    for name in sorted(names):
        if not re.fullmatch(rf'{locale}-[a-z0-9_-]+\.(png|json|lmtx)', name):
            continue
        data = adb('exec-out', 'run-as', PACKAGE, 'cat', f'files/ux-synthetic/{name}')
        if name.endswith('.png'):
            validate_png(data)
        elif name.endswith('.json'):
            json.loads(data)
        elif not data.startswith(b'PK\x03\x04'):
            raise ValueError('Not an LMTX ZIP')
        (destination / name).write_bytes(data)
        artifacts.append({'file': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
    return artifacts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--locale', choices=('en', 'fr', 'both'), default='both')
    parser.add_argument('--output', type=Path, default=ROOT/'build/ux-synthetic')
    options = parser.parse_args()
    # Refuse to let an ADB client implicitly start a missing local server. This recipe never manages AVDs.
    with socket.create_connection(('127.0.0.1', 5037), timeout=2):
        pass
    if adb('get-state').strip() != b'device' or adb('shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
        raise RuntimeError('emulator-5554 must already be online and be an emulator')
    if adb('shell', 'getprop', 'ro.build.version.sdk').strip() != b'36':
        raise RuntimeError('This recipe requires API 36')
    output = options.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    apk = ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    test_apk = ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    for artifact in (apk, test_apk):
        print(adb('install', '-r', str(artifact)).decode().strip(), flush=True)
    results = {'serial': SERIAL, 'api': 36, 'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
               'wm_size': adb('shell', 'wm', 'size').decode().strip(),
               'wm_density': adb('shell', 'wm', 'density').decode().strip(),
               'font_scale': adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip(), 'runs': []}
    for locale in ('en', 'fr') if options.locale == 'both' else (options.locale,):
        receipt = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', TEST,
                      '-e', 'demoLocale', locale, f'{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner')
        (output/f'{locale}-instrumentation.txt').write_bytes(receipt)
        passed = b'OK (3 tests)' in receipt and b'FAILURES' not in receipt
        run = {'locale': locale, 'passed': passed, 'tests': 3, 'artifacts': collect(locale, output)}
        results['runs'].append(run)
        (output/'results.json').write_text(json.dumps(results, indent=2) + '\n')
        print(f'{locale}: {"PASS" if passed else "FAIL"}; receipt: {output / (locale + "-instrumentation.txt")}', flush=True)
        if not passed:
            print(receipt.decode(), file=sys.stderr)
            return 1
    # Leave the actual demo entry available for the operator; Connect remains an explicit UI action.
    print(adb('shell', 'am', 'start', '-n', f'{PACKAGE}/.demo.SyntheticDemoActivity').decode().strip())
    print(f'Artifacts: {output}\nTap Connect / Open to animate the scene. No AVD data was cleared.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
