# R3 implementation checklist and acceptance coverage

Base main `fe029363062cdf54d6cefa9d4f24265e80188091` contains accepted R1b.
Normative authority: [LMTR v1 at acceptance commit](https://github.com/LounaMaili/LMThermal/blob/8c343cd6cf1ca11eff4f3bbaa5c2b10dfbbf645f/docs/LMTR_WIRE_V1.md).
R3 branch `feat/lmtr-r3-contract-conformance`; #8 stays open, no R3 merge or R4/R5.

## Dependency-ordered checklist

- [x] 1. Checked binary envelope/CRC/body/reference integrity and read-only I/O.
- [x] 2. Independent strict exact JSON and canonical spelling/hash boundaries.
- [x] 3. Header/version/features/codecs/immutable stream inventory.
- [x] 4. Complete segment/context closure and semantic IDs.
- [x] 5. Full-grid Float32/masks/native/acquisition/visual roles and logical hashes.
- [x] 6. Bounded STORED and single RFC1950 DEFLATE streams.
- [x] 7. Entry IDs/intake clocks/source times/sequences/gaps/retention.
- [x] 8. Same-observation/chunk materialized-parent native view integrity.
- [ ] 9. Paged index/checkpoint primitives tested; time/stream seek and exhaustive depth limits pending.
- [ ] 10. END/finalization and truthful read-only recovery/errors.
- [x] 11. Small deterministic synthetic canonical fixture inventory/provenance.
- [x] 12. Kotlin-write/Python-read and Python-write/Kotlin-read exact logical parity.
- [x] 13. Negative/hostile lengths/metadata/features/codecs/views/recovery cases.
- [x] 14. On-demand Fedora sparse >4 GiB, lazy access and explicit local-budget refusal demonstrated (sampled scope).
- [x] 15. Clean JVM/Python/applicable core regression and deterministic regeneration.
- [ ] 16. Real native Windows canonical LMTR validation (unavailable here; remains gate).

## Isolation and evidence

Pure standalone `lmtr-contract` JVM module, no Android or application dependency;
independent standard-library Python under `tools/lmtr_v1`. Existing production
camera/session/thermometry/UI/LMTX and Desktop implementations stay unchanged.
R2 is reference evidence, not reusable canonical schema/identifiers.

Implementation milestones are committed/pushed only with factual checks. Missing
features must return explicit unsupported/incomplete scope instead of advertising
full conformance. Source files remain immutable. No physical calibration or power-loss
certification follows from synthetic conformance data or software fault tests.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Recovered checkpoint — 2026-10-13

The existing R3 checkout was preserved at base `fe029363`, with untracked
`lmtr-contract/`, Python reference, generator/corpus and stress tooling. No R3
commit existed before this checkpoint; original R2 remains `747b89c`. No stale
LMTR test process was found on recovery. No existing file was reset or discarded.

Verified checkpoint results: 56 JVM R3 tests, 48 independent Python tests,
51/51 Python corpus files read/classified by Kotlin and Python, 33/33 Kotlin
written plans independently read by Python, 33 canonical plan-byte comparisons,
39 cross-runtime numerical/Unicode/resource vectors. The durable
[interop report](linux-interop-validation.json) records actual counts/hashes.
Core regression: 232 tests, `:app:assembleDebug` and `:app:lintDebug` passed offline.

Actual repairs include paired-null empty refs, exact synthetic numeric recipes,
owned payload/view hash construction, snapshot overflow/early admission, immutable
historical page checks and streaming Python snapshot traversal (the former list
retained duration-proportional chunk bodies). Tests distinguish legitimate
unreferenced snapshots from corrupt ones; lazy scope does not certify historical
payloads. Actual 260-CHUNK promotion and selected one-CHUNK access pass.

Items 9 and 13 mark tested bounded baseline primitives, not every stress case.
Item 10 remains partial: terminal and missing/torn END prefix recovery exist;
navigation-damage recovery/rebuilt-index/full salvage coverage is still being
completed. Item 14's on-demand sparse tool exists; its durable rerun/report and
resource measurements remain pending at this checkpoint. No asynchronous recorder
timer/admission/storage backend, Windows or ART claim follows.

## Continuation after the pushed recovery checkpoint

Checkpoint `0554c49cf538eb615e5878f79df430a0cba09a3c` is preserved on the same
branch; [draft PR #14](https://github.com/LounaMaili/LMThermal/pull/14) is open.
No implementation was restarted and no R1b workflow was repeated.

Final host coverage: **60 R3 JVM tests**, **54 Python tests**, all with zero failures;
**51 corpus cases per reader**, **33 Kotlin-write/Python-full-read**, **33 canonical
plan byte comparisons**, **43 JSON runtime vectors**, **5 version cases**,
**3 DEFLATE adversarial cases**, **2 explicit navigation recovery cases** and
**4 explicit read-only scan cases**. Final rebuild used `:lmtr-contract:clean`;
core/build/lint gates remain green (232 core tests). Unsupported CLI options reject.

[Interop evidence](linux-interop-validation.json) and [sparse evidence](linux-sparse-validation.json)
retain measured results. Sparse source size is 4,404,323,163 bytes with the selected
CHUNK at 4,404,298,782; only 593,920 physical bytes are allocated. Both readers
select one CHUNK; Kotlin visits five records and Python six. Python additionally
fully validates the 71-CHUNK sparse source. Source size/mtime/allocated blocks do
not change. Exact RSS figures are sampled observations, not universal ceilings.

Item 10 is **partially implemented/tested**: full finalized integrity, immutable
missing/torn prefix recovery, bounded navigation rebuilding and explicit cancellable
salvage are implemented. Salvage exposes only independently valid ranges and retains
known source corruption; it does not certify global completeness. Arbitrary damaged-
framing range discovery, a disposable rebuilt-index cache, exhaustive fast-tail/
predecessor/cancellation boundary proof remain pending. Item 9's entry-ID seek works;
monotonic-time and stream-filter seek do not yet exist. No wire semantics changed.

**Unimplemented:** asynchronous timer/admission/queue/Stop/backend durability or
export guarantees, production camera/application integration, time/stream seek,
arbitrary damaged-framing discovery/rebuilt-index cache. **Partially tested:**
maximum index depth and fast-recovery budgets, every accepted module/resource
combination and cancellation latency. **External pending gates:** real native
Windows/NTFS and ART runtime. [Native Windows protocol](WINDOWS_VALIDATION.md)
is prepared, not executed. R3 #8 remains open; R4/R5/R6/R7 are not started.


A final resource audit found another real retention risk: recovery stored up to 64
full suffix bodies. Both implementations now retain fixed envelopes and reread
one body at a time. [The targeted recovery stress](linux-recovery-memory-validation.json)
checks17 CHUNKs/134,279,895 source bytes, exact full-prefix/hash parity, source
immutability,128 MiB capped JVM and17,076,055 traced Python peak bytes (<80 MiB).
An initial parameterized stress source incorrectly reused a 60 MiB default payload
hash after selecting 8 MiB chunks; both readers correctly rejected it as committed
integrity damage. The generator now hashes its explicit selected size; no failure
was reclassified or assertion weakened. The huge temporary artifacts are not in Git.
