# Accepted LMTR v1 companions and registry

R1b `r1b-review-2` was explicitly accepted on 2026-10-13, D1–D10 and historical
conversion approach B included. The [normative specification](../LMTR_WIRE_V1.md)
is canonical; [local JSON shapes](schema.json) retain exactly the reviewed 76
shape definitions. JSON Schema alone cannot enforce lexical integers, canonical
precision, semantic IDs, binary integrity, cross-field/time/index/view limits.

## Frozen baseline registry

- Container/schema 1.0, envelope 1; `LMTRREC1` / `LMTRCMT1`; header/footer 40/72 bytes.
- Record IDs 1 HEADER, 2 CHUNK, 3 INDEX_PAGE, 4 CHECKPOINT, 5 END; flags/reserved zero.
- Required core feature: `org.lmthermal.lmtr.core-v1`.
- Required restricted-view feature when used: `org.lmthermal.lmtr.contiguous-native-view-v1`.
- Mandatory codecs 0 STORED and 1 RFC1950-wrapped DEFLATE. ID2 is reserved/unassigned;
  production Zstd grammar/adoption is deferred. Unknown required features/codecs reject.
- Namespaced module/optional JSON retention does not redefine baseline semantics.
- Single-file Parent only; historical dataset/imported-timeline feature remains R7 work.

The [historical review companions](../lmtr-r1b/README.md) and 14 noncanonical
illustrations remain unchanged evidence, not a canonical R3 binary corpus. R3
must add its separately tracked corpus, independent implementations and real-platform
proof. Owner wire acceptance is not implementation conformity or end-user release.
Compatibility/version changes follow the canonical specification, not local edits.

Editorial equivalence is reproducible from repository root:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_acceptance.py --report docs/lmtr-v1/acceptance-validation.json
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_draft.py --jvm
```

The [acceptance report](acceptance-validation.json) verifies unchanged reviewed
normative §§2–16 and all 76 local shape definitions. It is not R3 conformity proof.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
