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

- parameter block starts at byte `223742`;
- parameter block size is `514` bytes;
- camera-calculated center temperature is stored at parameter offset `356`.

The parameter block occupies the end of the nominal YUYV byte buffer. Do not treat those bytes as valid thermal image pixels in image statistics or extrema.

## Thermometry status

The embedded parameter parser is producing plausible values from real hardware.

The complete per-pixel thermometry chain is **not yet validated**.

Known reverse-engineered components include:

- `GetTempEvn`
- `InitTempParam`
- a partial understanding of `CalcFixRaw`
- constants extracted from `libthermometry.so`

The current Python approximation used by the desktop prototype must not be documented as validated thermometry.

Do not introduce arbitrary scale or offset values merely to make calculated values match one observed temperature.

The firmware-provided center temperature is a useful regression reference, but first establish experimentally whether it corresponds to one pixel, a region average, or another internal calculation.

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
