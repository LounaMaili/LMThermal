#!/usr/bin/env python3
"""Offline diagnostics-quality check, separate from LMTR integrity and the existing A/B/C verdict.

No ADB, JVM invocation or source repair. Missing data stays missing; invalid metrics cannot
become a throughput success. A valid interrupted operation is evidence, not a parser error.
"""
import argparse
import json
from pathlib import Path

PHASES = {'CHUNK_BUILD', 'CHUNK_FIRST_VALIDATION', 'CHUNK_ENCODE_TOTAL', 'APPEND_CHUNK',
          'ENTRY_PROCESSING', 'CHECKPOINT', 'FINALIZATION', 'BACKEND_CLOSE', 'READBACK'}
TYPES = {'OTHER', 'HEADER', 'CHUNK', 'INDEX', 'CHECKPOINT', 'END'}
LONG_MAX = 2**63 - 1


def decimal(value):
    """Require nonnegative bounded decimal counters; do not invent zero for a missing value."""
    if not isinstance(value, str) or not value.isascii() or not value.isdecimal() or len(value) > 19:
        raise ValueError('invalid_decimal_counter')
    result = int(value)
    if result > LONG_MAX:
        raise ValueError('counter_outside_Long')
    return result


def distribution(value, unit, completed, limitations):
    """Check all-call accounting independently of sampled quantiles; retain saturation explicitly."""
    if not isinstance(value, dict):
        raise ValueError('missing_distribution')
    count, capacity = value['count'], value['capacity']
    if type(count) is not int or type(capacity) is not int or not 0 <= count <= capacity <= 8192:
        raise ValueError('invalid_sample_capacity')
    for key in ('counter_overflow', 'sum_overflow'):
        if type(value[key]) is not bool:
            raise ValueError('missing_overflow_flag')
    if value['counter_overflow']:
        limitations.append('counter_saturated')
        return
    observed, valid, omitted, invalid, interrupted = [decimal(value[k]) for k in
        ('observed_count', 'valid_count', 'omitted', 'invalid', 'interrupted')]
    if observed != valid + invalid or valid != count + omitted or interrupted != (0 if completed else observed):
        raise ValueError('inconsistent_call_accounting')
    if invalid or omitted:
        limitations.append('invalid_or_omitted_samples')
    if not valid:
        if value['sum_' + unit] is not None or value['min_' + unit] is not None or value['max_all_' + unit] is not None:
            raise ValueError('fabricated_empty_statistics')
    else:
        minimum, maximum = decimal(value['min_' + unit]), decimal(value['max_all_' + unit])
        if minimum > maximum:
            raise ValueError('reversed_extrema')
        if value['sum_overflow']:
            if value['sum_' + unit] is not None:
                raise ValueError('overflow_presented_as_exact')
            limitations.append('sum_overflow')
        elif not minimum * valid <= decimal(value['sum_' + unit]) <= maximum * valid:
            raise ValueError('sum_outside_extrema_bounds')
    quantiles = [value[k + '_' + unit] for k in ('p50', 'p95', 'max')]
    if count == 0:
        if any(v is not None for v in quantiles):
            raise ValueError('fabricated_empty_quantile')
    else:
        quantiles = list(map(decimal, quantiles))
        if quantiles != sorted(quantiles) or quantiles[0] < minimum or quantiles[-1] > maximum:
            raise ValueError('invalid_sample_quantiles')


def metrics(value):
    """Validate fixed vocabulary and attribute availability without parsing or retaining a body."""
    limitations = []
    if not isinstance(value, dict) or value.get('version') != 1:
        raise ValueError('missing_or_unsupported_fine_metrics')
    phases, backend = value['phase_cells'], value['backend_cells']
    if len(phases) != len(PHASES) or {v['phase'] for v in phases} != PHASES:
        raise ValueError('phase_inventory_changed')
    if len(backend) != len(TYPES) or {v['record_type'] for v in backend} != TYPES:
        raise ValueError('backend_inventory_changed')
    for cell in phases + backend:
        for status in ('completed', 'interrupted'):
            for key, unit in [('duration', 'ns'), ('body_bytes', 'bytes'),
                              ('observation_entries', 'entries'), ('gap_entries', 'entries')]:
                distribution(cell[status][key], unit, status == 'completed', limitations)
    if decimal(value['clock_exceptions']) or decimal(value['observer_exceptions']) or value['failure_counter_saturated']:
        limitations.append('clock_or_observer_failure')
    return sorted(set(limitations))


def assess(directory, mode):
    """Assess only new diagnostics; successful parsing never certifies no drops, cadence or LMTR bytes."""
    report = {'scope': 'fine_metrics_quality_only_not_LMTR_or_throughput_acceptance',
              'mode': mode, 'cases': [], 'issues': [], 'limitations': []}
    paths = list((directory / 'extracted/files/r4').glob('recorder-stress-*/stress.json'))
    if len(paths) != 1:
        report['issues'].append('stress_missing_or_ambiguous')
    else:
        try:
            stress = json.loads(paths[0].read_text())
            if stress.get('writer_metrics_mode') != mode:
                raise ValueError('stress_mode_mismatch')
            if not stress.get('cases'):
                raise ValueError('no_completed_case_report')
            seen = set()
            for case in stress['cases']:
                if case.get('codec') not in (0, 1) or case['codec'] in seen:
                    raise ValueError('duplicate_or_invalid_codec')
                seen.add(case['codec'])
                if case.get('writer_metrics_mode') != mode:
                    raise ValueError('case_mode_mismatch')
                if mode == 'off':
                    if case.get('writer_metrics', 'missing') is not None:
                        raise ValueError('off_mode_fabricated_or_missing_metrics')
                    limited = []
                else:
                    limited = metrics(case.get('writer_metrics'))
                report['cases'].append({'codec': case['codec'], 'limitations': limited})
                report['limitations'].extend(limited)
            if seen != {0, 1}:
                report['limitations'].append('partial_codec_campaign')
        except (ValueError, KeyError, TypeError, OSError) as error:
            report['issues'].append(type(error).__name__ + ':' + str(error))
    report['limitations'] = sorted(set(report['limitations']))
    report['status'] = 'invalid_or_missing' if report['issues'] else ('limited' if report['limitations'] else 'usable')
    return report


def main():
    """Write a new-only quality receipt; never overwrite extracted evidence or historical assessments."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--mode', choices=('on', 'off'), required=True)
    args = parser.parse_args()
    report = assess(args.directory, args.mode)
    with args.output.open('x') as output:
        json.dump(report, output, indent=2)
        output.write('\n')
    print(json.dumps(report))
    return 20 if report['issues'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
