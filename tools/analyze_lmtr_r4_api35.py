#!/usr/bin/env python3
"""Independent readback/assessment of a collected targeted replay; no ADB or source repair.

A new output directory is required. Missing evidence remains unknown. A cadence review
can approve only an otherwise complete replay, never override failed integrity or losses.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tarfile
import traceback

from check_lmtr_r4_field import check, oracle_rows, file_hash
from lmtr_v1 import Reader

TEST_CLASS = 'org.lmthermal.art.HtMemoryRuntimeTest'
TEST_METHOD = 'fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs'


def save(path, value):
    """Assessment outputs are new-only and never written into extracted source evidence."""
    with path.open('x', encoding='utf-8') as output:
        json.dump(value, output, indent=2)
        output.write('\n')


def read_json(path, issues):
    """Missing, malformed and explicit null evidence are not fabricated terminal states."""
    try:
        with path.open() as source:
            value = json.load(source)
            if not isinstance(value, dict):
                raise ValueError('expected_json_object')
            return value
    except (OSError, ValueError) as error:
        issues.append(str(path.name) + ':' + type(error).__name__)
        return None


def verify_archive(directory, collection, issues):
    """Bind extracted inputs to the retained original tar, including offline re-analysis."""
    archive = directory / 'synthetic.tar'
    if not archive.is_file() or file_hash(archive) != collection.get('archive_sha256'):
        issues.append('missing_or_changed_original_archive')
        return
    expected = set()
    try:
        with tarfile.open(archive, 'r:') as source:
            for member in source:
                parts = Path(member.name).parts
                if Path(member.name).is_absolute() or '..' in parts or parts[:2] != ('files', 'r4'):
                    raise ValueError('unsafe_archive_path')
                if member.isdir():
                    continue
                if not member.isfile() or member.name in expected:
                    raise ValueError('unsafe_or_duplicate_archive_file')
                expected.add(member.name)
                path = directory / 'extracted' / member.name
                if path.is_symlink() or not path.is_file() or path.stat().st_size != member.size:
                    raise ValueError('missing_or_invalid_extracted_file')
                value = hashlib.sha256()
                with source.extractfile(member) as data:
                    for block in iter(lambda: data.read(65536), b''):
                        value.update(block)
                if value.hexdigest() != file_hash(path):
                    raise ValueError('archive_extraction_hash_mismatch')
        actual = {str(p.relative_to(directory / 'extracted')) for p in
                  (directory / 'extracted').rglob('*') if p.is_file()}
        if expected != actual:
            raise ValueError('unexpected_extracted_file_inventory')
    except Exception as error:
        issues.append('archive_binding:' + type(error).__name__ + ':' + str(error))


def number(snapshot, key):
    """No zero default for absent counters."""
    value = snapshot.get(key)
    return int(value) if value is not None else None


def inspect_case(path, output, result, stress):
    """Use the unchanged independent field reader and expose qualified accounting only."""
    row = {'codec': int(path.name.removeprefix('full-')), 'result': result, 'stress_case': stress,
           'issues': [], 'complete_data_gate': False, 'diagnosed_failure': False}
    capture = path / 'capture.lmtr'
    oracle = path / 'oracle.jsonl'
    if not capture.is_file() or not oracle.is_file():
        row['issues'].append('missing_capture_or_oracle')
        return row
    row['capture_sha256'] = file_hash(capture)
    row['oracle_sha256'] = file_hash(oracle)
    # Unknown terminal state selects prefix inspection, but remains unknown in the report.
    # Recovery is a reader mode, NOT an assertion that END is absent or the file finalized.
    recovery = result is None or result.get('finalized') is not True
    row['recovery_requested'] = recovery
    try:
        readback = check(path, recover=recovery)
        save(output / (path.name + '-python-readback.json'), readback)
        row['readback'] = readback
        with Reader(capture) as reader:
            complete = reader.check(full=True, recover=recovery)
            # Inspect only the independently verified prefix; never parse an unknown tail.
            end = int(complete.get('verified_prefix_bytes', reader.size))
            offset = 0
            observed_codecs = set()
            while offset < end:
                record = reader.record(offset)
                if record['kind'] == 2:
                    metadata, _ = reader.decode(record)
                    observed_codecs.update(block['codec'] for block in metadata['blocks'])
                offset += record['length']
        row['verified_block_codecs'] = sorted(observed_codecs)
        if observed_codecs and observed_codecs != {row['codec']}:
            row['issues'].append('verified_block_codec_differs_from_requested_case')
        row['wire_summary'] = complete['summary']
        row['wire_terminal'] = complete.get('declared_end_status')
        row['unknown_tail'] = complete.get('unknown_tail')
        row['prepared_oracle_rows'] = sum(1 for _ in oracle_rows(oracle))
    except Exception as error:
        row['issues'].append('independent_readback:' + type(error).__name__ + ':' + str(error))
        row['source_hashes_unchanged_after_failed_readback'] = (
            file_hash(capture) == row['capture_sha256'] and file_hash(oracle) == row['oracle_sha256'])
        (output / (path.name + '-readback-error.txt')).write_text(traceback.format_exc())
        return row
    if file_hash(capture) != row['capture_sha256'] or file_hash(oracle) != row['oracle_sha256']:
        row['issues'].append('source_changed_during_readback')
    if result is None or stress is None:
        row['issues'].append('missing_result_or_stress_chronology')
        return row
    snapshot = result.get('snapshot', {})
    first = snapshot.get('first_failure')
    row['first_failure'] = first
    row['current_unrepresented_loss'] = snapshot.get('unrepresented_loss')
    accepted = number(snapshot, 'accepted')
    ack = number(snapshot, 'acknowledged')
    preparation = number(snapshot, 'preparation_drops')
    writer = number(snapshot, 'writer_drops')
    offered = stress.get('observations_offered')
    row['accounting'] = {'offered': offered, 'accepted': accepted, 'acknowledged': ack,
                         'preparation_drops': preparation, 'writer_drops': writer,
                         'verified_prefix_observations': row['readback']['observations'],
                         'wire_known_missing_observations': row['wire_summary'].get('known_missing_observations'),
                         'accepted_minus_acknowledged_minus_writer_drops':
                             accepted - ack - writer if None not in (accepted, ack, writer) else None,
                         'qualification': 'Arithmetic remainder is NOT a newly inferred gap/loss count. '
                             'Prepared oracle rows, admitted owners, verified prefix and durable acknowledgments '
                             'are different domains. An incomplete tail is not silently filled.'}
    row['cadence'] = {key: stress.get(key) for key in ('requested_fps', 'intake_elapsed_ns',
                      'producer_intervals', 'schedule_lateness', 'admission', 'stop_ns')}
    intervals = stress.get('producer_intervals')
    if (not isinstance(intervals, dict) or any(intervals.get(k) is None for k in ('count', 'p50_ns', 'p95_ns', 'max_ns'))
            or stress.get('intake_elapsed_ns') is None or stress.get('requested_fps') != 25):
        row['issues'].append('missing_or_changed_cadence_evidence')
    if 'first_failure' not in snapshot or 'unrepresented_loss' not in snapshot:
        row['issues'].append('missing_new_diagnostic_fields')
    if first != stress.get('snapshot', {}).get('first_failure'):
        row['issues'].append('first_witness_not_stable_between_result_and_stress')
    if first is not None and not all(key in first for key in
            ('stage', 'code', 'exception_kind', 'last_intake_witness_ns', 'accepted', 'acknowledged')):
        row['issues'].append('incomplete_first_witness')
    if first is not None and not all(isinstance(first.get(k), str) and first[k]
                                      for k in ('stage', 'code', 'exception_kind')):
        row['issues'].append('unusable_first_witness_category')
    if None in (accepted, ack, preparation, writer, offered):
        row['issues'].append('missing_accounting_counters')
    preflight_issues = []
    preflight = read_json(path / 'preflight.json', preflight_issues)
    row['preflight'] = preflight
    # These are identity checks against the unchanged existing workload, not tunable limits.
    expected_limits = {'preparation_bytes': '4194304', 'writer_bytes': '4194304',
                       'preferred_chunk_bytes': '4194304', 'control_bytes': '1048576',
                       'max_observation_bytes': '3145728', 'reserved_logical_bytes': '81788928',
                       'profile': 'full', 'codec': row['codec'], 'physical': False}
    limits_match = preflight is not None and all(preflight.get(k) == v for k, v in expected_limits.items())
    row['workload_limits_match'] = limits_match
    if not limits_match:
        row['issues'].append('missing_or_changed_workload_preflight')
    row['complete_data_gate'] = not row['issues'] and all((
        result.get('finalized') is True, result.get('status') == 'completed',
        result.get('reason') == 'user_stop', result.get('failure') is None,
        result.get('refused_admission') is None, first is None,
        snapshot.get('unrepresented_loss') is False, snapshot.get('error') is None,
        accepted == 200, ack == 200,
        preparation == 0, writer == 0, offered == 200,
        stress.get('verdict') == 'completed_no_drops', stress.get('memory_guard_stopped') is False,
        stress.get('original_input_not_mutated') is True,
        stress.get('minimum_headroom_bytes') is not None and int(stress['minimum_headroom_bytes']) >= 33554432,
        isinstance(intervals, dict) and intervals.get('count') == 199,
        row['readback']['observations'] == 200, row['readback']['unavailable'] == 0,
        row['readback']['integrity_scope'] == 'full', row['wire_terminal'] == 'completed',
        row['verified_block_codecs'] == [row['codec']],
        row['wire_summary'].get('gap_events') == '0',
        row['wire_summary'].get('known_missing_observations') == '0',
        row['wire_summary'].get('unknown_gap_events') == '0',
        number(snapshot, 'max_preparation_queued_bytes') is not None and number(snapshot, 'max_preparation_queued_bytes') <= 4194304,
        number(snapshot, 'max_writer_queued_bytes') is not None and number(snapshot, 'max_writer_queued_bytes') <= 4194304))
    # An explicit represented-overload failure can be diagnosed without a fatal witness.
    # That is not a reproduction of the original unrepresented-loss terminal failure.
    row['diagnosed_failure'] = not row['issues'] and not row['complete_data_gate'] and (
        first is not None or (preparation is not None and preparation > 0)
        or (writer is not None and writer > 0)
        or (stress.get('memory_guard_stopped') is True and stress.get('minimum_headroom_bytes') is not None))
    return row


def tested_source_sha(directory, context, issues):
    """Bind the tested checkout independently of the workflow event commit.

    A paired reference build legitimately has a different HEAD. Preserve the event
    SHA as a separate report field; an inconsistent/missing checkout receipt is C.
    """
    expected = context.get('expected_source_sha', context.get('GITHUB_SHA'))
    try:
        actual = (directory / 'source-sha.txt').read_text().strip()
    except OSError:
        actual = None
    if (not isinstance(expected, str) or not re.fullmatch('[0-9a-f]{40}', expected)
            or actual != expected):
        issues.append('tested_source_identity_mismatch_or_missing')
        return None
    return actual


def assess(directory, output, cadence_review=None):
    """Keep instrumentation, collection, integrity and qualitative cadence decisions separate."""
    output.mkdir(mode=0o700, exist_ok=False)
    issues = []
    context = read_json(directory / 'context.json', issues)
    collection = read_json(directory / 'collection.json', issues)
    if context is None or collection is None:
        report = {'outcome': 'C', 'reasons': issues, 'cases': []}
        save(output / 'assessment.json', report)
        return report
    source_sha = tested_source_sha(directory, context, issues)
    issues.extend(collection.get('errors', []))
    if not collection.get('qualified_emulator') or not collection.get('archive_ready'):
        issues.append('environment_or_archive_not_qualified')
    instruction = collection.get('instrumentation')
    if instruction is None or instruction.get('timed_out'):
        issues.append('instrumentation_missing_or_timed_out')
    text = (directory / 'instrumentation.txt').read_text(errors='replace') if (directory / 'instrumentation.txt').is_file() else ''
    classes = set(re.findall(r'(?m)^INSTRUMENTATION_STATUS: class=(.*)\r?$', text))
    methods = set(re.findall(r'(?m)^INSTRUMENTATION_STATUS: test=(.*)\r?$', text))
    numbers = set(re.findall(r'(?m)^INSTRUMENTATION_STATUS: numtests=(\d+)\r?$', text))
    exclusive = classes == {TEST_CLASS} and methods == {TEST_METHOD} and numbers == {'1'}
    junit_passed = bool(re.search(r'(?m)^OK \(1 test\)\s*$', text)) and not re.search(
        r'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed', text)
    if not exclusive:
        issues.append('exclusive_one_method_execution_not_attested')
    if collection.get('archive_ready'):
        verify_archive(directory, collection, issues)
    roots = sorted((directory / 'extracted/files/r4').glob('recorder-stress-*')) if collection.get('archive_ready') else []
    if len(roots) != 1:
        issues.append('expected_exactly_one_new_stress_root')
    cases = []
    baselines = []
    for root in roots:
        local_issues = []
        stress = read_json(root / 'stress.json', local_issues)
        issues.extend(local_issues)
        if stress is not None:
            baselines.append(stress.get('baseline'))
            if stress.get('forced_gc') is not False:
                issues.append('forced_gc_evidence_missing_or_true')
        observed_codecs = {codec for codec in (0, 1) if (root / ('full-' + str(codec))).is_dir()}
        if stress is not None and {case.get('codec') for case in stress.get('cases', [])} != observed_codecs:
            issues.append('stress_cases_do_not_match_observed_codec_directories')
        for codec in (0, 1):
            case = root / ('full-' + str(codec))
            if not case.is_dir():
                continue  # Never request readback for a codec which did not start.
            local_issues = []
            result = read_json(case / 'run-result.json', local_issues)
            matches = [s for s in stress.get('cases', []) if s.get('codec') == codec] if stress else []
            try:
                row = inspect_case(case, output, result, matches[0] if len(matches) == 1 else None)
            except Exception as error:
                row = {'codec': codec, 'issues': ['case_analysis:' + type(error).__name__ + ':' + str(error)],
                       'complete_data_gate': False, 'diagnosed_failure': False, 'result': result}
                (output / (case.name + '-analysis-error.txt')).write_text(traceback.format_exc())
            row['issues'].extend(local_issues)
            cases.append(row)
    if not cases:
        issues.append('no_executed_codec_evidence')
    if any(row['issues'] for row in cases):
        issues.append('case_readback_or_diagnostic_error')
    baseline_ok = len(baselines) == 1 and isinstance(baselines[0], dict) and baselines[0].get('observations') == 50
    if baseline_ok:
        base = baselines[0]
        baseline_ok = (base.get('elapsed_ns') is not None and isinstance(base.get('intervals'), dict)
                       and base['intervals'].get('count') == 49
                       and all(base['intervals'].get(k) is not None for k in ('p50_ns', 'p95_ns', 'max_ns')))
    if not baseline_ok:
        issues.append('missing_50_observation_matched_baseline')
    both_complete = len(cases) == 2 and {row['codec'] for row in cases} == {0, 1} and all(row['complete_data_gate'] for row in cases)
    eligible = not issues and both_complete and junit_passed and instruction.get('exit_code') == 0
    cadence = {'decision': 'pending_owner_comparability_review', 'numeric_cutoff_added': False,
               'basis': 'Existing qualitative matched-baseline comparability; no numeric tolerance is defined for this replay.'}
    if cadence_review is not None:
        review = read_json(cadence_review, issues)
        identity = {'source_sha': source_sha, 'run_id': context.get('GITHUB_RUN_ID'),
                    'run_attempt': context.get('GITHUB_RUN_ATTEMPT'), 'archive_sha256': collection.get('archive_sha256')}
        if (review is None or review.get('identity') != identity
                or review.get('decision') != 'comparable_to_measured_baseline'
                or not review.get('reviewer') or not review.get('rationale')):
            issues.append('invalid_or_mismatched_explicit_cadence_review')
        else:
            cadence = review
    if issues:
        outcome = 'C'
    elif eligible and cadence.get('decision') == 'comparable_to_measured_baseline':
        outcome = 'A'
    elif any(row['diagnosed_failure'] for row in cases):
        outcome = 'B'
    else:
        outcome = 'C'  # A clean capture can be A-eligible, but cadence is not auto-approved.
    report = {'outcome': outcome, 'reasons': issues, 'eligible_for_A_after_cadence_review': eligible,
              'instrumentation': {'raw_exit': instruction, 'exclusive_method_attested': exclusive,
                                  'junit_passed': junit_passed},
              'identity': {'source_sha': source_sha, 'run_id': context.get('GITHUB_RUN_ID'),
                           'run_attempt': context.get('GITHUB_RUN_ATTEMPT'), 'archive_sha256': collection.get('archive_sha256')},
              'workflow_event_sha': context.get('GITHUB_SHA'),
              'baseline': baselines, 'cases': cases, 'cadence_review': cadence,
              'not_executed_codecs': sorted({0, 1} - {row['codec'] for row in cases}),
              'scope': 'One fresh-process synthetic ART method; not the old full-suite heap/order context, physical admission or calibration.'}
    save(output / 'assessment.json', report)
    return report


def main():
    """No runtime probing: offline re-analysis writes only a fresh assessment directory."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--cadence-review', type=Path, help='Explicit offline owner review bound to commit/run/attempt/archive')
    args = parser.parse_args()
    os.umask(0o077)
    report = assess(args.directory, args.output, args.cadence_review)
    print(json.dumps(report, indent=2))
    return {'A': 0, 'B': 10, 'C': 20}[report['outcome']]


if __name__ == '__main__':
    raise SystemExit(main())
