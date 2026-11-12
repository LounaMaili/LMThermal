#!/usr/bin/env python3
"""Independent full readback of isolated phase3 prototype outputs, never corpus generation."""
import argparse
import hashlib
import json
from pathlib import Path
from lmtr_v1 import Reader, loads, dumps

NS='org.lmthermal.diagnostic.loss-witnesses'


def sha(path):
    """Stream exact originals without modifying them."""
    digest=hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda:stream.read(65536),b''):digest.update(block)
    return digest.hexdigest()


def entries(reader,boundary):
    """Read complete committed records only; reference decode retains both integrity layers."""
    cursor=0
    while cursor<boundary:
        record=reader.record(cursor)
        if record['kind']==2:
            meta,decoded=reader.decode(record)
            for entry in meta['entries']:yield entry,meta,decoded
        cursor+=record['length']


def check(root):
    """Verify every sequence, original witness, qualified fact and payload, not just summaries."""
    rows=[]
    for group in ('reference','prototype'):
        for probe in sorted((root/group).glob('*/probe.json')):
            source=loads(probe.read_bytes());path=probe.with_name('capture.lmtr');before=sha(path)
            assert before==source['file_sha256']
            originals={int(x['sequence']):x['payloads'] for x in source['original_payloads']}
            recovered=not source['finalized'];observed=[];lost=[];witnesses=0;event_ids=[];times=[]
            with Reader(path) as reader:
                report=reader.check(full=True,recover=recovered)
                for k in ('summary','status','integrity_scope','logical_hashes','declared_end_status'):
                    assert report.get(k)==source['kotlin_report'].get(k),(group,probe.parent.name,k)
                boundary=int(report['verified_prefix_bytes']) if recovered else reader.size
                for e,meta,decoded in entries(reader,boundary):
                    event_ids.append(int(e['entry_id']));times.append(int(e['timeline_ns']))
                    if e['kind']=='gap':
                        first,last=int(e['observation_first']),int(e['observation_last'])
                        assert e['category']=='writer_overload' and int(e['count'])==last-first+1
                        lost.extend(range(first,last+1))
                        if last>first:
                            assert group=='prototype'
                            extension=e['extensions'][NS]
                            assert extension['schema_version']==dict(major=1,minor=0) and extension['required_feature'] is None
                            data=extension['data'];actual=data['intake_monotonic_ns']
                            expected=[source['offered_intake_witnesses'][str(n)] for n in range(first,last+1)]
                            assert actual==expected and len(actual)==last-first+1
                            assert e['timeline_ns']==str(int(actual[0])-int(reader.head['timeline']['origin_monotonic_ns']))
                            witnesses+=len(actual)
                    elif e['kind']=='observation':
                        sequence=int(e['observation_sequence']);observed.append(sequence)
                        assert {p['role'] for p in e['payloads']}==set(originals[sequence])
                        for p in e['payloads']:
                            expected=originals[sequence][p['role']]
                            assert p['length']==expected['length']
                            assert decoded['logical_hashes'][e['entry_id']+':'+p['id']]==expected['hash']
                assert event_ids==list(range(len(event_ids))) and times==sorted(times)
                assert lost==sorted(set(lost)) and observed==sorted(set(observed))
                assert not set(lost)&set(observed) and len(observed)==len(originals)==17
                assert len(lost)==int(report['summary']['known_missing_observations'])
                if not recovered:assert sorted(lost+observed)==list(range(int(source['source_offers'])))
                else:assert report['unknown_tail'] is True and report['declared_end_status'] is None
                trace=source.get('flow_trace')
                if trace is not None:
                    assert trace['omitted']=='0' and trace['clock_failures']=='0'
                    records=[dict(zip(trace['columns'],v)) for v in trace['rows']]
                    assert sum(int(r['control_delta']) for r in records)==0
                    assert sum(r['kind']=='RESERVE' for r in records)==32
                    assert max(int(r['control_bytes']) for r in records)==1048576
                    for r in records:
                        for k in ('preparation_oldest_witness','writer_oldest_witness','pending_oldest_witness'):
                            if r[k] is not None:assert int(r['diagnostic_monotonic_ns'])>=int(r[k])
            assert before==sha(path)
            rows.append(dict(group=group,case=probe.parent.name,source_sha256=before,observations=len(observed),
                missing=len(lost),chunks=int(report['summary']['chunks']),finalized=not recovered,
                retained_run_witnesses=witnesses,journal_peak_bytes=int(source['journal_peak_bytes']),
                first_failure=source['first_failure'],source_unchanged=True))
    assert len(rows)==16
    boundaries=[]
    for probe in sorted((root/'ack-boundaries').glob('*/boundary.json')):
        source=loads(probe.read_bytes());path=probe.with_name('capture.lmtr');before=sha(path)
        with Reader(path) as reader:
            report=reader.check(full=True,recover=True)
            assert report['summary']==source['kotlin_report']['summary']
            assert int(report['summary']['observations'])==int(source['after_durable'])
            assert source['acknowledged']=='0' and report['unknown_tail']
            for e,_,decoded in entries(reader,int(report['verified_prefix_bytes'])):
                if e['kind']=='observation':
                    for p in e['payloads']:
                        assert p['length']==source['payloads'][p['role']]['length']
                        assert decoded['logical_hashes'][e['entry_id']+':'+p['id']]==source['payloads'][p['role']]['hash']
        assert before==source['source_sha256']==sha(path)
        boundaries.append(dict(case=probe.parent.name,source_sha256=before,live_acknowledged=0,
            forensic_observations=int(report['summary']['observations']),unknown_tail=True,source_unchanged=True))
    assert len(boundaries)==4
    probe=root/'qualified/qualified.json';source=loads(probe.read_bytes());path=probe.with_name('capture.lmtr');before=sha(path)
    with Reader(path) as reader:
        report=reader.check(full=True);found=[]
        for e,meta,_ in entries(reader,reader.size):
            if e['kind']!='gap':continue
            expected=source['expected'][len(found)]
            assert e['stream_id']==expected['stream'] and e['timeline_ns']==expected['witness']
            for k,v in expected['facts'].items():assert dumps(e[k])==dumps(v)
            segment=next(s for s in meta['segments'] if s['id']==e['segment_id'])
            stream=next(s for s in segment['streams'] if s['stream_id']==e['stream_id'])
            assert stream['session_id']==expected['session'];found.append(e)
        assert len(found)==6 and report['summary']==source['kotlin_report']['summary']
    assert before==source['sha256']==sha(path)
    return dict(status='passed',scope='controlled host prototype; no ART or physical validation',cases=rows,
        ack_boundaries=boundaries,qualified_source_gaps=dict(events=6,known_missing=5,unknown_count_events=1,source_sha256=before),
        warning='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.')


def main():
    """Never overwrite old engineering output or a canonical source."""
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('directory',type=Path)
    parser.add_argument('--report',type=Path,required=True);args=parser.parse_args()
    result=check(args.directory)
    with args.report.open('x') as output:json.dump(result,output,indent=2);output.write('\n')
    print('Verified16 reference/prototype cases,4 ACK boundaries and6 qualified gap events')


if __name__=='__main__':main()
