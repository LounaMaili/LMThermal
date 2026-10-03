# Kotlin thermometry goldens

These are development/test data only, never product assets. Git attributes mark
raw/float artifacts binary to preserve exact bytes and prevent text merges. Original raw files are
copied unchanged from sanitized Desktop `tests/fixtures/`; no new private captures.
`manifest.json` lists SHA-256 for every artifact, the exact Desktop oracle source,
and the five executed-native `.npy` reference hashes. `tools/generate_thermometry_goldens.py`
is the reproducible exporter (run with Desktop's `.venv/bin/python`).

The six selected real frames cover initial reconstruction, first raw14, normal range,
shutter-held, settled room and operator-confirmed hand. Their original provenance is
in Desktop `tests/fixtures/README.md`. Identifier spans were sanitized there.
Room/hand spatial structure is retained. The initial fixture's shuffled image cannot
validate extrema coordinates. Held/early frames are LUT inputs, not readiness evidence.
The existing settled-room raw in `../fixtures` is reused, not duplicated.

`.lut.f32` is 16384 little-endian float32 entries exported by the current Desktop
engine. For five fixtures, every finite bit and the entire NaN pattern are first
compared against the saved executed official x86_64 table. Export canonicalizes
undefined entries to NumPy NaN; NaN payload/sign bit identity is not asserted.
The hand LUT is a Desktop-derived reference, not a newly executed APK reference.
Two `.matrix.f32` files contain all 110592 row-major float32 outputs from Desktop;
`.properties` records integer inputs and unsigned float32 bit patterns for intermediates
and summary outputs. The exporter fails on any native finite-bit or NaN-pattern mismatch.

Synthetic cases modify only the settled-room stored distance (20 → effective 60) or
lookup-base word (0 → uint16 wrap). Tests derive these frames in memory, so no
redundant raw copies are stored. Their LUT/intermediate goldens exercise arithmetic,
not real camera support for a different setting or validation of physical accuracy.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
