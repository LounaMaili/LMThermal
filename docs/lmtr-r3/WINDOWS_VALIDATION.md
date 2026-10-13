# Native Windows R3 gate — NOT EXECUTED

No authorized native Windows executor was available in this session. This is a
prepared camera-free protocol, **not a Windows result**. PowerShell/NTFS calls have
not executed here; Fedora and WSL do not establish their runtime correctness.
Use the exact pushed PR #14 commit, JDK 17, Python 3 and a native local checkout.
No camera, ADB, credentials, Android SDK/NDK or application installation is needed.

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
No native Windows execution has yet confirmed this adapter or PowerShell runner.

## Separate acceptance boundaries

See [HARDENING_REVIEW](HARDENING_REVIEW.md): native Windows is a remaining mandatory
R3 platform gate. ART was unavailable and remains pre-R4 compatibility work, not
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
This prepared workflow alone is not a Windows pass. Actual run evidence follows
only after execution, without regenerating the51pinned fixtures.
