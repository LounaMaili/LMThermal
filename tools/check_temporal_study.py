#!/usr/bin/env python3
"""Independent readback of private, noncanonical offline temporal-study outputs.

Never modifies sources. Original source hashes are independent binary oracles;
adapter metadata parity is a cross-check, not independent thermometry validation.
"""
import argparse
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import sys

from check_lmtr_r4_field import check as field_check
from lmtr_v1 import Reader, loads


def sha(path):
    """Hash source files with bounded scratch memory."""
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for value in iter(lambda:stream.read(65536),b''):digest.update(value)
    return digest.hexdigest()


def cadence_check(directory):
    """Respect distinct selected and original counters, without changing the HT field checker."""
    path=directory/'capture.lmtr';before=sha(path)
    originals={int(row['selected_sequence']):row for row in
               map(loads,(directory/'oracle.jsonl').read_bytes().splitlines())}
    observed=unavailable=overload=missed=0
    with Reader(path) as reader:
        report=reader.check(full=True);offset=0
        while offset<reader.size:
            record=reader.record(offset);offset+=record['length']
            if record['kind']!=2:continue
            metadata,decoded=reader.decode(record)
            for entry in metadata['entries']:
                if entry['kind']=='gap':
                    if entry['category']=='missed_schedule':missed+=int(entry['count'])
                    elif entry['category'] in ('preparation_overload','writer_overload'):
                        overload+=int(entry['count'])
                    continue
                if entry['kind']!='observation':continue
                original=originals.pop(int(entry['observation_sequence']))
                assert entry['source_evidence']['original_acquisition_ordinal']==original['original_ordinal']
                assert entry['source_evidence']['host_uvc_callback_sequence']==original['original_source_sequence']
                assert entry['receipt']['stamp']['ticks']==original['original_receipt_ns']
                intake=int(entry['timeline_ns'])+int(reader.head['timeline']['origin_monotonic_ns'])
                assert int(original['intake_lower_ns'])<=intake<=int(original['intake_upper_ns'])
                assert entry['scheduled']['first_slot']==original['selected_slot']
                unavailable+=entry['measurement']['status']!='available'
                for payload in entry['payloads']:
                    key=entry['entry_id']+':'+payload['id']
                    assert decoded['logical_hashes'][key]==original['original_payloads'][payload['role']]['hash']
                    assert payload['length']==original['original_payloads'][payload['role']]['length']
                observed+=1
    # Materialized-but-dropped originals may remain; every one needs a real journal gap.
    assert len(originals)<=overload
    assert sha(path)==before
    return dict(sha256=before,observations=observed,unavailable=unavailable,
                overload_gaps=overload,missed_slots=missed,source_unchanged=True,python=report)


def run(root,desktop,java,classpath):
    """Validate exact arm parity, original hashes, both readers and Desktop playback."""
    arm_results=[];allocation=[]
    arms=sorted(root.glob('profile-*-*/report.json'))
    assert len(arms)==4
    reference=arms[0].parent
    for report_path in arms:
        summary=json.loads(report_path.read_text());directory=report_path.parent
        rows=[]
        for case in summary['cases']:
            allocation.append(dict(arm=summary['arm'],profile=case['profile'],
                allocation_per_freeze=statistics.median(int(x['allocated_bytes']) for x in case['samples'])/6,
                wall_ms_per_freeze=statistics.median(int(x['wall_ns']) for x in case['samples'])/6e6,
                prepared_owned_bytes=case['prepared_owned_bytes']))
            for file in case['files']:
                name=f"{case['profile']}-{file['codec']}";source=directory/name
                assert sha(source/'capture.lmtr')==sha(reference/name/'capture.lmtr')
                rows.append(dict(name=name,**field_check(source)))
        arm_results.append(dict(arm=directory.name,files=rows))
        print('Checked profile arm:',directory.name,flush=True)
    cadence=[]
    for item in json.loads((root/'cadence-01/report.json').read_text())['cases']:
        name=f"{item['profile']}-{item['requested_hz']}";checked=cadence_check(root/'cadence-01'/name)
        snap=item['snapshot']
        assert int(item['selected'])==checked['observations']+int(snap['preparation_drops'])+int(snap['writer_drops'])
        assert int(item['source_callbacks'])==int(item['selected'])+int(item['intentional_not_selected'])
        assert int(item['missed_slots'])==checked['missed_slots']
        cadence.append(dict(name=name,**checked))
        print('Checked cadence:',name,flush=True)
    # Use the independent Kotlin reader on periodic mixed-event examples as well.
    sampling=[]
    for path in sorted((root/'sampling-final').glob('*hz.lmtr')):
        result=subprocess.run([java,'-Xmx256m','-cp',classpath,'org.lmthermal.lmtr.LmtrCommand',
                               'check',str(path),'--full'],capture_output=True,check=True)
        sampling.append(dict(name=path.name,kotlin=json.loads(result.stdout)))
    sys.path.insert(0,str(desktop))
    from lmtr_reader import LmtrPlaybackModel
    desktop_rows=[]
    for path in [root/'cadence-01'/x['name']/'capture.lmtr' for x in cadence]+list((root/'sampling-final').glob('*hz.lmtr')):
        before=sha(path);model=LmtrPlaybackModel(path)
        try:
            verified=model.verify_full();available=unavailable=0
            for i in range(model.entry_count):
                frame=model.frame(i)
                if frame is None:continue
                if frame.temperature_bytes is None:
                    assert not frame.has_readings
                    unavailable+=1
                else:available+=1
                for payload in model.original_entry(i)['payloads']:
                    assert hashlib.sha256(frame.payload_bytes[payload['id']]).hexdigest()==payload['hash']
            desktop_rows.append(dict(name=str(path.relative_to(root)),available_temperature_planes=available,
                unavailable_observation_frames=unavailable,verified=verified,source_unchanged=sha(path)==before))
            print('Checked Desktop:',path.parent.name,path.name,available,unavailable,flush=True)
        finally:model.close()
    return dict(status='passed',scope='offline synthetic proposals, not physical or runtime-sampler qualification',
                profile_arms=arm_results,allocation=allocation,cadence=cadence,sampling=sampling,desktop=desktop_rows)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root',type=Path)
    parser.add_argument('--desktop',type=Path,required=True)
    parser.add_argument('--java',required=True)
    parser.add_argument('--classpath',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    result=run(args.root,args.desktop,args.java,args.classpath.read_text().strip())
    with args.report.open('x') as stream:json.dump(result,stream,indent=2);stream.write('\n')
