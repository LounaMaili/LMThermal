#!/usr/bin/env python3
"""Bounded deterministic cancellation measurements, never a hard latency guarantee."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import time
import tracemalloc

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from tools.lmtr_v1 import Reader, ContractError
from tools.lmtr_v1.tests.test_hardening import historical_cases


def source_descriptors(path):
    """Count exact owned source descriptors on Linux; no private paths enter reports."""
    directory=Path('/proc/self/fd')
    if not directory.exists():return None
    return sum(1 for fd in directory.iterdir() if fd.resolve(strict=False)==path.resolve())


def python_probe(path,mode,stop_poll):
    """Request deterministically at a record boundary; include close in observed latency."""
    before=hashlib.sha256(path.read_bytes()).hexdigest();fds=source_descriptors(path)
    polls=0;request=0;reads_at_request=None
    tracemalloc.start()
    with Reader(path) as reader:
        accesses=0
        original=reader.record
        def record(*args,**kwargs):
            nonlocal accesses
            # Count completed accesses (including metadata cache hits), not just hashes.
            result=original(*args,**kwargs);accesses+=1;return result
        reader.record=record
        def cancelled():
            nonlocal polls,request,reads_at_request
            polls+=1
            if polls!=stop_poll:return False
            request=time.perf_counter_ns();reads_at_request=accesses
            return True
        if mode=='salvage':result=reader.salvage(cancelled)
        else:
            try:reader.check(full=True,recover=mode=='recovery',cancelled=cancelled)
            except ContractError as error:
                assert error.code=='cancelled';result=dict(status='cancelled',integrity_scope='none')
            else:raise AssertionError('cancel request not observed')
        additional=accesses-reads_at_request
    latency=time.perf_counter_ns()-request;_,peak=tracemalloc.get_traced_memory();tracemalloc.stop()
    assert request>0 and additional==0 and reader.file.closed
    assert before==hashlib.sha256(path.read_bytes()).hexdigest() and fds==source_descriptors(path)
    return dict(runtime=platform.python_version(),mode=mode,request_point=f'before record access at poll {stop_poll}',
                polls=polls,additional_record_reads=additional,observed_latency_ns=latency,
                traced_peak_bytes=peak,descriptors_before=fds,descriptors_after=source_descriptors(path),
                source_sha256=before,immutable=True,result=result)


def main():
    """Compare measured boundary responsiveness independently on bounded owned inputs."""
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java');parser.add_argument('--classpath');parser.add_argument('--report',type=Path);args=parser.parse_args()
    java=args.java or str(Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java'))
    classpath=args.classpath or (ROOT/'lmtr-contract/build/test-runtime-classpath.txt').read_text().strip()
    results=[]
    with tempfile.TemporaryDirectory(prefix='lmtr-cancel-') as directory:
        cases=historical_cases(directory)
        for mode,case,poll in [('full',cases[0],10),('recovery',cases[1],8),('salvage',cases[0],12)]:
            path=Path(directory)/(mode+'.lmtr');path.write_bytes(case['raw'])
            python=python_probe(path,mode,poll)
            process=subprocess.run([java,'-Xmx128m','-cp',classpath,'org.lmthermal.lmtr.LmtrCancellationProbe',str(path),mode,str(poll)],capture_output=True,text=True,timeout=30)
            assert process.returncode==0,process.stdout+process.stderr
            kotlin=json.loads(process.stdout)
            results.append(dict(mode=mode,source_bytes=len(case['raw']),kotlin=kotlin,python=python))
    report=dict(platform=platform.platform(),runs=results,
                limitations=['only cooperative boundary latency measured, including owned close',
                             'no interruption or deadline inside synchronous hash/decompression',
                             'tracemalloc excludes native allocations; JVM heap sampled at polls only',
                             'descriptor counts checked on Linux only; caller owns use/context close'],
                accuracy_warning='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.')
    if args.report:args.report.write_text(json.dumps(report,sort_keys=True,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(status='passed',runs=len(results),additional_records=0)))

if __name__=='__main__':main()
