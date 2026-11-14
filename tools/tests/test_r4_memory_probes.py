"""Diagnostic schema/precision/recovery boundaries, not ART or physical qualification."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from r4_memory_probes import FIELDS, STATUSES, WIDTH, STRIDE, PRIMITIVE_BYTES, read_report, counter


class MemoryProbeReaderTests(unittest.TestCase):
    def header(self):
        return dict(format='r4-memory-probes-1', generation='17', rows=1, contended_attempts='0',
            capacity_exceeded=False, probe_failed=False, clock_failed=False, atomic=False,
            primitive_bytes=PRIMITIVE_BYTES, domains=[dict(id=k, fields=v) for k, v in FIELDS.items()], statuses=STATUSES)

    def row(self):
        values = [None] * WIDTH
        values[:6] = ['0', '17', '100', '999', '0', '0']
        for n, _ in enumerate(FIELDS):
            i = 6 + n * STRIDE
            values[i:i+3] = [str(101+n*2), str(102+n*2), '2']
        return dict(sample=0, values=values)

    def read(self, header=None, row=None):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'memory.jsonl'
            raw = (json.dumps(header or self.header())+'\n'+json.dumps(row or self.row())+'\n').encode()
            p.write_bytes(raw)
            result = read_report(p)
            self.assertEqual(p.read_bytes(), raw)
            self.assertEqual(result['source_sha256'], hashlib.sha256(raw).hexdigest())
            return result

    def test_actual_unknowns_are_preserved_without_any_atomic_or_admission_verdict(self):
        result = self.read()
        self.assertEqual(result['samples'], 1)
        self.assertFalse(result['header']['atomic'])
        self.assertIsNone(result['rows'][0]['NATIVE_CAPACITY']['values']['transfer_count'])

    def test_boolean_float_overflow_and_noncanonical_counter_tokens_fail(self):
        for x in [True, 0, 1.0, '1.0', '-1', '01', '１２', str(2**63)]:
            with self.assertRaises(ValueError):
                counter(x)
        self.assertEqual(counter(str(2**63-1)), 2**63-1)
        self.assertIsNone(counter(None))

    def test_ok_missing_values_and_unsupported_invented_values_are_rejected(self):
        for status, value in [('0', None), ('2', '0')]:
            row = self.row()
            row['values'][8] = status
            row['values'][9] = value
            with self.assertRaises(ValueError):
                self.read(row=row)

    def test_failed_partial_probe_can_preserve_a_known_scalar(self):
        row = self.row()
        row['values'][8:11] = ['3', '100', None]
        header = self.header()
        header['probe_failed'] = True
        result = self.read(header, row)
        self.assertEqual(result['rows'][0]['GC_BEFORE']['status'], 'FAILED')
        self.assertEqual(result['rows'][0]['GC_BEFORE']['values']['allocated'], 100)

    def test_schema_charge_capacity_generation_and_width_are_checked(self):
        for key, value in [('rows', True), ('rows', 65), ('atomic', True), ('primitive_bytes', 12), ('generation', '0')]:
            h = self.header()
            h[key] = value
            with self.assertRaises(ValueError):
                self.read(h)
        r = self.row()
        r['values'].pop()
        with self.assertRaises(ValueError):
            self.read(row=r)

    def test_gc_advanced_relation_is_checked_and_not_called_a_heap_floor(self):
        row = self.row()
        row['values'][5] = '2'
        for name, count in [('GC_BEFORE', '9'), ('GC_AFTER', '10')]:
            i = 6 + list(FIELDS).index(name)*STRIDE
            row['values'][i+2:i+7] = ['0', '100', '50', count, '2']
        self.read(row=row)
        row['values'][5] = '1'
        with self.assertRaises(ValueError):
            self.read(row=row)

    def test_truncation_duplicate_keys_and_nonfinite_tokens_never_pass(self):
        raws = [json.dumps(self.header()).encode(),
            (json.dumps(self.header())+'\n').encode(), b'{"format":1,"format":2}\n', b'{"format":NaN}\n']
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'partial'
            for raw in raws:
                p.write_bytes(raw)
                with self.assertRaises(ValueError):
                    read_report(p)
                self.assertEqual(p.read_bytes(), raw)

    def test_missing_domain_has_no_reused_previous_values(self):
        row = self.row()
        row['values'][6:6+STRIDE] = [None]*STRIDE
        row['values'][4] = '1'
        result = self.read(row=row)
        self.assertIsNone(result['rows'][0]['GC_BEFORE']['status'])
        row['values'][9] = '0'
        with self.assertRaises(ValueError):
            self.read(row=row)


if __name__ == '__main__':
    unittest.main()
