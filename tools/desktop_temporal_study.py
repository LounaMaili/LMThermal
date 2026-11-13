#!/usr/bin/env python3
"""Finite offline load through the unchanged legacy Desktop recorder and playback.

The source deliberately alternates two existing sanitized fixture matrices at 25 Hz.
It is a synthetic gate stub, NOT USB acquisition, scene motion, current readiness or
physical accuracy evidence. Each ordinary sample uses real host sampling time; no
old measurement is presented as a newly captured physical scene. Outputs are private.
"""
import argparse
from dataclasses import replace
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import time
from unittest.mock import patch


def sha(path):
    """Stream a source digest without retaining the complete recording in memory."""
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for data in iter(lambda:stream.read(1048576),b''):digest.update(data)
    return digest.hexdigest()


def run(desktop,output,seconds):
    """Use actual production sampler/writer threads; never patch their scheduling/queues."""
    output.mkdir(mode=0o700)
    sys.path[:0]=[str(desktop),str(desktop/'tests')]
    import numpy as np
    from playback_support import observations
    from radiometric_recorder import RadiometricRecorder
    from radiometric_recording import ChunkWriter,open_recording
    from radiometric_playback import PlaybackModel
    from radiometric_session import SessionState
    from roi_measurement import NativeROI,roi_statistics
    templates=observations()
    roi=NativeROI(160,100,220,170)
    fixture_hashes={p.name:sha(p) for p in (desktop/'tests/fixtures').glob('*.raw')}
    reports=[]
    for rate in (5,10,25):
        destination=output/f'{rate}hz.lmthermal'
        commits=[];samples=[];source_count=0;maximum_queue=0
        original=ChunkWriter.commit
        def observed_commit(writer):
            # Diagnostic wrapper delegates unconditionally and retains scalars only.
            count=len(writer.pending);start=time.perf_counter_ns()
            try:return original(writer)
            finally:
                if count:
                    assert len(commits)<128
                    commits.append(dict(frames=count,wall_ns=time.perf_counter_ns()-start))
        with patch.object(ChunkWriter,'commit',observed_commit):
            recorder=RadiometricRecorder(destination,rate_hz=rate)
            recorder.manifest['validation_provenance']='Synthetic 25 Hz gate stub alternating sanitized fixtures; no new acquisition'
            source_oracle=(output/f'{rate}hz-source.jsonl').open('x')
            try:
                while time.monotonic()-recorder.started<seconds:
                    source_count+=1
                    deadline=recorder.started+source_count/25
                    time.sleep(max(0,deadline-time.monotonic()))
                    now=time.monotonic();elapsed=now-recorder.started
                    base=templates[source_count%2]
                    stamp=datetime.now(timezone.utc).isoformat()
                    measurement=replace(base.measurement,received_monotonic=now,measured_at_utc=stamp)
                    transient=8<=elapsed<9 or 19<=elapsed<19.5
                    frame=replace(base,received_monotonic=now,measurement=measurement,
                                  state=SessionState.SHUTTER_TRANSIENT if transient else SessionState.RADIOMETRIC_READY,
                                  rejection='synthetic_unavailable' if transient else None)
                    recorder.update_latest(frame,roi)
                    source_oracle.write(json.dumps(dict(callback=source_count,elapsed_s=elapsed,stamp=stamp,
                        fixture=source_count%2,unavailable=transient))+'\n')
                    status=recorder.status;maximum_queue=max(maximum_queue,status.queue_depth)
                    if len(samples)<800:
                        rss=next((int(line.split()[1])*1024 for line in Path('/proc/self/status').read_text().splitlines() if line.startswith('VmRSS:')),None)
                        samples.append(dict(elapsed_s=elapsed,queue_depth=status.queue_depth,rss_bytes=rss))
                start=time.perf_counter_ns();recorder.stop(timeout=30);stop_ns=time.perf_counter_ns()-start
            finally:
                source_oracle.close()
                if recorder.status.state=='recording':recorder.stop(timeout=30)
        original_hashes={str(p.relative_to(destination)):sha(p) for p in destination.rglob('*') if p.is_file()}
        recording=open_recording(destination);model=PlaybackModel(recording)
        known={row['stamp']:row for row in map(json.loads,(output/f'{rate}hz-source.jsonl').read_text().splitlines())}
        points=[];roi_means=[];stored=0;gaps=0
        for i,entry in enumerate(model.entries):
            frame=model.frame(i)
            if frame is None:
                gaps+=1;continue
            stored+=1
            source=known[frame.summary['frame_measured_at_utc']]
            assert not source['unavailable']
            expected=templates[source['fixture']].measurement
            assert frame.raw14.tobytes()==expected.raw14.tobytes()
            assert frame.temperature_c.tobytes()==expected.temperature_c.tobytes()
            point=float(frame.temperature_c[144,192]);stats=roi_statistics(frame.temperature_c,roi)
            assert point==expected.literal_center_c
            assert stats.mean_c==roi_statistics(expected.temperature_c,roi).mean_c
            points.append(point);roi_means.append(stats.mean_c)
        assert original_hashes=={str(p.relative_to(destination)):sha(p) for p in destination.rglob('*') if p.is_file()}
        manifest=recording.manifest
        assert stored==manifest['valid_frames'] and len(model.entries)==manifest['requested_samples']
        report=dict(rate_hz=rate,synthetic_source_callbacks=source_count,manifest=manifest,
                    stored_verified=stored,gap_or_drop_rows=gaps,stop_ns=stop_ns,
                    sampled_max_queue=maximum_queue,commit_inclusive_wall_ns=commits,
                    memory_queue_points=samples,point_range=[min(points),max(points)],
                    roi_mean_range=[min(roi_means),max(roi_means)],source_unchanged=True,
                    total_bytes=sum(p.stat().st_size for p in destination.rglob('*') if p.is_file()),
                    file_sha256=original_hashes)
        reports.append(report)
        (output/f'{rate}hz-report.json').write_text(json.dumps(report,indent=2)+'\n')
        print(rate,manifest['requested_samples'],stored,manifest['gap_samples'],manifest['dropped_samples'],flush=True)
    assert fixture_hashes=={p.name:sha(p) for p in (desktop/'tests/fixtures').glob('*.raw')}
    (output/'report.json').write_text(json.dumps(dict(scope='Host synthetic load, not a physical qualification',
        duration_per_rate_s=seconds,source_hz=25,desktop_head='dd1c1ff4d76fef4a7cf4ed90dd76354b2ed8ca86',
        fixture_sources_unchanged=True,cases=reports),indent=2)+'\n')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--desktop',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--seconds',type=int,default=30,choices=(30,))
    args=parser.parse_args();run(args.desktop.resolve(),args.output.resolve(),args.seconds)
