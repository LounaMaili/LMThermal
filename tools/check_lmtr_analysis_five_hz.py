#!/usr/bin/env python3
"""Independent finite R4 Analysis/5 Hz checks, including Desktop and private scalar oracles.

The input cases are explicitly synthetic virtual-clock recordings. This validator
does not open a camera, run Android, interpolate data or certify physical cadence/RSS.
Private CSVs are engineering comparison output, not a new product/export contract.
"""
import argparse
import csv
from collections import Counter
import hashlib
import json
import math
from pathlib import Path
import struct
import sys

from lmtr_v1 import Reader, dumps, loads
from lmtr_v1.container import inflate

NS='org.lmthermal.recorder.periodic-v1'
PERIOD=200_000_000


def sha(path):
    """Hash immutable inputs with a bounded scratch buffer."""
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(65536),b''):digest.update(block)
    return digest.hexdigest()


def rows(path):
    """These finite test plans admit at most512 source/oracle rows, not duration-sized payload caches."""
    result=[]
    with path.open('rb') as stream:
        while line:=stream.readline(65537):
            assert len(line)<=65536 and len(result)<512
            result.append(loads(line))
    return result


def logical_payloads(record,metadata,entry):
    """Materialize selected original bytes after complete independent CHUNK validation."""
    prefix=4+struct.unpack_from('<I',record['body'])[0]
    blocks={}
    for block in metadata['blocks']:
        start=prefix+int(block['offset']);data=record['body'][start:start+int(block['stored'])]
        blocks[block['id']]=data if block['codec']==0 else inflate(data,int(block['decoded']))
    result={}
    for payload in entry['payloads']:
        assert payload['kind']=='materialized', 'Analysis must not smuggle native/transport views'
        offset=int(payload['offset']);result[payload['id']]=blocks[payload['block_id']][offset:offset+int(payload['length'])]
    return result


def scalar_expectation(entry,payloads):
    """Independent full-plane native point/ROI reduction, excluding invalid pixels and gaps."""
    import numpy as np
    measurement=entry.get('measurement',{})
    if measurement.get('status')!='available':return None
    descriptor=next(p for p in entry['payloads'] if p['id']==measurement['temperature_payload_id'])
    height,width=descriptor['shape'];matrix=np.frombuffer(payloads[descriptor['id']],dtype='<f4').reshape(height,width)
    mask=(np.ones((height,width),dtype=bool) if 'mask_payload_id' not in measurement else
          np.frombuffer(payloads[measurement['mask_payload_id']],dtype='u1').reshape(height,width)==1)
    x,y=width//2,height//2
    x1,x2=max(0,x-max(1,width//8)),min(width,x+max(1,width//8))
    y1,y2=max(0,y-max(1,height//8)),min(height,y+max(1,height//8))
    values=matrix[y1:y2,x1:x2][mask[y1:y2,x1:x2]]
    return dict(point_xy=[x,y],point_c=float(matrix[y,x]) if mask[y,x] else None,
                roi=[x1,y1,x2,y2],valid_pixels=int(values.size),
                roi_min=None if not values.size else float(values.min()),
                roi_max=None if not values.size else float(values.max()),
                roi_mean=None if not values.size else float(values.mean(dtype=np.float64)))


def check_case(root,case,series_path,desktop_model,rectangle):
    """Reconcile slot identities with actual offered events, losses, durable ACKs and END separately."""
    directory=root/case['name'];path=directory/'capture.lmtr';before=sha(path)
    assert before==case['file_sha256']
    offered=rows(directory/'offered.jsonl');originals={int(r['original_ordinal']):r for r in rows(directory/'oracle.jsonl')}
    assert len(originals)==len(rows(directory/'oracle.jsonl'))
    selected={};not_selected=late=0;last_slot=-1
    for ordinal,event in enumerate(offered):
        assert int(event['original_ordinal'])==ordinal
        assert event['intake_lower_ns']==event['intake_upper_ns'], 'virtual clock must expose an exact intake witness'
        now=int(event['intake_lower_ns']);receipt=int(event['receipt_ns']);slot=now//PERIOD
        if slot>last_slot and receipt>=slot*PERIOD:
            selected[slot]=dict(event,selected_sequence=len(selected));last_slot=slot
        else:
            not_selected+=1;late+=receipt<slot*PERIOD
            assert event['admission']=='NOT_SELECTED'
    producer=case['periodic'];cutoff=int(producer['cutoff_ns']) if producer['cutoff_ns'] is not None else None
    planned=None if cutoff is None else max((cutoff+PERIOD-1)//PERIOD,last_slot+1)
    assert planned is None or planned<=512
    assert int(producer['selected'])==len(selected)
    assert int(producer['intentionally_not_selected'])==not_selected
    assert int(producer['late_not_selected'])==late
    assert int(producer['selected_available'])==sum(e['measurement_available'] for e in selected.values())
    counts=Counter();covered=set();seen_ordinals=set();expected_scalars={};closing=None
    with Reader(path) as reader,series_path.open('x',newline='') as stream:
        report=reader.check(full=True,recover=case['recover'])
        for key in ('status','integrity_scope','summary','logical_hashes','declared_end_status'):
            assert report.get(key)==case['kotlin'].get(key),(case['name'],key)
        writer=csv.DictWriter(stream,fieldnames=['entry_id','kind','status','timeline_ns','receipt_ns',
            'first_slot','last_slot','original_ordinal','point_c','roi_min','roi_max','roi_mean','valid_pixels'])
        writer.writeheader()
        boundary=int(report['verified_prefix_bytes']) if case['recover'] else reader.size
        cursor=0
        while cursor<boundary:
            record=reader.record(cursor);cursor+=record['length']
            if record['kind']!=2:continue
            metadata,_=reader.decode(record)
            contexts={c['id']:c['value'] for c in metadata['contexts']}
            segments={s['id']:s for s in metadata['segments']}
            for entry in metadata['entries']:
                kind=entry['kind'];row=dict(entry_id=entry['entry_id'],kind=kind,timeline_ns=entry['timeline_ns'])
                if kind=='segment_end':
                    closing=entry['extensions'][NS]['data']
                if kind not in ('observation','gap'):
                    writer.writerow(row);continue
                policy=next(s for s in segments[entry['segment_id']]['streams'] if s['stream_id']==entry['stream_id'])
                assert policy['sampling']==dict(mode='periodic',period_ns=str(PERIOD),origin_timeline_ns='0')
                assert policy['policy']['level']=='analysis'
                schedule=entry['scheduled']
                if schedule is not None:
                    first,last=int(schedule['first_slot']),int(schedule['last_slot'])
                    assert int(schedule['first_deadline_ns'])==first*PERIOD and int(schedule['last_deadline_ns'])==last*PERIOD
                    assert not covered.intersection(range(first,last+1));covered.update(range(first,last+1))
                    row.update(first_slot=first,last_slot=last)
                if kind=='gap':
                    category=entry['category'];row['status']=category
                    if entry['count'] is None:counts[category+'_unknown_events']+=1
                    else:counts[category]+=int(entry['count'])
                    assert not set(entry).intersection(('payloads','measurement','context_id','receipt'))
                    if category=='missed_schedule':
                        assert not set(range(first,last+1)).intersection(selected)
                        assert entry['observation_first'] is None and entry['device_first'] is None
                    elif category in ('preparation_overload','writer_overload'):
                        original=entry['extensions'][NS]['data'];event=selected[first]
                        ordinal=int(original['original_acquisition_ordinal'])
                        assert int(entry['count'])==1 and first==last
                        assert original['selected_sequence']==entry['observation_first']==entry['observation_last']==str(event['selected_sequence'])
                        assert ordinal==int(event['original_ordinal'])
                        assert original['measurement_available_at_source']==event['measurement_available']
                        row['original_ordinal']=ordinal
                    else:
                        assert category=='source_loss' and schedule is None and entry['count'] is None
                    writer.writerow(row);continue
                assert schedule is not None and first==last
                evidence=entry['extensions'][NS]['data'];event=selected[first]
                ordinal=int(evidence['original_acquisition_ordinal']);assert ordinal not in seen_ordinals;seen_ordinals.add(ordinal)
                assert ordinal==int(event['original_ordinal'])
                assert evidence['selected_sequence']==entry['observation_sequence']==str(event['selected_sequence'])
                assert evidence['original_callback_sequence']==event['original_callback_sequence']
                assert evidence['original_receipt_monotonic_ns']==entry['receipt']['stamp']['ticks']==event['receipt_ns']
                assert int(event['intake_lower_ns'])<=int(entry['timeline_ns'])+int(reader.head['timeline']['origin_monotonic_ns'])<=int(event['intake_upper_ns'])
                original=originals[ordinal]
                assert entry['receipt']['stamp']['clock_domain_id']==original['original_clock_id']
                assert dumps(contexts[entry['context_id']])==dumps(original['adapter_context'])
                available=entry['measurement']['status']=='available'
                assert available==event['measurement_available']==original['original_measurement_available']
                counts['available' if available else 'unavailable']+=1
                payloads=logical_payloads(record,metadata,entry)
                roles={p['role'] for p in entry['payloads']}
                assert roles==set(original['original_payloads']).intersection(('temperature','validity'))
                for payload in entry['payloads']:
                    expected=original['original_payloads'][payload['role']]
                    assert len(payloads[payload['id']])==int(expected['length'])
                    assert hashlib.sha256(payloads[payload['id']]).hexdigest()==expected['hash']
                expected_scalars[int(entry['entry_id'])]=scalar_expectation(entry,payloads)
                row.update(status=entry['measurement']['status'],receipt_ns=event['receipt_ns'],original_ordinal=ordinal)
                if expected_scalars[int(entry['entry_id'])] is not None:
                    values=expected_scalars[int(entry['entry_id'])]
                    row.update({k:values[k] for k in ('point_c','roi_min','roi_max','roi_mean','valid_pixels')})
                writer.writerow(row)
        assert int(case['snapshot']['acknowledged'])==counts['available']+counts['unavailable']==int(report['summary']['observations'])
        assert int(producer['acknowledged_available'])==counts['available']
        assert int(producer['acknowledged_unavailable'])==counts['unavailable']
        assert int(producer['acknowledged_missed'])==counts['missed_schedule']
        assert int(producer['acknowledged_preparation_loss'])==counts['preparation_overload']
        assert int(producer['acknowledged_writer_loss'])==counts['writer_overload']
        if not case['recover']:
            assert closing is not None and closing['selection_ledger_complete'] and producer['ledger_complete']
            assert int(closing['planned_slots'])==planned and covered==set(range(planned))
            assert int(closing['acquired_callbacks'])==len(offered)
            assert int(closing['selected_observations'])==len(selected)
            assert int(closing['intentionally_not_selected'])==not_selected
            assert int(closing['late_receipt_not_selected'])==late
            assert int(closing['missed_slots_journaled'])==counts['missed_schedule']==planned-len(selected)
            assert int(closing['preparation_losses'])==counts['preparation_overload']
            assert int(closing['writer_losses'])==counts['writer_overload']
            assert int(closing['selected_available'])+int(closing['selected_unavailable'])==len(selected)
            assert len(selected)==counts['available']+counts['unavailable']+counts['preparation_overload']+counts['writer_overload']
        else:
            assert report['unknown_tail'] and report['declared_end_status'] is None
    # Existing Desktop semantic reader must expose the identical saved bytes/validity and no gap readings.
    model=desktop_model(path,recover=case['recover'])
    try:
        model.verify_full()
        for i in range(model.entry_count):
            frame=model.frame(i);expected=expected_scalars.get(i)
            if expected is None:
                assert frame is None or frame.temperature_bytes is None
                if frame is not None:assert not frame.has_readings
                continue
            assert frame is not None
            assert frame.point(*expected['point_xy'])==expected['point_c']
            actual=frame.roi_statistics(rectangle(*expected['roi']))
            assert actual.valid_pixel_count==expected['valid_pixels']
            for name,key in (('min_c','roi_min'),('max_c','roi_max'),('mean_c','roi_mean')):
                value=getattr(actual,name)
                assert value==expected[key] or (value is not None and expected[key] is not None and math.isclose(value,expected[key],abs_tol=1e-12))
        assert model.recovered==case['recover']
    finally:model.close()
    assert sha(path)==before
    return dict(name=case['name'],sha256=before,recovered=case['recover'],counts=dict(counts),
                original_offers=len(offered),selected=len(selected),planned_slots=planned,
                complete_slot_coverage=not case['recover'],desktop_point_roi_parity=True,source_unchanged=True,
                pending_tail='unknown' if case['recover'] else 'closed',producer_ledger_seen=closing is not None)


def check_periodic_field(directory, recover=False):
    """Read bounded candidate field/real-paced output without inventing an all-callback oracle.

    The field oracle covers materialized owners only. It can prove stored source/slot/byte
    parity and represented writer losses; cumulative intentional-nonselection counts remain
    producer evidence, unlike the separate virtual probe's complete offered-event oracle.
    This finite engineering checker refuses more than 512 planned slots; not a wire limit.
    """
    from check_lmtr_r4_field import oracle_rows
    path=directory/'capture.lmtr';before=sha(path)
    originals=iter(oracle_rows(directory/'oracle.jsonl'));source=next(originals,None)
    slots=set();counts=Counter();closure=None;last_ordinal=-1
    namespace='org.lmthermal.recorder.periodic-v1'
    def witness(entry):
        return entry['extensions'][namespace]['data']
    with Reader(path) as reader:
        report=reader.check(full=True,recover=recover)
        end=int(report['verified_prefix_bytes']) if recover else reader.size
        at=0;origin=int(reader.head['timeline']['origin_monotonic_ns'])
        while at<end:
            record=reader.record(at);at+=record['length']
            if record['kind']!=2:continue
            meta,decoded=reader.decode(record)
            segments={s['id']:s for s in meta['segments']};contexts={c['id']:c['value'] for c in meta['contexts']}
            for entry in meta['entries']:
                if entry['kind']=='segment_end':
                    closure=entry.get('extensions',{}).get(namespace,{}).get('data');continue
                if entry['kind'] not in ('gap','observation'):continue
                stream=next(s for s in segments[entry['segment_id']]['streams'] if s['stream_id']==entry['stream_id'])
                policy=stream['sampling'];assert policy['mode']=='periodic' and policy['period_ns']=='200000000'
                scheduled=entry.get('scheduled')
                if scheduled is not None:
                    first,last=int(scheduled['first_slot']),int(scheduled['last_slot'])
                    assert 0<=first<=last<512
                    for slot in range(first,last+1):assert slot not in slots;slots.add(slot)
                    start=origin+int(policy['origin_timeline_ns']);period=int(policy['period_ns'])
                    assert int(scheduled['first_deadline_ns'])==start-origin+first*period
                    assert int(scheduled['last_deadline_ns'])==start-origin+last*period
                if entry['kind']=='gap':
                    counts[entry['category']]+=int(entry['count'] or 0)
                    if entry['category']!='writer_overload':continue
                    assert source is not None and witness(entry)['original_acquisition_ordinal']==source['original_ordinal']
                    source=next(originals,None);continue
                assert scheduled is not None and first==last and source is not None
                evidence=witness(entry);ordinal=int(evidence['original_acquisition_ordinal'])
                assert ordinal>last_ordinal;last_ordinal=ordinal
                assert ordinal==int(source['original_ordinal']) and source['sequence']==source['original_ordinal']
                assert evidence['selected_sequence']==entry['observation_sequence']
                assert evidence['original_callback_sequence']==source['original_source_sequence']
                assert evidence['original_receipt_monotonic_ns']==source['original_receipt_ns']
                assert evidence['measurement_available_at_source']==source['original_measurement_available']
                assert evidence['unavailable_reason']==source['original_unavailable_reason']
                assert stream['session_id']==source['original_session_id']==source['session_id']
                assert source['original_clock_id']==reader.head['timeline']['clock_domain_id']
                assert entry['receipt']['stamp']==dict(clock_domain_id=source['original_clock_id'],ticks=source['original_receipt_ns'])
                receipt=int(source['original_receipt_ns']);intake=origin+int(entry['timeline_ns'])
                assert start+first*period<=receipt<=intake<start+(first+1)*period
                assert int(source['intake_lower_monotonic_ns'])<=intake<=int(source['intake_upper_monotonic_ns'])
                assert dumps(contexts[entry['context_id']])==dumps(source['adapter_context'])
                for key in ('receipt','acquisition','source_evidence','device_sequence'):
                    assert entry[key]==source['adapter_facts'][key]
                wanted=set(stream['policy']['requested_roles'])|{'validity'}
                original=source['original_payloads'];present={p['role'] for p in entry['payloads']}
                assert present==set(original).intersection(wanted)
                assert ('temperature' in present)==source['original_measurement_available']
                for payload in entry['payloads']:
                    assert payload['length']==original[payload['role']]['length']
                    assert decoded['logical_hashes'][entry['entry_id']+':'+payload['id']]==original[payload['role']]['hash']
                if not source['original_measurement_available']:
                    assert entry['measurement']['reason']==source['original_unavailable_reason']
                counts['observations']+=1;counts['available' if source['original_measurement_available'] else 'unavailable']+=1
                source=next(originals,None)
        if not recover:
            assert source is None and closure is not None and closure['selection_ledger_complete'] is True
            planned=int(closure['planned_slots']);assert planned<=512 and slots==set(range(planned))
            assert int(closure['selected_observations'])==counts['observations']+counts['preparation_overload']+counts['writer_overload']+int(closure['invalid_evidence_losses'])
            assert int(closure['missed_slots_journaled'])==counts['missed_schedule']
        else:assert report['unknown_tail'] and report['declared_end_status'] is None
    assert before==sha(path)
    return dict(status='passed',sha256=before,counts=dict(counts),recovered=recover,source_unchanged=True,
                integrity_scope=report['integrity_scope'],planned_slot_coverage=not recover,
                original_owner_parity=True,nonselection_scope='producer aggregate, not independently witnessed by this materialized-owner oracle',
                warning='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.')


def main():
    """All reports/CSVs use exclusive new private paths; input LMTR/oracles remain unchanged."""
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root',type=Path);parser.add_argument('--desktop',type=Path)
    parser.add_argument('--field',action='store_true',help='Bounded candidate field/real-paced owner oracle instead of virtual offered-event cases')
    parser.add_argument('--recover',action='store_true')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();args.output.mkdir(mode=0o700)
    if args.field:
        report=check_periodic_field(args.root,args.recover)
        (args.output/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report));return
    if args.desktop is None:parser.error('--desktop is required for the virtual offered-event qualification')
    sys.path.insert(0,str(args.desktop))
    from lmtr_reader import LmtrPlaybackModel
    from offline_measurement import Rectangle
    cases=loads((args.root/'cases.json').read_bytes())['cases'];assert len(cases)==24
    result=[]
    for case in cases:
        checked=check_case(args.root,case,args.output/(case['name']+'-scalar-oracle.csv'),LmtrPlaybackModel,Rectangle)
        result.append(checked);print(case['name'],'passed',checked['counts'],flush=True)
    (args.output/'report.json').write_text(json.dumps(dict(status='passed',cases=result,
        scope='synthetic virtual-clock qualification; no physical or ART performance claim',
        scalar_csvs='private engineering comparison output, not a new product export contract'),indent=2)+'\n')


if __name__=='__main__':main()
