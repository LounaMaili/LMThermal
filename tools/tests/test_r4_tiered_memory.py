"""Finite diagnostic schedule conformance, independent of Android runtime costs or LMTR."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

from r4_memory_probes import FIELDS, STRIDE, TIERED_STATUSES, TIERED_SCHEDULE, read_report
from tools.tests import test_r4_memory_probes as reference


class TieredMemoryReaderTests(unittest.TestCase):
    def make(self):
        """Six separate read intervals, including a second due MemoryInfo row."""
        source = reference.MemoryProbeReaderTests()
        header = source.header()
        header.update(format='r4-memory-probes-2', rows=6, statuses=TIERED_STATUSES, schedule=dict(TIERED_SCHEDULE))
        rows = []
        for index in range(6):
            row = source.row()
            row['sample'] = index
            v = row['values']
            v[0], v[2], v[3] = str(index), str(100+index*1000), str(999+index*1000)
            for n in range(len(FIELDS)):
                i = 6+n*STRIDE
                v[i:i+2] = [str(101+index*1000+n*2), str(102+index*1000+n*2)]
            if index % 5:
                i = self.memory_offset()
                v[i:i+STRIDE] = [None]*STRIDE
                v[i+2] = '7'
            rows.append(row)
        return header, rows

    def memory_offset(self):
        return 6+list(FIELDS).index('ANDROID_MEMORY')*STRIDE

    def read(self, header, rows):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/'memory.jsonl'
            raw = ''.join(json.dumps(x)+'\n' for x in [header, *rows]).encode()
            p.write_bytes(raw)
            out = read_report(p)
            self.assertEqual(p.read_bytes(), raw)
            return out

    def test_schedule_exposes_all_four_unknown_rows_without_interpolation(self):
        h, rows = self.make()
        result = self.read(h, rows)
        for index in range(6):
            m = result['rows'][index]['ANDROID_MEMORY']
            self.assertEqual(m['status'], 'UNSUPPORTED' if index % 5 == 0 else 'NOT_SCHEDULED')
            self.assertTrue(all(x is None for x in m['values'].values()))
            if index % 5:
                self.assertIsNone(m['begin_ns'])
                self.assertIsNone(m['end_ns'])

    def test_unknown_schedule_or_imprecise_period_is_rejected(self):
        for value in [0, True, 5.0, 6, '5']:
            h, rows = self.make()
            h['schedule']['android_memory_every_rows'] = value
            with self.assertRaises(ValueError): self.read(h, rows)

    def test_missing_or_invented_time_and_carried_forward_values_are_rejected(self):
        h, rows = self.make()
        for change in ['time', 'value', 'missing', 'unannounced']:
            changed = copy.deepcopy(rows)
            i = self.memory_offset()
            if change == 'time': changed[1]['values'][i] = '1111'
            if change == 'value': changed[1]['values'][i+3] = '0'
            if change == 'missing': changed[1]['values'][i+2] = None
            if change == 'unannounced':
                changed[1]['values'][i:i+3] = ['1111', '1112', '2']
            with self.assertRaises(ValueError): self.read(h, changed)

    def test_due_row_or_other_domain_cannot_be_labeled_not_scheduled(self):
        h, rows = self.make()
        for index, name in [(0, 'ANDROID_MEMORY'), (1, 'JAVA')]:
            changed = copy.deepcopy(rows)
            i = 6+list(FIELDS).index(name)*STRIDE
            changed[index]['values'][i:i+STRIDE] = [None]*STRIDE
            changed[index]['values'][i+2] = '7'
            with self.assertRaises(ValueError): self.read(h, changed)

    def test_stop_during_read_preserves_no_later_scheduled_or_actual_read(self):
        h, rows = self.make()
        rows = rows[:2]
        h['rows'] = 2
        v = rows[1]['values']
        v[4] = '1'
        v[self.memory_offset():] = [None]*len(v[self.memory_offset():])
        out = self.read(h, rows)
        self.assertIsNone(out['rows'][1]['ANDROID_MEMORY']['status'])

    def test_reference_reader_still_rejects_extended_status_table(self):
        source = reference.MemoryProbeReaderTests()
        h = source.header()
        h['statuses'] = TIERED_STATUSES
        with self.assertRaises(ValueError): self.read(h, [source.row()])


if __name__ == '__main__':
    unittest.main()
