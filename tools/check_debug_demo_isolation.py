#!/usr/bin/env python3
"""Verify that the built APK contains the interactive fixture only in debug, including manifest and resources."""
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MARKERS = ('org/lmthermal/app/demo/', 'org.lmthermal.app.demo.SyntheticDemoActivity',
           'debug-thermal-scene', 'camera_demo_')


def markers(path: Path) -> set[str]:
    """Inspect decompressed DEX, binary XML and resource tables in either Android string encoding."""
    found = set()
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.endswith(('.dex', '.xml', '.arsc')):
                data = archive.read(name)
                found.update(marker for marker in MARKERS if any(
                    marker.encode(encoding) in data for encoding in ('utf-8', 'utf-16le')))
    return found


def main() -> None:
    debug = ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    release = ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk'
    assert markers(debug) == set(MARKERS), 'Debug fixture is incomplete or the check cannot identify its markers'
    assert not markers(release), 'Debug demo leaked into release APK'
    print('APK isolation passed: demo classes, activity, identity and resource keys in debug only.')


if __name__ == '__main__':
    main()
