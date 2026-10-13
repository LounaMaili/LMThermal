#!/usr/bin/env python3
"""On-demand sparse >4 GiB LMTR navigation stress; never commits huge fixture bytes.

Sparse holes are actual zero bytes INSIDE STORED payload blocks, not skipped record
framing. All record/body/block/logical hashes and indexes bind those exact bytes.
The construction is synthetic opaque source evidence, not a live producer, sensor
measurement, disk throughput benchmark or backend power-loss test.
"""
from copy import deepcopy
import argparse
import hashlib
import json
import os
from pathlib import Path
import resource
import struct
import subprocess
import tempfile
import zlib

from lmtr_v1 import Reader, dumps, loads
from lmtr_v1.container import HEADER, FOOTER, MAGIC, COMMIT, blank_summary, add_chunk
from lmtr_v1.metadata import SHAPES, sha

ROOT = Path(__file__).resolve().parents[1]
MIB = 1048576
PAYLOAD = 60 * MIB
LARGE_CHUNKS = 70
CHUNKS = LARGE_CHUNKS + 1
FINAL_PAYLOAD = 16
ZERO_PAGE = bytes(MIB)


def zero_hash(prefix=b'', length=PAYLOAD):
    """Hash exact sparse zero bytes in bounded pages, without allocating the whole block."""
    digest = hashlib.sha256(prefix)
    for _ in range(length // MIB): digest.update(ZERO_PAGE)
    if length % MIB: digest.update(ZERO_PAGE[:length % MIB])
    return digest.digest()


def construct(path,require_large_offset=True):
    """Emit complete contiguous envelopes with sparse physical zero-valued payloads."""
    template = loads((ROOT / 'lmtr-contract/src/test/resources/lmtr/plans/illustration-01-stored.json').read_bytes())
    head = template['header']; head['recording_id'] = '00000000-0000-4000-8000-000000000600'; head['streams'][0]['purpose'] = 'opaque'
    prototype = template['chunks'][0]['metadata']; segment = deepcopy(prototype['segments'][0]); stream = segment['streams'][0]
    stream['source'] = {'module_id': 'org.example.synthetic.sparse', 'model_id': 'Opaque zero evidence', 'module_version': '1', 'origin': 'simulated'}
    stream['geometry'] = None
    stream['capabilities'] = {name: 'unsupported' for name in stream['capabilities']}; stream['capabilities']['acquisition'] = 'supported'
    encoding = 'org.example.synthetic.sparse-bytes-v1'
    stream['encodings'] = [{'role': 'acquisition', 'encoding_id': encoding}]
    stream['policy'] = {'level': 'full', 'requested_roles': ['acquisition'], 'choice': 'explicit'}
    segment['id'] = sha(dumps({key: value for key, value in segment.items() if key != 'id'}))
    context = deepcopy(prototype['contexts'][0]); context['value']['source'] = deepcopy(stream['source'])
    context['value']['algorithm'] = {'status': 'unsupported', 'reason': 'synthetic_opaque_no_thermometry'}; context['id'] = sha(dumps(context['value']))
    observation = deepcopy(prototype['entries'][1]); observation['segment_id'] = segment['id']; observation['context_id'] = context['id']
    observation['measurement'] = {'status': 'unsupported', 'reason': 'synthetic_opaque_no_temperature'}
    observation['retention']['temperature'] = {'status': 'unsupported', 'reason': 'synthetic_opaque_no_temperature'}
    observation['retention']['acquisition'] = {'status': 'present'}
    payload_hash = zero_hash(length=PAYLOAD).hex()
    payload = {'id': 'acquisition', 'role': 'acquisition', 'kind': 'materialized', 'block_id': 'b0', 'offset': '0', 'length': str(PAYLOAD), 'hash': payload_hash, 'encoding_id': encoding, 'dtype': 'bytes', 'byte_order': 'not_applicable', 'shape': [PAYLOAD], 'layout': 'opaque', 'unit': None, 'geometry': None, 'lossiness': 'lossless'}
    observation['payloads'] = [payload]
    block = {'id': 'b0', 'role': 'acquisition', 'encoding_id': encoding, 'dtype': 'bytes', 'byte_order': 'not_applicable', 'offset': '0', 'stored': str(PAYLOAD), 'decoded': str(PAYLOAD), 'codec': 0, 'hash': payload_hash}
    ordinal = 0; previous = 2**63 - 1; refs = []; previous_checkpoint = None; last_chunk = None; summary = blank_summary(head)
    with path.open('xb') as output:
        def append(kind, body, metadata=None, sparse_length=0):
            nonlocal ordinal, previous
            offset = output.tell(); body_size = len(body) + sparse_length
            digest = zero_hash(body, sparse_length) if sparse_length else hashlib.sha256(body).digest()
            h = HEADER.pack(MAGIC, 1, kind, 0, ordinal, body_size, previous)
            f = FOOTER.pack(COMMIT, body_size + 112, ordinal, previous, digest, 0, 0)
            f = f[:64] + struct.pack('<II', zlib.crc32(h + f[:64]), 0)
            output.write(h); output.write(body)
            if sparse_length: output.seek(sparse_length, os.SEEK_CUR)
            output.write(f)
            record = {'offset': str(offset), 'length': str(body_size + 112), 'ordinal': str(ordinal), 'hash': sha(h + digest)}
            if kind == 2: record.update(first=metadata['entries'][0]['entry_id'], last=metadata['entries'][-1]['entry_id'])
            elif kind == 3: record.update(first=metadata['first'], last=metadata['last'])
            elif kind == 4: record.update(first=metadata['summary']['first_entry'], last=metadata['summary']['last_entry'])
            previous = offset; ordinal += 1
            return record
        append(1, dumps(head), head)
        def checkpoint():
            nonlocal previous_checkpoint
            page = {'depth': 0, 'children': deepcopy(refs), 'first': refs[0]['record']['first'], 'last': refs[-1]['record']['last'], 'time_first_ns': refs[0]['time_first_ns'], 'time_last_ns': refs[-1]['time_last_ns'], 'stream_ids': ['primary'], 'chunks': summary['chunks'], 'entries': summary['entries']}
            SHAPES.check('IndexPage', page); root = append(3, dumps(page), page)
            value = {'recording_id': head['recording_id'], 'root': root, 'last_chunk': last_chunk, 'previous_checkpoint': previous_checkpoint, 'summary': deepcopy(summary)}
            SHAPES.check('Checkpoint', value); previous_checkpoint = append(4, dumps(value), value)
            return root
        for index in range(CHUNKS):
            current_payload = PAYLOAD if index < LARGE_CHUNKS else FINAL_PAYLOAD
            current_hash = payload_hash if index < LARGE_CHUNKS else zero_hash(length=FINAL_PAYLOAD).hex()
            e = deepcopy(observation); entry_id = index + 1; time = entry_id * 1000000
            e.update(entry_id=str(entry_id), timeline_ns=str(time), observation_sequence=str(index)); e['receipt']['stamp']['ticks'] = str(time)
            e['payloads'] = [deepcopy(payload)]
            e['payloads'][0].update(length=str(current_payload), shape=[current_payload], hash=current_hash)
            current_block = deepcopy(block); current_block.update(stored=str(current_payload), decoded=str(current_payload), hash=current_hash)
            entries = [e]
            if index == 0: entries.insert(0, {'kind': 'segment_start', 'entry_id': '0', 'timeline_ns': '0', 'segment_id': segment['id']})
            if index == CHUNKS - 1: entries.append({'kind': 'segment_end', 'entry_id': str(CHUNKS + 1), 'timeline_ns': str((CHUNKS + 1) * 1000000), 'segment_id': segment['id'], 'reason': 'normal_stop'})
            metadata = {'recording_id': head['recording_id'], 'required_features': head['required_features'], 'segments': [segment], 'contexts': [context], 'entries': entries, 'blocks': [current_block]}
            SHAPES.check('Chunk', metadata); encoded = dumps(metadata)
            assert len(encoded) + 4 + current_payload <= 64 * MIB
            last_chunk = append(2, struct.pack('<I', len(encoded)) + encoded, metadata, current_payload); add_chunk(summary, metadata)
            refs.append({'record': last_chunk, 'time_first_ns': entries[0]['timeline_ns'], 'time_last_ns': entries[-1]['timeline_ns'], 'stream_ids': ['primary']})
            if (index + 1) % 32 == 0: checkpoint()
        root = checkpoint()
        end = {'recording_id': head['recording_id'], 'root': root, 'checkpoint': previous_checkpoint, 'summary': summary, 'status': 'completed', 'reason': 'normal_stop', 'finalization_time': {'status': 'known', 'clock_source': 'synthetic_utc', 'utc': '2026-10-13T00:00:00Z'}, 'error': None}
        SHAPES.check('End', end); append(5, dumps(end), end)
    if require_large_offset: assert int(last_chunk['offset']) > 2**32
    return {'chunks': CHUNKS, 'target_entry': CHUNKS, 'target_chunk_offset': last_chunk['offset'], 'target_logical_hash': zero_hash(length=FINAL_PAYLOAD).hex(), 'summary': summary}


def validate(path, expected, kotlin=None, full=False):
    """Compare selected/readback results independently while preserving narrow integrity scope."""
    before=path.stat()
    with Reader(path) as reader:
        result = reader.check(entry=expected['target_entry'])
        assert result['status'] == 'valid' and result['integrity_scope'] == 'selected_chunks'
        assert result['summary'] == expected['summary']
        assert result['logical_hashes'] == {str(CHUNKS) + ':acquisition': expected['target_logical_hash']}
        assert result['selected_chunks'] == 1 and result['validated_records'] <= 12
        result['source_byte_size'] = path.stat().st_size
        result['source_allocated_bytes'] = path.stat().st_blocks * 512
        result['peak_process_rss_kib'] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
        assert result['source_byte_size'] > 2**32 and result['source_allocated_bytes'] < 16 * MIB
        assert result['peak_process_rss_kib'] < 128 * 1024, 'selected-access process exceeded explicit test budget'
    if kotlin:
        command = [kotlin[0], '-Xmx256m', '-cp', kotlin[1], 'org.lmthermal.lmtr.LmtrCommand', 'check', str(path), '--entry', str(CHUNKS)]
        process = subprocess.run(command, capture_output=True, text=True, timeout=180)
        assert process.returncode == 0, process.stdout + process.stderr
        # Run JVM probes before the large Python full scan: child ru_maxrss can
        # otherwise inherit the parent's pre-exec high-water mark on Unix.
        result['kotlin_selected_peak_child_rss_kib']=resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
        checked = json.loads(process.stdout)
        assert checked['status'] == 'valid' and checked['integrity_scope'] == 'selected_chunks'
        assert checked['summary'] == expected['summary'] and checked['logical_hashes'] == result['logical_hashes']
        assert int(checked.get('selected_chunks', checked.get('chunks_decoded'))) == 1
        assert int(checked.get('records_read', 0)) <= 16
        refusal = subprocess.run(command[:-1] + ['1'], capture_output=True, text=True, timeout=180)
        result['kotlin_selected_and_refusal_cumulative_peak_child_rss_kib']=resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
        refusal_result = json.loads(refusal.stdout)
        assert refusal.returncode == 2 and refusal_result.get('error') == 'local_resource_limit', refusal.stdout + refusal.stderr
        result['kotlin_large_chunk_refusal'] = 'local_resource_limit_under_default_budget'
        result['kotlin_records_read'] = int(checked.get('records_read', 0))
        result['kotlin_chunks_decoded'] = int(checked.get('selected_chunks', checked.get('chunks_decoded')))
        result['kotlin_max_heap_bytes'] = 256 * MIB
        result['kotlin_default_working_budget_bytes'] = 128 * MIB
        result['kotlin_selected_access'] = 'pass_with_256m_heap'
    if full:
        with Reader(path) as reader: checked = reader.check(full=True)
        assert checked['status'] == 'valid' and checked['summary'] == expected['summary']
        result['full_streaming_integrity'] = 'pass'
        result['peak_full_process_rss_kib'] = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    after=path.stat()
    assert (before.st_size,before.st_mtime_ns,before.st_blocks)==(after.st_size,after.st_mtime_ns,after.st_blocks)
    result['source_immutability']='size,mtime_ns,allocated blocks unchanged after read-only validation'
    result['platform']='Linux'
    result['normative_commit']='8c343cd6cf1ca11eff4f3bbaa5c2b10dfbbf645f'
    result['accuracy_warning']='Native-equivalent temperatures; absolute physical accuracy not yet independently validated.'
    result['target_chunk_offset'] = expected['target_chunk_offset']
    result['limitations'] = ['synthetic zero-filled opaque evidence, not physical disk-write throughput', 'single selected 16-byte chunk after70large chunks; not a general admission guarantee', 'no real Windows filesystem execution', 'optional full streaming check is separate from selected scope', 'no backend power-loss or live memory/timer certification']
    return result


def main():
    """Create and remove an on-demand stress file; persist only an optional small report."""
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--full', action='store_true'); parser.add_argument('--java'); parser.add_argument('--classpath'); parser.add_argument('--report', type=Path); args = parser.parse_args()
    assert bool(args.java) == bool(args.classpath), 'provide both Java and runtime classpath'
    with tempfile.TemporaryDirectory(prefix='lmtr-sparse-') as directory:
        path = Path(directory) / 'over-4gib.lmtr'; expected = construct(path)
        report = validate(path, expected, (args.java, args.classpath) if args.java else None, args.full)
        if args.report: args.report.write_text(json.dumps(report, sort_keys=True, indent=2) + '\n')
        print(json.dumps(report, sort_keys=True))

if __name__ == '__main__': main()
