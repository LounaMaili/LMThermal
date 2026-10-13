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
- [x] 9. Bounded paged entry/time/stream navigation, checkpoints and actual maximum depth tested (finite host scope).
- [x] 10. Pure contract END/finalization and bounded truthful read-only recovery/errors (finite host scope; platform gates below).
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


## Additional host contract coverage

The first two checkpoints remain preserved. Independent intake-time/stream seek
now selects the first recorded participating entry at/after the requested common
timeline, including explicit segment events. Not-found results contain null entry
fields; source clocks never replace the intake chronology. All 33positive fixtures
match sequential oracles, and 44cross-runtime seek queries pass.

On-demand [hard-limit evidence](linux-limit-validation.json) verifies actual legal
eight-level indexing/full parity, ninth-level refusal,65 MiB fast-tail refusal,
1,025candidate and predecessor refusal,64 suffix chunks accepted and 65refused.
The ninth-level error is aligned to the resource class. Both implementations now require typed ASCII tokens to use a a full lexical match, rejecting a trailing newline without changing
unknown optional JSON strings. Eight cross-runtime compatibility/lexical cases pass.
Kotlin synthetic publication now defaults unrecorded finalization UTC to unknown,
matching Python; known earlier creation UTC is not evidence of Stop/publication UTC.

A whole Python run initially overlapped corpus publication and failed with a
missing fixture path. Regeneration and corpus-consuming tests are now serialized;
no assertion or wire rule was weakened. Current final suite totals and hashes are
in [the refreshed summary](linux-validation-summary.json). Earlier 60/54counts above
are retained checkpoint history, not current totals.

Current remaining work: real native Windows/NTFS and ART; measured cancellation
latency and exhaustive mixed-corruption/admission/resource combinations; arbitrary
damaged-framing discovery/rebuilt-index cache; asynchronous producer timers/Stop/
queues/backend durability and production integration. R3 remains incomplete/open.


Current extended host checkpoint also enforces two Python append-sync barriers
(body before promised footer, then footer before acknowledgement), tested by an
injected body-sync failure yielding zero saved CHUNKs and no END. Synthetic recipe
and complete declared physical products are admitted before byte allocation;
provisional plan offsets are recalculated before full wire validation. A known
creation UTC is never silently reused as finalization UTC. None of these host
checks certifies directory/provider durability or physical power-loss behavior.

Final current results supersede earlier checkpoint counters:65 JVM R3 tests,
61 Python tests,232 unchanged core tests;51 canonical classifications per reader,
33 Kotlin-write/Python-full-read and 33 canonical plan-byte comparisons;43 JSON,
8 version/lexical,3 codec,44 time/stream,2 navigation-recovery and 4 scan cross cases.
All reported numbers are tied to durable reports; R3 remains open/incomplete for
native Windows/ART and disclosed runtime/mixed-corruption/backend gates.

## Final hardening checkpoint

See [the current gap matrix and independent audit](HARDENING_REVIEW.md) for required
R3 versus optional recovery, ART, native Windows and R4/R6 boundaries. The previous
checkpoint paragraphs above are preserved history, not the current gate grouping.
Mixed-corruption, lineage, checked arithmetic and measured cooperative cancellation
are covered in new durable reports. Native Windows remains NOT EXECUTED; ART was
unavailable through authorized ADB without intervention. No production code or frozen
wire/corpus change. R3 #8 stays open and PR #14 stays draft.

Final hardening totals: **71 JVM R3**, **64 Python**, **232 core**, all passing;
**29** additional independently checked temporary hostile combinations and measured
full/recovery/salvage cancellation. [New summary](linux-hardening-summary.json) and
[interop continuation](linux-hardening-interop-validation.json) preserve earlier
checkpoint files. The 51 fixtures and original manifest hash remain unchanged.
