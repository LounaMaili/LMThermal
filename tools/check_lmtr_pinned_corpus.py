#!/usr/bin/env python3
"""Regenerate --check using the pinned generator provenance, never repin old evidence.

Current readers are separately tested against these unchanged bytes. A changed
reader source hash must not rewrite the historical manifest's generation receipt.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]
PIN='1f1a668caf0af9c0c4d0f9ba036a88cd86cbddb0'
CORPUS='lmtr-contract/src/test/resources/lmtr/'


def main():
    """Extract only Git-tracked immutable inputs to an owned temp directory and check."""
    paths=[CORPUS,'docs/lmtr-r1b/examples.json','docs/lmtr-r1b/schema.json',
           'docs/lmtr-v1/schema.json','docs/LMTR_WIRE_V1.md','tools/lmtr_v1/',
           'tools/generate_lmtr_corpus.py']
    names=subprocess.check_output(['git','ls-tree','-r','--name-only',PIN,'--',*paths],cwd=ROOT,text=True).splitlines()
    assert names and all(not Path(name).is_absolute() and '..' not in Path(name).parts for name in names)
    with tempfile.TemporaryDirectory(prefix='lmtr-pinned-regeneration-') as directory:
        destination=Path(directory)
        for name in names:
            data=subprocess.check_output(['git','show',PIN+':'+name],cwd=ROOT)
            target=destination/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
            if name.startswith(CORPUS):assert (ROOT/name).read_bytes()==data,'pinned fixture/plan/manifest changed: '+name
        # This executes the original --check grammar/source/runtime receipt, not a
        # replacement provenance calculation with today's modified reader source.
        result=subprocess.run([sys.executable,'tools/generate_lmtr_corpus.py','--check'],cwd=destination,capture_output=True,text=True)
        assert result.returncode==0,result.stdout+result.stderr
        print(json.dumps(dict(status='passed',provenance_commit=PIN,original_check=json.loads(result.stdout),
                              manifest_sha256=hashlib.sha256((ROOT/CORPUS/'manifest.json').read_bytes()).hexdigest(),
                              current_reader_conformance='separate host/JVM/cross-runtime suites; not implied by historical regeneration')))

if __name__=='__main__':main()
