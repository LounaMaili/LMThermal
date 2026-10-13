# LMTR v1 synthetic canonical R3 corpus

This corpus implements the accepted [LMTR v1 wire contract](../LMTR_WIRE_V1.md)
for camera-independent cross-language verification. The source shapes and original
14 illustrative draft scenarios remain unchanged. R3 integration/closure and native
Windows acceptance are separate gates; checked synthetic bytes establish no physical
calibration, production recorder, real-time timer, queue/admission or power-loss proof.

## Ownership, provenance and reproducibility

- `lmtr-contract/src/test/resources/lmtr/manifest.json` is the inventory: every
  fixture's immutable SHA-256, actual byte length, exact expected classifications,
  summary/counts/logical hashes and read scope; generator/contract/reference-source
  hashes and Python/zlib encoder provenance are pinned there.
- `plans/` contains exact-decimal JSON source plans for supported positive cases.
  `payload_recipes` is **test transport**, not accepted LMTR wire syntax. Recipes
  create exact logical bytes before the reference writer validates their descriptors,
  context/segment closure and complete journal.
- `files/` contains actual LMTR bytes. Every source is explicitly simulated;
  numerical values, clocks, identifiers and UTC are fixed synthetic inputs. Native
  HT-301-shaped data is a synthetic encoding/shape vector, never a camera capture
  or thermometry reconstruction.
- All positive cases are written by the independently implemented Python writer and
  reopened with its full streaming integrity pass. Kotlin must independently read
  them, write corresponding plans and expose equal preserved logical bytes/counters.
  Equal DEFLATE *logical* bytes are required; compressed byte identity between
  independent implementations/library versions is not generally required.
- The isolated raw mutator is used **only** for intentional negative fixtures. It
  cannot bypass production-writer checks. Where a later validation layer is the
  target, earlier body/block/logical hashes are kept correct intentionally; malformed
  committed metadata and promised payload corruption must not become benign gaps.
- Frozen single-file lineage binds a real, immutable synthetic parent fixture;
  multi-file historical Desktop conversion remains unsupported by baseline v1.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/generate_lmtr_corpus.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/generate_lmtr_corpus.py --check
```

Generation uses an isolated temporary directory, validates every resulting file,
then publishes only its owned plans/files/inventory. `--check` regenerates and checks
exact membership and every byte; it never modifies the published corpus. Python and
zlib runtime/source changes are visible provenance changes, not silently repinned
reference bytes.

## Coverage

The current inventory has **51 files: 33 positive and 18 negative**, about **2.75 MiB**
of binary input. The manifest is authoritative for exact sizes and outcomes.

Positive coverage:

- accepted illustrations 01–10 and 14, each STORED and DEFLATE-1;
- masked temperature-only and alternative dimensions; HT-shaped Analysis/Native/Full
  with bounded same-observation acquisition views; shutter/unavailable observations;
  known counted writer gaps, preview-only, source replacement, exact optional values,
  opaque extension evidence and independent visible stream;
- no-valid full Float32 plane with exact +0 filler/all-zero mask;
- non-raw14 big-endian native-only integers and exact native Float64 nonfinite/NaN
  payload/negative-zero bits without claiming primary temperature authority;
- independent native/acquisition evidence with no fabricated dedup;
- context replacement and two chunks carrying independent local context closure;
- qualified sensor ticks with unknown frequency, original receipt clocks, periodic
  sampling, a finalized empty recording and supported single-file continuation/
  labelled reprocessing with explicit algorithm/settings and immutable parent bytes.

Negative coverage:

- missing/torn END, torn CHUNK and invalid uncommitted footer: read-only verified
  prefix, unknown tail and no invented readings;
- committed body, decoded-block and logical-hash corruption: explicit integrity
  errors, including when recovery is requested;
- malformed committed UTF-8/JSON, hostile header length, unknown required feature,
  reserved Zstd codec and forbidden known union field;
- corrupt index/checkpoint, out-of-parent native view, nonbinary validity mask,
  nonfinite valid temperature and negative-zero invalid filler.

Index/checkpoint corruption is currently an explicit navigation error in these
negative vectors. It does not claim that automatic repair or a fully implemented
separate salvage/rebuilt-index workflow exists. Fixtures expecting recovered
prefixes are independently distinguished from these corruption cases.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
