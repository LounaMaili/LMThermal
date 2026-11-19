# Experimental Android field recorder

The `org.lmthermal.field` panel shares the existing Compose camera preview and
accepts an explicit **Start** request after the source reports a current ready
measurement. Start runs the existing admission checks. The only available
option is Analysis, DEFLATE-1 and a 5 Hz target for the existing ten-second
request. The field guard still owns stopping, resource refusal and drain.
Other profiles and cadences are outside this UI. The production APK excludes
the recorder.

Analysis retains the measured Float32 planes, timing, available
calibration/settings and loss evidence. It omits native pixels and USB transport.
The storage sheet separates the 50-plane uncompressed estimate from the current
private-directory capacity and the more conservative existing preflight.

Selected counts come from periodic intake. Accepted counts are admissions to
memory. Stored ACKs are committed CHUNK observations and may include unavailable
measurements. The sheet identifies preparation and writer queue losses,
materialization losses, missed slots and unclassified callbacks separately.
Acquisition FPS, the 5 Hz target and the observed stored ACK average have
different populations. The rate uses elapsed intake time ending at the
recorder cutoff once known. A missing witness is shown as unavailable.

Stop requests cut off intake through the existing path. Drain includes
finalization and the read-only recheck. The latest verified file metadata
includes the private run ID, filename, bytes, END status/reason and SHA-256.
An interruption can still have a valid END and retain data. A missing or
unverified END does not appear as a successfully checked file. The UI keeps
the original archive intact. Its separate library exports a copy on request.
The pending `MEMORY_SAVE` export issue following an RSS Stop remains tracked.
A future correction must wait for released resources and keep Start blocked.

## Build and emulator demo

Use the repository JDK and Android SDK/NDK/CMake settings. The normal field
build keeps its arm64 ABI. For a local x86_64 emulator, build:

```bash
./gradlew -PfieldX86=true :lmtr-field:assembleDebug
adb -s <local-emulator-serial> install -r lmtr-field/build/outputs/apk/debug/lmtr-field-debug.apk
adb -s <local-emulator-serial> shell am start -n org.lmthermal.field/org.lmthermal.field.FieldActivity \
  -a org.lmthermal.field.RECORDING_UI_DEMO
```

In a freshly launched debug process the demo displays a generated thermal
illustration, independent example counters, Start/Stop transitions, storage
information and interruption/refusal examples. It constructs no live field
model or recorder and writes no LMTR file. A launcher start opens the real
field preview with Start disabled until a qualified source exists. Keep the
demo and any physical test authorizations separate.

## Find and export a recording

1. Tap **Recordings** after recorder ownership has been released. This uses the
   normal field lifecycle when leaving the preview. The library lists up to 200
   recent private `r4-field/<session>/capture.lmtr` files and reports older
   entries omitted from the list.
2. Tap **Verify**. A full check distinguishes completed, interrupted and failed
   ENDs. An incomplete or navigation-damaged file can have a verified prefix.
   Unsupported files, errors and untouched rows remain unverified. Checks run
   serially in the background with the existing LMTR reader's 48 MiB working
   budget. Cancellation is cooperative between reads and copy blocks.
3. Tap **Export copy…** and choose a new document in the Android chooser. The
   copy uses bounded streaming and compares its exact length and SHA-256 with
   the inspected source. If readback is available, it also checks the
   destination. A provider that refuses readback can leave the copy written
   with destination integrity unconfirmed. Failed or cancelled transfers
   attempt to remove only the newly created destination.
4. Open the resulting `.lmtr` in LMThermal Desktop. Use explicit recovery when
   the source had only a verified prefix. The copy includes the full original
   bytes and never repairs the recording.

On an emulator launched with the demo action above, **Recordings** opens
three labeled, synthetic examples in a separate private demo cache. The
completed sample includes two observations and a gap. The other samples have
a missing END or intentionally damaged logical data. Choose the completed
sample for a normal Desktop open.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
