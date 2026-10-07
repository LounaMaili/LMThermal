#!/usr/bin/env python3
"""Kill only freshly spawned disposable writer children at declared framing boundaries."""
import argparse
import json
import pathlib
import subprocess
import tempfile
import time
from reader import Reader, file_sha, validate, demand
from validate_packet import synthetic_check


def run(java, classpath, directory):
    directory = pathlib.Path(directory); directory.mkdir(parents=True, exist_ok=True)
    rows = []
    # Both metadata and payload writes, body-sync/no-footer, committed/no-checkpoint,
    # and checkpoint/no-END are real process kills, not renamed truncation tests.
    cases = [('body_0', 2), ('body_1', 2), ('body_2', 2), ('body_synced', 2),
             ('footer_written', 2), ('footer_synced', 2), ('after_chunk', 1), ('after_checkpoint', -1)]
    for i, (phase, ordinal) in enumerate(cases):
        file = directory/f'kill-{i}.r2proto'; marker = directory/f'kill-{i}.marker'
        child = subprocess.Popen([java, '-cp', classpath, 'org.lmthermal.r2.PrototypeCommand', 'kill-target', str(file), phase, str(marker), str(ordinal)],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            deadline = time.monotonic()+45
            while not marker.exists() and child.poll() is None and time.monotonic() < deadline: time.sleep(.05)
            demand(marker.exists(), 'child_boundary_not_reached')
            child.kill(); out, error = child.communicate(timeout=10)
            demand(child.returncode < 0, 'writer_not_killed')
            result = validate(file)
            demand(not result['complete'] and result['frames'] > 0, 'kill_recovery')
            with Reader(file) as reader:
                for chunk in reader.chunks(): synthetic_check(chunk)
            rows.append(dict(boundary=phase, ordinal=ordinal, actual_process_kill=True, passed=True, result=result))
        finally:
            if child.poll() is None: child.kill(); child.wait(timeout=10)
    return dict(artifact='noncanonical-r2', cases=rows, passed=True,
                limitation='Software process kill and fsync evidence do not certify physical power-loss durability')


def main():
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--java', required=True); p.add_argument('--classpath', required=True)
    p.add_argument('--directory', required=True); p.add_argument('--output', required=True); a=p.parse_args()
    result=run(a.java,a.classpath,a.directory); pathlib.Path(a.output).write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(passed=result['passed'], cases=len(result['cases']))))
if __name__=='__main__': main()
