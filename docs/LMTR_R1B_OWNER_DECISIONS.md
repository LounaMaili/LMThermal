# R1b explicit owner acceptance — 2026-10-13

The owner explicitly accepts the complete **`r1b-review-2`**, D1–D10 and **approach B**,
as reviewed at `2c18401af7ef45319a7c025f136d94939e5bee03`. The [accepted normative
LMTR v1 contract](LMTR_WIRE_V1.md) is frozen for R3; no end-user release/runtime
conformance or automatic R3 merge is implied. PR #13 integration and #6 closure
are authorized only after final checks and actual main integration.

| Accepted decision | Scope |
|---|---|
| D1 | Identity, versions, envelope and compatibility |
| D2 | 64 immutable-purpose stream slots and segment rules |
| D3 | Exact canonical JSON and semantic context/segment IDs |
| D4 | Recorder-intake chronology separate from original clocks |
| D5 | STORED/DEFLATE baseline; production Zstd deferred |
| D6 | Strict bounded same-observation/chunk native views |
| D7 | Hard file limits distinct from preferences/queues/local admission |
| D8 | Monotonic idle policy, serialized Stop and checkpoint behavior |
| D9 | Bounded index/integrity/read-only recovery |
| D10 | Local authority, verified export, immutability and historical conversion B |

The following complete review register is preserved as **historical rationale**;
its prior pending/DRAFT statements do not override the explicit acceptance above.
R7 remains planned: faithful multi-file dataset provenance and qualified imported
timelines require a separately reviewed extension; base v1 must not invent them.

---

# LMTR R1b — owner decision review

**DRAFT / `r1b-review-2` / 2026-10-12. No R1b owner acceptance is recorded.**

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
| D2 generic schema and multi-stream model | Retain fixed <=64 purpose-stable slots; same-purpose replacement via a new segment. Unexpected visual/second-camera sources need an unused declared suitable slot, otherwise explicit new recording or unrecorded outcome; no silent inclusion | Mutable global declarations or unbounded event chain reduce repetition but undermine lazy seek/recovery; separate files per change simpler but fragment coherent experiments. Fixed slots cannot add an unplanned slot mid-file; start a new recording | **Format**, new P grammar. No generic-camera or concurrent-device implementation promise. Owner acceptance required |
| D3 JSON precision and deterministic metadata | Preserve unknown optional semantic values with exact Decimal precision; canonical UTF-8/key/number spelling for writers; accept bounded equivalent input spellings; content-hash exact context values | Unordered RFC JSON alone is simpler, but metadata/context bytes differ across encoders. RFC8785/binary64 model can lose high-precision values. Exact lexical preservation conflicts with normal semantic editing and is not owner-required. Raw exponent bounds apply even to zero; input and canonical token length must both fit; negative floating zero requires typed bits | **Format**, A precision goal; P canonical spelling/token limits/hashes. Raw-body hashes differ from canonical semantic IDs; Kotlin scalar sorting/BigDecimal and Python Decimal probes do not prove runtime conformance. Adds implementation/CPU cost needing future performance validation. Owner acceptance required |
| D4 timelines and loss | Global entry ID plus recorder-intake monotonic witness for append/playback; original host receipt/sensor times and quality/domains remain independent; gaps preserve exact known ranges or null unknown count | Sensor-time ordering/reorder queues require real clock/sync evidence and add buffering; direct receipt ordering alone fails late multi-stream arrivals. Intake timeline is not the sensor acquisition chronology; analytic users may choose original clocks without assuming registration | **Format**, A truthful clock/gap goals; P timeline representation. This differs explicitly from R2's mapped host-receipt timeline. Owner acceptance required |
| D5 codecs | Mandatory STORED=0 and one bounded RFC1950-wrapped DEFLATE=1; writer level 1 recommendation. Reserve ID2 **unassigned**, defer production Zstd | Define an optional Zstd feature now, or do not reserve a number. R2 parity supports a later extension but not optimized sustained production adoption. Reserving prevents accidental conflicting assignment without adding runtime dependency | Baseline decoders **format**; encoder level **policy**. E codec evidence/P registry choice. Owner acceptance required |
| D6 restricted views and full evidence | Only independently hashed same-observation/same-chunk contiguous native view into materialized acquisition; always verify parent; other modules may materialize distinct evidence | Always duplicate raw/transport is simple but HT Full adds ~19.9 decimal GB/hour uncompressed. General views/strides/transforms add unsafe/untested interpretation. View feature is explicitly required by files using it | **Format**, E scoped Android/Linux/Windows native slice proof; candidate remains unaccepted until owner action |
| D7 limits and admission | Retain 64 MiB decoded+metadata, 65 MiB records, 1 MiB metadata; count prefix's extra 4 bytes explicitly. Geometry <=16384/axis and <=4194304 pixels; singleton permitted within hard/local bounds. Negotiate peak-live/module budget before Start; no implicit queue bypass | Larger universal limits burden all readers; universal 64 MiB heap promise is unsupported. Rejecting all frames >preferred incorrectly equates target with syntax limit. Strict initial 4 MiB queue may refuse a larger valid module unless separately configured and benchmarked | Hard limits **format**; local refusal/admission/queue **policy**. E starting bounds/P generic details and 4-byte refinement. Owner acceptance required |
| D8 chunk and idle policy | HT/DEFLATE preferred 4 MiB/32 entries/1 s; seal from first prepared entry's monotonic intake witness +1 s; checkpoint <=32 commits or +5 s from the first uncovered footer-sync acknowledgement. Single writer owner, coalesced generation-tagged timer flags, latched Stop cutoff and bounded loss/finalization reserves | Arrival-only is R2-tested but can leave final frame pending forever. Last-arrival timer can defer under load; shorter timers add sync/energy overhead. First-entry timer limits assembly exposure, **not** a hard durability latency bound | Preferred sizes/timers **policy**, checkpoint count/topology **format**. E measured target; P real timer and timed checkpoint, requiring future races/power/backend validation. Owner acceptance required |
| D9 index/recovery/status | Bounded pages <=256/depth8/512 KiB, global entry/time bounds with stream filtering; lazy opened/selected integrity distinct from full verification. Fast tail<=65 MiB+72, <=1024 candidates/records and <=64 suffix chunks; explicit slower read-only salvage/scan when needed | Loading every index/plane at open is slow for hours of data; silently broad recovery can hide damage. Unknown tail/END never becomes normal gap or complete file. Integrity and producer close acknowledgement differ | **Format**, E topology/recovery and P generic summaries/time index/error details. No physical power-loss certification. Owner acceptance required |
| D10 canonical storage, export and lineage | Initially app-owned non-cache local authority with tested sync/close/reopen scope; no mandatory copy for read-only access; new-destination independently verified SAF export only. Single-file Parent only for representable conversion/reprocessing/continuation; no source rewrite. Recommend B: defer multi-file historical provenance/imported timelines to an explicitly reviewed future feature, retaining R7 | Direct arbitrary SAF or mandatory huge copy is convenient-looking but unproven/expensive. App-private files can vanish on data-clear/uninstall; URI access is not backup. Multi-file dataset inventories/sample mapping/qualified imported clocks now (A) would add substantial untested grammar; B safely defers them without pretending base v1 converts historical Desktop bundles. Multi-GB/remote provider guarantees need new tests | A immutability/storage goals; **policy plus format provenance/lineage** details P. E small local export only. Owner acceptance required |

Minor grammar/ordering/integrity constants belong to these packages; they are not
ten approvals plus hundreds of independent checkbox decisions. The owner may accept
one identified complete revision with stated changes. Accepted changes must be
incorporated into a new reviewed revision before a stable spec is identified.

## 3. Unresolved ambiguities and proposed resolutions

The draft has concrete rules; the following are **review/validation uncertainties**,
not omitted definitions that an implementer is expected to guess.

| Ambiguity / risk | Proposed resolution already in draft | What remains |
|---|---|---|
| Can JSON numbers retain sufficient precision while metadata has deterministic bytes? | Exact decimal normalization, input/canonical token and coefficient <=1024, raw exponent ±5120 even for zero, nonzero adjusted exponent ±4096; semantic not lexical preservation; exact source type/bits when needed | Owner accepts the custom canonicalizer cost/limits; Kotlin/Python vectors after approval |
| What does recording time mean for late streams? | Monotone recorder-intake journal time, separate source times/domain quality and explicit UTC anchors; never inferred alignment | Owner agrees on playback chronology; historical estimated/imported clocks need a future required feature, not a fabricated live witness |
| Does a same stream ID imply unchanged dimensions/module? | SegmentStream binds native geometry/source/session; new segment on replacement; chunk repeats complete definitions | Owner accepts explicit new-recording/unrecorded outcomes without suitable fixed slots; real module negotiation remains unclaimed |
| Can idle timers promise bounded data loss in seconds? | They request sealing of actual complete pending entries; codec/sync/queue/scheduler delay stays visible, no hard disk deadline | Single owner/control generations/Stop cutoff and observable outcomes are specified; races, energy/sync frequency and backend stalls still need implementation tests |
| Is 64 MiB a global memory guarantee? | It is a file decoded bound/HT engineering target distinction; explicit local peak-live admission and stricter honest refusal | No blanket supported geometry. Real larger modules need budgets and measurements |
| Does final END prove successful provider close? | No: structural END versus external live close/reopen acknowledgement; failed live Stop remains failure | Catalogue/UI needs explicit producer outcome; no retroactive success inferred from forensic bytes |
| Can index damage be treated like payload damage? | Rebuildable index navigation versus committed_payload_integrity; fast limits/salvage explicit, source immutable | Future corpus must exercise false candidates, partial/damaged checkpoints and full-scan cancellation |
| How should unknown binary extensions be retained? | Entry module_extension payload with namespace/encoding, exact hash/bytes; optional JSON precise; required interpretation negotiated | Comprehensive extension vectors after approval; no remote code resolution |
| Are new record types safely optional in v1? | No; only known types 1–5/flags zero. Optional evolution uses JSON/extensions; future framing changes need explicit version strategy | Owner may prefer a skip flag, but it needs separate recovery/compatibility design before acceptance |
| Are stored presentation/analysis states a global project timeline? | Optional per-observation snapshots in v1; future tracks/projects in namespaced extension/independent project, source immutable | Avoid expanding this release into an annotation/sidecar standard; owner accepts this scope |

### Targeted review findings and unresolved choices

**Changes from review-1 are [P] corrections, not owner decisions.** D1, D5, D6,
D7 and D9 remain unchanged recommendations. D2 makes fixed-slot consequences
explicit; D3 corrects canonical hash/precision/negative-zero boundaries; D4/D10
make historical conversion honestly unsupported by base fields; D8 specifies
serialization and observability without claiming an implemented timer.

#### Historical conversion: A versus B (recommend B)

Read-only inspection of Desktop main `b0afdf3b42b6f55c2ae3d926f0d8c9df09255746`
confirms the [directory/NPZ contract](https://github.com/LounaMaili/LMThermal-Desktop/blob/b0afdf3b42b6f55c2ae3d926f0d8c9df09255746/docs/RADIOMETRIC_RECORDING_FORMAT.md),
[manifest/sequence mapping](https://github.com/LounaMaili/LMThermal-Desktop/blob/b0afdf3b42b6f55c2ae3d926f0d8c9df09255746/radiometric_recording.py#L247)
and [sample/estimated timing](https://github.com/LounaMaili/LMThermal-Desktop/blob/b0afdf3b42b6f55c2ae3d926f0d8c9df09255746/radiometric_recorder.py#L106).
One recording can contain arbitrarily more than 16 chunk files plus its manifest
and CSV. Per-measurement identity is source sequence/chunk/frame index. Only
committed chunks expose matrices; missing committed chunks remain integrity errors.
Float sampling-relative elapsed values and estimated missed-slot UTC are not the
proposed integer intake journal, and converter wall time cannot replace them.

| Approach | Required design / tradeoff | Recommendation |
|---|---|---|
| A now | Bounded paged dataset inventory with deterministic safe relative names, membership/completion policy and exact member hashes/sizes; per-sample CSV/NPZ identity; imported timeline mode with observed/estimated/unknown precision and reader/index negotiation | Technically feasible, but a new architecture needing new grammar/vectors and review. An arbitrary zip/manifest-only hash or 16 unrelated parents is insufficient |
| B scoped base v1 | Each Parent binds one real file; reject unsupported dataset conversions and preserve independent legacy reading. R7 later reviews dataset/imported-clock semantics as a required feature; compatible minor only if additive, otherwise major | **Smallest safe recommendation.** Does not weaken timing/immutability or cancel R7; it makes R7's extra contract prerequisite explicit |

The owner must accept this scope deferral explicitly. If actual historical conversion
in the first base release is required, choose A and reopen the relevant contract
work **before** acceptance; the present draft does not pretend A is defined.

#### JSON and timer review boundary

The wire draft's §3.3 lists the complete R3 cross-language vector families; §14
specifies timer/admission/Stop/sync/refusal races and observable counters/deadlines.
Host Decimal/BigDecimal probes cover selected coefficient, exponent, scalar-order
and native-float operations only. They do not test Kotlin/ART JSON adapters,
full metadata hashing pipelines, actual timers, backend stalls or memory peaks.
Source Float32/Float64 negative zero/nonfinite payloads require typed exact bits;
finite decimal evidence requires its known source type. Context/segment IDs include
unknown semantic fields, while body hashes bind original bytes.

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

1. Review D1–D10, the ambiguities above and the complete `r1b-review-2` specification.
2. Explicitly accept the identified revision or request concrete changes. Neither
   a document/PR merge nor no response is owner acceptance.
3. Resolve any companion/prose contradiction and record acceptance in #6; identify
   the accepted Git-tracked spec, schema/version/feature registry and supported scope.
4. Only then authorize R3's shared canonical bytes/primitives and subsequent gated
   Android/Windows/Linux work. #6 stays open now; this PR remains draft/unmerged.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
