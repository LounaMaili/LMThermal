#!/usr/bin/env python3
"""Regenerate synthetic canonical R3 bytes using the independent Python primitives.

Inputs under docs/lmtr-r1b remain historical illustrative metadata. Accepted plans
are separate generated documents. Only negative fixtures use the clearly isolated
raw-envelope mutator; it is never a production-writer validation bypass.
"""
from copy import deepcopy
from decimal import Decimal
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import struct
import tempfile
import zlib

from lmtr_v1 import Reader, write_plan, ContractError, dumps, loads
from lmtr_v1.container import HEADER, FOOTER, encode_record, prepare_chunk

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'lmtr-contract/src/test/resources/lmtr'
WARNING = 'Native-equivalent temperatures; absolute physical accuracy not yet independently validated.'


def sha(data): return hashlib.sha256(data).hexdigest()


def plan(case, codec):
    """Copy an illustrative scenario into a distinct accepted synthetic wire plan."""
    metadata = deepcopy(case['chunk'])
    for block in metadata['blocks']: block['codec'] = codec
    head = deepcopy(case['header']); head['codecs'] = [codec]
    return {'header': head, 'chunks': [{'metadata': metadata, 'payload_recipes': deepcopy(case['payload_recipes'])}], 'finalization_time': {'status': 'known', 'clock_source': 'synthetic_utc', 'utc': '2026-10-13T00:00:00Z'}}


def rehash(plan):
    """Recompute complete segment IDs after deliberate source configuration changes."""
    for item in plan['chunks']:
        chunk = item['metadata']
        replacements = {}
        for segment in chunk['segments']:
            old = segment['id']; segment['id'] = sha(dumps({k: v for k, v in segment.items() if k != 'id'})); replacements[old] = segment['id']
        for e in chunk['entries']: e['segment_id'] = replacements[e['segment_id']]
        chunk['segments'].sort(key=lambda s: s['id'])
        chunk['contexts'].sort(key=lambda c: c['id'])
    return plan


def payload(role, data, geometry=None, dtype='bytes', shape=None, byte_order='not_applicable', encoding=None):
    """Describe explicitly synthesized logical evidence without interpretation defaults."""
    return {'id': role, 'role': role, 'kind': 'materialized', 'block_id': 'placeholder', 'offset': '0', 'length': str(len(data)), 'hash': sha(data), 'encoding_id': encoding or 'org.example.synthetic.' + role, 'dtype': dtype, 'shape': shape or [len(data)], 'byte_order': byte_order, 'layout': 'opaque' if dtype == 'bytes' else 'row_major', 'geometry': geometry, 'unit': None, 'lossiness': 'lossless'}


def regroup(item):
    """Construct uncompressed descriptor groups; write_plan independently verifies them."""
    roles = ['temperature', 'validity', 'native_samples', 'acquisition', 'preview', 'visible', 'module_extension']
    dtype_order = ['u8', 'i8', 'u16', 'i16', 'u32', 'i32', 'u64', 'i64', 'f32', 'f64', 'bytes']
    orders = ['little', 'big', 'not_applicable']; groups = {}
    for e in item['metadata']['entries']:
        for p in e.get('payloads', []):
            if p['kind'] == 'materialized': groups.setdefault((p['role'], p['encoding_id'], p['dtype'], p['byte_order']), []).append(p)
    blocks = []
    for i, (key, members) in enumerate(sorted(groups.items(), key=lambda pair: (roles.index(pair[0][0]), pair[0][1], dtype_order.index(pair[0][2]), orders.index(pair[0][3])))):
        length = 0
        for p in members: p['block_id'] = f'b{i}'; p['offset'] = str(length); length += int(p['length'])
        blocks.append(dict(id=f'b{i}', role=key[0], encoding_id=key[1], dtype=key[2], byte_order=key[3], offset='0', stored=str(length), decoded=str(length), codec=0, hash='0' * 64))
    item['metadata']['blocks'] = blocks


def focused(base):
    """Construct independent edge cases outside the unchanged illustrative metadata."""
    output = []
    zero = plan(base, 0); item = zero['chunks'][0]; obs = item['metadata']['entries'][1]
    obs['measurement']['validity'] = 'no_valid_pixels'
    stats = obs['measurement']['statistics']; stats['valid_pixel_count'] = 0
    for key in ['min', 'max', 'mean', 'min_xy', 'max_xy']: stats.pop(key, None)
    item['payload_recipes']['1:temperature'] = {'kind': 'hex', 'hex': '00' * 24}
    item['payload_recipes']['1:validity'] = {'kind': 'hex', 'hex': '00' * 6}
    obs['presentation'] = {'palette_id': 'inferno', 'range_mode': 'none', 'effective_bounds': None, 'transform': {'rotation_degrees': 0, 'mirror_x': False, 'mirror_y': False}}
    output.append(('zero-valid-mask', zero))

    native = plan(base, 0); item = native['chunks'][0]; stream = item['metadata']['segments'][0]['streams'][0]; obs = item['metadata']['entries'][1]
    native_bytes = bytes.fromhex('8000ffff123400016789abcd')
    descriptor = payload('native_samples', native_bytes, stream['geometry'], 'u16', [2, 3], 'big')
    obs['payloads'] = [descriptor]; obs['measurement'] = {'status': 'unsupported', 'reason': 'synthetic_native_only'}
    obs['retention']['temperature'] = {'status': 'unsupported', 'reason': 'synthetic_native_only'}; obs['retention']['native_samples'] = {'status': 'present'}
    stream['capabilities']['temperature'] = 'unsupported'; stream['capabilities']['native_samples'] = 'supported'
    stream['encodings'] = [{'role': 'native_samples', 'encoding_id': descriptor['encoding_id']}]
    stream['policy'] = {'level': 'native', 'requested_roles': ['native_samples'], 'choice': 'explicit'}
    item['payload_recipes'] = {'1:native_samples': {'kind': 'hex', 'hex': native_bytes.hex()}}; regroup(item)
    output.append(('native-only-big-endian-nonraw14', rehash(native)))
    f64 = deepcopy(native); item64 = f64['chunks'][0]; stream64 = item64['metadata']['segments'][0]['streams'][0]
    f64_bytes = bytes.fromhex('00000000000000800000000000000000010000000000f07fff0000000000f87f000000000000f07f000000000000f0ff')
    p64 = payload('native_samples', f64_bytes, stream64['geometry'], 'f64', [2, 3], 'little', 'org.example.synthetic.ieee64-evidence')
    item64['metadata']['entries'][1]['payloads'] = [p64]; stream64['encodings'] = [{'role': 'native_samples', 'encoding_id': p64['encoding_id']}]
    item64['payload_recipes'] = {'1:native_samples': {'kind': 'hex', 'hex': f64_bytes.hex()}}; regroup(item64)
    output.append(('native-f64-nonfinite-bits', rehash(f64)))

    distinct = plan(base, 0); item = distinct['chunks'][0]; stream = item['metadata']['segments'][0]['streams'][0]; obs = item['metadata']['entries'][1]
    for role, data, dtype, shape, geometry in [('native_samples', native_bytes, 'u16', [2, 3], stream['geometry']), ('acquisition', b'opaque!', 'bytes', [7], None)]:
        desc = payload(role, data, geometry, dtype, shape, 'big' if dtype == 'u16' else 'not_applicable')
        obs['payloads'].append(desc); obs['retention'][role] = {'status': 'present'}; stream['capabilities'][role] = 'supported'
        stream['encodings'].append({'role': role, 'encoding_id': desc['encoding_id']}); item['payload_recipes']['1:' + role] = {'kind': 'hex', 'hex': data.hex()}
    stream['policy'] = {'level': 'full', 'requested_roles': ['acquisition', 'native_samples', 'temperature'], 'choice': 'explicit'}
    regroup(item); output.append(('distinct-native-acquisition', rehash(distinct)))

    change = plan(base, 0); item = change['chunks'][0]; old = item['metadata']['contexts'][0]
    new = deepcopy(old); new['value']['settings'] = {'status': 'known', 'value': {'gain': {'type': 'f32le', 'bits': '0000803f'}}}; new['id'] = sha(dumps(new['value']))
    item['metadata']['contexts'].append(new)
    stream = item['metadata']['segments'][0]['streams'][0]; stream['capabilities']['calibration_settings'] = 'supported'
    obs2 = deepcopy(item['metadata']['entries'][1]); obs2.update(entry_id='2', observation_sequence='1', timeline_ns='1500000', context_id=new['id'])
    obs2['receipt']['stamp']['ticks'] = '1500000'; obs2['retention']['calibration_settings'] = {'status': 'present'}
    end = item['metadata']['entries'][-1]; end['entry_id'] = '3'; end['timeline_ns'] = '2000000'
    item['metadata']['entries'].insert(2, obs2)
    for key, recipe in list(item['payload_recipes'].items()): item['payload_recipes'][key.replace('1:', '2:')] = deepcopy(recipe)
    regroup(item); rehash(change); output.append(('context-change', change))
    multi = deepcopy(change); original = multi['chunks'][0]
    left = deepcopy(original); right = deepcopy(original)
    left['metadata']['entries'] = left['metadata']['entries'][:2]; right['metadata']['entries'] = right['metadata']['entries'][2:]
    left['metadata']['contexts'] = [c for c in left['metadata']['contexts'] if c['id'] == left['metadata']['entries'][1]['context_id']]
    right['metadata']['contexts'] = [c for c in right['metadata']['contexts'] if c['id'] == right['metadata']['entries'][0]['context_id']]
    for part in [left, right]: regroup(part)
    multi['chunks'] = [left, right]; output.append(('multi-chunk-context-closure', multi))

    clock = plan(base, 0); item = clock['chunks'][0]; stream = item['metadata']['segments'][0]['streams'][0]
    clock_id = '00000000-0000-4000-8000-000000000777'
    stream['clocks'].append({'id': clock_id, 'kind': 'sensor_counter', 'unit': 'ticks', 'tick_period_ns': None, 'resolution_ticks': '1', 'quality': 'unreliable'})
    item['metadata']['entries'][1]['acquisition'] = {'status': 'unreliable', 'reason': 'synthetic_unknown_tick_period', 'stamp': {'clock_domain_id': clock_id, 'ticks': '42'}, 'utc': {'status': 'unknown', 'clock_source': 'unknown', 'reason': 'synthetic_no_anchor'}}
    output.append(('qualified-sensor-clock', rehash(clock)))

    periodic = plan(base, 0); item = periodic['chunks'][0]; stream = item['metadata']['segments'][0]['streams'][0]
    stream['sampling'] = {'mode': 'periodic', 'period_ns': '40000000', 'origin_timeline_ns': '1000000'}
    item['metadata']['entries'][1]['scheduled'] = {'first_slot': '0', 'last_slot': '0', 'first_deadline_ns': '1000000', 'last_deadline_ns': '1000000'}
    output.append(('periodic-sampling', rehash(periodic)))
    empty = plan(base, 0); empty['chunks'] = []; output.append(('empty-recording', empty))
    return output


def records(raw):
    """Locate generator-owned complete envelopes before intentional negative mutation."""
    cursor = 0; output = []
    while cursor < len(raw):
        values = HEADER.unpack_from(raw, cursor); length = values[5] + 112
        output.append({'offset': cursor, 'length': length, 'kind': values[2], 'ordinal': values[4], 'previous': values[6], 'body': raw[cursor + 40: cursor + length - 72]}); cursor += length
    return output


def negative_fixtures(raw, head, view_plan):
    """Intentional malformed bytes only; never called by the reference writer."""
    recs = records(raw); header_rec = recs[0]; chunk_rec = next(r for r in recs if r['kind'] == 2); end = recs[-1]
    prefix = raw[:header_rec['length']]
    def raw_chunk(body): return prefix + encode_record(2, body, 1, 0)
    out = []
    out.append(('missing-end', raw[:end['offset']], True, 'recovered'))
    out.append(('torn-end', raw[:-13], True, 'recovered'))
    out.append(('torn-chunk', raw[:chunk_rec['offset'] + chunk_rec['length'] // 2], True, 'recovered'))
    damaged = bytearray(raw_chunk(chunk_rec['body'])); damaged[header_rec['length'] + 45] ^= 1
    out.append(('committed-body-corrupt', bytes(damaged), True, 'committed_payload_integrity'))
    torn = bytearray(raw_chunk(chunk_rec['body'])); torn[-8] ^= 1
    out.append(('uncommitted-footer-crc', bytes(torn), True, 'recovered'))
    malformed = bytearray(chunk_rec['body']); malformed[4] = 0xff
    out.append(('malformed-committed-json', raw_chunk(bytes(malformed)), True, 'invalid_metadata'))
    hostile = bytearray(raw); struct.pack_into('<Q', hostile, 24, 68157440)
    out.append(('hostile-header-length', bytes(hostile), False, 'resource_limit'))
    unknown = deepcopy(head); unknown['required_features'] = sorted(unknown['required_features'] + ['org.example.unsupported-v1'])
    out.append(('unknown-required-feature', encode_record(1, dumps(unknown), 0, 2**63 - 1), False, 'unsupported_required_feature'))
    for kind, name in [(3, 'committed-index-damage'), (4, 'committed-checkpoint-damage')]:
        target = next(r for r in recs if r['kind'] == kind); data = bytearray(raw); data[target['offset'] + 40] ^= 1
        out.append((name, bytes(data), False, 'invalid_container'))
    view_metadata, view_body, _ = prepare_chunk(view_plan['chunks'][0], view_plan['header'])
    native = next(p for e in view_metadata['entries'] for p in e.get('payloads', []) if p['kind'] == 'view')
    native['offset'] = '999999999'; encoded = dumps(view_metadata)
    old_len = struct.unpack_from('<I', view_body)[0]
    body = struct.pack('<I', len(encoded)) + encoded + view_body[4 + old_len:]
    view_header = encode_record(1, dumps(view_plan['header']), 0, 2**63 - 1)
    out.append(('hostile-view-out-of-parent', view_header + encode_record(2, body, 1, 0), True, 'invalid_payload'))
    # Structured mutations retain valid body/block/logical SHA layers as needed so the
    # intended semantic error cannot be accidentally hidden by an earlier checksum.
    def edited_chunk(mutator, edit_data=False):
        metadata_len = struct.unpack_from('<I', chunk_rec['body'])[0]
        metadata = loads(chunk_rec['body'][4:4 + metadata_len]); data = bytearray(chunk_rec['body'][4 + metadata_len:])
        mutator(metadata, data)
        encoded = dumps(metadata)
        return raw_chunk(struct.pack('<I', len(encoded)) + encoded + data)
    def bad_logical(metadata, data): metadata['entries'][1]['payloads'][0]['hash'] = '0' * 64
    out.append(('committed-logical-hash-corrupt', edited_chunk(bad_logical), True, 'committed_payload_integrity'))
    def changed_payload(metadata, data, role, relative, replacement):
        payload = next(p for p in metadata['entries'][1]['payloads'] if p['role'] == role)
        block = next(b for b in metadata['blocks'] if b['id'] == payload['block_id'])
        start = int(block['offset']) + int(payload['offset']); length = int(payload['length'])
        data[start + relative:start + relative + len(replacement)] = replacement
        payload['hash'] = sha(data[start:start + length])
        offset = int(block['offset']); block['hash'] = sha(data[offset:offset + int(block['decoded'])])
    out.append(('nonbinary-validity-mask', edited_chunk(lambda m, d: changed_payload(m, d, 'validity', 0, b'\x02')), True, 'invalid_payload'))
    out.append(('nonfinite-valid-temperature', edited_chunk(lambda m, d: changed_payload(m, d, 'temperature', 0, bytes.fromhex('0000c07f'))), True, 'invalid_payload'))
    out.append(('negative-zero-invalid-filler', edited_chunk(lambda m, d: changed_payload(m, d, 'temperature', 12, bytes.fromhex('00000080'))), True, 'invalid_payload'))
    def bad_union(metadata, data): metadata['entries'][0]['payloads'] = []
    out.append(('forbidden-union-field', edited_chunk(bad_union), True, 'invalid_metadata'))
    def reserved_codec(metadata, data): metadata['blocks'][0]['codec'] = 2
    out.append(('reserved-zstd-codec', edited_chunk(reserved_codec), True, 'unsupported_codec'))
    # A valid committed envelope promises its hashes but contains a changed decoded byte.
    metadata = loads(chunk_rec['body'][4:4 + struct.unpack_from('<I', chunk_rec['body'])[0]])
    encoded = dumps(metadata); body = bytearray(chunk_rec['body']); body[4 + len(encoded)] ^= 1
    out.append(('committed-decoded-byte-corrupt', raw_chunk(bytes(body)), True, 'committed_payload_integrity'))
    return out


def outcome(path, recovery):
    """Run the independent full reader and retain precise rejection/recovery classes."""
    try:
        with Reader(path) as reader: result = reader.check(full=True, recover=recovery)
        return result['status'], result
    except ContractError as error: return error.code, {'status': 'error', 'error': error.code, 'integrity_scope': 'none'}


def generate(destination):
    """Validate a complete isolated corpus and record immutable byte/provenance facts."""
    illustrations = loads((ROOT / 'docs/lmtr-r1b/examples.json').read_bytes())
    examples = {c['id']: c for c in illustrations['cases']}
    destination.mkdir(parents=True, exist_ok=True); (destination / 'files').mkdir(); (destination / 'plans').mkdir()
    inventory = []
    def positive(name, p):
        file = destination / 'files' / (name + '.lmtr'); plan_file = destination / 'plans' / (name + '.json')
        plan_file.write_bytes(dumps(p)); result = write_plan(p, file)
        observed, checked = outcome(file, False); assert observed == 'valid', (name, observed)
        raw = file.read_bytes()
        inventory.append({'id': name, 'file': 'files/' + file.name, 'plan': 'plans/' + plan_file.name, 'classification': 'positive', 'bytes': len(raw), 'sha256': sha(raw), 'plan_sha256': sha(plan_file.read_bytes()), 'expected': checked, 'provenance': 'synthetic simulated evidence; no physical capture or calibrated temperatures'})
        return raw
    for code in ['01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '14']:
        for codec in [0, 1]: positive('illustration-' + code + ('-stored' if codec == 0 else '-deflate1'), plan(examples[code], codec))
    for name, p in focused(examples['01']): positive(name, p)
    frozen_parent = (destination / 'files/illustration-01-stored.lmtr').read_bytes()
    for operation, suffix in [('continuation', '501'), ('reprocessing', '502')]:
        child = plan(examples['01'], 0)
        old_id = child['header']['recording_id']; new_id = '00000000-0000-4000-8000-000000000' + suffix
        child['header']['recording_id'] = new_id
        child['header']['lineage'] = {'operation': operation, 'parents': [{'format_id': 'lmthermal-recording', 'recording_id': old_id, 'file_sha256': sha(frozen_parent), 'file_bytes': str(len(frozen_parent))}], 'algorithm': {'status': 'known', 'value': {'id': 'org.example.synthetic.copy', 'version': '1'}}}
        for item in child['chunks']:
            item['metadata']['recording_id'] = new_id
            for e in item['metadata']['entries']:
                if e['kind'] == 'observation' and operation == 'reprocessing':
                    stream = item['metadata']['segments'][0]['streams'][0]
                    stream['capabilities']['calibration_settings'] = 'supported'
                    context = item['metadata']['contexts'][0]
                    context['value']['algorithm'] = {'status': 'known', 'value': {'id': 'org.example.synthetic.copy', 'version': '1'}}
                    context['value']['settings'] = {'status': 'known', 'value': {'operation': 'exact_synthetic_copy', 'parameters': {}}}
                    context['id'] = sha(dumps(context['value'])); e['context_id'] = context['id']
                    e['retention']['calibration_settings'] = {'status': 'present'}
                    e['parent_entry'] = {'parent_index': 0, 'entry_id': '1'}
                    e['measurement']['provenance']['kind'] = 'derived'
                    e['measurement']['provenance']['algorithm'] = {'id': 'org.example.synthetic.copy', 'version': '1'}
        positive('frozen-single-file-' + operation, rehash(child))
    assert frozen_parent == (destination / 'files/illustration-01-stored.lmtr').read_bytes(), 'immutable parent changed'
    base_raw = (destination / 'files/illustration-01-stored.lmtr').read_bytes()
    for name, raw, recovery, expected in negative_fixtures(base_raw, examples['01']['header'], plan(examples['04'], 0)):
        file = destination / 'files' / (name + '.lmtr'); file.write_bytes(raw)
        observed, result = outcome(file, recovery); assert observed == expected, (name, observed, expected)
        inventory.append({'id': name, 'file': 'files/' + file.name, 'classification': 'negative', 'bytes': len(raw), 'sha256': sha(raw), 'recover': recovery, 'expected': result, 'provenance': 'intentional raw corruption of synthetic accepted fixtures; not a writer-supported plan'})
    manifest = {'format': 'lmtr-r3-corpus', 'version': 1, 'contract': 'docs/LMTR_WIRE_V1.md', 'contract_sha256': sha((ROOT / 'docs/LMTR_WIRE_V1.md').read_bytes()), 'generator_sha256': sha(Path(__file__).read_bytes()), 'implementation_source_sha256': {name: sha((ROOT / 'tools/lmtr_v1' / name).read_bytes()) for name in ['container.py', 'jsoncodec.py', 'metadata.py']}, 'encoder': {'implementation': 'independent Python reference writer', 'python': platform.python_version(), 'zlib_runtime': zlib.ZLIB_RUNTIME_VERSION, 'deflate_level': 1}, 'scope': 'synthetic canonical contract fixtures; independent Kotlin read/write verification required', 'physical_accuracy_warning': WARNING, 'cases': inventory, 'total_bytes': sum(c['bytes'] for c in inventory), 'pending': ['real Windows cross-language execution', 'indefinite-runtime memory/queue/timer admission', 'backend fsync/power-loss certification', 'production camera integration']}
    (destination / 'manifest.json').write_bytes(dumps(manifest))
    return manifest


def main():
    """Publish validated owned corpus bytes or prove deterministic regeneration."""
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--check', action='store_true', help='Rebuild in isolation and require exact fixture/plan/manifest bytes'); args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='lmtr-corpus-') as directory:
        generated = Path(directory) / 'lmtr'; manifest = generate(generated)
        if args.check:
            expected = {p.relative_to(generated) for p in generated.rglob('*') if p.is_file()}
            existing = {p.relative_to(DEST) for p in DEST.rglob('*') if p.is_file()}
            assert expected == existing, 'corpus membership differs'
            assert all((generated / p).read_bytes() == (DEST / p).read_bytes() for p in expected), 'corpus byte drift'
        else:
            DEST.mkdir(parents=True, exist_ok=True)
            for name in ['plans', 'files']:
                if (DEST / name).exists(): shutil.rmtree(DEST / name)
                shutil.copytree(generated / name, DEST / name)
            shutil.copyfile(generated / 'manifest.json', DEST / 'manifest.json')
        print(json.dumps({'cases': len(manifest['cases']), 'total_bytes': manifest['total_bytes'], 'deterministic_check': args.check}, sort_keys=True))

if __name__ == '__main__': main()
