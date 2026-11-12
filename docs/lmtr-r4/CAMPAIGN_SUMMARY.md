# Autonomous R4/R5 software campaign — 2026-11-12

## Outcome and preserved boundaries

Five of five authorized Actions runs, no reruns and no physical device/ADB access.
No merge, issue closure, PR16 edit, production recording activation, wire/corpus,
queue, memory78+32MiB, ACK or END-permission change. Historical worktrees/evidence
remain intact. R4 throughput and safe physical admission remain incomplete.

### A — Instrumentation and limited allocation candidates

[Run43238655791](https://github.com/LounaMaili/LMThermal/actions/runs/43238655791),
source27f0924, same APK bytes, fresh processes, fixed OFF/ON/ON/OFF:

| Arm | STORED saved / prep loss / writer loss | DEFLATE saved / prep loss / writer loss |
|---|---|---|
| OFF1 |81 /4 /115|74 /0 /126|
| ON1 |81 /1 /118|77 /1 /122|
| ON2 |81 /1 /118|76 /0 /124|
| OFF2 |82 /2 /116|77 /0 /123|

Each codec offered200; all971 missing observations across these eight cases are
represented, with629 retained measurements. All files finalized and first_failure=null.
Producer median intervals39.95–40.06ms; p95 lateness14.06–16.06ms. No consistent saved-
count penalty attributable to fine instrumentation appears in these two finite pairs;
there is no statistical equivalence, physical25Hz or zero-overhead claim. A/B/C=B,
not lossless acceptance. Fine metrics are usable; OFF is explicitly null, not zero.

Two sequential changes retain every complete validation:

1. Seven fixed registry regexes compiled once; no file-derived cache keys/shared matchers.
2. Metadata SHA-256 uses existing bounded lowercase nibble encoding instead of per-byte
   Formatter graphs; no cross-validation digest cache or input/output change.

Twenty engine/full-size fixture files per candidate are byte-identical to reference.
447 final JVM test executions (75 contract,67 recorder,21 adapter,50 field,234 core;
shared recorder test sources intentionally execute in several modules),64 independent
Python conformance tests and12 CI-helper tests pass. Both targeted APKs build; lint
has0 errors and4 harness warnings (dependencies, backup metadata, icon).

One fixed host CHUNK/100-complete-validation sample reduced current-thread allocated
bytes397012688→338950216→333331440 (16.0% combined). Wall sums1556820622→1740550461→
1616288059ns: final remains3.8% worse than original in these unpaired host samples.
Do not label this a demonstrated recorder throughput improvement. See individual
[pattern](SCHEMA_PATTERN_CANDIDATE.md)/[metadata](METADATA_DIGEST_CANDIDATE.md) protocols.

### B — Fatal-mode investigation

[Pattern run43242970885](https://github.com/LounaMaili/LMThermal/actions/runs/43242970885):
reference/candidate/candidate/reference retained16/16/16/17 STORED observations.
[Metadata run43245977603](https://github.com/LounaMaili/LMThermal/actions/runs/43245977603):
13/13/11/13. In every arm, the first recorded event is
`WRITER_LOSS_RESERVE / LOSS_JOURNAL_EXHAUSTED`, control1048576 bytes, sticky
unrepresented loss. The terminal reason is the later END-guard refusal. No END was
written, JUnit failed and DEFLATE was not started. All eight complete available
prefixes/oracles pass independent Fedora readback; source files remain unchanged.
Fine reports are honestly limited by interrupted finalization, with0 observer/clock
exceptions. No memory guard stop occurred. Both runs are B, not infrastructure C.

This directly identifies the new failures' reserve boundary. It is the same observed
failure mode as historical39782301329, not retrospective proof of its first cause.
Why throughput/queue age degraded across hosts/process contexts remains unresolved.
A fixed-clock gap-only test passes both codecs without inventing acknowledgments;
it does not cover aged-backlog cascades. The next useful targeted diagnostic measures
pending witness age/CHUNK composition versus reserve occupancy, retaining deadlines and
capacity. No opportunistic retry, hidden loss, increased journal or END bypass.

The first paired analyzer used event SHA as source label even for reference. Original
run43242970885 receipts remain unchanged; actual `source-sha.txt`, expected source,
full tracked hashes and APK receipts identify sources. Run43245977603 separately binds
actual tested checkout and event identity; three offline identity regressions pass.

### C — Memory

[Ownership analysis](CAMPAIGN_MEMORY.md) retains exact logical78MiB calculation:
4+4MiB queues,2×3MiB in-flight estimate,12×4MiB scratch,16×1MiB control/metadata.
The32MiB margin stays mandatory. Temporary JSON/regex/format allocation volume,
retained objects, Java occupied heap, native and RSS/PSS are different domains.
Seven Pattern graphs are retained; fine sample tables512KiB plus objects/reporting
are separate. No physical normal-Compose owner census or worst metadata-object cost
is proven, and none is inferred from the allocation reduction. No physical recording.

### D — Desktop R5

[Draft Desktop PR4](https://github.com/LounaMaili/LMThermal-Desktop/pull/4), source branch
`codex/lmtr-r5-offline-reader`: pinned independent R3 reader, all51 unchanged canonical
files, lazy indexes/events, one <=64MiB decoded-CHUNK cache, explicit full/prefix
verification, stored Float32/mask/native/transport, gaps without fabricated readings,
native point/ROI, palette/range, time navigation and exclusive separate PNG.
One worker owns file/index/decode work with bounded coalescing and no timer starvation.
Existing still/LMTX/legacy recording readers remain unchanged.

Linux207 tests pass. Initial [Windows43240927585](https://github.com/LounaMaili/LMThermal-Desktop/actions/runs/43240927585)
failed one test cleanup: Windows directory deletion preceded model descriptor close.
The targeted fixture ownership correction preserves assertions/application semantics.
[Windows43241547022](https://github.com/LounaMaili/LMThermal-Desktop/actions/runs/43241547022),
tested4c73e9f:200 tests,196 pass,4 Linux-only skips. No failure is discarded.

The R5 adapter independently matches original Float32/native/acquisition oracles and
whole-grid ROI for155 retained synthetic hosted ART observations, preserving245 gaps.
It also opens all four recovered incomplete prefixes from run43242970885 with honest
unknown tail and no invented END. Linux offscreen layout was visually inspected.
Real operator interaction and physical Android LMTR→Linux/Windows interoperability
remain unvalidated; R5 #2 stays open. No thermometry is recomputed.

### E — R6

[R6 integration gates](R6_INTEGRATION_GATES.md) retain already accepted choices,
lifecycle/single-owner policy, storage estimates, pending/committed/loss status,
canonical local access and independently verified export. R6 stays architecture only.

## Evidence and residual limitations

Original ZIPs for all five runs have verified GitHub SHA-256 digests. Run/job/attempt,
source/APK/environment metadata, logs, original synthetic tar, complete/incomplete
LMTR, oracles, first failures, counters and independent assessments are preserved in
an ignored owner-private persistent local destination. Nested manifests and complete
tracked source hashes are checked against pinned Git trees. This local preservation
is not an independent off-filesystem backup; remote artifacts still expire normally.

A historical generator check was inadvertently invoked through unsupported `--help`.
It only regenerated in isolated temporary directories and failed exact byte comparison.
At least the manifest must differ: it embeds Python3.14.8 whereas this host is3.15.2
(zlib runtime matches). Additional regenerated differences were not diagnosed and no
second regeneration was attempted. All51 ORIGINAL file hashes and direct read verdicts
pass; no canonical source was regenerated/replaced/published. Editorial acceptance
checks independently confirm exact frozen §§2–16 and76 shape definitions.

Remaining gates: no demonstrated Full25Hz recorder throughput; historic cause identity
and aged-backlog mechanism remain unproven; no physical safe admission/recording or
real Android LMTR interoperability; R6 backend/durability remains unimplemented. Any
further hosted test needs a new explicit CI budget. A physical session additionally
requires Pixel released from LMarchery and separate owner authorization, legitimate
wireless/USB permissions, non-sensitive scene and battery/thermal/storage/memory checks.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
