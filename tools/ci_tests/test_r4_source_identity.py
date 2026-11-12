"""Offline evidence identity: a reference checkout is not the workflow's event SHA."""
import sys
from pathlib import Path
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from analyze_lmtr_r4_api35 import tested_source_sha


class SourceIdentityTests(unittest.TestCase):
    def test_paired_reference_uses_verified_checkout_receipt(self):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root);(path/'source-sha.txt').write_text('a'*40+'\n');issues=[]
            self.assertEqual('a'*40,tested_source_sha(path,{'GITHUB_SHA':'b'*40,'expected_source_sha':'a'*40},issues))
            self.assertEqual([],issues)
    def test_missing_or_conflicting_receipt_is_not_a_valid_identity(self):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root);issues=[]
            self.assertIsNone(tested_source_sha(path,{'GITHUB_SHA':'a'*40},issues));self.assertTrue(issues)
            (path/'source-sha.txt').write_text('b'*40);issues=[]
            self.assertIsNone(tested_source_sha(path,{'GITHUB_SHA':'a'*40},issues));self.assertTrue(issues)
    def test_historical_single_checkout_defaults_to_event_sha(self):
        with tempfile.TemporaryDirectory() as root:
            path=Path(root);(path/'source-sha.txt').write_text('a'*40);issues=[]
            self.assertEqual('a'*40,tested_source_sha(path,{'GITHUB_SHA':'a'*40},issues))
            self.assertEqual([],issues)
