#!/usr/bin/env python3
"""Independent bounded reader for field scalar diagnostics, never an LMTR/Android policy.

Domain brackets remain separate. No unique-owner total, RSS decomposition,
post-GC floor or safety verdict is manufactured from these samples.
"""
import argparse
import hashlib
import json
from pathlib import Path

FIELDS = {
    'GC_BEFORE': ['allocated', 'freed', 'count', 'time_ms'],
    'JAVA': ['maximum', 'occupied', 'headroom'],
    'RSS': ['proc_vm_rss'],
    'ANDROID_MEMORY': ['total_pss', 'summary_java', 'summary_native', 'summary_graphics', 'summary_stack', 'summary_code', 'summary_private_other', 'summary_system'],
    'NATIVE_ALLOCATOR': ['allocated', 'free', 'heap_size'],
    'PUBLISHED_ROLES': ['source_generation_before', 'source_generation_after', 'source_sequence', 'rendered_sequence', 'roi_sequence',
        'source_is_rendered', 'source_is_roi', 'rendered_is_roi', 'bitmap_allocation', 'legend_allocation', 'nominal_celsius_plane',
        'received', 'replaced', 'malformed', 'active_event_sequence', 'pending_event_sequence'],
    'RECORDER': ['run_origin', 'state', 'accepted', 'acknowledged', 'preparation_drops', 'writer_drops', 'preparation_queued',
        'preparation_in_flight', 'writer_queued', 'writer_in_flight', 'pending', 'journal', 'chunks', 'checkpoints'],
    'NATIVE_CAPACITY': ['transfer_count', 'transfer_capacity', 'assembly_capacity', 'delivered_capacity', 'latest_size', 'latest_capacity'],
    'GC_AFTER': ['allocated', 'freed', 'count', 'time_ms'],
}
STATUSES = ['OK', 'PARTIAL', 'UNSUPPORTED', 'FAILED', 'CANCELLED', 'CLOCK_INVALID', 'SOURCE_CHANGED']
VALUES = 16
STRIDE = 19
WIDTH = 6 + len(FIELDS) * STRIDE
PRIMITIVE_BYTES = (64 * WIDTH + WIDTH + VALUES) * 8


def counter(value):
    """Exact nonnegative signed-64 decimal strings; null is an explicit unknown."""
    if value is None:
        return None
    if not isinstance(value, str) or not 1 <= len(value) <= 19 or not value.isascii() or not value.isdecimal():
        raise ValueError('inexact counter')
    if len(value) > 1 and value[0] == '0':
        raise ValueError('counter spelling')
    result = int(value)
    if result > 2**63 - 1:
        raise ValueError('counter range')
    return result


def exact_json(line):
    """Duplicate keys and nonfinite JSON tokens cannot hide partial/failed evidence."""
    def pairs(items):
        out = {}
        for key, value in items:
            if key in out:
                raise ValueError('duplicate key')
            out[key] = value
        return out
    def invalid(_):
        raise ValueError('nonfinite JSON')
    return json.loads(line, object_pairs_hook=pairs, parse_constant=invalid)


def validate_header(header):
    """Pin this diagnostic schema independently; it does not revise LMTR v1."""
    keys = {'format', 'generation', 'rows', 'contended_attempts', 'capacity_exceeded', 'probe_failed', 'clock_failed', 'atomic', 'primitive_bytes', 'domains', 'statuses'}
    if set(header) != keys or header['format'] != 'r4-memory-probes-1' or header['atomic'] is not False:
        raise ValueError('diagnostic header')
    if type(header['rows']) is not int or not 0 <= header['rows'] <= 64:
        raise ValueError('row count')
    for key in ('capacity_exceeded', 'probe_failed', 'clock_failed'):
        if type(header[key]) is not bool:
            raise ValueError('diagnostic flag')
    if header['primitive_bytes'] != PRIMITIVE_BYTES or type(header['primitive_bytes']) is not int:
        raise ValueError('primitive charge')
    if header['domains'] != [{'id': k, 'fields': v} for k, v in FIELDS.items()] or header['statuses'] != STATUSES:
        raise ValueError('domain schema')
    if not counter(header['generation']):
        raise ValueError('generation')
    if counter(header['contended_attempts']) is None:
        raise ValueError('contention unknown')


def validate_row(row, header, expected):
    """Validate known fields and preserve failed/unknown domains without promoting them to valid."""
    if set(row) != {'sample', 'values'} or type(row['sample']) is not int or row['sample'] != expected:
        raise ValueError('sample sequence')
    if not isinstance(row['values'], list) or len(row['values']) != WIDTH:
        raise ValueError('row width')
    values = [counter(x) for x in row['values']]
    if values[:2] != [expected, counter(header['generation'])] or values[4] not in (0, 1) or values[5] not in range(4):
        raise ValueError('row witnesses')
    domains = {}
    previous = values[2]
    if not header['clock_failed'] and (previous is None or values[3] is None or values[3] < previous):
        raise ValueError('row bracket')
    for ordinal, (name, fields) in enumerate(FIELDS.items()):
        i = 6 + ordinal * STRIDE
        begin, end, status = values[i:i+3]
        data = values[i+3:i+3+len(fields)]
        if any(x is not None for x in values[i+3+len(fields):i+STRIDE]):
            raise ValueError('unused scalar slots')
        if status is None:
            if begin is not None or end is not None or any(x is not None for x in data):
                raise ValueError('skipped domain contains invented data')
        else:
            if status not in range(len(STATUSES)):
                raise ValueError('probe status')
            if not header['clock_failed'] and (begin is None or end is None or end < begin):
                raise ValueError('probe bracket')
            if not header['clock_failed'] and (begin < previous or end > values[3]):
                raise ValueError('domain ordering')
            previous = end
            if status == 0 and any(x is None for x in data):
                raise ValueError('OK probe missing data')
            if status == 2 and any(x is not None for x in data):
                raise ValueError('unsupported probe has data')
        domains[name] = {'begin_ns': begin, 'end_ns': end, 'status': None if status is None else STATUSES[status], 'values': dict(zip(fields, data))}
    roles = domains['PUBLISHED_ROLES']
    for key in ('source_is_rendered', 'source_is_roi', 'rendered_is_roi'):
        if roles['values'][key] not in (None, 0, 1):
            raise ValueError('alias flag')
    if roles['status'] == 'SOURCE_CHANGED' and any(x is not None for k, x in roles['values'].items() if not k.startswith('source_generation_')):
        raise ValueError('ambiguous generation role claims')
    first, last = domains['GC_BEFORE']['values']['count'], domains['GC_AFTER']['values']['count']
    relation = 0 if first is None or last is None else 3 if last < first else 2 if last > first else 1
    if values[5] != relation:
        raise ValueError('GC relation')
    return domains


def read_report(path):
    """Hash and parse finite immutable input; no scene, device, source or recording discovery."""
    path = Path(path)
    before = path.stat()
    digest = hashlib.sha256()
    header = None
    rows = []
    total = 0
    with path.open('rb') as stream:
        while line := stream.readline(16385):
            total += len(line)
            if len(line) > 16384 or total > 2*1024**2 or len(rows) > 64 or not line.endswith(b'\n'):
                raise ValueError('diagnostic input budget/truncation')
            digest.update(line)
            item = exact_json(line)
            if header is None:
                header = item
                validate_header(header)
            else:
                rows.append(validate_row(item, header, len(rows)))
    if header is None or len(rows) != header['rows']:
        raise ValueError('incomplete diagnostic export')
    after = path.stat()
    if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
        raise ValueError('source changed during read')
    return dict(format='r4-memory-probes-readback-1', status='structurally_verified_not_runtime_qualification',
        source_sha256=digest.hexdigest(), source_unchanged=True, samples=len(rows), header=header, rows=rows,
        limitations=['Sequential bracketed domains, not an atomic snapshot', 'Known role aliases are not a unique live-heap sum',
            'Native capacities and graphics ownership may remain unknown', 'No RSS policy/admission or physical accuracy verdict'])


def main():
    """Explicit single-file readback; report destination must be new."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = read_report(args.input)
    with args.output.open('x') as out:
        json.dump(result, out, indent=2)
        out.write('\n')
    print(json.dumps({k: result[k] for k in ('status', 'samples', 'source_sha256', 'source_unchanged')}))


if __name__ == '__main__':
    main()
