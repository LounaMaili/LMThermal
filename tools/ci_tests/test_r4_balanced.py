"""Camera-free orchestration tests: neither ADB nor subprocess is executed."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('balanced', Path(__file__).resolve().parents[1] / 'run_lmtr_r4_balanced.py')
balanced = importlib.util.module_from_spec(spec)
spec.loader.exec_module(balanced)


class BalancedTest(unittest.TestCase):
    def test_order_remains_fixed_after_negative_outcomes(self):
        with tempfile.TemporaryDirectory() as root:
            seen = []
            def fake(command, check):
                seen.append(command[-1])
                return SimpleNamespace(returncode=(20 if len(seen) == 1 else 10))
            self.assertEqual(20, balanced.execute(Path(root) / 'new', fake))
            self.assertEqual(['off', 'on', 'on', 'off'], seen)
            self.assertEqual(4, len(list((Path(root) / 'new').glob('*-receipt.json'))))

    def test_existing_destination_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(FileExistsError):
                balanced.execute(Path(root), lambda *a, **k: self.fail('must not run'))

    def test_os_error_does_not_remove_a_planned_arm(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            def fake(*args, **kwargs):
                calls.append(1)
                raise OSError('synthetic launch refusal')
            self.assertEqual(20, balanced.execute(Path(root) / 'new', fake))
            self.assertEqual(4, len(calls))
