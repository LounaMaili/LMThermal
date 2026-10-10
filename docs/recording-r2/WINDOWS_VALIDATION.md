# Real Windows R2 validation handoff

**Pending. Preparing this packet does not pass R2 Windows parity or complete R2.**
All packets are noncanonical R2 synthetic artifacts, not R3/canonical release fixtures.
Same source bytes already passed Android and the independent Linux validator.
The optimized candidate `9f9d6097960d17188611e2da159ac4606cee148d` regenerated
all 15 exact member hashes unchanged and passed independent Linux readback again
([continuation proof](reports/continuation-packet.json)). The original ZIP is
retained; no Windows run is inferred from Android/Linux execution.

Use the repository at the pushed R2 branch with Python 3.11 or newer. In PowerShell,
from the repository root:

```powershell
Expand-Archive -Path docs/recording-r2/artifacts/android-r2-packet.zip -DestinationPath r2-packet-local
py -3 tools/recording_r2/validate_packet.py r2-packet-local/packet --baseline-only --output r2-windows-baseline.json
```

Baseline-only explicitly excludes Zstd cases; it validates STORED/DEFLATE complete
Analysis/Native/Full, exact synthetic Float32/mask/native/transport/context/gap bytes,
HT Full restricted native views, missing-END recovery, committed corruption errors,
lazy seeks and source SHA-256 immutability. No camera/Android/GUI needed. Do not edit
the source members. Report Windows version, Python version and the produced JSON.
A baseline-only pass cannot be reported as a Zstd pass.

For optional Zstd parity, obtain a trusted reference Zstd library for the machine's
Python architecture (reference 1.5.7 used on Android/Linux), then:

```powershell
py -3 tools/recording_r2/validate_packet.py r2-packet-local/packet --zstd-library C:/path/to/libzstd.dll --output r2-windows-all-codecs.json
```

The independent bounded decoder records the loaded library version and enforces
single-frame, exact size, dictionary ID 0 and window <=8 MiB before output allocation.
No binary DLL is bundled into the repository; do not replace missing codec support
with silently skipped cases. The [reference source/release](https://github.com/facebook/zstd/releases/tag/v1.5.7)
and BSD license are the authority for optional builds.

Return the JSON plus Windows/Python details. The archive hash/provenance is in
[PROVENANCE.json](artifacts/PROVENANCE.json); each exact member hash is in packet.json.
R2 #7 and R1 #6 remain open until their own gates and owner decisions are satisfied.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
