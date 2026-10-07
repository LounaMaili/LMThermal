# R2 feasibility evidence

**R2 INCOMPLETE.** Both live matrices failed the writer-throughput/thermal-preview
gate. Incremental/steady memory remains unproven and real Windows parity is pending.
Passing prototype JUnit methods does not override these failed acceptance gates.

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
- [Same-byte real Windows validation packet and commands](WINDOWS_VALIDATION.md):
  ready to run; not yet executed on Windows.
- [Machine-readable gate summary](reports/aggregate.json) and linked raw reports.
- [Final regression and scope checks](REGRESSION.md).
- [Final test-APK packet proof](reports/android-final-packet.json): all 15 file hashes
  match the published Android/Linux/Windows packet; no replacement of the artifact.
- [Executable tooling and commands](../../tools/recording_r2/README.md).

R1a remains accepted semantic direction. R1b wire freeze and R3 canonical fixtures
have not started. Test APK helpers add no production recording action. Accepted
[LMTX still v1](../LMTX_FORMAT_V1.md), source coordinates, camera controls, session
readiness and thermometry remain unchanged. Desktop is read-only.

The final test-only queue correction now charges metadata and pending gap closures;
its bounds/failed-close regressions pass on the JVM but a new live matrix remains
necessary. No tested configuration is promoted as a practical 25 FPS default.
The exact annex is an evidence-backed proposal, not owner acceptance of R1b.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
