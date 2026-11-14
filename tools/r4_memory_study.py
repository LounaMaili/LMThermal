#!/usr/bin/env python3
"""Bounded offline analysis of preserved R4 memory probes; never connects to Android.

Health predicates and later diagnostic probes remain separate. A row's elapsed
label is not an atomic health/GC/RSS timestamp. No unobserved trajectory, native
RSS decomposition, recorder-only subtraction or post-GC retained heap is invented.
"""
import argparse
import hashlib
import json
from pathlib import Path

MIB=1048576
RSS_INCREMENT=128*MIB
LONG_MAX=2**63-1
GC_KEYS=('art.gc.bytes-allocated','art.gc.bytes-freed','art.gc.gc-count','art.gc.gc-time')


def integer(value):
    """Diagnostic counters must be exact nonnegative signed-64 integers, never booleans/floats."""
    if value is None:return None
    if isinstance(value,bool) or not isinstance(value,(int,str)):
        raise ValueError('not an exact counter')
    if isinstance(value,str) and (not value.isascii() or not value.isdecimal()):raise ValueError('counter token')
    result=int(value)
    if not 0<=result<=LONG_MAX:raise ValueError('counter range')
    return result


def relative_trip(reference,rss,increment=RSS_INCREMENT):
    """Reproduce the existing strict relative predicate, not recorder attribution or admission."""
    reference=integer(reference);rss=integer(rss);increment=integer(increment)
    if reference is None or rss is None or increment is None:return None
    if reference>LONG_MAX-increment:raise ValueError('threshold overflow')
    return rss>reference+increment


def minimum_reference_for_held_points(values,increment=RSS_INCREMENT):
    """Algebraic sensitivity only: hold supplied points fixed, not a physical counterfactual.

    This is not a proposed Start baseline, threshold or permission to pick a favorable snapshot.
    Unknown samples cannot establish coverage of unobserved instants.
    """
    known=[integer(x) for x in values if x is not None]
    return None if not known else max(0,max(known)-integer(increment))


class AbsoluteBudgetShadow:
    """Unintegrated policy thought experiment. A ceiling needs an independent qualified budget.

    Real evidence has no qualified ceiling here, so its shadow is inconclusive. Persistence
    is an advisory below the ceiling, never grace above a hard limit. Independent heap/system/
    thermal/battery/foreground/liveness rules remain the production policy's responsibility.
    No API here can admit a recording or override a legacy stop.
    """
    def __init__(self,ceiling=None,qualified=False):
        self.ceiling=integer(ceiling);self.qualified=qualified;self.previous=None;self.rises=0

    def observe(self,rss,independent_refusal=None):
        rss=integer(rss)
        if independent_refusal:return dict(verdict='would_stop_independent',reason=independent_refusal)
        if rss is None:return dict(verdict='inconclusive',reason='rss_unknown')
        if self.ceiling is None or not self.qualified:return dict(verdict='inconclusive',reason='independent_budget_not_qualified')
        if rss>self.ceiling:return dict(verdict='would_stop_absolute',reason='hypothetical_hard_ceiling')
        self.rises=self.rises+1 if self.previous is not None and rss>self.previous else 0
        self.previous=rss
        return dict(verdict='below_hypothetical_ceiling_not_admission',rising_sample_advisory=self.rises>=3)


def read_immutable(path,rows=False):
    """Read finite scalar evidence, verify no mid-read change, retain byte hashes without editing input."""
    path=Path(path);before=path.stat();digest=hashlib.sha256();data=[];total=0
    with path.open('rb') as stream:
        if rows:
            while line:=stream.readline(65537):
                total+=len(line)
                if len(line)>65536 or len(data)>=256 or total>4*1024**2:raise ValueError('diagnostic row budget')
                digest.update(line);data.append(json.loads(line))
        else:
            raw=stream.read(2*1024**2+1)
            if len(raw)>2*1024**2:raise ValueError('diagnostic document budget')
            digest.update(raw);data=json.loads(raw)
    after=path.stat()
    if (before.st_size,before.st_mtime_ns)!=(after.st_size,after.st_mtime_ns):raise ValueError('source changed during read')
    return data,dict(sha256=digest.hexdigest(),bytes=after.st_size)


def normalize(row,index):
    """Keep the two sequential probe domains; elapsed identifies row output, not all contained instants."""
    health=row.get('health',{});diagnostic=row.get('memory',row)
    def counters(source,keys):return {k:integer(source.get(k)) for k in keys}
    return dict(row=index,row_output_elapsed_ns=integer(row.get('elapsed_ns')),
        health=counters(health,('rss_bytes','java_used_bytes','java_headroom_bytes','system_available_bytes')),
        diagnostic=counters(diagnostic,('rss_bytes','java_used_bytes','native_allocated_bytes','pss_bytes')),
        gc={k:integer(diagnostic.get('gc_runtime',{}).get(k)) for k in GC_KEYS},
        actual_safety_reason=row.get('safety_stop_reason'),
        recorder={k:integer(row.get('recorder',{}).get(k)) for k in ('accepted','acknowledged','preparation_queued_bytes','preparation_in_flight_bytes','writer_queued_bytes','writer_in_flight_bytes','pending_bytes','control_bytes')},
        scope='sequential probes, no exact joint timestamp or inferred missing values')


def counter_deltas(rows):
    """Runtime endpoint differences describe a counter interval, not retained owners or per-stage bytes."""
    result={}
    for key in GC_KEYS:
        if len(rows)<2:result[key]=None;continue
        first,last=rows[0]['gc'][key],rows[-1]['gc'][key]
        result[key]=None if first is None or last is None or last<first else last-first
    return result


def analyze_case(case):
    """Copy only bounded memory/counter scalars to the derived report, excluding camera/scene metadata."""
    raw,source=read_immutable(case['samples'],True);rows=[normalize(r,i) for i,r in enumerate(raw)]
    anchors=[r['row_output_elapsed_ns'] for r in rows if r['row_output_elapsed_ns'] is not None]
    if any(a>b for a,b in zip(anchors,anchors[1:])):raise ValueError('row elapsed regression')
    fingerprints={'samples':source};admission=None;terminal=None;reference=None
    if case.get('preflight'):
        pre,fingerprints['preflight']=read_immutable(case['preflight']);admission=pre.get('health');reference=integer(admission.get('rss_bytes'))
    if case.get('report'):
        report,fingerprints['report']=read_immutable(case['report'])
        terminal={k:report.get(k) for k in ('termination_status','termination_reason','diagnostic_failure','safety_stop_reason','observed_stop_request_elapsed_ns','duration_ns')}
    health_rss=[r['health']['rss_bytes'] for r in rows]
    trips=[relative_trip(reference,x) for x in health_rss]
    for r,trip in zip(rows,trips):r['existing_relative_predicate']=trip
    if case['kind']!='recording' and any(r.get('recorder_created') is not False for r in raw):
        raise ValueError('preview-only provenance missing')
    return dict(label=case['label'],kind=case['kind'],configuration=case['configuration'],source_commit=case.get('source_commit'),
        original_fingerprints=fingerprints,admission_health=admission,terminal=terminal,rows=rows,
        existing_guard_reference_bytes=reference,existing_guard_limit_bytes=None if reference is None else reference+RSS_INCREMENT,
        first_observed_health_trip_row=next((i for i,x in enumerate(trips) if x is True),None),
        minimum_reference_for_held_points_bytes=minimum_reference_for_held_points(health_rss),
        sensitivity_scope='algebra on supplied fixed points only; never a new baseline, recorder-only delta or safety approval',
        endpoint_gc_counter_deltas=counter_deltas(rows),
        shadow=AbsoluteBudgetShadow().observe(health_rss[-1] if health_rss else None),
        health_probe_scope='not atomic with diagnostic RSS/GC or output elapsed; no values between samples reconstructed')


def main():
    """Use an explicit private source plan and exclusive new output, without discovery or device tools."""
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sources',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();plan,_=read_immutable(args.sources)
    if not 1<=len(plan['cases'])<=16:raise ValueError('finite case budget')
    result=[analyze_case(c) for c in plan['cases']]
    if len({r['label'] for r in result})!=len(result):raise ValueError('duplicate label')
    args.output.mkdir(mode=0o700)
    output=dict(scope='Preserved physical scalar probes, no new Android test; unpaired builds/windows are not recorder attribution',
        policy_unchanged=True,physical_or_adb_access=False,cases=result,
        warnings=['No RSS-minus-Java/native decomposition','GC endpoint changes do not locate collection times or post-GC floors',
            'Initial-reference sensitivity does not authorize selecting a favorable snapshot or lifting the guard'])
    (args.output/'report.json').write_text(json.dumps(output,indent=2)+'\n')
    print(json.dumps(dict(cases=len(result),samples=sum(len(x['rows']) for x in result),status='derived_without_source_mutation')))


if __name__=='__main__':main()
