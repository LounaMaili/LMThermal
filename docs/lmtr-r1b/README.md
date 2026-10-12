# LMTR R1b review companions

**DRAFT `r1b-review-2`; NONCANONICAL metadata; no owner acceptance or R3 corpus.**

- [Complete proposed contract](../LMTR_WIRE_V1_DRAFT.md).
- [Owner decisions/ambiguities](../LMTR_R1B_OWNER_DECISIONS.md).
- [Local-shape JSON Schema](schema.json): 76 definitions; prose additionally defines
  lexical integers/decimal precision, closure/ordering/clock/policy, binary and
  cross-field rules. A general JSON Schema validator alone does not establish a
  valid recording; its `integer` also cannot enforce the proposed integer-token
  spelling. No remote `$ref` resolution or network dependency is required.
- [Fourteen illustrative cases](examples.json): concrete HEADER/CHUNK metadata,
  synthetic logical-byte recipes and symbolic record plans. These review-1 cases
  remain byte-identical in review-2. The first thirteen
  cover the requested cases; the extra case covers independent visible geometry
  and a late receipt. HT layouts contain synthetic values, not official thermometry
  outputs or new camera captures. No footer/index offsets or `.lmtr` binaries are
  generated. A complete plan is an illustration, not an existing accepted file.
- [Executed host-check report](validation-report.json): input hashes, checks and
  unexecuted implementation/platform/timer gates. It is not conformance approval.

From repository root, with Python standard library only:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_draft.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_draft.py --jvm --report docs/lmtr-r1b/validation-report.json
```

The [checker](../../tools/check_lmtr_draft.py) is documentation tooling only. It
validates its supported schema subset and selected cross-field/recipe relationships,
context/segment hashes, mask/full-grid/view/statistics facts, exact decimal spelling,
Unicode ordering, signed zero, strict integer rejection, canonical numeric bounds,
semantic-ID versus byte-hash domains and framing-width/CRC arithmetic.
It never opens USB/ADB, reads/writes a binary recording or supplies a runtime codec,
index, recovery or idle-timer implementation. There are no canonical binary fixtures.
Unknown required and corrupt-committed cases are **expected rejections**, not failures.

`--jvm` additionally invokes the [host library probe](../../tools/LmtrJsonLibraryProbe.java)
with an available JDK source launcher. It compares selected exact BigDecimal number
operations with Python Decimal, scalar versus UTF-16 sorting and finite/negative-zero
float bits. The default checker needs only Python; the committed report records the
optional JDK run. Neither probe parses a complete JVM JSON document or validates
Kotlin/ART, Windows, timing, dataset conversion or runtime resource behavior.
The wire draft's §3.3 lists required future cross-language R3 vector families;
no canonical R3 binary or runtime parser/serializer is provided.

After owner acceptance, separate Kotlin/Python implementations and R3 vectors must
verify the complete contract on Android/Linux/native Windows. New generic grammar,
canonical JSON/context hashes and deadline/checkpoint performance are untested.
Accepted R1a/LMTX v1 and original R2 reports/packets remain unchanged.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
