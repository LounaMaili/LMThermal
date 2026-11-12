#!/usr/bin/env python3
"""Four predeclared OFF/ON arms; never retry, replace or select a favorable arm.

Only the underlying collector can access its positively identified hosted emulator.
B/represented loss does not skip later arms. Every arm has a unique private directory,
and the collector clears only disposable target data AFTER previous evidence extraction.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ARMS = (('01-off', 'off'), ('02-on', 'on'), ('03-on', 'on'), ('04-off', 'off'))
ROOT = Path(__file__).resolve().parents[1]


def execute(directory, invoke=subprocess.run):
    """Retain all four outcomes, including infrastructure failures, without retries."""
    directory.mkdir(mode=0o700, parents=True, exist_ok=False)
    results = []
    for name, mode in ARMS:
        command = [sys.executable, str(ROOT / 'tools/validate_lmtr_r4_campaign.py'),
                   str(directory / name), '--metrics-mode', mode]
        try:
            completed = invoke(command, check=False)
            code = completed.returncode
            error = None
        except OSError as failure:
            code, error = 20, type(failure).__name__
        results.append({'arm': name, 'mode': mode, 'exit_code': code, 'error': error})
        with (directory / (name + '-receipt.json')).open('x') as output:
            json.dump(results[-1], output, indent=2)
    with (directory / 'balanced-result.json').open('x') as output:
        json.dump({'predeclared': list(ARMS), 'arms': results,
                   'retries': 0, 'forced_gc': False,
                   'scope': 'fine instrumentation only; coarse metrics in both arms'}, output, indent=2)
    return 20 if any(r['exit_code'] not in (0, 10) for r in results) else max(r['exit_code'] for r in results)


def main():
    """Require a new destination; host publication is the explicit campaign trigger."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    return execute(args.directory.resolve())


if __name__ == '__main__':
    raise SystemExit(main())
