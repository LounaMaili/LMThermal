# Changelog

All notable changes to LMThermal will be documented in this file.

## [Unreleased]

### Added (R4 measured heap refusal)

- Record Pixel ready-state occupied Java 16.76–228.85 MB and headroom down to 39.59 MB: nine of 20 read-only ready samples fail the unchanged 110 MiB admission threshold. Preserve the partial fresh-process window and original refusing history; no physical LMTR, lucky-GC retry, long profile or reservation reduction follows.
- Record 405 JVM/64 Python tests, six byte-identical pre/post engineering artifacts and12 host original-owner readbacks; actual Pixel12 camera-free tests/six readbacks and exact be41db4 API26/API35 88/88 tests plus six readbacks each pass. Keep measured occupancy and unproven generic peak-live accounting separate.

### Fixed (R4 partial memory diagnostic receipts)

- Refuse recorder Start while a read-only health window is active, preserving its no-recorder provenance and avoiding diagnostic sampling overhead during recorder admission.

- Record completed sample count and cancellation/error classification for future read-only health windows instead of silently treating a partial diagnostic as complete. Preserve the observed11/20 fresh window without claiming its cause or rerunning for favorable GC.

### Changed (R4 memory diagnosis)

- Eliminate writer-side payload copies used only for hashes, concatenation and statistics. Retain constructor/public copy isolation, exact source bytes and both full validation boundaries; change no reservation factors, safety margin, queues or profile roles.
- Add read-only finite field-health sampling before a source exists, using existing Runtime/Android probes without controls, recorder, forced GC or device settings. Distinguish occupied heap from reachable live memory and actual lifecycle from a diagnostic phase label.
- Add six full-size existing-HT-fixture engineering cases across Analysis/Native/Full and STORED/DEFLATE, bounded ownership/queue assertions, sampled occupied/native/PSS observations and independent original-owner readback. Preserve canonical fixtures and previous refusal evidence.

### Added (R4 conditional physical attempt)

- Preserve actual Pixel 8/API37 HT-301 initialization and normal single-owner Close evidence on cd6606f. The 20-second Full smoke was refused for Java headroom before any LMTR was created; no longer profiles or automatic retries ran. Keep original private logs/OS snapshot locally and publish only sanitized metrics/hashes, without inferring recording parity or memory plateau.
- Record 405 JVM, 64+8 Python and eight actual camera-free Pixel ART safety/clock/fixture passes and independent original-owner readback of three saved-room profile files. Keep previous host/emulator receipts and remaining physical smoke, matched profiles, memory/freshness/byte-parity/recovery gates distinct; PR #16 remains draft and #9 open.

### Fixed (R4 admission refusal evidence)

- Preserve exact sampled Runtime heap/headroom and read-only health/preflight facts before refusing field admission. A later GC/dumpsys snapshot cannot reconstruct an earlier safety refusal; do not reduce reservations or force GC to manufacture acceptance.

### Added (R4 conditional unattended safety)

- Record owner authorization for bounded unattended physical validation, conditional on existing permission/unlock/scene/device/resource prerequisites. Keep missing prerequisites and actual runs distinct from emulator evidence.
- Add deterministic test-only resource/cadence safety policy and read-only Android probes; refuse unknown safety facts, stop unhealthy/interrupted runs and retain originals without automatic retry. Keep 4 MiB queues/chunks and existing production camera/thermometry behavior unchanged.
- Use a 20-second Full smoke and activity-local keep-screen-on only during bounded experiments, clearing it on Stop/failure/background. No pairing, permission bypass, global settings, background service or physical fault injection.

### Added (R4 final Phase A runtime receipts)

- Record exact field code f90db85 with 398 JVM, 64+8 Python and 80/80 camera-free ART tests per API26/API35. Preserve source-byte parity for 14 engine files, 75 synthetic observations and three existing-room profile readbacks per API, plus the failed API26 scheduling checkpoint. Keep physical Pixel8/HT tests explicitly unexecuted/operator-gated.

### Fixed (R4 matched measurement window)

- Close acquisition/preview aggregates at Stop so the subsequent full readback and streaming hashes cannot inflate the matched performance interval. Keep codec/seal/drain timing active and report its separate finalization scope.

### Fixed (R4 deterministic preparation-overload regression)

- Preserve the genuine API26 failure at run 38450510713: the preparation-only test reserved just one writer entry and could independently drop its second already-prepared owner while HEADER sync lagged. Reserve both accepted owners in this test only, retain exact freeze/thread/two-ack assertions and add zero writer-loss assertion. Product/field queue capacities are unchanged; writer-overload coverage remains separate.

### Fixed (R4 field failure publication)

- Stop the bounded field run if its private metric writer fails and prevent a failed/missing terminal readback from enabling a longer run. Preserve diagnostic classification and original files; baseline without a capture does not manufacture a source-immutability verdict. Receipt-domain validation applies only during active intake; a later explicit source reopen cannot relabel an already interrupted run as a clock error.

### Added (R4 operator-gated field preparation)

- Add a separate test-only arm64 field APK reusing the existing single HT acquisition/preview owner, authoritative observer, adapter and recorder with explicit Start/Stop, app-owned bounded files, private streaming source oracles and read-only terminal recovery. Production app has no recorder dependency or recording UI.
- Configure physical intake/timeline/timers with Android elapsedRealtimeNanos; retain truthful source IDs, unknown UTC/sensor/resolution and test domain/regression/recreation/suspend boundaries. Add conservative per-profile storage admission, unchanged 4 MiB queues/chunks, bounded metrics and private streaming ADB extraction/independent Python byte checks.
- Add inert optional lifetime injection and diagnostic session-reason/gate-duration facts; camera controls, initialization and thermometry are unchanged. Optional seal/codec timing observers cannot alter encoded bytes or poison acknowledged commits.
- Document the complete two-phase operator protocol and historical R2 comparison. Physical tests remain unexecuted and require explicit go-ahead; R4 #9/PR #16 remain open/draft, frozen wire/corpus and Desktop unchanged.

### Added (R4 resumed validation and final runtime receipts)

- Preserve interrupted documentation/reports and all failed experiments; record exact tested code `abea098`, 380 JVM/64 Python tests and actual 61/61 ART tests on both API26/API35. Independent Python checks 14 actual engine outputs, original bytes in three recovered prefixes, an honestly failed END/gap, 75 source observations per ART job and original source immutability.
- Record current 120-second/3000-observation synthetic source on the same code SHA, original clock/intake/oracle hash checks, zero preparation/writer drops, 118 chunks/23 checkpoints, 198.535 ms Stop and separate charged queue/in-flight/pending peaks. Preserve prior run receipts and publish only sanitized metrics/hashes, not recordings or large JSONL files.
- Document sticky noncertifiable loss boundaries, exceptional callback copy cost, exact runtime/environment receipts and reproducible host commands. R4 remains incomplete pending opt-in physical Pixel8/HT-301 source/lifecycle/cadence/freshness/memory/failure validation; no recording product UI, R5/R6, frozen wire/corpus or Desktop change.

### Fixed (R4 precise worker-failure accounting)

- Distinguish an idle preparation interruption with no admitted/queued evidence from a worker abandoning an intake item or queue. Only actual unrepresented evidence sets the sticky no-END condition; add an instance-specific interruption regression proving the empty failed recording can finalize honestly.

### Added (R4 independent mixed-failure prefix evidence)

- Extend the existing engine probe with an acknowledged prefix followed by unjournaled loss/multiple errors and an honestly finalized represented source failure. Python now checks original payload bytes in verified recovered prefixes and exact known-gap reasons/ranges, preserving all older outputs and the immutable canonical corpus.

### Added (R4 clock/oracle and resource-peak continuation)

- Add source-oracle SHA-256, exact original receipt/acquisition/schedule/device/source facts and genuine callback intake bounds to finite source stress; independent Python checks these without changing wire metadata. Expose charged preparation/writer in-flight and pending peaks alongside existing queue peaks; no capacity increase or ART/RSS claim.

### Added (R4 combined-failure integrity regression matrix)

- Cover both preparation/journal-exhaustion orderings, racing Stop/detach/source-error/memory pressure, multiple termination errors, repeated Stop after failure and unexpected preparation exit. Assert no noncertifiable loss can produce END, while completely journaled source failure can retain an honest failed END. Strengthen post-ack navigation recovery/source immutability assertions.

### Added (R4 exceptional acquisition copy-cost probe)

- Measure callback-side immutable ownership of synthetic malformed full-size transport separately from deferred HT evidence freezing; verify copied buffers cannot be mutated by the source. The host/ART test tool performs no camera access and times no parsing, thermometry, hashing, compression or I/O.

### Fixed (R4 noncertifiable loss remains sticky)

- Retain synthetic ART outputs/readbacks after a failing assertion while preserving the strict non-success verdict.
- Preserve a sticky unrepresented-loss condition when the loss journal is exhausted or a preparation worker fails unexpectedly. A later exception may change diagnostics but cannot authorize END for abandoned evidence. Actual isolated API26 ART exposed the prior reason-overwrite race; add a deterministic mixed-failure regression, preserving the failed run and original assertions.

### Added (R4 authoritative-source validation receipts)

- Recorded 374 passing JVM tests, 64 Python tests, Android build/lint, unchanged accepted wire and all 51 pinned fixtures. Actual API26/API35 owner-guard checkpoints passed 55 ART tests each with independent Python readback of 12 engine files and 75 synthetic source observations per API; emulator evidence is not Pixel8/HT performance.
- Recorded an exact-SHA 120-second 25 Hz non-USB host repeat: 3000 retained observations, 38 explicitly unavailable planes, zero preparation/writer drops, 118 chunks, 23 checkpoints and 184.802 ms Stop. Published sanitized queue/admission/copy-cost/integrity receipts and preserved earlier reporter/infrastructure failures and prior reports.
- Updated source/lifecycle/configuration/roadmap documentation and kept physical adapter/cadence/freshness/memory/recovery gates pending. No product recording UI, controls, thermometry, frozen contract, canonical fixture or Desktop change.

### Fixed (R4 ART build isolation)

- Add a camera-free Gradle entry point excluding production app configuration and its NDK/CMake/UVC requirements, after an API26 job booted but failed provisioning an unrelated product NDK. Reuse the exact same contract, recorder and adapter sources; preserve the failure and product ABI.

### Fixed (R4 emulator matrix literal)

- Correct the API35 image target to a literal matrix value; expressions apply only when consuming the completed matrix. This is CI provisioning only.

### Changed (R4 API26 emulator provisioning)

- Use the official minimal AOSP x86_64 image for camera-free API26 tests after repeated pre-test Google image archive-download failures; API35 remains Google APIs. Tests need no Google services; keep all earlier failures and runtime gates unchanged.

### Fixed (R4 optional owner notification races)

- Serialize optional recording lifetime notifications with camera-owner generation invalidation, preventing a late open callback after immediate Close from reviving source intake. Isolate optional observer exceptions so original camera release can complete; add two deterministic owner regressions.

### Fixed (R4 synthetic clock and preview evidence)

- Keep synthetic preview cadence independent of camera acquisition statistics and leave actual common-clock resolution unknown instead of equating nanosecond units with measured precision. Preserve the third API26 pre-test SDK image-download failure.

### Fixed (R4 emulator evidence gates)

- Treat emulator log-buffer clearing as best-effort diagnostics while keeping strict instrumentation/readback verdicts; explicitly select and verify an emulator before any ADB operation. Preserve the API35 passing checkpoint and API26 pre-test logcat failure.

### Added (R4 authoritative acquisition and lifecycle integration)

- Added an optional module-owned acquisition observation channel before UI StateFlow conflation, with genuine nanosecond receipt witnesses, qualified native callback loss signals and immutable source owners. HT-301's existing gate/control/thermometry path is unchanged; the channel is inert without explicit subscription.
- Added a separate camera-recording adapter module, deferred owned-evidence admission on the existing recorder preparation worker, exact HT-301 Float32/native/transport/settings/calibration evidence and guarded Start/Stop/lifecycle integration. The product does not link the adapter/recorder or expose recording UI.
- Added session-generation guards, source-error outcomes, callback isolation, owner close/replacement/background/rotation/screen-off tests and an expanded ART harness. Fixed test-only evidence extraction after Gradle's automatic target uninstall; the first API26/API35 run passed 37 ART tests per emulator but its extraction gate failed and is preserved.

### Added (R4 camera-free Android runtime harness)

- Added a separate test-only Android application packaging the existing LMTR contract/recorder without production UVC/JNI or changing the product arm64 ABI. It shares recorder regressions and tests exact JSON/binary/hash/codec semantics, all 51 pinned inputs and real synthetic engine output.
- Added exact-SHA branch-restricted API 26/35 x86_64 emulator CI with pinned setup actions, read-only permissions, disabled emulator cameras and retained logs/synthetic artifacts. Independent Python can read ART-produced outputs directly; the initial packaging checkpoint was pending until runtime artifacts were reviewed; the later receipts above record actual scope.

### Added (R4 camera-free recorder foundation — 2026-10-13)

- Added an isolated pure-Kotlin recorder module with owned module observations, exact Float32/masks/native/acquisition evidence, context/clock/session identities, explicit capability profiles and qualified source losses. It is not linked into the app and adds no user recording action, camera controls or thermometry change.
- Implemented bounded asynchronous preparation/writer stages, separately accounted in-flight/pending/control reservations, truthful overload gaps, first-intake idle sealing and post-ack checkpoints, global segment transitions, idempotent cutoff/drain, local append/close/reopen and preserved incomplete files on uncertain failures.
- Extracted the existing R3 writer/index into a shared incremental session used by both fixture and recorder paths, with private owned state and poisoned-owner guards. Wire/schema and all 51 pinned fixtures are unchanged.
- Added deterministic non-USB simulations, failure/race tests and independent Python full readback of actual engineering outputs. At this initial foundation checkpoint ART had no available authorized device/emulator; later source/lifecycle/ART receipts above supersede that availability status, while physical live acceptance remains pending in open #9.

### Changed (R3 integration — 2026-10-13)

- Recorded owner-authorized PR #14 merge at `8eb4ace`, exact final Windows head/run scope and #8 closure; preserved R2 and all historical receipts. A separate CI follow-up PR #15 retains native Windows main/PR regression scheduling outside the accepted R3 merge.

### Added (R3 native Windows CI validation — 2026-10-13)

- Added a branch-restricted, exact-SHA Windows GitHub Actions protocol with JDK17, Python3.13, bounded resources, read-only token permissions and persistent success/failure artifacts. It executes the existing independent suites and native NTFS sparse/full-integrity gate without camera or Android dependencies.
- Preserved the first native CI failure before tests and hardened no-output Git handling with explicit arrays and detailed failure-location artifacts.
- Recorded actual GitHub-hosted Windows Server2022/NTFS success:71JVM/64Python,51case classifications per reader,33independent rewrites, matching43JSON vectors,29hostile cases, cancellation and sparse >4GiB/full71CHUNK integrity. Exact raw receipts and passing/failed run/artifact hashes are retained; ART stays unexecuted.
- Added four native preflight rejection guards for wrong SHA/ref/context and dirty source.
- Restricted detached checkout acceptance to the exact authorized Actions push branch/ref/SHA and retained strict HEAD/clean-source checks; report summaries now include Python counts and checkout mode. The frozen wire, canonical corpus and production applications remain unchanged.

### Fixed (R3 final hardening and platform preparation — 2026-10-13)

- Added independently checked mixed-corruption, historical checkpoint/page, feature/view and lineage attacks without modifying the 51 pinned fixtures or their original manifest. Enforced Python signed-64 reference arithmetic/self-parent rejection and both readers' approach-B/null-entry lineage boundaries.
- Added cooperative full/recovery cancellation with truthful non-success propagation, callback restoration and measured record-boundary cleanup; large synchronous hashes/decompression remain non-preemptive. Preserved prior reports and added new hardening/cancellation checkpoints.
- Prepared an SDK-independent host build, reviewed native Windows PowerShell runner and optional bounded-disk NTFS sparse adapter. Native Windows/ART are explicitly NOT EXECUTED here; optional reader enhancements and R4/R6 producer/backend duties are separated from R3 contract requirements. No frozen wire, production application, camera or thermometry changes.

### Added (Isolated LMTR v1 R3 conformance checkpoint — 2026-10-13)

- Added a standalone Kotlin/JVM contract module and independently structured standard-library Python reference for accepted LMTR v1 exact JSON, framing/integrity, metadata, grids/masks/evidence/views, STORED/DEFLATE, chronology, paged indexing and immutable bounded-prefix recovery. Neither is wired into Android or Desktop production applications.
- Generated 51 small synthetic canonical binary files (33 positive, 18 negative), exact provenance/SHA inventory, bidirectional runtime interoperability and numerical/adversarial checks. Added on-demand sparse-offset stress tooling; explicit selected/full scopes and local resource refusal prevent partial verification claims.
- Fixed test-discovered edge cases in empty reference bounds, synthetic Float32 recipes, snapshot fanout/body admission and historical index-page validation. Kept exact binary signed-zero/mask authority and precise unknown optional JSON semantics.
- Documented reproducible host commands, implemented scope and remaining Windows/ART, general salvage and asynchronous recorder/backend durability gates. R3 #8 remains open; no R3 merge, production recorder, camera/thermometry change or physical-calibration claim.

### Fixed (R3 recovery, resource and interoperability continuation — 2026-10-13)

- Preserved and published the interrupted R3 checkpoint; added immutable navigation-damage recovery, explicit cancellable salvage with narrow verified-range scope, checkpoint-chain guards and regressions. Known committed source corruption remains an integrity failure and is never fabricated as a missing observation.
- Replaced retained recovery suffix bodies with bounded fixed references; added an independent 17-CHUNK/128 MiB source-prefix memory/parity stress. Removed image-sized Python statistics temporaries, aligned strict JSON input/canonical byte budgets and version/codec error classes, and rejected unsupported Kotlin CLI options instead of silently ignoring requested seek semantics.
- Recorded real Fedora sparse access beyond 4 GiB with independent full Python integrity, sampled memory and explicit Kotlin local refusal for a large chunk. Added cross-runtime version/codec/recovery/scan vectors, native Windows instructions and byte-preserving corpus/source checkout attributes; native Windows/ART and remaining runtime gates stay pending.

### Added (R3 indexed intake-time and stream selection — 2026-10-13)

- Added independent bounded Kotlin/Python seek APIs and CLI commands selecting the first recorded entry at/after the common recorder-intake time, optionally filtered by declared stream participation. Original sensor clocks stay separate; explicit segment events and not-found results remain truthful.
- Compared every positive fixture to sequential chronology oracles and added bidirectional runtime seek cases. Exercised eight-level indexing and explicit candidate/predecessor/suffix/tail recovery limits; aligned depth refusal and strict typed-token newline validation.
- Kept absent finalization UTC unknown rather than borrowing earlier creation UTC. Added Python body/footer sync barriers with an injected incomplete-append regression, explicit storage errors and pre-allocation bounds for synthetic recipes. No wire, application, measurement or camera behavior changed; remaining platform/runtime conformance gates stay open.

### Changed (Explicit LMTR R1b acceptance — 2026-10-13)

- Recorded explicit owner acceptance of complete r1b-review-2, D1–D10 and historical-conversion approach B; published `docs/LMTR_WIRE_V1.md` as the normative frozen R3 specification with unchanged reviewed rule body/constants and byte-equivalent local shape definitions.
- Preserved the original review document, decisions, illustrative metadata and R2 history; distinguished wire acceptance from implementation conformity/end-user release. Updated canonical references/registry/roadmap; authorized integration and R1 closure remain gated on successful checks and actual main integration.
- R3 pure Kotlin/Python contract work is authorized after integration. No production camera/thermometry/UI, Desktop implementation, LMTX v1, recording prototypes or runtime dependencies were changed by acceptance.

### Changed (Targeted recording R1b review-2 corrections — 2026-10-12)

- Kept the unaccepted draft structure/R1a invariants/R2 evidence; clarified single-file Parent limits and recommended deferring historical multi-file provenance/imported timeline semantics to a future required feature, preserving R7's explicit conversion gate and strict legacy coexistence.
- Made immutable stream-purpose inventory consequences explicit for module replacement, unexpected visual/second-camera streams and exhausted slots; no dynamic declarations were added.
- Corrected byte-hash versus canonical semantic-ID wording, bounded lexical decimal exponents including zero, clarified strict integer spelling and source-type/negative-zero bit preservation, and listed required future cross-language R3 vectors.
- Specified serialized timer/admission/Stop/backpressure/sync/memory-refusal behavior with bounded control flags and observable outcomes. Extended documentation-only precision checks; all 14 existing illustrative cases remain byte-identical. No production code, converter, device access, canonical R3 bytes, merge or owner acceptance.

### Added (Complete proposed recording R1b wire draft — 2026-10-12)

- Proposed the complete camera-independent LMTR container/schema 1.0/envelope-1 contract: exact release framing, generic stream/segment/context/payload grammar, authoritative Float32/masks, precise clocks/gaps, codecs, integrity/index/recovery, resource/admission/idle and storage/lineage semantics. Every rule group distinguishes accepted R1a goals, scoped R2 evidence and new R1b proposals.
- Added an owner decision/ambiguity table, local JSON shape schema, 14 explicitly noncanonical illustrative metadata scenarios and deterministic documentation-only Fedora checks. No binary LMTR/R3 fixture, production parser/serializer or device/camera test was introduced.
- Recommended STORED/DEFLATE baseline with Zstd ID2 reserved/unassigned, measured 4 MiB HT preference distinct from queues/hard bounds, first-entry monotonic idle sealing and bounded timed checkpoints; new grammar/timer/large-module performance remains unvalidated pending owner acceptance and later implementation.
- Updated navigation/current roadmap to the integrated, scoped-complete R2 status. R1b remains DRAFT, issue #6 open and R3/production gated. Existing LMTX v1, Android/Desktop code, R2 artifacts/reports and failed history are unchanged.

### Fixed (Celsius presentation starvation and session isolation — 2026-10-12)

- Isolated the R2-proven presentation correction: one in-flight render and one conflated latest request publish useful completed measurements during continuous valid arrivals, with matching bitmap/range/readings and bounded work.
- Reproduced a rapid same-device reopen leak with a deterministic blocked render and conflated Close; paired the existing owner invalidation generation with its snapshot through a read-only presentation guard. Replacement/settings/unavailable/disposal invalidate completion; device identity is not session identity.
- Added controlled replacement/reopen, slow-source, cancellation and temperature-only generic-module regressions. No recorder/test-prototype build wiring, protocol, acquisition, thermometry, USB initialization, source coordinate or LMTX/LMTR semantic change.

### Added (Reviewed R2 evidence integration preparation — 2026-10-12)

- Integrated durable scoped HT-301/Pixel 8 completion, throughput/sampled-memory, Linux/native Windows parity, framing/recovery/backend limits and R1b recommendations from reviewed R2 tip `747b89c`, with byte-identical curated JSON evidence and commit-pinned historical source/report/artifact references.
- Recorded owner acceptance of the review for integration preparation, pending separate merge/closure authorization. Distinguished 4 MiB preferred chunks, unchanged queues, larger hard file ceilings and sampled memory targets; retained failures, deferred Zstd and unresolved idle-flush/generic-schema decisions.
- Kept disposable prototypes, build wiring, private recordings and presentation code out of this branch. R1a and LMTX v1 semantics remain unchanged; no wire acceptance, R3 or production recorder.

### Changed (Recording R1a owner decisions and planning — 2026-10-07)

- Recorded explicit owner acceptance of the next-stage Analysis default/visible first-use choice/remembered per-module selection, deferred expert checkboxes, Full / Research meaning and helpful storage policy. Unknown capacity warns and allows Start; known insufficient startup+reserve blocks; write refusal preserves prior valid commits without counting pending frames saved.
- Added `docs/LMTR_FORMAT_CANDIDATE.md` to separate accepted full-grid/evidence/Celsius/lossless/clock/gap/immutability/coexistence direction from candidate views and provisional identities, exact framing/checksum/index/codec/limit/backend mechanics.
- Defined R2 device throughput, exact codec parity, bounded forward chunks/indexes, failure recovery, adversarial views and backend/no-copy publication gates; R1b owner wire review/freeze must follow R2 before the canonical R3 corpus and dependent production implementation.
- Created and cross-linked the R1–R7 GitHub roadmap (LMThermal #6–#10; LMThermal-Desktop #2–#3), with R1a complete, R1 open for R1b, R2 ready only for authorized feasibility and R3–R7 blocked/later. Updated architecture/product/module references. No new recording implementation, application/camera/thermometry change, fixture or benchmark rewrite; Accepted LMTX v1 remains unchanged.

### Added (Common radiometric recording review draft — 2026-10-06)

- Audited Android owned measurement/module/still-export/lifecycle seams and Desktop legacy recording/playback read-only; documented reusable concepts and platform/HT-specific boundaries.
- Proposed an independently versioned recording format with complete native-coordinate temperature grids, explicit validity/clocks/gaps/context, capability-driven Analysis/Native/Full evidence and restricted same-frame native views into exact acquisition payloads. Accepted LMTX v1 still semantics are unchanged.
- Compared containers and proposed byte-bounded append-only chunks, paged indexes/checkpoints, committed-prefix recovery, resource limits, immutable source analysis and Android storage/publication without a mandatory second full copy.
- Added exact HT-301 storage budgets and an analysis-only lossless codec benchmark: 180 STORED/DEFLATE/Zstd role/group/dataset cases on saved live raw frames, exact roundtrips and unchanged source hashes. Separated held-room repetition from changing-frame results and offline-derived reference Celsius; no Android sustained codec/provider performance or physical accuracy claim.
- Added owner recommendations, future shared conformance corpus design, implementation gates and seven proposed issue bodies; the design is not accepted and no recording writer/reader/UI, fixture, application, camera or thermometry code was added/changed. Desktop stayed unchanged.
- Linked the draft from product/module/export documentation and corrected current still-interoperability status to completed/closed Android #5 and Desktop #1, retaining issue #4 as accepted still history.

### Fixed (Android camera lifecycle continuity — 2026-10-06)

- Replaced unconditional Activity-stop release with a generic retained UI-lifetime policy: rotation/configuration recreation and a registered SAF destination transaction keep the same single session, without reconnect or radiometric control writes.
- Kept awaited release for ordinary viewer background/finish, screen-off, Close, matching detach, fatal module ERROR/action failure and final disposal. Locale changes intentionally release before the replacement UI composes and require explicit reopen. Documented the outstanding-picker event-lifetime exception, including Home inside external SAF, without timers or a background service.
- Constrained MainActivity to singleTask entry so launcher/USB repeat entry above SAF reuses the retained composition root instead of allocating another coordinator; added a real-device entry regression.
- Preserved retained ROI/Point, palette/range and immutable export artifacts; rejected obsolete Activity lifecycle/result callbacks. HT-301 protocol, thermometry and Accepted LMTX v1 semantics are unchanged.
- Added bounded numeric owner/session/open/release/action/frame-continuity diagnostics, eight pure ownership-policy tests and two actual Activity/ViewModel regression tests; documented event policy and sanitized physical evidence.
- Validated Pixel 8/HT-301 picker cancel/success and both rotations on one ready session with zero extra controls, preserved ROI/Turbo/Locked settings and identical prepared capture bytes; confirmed single Close/detach/background/locale releases and conservative raw14 reopen. 232 core, ten Android JVM, 39 Pixel and ten localization-checker tests pass; build/lint/boundary/diff checks pass. Native callback samples remain near 25 FPS; hidden-picker worker replacement and existing invalid/held-frame gates remain observable limitations.
- Rechecked the final APK above a live destination picker: root re-entry cancelled SAF without changing its ready owner, reopening after Close worked without replug, and no extra SET writes occurred. Recorded an unresolved operator-observed blank initial Connect after test/reinstall separately from the passed continuity/reopen checks.



### Added (Android LMTX v1 still export — 2026-10-04)

- Added pure JVM owned capture/evidence models, bounded precision-preserving UTF-8 JSON, streaming DEFLATE/SHA-256 writer and camera-free physical ZIP/schema/payload/statistics checker under the unchanged Accepted v1.0 contract.
- Added module-owned optional export evidence: HT-301 preserves authoritative Float32 Celsius, unmasked native raw14, original full transport, actual calibration/settings/lookup trace and distinct trailer/literal center observations; the 160×120 simulator exports an honest visual-only capture.
- Added canonical 0/1 mask/positive-zero filler and deterministic current matrix/ROI/point analysis, separate stable palette/range/derived preview, truthful acquisition/receipt/creation clocks and explicit native-equivalent physical-accuracy warning.
- Added bounded background capture preparation, private finalize/sync/reopen verification, new-document SAF publication with close/failure/cancel/partial cleanup gates and finalized FileProvider sharing without broad storage permissions. Prepared captures survive source release and Activity/language recreation; no stale ready temperatures are reused.
- Added 15 EN/FR export/privacy resources with fixed status slots, expanded boundary/localization checks, eleven synthetic/previously sanitized shared conformance files with pinned hashes/outcomes, and writer/reader/publication/ART/lifecycle/locale regressions.
- Validated live exact current Float32 bits, raw/transport equality, half-open ROI and Turbo Locked 25–45 °C, SAF byte equality after lifecycle transitions, unavailable raw14 preview export, chooser/cancellation, near-25-FPS preparation and zero export controls. Final 224 core JVM, eight API-compatibility, 38 Pixel and ten localization self-tests pass; build/lint/diff checks pass.
- Documented local resource/self-checker limits, provider/privacy guarantees and the inherited unconditional onStop release for destination UI/rotation/language. USB does not technically require these releases; camera continuity remains a separate lifecycle follow-up. Android #5 remains open with Desktop #1 interoperability pending; no camera/session/thermometry semantics or Desktop importer changed.

### Added (Android native rectangular ROI — 2026-10-03)

- Added generic strict native half-open rectangles, inclusive touched-cell drag conversion and fitted cell-edge overlays without camera dimensions/protocol dependencies.
- Added LMTX-v1-compatible valid-only Float32 extrema, first row-major ties, Float64 means and exact valid/total counts; optional 0/1 masks and zero-valid regions are supported without changing HT-301's all-valid representation.
- Added explicit localized Point/ROI modes, create/replace/clear interaction and current-frame readings in a bounded analysis worker independent of palette/range. Compatible geometry persists across gaps; readings clear on unavailable/closed/detached frames and replacement source/geometry clears the selection.
- Added comprehensive JVM, ART worker/lifecycle/mask, locale/orientation gesture and performance checks; documented ROI architecture, validity, LMTX mapping and unchanged native-equivalent physical-accuracy uncertainty. No LMTX serializer/export, camera control/session or thermometry semantics changed.
- Fixed operator-reported ROI layout reflow by reserving font-scaled readout/legend slots before drawing and through unavailable frames; regression checks hold a pointer across rendered frames and compare viewport/control positions in both orientations and all four test locales.

### Added (Accepted LMTX v1.0 specification — 2026-10-03)

- Added `docs/LMTX_FORMAT_V1.md` as the canonical implementation specification, preserving all 14 normative sections of the owner-accepted issue #4 contract, the masked example and conformance plan without changing the schema.
- Included the explicit semantic JSON-value preservation clarification with sufficient numeric precision; original number spellings are not required, while specified binary payload byte preservation remains exact.
- Linked the accepted contract from the README, application specification and camera-module architecture. Issue #4 remains decision/review history; Android export #5 and Desktop import #1 remain open and unimplemented. No application, camera or thermometry code changed.

### Added (Android internationalization — 2026-10-03)
- Added complete English fallback and French translations for 87 product resource keys, including accessibility, common/module statuses/errors, Celsius controls and HT-301 debug permission/inventory prose.
- Added System/French/English selection with AppCompat 1.7.1 and AppCompatActivity, documented AndroidX auto-storage on API 26–32 and platform per-app language state on API 33+. AGP generates filtered LocaleConfig; release languages are English/French, debug also enables expanded/RTL pseudolocales.
- Localized legend/reading/input formatting, preserved round-trip Celsius bounds and introduced wrapping action rows for longer labels. Protocol/core, measurement values, coordinate mapping, palette tables and machine JSON are unchanged.
- Added resource/format/namespace parity checks with deliberate failure tests, Pixel mapping/fallback/formatting/platform/recreation/simulator coverage, synthetic both-orientation UI checks and actual AppCompat storage tests on simulated API 26/32. Test-only Robolectric/Compose UI/Espresso dependencies are excluded from production.
- Documented language persistence, locale-recreation camera release/explicit reopen, module translation boundaries and future language/module procedure in the localization guide and related product/configuration documents.
- Validated Pixel picker/force-stop/System and OS Settings agreement, physical HT-301 release/explicit raw14-unsettled reopen with zero initialization controls, and English/French/expanded/RTL layouts in both orientations. All 147 core JVM, eight simulated API 26/32 AppCompat and 22 Pixel tests pass; lint has zero errors, resource/boundary checks and diff check pass. Recorded sanitized evidence and all ten issue #2 criteria as completed, leaving issue/merge approval to review.

### Changed (Integrated camera-module foundation — 2026-10-03)
- Added transport-independent camera contracts, stable IDs/capabilities, pure none/unique/ambiguous registry selection, structured status/errors and one-session ownership with awaited replacement release and generation-bound publication.
- Adapted the targeted HT-301 controller behind module/session/resource bindings while retaining native transport, parsing, control sequence, shutter/liveness gates, thermometry arithmetic and richer raw/calibration/trailer evidence.
- Removed HT-301 geometry and raw-plane requirements from shared screen, coordinate mapping, cursor, Celsius renderer and presenter; added optional validity/provenance-qualified measurements and a generated 160×120 preview-only module with no camera controls.
- Moved shared visible labels into default-English Android resources and kept palette/module/status identities untranslated. Defined common/module namespaces for issue #2; full French/English selection and locale validation remain separate.
- Preserved machine diagnostic keys and added module/model/geometry/capability metadata. Documented deferred legacy file moves, future module validation requirements and multiple-model support versus excluded simultaneous acquisition.
- Added registry, alternate-geometry, ownership/cancellation/provenance/error, simulation/capability regressions, Android generic-module tests and a shared-boundary dependency check. Existing numerical/color fixtures are unchanged.
- Passed 147 JVM and nine Pixel instrumentation tests with exact numerical/color parity, debug/test builds, lint and diff checks. Preserved Pixel HT-301 evidence: explicit readiness in 6.271/6.354 s, usable Celsius/touch/palettes and conservative raw14 reopen without controls. Recorded one transient native range-transition burst with unresolved cause; it did not recur in the later accepted run.
- Completed the seven issue #1 acceptance criteria for review, preserving the existing work and accepted hardware evidence on resume. Issue #1 is not closed automatically; full localization issue #2 remains open.

### Added (Android Celsius presentation — 2026-10-03)
- Added JVM-testable Desktop-equivalent White hot, Black hot, Inferno, Iron-like/HOT and Turbo palettes with exact 2nd/98th percentile Auto range, centered minimum 1 °C span, finite exact Locked bounds and five-tick Celsius legend.
- Added native-coordinate tap/drag inspection and persistent selected pixel with current raw14/Celsius, plus a shared fitted-image/letterbox mapper for touch, cursor and high/low overlays. Native matrices and orientation are unchanged.
- Separated latest-request Celsius bitmap rendering from camera/thermometry work, publishing coherent measurement/color snapshots and clearing colors/legend/cursor readings when measurement becomes unavailable. No camera commands, session gates or LUT cache were added.
- Grouped the portrait/landscape live screen around image/readings/presentation controls, with optional diagnostics and existing debug-only control experiments.
- Added independent Desktop full-image color/percentile references, mapping/ownership/cursor regressions and Pixel runtime presentation/worker tests. Numerical fixtures remain test-only; no new runtime dependency was introduced.
- Validated all palettes, exact 25–45 °C lock, tap/drag/markers and stale-value clearing/recovery on Pixel 8 with HT-301; preserved numeric-only results. Sampled rendering median 24.952 ms, warmed thermometry 18.674 ms and callbacks near 25 FPS; 116 JVM and six Pixel instrumentation tests passed. Absolute physical accuracy remains unvalidated.

### Added (Android thermometry parity — 2026-10-03)
- Ported the validated width-384/range-120/lens-68/shutter-fix-1.5 native-equivalent arithmetic into plain Kotlin core, preserving Float/Double operation boundaries, trailer inputs and undefined LUT entries.
- Added owned raw14/matrix measurement evidence, separate trailer/literal center, numerical trace and a current-frame thermometry gate independent of structural session readiness.
- Added complete LUT goldens for six sanitized fixtures and two synthetic branches, full settled room/hand matrix references, intermediate/failure/ownership regressions and Android-runtime parity instrumentation.
- Added minimal worker-produced live numeric temperatures and coordinate markers with explicit physical-accuracy warning; invalid/unsettled/disconnected frames clear Celsius. No controls, timing, orientation or liveness rules changed.
- Confirmed all full LUTs/matrices bit-exact on Pixel 8 ART, then validated live hand/background response, coordinate markers and null measurement after close/unknown-raw14 reopen; recorded sampled per-frame evaluation timing without adding a cache.
- Documented golden provenance/hashes and reproducible export. Test-only AndroidX runner/JUnit dependencies and fixture assets do not ship in the product APK; ignored Kotlin compiler state and preserved numerical fixture bytes with binary Git attributes alongside the existing upstream/license whitespace rules.

### Fixed (Android frame-observed radiometric acceptance — 2026-10-02)
- Confirmed the existing 32772 DISPLAY→raw14 transition on Pixel 8 with a restricted one-shot diagnostic: exact two-byte SET, 15 receipt discards and two distinct valid raw14 images; no GET, range or shutter operation was used.
- Removed baseline-zero and post-write GET_CUR equality gates. The control interface now returns actual SET length/error and exposes no GET; frame evidence remains mandatory before subsequent commands and readiness.
- Preserved stage timings, bounded frame/discard rules, shutter/liveness gates, lifecycle cancellation, unknown-raw14 startup policy and executed Desktop shutter-trace parity. No thermometry/Celsius work was added.
- Added bounded debug-only numeric evidence persistence for deliberate hardware validation, excluding scene payloads and image hashes.
- Validated the full Pixel 8 sequence: exact SETs, 15/15 stage discards, 75 shutter frames and five live images reached structural RADIOMETRIC_READY in 6.312 s near 25 FPS; existing-raw14 reopen remained unsettled without automatic controls.

### Added (Zoom control semantics investigation — 2026-10-02)
- Added an explicit debug GET-only Zoom inventory with descriptor-derived IDs, exact transfer evidence and CURRENT observations around capability/range queries; no arbitrary selector or modifying control is exposed. The bounded control-only inventory is persisted privately to survive logcat rotation.
- Recorded query-dependent Android CURRENT replies: 1 after stream open, 3 after INFO, 65535 after MAX, and zero after zero-valued GETs. Fresh Linux V4L2/Desktop reads return zero; direct libusb access failed without driver detach. Session gates are unchanged and no Zoom SET was sent.
- Audited matching upstream Linux UVC source and traced one repeated V4L2 GET returning zero without a Zoom USB transaction. Direct USBFS submit was rejected with EBUSY while uvcvideo owned the interface; no detach was forced. A V4L2 SET/GET pair alone is not proof of a fresh firmware readback.

### Added (Android explicit radiometric session — 2026-10-02)
- Added a plain-JVM evidence-gated session with explicit initialization, fresh display baseline, exact zoom readbacks, transition discards, shutter settling and image-only liveness; pre-existing raw14 remains conservatively unsettled.
- Added restricted semantic controls on the existing UVC handle with exact two-byte transfers and bounded timeout, generation-bound cancellation and minimal Compose session/progress/retry presentation.
- Added scripted session/cancellation regressions and an executed Desktop shutter-trace oracle. Documented the deferred finite-LUT gate: Android readiness is structural/liveness only, with no Celsius or accuracy claim.
- Added structured numeric session diagnostics; removed automatic debug raw-frame saving. Acquisition payload and native coordinates remain unchanged.
- Recorded a real-device acceptance blocker: direct Zoom Absolute GET_CUR returns 1 after reconnect, while the unchanged Desktop-derived baseline requires zero. Initialization aborts before any write; hardware readiness remains unvalidated.

### Added (Android USB/UVC foundation — 2026-10-02)
- Added the native Kotlin/Compose Android product in this repository, with a plain JVM core and pinned Gradle wrapper/toolchain.
- Added Android camera/USB authorization, VID/PID-specific discovery, a read-only NDK libusb/libuvc transport with unconverted full-payload callbacks, and explicit close/reopen lifecycle handling.
- Ported Desktop's exact frame/image/trailer boundaries and display/raw14 candidate inspection without high-bit masking or thermometry. Preview normalization is independent and display-only.
- Added sanitized Desktop golden fixtures, ownership/classification tests and bounded latest-value tests; documented architecture, acquisition choices, dependency licenses and configuration.
- Added app-private debug payload evidence capture for verifying trailer preservation; unsanitized camera bytes are excluded from Git. Real-device acceptance is recorded in docs/ANDROID_VALIDATION.md.

### Fixed (Android foundation review — 2026-10-02)
- Kept attached device identity/authorization in diagnostics across normal stream close.
- Serialized frame publication with close/reset so an in-flight worker cannot restore a stale preview after release; added a concurrent regression.
- Selected the first valid exact debug payload for acquisition evidence, excluding size-correct mixed startup frames.

### Changed (Android product direction — 2026-10-02)
- Updated the README roadmap and specification to identify Android as the product and Desktop as the executable reference, diagnostic and fixture source.
- Deferred camera setting writes, radiometric readiness and Celsius/LUT porting to later focused milestones.

### Added (Desktop measurement foundation — 2026-09-29)
- Documented the reusable normal-range radiometric session, evidence-gated post-shutter readiness and read-only handling of raw14 streams with unknown host state. These are software acceptance rules built on the completed camera findings, not a new hardware protocol claim.
- Specified the separate OpenCV diagnostic aiming preview, display-only raw14 normalization and strict separation from native-equivalent thermometry. Independent physical accuracy validation remains outstanding.

### Added (Operator-confirmed hand discrimination — 2026-09-29)
- Documented 23 distinct live raw14 hand/background frames with an 8.596 °C mean ROI difference, consistent extrema coordinates, and the observed trailer/literal-center mismatch; no physical accuracy claim is made.
- Recorded a second 31-frame held image interval after the initial 75-frame post-shutter discard and one malformed frame, requiring liveness checks beyond a fixed settling count.

### Added (Shutter and matrix validation — 2026-09-29)
- Documented the roughly 1.3-second held-frame shutter interval, a setup-specific minimum 75-frame post-shutter discard, and a later 75-frame window of distinct raw14 images with spatially consistent trailer extrema.
- Recorded the experimental full temperature-matrix interface and the boundary between native arithmetic equivalence, relative scene discrimination, and independent physical accuracy.

### Added (Full radiometric initialization — 2026-09-27)
- Traced the ThermViewer ARMv7 UVC copy through the temperature callback and confirmed that both native searches reject full uint16 words >=0x4000 without pixel masking or flag stripping.
- Decoded all thermometry parameter byte commands and Handler delays; separated native host range/shutter/LUT state from device writes through standard UVC zoom absolute.
- Documented the observed `32772` raw14 transition and complete official replay, plus the separately reset ThermViewer type-0 replay that changed emissivity but kept display words.
- Documented the standalone desktop lookup and execution against the official x86_64 ELF, with matching full reference tables and center/high/low outputs. Independent physical accuracy remains unresolved.
- Added `docs/RADIOMETRIC_INITIALIZATION.md`, updated both APK comparisons and maintained original interrupted-session evidence without treating lost temporary reports as surviving artifacts.

### Corrected (Native interpretation — 2026-09-27)
- Distinguished byte 221186's FPA transform from byte 223490's `word/10-273.15` calibration input, correcting the former erroneous ~150 °C interpretation.
- Identified default native lens 68, its threefold stored-distance multiplier and its specific final correction branch.
- Identified ThermViewer's spot-0 center override, extra correction at byte 223514 and ten-float output in type 0; output type depends on `images_form_camera`.
- Corrected host-only refresh versus SDK device shutter naming, ThermViewer's float distance versus official uint16 distance, and stale hardware/specification/measurement-status statements. End-of-task workflow requirements remain intact.

### Added (Radiometric-mode comparison — 2026-09-26)
- Inventoried the newly supplied ThermViewer 2.0.23(ot) APK, its USB filters, ABIs, ARMv7 libraries, and distinct HT-301 UVC/thermometry path.
- Added `docs/APPLICATION_COMPARISON.md` with ordered startup controls, explicit evidence status, and a controlled Linux before/after experiment.
- Traced ThermViewer output type 0 to `zoom_absolute=32773`, output type 1 to `32772`, range commands to `32800/32801`, and shutter refresh to `32768` on its HT-301 path.
- Confirmed experimentally that `32773` alone did not turn current Linux `0x80YY` image words into 14-bit indices; the full app path remains unverified.

### Added (Native thermometry audit — 2026-09-26)
- Inventoried the only available HT-301 APK, five ABI sets of native libraries, decompiled Java temperature bridge, and 20 legacy scripts without changing research originals.
- Traced the Android path through `thermometryT4Line` and `thermometrySearch`, including a 16,384-entry lookup, 384 × 288 pixel array, trailer summary readings, and Java center/high/low positions.
- Reconstructed `CalcFixRaw` normal-path arithmetic and mapped its five inputs to the app's ambient, humidity, distance, emissivity, and reflected-temperature settings.
- Added `docs/NATIVE_CALL_CHAIN.md` and `docs/RESEARCH_INVENTORY.md` with binary hashes, evidence, and remaining initialization questions.

### Corrected (Native thermometry audit — 2026-09-26)
- Identified block fields 352–371 as copies of five preceding trailer calibration floats; field 356 is a calibration coefficient copy, while the app maps a live center index at frame byte 221208 through its lookup.
- Corrected parameter labels: offsets 4/8/12/16/20 are reflected temperature, ambient temperature, humidity, emissivity, and uint16 distance.
- Corrected the historical `CalcFixRaw` expression: its second exponential has a negative ~0.9 multiplier, not 100.0.
- Found that the saved V4L2 image words are `0x8000 + Y` and exceed the native 14-bit lookup range; a visible image does not establish radiometric input or validated Celsius measurements.

### Changed
- Added persistent agent workflow instructions requiring documentation/changelog maintenance and pushing completed task branches to GitHub for review.

### Corrected (Measurement audit — 2026-09-26)
- Confirmed that only the first 288 rows of the 292-row transport are thermal image; 2558 non-image bytes precede the 514-byte parameter block.
- Reclassified parameter field 356 as an unverified center-temperature candidate after it stayed constant across changing live image data.
- Rejected the desktop prototype's `gain × emissivity` thermometry input mapping; it gave −55.10 °C at one center while field 356 was 35.992.
- Corrected the historical claim that the P2 Pro `uint16/64 - 273.15` conversion was validated on HT-301.

Historical discoveries below record earlier conclusions; the corrections above supersede contradictory temperature and initialization claims.

### Added
- Project repository created
- README with project plan (6 phases)
- `docs/HARDWARE.md` — hardware technical documentation (USB IDs, video stream, vendor commands)
- `docs/SPECIFICATION.md` — application feature specification
- `REPO_RULES.md` — repository contribution rules
- `CHANGELOG.md` — this file

### Discovered
- YUYV stream accessible via OpenCV (`CAP_PROP_CONVERT_RGB=0` + `CAP_V4L2` backend)
- Frame shape: (292, 384, 2) uint8 — but appears as sensor noise without initialization
- P2 Pro formula (`uint16/64 - 273.15`) gives unrealistic values on HT-301 (not the same camera)
- Vendor USB writes accepted (2-4 bytes) but reads always timeout (protocol differs from P2 Pro)
- Reference project: [ftobler/infiray_p2_pro_python](https://github.com/ftobler/infiray_p2_pro_python)
- **Key finding**: HT-301 requires vendor command initialization sequence before valid thermal output
- **Blocker**: need USB traffic capture from Android app to discover initialization commands
- **Breakthrough**: decompiled APK (HT-301ThermCameraViewerX V6.4) — temperature is in video stream
- `libthermometry.so` converts raw pixels to °C via `thermometryT()` (uses exp/pow/sqrt)
- Temperature thread processes frames: `startTemp()` → `temperature_thread_func` → `do_temperature_callback`
- Calibration: `GetTempEvn()`, `InitTempParam()`, `CalcFixRaw()`
- Java conversion: `short = celsius * 10.0 + 2731.0`
- Created `docs/APK_ANALYSIS.md` with full architecture analysis

### Discovered (Session 2 — 2026-04-18)
- **Frame structure decoded**: last 514 bytes of each frame contain temperature parameters
- **Parameters**: env_temp (25°C), emissivity (0.45), distance_factor (0.98), gain, center temperature
- **Center temperature**: pre-calculated by camera firmware, available at offset 356 in params
- **GetTempEvn() fully decoded**: uses Stefan-Boltzmann law (T⁴ / ⁴√T) with env_temp correction
  - Formula: `T = pow(b * (pow(raw + 273.15, 4.0) - env_temp), 0.25) - 273.15`
- **InitTempParam() decoded**: computes calibration `a = y/(2x)`, `b = (y/2x)²`
- **CalcFixRaw() partially decoded**: cubic polynomial → exp(), then sqrt/exp correction chain
  - Uses polynomial: `P(t) = 1.5587 + 0.06939t - 0.000278t² + 6.86e-7t³`
- **All .rodata constants extracted**: 27 constants decoded (float32 and float64)
- **Empirical calibration validated**: face ~35°C, wall ~20°C (room at 20°C)

### Added (Session 2 — 2026-04-18)
- `docs/THERMOMETRY_LIB.md` — complete reverse engineering documentation of libthermometry.so
- `prototype/thermal_capture.py` — Python prototype with frame capture, param extraction, temp calculation
- Live view mode with temperature overlay and click-to-measure
