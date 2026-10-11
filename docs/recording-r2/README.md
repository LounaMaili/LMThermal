# R2 feasibility evidence

**R2 READY FOR COMPLETION REVIEW.** The original live matrices failed
throughput/preview gates and remain preserved. The measured continuation now retains every delivered valid
STORED measurement with zero preparation/writer drops and fresh presentation.
The resumed clean Full-STORED and explicit 4 MiB Analysis/Native/Full DEFLATE runs
also pass throughput/freshness, with measured clean-process memory differences
below the 64 MiB target. Native Windows now passes all 10 STORED/DEFLATE baseline
outcomes and the separate optional 15-case Zstd reference run with exact Linux
parity and immutable sources. R2 #7 stays open for completion review; R1b owner
approval remains separate. Memory sampling and backend assurance limits remain
explicit; historical failures are retained.

- [Measured continuation and bounded candidate](CONTINUATION_RESULTS.md): original ablations, cost/copy model and staged rerun status.
- [Continuation diagnosis plan](CONTINUATION_PLAN.md): ownership audit and staged ablations before optimization.
- [Predeclared plan](TEST_PLAN.md), committed before measurements (`9269417`).
- [Exact proposed framing annex](FRAMING_PROPOSAL.md), for R1b owner review only.
- [Pixel 8 benchmark](ANDROID_BENCHMARK.md): both matrices and the separately
  predeclared post-matrix baseline; explicit drops, severe image latency and limits.
- [Codec evidence/decision](CODECS.md): same-byte Android comparisons, exact
  roundtrips, STORED/DEFLATE candidates and deferred product Zstd adoption.
- [Framing/recovery](FRAMING_RECOVERY.md): process kills, torn writes, corruption,
  lazy seeking and sparse offsets beyond 4 GiB.
- [Hostile inputs and restricted views](HOSTILE_VIEWS.md).
- [Storage/backend and SAF matrix](BACKENDS.md).
- [Completed native Windows validation and commands](WINDOWS_VALIDATION.md), with
  [sanitized machine-readable evidence](reports/windows-packet.json): baseline and
  separate optional codec parity passed against the unchanged packet.
- [Historical machine-readable gate summary](reports/aggregate.json), preserved
  unchanged, and [resumed candidate metrics](reports/continuation-resume-summary.json).
- [Final regression and scope checks](REGRESSION.md).
- [Final test-APK packet proof](reports/android-final-packet.json): all 15 file hashes
  match the published Android/Linux/Windows packet; no replacement of the artifact.
- [Executable tooling and commands](../../tools/recording_r2/README.md).

R1a remains accepted semantic direction. R1b wire freeze and R3 canonical fixtures
have not started. Test APK helpers add no production recording action. Accepted
[LMTX still v1](../LMTX_FORMAT_V1.md), source coordinates, camera controls, session
readiness and thermometry remain unchanged. Desktop is read-only.

The candidate keeps the 4 MiB writer bound, adds a dedicated byte-bounded preparation
thread and records source gaps independently of writer loss. Initial Analysis/Full
STORED retained the actual approximately 25 FPS stream. The continuation also fixes
a demonstrated production display-publication starvation bug with regression
coverage; camera/session/thermometry are unchanged. No product recorder or final
codec/default decision follows from these prototype measurements.
The exact annex is an evidence-backed proposal, not owner acceptance of R1b.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
