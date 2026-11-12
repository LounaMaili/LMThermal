#!/usr/bin/env python3
"""Predeclared ON reference/candidate/candidate/reference; no adaptive retry.

Both revisions run on one hosted emulator/runner with fresh cleared target processes,
identical selected test source, invocation arguments, limits and coarse/fine diagnostics.
Candidate source identity and event SHA stay separate in every collector receipt.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

REFERENCE = 'a78fa70e300713b337c33eba2629fd4b217ea6c1'
ROOT = Path(__file__).resolve().parents[1]
ARMS = (('01-reference', False), ('02-candidate', True), ('03-candidate', True), ('04-reference', False))


def main():
    """Validate fixed input identities before starting the four one-time arms."""
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path);parser.add_argument('reference',type=Path)
    args=parser.parse_args();os.umask(0o077)
    candidate=os.environ['GITHUB_SHA'];reference=args.reference.resolve()
    if subprocess.check_output(['git','rev-parse','HEAD'],cwd=reference,text=True).strip()!=REFERENCE:
        raise ValueError('reference SHA mismatch')
    # The selected test and load definition must be exactly unchanged.
    for name in ('lmtr-art/src/androidTest/kotlin/org/lmthermal/art/HtMemoryRuntimeTest.kt',
                 'lmtr-field-support/src/test/kotlin/org/lmthermal/field/HtRecorderStressProbe.kt',
                 'lmtr-field-support/src/main/kotlin/org/lmthermal/field/FieldPreflight.kt',
                 'lmtr-recorder/src/main/kotlin/org/lmthermal/recorder/RecorderEngine.kt',
                 'lmtr-recorder/src/main/kotlin/org/lmthermal/recorder/RecordingChunk.kt'):
        if (ROOT/name).read_bytes()!=(reference/name).read_bytes(): raise ValueError('load/engine drift:'+name)
    args.directory.mkdir(mode=0o700,parents=True,exist_ok=False)
    results=[]
    for name,is_candidate in ARMS:
        source=ROOT if is_candidate else reference
        expected=candidate if is_candidate else REFERENCE
        command=[sys.executable,str(ROOT/'tools/validate_lmtr_r4_digest.py'),str(args.directory/name),
                 '--metrics-mode','on','--checkout',str(source),'--source-sha',expected]
        try: code=subprocess.run(command,check=False).returncode
        except OSError: code=20
        result={'arm':name,'source_sha':expected,'metrics':'on','exit_code':code}
        results.append(result)
        with (args.directory/(name+'-receipt.json')).open('x') as output:json.dump(result,output,indent=2)
    with (args.directory/'candidate-result.json').open('x') as output:
        json.dump({'predeclared_arms':list(ARMS),'results':results,'retries':0,'forced_gc':False},output,indent=2)
    return 20 if any(r['exit_code'] not in (0,10) for r in results) else max(r['exit_code'] for r in results)


if __name__=='__main__':raise SystemExit(main())
