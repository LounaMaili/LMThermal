# Changelog

All notable changes to LMThermal will be documented in this file.

## [Unreleased]

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
