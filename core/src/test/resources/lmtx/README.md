# Shared LMTX v1 conformance corpus

Normative schema: [LMTX_FORMAT_V1.md](../../../../../docs/LMTX_FORMAT_V1.md).
`corpus.json` pins complete archive SHA-256, size and expected checker outcome.
Each manifest independently inventories uncompressed member lengths/SHA-256.
These exact files are intended for the future Desktop #1 consumer, not production
Android assets. They contain no private current scene captures.

| File | Expected | Evidence |
|---|---|---|
| `temperature-only.lmtx` | valid | Synthetic 2×2 Float32: 20, negative zero, 31, 24; full-grid ROI, no unnecessary mask |
| `validity-mask.lmtx` | valid | Canonical 2×2 example: 20, invalid/+0 filler, 31, 24; mask 1,0,1,1; mean 25 |
| `no-valid-pixels.lmtx` | valid | All-zero mask and canonical zero fillers; counts without readings |
| `preview-only-160x120.lmtx` | valid | Synthetic PNG gradient; simulated visual source, no temperature/evidence fabrication |
| `alternate-7x19.lmtx` | valid | Synthetic 133 ordered Float32 samples; alternate geometry |
| `ht301-rich-sanitized.lmtx` | valid | Previously sanitized raw14 room/ceiling frame, current native-equivalent reconstruction, original transport/native/calibration evidence; no new hand/physical calibration claim |
| `newer-minor-optional.lmtx` | valid | Major 1/minor 7; unknown optional decimal retains 29 fractional digits |
| `unsupported-major.lmtx` | unsupported_major | Major 2 identity |
| `unsupported-required.lmtx` | unsupported_required_feature | Unknown required feature |
| `optional-extension-shape-palette.lmtx` | valid | Unknown optional namespaced JSON/blob, precise decimal, future declarative shape and palette; no required feature relies on them |
| `corrupt-sha.lmtx` | integrity_mismatch | Modified temperature bytes with intact ZIP CRC but stale manifest SHA |

HT-rich source is byte-for-byte `../fixtures/radiometric-room-settled.raw`, SHA-256
`fde6a4b803b68fcea07ab7b428f4769d22e896f01055969f43102bdc9270f5d6`.
Its existing [provenance](../fixtures/README.md) identifies the 2026-09-27 room/
ceiling capture, cleared identifier spans and Desktop revision. Spatial structure,
trailer and calibration remain. Fixture-generation thermometry is the existing
validated reference, not a new camera operation; the exporter itself copies the
current authoritative measurement. Independent physical accuracy is unvalidated.

Synthetic clocks truthfully lack event UTC. Fixed UUIDs are fixture identities,
not a template for runtime capture IDs. All runtime acquisitions allocate new IDs.

Regenerate using `:core:generateLmtxFixtures` (fixed timezone/JDK for byte reproducibility).
Run `:core:test` to verify the pinned corpus and malformed ZIP/JSON/binary cases;
`:core:checkLmtx -PlmtxFile=/absolute/path/file.lmtx` checks any individual file.
Unknown-value preservation is semantic with sufficient numeric precision, not
number spelling. Desktop interoperability and immutable derivative editing remain
pending; this corpus does not implement the Desktop importer.
