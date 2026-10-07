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
