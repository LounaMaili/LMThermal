# AGENTS.md — LMThermal

## Repository role

This repository is the source of truth for the Infiray HT-301 / T3-317-13 reverse engineering, hardware behavior, thermometry research, protocol notes, and application specification.

The desktop application lives in the sibling repository `LMThermal-Desktop` when both repositories are available in the same workspace.

## Mandatory first reads

Before changing anything, read:

1. `REPO_RULES.md`
2. `README.md`
3. the relevant files under `docs/`
4. `CHANGELOG.md`

For thermometry work, always inspect at least:

- `docs/HARDWARE.md`
- `docs/APK_ANALYSIS.md`
- `docs/THERMOMETRY_LIB.md`
- `docs/SPECIFICATION.md`

## Repository rules

`REPO_RULES.md` is authoritative.

In particular:

- code comments, documentation, and commit messages are in English;
- document meaningful discoveries and behavior changes;
- update `CHANGELOG.md` for meaningful changes;
- do not commit credentials, tokens, secrets, or machine-specific private data;
- keep reverse-engineering facts distinguishable from hypotheses and approximations.

## Known camera facts

Target camera:

- Infiray HT-301 / T3-317-13
- USB VID: `1514`
- USB PID: `0001`
- UVC / `uvcvideo`
- YUYV 4:2:2
- 384 x 292
- 25 fps
- frame size: 224256 bytes

Known frame structure:

- thermal image occupies rows 0–287 (384 × 288 pixels);
- rows 288–291 are non-image trailer;
- parameter block starts at byte `223742`;
- parameter block size is `514` bytes;
- field at parameter offset `356` copies a calibration coefficient from byte `223498`; the app's live center index is elsewhere in the trailer.

The parameter block occupies only part of the four-row trailer. Do not treat
any trailer bytes as valid thermal image pixels in statistics or extrema.

## Thermometry status

The embedded parameter parser is producing plausible values from real hardware.

The complete per-pixel thermometry chain is **not yet accuracy-validated**.
A clean `zoom_absolute=32772` transition yields 14-bit indices; the standalone
desktop lookup matches executed official x86_64 arithmetic for the tested
range-120/lens-68 branch. Read `docs/RADIOMETRIC_INITIALIZATION.md` before
new hardware or thermometry changes. Independent accuracy remains unresolved.

Known reverse-engineered components include:

- `GetTempEvn`
- `InitTempParam`
- the normal-path arithmetic and caller input mapping of `CalcFixRaw`
- constants extracted from `libthermometry.so`
- the `thermometryT4Line`/`thermometrySearch` lookup path

The current Python approximation used by the desktop prototype must not be documented as validated thermometry.

Do not introduce arbitrary scale or offset values merely to make calculated values match one observed temperature.

Field 356 stayed constant across changing captured images and is a duplicated
calibration coefficient. Do not use it as a center-temperature regression
target. The native app instead maps a 16-bit center index at frame byte
221208 through its lookup. See `docs/NATIVE_CALL_CHAIN.md`, `docs/HARDWARE.md`,
and the desktop repository's `docs/MEASUREMENT_AUDIT.md`.

## Reverse-engineering workflow

For each new thermometry or protocol claim:

1. state the hypothesis;
2. identify the evidence from disassembly, APK behavior, frame data, or hardware tests;
3. reproduce the observation when practical;
4. distinguish confirmed facts from inferred behavior;
5. document unresolved uncertainty;
6. update the relevant documentation and changelog.

Prefer evidence and reproducible diagnostics over curve fitting.

## Cross-repository work

When `LMThermal-Desktop` is available as a sibling repository:

- use this repository for protocol, hardware, thermometry, and specification knowledge;
- use `LMThermal-Desktop` for production desktop code and tests;
- keep Git histories independent;
- commit changes separately in each repository;
- do not copy large documentation blocks between repositories when a reference is sufficient.

## Scope discipline

Avoid unrelated refactors while performing reverse engineering.

For substantial work, use focused branches following `REPO_RULES.md`, for example:

- `proto/thermometry-chain`
- `docs/thermometry-findings`
- `fix/frame-layout-docs`


## End-of-task repository sync

At the end of every completed task, after relevant checks pass:

1. review the final diff and remove accidental or unrelated changes;
2. update every documentation file affected by confirmed findings or behavior changes;
3. update `CHANGELOG.md` for every meaningful discovery, correction, protocol/thermometry finding, documentation change, or prototype behavior change;
4. commit the complete task on its focused working branch with a descriptive English commit message;
5. push that working branch to GitHub so remote reviewers and other agents can inspect the exact result.

Do not leave completed work only in the local working tree unless the user explicitly asks for that.

Do not merge into `main` automatically. Push the working branch and leave merge or pull-request approval to the user/reviewer.

If the task also changes production desktop code, make the corresponding `LMThermal-Desktop` changes in that repository's own branch, commit, and push. Keep both Git histories independent.

Before reporting a task as complete, include:

- branch name;
- commit SHA(s);
- whether the branch was pushed successfully;
- checks/tests/diagnostics run and their result;
- documentation/changelog files updated;
- remaining uncertainty or follow-up work.
