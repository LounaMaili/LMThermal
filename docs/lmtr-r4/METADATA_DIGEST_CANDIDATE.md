# Metadata SHA-256 hexadecimal encoding candidate

Reference:a78fa70e300713b337c33eba2629fd4b217ea6c1. This is the second limited candidate,
following the separately measured seven-pattern cache. No validation is removed.

`LmtrMetadata.hash` now reuses `LmtrChunk.sha`, the already tested direct lowercase
nibble encoder, instead of allocating a Formatter graph for each of32 digest bytes.
Canonical JSON and SHA-256 input/output bytes are unchanged, including leading zeros,
Unicode scalars and arbitrary-precision optional values. Four independent pinned hashes
protect those boundaries. Each call still encodes and hashes independently; neither
validation can reuse another validation's digest. There is no recording-sized cache,
new payload owner, queue/threshold/clock change or relaxed integrity assertion.

## Host result before ART

Same2.686996MB synthetic CHUNK,20 warmups and100 complete validations, fresh HotSpot17
process with256MiB heap, no forced GC. Against the preceding pattern-cache candidate:

| Metric | Pattern-cache reference | Metadata encoder candidate |
|---|---:|---:|
| Sum ns |1740550461|1616288059|
| p50 ns |15923394|14481212|
| p95 ns |22414919|20638900|
| Current-thread allocated bytes |338950216|333331440|

This one unpaired host sample reduces measured elapsed time7.1% and thread allocation
volume1.7% relative to the preceding candidate. Against the ORIGINAL host baseline,
combined allocation volume falls16.0%, while elapsed time is still3.8% higher. These
are qualified host observations, not an ART/live-recorder throughput guarantee or a
favorable replacement of the earlier negative sample.

## Final predefined hosted comparison

The fifth and last run authorized by this mandate uses reference/candidate/candidate/
reference, all fine metrics ON, one hosted API35 x86_64 google_apis emulator, disabled
cameras, exact existing50-baseline/200-per-codec method, same queues/budgets/assertions.
Fresh target processes and private evidence per arm; no retry or substitute. The
explicit `digest-run-request.json` change is the sole push activation path. No further
hosted experiment follows once this budget is consumed.

A small evidence-only correction distinguishes verified tested checkout SHA from the
workflow event SHA. The previous paired run's reference rows inherited a misleading
`assessment.identity.source_sha` label from the event, while `source-sha.txt`, expected
SHA, complete source/APK receipts remain correct. Original receipts stay unchanged;
report this qualification instead of rewriting history. A shared collector-side analyzer
now binds expected source SHA to actual checkout receipt for BOTH arms and records the
event separately. Three offline regressions reject missing/conflicting identities.
This does not change recorder behavior or historical A/B/C integrity acceptance rules.

The prior run43242970885 reproduced writer control-reserve exhaustion in both reference
and candidate: every STORED run retained an incomplete prefix and correctly refused END;
DEFLATE did not run. It is evidence of the same failure MODE as the old full-suite fatal,
not proof that its original cause was identical. Pending late-gap/deadline batching and
process/host pressure remain hypotheses until directly observed. No control budget or
one-second deadline is altered to avoid that outcome.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

## Final ART result: B, not throughput acceptance

Run[43245977603](https://github.com/LounaMaili/LMThermal/actions/runs/43245977603)
tested dd05976 against a78fa70 in the fixed four arms. STORED retained13/13/11/13
measurements; every arm captured `writer_loss_reserve / loss_journal_exhausted` with
control1048576 bytes, sticky unrepresented loss, incomplete prefix and absent END.
JUnit failed and DEFLATE did not start. Fine diagnostics are limited by interrupted
finalization; observer/clock exception counters remain zero. The corrected tested-source
identity matches each checkout receipt separately from the event SHA.

No ART throughput improvement is demonstrated. Keep the narrow allocation changes as
reviewed draft candidates only; do not use the host result to certify capacity, runtime
performance, physical safety or production recording. All five hosted runs are consumed.
