#!/usr/bin/env python3
"""On-demand recovery suffix memory regression; no huge or private source enters Git.

A separately constructed complete source is preserved while a second owned file
contains only its HEADER+17CHUNK prefix. Each large chunk is8MiB, so retaining the
suffix bodies would exceed the tested JVM128MiB heap/Python80MiB traced budget.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import tracemalloc

import check_lmtr_sparse as sparse
from lmtr_v1 import Reader

ROOT=Path(__file__).resolve().parents[1]


def sha(path):
    """Hash the entire owned prefix in bounded pages before/after immutable reads."""
    digest=hashlib.sha256()
    with path.open('rb') as source:
        for data in iter(lambda:source.read(1048576),b''):digest.update(data)
    return digest.hexdigest()


def run(java,classpath):
    """Demonstrate suffix references stay bounded while all17chunks are verified."""
    sparse.PAYLOAD=8*sparse.MIB;sparse.LARGE_CHUNKS=16;sparse.CHUNKS=17
    with tempfile.TemporaryDirectory(prefix='lmtr-suffix-memory-') as directory:
        complete=Path(directory)/'source.lmtr';prefix=Path(directory)/'incomplete.lmtr'
        # The sparse constructor's >4GiB final-offset assertion belongs only to
        # that stress case; it is parameterized explicitly here, never weakened.
        expected=sparse.construct(complete,require_large_offset=False)
        with Reader(complete) as reader:last=reader.envelope(int(expected['target_chunk_offset']))
        boundary=last['offset']+last['length']
        with complete.open('rb') as source,prefix.open('xb') as output:
            remaining=boundary
            while remaining:
                data=source.read(min(1048576,remaining));assert data
                output.write(data);remaining-=len(data)
        before=sha(prefix)
        tracemalloc.start()
        with Reader(prefix) as reader:result=reader.check(full=True,recover=True)
        _,peak=tracemalloc.get_traced_memory();tracemalloc.stop()
        assert result['status']=='recovered' and result['integrity_scope']=='full_verified_prefix'
        assert result['summary']==expected['summary'];assert len(result['logical_hashes'])==17
        assert peak<80*sparse.MIB,peak
        process=subprocess.run([java,'-Xmx128m','-cp',classpath,'org.lmthermal.lmtr.LmtrCommand','check',str(prefix),'--full','--recover'],capture_output=True,text=True,timeout=180)
        assert process.returncode==0,process.stdout+process.stderr
        kotlin=json.loads(process.stdout)
        for field in ['status','integrity_scope','summary','logical_hashes','verified_prefix_bytes','last_verified_entry','reason']:
            assert kotlin[field]==result[field],field
        assert sha(prefix)==before
        return {'status':'passed','chunks':17,'large_chunk_bytes':8*sparse.MIB,'source_prefix_bytes':boundary,'prefix_sha256':before,'python_traced_peak_bytes':peak,'python_traced_budget_bytes':80*sparse.MIB,'jvm_heap_cap_bytes':128*sparse.MIB,'integrity_scope':'full_verified_prefix','source_immutability':'complete-prefix SHA256 unchanged','limitations':['one synthetic host suffix, not indefinite runtime certification','tracemalloc excludes some native/runtime allocations','no native Windows/ART'],'accuracy_warning':sparse.WARNING if hasattr(sparse,'WARNING') else 'Native-equivalent temperatures; absolute physical accuracy not yet independently validated.'}


def main():
    """Persist a small measured report; temporary source and prefix are owned/deleted."""
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',required=True);parser.add_argument('--classpath',required=True);parser.add_argument('--report',type=Path);args=parser.parse_args()
    result=run(args.java,args.classpath)
    if args.report:args.report.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
    print(json.dumps(result,sort_keys=True))

if __name__=='__main__':main()
