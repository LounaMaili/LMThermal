"""Offline probe-domain, precision and hypothetical-policy limits; not Android conformance."""
import tempfile
from pathlib import Path
import unittest
from r4_memory_study import AbsoluteBudgetShadow,integer,relative_trip,minimum_reference_for_held_points,normalize,counter_deltas,read_immutable


class MemoryStudyTests(unittest.TestCase):
    def test_relative_guard_has_the_same_strict_boundary(self):
        self.assertFalse(relative_trip(301993984,436211712))
        self.assertTrue(relative_trip(301993984,436211713))
        self.assertIsNone(relative_trip(None,436211713))

    def test_unknown_or_inexact_counters_cannot_be_manufactured_as_zero(self):
        self.assertIsNone(integer(None))
        for x in (True,1.0,'1.0','-1','１２',2**63):
            with self.assertRaises(ValueError):integer(x)
        self.assertEqual(integer('25018507520'),25018507520)

    def test_initial_reference_sensitivity_is_fixed_input_algebra_only(self):
        trace=[314155008,426303488,362545152,493776896]
        self.assertEqual(minimum_reference_for_held_points(trace),359559168)
        self.assertTrue(any(relative_trip(301993984,x) for x in trace))
        self.assertFalse(any(relative_trip(388730880,x) for x in trace))

    def test_health_and_later_diagnostic_values_are_never_merged(self):
        r=normalize(dict(elapsed_ns='3329313071',health=dict(rss_bytes='493776896'),rss_bytes='499560448'),0)
        self.assertEqual(r['health']['rss_bytes'],493776896)
        self.assertEqual(r['diagnostic']['rss_bytes'],499560448)
        self.assertIsNone(r['health']['java_used_bytes'])

    def test_missing_or_regressed_gc_counter_does_not_make_a_zero_delta(self):
        r=normalize({},0);s=normalize({'gc_runtime':{'art.gc.gc-count':'5'}},1)
        self.assertIsNone(counter_deltas([r,s])['art.gc.gc-count'])
        self.assertIsNone(counter_deltas([s,normalize({'gc_runtime':{'art.gc.gc-count':'4'}},2)])['art.gc.gc-count'])

    def test_unqualified_absolute_budget_never_accepts_real_probes(self):
        for m in (AbsoluteBudgetShadow(),AbsoluteBudgetShadow(900000000)):
            self.assertEqual(m.observe(300000000)['verdict'],'inconclusive')

    def test_hypothetical_hard_stop_is_not_debounced_as_a_transient(self):
        model=AbsoluteBudgetShadow(1000,True)
        self.assertEqual(model.observe(1001)['verdict'],'would_stop_absolute')
        self.assertEqual(model.observe(10,'heap_low')['verdict'],'would_stop_independent')

    def test_rising_samples_are_only_advisory_and_never_permission_to_record(self):
        model=AbsoluteBudgetShadow(1000,True)
        for x in (1,2,3):self.assertFalse(model.observe(x)['rising_sample_advisory'])
        result=model.observe(4);self.assertTrue(result['rising_sample_advisory'])
        self.assertEqual(result['verdict'],'below_hypothetical_ceiling_not_admission')

    def test_bounded_scalar_read_does_not_edit_its_input(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'sample.jsonl';raw=b'{"elapsed_ns":"1"}\n';p.write_bytes(raw)
            rows,info=read_immutable(p,True)
            self.assertEqual(rows,[{'elapsed_ns':'1'}]);self.assertEqual(info['bytes'],len(raw));self.assertEqual(p.read_bytes(),raw)
            p.write_bytes(b'{}\n'*257)
            with self.assertRaises(ValueError):read_immutable(p,True)


if __name__=='__main__':unittest.main()
