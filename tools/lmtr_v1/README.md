# Independent Python LMTR v1 reference tools

These standard-library host tools implement the accepted
[`docs/LMTR_WIRE_V1.md`](../../docs/LMTR_WIRE_V1.md) bytes directly. They do not
import the draft documentary checker or Kotlin implementation. The official local
schema is used for shapes; exact JSON, framing, semantic closure, codecs, payloads,
statistics, references and chronology have independent executable checks here.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools.lmtr_v1.tests.test_reference -v
python3 -m tools.lmtr_v1 canon metadata.json
python3 -m tools.lmtr_v1 write-plan plan.json new-file.lmtr
python3 -m tools.lmtr_v1 check new-file.lmtr
python3 -m tools.lmtr_v1 check new-file.lmtr --entry 1
python3 -m tools.lmtr_v1 check new-file.lmtr --full
python3 -m tools.lmtr_v1 check incomplete.lmtr --recover --full
python3 -m tools.lmtr_v1 scan damaged.lmtr
python3 -m tools.lmtr_v1 seek example.lmtr 1000000 --stream primary
```

`canon` emits UTF-8 canonical bytes with no newline. Other commands emit one JSON
result; a rejected operation exits 2. Opening is read-only. `opened_records`,
`selected_chunks`, `full`, `full_verified_prefix` and explicit `salvaged_chunks` are distinct scopes. A valid
END records declared completion, not external producer close acknowledgement.
`--entry` verifies all logical data in its selected CHUNK, not just one pixel.
`seek` searches recorder-intake time with optional declared-stream participation;
segment events involve their explicit stream inventory. A not-found query has null
selected fields and never invents an observation. Original sensor clocks are separate.
`--recover` follows bounded tail/predecessor limits; `--full` additionally verifies
the recovered checkpoint's indexed prefix. Recovery never repairs/appends a source.

## Non-wire generation plans

A plan contains `header` and `chunks`. Each item contains complete accepted `metadata`
and either `payload_recipes` keyed by `entry_id:payload_id`, or physical `blocks`
with `id`, `bytes_base64`, and `codec`. Recipes support `hex`, `f32_values`,
`f32_constant`, `u16_constant`, and `transport` (native recipe plus trailer byte count).
They describe synthetic corpus data, not camera acquisition or Celsius inference.
Views are checked against independently supplied native recipes. The writer updates
actual compression lengths, group offsets and integrity hashes, appends rolling
index pages/checkpoints, and refuses END with an open segment. Output uses exclusive
creation, never replaces an existing source. Recipe/base64 transport limits do not
relax record JSON's 16 KiB string bound; every wire body is checked separately.

## Scope and explicit limitations

- Full checks cover the accepted local metadata shapes, exact Decimal semantics,
  content hashes, core matrix/mask/ROI and payload authority, chunk grouping and
  contiguous views, STORED and bounded RFC1950, entry/time/segment ordering,
  immutable paged indexes, checkpoint summaries and read-only prefix recovery.
- The reference writer has bounded rolling index state. Reader navigation caches
  at most two pages per depth, at most 16 metadata records and 8 MiB. To bound its
  diagnostic JSON output and reference bookkeeping, this implementation refuses
  more than 65,536 distinct records/logical payloads with `local_resource_limit`.
  This is a disclosed tool budget, **not** an LMTR duration/format limit.
- Full streaming validation uses this bounded reporting budget; it does not claim
  indefinite-runtime or arbitrary-duration memory certification. Unknown optional
  extensions are retained semantically, not interpreted as module algorithms.
- Fast recovery reconstructs trusted-body navigation damage from bounded checked
  predecessor links; committed source corruption remains fatal. Explicit `scan`
  exposes independently verified ranges and records source integrity damage without
  certifying global chronology/index/completion. Its API is cancellable between
  records. Broken framing stops safely; arbitrary-range discovery and an external
  rebuilt-index cache are not implemented. Checkpoint recovery without `--full`
  does not certify its unvisited prefix or every prior source-counter observation.
- New-file append uses separate body/footer flush/fsync barriers, with an injected
  body-sync failure regression; unrecorded finalization UTC remains unknown.
- No asynchronous recorder admission/timers, live frame acquisition, directory
  persistence/provider durability/power loss, SAF providers, Kotlin/ART execution or native Windows run
  is demonstrated by these tools. The host writer does not advertise production
  Stop/durability conformance. These gates require separate evidence.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
