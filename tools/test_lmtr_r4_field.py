"""Negative field-oracle/read-only recovery regressions using fresh noncanonical probe outputs."""
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from lmtr_v1 import Reader
from check_lmtr_r4_field import check, file_hash, oracle_rows


class FieldReadbackTest(unittest.TestCase):
    """Mutate disposable copies, never a historical recording or original fixture."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='lmtr-field-negative-')
        self.directory = Path(self.temp.name) / 'full'
        shutil.copytree(Path(os.environ['LMTR_FIELD_TEST_ROOT']) / 'full', self.directory)

    def tearDown(self):
        self.temp.cleanup()

    def alter(self, field, value):
        """Alter only one bounded original oracle fact, leaving the recording intact."""
        oracle = self.directory / 'oracle.jsonl'
        lines = oracle.read_text().splitlines()
        row = json.loads(lines[1])
        row[field] = value
        lines[1] = json.dumps(row)
        oracle.write_text('\n'.join(lines) + '\n')

    def test_all_profiles_are_actual_original_owner_parity(self):
        for profile in ('analysis', 'native', 'full'):
            report = check(Path(os.environ['LMTR_FIELD_TEST_ROOT']) / profile)
            self.assertEqual(3, report['observations'])
            self.assertEqual(1, report['unavailable'])
            self.assertTrue(report['source_unchanged'])

    def test_original_temperature_bit_hash_mismatch_rejects(self):
        row = list(oracle_rows(self.directory / 'oracle.jsonl'))[1]
        payloads = row['original_payloads']
        payloads['temperature']['hash'] = '0' * 64
        self.alter('original_payloads', payloads)
        before = file_hash(self.directory / 'capture.lmtr')
        with self.assertRaises(AssertionError):
            check(self.directory)
        self.assertEqual(before, file_hash(self.directory / 'capture.lmtr'))

    def test_wrong_original_source_sequence_rejects(self):
        self.alter('original_source_sequence', '999')
        with self.assertRaises(AssertionError):
            check(self.directory)

    def test_wrong_clock_domain_rejects(self):
        self.alter('original_clock_id', '00000000-0000-4000-8000-000000000999')
        with self.assertRaises(AssertionError):
            check(self.directory)

    def test_invented_intake_window_rejects(self):
        self.alter('intake_upper_monotonic_ns', '1')
        with self.assertRaises(AssertionError):
            check(self.directory)

    def test_false_same_observation_view_rejects(self):
        self.alter('original_native_transport_same_observation_view', False)
        with self.assertRaises(AssertionError):
            check(self.directory)

    def test_damaged_end_keeps_exact_prefix_and_source_immutable(self):
        file = self.directory / 'capture.lmtr'
        with file.open('r+b') as stream:
            stream.truncate(file.stat().st_size - 20)
        before = file_hash(file)
        result = check(self.directory, recover=True)
        self.assertEqual(3, result['observations'])
        self.assertIsNone(result['terminal'])
        self.assertEqual(before, file_hash(file))

    def test_overlong_jsonl_refuses_before_unbounded_parse(self):
        (self.directory / 'oracle.jsonl').write_bytes(b' ' * 65537)
        with self.assertRaises(AssertionError):
            next(oracle_rows(self.directory / 'oracle.jsonl'))


if __name__ == '__main__':
    unittest.main()
