# Real Windows R2 validation

**Native Windows packet interoperability passed on 2026-10-11. R2 is ready for completion review.**
All packets are noncanonical R2 synthetic artifacts, not accepted LMTR v1 or R3
release fixtures. This result does not approve the R1b wire contract or a product
recording configuration. R2 #7 remains open for review; R1 #6 remains open for its
separate owner decisions.

## Recorded execution

The clean checkout was on `test/recording-r2-feasibility`, at
`736aba08fa78d070d0a31b29826b7cd4b63fe2ec`, with that expected revision verified in
branch history before testing. The host ran native Windows 11 Pro 25H2, build
26200.9550, AMD64/x64, with Python 3.12.10 (64-bit) and zlib 1.3.1. Python reported
`win32`/`nt`; no WSL environment was present. No Android/Linux tests were rerun.

The original ZIP was hashed before extraction into a fresh ignored local directory.
Its SHA-256 before extraction and after validation was:
`49cc244c60ca51b71c4d1ee38b4360e7e4bc8f490216ad4887f0fd1b770117cc`.
The archive remains 678,802 bytes and was not regenerated or replaced. All 15
member hashes match the packet manifest and the final bounded-chunk candidate
`531b8fd0653d503309f5def0899380a939b6f6d6`'s
[published proof](reports/continuation-final-packet.json).

The sanitized [Windows report](reports/windows-packet.json) contains the environment,
validated revision, commands, full validator outputs, member hashes, comparison
assertions and caveats. Both executions exited 0 with no unexpected failure.

| Execution | Complete | Missing-END recovered | Expected corruption rejected | Skipped | Expected outcomes passed |
|---|---:|---:|---:|---:|---:|
| Mandatory STORED/DEFLATE baseline | 6 | 2 | 2 | 5 Zstd | 10/10 |
| Separate optional all-codec run | 9 | 3 | 3 | 0 | 15/15 |

The baseline run explicitly skipped all five `zstd-3` cases and loaded no Zstd
library. Its success is not a Zstd result. The separate optional run used an
already installed Codex bundled native Poppler runtime (`poppler=26.07.0=h6618ce5_3`,
win32/x64), whose `zstd.dll` reported reference version 1.5.7. No library was
downloaded or installed for this task. DLL SHA-256:
`28f7a0576cb58377eb799581cb539d9a8d729408edee1b474866de77c72c4bea`.
This optional parity pass does not change the deferred Zstd production recommendation.

Each executed case row equals both the [original Linux report](reports/linux-packet.json)
and the Linux section of the [final candidate report](reports/continuation-final-packet.json),
including expected success/failure, source/logical hashes, frame/gap/chunk counts,
recovery state, integrity error and lazy-open/validation read counts. Independent
synthetic checks pass exact Float32 values, validity masks, native samples,
acquisition transport, HT-301 restricted native views and context metadata across
Analysis/Native/Full. Each readable case contains five frames, one gap and one
chunk, and lazy seeking succeeds. Every missing-END case recovers all five frames
and its gap while remaining explicitly incomplete (`missing_or_torn_end`). Every
committed-corruption case rejects with `committed_payload_integrity`. No Windows/Linux
compatibility difference was observed for this packet.

Before/after SHA-256 checks cover the unchanged archive and all 16 extracted files
(the manifest plus 15 members), including the baseline's skipped Zstd sources.
No reader rewrote a recovered or corrupt source. Historical Linux/Android reports,
private recordings and failed live evidence are retained unchanged.

## Reproduce the mandatory baseline

Use native Windows and Python 3.11 or newer. Inspect local work before checkout;
do not reset/delete unrelated files. Fetch the existing R2 branch and verify the
expected revision, clean working tree and archive hash before extraction. Use a
fresh destination. In PowerShell, from the repository root:

```powershell
git branch --show-current
git rev-parse HEAD
git merge-base --is-ancestor 736aba08fa78d070d0a31b29826b7cd4b63fe2ec HEAD
git status --short
Get-FileHash -Algorithm SHA256 docs/recording-r2/artifacts/android-r2-packet.zip
Expand-Archive -Path docs/recording-r2/artifacts/android-r2-packet.zip -DestinationPath r2-packet-local
py -3 tools/recording_r2/validate_packet.py r2-packet-local/packet --baseline-only --output r2-windows-baseline.json
```

Baseline-only validates STORED/DEFLATE complete Analysis/Native/Full, exact synthetic
Float32/mask/native/transport/context/gap bytes, HT Full restricted native views,
missing-END recovery, committed corruption errors, lazy seeks and source SHA-256
immutability. No camera/Android/GUI is needed. Do not edit the source members.
Record Windows/Python details and the produced JSON; never report a Zstd pass from
baseline-only execution.

## Optional Zstd parity

If a trusted reference library matching Python's architecture is available, run
separately and record its origin, hash and loaded version:

```powershell
py -3 tools/recording_r2/validate_packet.py r2-packet-local/packet --zstd-library C:/path/to/libzstd.dll --output r2-windows-all-codecs.json
```

The independent bounded decoder enforces one frame, exact size, dictionary ID 0
and window <=8 MiB before output allocation. No DLL is bundled in this repository.
Do not download arbitrary DLLs or silently skip unsupported cases while claiming
all-codec success. If no suitable library is available, record optional Zstd as
not executed/deferred. The [reference release](https://github.com/facebook/zstd/releases/tag/v1.5.7)
and BSD license are the authority for optional builds.

## Completion boundary

The pending native Windows baseline gate is satisfied. Combined with the preserved
[completed live/memory continuation](CONTINUATION_RESULTS.md), R2 is ready for
completion review, with earlier failed runs and memory sampling/backend assurance
limits retained. The 4 MiB candidate remains provisional. R1b owner approval still
must settle identity/magic/version, complete JSON grammar and clock domains,
codec set/IDs, restricted views, numerical limits and large-geometry memory
admission, checkpoint/recovery rules and supported canonical backends. No wire
freeze, canonical R3 fixtures, production recorder, merge or automatic issue closure
follows from this evidence.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
