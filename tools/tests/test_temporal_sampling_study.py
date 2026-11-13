"""Finite model regressions; do not equate these with an Android periodic recorder."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from temporal_sampling_study import exact_period, simulate, wire_plan
from lmtr_v1.metadata import SHAPES


class SamplingStudyTests(unittest.TestCase):
    def test_requested_rates_have_exact_periods_except_fifteen(self):
        self.assertEqual([exact_period(h) for h in (5,10,25)],[200000000,100000000,40000000])
        with self.assertRaises(ValueError): exact_period(15)

    def test_twenty_five_hz_source_is_not_claimed_as_a_ten_hz_sensor_clock(self):
        result=simulate(10,[(i,i*40000000,True) for i in range(1,26)],1000000000)
        rows=result['rows'];self.assertEqual(len(rows),10)
        self.assertEqual(result['intentionally_not_selected'],15)
        self.assertEqual([r['receipt_ns']-r['deadline_ns'] for r in rows],[20000000,0]*5)
        self.assertEqual([r['selected_counter'] for r in rows],list(range(10)))
        self.assertEqual([r['acquisition_counter'] for r in rows],[3,5,8,10,13,15,18,20,23,25])

    def test_unavailable_is_an_observed_slot_not_a_missed_deadline(self):
        rows=simulate(5,[(8,200000000,False)],200000000)['rows']
        self.assertEqual(rows[0]['kind'],'unavailable')
        self.assertEqual(rows[0]['selected_counter'],0)

    def test_regressed_clock_or_counter_is_refused_instead_of_clamped(self):
        for callbacks in ([(1,200000000,True),(2,199999999,True)],
                          [(1,200000000,True),(1,400000000,True)]):
            with self.assertRaises(ValueError):simulate(5,callbacks,1000000000)

    def test_unknown_or_negative_witness_cannot_become_a_schedule_time(self):
        for end in (None,-1):
            with self.assertRaises(ValueError):simulate(5,[],end)
        with self.assertRaises(ValueError):simulate(5,[(1,None,True)],1000000000)

    def test_idle_stop_records_elapsed_slots_with_detection_time_not_fake_receipt(self):
        rows=simulate(5,[(1,200000000,True)],1000000000)['rows']
        self.assertEqual(rows[-1],dict(kind='missed_schedule',first=2,last=5,detected_ns=1000000000))
        self.assertNotIn('receipt_ns',rows[-1])

    def test_late_callback_does_not_create_catchup_matrices(self):
        rows=simulate(10,[(7,450000000,True)],450000000)['rows']
        self.assertEqual(rows[0]['first'],1);self.assertEqual(rows[0]['last'],3)
        self.assertEqual(rows[1]['slot'],4);self.assertEqual(rows[1]['receipt_ns'],450000000)
        self.assertEqual(sum(r['kind']=='available' for r in rows),1)

    def test_wire_examples_preserve_mask_and_all_distinct_event_classes(self):
        for hz in (5,10,25):
            plan=wire_plan(hz);entries=plan['chunks'][0]['metadata']['entries']
            SHAPES.check('Chunk',plan['chunks'][0]['metadata'])
            self.assertEqual(entries[1]['measurement']['validity'],'partially_valid')
            self.assertEqual(entries[2]['measurement']['status'],'unavailable')
            self.assertEqual(entries[3]['category'],'missed_schedule')
            self.assertEqual(entries[4]['category'],'writer_overload')
            self.assertEqual(entries[5]['observation_sequence'],'3')


if __name__=='__main__': unittest.main()
