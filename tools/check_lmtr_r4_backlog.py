#!/usr/bin/env python3
"""Independent readback of deterministic, noncanonical synthetic recorder backlog probes."""
import argparse
import hashlib
import json
from pathlib import Path
from lmtr_v1 import Reader, loads


def file_hash(path):
    """Stream hashes without retaining an entire capture."""
    digest=hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda:source.read(65536),b''):digest.update(block)
    return digest.hexdigest()


def check(directory):
    """Validate committed chronology, payloads, loss accounting and truthful END absence."""
    rows=[]
    for probe in sorted(directory.glob('*/probe.json')):
        source=loads(probe.read_bytes());path=probe.with_name('capture.lmtr');before=file_hash(path)
        assert before==source['file_sha256']
        recover=not source['finalized'];observations=[];losses=[];entries=[];times=[]
        with Reader(path) as reader:
            report=reader.check(full=True,recover=recover)
            kotlin=source['kotlin_report']
            for key in ('status','summary','logical_hashes','integrity_scope','unknown_tail','declared_end_status'):
                # Python omits unknown_tail on a complete END; Kotlin emits false.
                left=report.get(key,False) if key=='unknown_tail' else report.get(key)
                right=kotlin.get(key,False) if key=='unknown_tail' else kotlin.get(key)
                assert left==right,(probe.parent.name,key)
            originals={int(o['sequence']):o['payloads'] for o in source['original_payloads']}
            boundary=int(report['verified_prefix_bytes']) if recover else reader.size
            cursor=0;end_seen=False
            while cursor<boundary:
                record=reader.record(cursor);end_seen|=record['kind']==5
                if record['kind']==2:
                    meta,decoded=reader.decode(record)
                    for e in meta['entries']:
                        entries.append(int(e['entry_id']));times.append(int(e['timeline_ns']))
                        if e['kind']=='gap':
                            assert e['category']=='writer_overload' and e['count']=='1'
                            assert e['observation_first']==e['observation_last']
                            losses.append(int(e['observation_first']))
                        elif e['kind']=='observation':
                            sequence=int(e['observation_sequence']);observations.append(sequence)
                            expected=originals[sequence]
                            assert {p['role'] for p in e['payloads']}==set(expected)
                            for p in e['payloads']:
                                assert p['length']==expected[p['role']]['length']
                                assert decoded['logical_hashes'][e['entry_id']+':'+p['id']]==expected[p['role']]['hash']
                cursor+=record['length']
            assert entries==list(range(len(entries))) and times==sorted(times)
            assert observations==sorted(set(observations)) and losses==sorted(set(losses))
            assert not set(observations)&set(losses)
            assert len(observations)==int(report['summary']['observations'])==len(originals)
            assert len(losses)==int(source['expected_loss_records'])-int(source.get('last_gap_append_failure',source.get('last_gap_sync_failure',False)))
            assert end_seen==source['finalized']
            # Completed files cover every offered original. Incomplete files never
            # certify unknown tail, even when all earlier known losses are present.
            represented=sorted(observations+losses)
            if not recover:assert represented==list(range(int(source['source_offers'])))
            else:assert report['unknown_tail'] is True
        assert before==file_hash(path)
        trace=[r for r in source['trace'] if r['record_type']==2]
        # Trace includes failed append ATTEMPTS; the committed summary does not.
        rows.append(dict(case=probe.parent.name,codec=source['codec'],aged=source['aged'],finalized=source['finalized'],
            source_sha256=before,observations=len(observations),known_missing=len(losses),
            unknown_tail=recover,chunk_attempts=len(trace),committed_chunks=int(report['summary']['chunks']),
            observation_chunk_attempts=sum(r['observations']>0 for r in trace),
            gap_only_chunk_attempts=sum(r['observations']==0 and r['gap_entries']>0 for r in trace),
            mixed_chunk_attempts=sum(r['observations']>0 and r['gap_entries']>0 for r in trace),
            maximum_simulated_age_ns=max(int(r['oldest_age_ns']) for r in trace),
            first_validation_calls=int(source['first_validation_calls']),second_validation_calls=int(source['second_validation_calls']),
            journal_peak_bytes=int(source['journal_peak_bytes']),unrepresented_loss=source['unrepresented_loss'],
            first_failure=source['first_failure'],terminal_reason=source['terminal_reason'],
            logical_bytes=report['summary']['logical_bytes'],integrity_scope=report['integrity_scope'],source_unchanged=True))
    assert len(rows)==8,'Expected all original eight cases, including negative outcomes'
    return dict(status='passed',scope='controlled software clock and injected backend latency; not ART throughput',cases=rows,
                warning='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.')


def main():
    """Write aggregate evidence separately from original probe files."""
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory',type=Path);parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args();report=check(args.directory)
    with args.report.open('x') as output:json.dump(report,output,indent=2);output.write('\n')
    print('Passed independent readback of all eight complete/incomplete probes')


if __name__=='__main__':main()
