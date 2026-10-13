# R3 hardening and acceptance boundary — 2026-10-13

Authority: [frozen LMTR v1](../LMTR_WIRE_V1.md), acceptance
`8c343cd6cf1ca11eff4f3bbaa5c2b10dfbbf645f`. Starting checkpoint
`1f1a668caf0af9c0c4d0f9ba036a88cd86cbddb0` and all earlier evidence are preserved.
No normative amendment, application integration, camera operation or R4/R5 work.

## Evidence-based gap matrix

| Category | Evidence / current boundary | Acceptance consequence |
|---|---|---|
| Required R3 contract | Independent JSON/framing/payloads/closure/chronology/seek, 51 pinned cases, original Float32/masks/native views, finite 8-level indexes, >4 GiB Fedora source, bounded recovery; new combined hostile oracles and cancellation reports | Host primitives have tested finite conformance; native Windows remains a required R3 platform gate. No exhaustive/universal claim |
| Optional reader enhancements | §13 explicitly makes rebuilt-index cache optional; salvage stops at damaged framing and reports only verified ranges. Fast path searches a preceding verified record within the bounded tail | Arbitrary interior range discovery/cache deferred; their absence does not fail pure R3 contract acceptance |
| Android ART | No `device` from authorized wireless ADB and no mDNS endpoint at preflight; no pairing/interaction attempted | **NOT EXECUTED**. Pre-R4 Android runtime compatibility remains desirable/unproven; host JVM is not ART evidence |
| Native Windows / NTFS | No authorized native Windows executor supplied/available; PowerShell protocol and optional sparse adapter prepared | **NOT EXECUTED**, required independent R3 gate; WSL and previous R2/legacy Windows results do not replace it |
| R4 production recorder | §14 producer admission, 1-second idle seal, 5-second checkpoint timer, queue/Stop/source/memory-pressure races not implemented in synthetic writers | Deferred R4 requirements, not a pure R3 reader/corpus failure. Production recorder acceptance is not claimed |
| R6 publication/backend | §15 provider/export/directory/close acknowledgment and physical durability not certified; END only declares source finalization | Deferred R6/backend-specific requirements; no power-loss/SAF certification or successful live Stop inference |

Historical paragraphs in IMPLEMENTATION_PLAN and prior JSON reports retain their
checkpoint-time limitations. This matrix supersedes their grouping of optional,
platform and production-runtime work into one undifferentiated R3 failure list.

## New meaningful coverage and repairs

[Mixed-corruption evidence](linux-hardening-validation.json) independently checks
29 bounded temporary cases in both runtimes. They do not add/repin canonical files.
The oracles specify the expected class before either reader executes:

- Damaged END/CHUNK footer with plausible false markers; malformed/incomplete tail
  after a valid prefix; known committed gap plus missing END retains unknown tail.
- Complete promised source-body or committed-union damage combined with damaged
  INDEX/CHECKPOINT/END never becomes a routine gap.
- A real 37-CHUNK source with two checkpoints: legal immutable historical pages,
  corrupt latest checkpoint fallback, corruption before an earlier checkpoint,
  and a malicious orphan page outside the final root.
- Conflicting/duplicate CHUNK references, signed-64 offset/length overflow,
  unsupported HEADER/CHUNK features during recovery, wrong decoded length behind
  a correct outer hash, nested and cross-observation view parents.
- Single-file lineage self-parent rejection, approach-B refusal of the known
  historical `lmthermal-radiometric-recording` dataset identity, and null parent
  entry without nonempty namespaced reference evidence.

Concrete repairs: explicit Python checked reference arithmetic (it already refused
out-of-source references, but with a different class), missing Python self-parent
validation, and missing/weak legacy/null-entry guards in both runtimes. Unknown
single-file formats are not globally whitelisted or reinterpreted; no external
parent is opened. A generic reader can validate that evidence exists, not certify
the real-world meaning of arbitrary module JSON or an unavailable parent source.

Earlier coverage is retained instead of duplicated: strict Decimal precision/Unicode
limits, Float32 signed zero versus +0 invalid filler, mask authority/tie statistics,
logical/native-view hashes, codec Adler/truncation/trailing bytes, all 33 positive
chronology/seek oracles, geometry/budget limits and 8-level/fast-recovery stress.
The full-check local-budget regression verifies refusal before decoding a source
CHUNK. A refused full check does not return partial numerical success.

## Cancellation and resources

Both `check(..., cancelled=callback)` APIs now poll before record access, propagate
`cancelled` through terminal/recovery handling and restore callbacks in `finally`.
The caller owns Python context/Kotlin `use` closure on all exits. Full checks return
no successful partial report. Explicit salvage keeps only already validated ranges,
`salvaged_chunks`, unknown tail and no declared successful END.

[Cancellation evidence](linux-cancellation-validation.json) records deterministic
requests at polls 10/full, 8/recovery, 12/salvage on 206,675/205,253-byte sources.
Each runtime processes **zero additional record accesses** after the request; exact
source SHA remains unchanged and Linux descriptors return to zero. Latencies include
owned reader close, not source generation, SHA probes or subprocess startup. Python
traced allocation excludes native memory; JVM heap is sampled at polling boundaries.
These are observed runs, not percentile distributions or hard real-time ceilings.

One admitted body hash, JSON validation or decompression is synchronous. Cancellation
inside a large operation is **not implemented or measured**, and may wait until the
next record boundary. These APIs do not promise preemptive cancellation or a universal
latency bound. Reader-local resource refusal remains separate from cancellation and
wire incompatibility. No indefinite stress loop or huge committed file was added.

## Independent consistency audit

| Accepted invariant | Independent evidence and truthful limit |
|---|---|
| Original Float32 authority / zero validity | Exact binary hashes, negative-zero tests, no-valid and masked files; no LUT/thermometry recomputation |
| Native views | Independently supplied recipe/slice equality plus logical hashes; bounds/parent/cross-observation attacks reseal outer integrity |
| Precise optional JSON | 43 Kotlin/Python exact canonical vectors, low Decimal contexts, Unicode scalar order; source-native nonfinite/signed-zero bits remain typed binary evidence |
| Intake chronology / stream closure | 33 sequential oracles and 44 indexed queries; receipt/sensor clocks remain independent, no sensor synchronization claim |
| Context / segment closure | Semantic IDs include unknown values, local definitions plus physical journal and changed-source cases; hashes are integrity, not authenticity |
| Finalization / actual close | Missing UTC stays unknown, missing END stays incomplete; END is source-declared, not backend-close/Stop acknowledgment |
| Declared / verified integrity | Open/selected/full/recovered/salvage scopes remain distinct; known committed source damage is not a gap |
| >4 GiB / limits | Existing exact sparse source, one selected CHUNK, finite depth/recovery probes; local refusal is explicit, not universal heap certification |
| Historical conversion B | Known multi-file identity now refuses; no manifest-as-dataset source, estimated-clock conversion or converter implemented |

No contradiction requiring a frozen-specification erratum was established. Tests
cannot independently certify the truth of device clocks, sensor calibration, consent
or arbitrary module interpretation evidence. Documentation checks alone are never
runtime conformance. The complete cross-runtime suite is rerun with current code;
source-pinned historical regeneration is checked separately.

## Corpus durability and reproduction

The **same 51 files, plans and original manifest** remain byte-for-byte pinned.
Manifest SHA-256: `29b262a1f831b119c5094dd7385869ea03fdd44b0947263922ead0bd77f660e3`.
Earlier reports are unchanged; new hardening/cancellation/summary reports are separate.
Reference source changes cannot silently rewrite the old generation receipt.
`tools/check_lmtr_pinned_corpus.py` extracts the original tracked inputs from
`1f1a668` into an owned temporary directory, runs the original generator `--check`,
and compares the current corpus to those original bytes. Current-reader conformance
is independently measured by the comprehensive suites and interoperable rewrites;
it is not implied by old-generator regeneration.

```bash
./gradlew --offline -p tools/lmtr-r3-host :lmtr-contract:clean \
  :lmtr-contract:test :lmtr-contract:writeRuntimeClasspath \
  :lmtr-contract:writeTestRuntimeClasspath
PYTHONDONTWRITEBYTECODE=1 python3 -W error -m unittest discover -s tools/lmtr_v1/tests -t . -v
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_hardening.py --report hardening.json
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_cancellation.py --report cancellation.json
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_pinned_corpus.py
```

The isolated host Gradle settings are tested on Fedora without configuring the app
or requiring its SDK. Native Windows commands and unexecuted adapter limitations are
in [WINDOWS_VALIDATION](WINDOWS_VALIDATION.md). #8 stays open and #14 draft/unmerged.
Host hardening is reviewable; complete R3 integration acceptance still requires the
real native Windows gate. ART and optional extensions remain separately identified.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Executed final host totals

[The new summary](linux-hardening-summary.json) records 71 JVM R3 /64 Python /232
core tests, with zero failures/skips in the JVM suites and Python warnings treated
as errors. Earlier checkpoint totals were65/61/232. Clean module rebuild, cleanTest
core re-execution, offline debug build/lint, both-runtime51 classifications/33
Kotlin rewrites,29 mixed-corruption oracles,3 cancellation modes, pinned original
regeneration and documentation/diff checks pass. Previous summaries remain unchanged.
