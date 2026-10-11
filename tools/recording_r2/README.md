# Disposable R2 feasibility tooling

**Noncanonical R2 experiments. No production recorder or LMTR v1 release bytes.**
See [predeclared plan](../../docs/recording-r2/TEST_PLAN.md). No camera controls or
thermometry algorithms live here. The independent Python reader accepts only this
prototype revision; its constants are a proposal for R1b, not a frozen contract.

## Build / test

Use the repository's existing JDK 17 / Gradle environment:

```sh
./gradlew :recording-r2:test :app:assembleDebug :app:assembleDebugAndroidTest
python3 tools/recording_r2/reader.py /path/to/noncanonical.r2proto
```

Optional test-only arm64 Zstd requires the official `zstd-1.5.7.tar.gz` under
ignored `.local-tools/recording-r2/`, downloaded from the official v1.5.7 release.
`python3 tools/recording_r2/build_zstd_test.py` verifies SHA-256
`eb33e51f49a15e023950cd7825ca74a4a2b43db8354825ac24fc1b7ee09e6fa3`, builds
reference source with the existing NDK and packages `libr2_zstd.so` only through
the Android **test** source set. BSD-3-Clause option of its dual license applies.
No third-party source/binary or dependency is added to the application APK.
Window <=8 MiB, no dictionaries, independent single frames, exact decoded size.

`R2PacketDeviceTest` writes compact synthetic packets on Android, with exact
cross-platform logical-byte tests and read-only app-private descriptor access.
It requires explicit instrumentation argument `r2Packet=true` and the built test
codec library; ordinary product regression skips this optional packet experiment.
`R2ContextDeviceTest` always checks semantic metadata parity without native JNI.
`R2LiveDeviceTest` is ignored unless `r2Live=true` is explicitly passed to the
instrumentation runner. It launches the unchanged app and waits for operator
Connect/Open and Initialize. Never launch it as an unattended control test.

Live arguments include `r2Run`, `r2Plan`, `r2Revision`; output lives under the
application's private `files/recording-r2/<run>/`. Source scenes are private and
must not be committed. The harness consumes a latest StateFlow and reports missing
sequences; native callback counts do not imply all callbacks were recorded.
Compression/sync use a dedicated bounded writer thread. No GUI recording action.

## Limits / assurance

40-byte LE record header, bounded body, 72-byte commit footer; SHA-256 body and
logical payload integrity plus CRC32 repeated framing. Two file-sync barriers
precede commit counters. All referenced offsets point backwards and fit signed
64-bit nonnegative values. Chunk-local contexts; Full native views reference only
same-frame materialized acquisition. Paged indexes use 256-child fanout/depth 8.
Checkpoints every 32 chunks; recovery suffix <=64 chunks/1024 records allows one
damaged checkpoint. Longer corruption is a declared recovery limit. Sync tests
are software/filesystem evidence, never physical power-loss certification.

Unknown capacity permits a supported backend with a warning. Insufficient known
startup+reserve blocks. No-copy read-only descriptors are access, not backups.
App-private data disappears on data-clear/uninstall; backups/access lifetimes need
an explicit product decision. Untrusted SAF providers gain no durability claim.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.

`validate_packet.py DIRECTORY --zstd --output RESULT.json` checks the Android
packet on Linux/Windows, including independent synthetic Float32/native/transport
expectations, recovery, corruption and immutable source hashes. A local Zstd DLL
may be supplied with `--zstd-library PATH`; without it Zstd packets are explicitly
unsupported, never silently skipped. `process_faults.py` spawns and kills only its
own writer children at named boundaries. `sparse` is a JVM tool-only forward logical
zero-hole generator for >4 GiB offsets; production/backend seeking is not inferred.

The separately installed test APK has `R2SafActivity`, a disposable local-provider
probe with no camera ownership. Its file seed is an already completed synthetic
packet, copied into the test app's private directory for this probe. It requests
one operator-chosen SAF destination, checks rw/append/truncate/readback/sync/close,
and records source integrity. It is absent from the application APK. Cancellation
and permission lifetime are independent tests; no provider-wide guarantee follows.

## Continuation stage diagnosis

See [the committed continuation plan](../../docs/recording-r2/CONTINUATION_PLAN.md).
`R2StagesDeviceTest#controlledStages` is opt-in with `r2Stages`; defaults are
10 s warmup and 60 s per stage. Set `r2Run` to a fresh identifier and `r2Revision`
to the exact installed executable SHA. Choose
`baseline,observe,freeze,queue,discard,nosync,file`; output is private
`files/recording-r2/<run>/stages.json`. Discard/no-sync are nondurable ablations.
They retain the legacy intake algorithm for before/after diagnosis, not an approved
recording configuration. Stage CPU uses Android thread CPU; copy/allocation counters
are explicitly lower bounds and process allocation counters are not isolated CPU.

After building/installing the test APK, with the device unlocked and camera closed:

```sh
python3 tools/recording_r2/check_saf_launch.py --adb /path/to/adb --serial <paired-serial>
python3 -m unittest discover -s tools/recording_r2 -p 'test_*.py'
```

The first command launches only the separate test probe, then verifies visible
instructions and a usable nonoverlapping destination button via Android layout
diagnostics. It does not open USB or select a destination. Actual provider claims
remain limited to the preserved SAF evidence; this is a launch regression.


`R2CandidateDeviceTest#stagedCandidate` opts in with `r2Candidate`, using a fresh
`r2Run` and exact installed `r2Revision`. First choose
`baseline,analysis-stored,full-stored` (20 s warmup/120 s measurement each).
It stops escalation on preparation/writer loss or failure. Only after that passes,
choose `native-stored,analysis-deflate-1,native-deflate-1,full-deflate-1`.
Private `candidate.json` includes saved matrices versus gaps, costs, backlog,
render freshness, 100 ms Java/native and 1 s PSS/RSS, post-GC and conservative
live array-slot inventories. Clean-process memory comparisons are separate.


The measured 16 MiB preferred Native-DEFLATE seal exceeded the six-frame queue
window despite adequate mean service. The predeclared repeat explicitly passes
`r2ChunkTargetMiB=4` for Analysis/Native/Full DEFLATE. Default remains 16 MiB;
both handoffs remain 4 MiB and the hard decoded bound remains 64 MiB. This is a
provisional prototype configuration for R1b review, not a new format semantic.
The report records the actual preferred size. Use default 16 for the separate
fresh Full-STORED memory case; no bulk evidence backup during measured runs.

### Resuming an interrupted readiness wait

An expired wait is not a live session or performance result. Inspect actual ADB,
instrumentation/process/USB ownership first; preserve the old directory and logs.
Verify installed APK hashes against the committed executable, then start only the
unfinished stage in a fresh process with a new `r2Run`. Wait for actual measurement
readiness from a new explicit operator Connect/Open/Initialize; never inherit READY
or DETECTED from a previous run. Do not reinstall/clear data merely to restart a wait.
A previous completed raw14 session requires the validated display baseline before
new initialization; request a physical reconnect only for that concrete reason or
an absent device. Do not invent a camera reset command.

The resumed Full-STORED and 4 MiB DEFLATE measurements are documented in
[the continuation report](../../docs/recording-r2/CONTINUATION_RESULTS.md).
Their private recordings remain outside Git. The same-byte synthetic packet is
now passes [native Windows validation](../../docs/recording-r2/WINDOWS_VALIDATION.md):
10 baseline outcomes and a separate optional 15-case Zstd 1.5.7 run, with exact
Linux parity and immutable sources. R2 is ready for completion review; no
R1b/R3/product work follows automatically.
