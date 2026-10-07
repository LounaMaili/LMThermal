# R2 hostile-input and native-view evidence

**Disposable prototypes, not a production parser or canonical R3 corpus.**

## Executed validation

20 JVM R2 tests and seven independent Python tests cover parameterized cases:

- Negative and overflowing lengths/offsets; checked signed-64 addition; record
  lengths/ordinals/types and excessive dimensions before payload allocation.
- Decoded/stored aggregate chunk ceilings before decompression; exact zlib ending,
  expansion refusal, trailing bytes, unsupported codecs and required features.
  Final explicit Pixel tests also reject nonzero Zstd dictionary IDs and windows
  larger than 8 MiB before payload allocation.
- Strict UTF-8/JSON, duplicate fields, nonfinite values, depth/items/string limits
  and sufficient numeric precision. Test-only JSON optimization retains semantic
  parity with the unchanged Accepted LMTX helper, including unknown decimal values.
- Index backward offsets/ordinals, cycles, ordered nonoverlapping ranges, depth,
  wrong leaf type and false leaf bounds. Rebuildable damaged index recovery remains
  separate from explicit committed-payload integrity failure.
- Invalid context references, block/parent/logical hash corruption.
- Negative/out-of-range/overflowing view offset, shape/length/dtype/endian mismatch,
  nested/view-of-view, nonmaterialized parent, strided/transformed/cross-frame/
  cross-chunk forms. These reject before logical payload exposure.

## Restricted-view result

HT Full acquisition is exact 224,256 bytes. Native view is offset 0, length 221,184,
shape [288,384], u16le, encoding `org.lmthermal.ht301.raw14`. The writer compares it
with separately owned raw bytes, then physically stores only transport. Logical
native SHA and materialized parent SHA are independently checked. No masking of
high bits, matrix cropping, coordinate rotation or packet synthesis.

Android-written synthetic packets pass all three profiles/codecs on Android and
independent Linux: complete files, masks, contexts, explicit gaps, views, recovered
missing-END files and corrupt files. A synthetic 13×7 geometry also passes. Real
Windows remains pending; the [same-byte packet](WINDOWS_VALIDATION.md) is ready.
Recommend retaining **only this restricted view class** for R1b consideration.
General transformations, strides, nesting and cross-frame references remain excluded.

## Boundaries

Bounds do not prove practical memory for every allowed large geometry. The sparse
large singleton used ~229.6 MB Java heap in the JVM test. Normal HT live throughput
and UI failure are independently reported. No production deployment or physical
calibration claim follows from parser/byte parity.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
