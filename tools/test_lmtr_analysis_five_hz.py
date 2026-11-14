"""Negative periodic field readback using copies of the current noncanonical virtual output.

Generate AnalysisFiveHzInteropProbe first and set LMTR_PERIODIC_TEST_ROOT to that output.
The complete offered-event checker remains the stronger selection-policy oracle.
"""
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from check_lmtr_analysis_five_hz import check_periodic_field, rows


class PeriodicDiagnosticBoundsTests(unittest.TestCase):
    """Longer source decisions do not remove owner, line or aggregate evidence bounds."""
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='lmtr-periodic-bounds-')
        self.path=Path(self.temp.name)/'scalar.jsonl'

    def tearDown(self):self.temp.cleanup()

    def test_explicit_source_decision_limit_accepts_the_sixty_second_plan(self):
        self.path.write_bytes(b'{"ordinal":"0"}\n'*1500)
        self.assertEqual(len(rows(self.path,4096)),1500)
        with self.assertRaises(AssertionError):rows(self.path)

    def test_source_decision_count_limit_is_still_finite(self):
        self.path.write_bytes(b'{}\n'*4097)
        with self.assertRaises(AssertionError):rows(self.path,4096)

    def test_overlong_line_is_refused_before_json_parsing(self):
        self.path.write_bytes(b' '*65537)
        with self.assertRaises(AssertionError):rows(self.path,4096)

    def test_aggregate_json_evidence_budget_is_independent_of_the_row_limit(self):
        # Each string also respects the unchanged wire JSON's 16 KiB scalar bound.
        row=json.dumps({k:'x'*16000 for k in ('a','b','c','d')}).encode()+b'\n'
        self.path.write_bytes(row*132)
        with self.assertRaises(AssertionError):rows(self.path,4096)


class PeriodicFieldReadbackTests(unittest.TestCase):
    def setUp(self):
        self.root=Path(os.environ['LMTR_PERIODIC_TEST_ROOT'])
        self.temp=tempfile.TemporaryDirectory(prefix='lmtr-periodic-negative-')
        self.directory=Path(self.temp.name)/'copy';shutil.copytree(self.root/'ten_seconds-1',self.directory)
    def tearDown(self):self.temp.cleanup()
    def mutate(self,key,value):
        path=self.directory/'oracle.jsonl';rows=[json.loads(x) for x in path.read_text().splitlines()]
        rows[1][key]=value;path.write_text(''.join(json.dumps(r)+'\n' for r in rows))
    def test_both_codecs_keep_dense_selected_counters_and_distinct_original_ordinals(self):
        for codec in (0,1):
            report=check_periodic_field(self.root/f'ten_seconds-{codec}')
            self.assertEqual(report['counts']['available'],49);self.assertEqual(report['counts']['unavailable'],1)
            self.assertTrue(report['planned_slot_coverage']);self.assertTrue(report['source_unchanged'])
    def test_tampered_original_ordinal_is_not_accepted_as_the_dense_selected_counter(self):
        self.mutate('original_ordinal','999')
        with self.assertRaises(AssertionError):check_periodic_field(self.directory)
    def test_tampered_receipt_cannot_be_replaced_with_a_planned_deadline(self):
        self.mutate('original_receipt_ns','1')
        with self.assertRaises(AssertionError):check_periodic_field(self.directory)
    def test_tampered_real_intake_bounds_reject(self):
        self.mutate('intake_upper_monotonic_ns','1')
        with self.assertRaises(AssertionError):check_periodic_field(self.directory)
    def test_changed_original_float32_hash_rejects(self):
        rows=[json.loads(x) for x in (self.directory/'oracle.jsonl').read_text().splitlines()]
        payloads=rows[1]['original_payloads'];payloads['temperature']['hash']='0'*64
        self.mutate('original_payloads',payloads)
        with self.assertRaises(AssertionError):check_periodic_field(self.directory)
    def test_false_source_availability_is_not_a_missing_slot(self):
        self.mutate('original_measurement_available',False)
        with self.assertRaises(AssertionError):check_periodic_field(self.directory)
    def test_missing_materialized_oracle_is_not_intentional_nonselection(self):
        path=self.directory/'oracle.jsonl';rows=path.read_text().splitlines();path.write_text('\n'.join(rows[:1]+rows[2:])+'\n')
        with self.assertRaises(AssertionError):check_periodic_field(self.directory)
    def test_prefix_recovery_keeps_unknown_tail_and_does_not_certify_slot_coverage(self):
        for name in ('clock-0','clock-1','end_failure-0','end_failure-1','writer-0','preparation-1'):
            recover=name.startswith(('clock','end_failure'))
            report=check_periodic_field(self.root/name,recover)
            self.assertEqual(report['recovered'],recover);self.assertEqual(report['planned_slot_coverage'],not recover)


if __name__=='__main__':unittest.main()
