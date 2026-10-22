#!/usr/bin/env python3
"""Streaming independent field readback. Private scene/oracle facts never become public output."""
import argparse
import hashlib
import json
from pathlib import Path
from lmtr_v1 import Reader, dumps, loads

WARNING = 'Native-equivalent temperatures; absolute physical accuracy not yet independently validated.'


def file_hash(path):
    """Hash a source without an unbounded whole-file allocation."""
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()


def oracle_rows(path):
    """One bounded JSONL observation at a time; no complete recording/oracle list."""
    with path.open('rb') as stream:
        while line := stream.readline(65537):
            assert len(line) <= 65536, 'oracle_line_limit'
            yield loads(line)


def check(directory, recover=False):
    """Compare independent original-owner binary hashes, clocks and same-observation views.

    Adapter context/facts are cross-checked but explicitly not an independent interpretation oracle.
    Verified prefix parity says nothing about uncommitted tail or physical thermometric accuracy.
    """
    path = directory / 'capture.lmtr'
    before = file_hash(path)
    oracle_path = directory / 'oracle.jsonl'
    originals = iter(oracle_rows(oracle_path))
    next_original = next(originals, None)
    count = unavailable = views = 0
    role_bytes = {}
    physical_decoded = physical_stored = 0
    represented_writer_drops = set()
    with Reader(path) as reader:
        report = reader.check(full=True, recover=recover)
        end = int(report['verified_prefix_bytes']) if recover else reader.size
        offset = 0
        while offset < end:
            record = reader.record(offset)
            if record['kind'] == 2:
                meta, decoded = reader.decode(record)
                physical_decoded += sum(int(b['decoded']) for b in meta['blocks'])
                physical_stored += sum(int(b['stored']) for b in meta['blocks'])
                segments = {s['id']: s for s in meta['segments']}
                contexts = {c['id']: c['value'] for c in meta['contexts']}
                for entry in meta['entries']:
                    if entry['kind'] == 'gap':
                        if entry['category'] == 'writer_overload':
                            represented_writer_drops.add(int(entry['observation_first']))
                        continue
                    if entry['kind'] != 'observation':
                        continue
                    sequence = int(entry['observation_sequence'])
                    while next_original is not None and int(next_original['original_ordinal']) < sequence:
                        assert int(next_original['original_ordinal']) in represented_writer_drops, 'oracle_observation_silently_missing'
                        next_original = next(originals, None)
                    assert next_original is not None, 'missing_original_oracle'
                    source = next_original
                    assert sequence == int(source['original_ordinal'])
                    assert entry['observation_sequence'] == source['sequence']
                    stream = next(s for s in segments[entry['segment_id']]['streams'] if s['stream_id'] == entry['stream_id'])
                    assert stream['session_id'] == source['original_session_id'] == source['session_id']
                    assert entry['receipt']['stamp'] == dict(clock_domain_id=source['original_clock_id'], ticks=source['original_receipt_ns'])
                    assert source['original_clock_id'] in {c['id'] for c in stream['clocks']}
                    assert entry['source_evidence']['host_uvc_callback_sequence'] == source['original_source_sequence']
                    intake = int(entry['timeline_ns']) + int(reader.head['timeline']['origin_monotonic_ns'])
                    assert int(source['intake_lower_monotonic_ns']) <= intake <= int(source['intake_upper_monotonic_ns'])
                    assert entry['acquisition']['status'] == 'unknown' and entry['device_sequence'] is None
                    assert dumps(contexts[entry['context_id']]) == dumps(source['adapter_context'])
                    for key in ('receipt', 'acquisition', 'source_evidence', 'device_sequence', 'scheduled'):
                        assert entry[key] == source['adapter_facts'][key]
                    present = {p['role'] for p in entry['payloads']}
                    wanted = set(stream['policy']['requested_roles']) | {'validity'}
                    original = source['original_payloads']
                    assert present == set(original).intersection(wanted), 'profile_downgrade_or_fabricated_payload'
                    assert ('temperature' in present) == source['original_measurement_available']
                    if not source['original_measurement_available']:
                        assert entry['measurement']['reason'] == source['original_unavailable_reason']
                    for payload in entry['payloads']:
                        expected = original[payload['role']]
                        role_bytes[payload['role']] = role_bytes.get(payload['role'], 0) + int(payload['length'])
                        assert payload['length'] == expected['length']
                        assert decoded['logical_hashes'][entry['entry_id'] + ':' + payload['id']] == expected['hash']
                        if payload['kind'] == 'view':
                            assert source['original_native_transport_same_observation_view']
                            assert payload['parent_payload_id'] == 'acquisition' and payload['offset'] == '0'
                            views += 1
                    count += 1
                    unavailable += not source['original_measurement_available']
                    next_original = next(originals, None)
            offset += record['length']
        # A successful full END must account for every prepared original, including writer-loss gaps.
        if not recover:
            while next_original is not None:
                assert int(next_original['original_ordinal']) in represented_writer_drops
                next_original = next(originals, None)
    assert before == file_hash(path), 'source_mutated'
    return dict(status='passed', source_sha256=before, oracle_sha256=file_hash(oracle_path), observations=count,
                unavailable=unavailable, same_observation_views=views, recovery=recover,
                integrity_scope=report['integrity_scope'], terminal=report.get('declared_end_status'), source_unchanged=True,
                retained_role_bytes={k: str(v) for k, v in role_bytes.items()},
                physical_decoded_block_bytes=str(physical_decoded), physical_stored_block_bytes=str(physical_stored),
                binary_parity='pre_transform_original_owner_hashes', context_parity='adapter_metadata_cross_check_only',
                physical_accuracy='not_independently_validated', warning=WARNING)


def main():
    """Reports are sanitized aggregates; input files stay in the selected private directory."""
    parser = argparse.ArgumentParser()
    parser.add_argument('directory', type=Path)
    parser.add_argument('--recover', action='store_true')
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = check(args.directory, args.recover)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
