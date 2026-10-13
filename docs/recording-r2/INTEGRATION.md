# Reviewed R2 evidence integration

The owner accepts the completion review as sufficient evidence to prepare closure
for the **tested HT-301/Pixel 8 baseline**, with all documented limits preserved.
Focused documentation PR #11 and presentation PR #12 were owner-authorized and
merged on 2026-10-12, main `ed0e3338a72bba76a267e4701125f6f89b3f93e3`.
#7 is closed as scoped-complete; #6 remains open for the
[complete R1b DRAFT](../LMTR_WIRE_V1_DRAFT.md) and
[owner decision review](../LMTR_R1B_OWNER_DECISIONS.md). No wire acceptance is inferred.
The selection and historical review below remain preserved.

## Provenance and file selection

Source: [reviewed R2 tip](https://github.com/LounaMaili/LMThermal/tree/747b89c8a0072603a73deb81db5cdbef0ed900a1).
The original `test/recording-r2-feasibility` history is preserved. Durable Markdown
was copied with link/navigation corrections; the curated numeric reports below
are byte-identical copies. [Hash/reference inventory](reports/integration-provenance.json)
records each report hash and all excluded relative-link targets at that fixed commit.
Report-internal historical filenames resolve in that pinned tree; they are not
claims that executable tools or raw reports were copied into main.

Integrated files:

- `docs/recording-r2/README.md`
- `docs/recording-r2/COMPLETION_REVIEW.md`
- `docs/recording-r2/FRAMING_PROPOSAL.md`
- `docs/recording-r2/CONTINUATION_RESULTS.md`
- `docs/recording-r2/CODECS.md`
- `docs/recording-r2/FRAMING_RECOVERY.md`
- `docs/recording-r2/BACKENDS.md`
- `docs/recording-r2/HOSTILE_VIEWS.md`
- `docs/recording-r2/WINDOWS_VALIDATION.md`
- `docs/recording-r2/REGRESSION.md`
- `docs/recording-r2/reports/completion-review.json`
- `docs/recording-r2/reports/continuation-resume-summary.json`
- `docs/recording-r2/reports/windows-packet.json`
- `docs/recording-r2/reports/continuation-final-packet.json`
- `docs/recording-r2/reports/continuation-resume-readback.json`
- `docs/recording-r2/reports/continuation-resume-recovery.json`
- `docs/recording-r2/reports/continuation-short-chunk-process-kill.json`
- `docs/recording-r2/reports/continuation-regression.json`
- `docs/COMMON_RECORDING_DESIGN.md`
- `docs/LMTR_FORMAT_CANDIDATE.md`
- `docs/RECORDING_IMPLEMENTATION_PLAN.md`
- `docs/RECORDING_R2_VALIDATION.md`

Navigation/status updates also affect repository `README.md` and `CHANGELOG.md`.
This document and `reports/integration-provenance.json` record the selection.
The original plans, failed numerical matrices, large per-sample reports, prototype
packet/provenance, `recording-r2/`, `tools/recording_r2/`, Android R2 test harness,
SAF Activity, JNI/Zstd libraries and Gradle wiring remain only in pinned R2 history.
No private scene recording or new canonical fixture is imported.

## Three distinct boundaries

1. Tested `.r2proto` bytes are **noncanonical R2** artifacts. Android regenerated
   the unchanged 15-member packet; Linux 15/15 and native Windows baseline 10/10
   (five explicit Zstd skips), plus optional Windows 15/15 passed. The packet is
   678802 bytes, SHA-256
   `49cc244c60ca51b71c4d1ee38b4360e7e4bc8f490216ad4887f0fd1b770117cc`.
2. Proposed `.lmtr`/`lmthermal-recording` wire grammar remains **unaccepted**.
   [R1b checklist](COMPLETION_REVIEW.md#r1b-owner-handoff--ready-to-prepare-the-complete-contract)
   must settle identity/version/magic, complete generic JSON, clocks/gaps/features,
   codecs and deferred Zstd, restricted views, resource/memory/idle-flush policy,
   index/checkpoint/recovery and canonical storage/export assurance.
3. Future canonical **R3** conformance fixtures have not started. Accepted R1a
   full-grid/evidence/clock/gap/immutability direction and still LMTX v1 are unchanged.

Recommend the measured **4 MiB preferred chunk target only for HT-301/DEFLATE**,
subject to R1b. This is distinct from the two unchanged 4 MiB queue limits and
provisional 64 MiB decoded+metadata/65 MiB record ceilings. The executable default
remains 16 MiB. Sampled incremental process memory below 64 MiB is an engineering
result, not a universal allocation ceiling. Finite Pixel runs do not prove indefinite
operation or another module/ABI. Preserve the failed 16 MiB Native-DEFLATE and
original severe-latency runs. Software recovery is bounded, not physical power-loss
certification; small local SAF export is not multi-GB/remote/direct-provider assurance.
There is no idle wall-clock flush timer in the prototype.

## Independent presentation integration

`fix/celsius-presentation-starvation` starts independently from the same main base.
Its render scheduling/lifecycle regression work is excluded here. During focused
integration, deterministic conflated Close/reopen coverage exposed an earlier-session
publication leak on the R2 scheduling candidate. The separate fix adds a read-only
bridge to the owner's existing invalidation generation; no acquisition, protocol or
thermometry behavior changes. This new integration finding does not rewrite the
historical review or measured R2 reports. The historical
presentation audit describes R2 source; it does not claim the docs branch changes
application behavior. Review each focused PR independently; do not merge R2 wholesale.
The owner authorized integration and scoped #7 closure; both are verified. Only
CHANGELOG conflicted during the #12 update, and both entries were retained verbatim.
#6 remains open until the complete R1b contract is explicitly accepted.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Documentation validation (2026-10-12)

- All eight copied JSON reports parse and match their exact bytes/hashes at the
  reviewed R2 tip; the separate provenance inventory parses as well.
- Recalculated the unchanged archive, manifest and every one of its 15 member
  hashes; baseline 10 and optional 15 executed Windows rows exactly match both
  stored Linux reports. This is report consistency checking, not platform reruns.
- Reviewed Markdown navigation/fences and resolved local targets and pinned Git
  objects, including heading anchors. No missing prototype relative link remains.
- Compared all MUST/MUST NOT lines in the four updated planning documents against
  main; accepted mandatory direction is unchanged. `git diff --check` passes.

No completed physical Android/Linux/Windows R2 experiment was repeated here.
