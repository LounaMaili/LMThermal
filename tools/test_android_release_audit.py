"""Deterministic host tests for ELF alignment refusal, not application/device tests."""
import struct
import unittest
from inspect_android_release import load_alignments


class ElfAlignmentTests(unittest.TestCase):
    def elf(self, alignment=16384, offset=0):
        data = bytearray(120)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<I', data, 64, 1)
        struct.pack_into('<QQ', data, 72, offset, 0)
        struct.pack_into('<Q', data, 112, alignment)
        return bytes(data)
    def test_actual_load_alignment_and_congruence_are_required(self):
        self.assertEqual([16384], load_alignments(self.elf()))
        for data in [self.elf(4096), self.elf(16384, 1), self.elf(24576), self.elf()[:100], b'not an elf']:
            with self.subTest(data=data[:6]), self.assertRaises(ValueError):
                load_alignments(data)
    def test_absence_of_load_segments_is_rejected(self):
        data = bytearray(self.elf());struct.pack_into('<I', data, 64, 4)
        with self.assertRaises(ValueError):load_alignments(data)


if __name__ == '__main__':unittest.main()
