#!/usr/bin/env python3
"""Check documentation-only LMTR draft schemas and synthetic metadata recipes.

This is not an LMTR reader/writer or R3 contract primitive. It never emits a binary
container, accesses a camera/device, or changes source recordings. Local shape and
selected cross-field checks supplement the complete proposed prose, not replace it.
"""
from copy import deepcopy
from decimal import Decimal
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import math
import re
import struct
import subprocess
import zlib

ROOT = Path(__file__).resolve().parents[1]
LIMIT = (1 << 63) - 1
ROLES = ['temperature', 'validity', 'native_samples', 'acquisition', 'preview', 'visible', 'module_extension']
WIDTHS = {'u8': 1, 'i8': 1, 'u16': 2, 'i16': 2, 'u32': 4, 'i32': 4, 'f32': 4, 'u64': 8, 'i64': 8, 'f64': 8}
CORE = 'org.lmthermal.lmtr.core-v1'
VIEW = 'org.lmthermal.lmtr.contiguous-native-view-v1'


def parse(text):
    """Reject duplicate/nonfinite JSON while retaining exact optional decimal values."""
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('duplicate_key')
            result[key] = value
        return result
    def invalid(token):
        raise ValueError('nonfinite_number:' + token)
    def decimal(token):
        value = Decimal(token)
        if len(token) > 1024 or len(value.as_tuple().digits) > 1024 or value and not -4096 <= value.adjusted() <= 4096:
            raise ValueError('decimal_limit')
        return value
    def integer(token):
        if len(token) > 1024: raise ValueError('integer_token_limit')
        return int(token)
    return json.loads(text, parse_float=decimal, parse_int=integer, object_pairs_hook=pairs, parse_constant=invalid)


def canonical_json(value):
    """Exercise the proposed exact-decimal spelling on documentation metadata only."""
    if value is None: return 'null'
    if value is True: return 'true'
    if value is False: return 'false'
    if isinstance(value, str):
        return '"' + ''.join('\\"' if c == '"' else '\\\\' if c == '\\' else '\\u%04x' % ord(c) if ord(c) < 32 else c for c in value) + '"'
    if isinstance(value, (int, Decimal, float)):
        number = Decimal(str(value))
        if not number.is_finite(): raise ValueError('nonfinite_number')
        if not number: return '0'
        sign, digits, exponent = number.as_tuple()
        digits = list(digits)
        while digits[-1] == 0:
            digits.pop(); exponent += 1
        coefficient = ''.join(map(str, digits)); adjusted = len(digits) + exponent - 1
        prefix = '-' if sign else ''
        if -6 <= adjusted <= 20:
            point = len(coefficient) + exponent
            if point <= 0: body = '0.' + '0' * (-point) + coefficient
            elif point >= len(coefficient): body = coefficient + '0' * (point - len(coefficient))
            else: body = coefficient[:point] + '.' + coefficient[point:]
        else:
            body = coefficient[0] + ('.' + coefficient[1:] if len(coefficient) > 1 else '') + 'e' + str(adjusted)
        return prefix + body
    if isinstance(value, list): return '[' + ','.join(map(canonical_json, value)) + ']'
    if isinstance(value, dict):
        return '{' + ','.join(canonical_json(k) + ':' + canonical_json(value[k]) for k in sorted(value)) + '}'
    raise ValueError('not_json_value')


def sha(data): return hashlib.sha256(data).hexdigest()


def validate(schema, value, definitions, path='$'):
    """Validate exactly the declared local-schema keyword subset, failing on omissions."""
    if '$ref' in schema:
        name = schema['$ref'].removeprefix('#/$defs/')
        validate(definitions[name], value, definitions, path)
        if name in ['D', 'PositiveD'] and int(value) > LIMIT: raise ValueError(path + ':resource_limit')
        if name == 'UUID' and value == '00000000-0000-0000-0000-000000000000': raise ValueError(path + ':nil_uuid')
        if name in ['Id', 'Namespace'] and '..' in value: raise ValueError(path + ':invalid_id')
        if name == 'Utc':
            try: datetime.datetime.fromisoformat(value.replace('Z', '+00:00'))
            except ValueError as error: raise ValueError(path + ':calendar') from error
        siblings = {k:v for k,v in schema.items() if k != '$ref'}
        if siblings: validate(siblings, value, definitions, path)
        return
    for mode in ['oneOf', 'anyOf']:
        if mode in schema:
            hits = 0
            for choice in schema[mode]:
                try: validate(choice, value, definitions, path); hits += 1
                except ValueError: pass
            invalid = hits != 1 if mode == 'oneOf' else hits == 0
            if invalid: raise ValueError(path + ':union')
    for part in schema.get('allOf', []): validate(part, value, definitions, path)
    if 'not' in schema:
        try: validate(schema['not'], value, definitions, path)
        except ValueError: pass
        else: raise ValueError(path + ':forbidden_known_field')
    if 'enum' in schema and not any(type(value) is type(v) and value == v for v in schema['enum']): raise ValueError(path + ':enum')
    kind = schema.get('type')
    checks = {'object': isinstance(value, dict), 'array': isinstance(value, list), 'string': isinstance(value, str),
              'integer': type(value) is int, 'number': type(value) is int or isinstance(value, (float, Decimal)),
              'boolean': type(value) is bool, 'null': value is None}
    if kind and not checks[kind]: raise ValueError(path + ':type')
    if isinstance(value, dict):
        if any(k not in value for k in schema.get('required', [])): raise ValueError(path + ':required')
        if len(value) > schema.get('maxProperties', LIMIT): raise ValueError(path + ':properties')
        for key, item in value.items():
            if 'propertyNames' in schema: validate(schema['propertyNames'], key, definitions, path)
            prop = schema.get('properties', {}).get(key, schema.get('additionalProperties', True))
            if prop is False: raise ValueError(path + ':additional_property')
            if isinstance(prop, dict): validate(prop, item, definitions, path + '.' + key)
    elif isinstance(value, list):
        if not schema.get('minItems', 0) <= len(value) <= schema.get('maxItems', LIMIT): raise ValueError(path + ':array_limit')
        if schema.get('uniqueItems') and len({canonical_json(x) for x in value}) != len(value): raise ValueError(path + ':duplicates')
        for item in value:
            if 'items' in schema: validate(schema['items'], item, definitions, path + '[]')
    elif isinstance(value, str):
        if not schema.get('minLength', 0) <= len(value) <= schema.get('maxLength', LIMIT): raise ValueError(path + ':string_limit')
        if 'pattern' in schema and not re.search(schema['pattern'], value): raise ValueError(path + ':pattern')
    elif type(value) is int or isinstance(value, (float, Decimal)):
        if not schema.get('minimum', -math.inf) <= value <= schema.get('maximum', math.inf): raise ValueError(path + ':number_limit')


def json_limits(value, depth=0):
    """Bound exact optional values; do not use Float64 to validate preserved decimals."""
    if isinstance(value, (dict, list)):
        depth += 1
        if depth > 32: raise ValueError('json_depth')
        children = list(value.values()) if isinstance(value, dict) else value
        count = len(value)
        for child in children: count += json_limits(child, depth)
        if isinstance(value, dict):
            for key in value: json_limits(key, depth)
        if count > 65536: raise ValueError('json_items')
        return count
    if isinstance(value, str):
        if len(value.encode('utf-8')) > 16384: raise ValueError('json_string')
        if any(0xd800 <= ord(c) <= 0xdfff for c in value): raise ValueError('surrogate')
    elif type(value) is int or isinstance(value, Decimal):
        value = Decimal(value)
        if not value.is_finite() or len(value.as_tuple().digits) > 1024 or value and not -4096 <= value.adjusted() <= 4096:
            raise ValueError('decimal_limit')
    return 0


def recipe_bytes(recipe):
    """Materialize small synthetic logical-byte recipes in memory, never an archive."""
    kind = recipe['kind']
    if kind == 'f32_values': return b''.join(struct.pack('<f', float(x)) for x in recipe['values'])
    if kind == 'f32_constant': return struct.pack('<f', float(recipe['value'])) * recipe['count']
    if kind == 'u16_constant': return struct.pack('<H', recipe['value']) * recipe['count']
    if kind == 'hex': return bytes.fromhex(recipe['hex'])
    if kind == 'transport': return recipe_bytes(recipe['native']) + bytes(recipe['trailer_bytes'])
    raise ValueError('unknown_documentation_recipe')


def check_case(case, definitions):
    """Check local shapes and selected complete metadata relationships for each scenario."""
    header = case['header']; chunk = case['chunk']
    validate({'$ref': '#/$defs/Header'}, header, definitions)
    validate({'$ref': '#/$defs/Chunk'}, chunk, definitions)
    for item in [header, chunk]: json_limits(item)
    if len(canonical_json(header).encode()) > 1048576: raise ValueError('header_limit')
    if chunk['recording_id'] != header['recording_id']: raise ValueError('recording_id')
    if CORE not in header['required_features'] or CORE not in chunk['required_features']: raise ValueError('core_feature')
    if set(header['required_features']) - {CORE, VIEW}: raise ValueError('unsupported_required_feature')
    if not set(chunk['required_features']) <= set(header['required_features']): raise ValueError('undeclared_feature')
    if header['required_features'] != sorted(set(header['required_features'])): raise ValueError('feature_order')
    contexts = {c['id']: c['value'] for c in chunk['contexts']}
    for digest, value in contexts.items():
        if sha(canonical_json(value).encode()) != digest: raise ValueError('context_hash')
    segments = {s['id']: s for s in chunk['segments']}
    for segment in segments.values():
        if sha(canonical_json({k:v for k,v in segment.items() if k != 'id'}).encode()) != segment['id']:
            raise ValueError('segment_hash')
    physical = {}; decoded_slices = {}; physical_roles = {}; active = None; last_time = -1; observed = {}
    for entry in chunk['entries']:
        if int(entry['entry_id']) != chunk['entries'].index(entry): raise ValueError('entry_order')
        if int(entry['timeline_ns']) < last_time: raise ValueError('timeline_order')
        last_time = int(entry['timeline_ns']); segment = segments[entry['segment_id']]
        if entry['kind'] == 'segment_start':
            if active is not None: raise ValueError('overlapping_segments')
            if segment['start_entry'] != entry['entry_id'] or segment['start_timeline_ns'] != entry['timeline_ns']: raise ValueError('segment_start')
            active = entry['segment_id']; continue
        if entry['segment_id'] != active: raise ValueError('inactive_segment')
        if entry['kind'] == 'segment_end': active = None; continue
        config = next(c for c in segment['streams'] if c['stream_id'] == entry['stream_id'])
        if entry['kind'] == 'gap':
            pairs = [(entry[k+'_first'], entry[k+'_last']) for k in ['device', 'observation']]
            for first, last in pairs:
                if (first is None) != (last is None): raise ValueError('gap_pair')
                if first is not None and int(last) - int(first) + 1 != int(entry['count']): raise ValueError('gap_count')
            continue
        key = (entry['segment_id'], entry['stream_id']); sequence = int(entry['observation_sequence'])
        if sequence <= observed.get(key, -1): raise ValueError('observation_order')
        observed[key] = sequence
        if contexts[entry['context_id']]['source'] != config['source']: raise ValueError('context_source')
        descriptors = {p['id']: p for p in entry['payloads']}; data = {}
        if len(descriptors) != len(entry['payloads']): raise ValueError('payload_duplicate')
        for payload in entry['payloads']:
            pkey = entry['entry_id'] + ':' + payload['id']
            raw = recipe_bytes(case['payload_recipes'][pkey]); data[payload['id']] = raw
            if len(raw) != int(payload['length']) or sha(raw) != payload['hash']: raise ValueError('logical_integrity')
            if payload['dtype'] == 'bytes': expected = payload['shape'][0]
            else: expected = math.prod(payload['shape']) * WIDTHS[payload['dtype']]
            if expected != len(raw): raise ValueError('shape_length')
            if payload['geometry'] is not None and math.prod([payload['geometry']['width_px'], payload['geometry']['height_px']]) > 4194304: raise ValueError('pixel_limit')
            if payload['encoding_id'] == 'org.lmthermal.ht301.raw14':
                if payload['shape'] != [288,384] or payload['dtype'] != 'u16' or payload['byte_order'] != 'little': raise ValueError('ht_encoding')
                if any(word[0] >= 0x4000 for word in struct.iter_unpack('<H',raw)): raise ValueError('ht_not_raw14')
            if payload['kind'] == 'view':
                if VIEW not in chunk['required_features']: raise ValueError('view_feature')
                parent = descriptors[payload['parent_payload_id']]
                if parent['kind'] != 'materialized' or parent['role'] != 'acquisition' or payload['role'] != 'native_samples': raise ValueError('view_parent')
                parent_raw = recipe_bytes(case['payload_recipes'][entry['entry_id'] + ':' + parent['id']])
                off = int(payload['offset'])
                if off + len(raw) > len(parent_raw) or parent_raw[off:off+len(raw)] != raw: raise ValueError('view_slice')
            else:
                block = payload['block_id']; off = int(payload['offset'])
                physical.setdefault(block, []).append((off, raw)); physical_roles[block] = payload['role']
        measurement = entry['measurement']; temperature = data.get('temperature'); mask = data.get('validity')
        if measurement['status'] != 'available':
            if temperature is not None or mask is not None: raise ValueError('unavailable_temperature')
        else:
            if measurement['temperature_payload_id'] != 'temperature' or temperature is None: raise ValueError('missing_temperature')
            count = config['geometry']['width_px'] * config['geometry']['height_px']
            if len(temperature) != count*4: raise ValueError('full_grid')
            if mask is None: mask = bytes([1])*count
            if len(mask) != count or any(x not in [0,1] for x in mask): raise ValueError('mask')
            values = struct.unpack('<' + str(count) + 'f', temperature)
            for i, v in enumerate(values):
                if mask[i] and not math.isfinite(v): raise ValueError('nonfinite_temperature')
                if not mask[i] and temperature[i*4:i*4+4] != bytes(4): raise ValueError('invalid_filler')
            validity = 'all_valid' if all(mask) else 'partially_valid' if any(mask) else 'no_valid_pixels'
            if measurement['validity'] != validity: raise ValueError('validity_summary')
            stats = measurement.get('statistics')
            if stats:
                valid_indices = [i for i in range(count) if mask[i]]
                if stats['pixel_count'] != count or stats['valid_pixel_count'] != len(valid_indices): raise ValueError('statistics_counts')
                if valid_indices:
                    lo = min(valid_indices, key=lambda i:values[i]); hi = max(valid_indices, key=lambda i:values[i])
                    total = 0.0
                    for i in valid_indices: total += values[i]
                    for field, actual in [('min',values[lo]),('max',values[hi]),('mean',total/len(valid_indices))]:
                        if abs(float(stats[field])-actual) > max(1e-4,1e-6*abs(actual)): raise ValueError('statistics_value')
                    width = config['geometry']['width_px']
                    if stats['min_xy'] != [lo%width,lo//width] or stats['max_xy'] != [hi%width,hi//width]: raise ValueError('statistics_location')
                elif any(k in stats for k in ['min','max','mean','min_xy','max_xy']): raise ValueError('zero_valid_statistics')
        for role, available in entry['retention'].items():
            if role == 'calibration_settings': continue
            if (available['status'] == 'present') != (role in data): raise ValueError('availability')
    if case['record_plan'][-1] == 'END' and active is not None: raise ValueError('unclosed_segment')
    position = 0; decoded_total = 0
    for block in chunk['blocks']:
        parts = physical.get(block['id'], []); off = 0
        for start, raw in parts:
            if start != off: raise ValueError('block_slice_coverage')
            off += len(raw)
        raw = b''.join(x[1] for x in parts)
        if physical_roles[block['id']] != block['role'] or int(block['decoded']) != len(raw) or sha(raw) != block['hash']: raise ValueError('block_integrity')
        if block['codec'] != 0 or int(block['stored']) != len(raw): raise ValueError('illustrative_stored_codec')
        if int(block['offset']) != position: raise ValueError('stored_offset')
        position += len(raw); decoded_total += len(raw)
    metadata_len = len(canonical_json(chunk).encode())
    if metadata_len > 1048576 or decoded_total + metadata_len + 4 > 67108864 or position + metadata_len + 4 + 112 > 68157440: raise ValueError('chunk_limit')
    if case.get('damage'):
        damage = case['damage']; raw = bytearray(recipe_bytes(case['payload_recipes'][damage['entry_id']+':'+damage['payload_id']]))
        raw[int(damage['byte_offset'])] ^= damage['xor']
        expected = next(p['hash'] for e in chunk['entries'] if e['entry_id'] == damage['entry_id'] for p in e['payloads'] if p['id'] == damage['payload_id'])
        if sha(raw) != expected: raise ValueError('committed_payload_integrity')
    return 'recovered_missing_end' if case['record_plan'][-1] != 'END' else 'accept_metadata'


def main():
    """Record reproducible host checks and their explicitly limited validation scope."""
    parser = argparse.ArgumentParser(); parser.add_argument('--report', type=Path); args = parser.parse_args()
    schema = parse((ROOT/'docs/lmtr-r1b/schema.json').read_text()); examples = parse((ROOT/'docs/lmtr-r1b/examples.json').read_text())
    definitions = schema['$defs']; refs = re.findall(r'"\$ref"\s*:\s*"#\/\$defs\/([^\"]+)"', json.dumps(schema))
    assert all(name in definitions for name in refs)
    supported = {'$ref','type','pattern','maxLength','minLength','minimum','maximum','enum','oneOf','anyOf','allOf','not','properties','required','additionalProperties','items','minItems','maxItems','uniqueItems','maxProperties','propertyNames','description'}
    def schema_keywords(node):
        assert set(node) <= supported, set(node)-supported
        for key, value in node.items():
            if key == 'properties':
                for child in value.values(): schema_keywords(child)
            elif key in ['items','additionalProperties','propertyNames','not'] and isinstance(value,dict): schema_keywords(value)
            elif key in ['oneOf','anyOf','allOf']:
                for child in value: schema_keywords(child)
    for definition in definitions.values(): schema_keywords(definition)
    assert examples['status'].startswith('NONCANONICAL')
    cases = []
    for case in examples['cases']:
        try: outcome = check_case(case, definitions)
        except ValueError as error: outcome = str(error)
        assert outcome == case['expected'], (case['id'], outcome, case['expected'])
        cases.append({'id':case['id'],'outcome':outcome,'passed':True})
    # Mutations exercise independent semantic invariants, not canonical fixture production.
    semantic_negatives = []
    for case_index, mutate in [
        (0, lambda c:c['chunk']['entries'][1]['measurement']['statistics'].update(mean=21)),
        (0, lambda c:c['chunk']['contexts'][0]['value']['source'].update(model_id='changed')),
        (0, lambda c:c['chunk']['entries'][2].update(timeline_ns='0')),
        (3, lambda c:next(p for p in c['chunk']['entries'][1]['payloads'] if p['kind']=='view').update(offset='1')),
        (3, lambda c:next(p for p in c['chunk']['entries'][1]['payloads'] if p['kind']=='view').update(parent_payload_id='native_samples')),
        (3, lambda c:c['chunk'].update(required_features=[CORE])),
        (7, lambda c:c['chunk']['segments'][0].update(start_entry='7')),
    ]:
        bad = deepcopy(examples['cases'][case_index]); mutate(bad)
        try: check_case(bad, definitions)
        except ValueError as error: semantic_negatives.append(str(error))
        else: raise AssertionError('semantic mutation accepted')
    numeric = {'1.0' :'1','1e0':'1','-0.0':'0','0.0000001':'1e-7','1230000000000000000000':'1.23e21','1.0000000000000000000000000000000000001':'1.0000000000000000000000000000000000001'}
    for token, expected in numeric.items(): assert canonical_json(Decimal(token)) == expected
    unicode_order = canonical_json({'\U0001f600':1,'\ue000':2})
    assert unicode_order.index('\ue000') < unicode_order.index('\U0001f600')
    # Float32 sign is a binary fact; canonical JSON -0 is intentionally semantic zero.
    first = recipe_bytes(examples['cases'][0]['payload_recipes']['1:temperature'])
    assert first[8:12] == bytes.fromhex('00000080') and first[12:16] == bytes(4)
    assert struct.pack('<f', -0.0) == bytes.fromhex('00000080') and struct.pack('<f', 0.0) == bytes(4)
    assert struct.calcsize('<8sHHIQQQ') == 40 and struct.calcsize('<8sQQQ32sII') == 72
    assert zlib.crc32(b'123456789') == 0xcbf43926
    # Deliberately malformed documentation metadata must be rejected without a file/device.
    failures = 0
    for value in [True, Decimal('1.0'), -1, 2147483648]:
        try: validate({'$ref':'#/$defs/I'}, value, definitions)
        except ValueError: failures += 1
    for value in ['01','-1',str(1<<63)]:
        try: validate({'$ref':'#/$defs/D'}, value, definitions)
        except ValueError: failures += 1
    assert failures == 7
    for text in ['{"x":1,"x":2}', '{"x":NaN}']:
        try: parse(text)
        except ValueError: failures += 1
        else: raise AssertionError('bad JSON accepted')
    assert failures == 9
    result={'status':'DRAFT_HOST_CHECKS_PASS_NOT_R3_OR_OWNER_ACCEPTANCE','review':'r1b-review-1','schema_definitions':len(definitions),'schema_refs_resolve':len(refs),'scenarios':cases,'decimal_spelling_checks':len(numeric),'negative_type_json_checks':failures,'semantic_negative_checks':semantic_negatives,'framing_widths':{'header':40,'footer':72},'ieee_crc32_known_check':True,'float32_signed_zero_check':True,'unicode_scalar_key_order_check':True,'binary_container_written':False,'device_or_camera_accessed':False,'unexecuted':['Kotlin/Python production implementations','canonical R3 binary corpus','real Windows new-wire validation','multi-stream camera hardware','idle timer/Stop/backend races','new grammar sustained performance or peak-memory proof'],'scope':'Documentation local-shape checks and selected cross-field/byte recipes, not full LMTR/R3 conformance; prose binary/index/recovery/storage rules require future implementation validation.'}
    result['inputs_sha256'] = {p:sha((ROOT/p).read_bytes()) for p in [
        'docs/LMTR_WIRE_V1_DRAFT.md','docs/LMTR_R1B_OWNER_DECISIONS.md',
        'docs/lmtr-r1b/schema.json','docs/lmtr-r1b/examples.json','tools/check_lmtr_draft.py']}
    result['base_revision'] = 'ed0e3338a72bba76a267e4701125f6f89b3f93e3'
    if args.report: args.report.write_text(json.dumps(result,indent=2)+'\n')
    print('Draft host checks pass:',len(cases),'scenarios;',len(definitions),'schema definitions;',len(numeric),'exact-decimal checks;',failures + len(semantic_negatives),'negative checks. No binary fixture/device.')

if __name__ == '__main__': main()
