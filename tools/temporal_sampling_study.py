#!/usr/bin/env python3
"""Noncanonical offline periodic-clock model and wire examples; no product sampler.

Selection is first genuine new source callback at/after a deadline. Clock values in
this simulator are declared simulated, never fabricated physical recorder witnesses.
The proposed selected-observation counter is distinct from the unchanged acquisition
counter; production adoption needs an explicit adapter/overload design review.
"""
import argparse
from copy import deepcopy
import hashlib
from pathlib import Path

from lmtr_v1 import Reader, ContractError, dumps, loads, write_plan

ROOT = Path(__file__).resolve().parents[1]
NS = 1_000_000_000


def exact_period(hz):
    """Do not silently round a rational frequency into the v1 integer-ns schedule."""
    if type(hz) is not int or hz <= 0 or NS % hz:
        raise ValueError('Not an exact integer-nanosecond v1 periodic frequency')
    return NS // hz


def simulate(hz, callbacks, end_ns):
    """Return bounded finite study rows, including late detection and original counters.

    No backdated receipt/intake, repeated latest frame, catch-up matrix or overload
    is invented. Absence at Stop creates only explicitly elapsed scheduled slots.
    This result is an offline model, not an implementation latency measurement.
    """
    period = exact_period(hz)
    if type(end_ns) is not int or end_ns<0:
        raise ValueError('Unknown or negative Stop witness')
    next_slot = 1
    selected = 0
    not_selected = 0
    rows = []
    previous_counter=previous_time=-1
    for source_sequence, now, available in callbacks:
        if (type(source_sequence) is not int or source_sequence<=previous_counter or
                type(now) is not int or now<0 or now<previous_time or type(available) is not bool):
            raise ValueError('Inconsistent original callback evidence')
        previous_counter=source_sequence;previous_time=now
        if now > end_ns:
            break
        due = now // period
        if due < next_slot:
            not_selected += 1
            continue
        if due > next_slot:
            rows.append(dict(kind='missed_schedule', first=next_slot, last=due-1,
                             detected_ns=now))
        rows.append(dict(kind='available' if available else 'unavailable', slot=due,
                         deadline_ns=due*period, receipt_ns=now, intake_ns=now+1,
                         selected_counter=selected, acquisition_counter=source_sequence))
        selected += 1
        next_slot = due+1
    last = end_ns // period
    if next_slot <= last:
        rows.append(dict(kind='missed_schedule', first=next_slot, last=last,
                         detected_ns=end_ns))
    return dict(requested_hz=hz, period_ns=period, end_ns=end_ns,
                intentionally_not_selected=not_selected, rows=rows)


def extension(data):
    """Use the existing optional extension envelope, with no new required feature."""
    return dict(schema_version=dict(major=1,minor=0),required_feature=None,data=data)


def wire_plan(hz):
    """Use existing masked illustration to express five distinct events in frozen v1.

    Tiny 3x2 synthetic matrices check temporal/validity semantics only. They are not
    canonical R3 fixtures, performance loads, HT frames or evidence of a runtime sampler.
    """
    case=deepcopy(loads((ROOT/'docs/lmtr-r1b/examples.json').read_bytes())['cases'][0])
    chunk=case['chunk']; segment=chunk['segments'][0]; stream=segment['streams'][0]
    period=exact_period(hz)
    stream['sampling']=dict(mode='periodic', period_ns=str(period), origin_timeline_ns='0')
    stream['extensions']={'org.lmthermal.study.periodic-counter-v1': extension({
        'status':'unimplemented_proposal', 'observation_counter':'actual_selected_callback_counter',
        'acquisition_counter':'independent_original_callback_counter',
        'selection':'first_new_callback_at_or_after_deadline', 'requested_hz':hz,
        'not_selected_is_not_overload':True})}
    segment['id']=hashlib.sha256(dumps({k:v for k,v in segment.items() if k!='id'})).hexdigest()
    sid=segment['id']; template=deepcopy(chunk['entries'][1])
    def schedule(first,last=None):
        last=first if last is None else last
        return dict(first_slot=str(first),last_slot=str(last),
                    first_deadline_ns=str(first*period),last_deadline_ns=str(last*period))
    def observation(entry,sequence,slot,available=True):
        value=deepcopy(template)
        value.update(entry_id=str(entry),segment_id=sid,observation_sequence=str(sequence),
                     timeline_ns=str(slot*period+1_000_001),scheduled=schedule(slot))
        value['receipt']['stamp']['ticks']=str(slot*period+1_000_000)
        value['extensions']={'org.lmthermal.study.original-counter-v1':extension({
            'original_acquisition_ordinal':str((slot*period+39_999_999)//40_000_000), 'simulated':True})}
        if not available:
            value['payloads']=[]
            value['measurement']={'status':'unavailable','reason':'synthetic_transient'}
            value['retention']['temperature']={'status':'unavailable','reason':'synthetic_transient'}
        return value
    def gap(entry,slot,last,category,sequence=None):
        return dict(kind='gap',entry_id=str(entry),timeline_ns=str(last*period+2_000_000),
                    segment_id=sid,stream_id=template['stream_id'],category=category,reason=category,
                    count=str(last-slot+1),scheduled=schedule(slot,last),device_first=None,device_last=None,
                    observation_first=None if sequence is None else str(sequence),
                    observation_last=None if sequence is None else str(sequence),
                    evidence=dict(basis='declared_schedule' if sequence is None else 'host_observation',before=None,after=None))
    start=deepcopy(chunk['entries'][0]);start['segment_id']=sid
    end=deepcopy(chunk['entries'][-1]);end.update(entry_id='6',segment_id=sid,timeline_ns=str(6*period))
    chunk['entries']=[start,observation(1,0,0),observation(2,1,1,False),
                      gap(3,2,3,'missed_schedule'),gap(4,4,4,'writer_overload',2),
                      observation(5,3,5),end]
    recipes={f'{entry}:{role}':deepcopy(case['payload_recipes'][f'1:{role}'])
             for entry in (1,5) for role in ('temperature','validity')}
    return dict(header=case['header'],chunks=[dict(metadata=chunk,payload_recipes=recipes)])


def run(directory):
    """Persist new private examples and negative results without touching old evidence."""
    directory.mkdir(mode=0o700)
    results=[]
    for hz in (5,10,25):
        model=simulate(hz,[(i,i*40_000_000,i!=250) for i in range(1,751)],30*NS)
        assert len(model['rows'])==30*hz
        plan=wire_plan(hz)
        (directory/f'{hz}hz-plan.json').write_bytes(dumps(plan))
        path=directory/f'{hz}hz.lmtr';write_plan(plan,path)
        before=hashlib.sha256(path.read_bytes()).hexdigest()
        with Reader(path) as reader: report=reader.check(full=True)
        # Original-counter holes are rejected, rather than hidden by declared sampling.
        invalid=wire_plan(hz)
        invalid['chunks'][0]['metadata']['entries'][2]['observation_sequence']='5'
        try:
            write_plan(invalid,directory/f'{hz}hz-unexplained-counter-skip.lmtr')
        except ContractError as error:
            negative=error.code
        else:
            raise AssertionError('Unexplained source-counter skip accepted')
        assert hashlib.sha256(path.read_bytes()).hexdigest()==before
        results.append(dict(hz=hz,sha256=before,python=report,negative_counter_skip=negative,
                            simulation=model))
    output=dict(status='passed',scope='offline proposal, not runtime sampler conformance',
                cases=results,exact_15hz='not_representable_by_integer_ns_period',
                rounded_15hz_period_ns=66_666_667,rounded_15hz_actual='14.999999925000000374999998125 Hz',
                production_changed=False)
    (directory/'report.json').write_bytes(dumps(output))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('new_output',type=Path)
    run(parser.parse_args().new_output)
