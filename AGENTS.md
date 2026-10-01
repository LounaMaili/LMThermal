# AGENTS.md — LMThermal

## Repository role

This repository is the **primary product repository for the LMThermal Android application** for the Infiray HT-301 / T3-317-13 thermal camera.

It also contains the source-of-truth documentation for the camera protocol, reverse-engineered hardware behavior, thermometry research, and application specification. Those validated facts must be preserved when implementing the Android product.

The sibling repository `LMThermal-Desktop` is the executable desktop reference/diagnostic implementation. It is used to validate behavior, generate fixtures, compare results, and provide a readable reference implementation. It is **not** the final product target.

When both repositories are available:

- use `LMThermal` for the Android product and authoritative protocol/research documentation;
- use `LMThermal-Desktop` as the behavioral reference and diagnostic implementation;
- do not re-invent camera protocol or thermometry behavior that has already been validated in either repository.

## Mandatory first reads

Before changing anything, read:

1. `REPO_RULES.md`
2. `README.md`
3. this `AGENTS.md`
4. the relevant files under `docs/`
5. `CHANGELOG.md`

For camera, radiometric-session, or thermometry work, always inspect at least:

- `docs/HARDWARE.md`
- `docs/APK_ANALYSIS.md`
- `docs/THERMOMETRY_LIB.md`
- `docs/SPECIFICATION.md`
- `docs/RADIOMETRIC_INITIALIZATION.md`
- `docs/NATIVE_CALL_CHAIN.md`

When porting behavior that already exists in `LMThermal-Desktop`, inspect the corresponding Desktop implementation and tests before writing a new Android interpretation.

## Repository rules

`REPO_RULES.md` is authoritative.

In particular:

- code comments, KDoc, documentation, and commit messages are in English;
- document meaningful discoveries and behavior changes;
- update `CHANGELOG.md` for meaningful changes;
- do not commit credentials, tokens, secrets, or machine-specific private data;
- keep reverse-engineering facts distinguishable from hypotheses and approximations;
- do not silently simplify validated camera behavior merely because it looks unusual;
- do not merge feature branches into `main` automatically.

## Android product direction

The Android application is the target product.

Prefer:

- Kotlin;
- normal Android/AndroidX architecture;
- lifecycle-aware components;
- coroutines/Flow where appropriate;
- clear separation between USB/UVC transport, frame parsing, radiometric session logic, thermometry, measurements, persistence, and UI.

Do not embed Python into the Android application.

Do not attempt to run the PyQt/Desktop application on Android.

Port validated algorithms deliberately and verify parity with Desktop fixtures/reference outputs.

Opening the camera must initially be read-only. Do not automatically send radiometric initialization controls when the USB device is discovered or when the application launches. Radiometric initialization must remain an explicit user action.

Native thermal data remains 384 x 288 regardless of phone orientation. Presentation rotation/mirroring must not mutate native measurement coordinates or stored matrices.

USB acquisition, frame parsing, thermometry, compression, and other potentially blocking work must not run on the Android UI thread.

Use bounded/latest-frame handoff patterns. Never allow an unbounded frame queue to grow just to preserve presentation frames.

## Android test device and wireless ADB

The primary Android hardware test device has the fixed LAN IP:

`10.0.1.9`

Wireless ADB pairing has already been completed successfully.

The phone has one physical USB port. During HT-301 testing that port is occupied by the camera in USB Host/OTG mode, so installation, shell access, log collection, and debugging must use **wireless ADB**.

Do not require simultaneous wired ADB while the HT-301 is connected.

### Normal ADB workflow

Before any Android hardware test, run:

```bash
adb start-server
adb devices -l
```

If the device is already listed with state `device`, use it directly.

The paired device may appear through mDNS with a serial similar to:

`adb-38041FDJH00RFY-x7WMXz._adb-tls-connect._tcp`

That is a valid ADB target.

If no usable device appears, discover the current wireless-debugging endpoint:

```bash
adb mdns services
```

Find the `_adb-tls-connect._tcp` service for the device at `10.0.1.9`, then connect using its currently advertised port:

```bash
adb connect 10.0.1.9:<advertised-port>
adb devices -l
```

Do **not** hard-code the ADB TCP port. Android may change it.

Do **not** run `adb pair` again merely because:

- the development PC restarted;
- the ADB daemon restarted;
- the wireless-debugging TCP port changed.

Only request operator intervention when:

- Wireless Debugging is disabled;
- the existing pairing was genuinely removed/forgotten;
- Android requests a new manual authorization;
- the device cannot be discovered/reached over the network;
- the HT-301 must physically be connected/disconnected/replugged;
- Android USB permission requires user interaction.

Before asking the operator for a port or pairing code, attempt normal ADB discovery yourself.

### Hardware-validation rule

Before claiming a hardware-dependent Android task is validated:

1. confirm the Android device is available through ADB;
2. build/install the current application;
3. collect relevant logs when needed;
4. perform the requested real-device HT-301 test.

Do not substitute emulator-only validation for a requested real-device test.

## Known camera facts

Target camera:

- Infiray HT-301 / T3-317-13
- USB VID: `1514`
- USB PID: `0001`
- UVC
- YUYV 4:2:2
- 384 x 292
- 25 fps
- bytes per line: 768
- exact transport-frame size: 224256 bytes

Known frame structure:

- thermal image occupies rows 0-287 (384 x 288 pixels);
- rows 288-291 are non-image trailer;
- image payload is the first 221184 bytes;
- parameter block starts at byte `223742`;
- parameter block size is `514` bytes;
- field at parameter offset `356` copies a calibration coefficient from byte `223498`; it is not the live center temperature.

The parameter block occupies only part of the four-row trailer. Never treat trailer bytes as thermal image pixels in statistics, extrema, palette scaling, ROI calculations, or measurements.

Native sensor coordinates are always:

- x = 0..383
- y = 0..287

Do not rotate or mirror source matrices to match phone orientation. Any visual transform belongs in the presentation layer and must reverse-map interactions back to native coordinates.

### Raw/display semantics

Known display-mode image words use the `0x80YY` form.

True raw14 thermometry indices are below `0x4000`.

Do not mask high bits to manufacture raw14 values. Frames containing invalid/mixed words must be handled according to the validated frame-inspection/session rules.

## Thermometry status

The validated Desktop/reference path reconstructs the official normal-range native-equivalent thermometry chain for the tested HT-301 behavior.

Independent absolute physical accuracy remains unresolved.

Continue to describe exposed values as:

`Native-equivalent temperatures; absolute physical accuracy not yet independently validated.`

Known reverse-engineered components include:

- `GetTempEvn`
- `InitTempParam`
- the normal-path arithmetic and caller input mapping of `CalcFixRaw`
- constants extracted from `libthermometry.so`
- the `thermometryT4Line` / `thermometrySearch` lookup path

Do not introduce arbitrary scale or offset values merely to make calculated values match one observed temperature.

Field 356 is a duplicated calibration coefficient. Do not use it as a center-temperature regression target. The native app maps a trailer raw index elsewhere in the frame.

When porting thermometry to Kotlin, preserve validated float/lookup behavior deliberately and compare against Desktop/golden fixtures rather than rewriting formulas from memory.

## Validated radiometric initialization behavior

The validated normal-range control sequence includes the values:

- `32772`
- `32800`
- `32768`

These values must not be scattered through implementation code as unexplained literals.

Use descriptive constants and document their role in the evidence-gated initialization sequence.

Do not automatically send them simply because the camera was opened.

Read the current Desktop session implementation and `docs/RADIOMETRIC_INITIALIZATION.md` before changing this sequence, timing, readback validation, or readiness logic.

## Code readability and human maintainability

The codebase must remain understandable to a human developer who did not participate in the original reverse engineering.

Prefer clarity over cleverness.

### Comments and KDoc

Add meaningful comments or KDoc when code encodes:

- non-obvious HT-301 protocol behavior or camera-specific invariants;
- binary offsets, trailer fields, UVC controls, timing requirements, or state transitions;
- measurement-validity and frame-rejection rules;
- threading, synchronization, buffering, coroutines, or Android lifecycle decisions;
- coordinate transforms or native-vs-presentation semantics;
- thermometry formulas and floating-point behavior that must match validated reference behavior;
- Android/USB/UVC/device-specific workarounds whose rationale would otherwise be lost.

Comments should explain **why** a behavior exists, what evidence/invariant it protects, and what must not be simplified casually.

Do not comment obvious Kotlin syntax or trivial getters/setters.

Important public or architectural classes/interfaces should use KDoc to document:

- responsibility;
- important invariants;
- coordinate/data semantics;
- ownership/lifetime where relevant;
- expected failure states.

Do not add verbose KDoc merely to satisfy a documentation quota.

### Named constants instead of magic numbers

Do not scatter unexplained camera-specific numbers through implementation code.

Frame dimensions, byte offsets, control values, trailer locations, state thresholds, and timing values should use descriptive constants.

When a value is reverse-engineered or non-obvious, include a short rationale/source comment or reference the relevant documentation.

For example, the validated control values `32772`, `32800`, and `32768` must have semantic constant names and documentation explaining their role.

### Structure and naming

Prefer descriptive identifiers over abbreviations.

Keep functions reasonably focused.

If a function mixes several responsibilities such as:

- USB I/O;
- frame parsing;
- radiometric session decisions;
- thermometry;
- persistence;
- rendering;

split those responsibilities rather than compensating with a large comment block.

Avoid deeply nested control flow when early returns or dedicated functions improve readability.

### Reverse-engineered behavior

Whenever Android code implements behavior derived from validated LMThermal research or the Desktop reference implementation, leave enough context for a future maintainer to understand that the behavior is intentional.

Where useful, reference the corresponding documentation or Desktop component without copying large research notes into source code.

Do not silently "clean up" unusual behavior without first reproducing and understanding the evidence that established it.

### Tests

Test names should describe the behavior or invariant being protected.

Prefer names such as:

`raw14FrameWithHighBitsSetIsRejected`

over vague names such as:

`testFrame2`

Important regression tests may contain a short comment explaining the historical/device behavior they protect.

A future developer should be able to understand:

- what a component does;
- why unusual camera-specific behavior exists;
- which assumptions are validated facts;
- which points remain uncertain;
- which behavior is presentation-only;
- which code must not be changed casually;

without having to reverse engineer the camera again.

## Reverse-engineering workflow

For each new thermometry or protocol claim:

1. state the hypothesis;
2. identify evidence from disassembly, APK behavior, frame data, or hardware tests;
3. reproduce the observation when practical;
4. distinguish confirmed facts from inferred behavior;
5. document unresolved uncertainty;
6. update relevant documentation and `CHANGELOG.md`.

Prefer evidence and reproducible diagnostics over curve fitting.

Android implementation work that merely ports already validated behavior is not a new reverse-engineering discovery. Do not rewrite research documentation just because the platform implementation changed.

## Cross-repository work

When `LMThermal-Desktop` is available as a sibling repository:

- use `LMThermal` for the Android product and authoritative protocol/research documentation;
- use `LMThermal-Desktop` as the executable reference implementation, fixture source, diagnostics environment, and parity oracle;
- keep Git histories independent;
- commit changes separately in each repository;
- do not duplicate large documentation blocks when a reference is sufficient;
- do not add new Desktop-only features unless they are needed to validate or support the Android product.

## Scope discipline

The mobile application has priority.

Avoid unrelated Desktop UX work and unrelated reverse-engineering refactors while implementing Android functionality.

For substantial Android work, use focused branches, for example:

- `feat/android-usb-uvc-foundation`
- `feat/android-radiometric-session`
- `feat/android-thermometry`
- `fix/android-usb-lifecycle`

## End-of-task repository sync

At the end of every completed task, after relevant checks pass:

1. review the final diff and remove accidental/unrelated changes;
2. update documentation affected by the change;
3. update `CHANGELOG.md` for meaningful code, behavior, architecture, dependency, hardware, protocol, or thermometry changes;
4. run relevant JVM/Android tests, build, lint/static checks, and hardware diagnostics when practical;
5. commit the complete task on its focused working branch with a descriptive English commit message;
6. push that working branch to GitHub.

Do not leave completed work only in the local working tree unless the user explicitly asks for that.

Do not merge into `main` automatically. Push the feature/fix/prototype branch and leave merge approval to the user/reviewer.

If a task establishes genuinely new hardware, protocol, calibration, or thermometry knowledge, update the corresponding source-of-truth documentation in this repository in the same focused work or a clearly separated commit.

Before reporting a task as complete, include:

- branch name;
- commit SHA(s);
- whether the branch was pushed successfully;
- tests/build/lint/diagnostics run and their result;
- real-device validation performed, when applicable;
- documentation/changelog files updated;
- remaining uncertainty or follow-up work.
