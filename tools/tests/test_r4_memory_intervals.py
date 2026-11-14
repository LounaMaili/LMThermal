"""GC witness intervals cannot become point timestamps or fabricated sparse observations."""
import copy
import unittest
from analyze_r4_memory_intervals import analyze
from r4_memory_probes import FIELDS


class MemoryIntervalTests(unittest.TestCase):
    def report(self):
        """Known changes between rows; probes are separate and an optional domain is absent."""
        rows = []
        for i in range(3):
            row = {}
            for n, (name, fields) in enumerate(FIELDS.items()):
                row[name] = dict(begin_ns=i*100+2*n, end_ns=i*100+2*n+1, status='OK',
                                 values={f: 10 for f in fields})
            for name in ['GC_BEFORE', 'GC_AFTER']:
                row[name]['values'].update(count=5+i, allocated=100*i, freed=80*i, time_ms=2*i)
            row['JAVA']['values']['occupied'] = 300-i*20
            row['PUBLISHED_ROLES']['values'].update(source_generation_before=2, source_generation_after=2)
            rows.append(row)
        return dict(rows=rows, header=dict(domains=[dict(id=k, fields=v) for k, v in FIELDS.items()], clock_failed=False),
                    source_sha256='test', source_unchanged=True)

    def test_gc_changes_keep_outer_counter_brackets_and_all_original_probe_instants(self):
        r = self.report()
        out = analyze(r)
        self.assertEqual(len(out['gc_changes']), 2)
        first = out['gc_changes'][0]
        self.assertEqual(first['possible_interval_ns'], [16, 101])
        metrics = first['bracketing_metrics']['JAVA.occupied']
        self.assertEqual(metrics['left']['relation'], 'before_possible_change')
        self.assertEqual(metrics['right']['relation'], 'after_possible_change')
        self.assertEqual(metrics['delta'], -20)
        self.assertEqual(metrics['left']['begin_ns'], 2)

    def test_within_row_advance_does_not_invent_two_java_readings(self):
        r = self.report()
        r['rows'] = r['rows'][:1]
        r['rows'][0]['GC_AFTER']['values']['count'] += 1
        event = analyze(r)['gc_changes'][0]
        m = event['bracketing_metrics']['JAVA.occupied']
        self.assertEqual(m['left']['relation'], 'overlaps_possible_change')
        self.assertIsNone(m['delta'])

    def test_missing_graphics_remain_unknown_in_metrics_and_projection(self):
        r = self.report()
        d = r['rows'][1]['ANDROID_MEMORY']
        d.update(begin_ns=None, end_ns=None, status='NOT_SCHEDULED', values={k: None for k in d['values']})
        out = analyze(r)
        self.assertIsNone(out['gc_changes'][0]['bracketing_metrics']['ANDROID_MEMORY.summary_graphics']['delta'])
        self.assertEqual(out['tiered_call_opportunity_projection']['omitted_reference_calls'], 1)

    def test_clock_failure_and_regression_never_give_a_qualified_gc_interval(self):
        r = self.report()
        r['header']['clock_failed'] = True
        self.assertIsNone(analyze(r)['gc_changes'][0]['possible_interval_ns'])
        r = self.report()
        r['rows'][1]['GC_BEFORE']['values']['count'] = 2
        self.assertEqual(len(analyze(r)['gc_counter_regressions']), 1)

    def test_unknown_counters_and_aliases_are_not_filled_and_input_is_immutable(self):
        r = self.report()
        for row in r['rows']:
            row['GC_BEFORE']['values']['count'] = None
            row['GC_AFTER']['values']['count'] = None
            row['PUBLISHED_ROLES']['values']['source_is_rendered'] = None
        before = copy.deepcopy(r)
        out = analyze(r)
        self.assertEqual(out['gc_changes'], [])
        self.assertEqual(out['roles']['alias_counts']['source_is_rendered'], {'unknown': 3})
        self.assertEqual(r, before)

    def test_known_count_from_failed_probe_keeps_its_failure_in_the_event(self):
        r = self.report()
        r['rows'][0]['GC_AFTER']['status'] = 'FAILED'
        event = analyze(r)['gc_changes'][0]
        self.assertEqual(event['counter_statuses'], ['FAILED', 'OK'])
        self.assertFalse(event['counter_status_qualified'])


if __name__ == '__main__':
    unittest.main()
