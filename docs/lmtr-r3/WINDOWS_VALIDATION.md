# Native Windows R3 gate — PASSED on GitHub Actions

The native `windows-2022` gate passed on
[run 38392229227](https://github.com/LounaMaili/LMThermal/actions/runs/38392229227),
exact commit `9770e5a228a56884221103f7eae4b52297b8314e`. The
[durable receipt](windows-validation-summary.json) binds runtime identities, counts,
source/corpus hashes, raw report fingerprints and the
[artifact](https://github.com/LounaMaili/LMThermal/actions/runs/38392229227/artifacts/11809392539).
This is native Windows Server2022/NTFS, not WSL; Temurin17.0.20.1+1,
Python3.13.16 and zlib1.3.1.71 JVM/64 Python tests and all51 cases per reader pass.
The 4,404,323,163-byte sparse file occupies4,915,200 physical bytes; selected access
reads one CHUNK at4,404,298,782, and Python fully verifies all71 CHUNKs.
Exact source SHA/size/mtime/allocation and the original corpus manifest stay unchanged.

The first run 38391877487 failed in PowerShell preflight before any LMTR tests,
with a null-method exception. Empty native Git output handling was hardened with
explicit arrays/join; subsequent detached exact-SHA execution succeeds. Failed
logs/artifact and original passing reports remain preserved. Four focused negative
preflight guards are added to subsequent CI to retain strict SHA/ref/clean-source
behavior. No LMTR implementation defect, wire change or fixture repinning was needed.

Earlier NOT EXECUTED statements in historical reports describe the prior Fedora
checkpoint. This receipt supersedes that native-platform gate, not their original
results. Generic `interop.json` has an old static pending checklist; its placeholder
is not an execution classifier. Actual completion comes from the native workflow,
NTFS report and linked combined receipt. ART stays NOT EXECUTED before production.

The commands below reproduce the protocol with the exact pushed PR14 commit,
JDK17 and Python3. No camera, ADB, credentials, Android SDK/NDK or app install.

## One PowerShell command

Checkout `feat/lmtr-r3-contract-conformance` at the exact final SHA reported by the
review checkpoint, keep it clean, set `JAVA_HOME` to JDK 17, then run from its root:

```powershell
.\tools\validate_lmtr_r3_windows.ps1 -ExpectedCommit '<exact-40-character-PR-head-SHA>'
```

If execution policy disallows running local scripts, review the file and use a
process-only override, without changing the machine policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\validate_lmtr_r3_windows.ps1 -ExpectedCommit '<exact-40-character-PR-head-SHA>'
```

Add `-Offline` only with already-cached Gradle dependencies. Reports/logs use a unique
owned directory under `%TEMP%`, outside the pinned checkout. Retain it, including
any failed-command log. `-ReportDirectory 'D:\R3-evidence\unique-run'` chooses another
new local destination. No Git push, fixture regeneration or remote access occurs.

The runner verifies native Windows, exact branch/HEAD, clean sources, JDK version
and the unchanged corpus manifest; uses SDK-independent Gradle settings in
`tools/lmtr-r3-host`; executes JVM tests, all independent Python tests, both directions
of conformance, mixed-corruption cases and cancellation probes; records XML counts,
Windows/filesystem/JDK/Python/zlib identities and per-command results. It checks
exit status explicitly. It then verifies clean sources and unchanged manifest.
Do not substitute self-read or previous R2/legacy Windows evidence for this gate.

Exact manifest SHA-256:
`29b262a1f831b119c5094dd7385869ea03fdd44b0947263922ead0bd77f660e3`.
All 51 files (33 positive / 18 negative-recovery) keep their original hashes.
Interoperation checks each original source SHA before/after read, 33 Kotlin-written
positives independently fully read by Python, logical parity and canonical JSON.
Independent compressors may produce different valid compressed bytes; do not repin
fixtures to conceal differences. Original generation-provenance `--check` is separately
run from its pinned Linux/source runtime, not silently regenerated with Windows zlib.

## Optional native NTFS >4 GiB gate

```powershell
.\tools\validate_lmtr_r3_windows.ps1 -ExpectedCommit '<exact-40-character-PR-head-SHA>' -NtfsSparse -SparseTempRoot 'D:\R3-temp'
```

Use an existing local **NTFS** temp directory. The Python adapter
`tools/check_lmtr_ntfs.py` checks native Windows/local NTFS, marks only its newly
created temporary file sparse (`FSCTL_SET_SPARSE`), and creates the same contiguous
hash-bound 71-CHUNK logical zero source as the Fedora stress. It refuses non-NTFS or
remote volumes; no administrator access or existing source modification is requested.
The file is about 4.4 GB logically, with an asserted <16 MiB physical allocation.
There are no invalid gaps between records: sparse zeroes belong to real STORED
payloads. SHA/full read traverses logical bytes and can take a few minutes.
The adapter removes its owned temporary file on exit, retaining only its report.

The runner requests one selected CHUNK beyond 4 GiB in both runtimes plus Python
full 71-CHUNK integrity, logical hash/summary parity and source size/mtime/allocation
immutability. SHA is streamed in 1 MiB pages. This is seek/integrity evidence, not a
physical disk-write throughput or general heap ceiling. If sparse APIs fail, retain
the failure; do not fall back to allocating 4.4 GB or label WSL as NTFS evidence.
Native run 38392229227 confirms the sparse adapter and protocol on its exact commit.

## Separate acceptance boundaries

See [HARDENING_REVIEW](HARDENING_REVIEW.md): native Windows/NTFS passed the remaining mandatory
R3 platform gate in the finite tested scope. ART was unavailable and remains pre-R4 compatibility work, not
JVM evidence. Arbitrary damaged-framing salvage and disposable index cache are
optional. Async producer timers/Stop/admission and backend/export durability are
R4/R6 requirements; no green host suite establishes them. Keep #8 open and #14 draft.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## GitHub-hosted Windows execution

The branch-restricted push workflow `.github/workflows/lmtr-r3-windows.yml` uses
native `windows-2022`, Temurin17 and Python3.13. Checkout and `-ExpectedCommit` bind
the exact push SHA. A detached checkout is accepted only with matching Actions
R3 branch/ref/SHA context; source cleanliness remains mandatory. Token permissions
are `contents: read`; no repository secrets, camera, Android SDK or owner interaction.
Reports use unique `RUNNER_TEMP` directories, NTFS sparse/full read is enabled, and
logs/XML/reports upload even on failure with30-day retention. Job/step deadlines
and build/runtime resource caps are bounded; earlier failed runs must be retained.
Workflow existence alone is not a pass: actual execution is linked above, without
regenerating the51pinned fixtures. Four preflight guards separately reject wrong
HEAD, wrong detached ref/context SHA and dirty source; their output is retained
with subsequent CI artifacts.

## Follow-up regression workflow

The focused CI follow-up enables main pushes and PR merge candidates for contract/tool
changes, retaining read-only permissions, pinned actions and artifacts. Detached source
requires the exact event SHA, an allowed push ref or `pull_request` merge ref, and a clean
checkout. `pull_request_target`, mismatched SHA/ref and local detached contexts reject.
Named local branches may validate their exact explicitly requested SHA. This changes
CI scheduling/preflight only; the accepted R3 run remains 38393731476 on fb857b6.
