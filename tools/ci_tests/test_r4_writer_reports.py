"""Host-only report checks: no subprocess, emulator, ADB or recording workload is invoked."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

PATH = Path(__file__).resolve().parents[1] / 'check_lmtr_r4_writer_reports.py'
SPEC = importlib.util.spec_from_file_location('writer_reports', PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def sample(unit='ns', completed=True):
    """One fully attributed valid metadata sample, not runtime/recording evidence."""
    return dict(count=1, capacity=512, observed_count='1', valid_count='1', omitted='0',
                invalid='0', interrupted='0' if completed else '1', counter_overflow=False,
                sum_overflow=False, **{'sum_' + unit: '7', 'min_' + unit: '7',
                'max_all_' + unit: '7', 'p50_' + unit: '7', 'p95_' + unit: '7', 'max_' + unit: '7'})


class WriterReportsTest(unittest.TestCase):
    """Reject misleading totals while preserving valid interrupted/limited data."""
    def test_valid_complete_and_interrupted_samples(self):
        for completed in (False, True):
            MODULE.distribution(sample(completed=completed), 'ns', completed, [])

    def test_missing_counter_is_not_zero(self):
        value = sample(); del value['observed_count']
        with self.assertRaises(KeyError): MODULE.distribution(value, 'ns', True, [])

    def test_wrapped_or_inconsistent_sum_refused(self):
        for value in ('-1', '8', str(2**63)):
            data = sample(); data['sum_ns'] = value
            with self.assertRaises(ValueError): MODULE.distribution(data, 'ns', True, [])

    def test_explicit_overflow_is_limited_not_a_fake_exact_sum(self):
        data = sample(); data.update(sum_overflow=True, sum_ns=None)
        limitations = []; MODULE.distribution(data, 'ns', True, limitations)
        self.assertIn('sum_overflow', limitations)
        data['sum_ns'] = '7'
        with self.assertRaises(ValueError): MODULE.distribution(data, 'ns', True, [])

    def test_off_mode_requires_explicit_null_and_mode_identity(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); path = root / 'extracted/files/r4/recorder-stress-test'
            path.mkdir(parents=True); stress = path / 'stress.json'
            cases = [{'codec': c, 'writer_metrics_mode': 'off', 'writer_metrics': None} for c in (0, 1)]
            value = dict(writer_metrics_mode='off', cases=cases)
            stress.write_text(json.dumps(value))
            self.assertEqual(MODULE.assess(root, 'off')['status'], 'usable')
            self.assertTrue(MODULE.assess(root, 'on')['issues'])
            del value['cases'][0]['writer_metrics']; stress.write_text(json.dumps(value))
            self.assertTrue(MODULE.assess(root, 'off')['issues'])

    def test_missing_runtime_report_is_not_success(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertEqual(MODULE.assess(Path(temp), 'on')['status'], 'invalid_or_missing')


if __name__ == '__main__': unittest.main()
