# LMTR R1b — owner decision review

**DRAFT / `r1b-review-1` / 2026-10-12. No R1b owner acceptance is recorded.**

The [complete proposed wire specification](LMTR_WIRE_V1_DRAFT.md) is ready for review,
not release. [#6](https://github.com/LounaMaili/LMThermal/issues/6) remains open;
[R2 #7](https://github.com/LounaMaili/LMThermal/issues/7) is completed only for the
reviewed HT-301/Pixel 8 baseline. R3/production remain blocked. This unattended task
makes concrete recommendations instead of requiring an operator or device.

## 1. What is already accepted, and what is not

Accepted **R1a**: full native measurement grid, exact source Float32 Celsius and
validity, requested lossless evidence, available interpretation context, truthful
clocks/gaps, immutable sources/derived lineage and independent legacy/still support.
Product choices remain Analysis default, visible first-use choice and remembered
explicit per-module choice, Full / Research only for meaningful extra evidence,
no initial expert checkboxes, unknown-capacity warning with allowed Start and
known-insufficient reserve blocking. No repeat acceptance of those goals is sought.

R2 evidence: finite ~25 FPS Pixel/HT passing STORED/4 MiB DEFLATE runs with zero
preparation/writer drops, sampled memory under the engineering target, exact
Android/Linux/native-Windows prototype parity and bounded software recovery/local
storage proofs. It does not approve new release magic/grammar, all cameras, idle
timers, indefinite operation, physical power loss or arbitrary-provider durability.

Everything below is a proposed implementation of those goals requiring **explicit
owner acceptance of the complete contract**. The normative-language draft is not
an acceptance record. Minor linked details are grouped into coherent packages.

## 2. Major decisions — recommended complete package

| ID / decision | Concrete recommendation | Alternatives and tradeoffs | Obligation / evidence / acceptance |
|---|---|---|---|
| D1 identity and envelope | `.lmtr`, `lmthermal-recording`, kind recording; container/schema 1.0; envelope 1; `LMTRREC1` / `LMTRCMT1`; tested 40-byte header and 72-byte footer, types 1–5, flags/reserved zero | Different release spellings or optional-record flag now. Broad skip semantics add untested recovery/interpretation paths; keeping flags zero is simpler. Version/required-feature rules permit safe optional JSON evolution | **Format**. Layout/integrity E; identifiers/evolution P. Owner acceptance required; no R2 bytes relabelled |
| D2 generic schema and multi-stream model | Fixed <=64 logical slots in HEADER; complete segment/stream/source/clock/policy and content-hashed segment/context closure in each CHUNK; replace session/module/geometry via new segment | Mutable global declarations or unbounded event chain reduce repetition but undermine lazy seek/recovery; separate files per change simpler but fragment coherent experiments. Fixed slots cannot add an unplanned slot mid-file; start a new recording | **Format**, new P grammar. No generic-camera or concurrent-device implementation promise. Owner acceptance required |
| D3 JSON precision and deterministic metadata | Preserve unknown optional semantic values with exact Decimal precision; canonical UTF-8/key/number spelling for writers; accept bounded equivalent input spellings; content-hash exact context values | Unordered RFC JSON alone is simpler, but metadata/context bytes differ across encoders. RFC8785/binary64 model can lose high-precision values. Exact lexical preservation conflicts with normal semantic editing and is not owner-required | **Format**, A precision goal; P canonical spelling/token limits/hashes. Adds implementation/CPU cost needing future performance validation. Owner acceptance required |
| D4 timelines and loss | Global entry ID plus recorder-intake monotonic witness for append/playback; original host receipt/sensor times and quality/domains remain independent; gaps preserve exact known ranges or null unknown count | Sensor-time ordering/reorder queues require real clock/sync evidence and add buffering; direct receipt ordering alone fails late multi-stream arrivals. Intake timeline is not the sensor acquisition chronology; analytic users may choose original clocks without assuming registration | **Format**, A truthful clock/gap goals; P timeline representation. This differs explicitly from R2's mapped host-receipt timeline. Owner acceptance required |
| D5 codecs | Mandatory STORED=0 and one bounded RFC1950-wrapped DEFLATE=1; writer level 1 recommendation. Reserve ID2 **unassigned**, defer production Zstd | Define an optional Zstd feature now, or do not reserve a number. R2 parity supports a later extension but not optimized sustained production adoption. Reserving prevents accidental conflicting assignment without adding runtime dependency | Baseline decoders **format**; encoder level **policy**. E codec evidence/P registry choice. Owner acceptance required |
| D6 restricted views and full evidence | Only independently hashed same-observation/same-chunk contiguous native view into materialized acquisition; always verify parent; other modules may materialize distinct evidence | Always duplicate raw/transport is simple but HT Full adds ~19.9 decimal GB/hour uncompressed. General views/strides/transforms add unsafe/untested interpretation. View feature is explicitly required by files using it | **Format**, E scoped Android/Linux/Windows native slice proof; candidate remains unaccepted until owner action |
| D7 limits and admission | Retain 64 MiB decoded+metadata, 65 MiB records, 1 MiB metadata; count prefix's extra 4 bytes explicitly. Geometry <=16384/axis and <=4194304 pixels; singleton permitted within hard/local bounds. Negotiate peak-live/module budget before Start; no implicit queue bypass | Larger universal limits burden all readers; universal 64 MiB heap promise is unsupported. Rejecting all frames >preferred incorrectly equates target with syntax limit. Strict initial 4 MiB queue may refuse a larger valid module unless separately configured and benchmarked | Hard limits **format**; local refusal/admission/queue **policy**. E starting bounds/P generic details and 4-byte refinement. Owner acceptance required |
| D8 chunk and idle policy | HT/DEFLATE preferred 4 MiB/32 entries/1 s; seal from first prepared entry's monotonic intake witness +1 s independently of new arrivals; checkpoint <=32 chunks or +5 s from first uncovered commit | Arrival-only is R2-tested but can leave final frame pending forever. Last-arrival timer can defer under load; shorter timers add sync/energy overhead. First-entry timer limits assembly exposure, **not** a hard durability latency bound | Preferred sizes/timers **policy**, checkpoint count/topology **format**. E measured target; P real timer and timed checkpoint, requiring future races/power/backend validation. Owner acceptance required |
| D9 index/recovery/status | Bounded pages <=256/depth8/512 KiB, global entry/time bounds with stream filtering; lazy opened/selected integrity distinct from full verification. Fast tail<=65 MiB+72, <=1024 candidates/records and <=64 suffix chunks; explicit slower read-only salvage/scan when needed | Loading every index/plane at open is slow for hours of data; silently broad recovery can hide damage. Unknown tail/END never becomes normal gap or complete file. Integrity and producer close acknowledgement differ | **Format**, E topology/recovery and P generic summaries/time index/error details. No physical power-loss certification. Owner acceptance required |
| D10 canonical storage, export and lineage | Initially app-owned non-cache local authority with tested sync/close/reopen scope; no mandatory copy for read-only access; new-destination independently verified SAF export only. New identity for conversion/reprocessing/continuation; no source rewrite | Direct arbitrary SAF or mandatory huge copy is convenient-looking but unproven/expensive. App-private files can vanish on data-clear/uninstall; URI access is not backup. Multi-GB/remote provider guarantees need new tests | A immutability/storage goals; **policy plus format provenance/lineage** details P. E small local export only. Owner acceptance required |

Minor grammar/ordering/integrity constants belong to these packages; they are not
ten approvals plus hundreds of independent checkbox decisions. The owner may accept
one identified complete revision with stated changes. Accepted changes must be
incorporated into a new reviewed revision before a stable spec is identified.

## 3. Unresolved ambiguities and proposed resolutions

The draft has concrete rules; the following are **review/validation uncertainties**,
not omitted definitions that an implementer is expected to guess.

| Ambiguity / risk | Proposed resolution already in draft | What remains |
|---|---|---|
| Can JSON numbers retain sufficient precision while metadata has deterministic bytes? | Exact decimal normalization with <=1024 digits/token and adjusted exponent ±4096, not binary64; semantic preservation, no lexical requirement | Owner accepts the custom canonicalizer cost/limits; Kotlin/Python vectors after approval |
| What does recording time mean for late streams? | Monotone recorder-intake journal time, separate source times/domain quality and explicit UTC anchors; never inferred alignment | Owner agrees on playback chronology; future synchronized analytic view is separate |
| Does a same stream ID imply unchanged dimensions/module? | SegmentStream binds native geometry/source/session; new segment on replacement; chunk repeats complete definitions | Future multi-stream/replacement vectors; real module negotiation not claimed |
| Can idle timers promise bounded data loss in seconds? | They request sealing of actual complete pending entries; codec/sync/queue/scheduler delay stays visible, no hard disk deadline | Timer/Stop races, energy/sync frequency and backend stalls need implementation tests |
| Is 64 MiB a global memory guarantee? | It is a file decoded bound/HT engineering target distinction; explicit local peak-live admission and stricter honest refusal | No blanket supported geometry. Real larger modules need budgets and measurements |
| Does final END prove successful provider close? | No: structural END versus external live close/reopen acknowledgement; failed live Stop remains failure | Catalogue/UI needs explicit producer outcome; no retroactive success inferred from forensic bytes |
| Can index damage be treated like payload damage? | Rebuildable index navigation versus committed_payload_integrity; fast limits/salvage explicit, source immutable | Future corpus must exercise false candidates, partial/damaged checkpoints and full-scan cancellation |
| How should unknown binary extensions be retained? | Entry module_extension payload with namespace/encoding, exact hash/bytes; optional JSON precise; required interpretation negotiated | Comprehensive extension vectors after approval; no remote code resolution |
| Are new record types safely optional in v1? | No; only known types 1–5/flags zero. Optional evolution uses JSON/extensions; future framing changes need explicit version strategy | Owner may prefer a skip flag, but it needs separate recovery/compatibility design before acceptance |
| Are stored presentation/analysis states a global project timeline? | Optional per-observation snapshots in v1; future tracks/projects in namespaced extension/independent project, source immutable | Avoid expanding this release into an annotation/sidecar standard; owner accepts this scope |

## 4. Validation and evidence boundary

The companion schema and fourteen noncanonical scenarios are documentation checks,
not a production parser, serializer, recording app or R3 binary corpus. The host
checker validates its supported schema subset, local examples, references, precise
JSON/Float32 arithmetic, frame/view lengths and integrity recipes. Separate
documentation checks validate Markdown links/anchors and historical evidence targets.
It does **not** certify Kotlin/Python implementations, actual timer liveness,
platform codecs/providers, multi-camera hardware or physical calibration.

[Draft validation report](lmtr-r1b/validation-report.json) records the executed
Fedora checks and explicitly unexecuted platform/runtime gates. Existing R2 packet,
reports and failed experiments remain untouched at pinned commits; no Pixel/HT
interaction or production-code changes are useful for this specification task.

## 5. Next owner steps

1. Review D1–D10, the ambiguities above and the complete `r1b-review-1` specification.
2. Explicitly accept the identified revision or request concrete changes. Neither
   a document/PR merge nor no response is owner acceptance.
3. Resolve any companion/prose contradiction and record acceptance in #6; identify
   the accepted Git-tracked spec, schema/version/feature registry and supported scope.
4. Only then authorize R3's shared canonical bytes/primitives and subsequent gated
   Android/Windows/Linux work. #6 stays open now; this PR remains draft/unmerged.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
