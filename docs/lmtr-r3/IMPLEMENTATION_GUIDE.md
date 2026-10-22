# R3 implementation structure and boundaries

> **Integration update:** PR #14 merged at `8eb4aceaf8bb36a8061c932b3121f898b81b4af3`
> from reviewed `fb857b6`; #8 is closed for finite R3 contract conformance. Final Windows
> run [38393731476](https://github.com/LounaMaili/LMThermal/actions/runs/38393731476)
> tested that exact head. Older pending/draft statements below are checkpoint history.
> [R4 foundation](../lmtr-r4/README.md) now reuses extracted `LmtrAppendSession` mechanics;
> `LmtrWriter.writePlan` retains recipe-only fixture generation. The frozen wire/corpus
> are unchanged; ART and actual Android recording remain unvalidated.


Authority: [accepted LMTR v1](../LMTR_WIRE_V1.md) at
`8c343cd6cf1ca11eff4f3bbaa5c2b10dfbbf645f`. This guide describes the existing tools;
it neither replaces nor amends that contract.

## Independent implementation layers

| Responsibility | Kotlin/JVM | Independent Python |
|---|---|---|
| Exact JSON, scalar ordering, precision, lexical/resource limits | `LmtrJson` / `JsonNumber` | `jsoncodec` / `Decimal` |
| Frozen local shapes, required features, closure and evidence rules | `LmtrSchema`, `LmtrMetadata` | `Shapes`, `metadata` |
| Envelope/CRC/reference identity and checked offset arithmetic | `LmtrFraming`, `RecordRef` | `container.Reader.envelope`, `encode_record` |
| Bounded codecs, Float32/masks, logical hashes and restricted native views | `LmtrCodec`, `LmtrChunk` | `inflate`, `metadata.chunk` |
| Recorder-intake order, stream counters, clock-domain evidence | `JournalState` | `Timeline` |
| Acknowledged aggregate and stream counters | `SummaryAccumulator` | `blank_summary`, `add_chunk` |
| Read-only lazy/full/prefix/salvage access | `LmtrReader` | `Reader` |
| Exclusive owned synthetic-plan writer | `LmtrWriter` | `write_plan`, `prepare_chunk` |
| Structured host diagnostics | `LmtrCommand` | `python -m tools.lmtr_v1` |

The schema and corpus are shared **data**. Parsing, byte integrity, closure, chronology,
codecs, statistics, index traversal and recovery algorithms are separate executable
paths. Neither imports the other's validation functions. Neither computes thermometry;
original primary Float32 bits are read and retained. The synthetic recipe interface
is not a camera acquisition API or part of wire JSON.

## Read scopes and historical indexes

- Normal open verifies HEADER, END/checkpoint and bounded root/navigation; it does
  not certify every historical payload.
- `--entry N` verifies the containing CHUNK's body, decoded groups and logical data.
  `seek FILE TIMELINE_NS [--stream ID]` additionally selects the first recorded
  entry at/after **recorder-intake** time. Segment events participate in their
  explicit stream inventory. Sensor/receipt clocks never replace this timeline.
  A no-match query returns `seek_result=not_found` and null selected fields; it
  does not mark an otherwise valid source corrupt. Candidate visits are capped
  at1,024 and page caches stay bounded. Unknown options still reject.
- `--full` streams every physical record, including legitimate immutable pages no
  longer reachable from the final root, and verifies final root/summary/journal.
  Unreferenced historical pages are legal; invalid committed ones are not hidden
  by a fresh final root. Direct child checks keep Kotlin historical-page validation
  separate from repeated numerical decoding. Python consumes traversal iterators
  rather than retaining duration-proportional CHUNK bodies.
- `--recover --full` performs bounded trusted-tail/predecessor discovery, verifies
  indexed prefix plus suffix, and reports `full_verified_prefix`, unknown tail and
  no invented finalization. Rebuildable navigation damage is `navigation_rebuilt`;
  complete promised source/hash damage still fails. This scope does not claim
  every discarded historical navigation record is intact.
- Explicit `scan FILE` reports only independently verified CHUNK ranges with
  `salvaged_chunks`; it never certifies global sequence/index/completion. Bad
  committed chunks remain failures with `integrity_damaged`. Broken framing stops
  without a guessed next offset. Cancellation is available through both reader
  APIs between records, including `check(..., cancelled=callback)` for full/recovery checks; CLI termination closes descriptors through process/context
  ownership. A large single operation has no certified cancellation latency.
  `verified_prefix_bytes` in scan is a structural traversal boundary, not a claim
  that damaged payload bytes within that interval verified.

All operations open sources read-only. No truncate, append, in-place repair or old
frame substitution exists. An external rebuilt-index cache and arbitrary damaged-
framing range discovery are still unimplemented. Unsupported header/features/codecs
are not bypassed by recovery/salvage.

## Bounded resources and truthful memory evidence

Wire limits apply before body/decompression/shape allocations. Kotlin additionally
charges body, decoded groups, logical slices, stored scratch and estimated metadata
against a default 128 MiB working budget, returning `local_resource_limit`.
Recovery keeps at most 64 fixed envelope references and rereads each suffix body,
instead of retaining up to 64 large bodies. A separate [17-CHUNK suffix stress](linux-recovery-memory-validation.json)
verifies 128 MiB of large source payloads with a 128 MiB capped JVM and about 16.3 MiB
of traced Python allocation; tracing excludes native allocations. This
admission estimate is not a universal JVM RSS/heap guarantee. Page caches are capped
at two per depth; parsed-object overhead is separate from the 8 MiB raw-page bound.

Python bounds record/decoded lengths, navigation cache and reporting/clock state.
Statistics use one row-major accumulator, avoiding image-sized Python float/tuple
lists. Diagnostic bookkeeping caps 65,536 records/logical hashes; current clock-
domain state caps 65,536 Python definitions (Kotlin uses a separate 4,096-definition local budget). These are disclosed tool limits, not file-duration
limits. Host synthetic writers are not production memory-admission controllers.

The on-demand [sparse report](linux-sparse-validation.json) records selected access
and a separate Python full scan beyond 4 GiB. The large file never enters Git. JVM probes precede the large Python full scan:
an initial measurement order returned the parent's pre-exec high-water mark in
Unix child RSS (269,924 KiB), so it was corrected rather than labelled Java heap.
[Hard-limit probes](linux-limit-validation.json) additionally verify all 8 index levels
and explicit candidate/predecessor/suffix/tail refusal. The report distinguishes selected child RSS, cumulative child RSS including
the explicit-refusal probe, Python selected/full RSS and configured heap/budget. A
sampled synthetic run cannot certify every geometry, duration, heap, backend
or real-time workload. Kotlin's default budget correctly refuses selecting the
large 60 MiB chunk while allowing the small indexed target beyond 4 GiB.

## Publication and remaining runtime gates

Writers build exact logical recipe bytes, validate independently provided native
view evidence against acquisition slices, and derive their hashes. They publish new
files exclusively and retain unfinished owned artifacts on failure. Kotlin framing
and Python now use separate body/footer file-sync barriers and independent
readback. An injected Python body-sync failure produces no promised CHUNK/footer
or END. Neither certifies directory persistence, provider close/export durability
or power-loss behavior. Neither implements
monotonic 1-second sealing, 5-second checkpoints, admission/Stop races, async queue
backpressure or provider close/export guarantees. No such success follows from END.

Native Windows protocol/results: [WINDOWS_VALIDATION.md](WINDOWS_VALIDATION.md); the exact native CI gate passed.
ART execution, measured cancellation latency and exhaustive mixed-corruption boundaries,
backend runtime and generic module performance remain unverified or unimplemented
as tracked in [the checklist](IMPLEMENTATION_PLAN.md). R3 #8 stays open; PR #14 is
draft. R4/R5/R6/R7 and application integration are not started.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Current hardening and gate classification

[HARDENING_REVIEW](HARDENING_REVIEW.md) supersedes the earlier undifferentiated
remaining-work list: general damaged-framing salvage/cache are optional; async
producer and backend/export guarantees belong to R4/R6. Required R3 host recovery
primitives are tested, while native Windows now passes its required finite conformance gate.
Cooperative full/recovery checks propagate cancellation without numerical success;
one admitted synchronous operation cannot be interrupted. New reports preserve old
checkpoint evidence and pinned corpus provenance.

The [native Windows receipt](windows-validation-summary.json) establishes71 JVM/
64 Python, independent51-case corpus/33-write interoperability and NTFS >4GiB/full
read integrity on the pinned CI SHA. Earlier unverified-platform lists above are
checkpoint history; ART remains separately unexecuted before production.
