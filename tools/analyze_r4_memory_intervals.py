#!/usr/bin/env python3
"""Host-only interval analysis of finite scalar exports, with no hardware discovery.

GC changes have conservative possible-time brackets, not event timestamps.
Differences of bracketing readings are observations, never a post-GC live floor,
causal attribution, atomic census, RSS decomposition or recorder admission.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import statistics

from r4_memory_probes import read_report


def distribution(values):
    """Retain exact extrema/count/sum; median is descriptive, not a time-weighted value."""
    values = [x for x in values if x is not None]
    if not values:
        return dict(count=0, minimum=None, median=None, maximum=None, total=None)
    return dict(count=len(values), minimum=min(values), median=statistics.median(values),
                maximum=max(values), total=sum(values))


def observation(domain, field, interval):
    """Keep the original bracket and its relationship to the whole possible GC interval."""
    start, end = domain['begin_ns'], domain['end_ns']
    relation = 'unknown'
    if start is not None and end is not None:
        relation = ('before_possible_change' if end <= interval[0] else
                    'after_possible_change' if start >= interval[1] else 'overlaps_possible_change')
    return dict(begin_ns=start, end_ns=end, status=domain['status'], value=domain['values'][field], relation=relation)


def analyze(report):
    """Describe every observed collection advance and scope each scalar difference separately."""
    rows = report['rows']
    domains = {}
    for name in report['header']['domains']:
        key = name['id']
        domains[key] = dict(status_counts=dict(Counter(r[key]['status'] for r in rows)),
                            wall_ns=distribution(r[key]['end_ns']-r[key]['begin_ns'] for r in rows
                                                 if r[key]['end_ns'] is not None and r[key]['begin_ns'] is not None),
                            fields={f: distribution(r[key]['values'][f] for r in rows) for f in name['fields']})
    endpoints = [(i, n, r[n]) for i, r in enumerate(rows) for n in ('GC_BEFORE', 'GC_AFTER')]
    events, regressions = [], []
    metric_fields = dict(JAVA=['occupied', 'headroom'], RSS=['proc_vm_rss'],
                         ANDROID_MEMORY=['total_pss', 'summary_java', 'summary_native', 'summary_graphics'],
                         NATIVE_ALLOCATOR=['allocated', 'heap_size'])
    for left, right in zip(endpoints, endpoints[1:]):
        li, ln, ld = left
        ri, rn, rd = right
        lc, rc = ld['values']['count'], rd['values']['count']
        if lc is None or rc is None:
            continue
        if rc < lc:
            regressions.append(dict(left_row=li, right_row=ri, left_count=lc, right_count=rc))
        if rc <= lc:
            continue
        counter_statuses = [ld['status'], rd['status']]
        counter_status_qualified = all(s in ('OK', 'PARTIAL') for s in counter_statuses)
        interval = (ld['begin_ns'], rd['end_ns'])
        if any(x is None for x in interval) or interval[1] < interval[0] or report['header']['clock_failed']:
            events.append(dict(left_row=li, right_row=ri, count_delta=rc-lc, counter_statuses=counter_statuses,
                               counter_status_qualified=counter_status_qualified, possible_interval_ns=None,
                               timing_qualified=False, reason='unknown_or_failed_clock'))
            continue
        event = dict(left_row=li, left_domain=ln, right_row=ri, right_domain=rn,
                     counter_statuses=counter_statuses, counter_status_qualified=counter_status_qualified,
                     left_counter_bracket_ns=[ld['begin_ns'], ld['end_ns']],
                     right_counter_bracket_ns=[rd['begin_ns'], rd['end_ns']],
                     possible_interval_ns=list(interval), count_delta=rc-lc,
                     counter_deltas={k: None if ld['values'][k] is None or rd['values'][k] is None else
                                     rd['values'][k]-ld['values'][k] for k in ('allocated', 'freed', 'time_ms')},
                     bracketing_metrics={})
        for name, fields in metric_fields.items():
            for field in fields:
                a = observation(rows[li][name], field, interval)
                b = observation(rows[ri][name], field, interval)
                event['bracketing_metrics'][f'{name}.{field}'] = dict(
                    left=a, right=b, paired_outside_possible_change=(a['relation']=='before_possible_change' and
                                                                    b['relation']=='after_possible_change'),
                    delta=None if li == ri or a['value'] is None or b['value'] is None else b['value']-a['value'])
        events.append(event)
    roles = [r['PUBLISHED_ROLES']['values'] for r in rows]
    same_epoch = bool(roles) and all(x['source_generation_before'] is not None and
                     x['source_generation_before'] == x['source_generation_after'] for x in roles)
    aliases = {name: dict(Counter('unknown' if r[name] is None else str(r[name]) for r in roles))
               for name in ('source_is_rendered', 'source_is_roi', 'rendered_is_roi')}
    # A deterministic call-opportunity projection on the *recorded reference* only. It is
    # not a benchmark or a prediction after changing GC/scheduling/observer interaction.
    removed = [r['ANDROID_MEMORY']['end_ns']-r['ANDROID_MEMORY']['begin_ns'] for i, r in enumerate(rows)
               if i % 5 and r['ANDROID_MEMORY']['end_ns'] is not None and r['ANDROID_MEMORY']['begin_ns'] is not None]
    return dict(format='r4-memory-interval-analysis-1', source_sha256=report['source_sha256'],
                source_unchanged=report['source_unchanged'], rows=len(rows), domains=domains,
                gc_changes=events, gc_counter_regressions=regressions,
                roles=dict(all_individual_epoch_brackets_match=same_epoch, alias_counts=aliases,
                           epochs=[r['source_generation_before'] for r in roles]),
                tiered_call_opportunity_projection=dict(period_rows=5, omitted_reference_calls=len(removed),
                    recorded_wall_ns_on_those_calls=distribution(removed),
                    interpretation='Arithmetic on original brackets only; no measured Android speedup or RSS reduction'),
                limitations=['GC interval endpoints enclose unknown counter-read instants; no precise GC timestamp',
                             'Before/after readings contain intervening allocations and may straddle other activity',
                             'Equal counters do not prove a post-GC floor or no overlapping GC',
                             'Roles can alias; published roles are not all roots or unique owners',
                             'Sparse domains are unknown between actual reads; no interpolation',
                             'No runtime safety, causal A/B comparison or recorder qualification verdict'])


def main():
    """Read one original and write only a new analysis destination."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = analyze(read_report(args.input))
    with args.output.open('x') as stream:
        json.dump(out, stream, indent=2)
        stream.write('\n')
    print(json.dumps(dict(rows=out['rows'], gc_change_intervals=len(out['gc_changes']), source_unchanged=out['source_unchanged'])))


if __name__ == '__main__':
    main()
