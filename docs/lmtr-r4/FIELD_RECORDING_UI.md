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
After an RSS Stop, `MEMORY_SAVE` becomes available once the recorder and field
jobs finish and the stopped collector releases its lease. The Start lock still
applies. A Stop timeout, cancelling worker or collector that still holds its
lease defers the export.

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

## Next authorized field qualification

1. Confirm the owner's authorization for the physical session. Record the
   Android field and Desktop SHAs, APK digest, and exclusive device
   availability. Use a non-sensitive scene and the normal permission and safety
   checks. The operator observes the preview in normal radiometric Ready mode.
2. Keep the preview and configuration fixed. If the authorization includes
   tiered diagnostics, arm them before admission without waiting for a favorable
   sample. After the operator has observed the preview and explicitly
   authorized a single Start, press **Start** for
   **Analysis / 5 Hz / DEFLATE-1 / 10 s requested**.
   Observe responsiveness, acquisition FPS, selections, admissions, ACKs and
   losses. Let the existing cutoff and drain run. A refusal, early safety
   interruption or blocked worker is a result. Preserve it without retry.
3. If armed, issue `MEMORY_STOP` and wait for actual collector and recorder
   release before `MEMORY_SAVE`. Retain the Start lock after a safety stop.
   Open **Recordings**, select the exact run, choose **Verify**, then
   **Export copy…** to a new document in local Downloads after verification.
   Record the reported integrity scope and readback result. Preserve the
   original private LMTR and sidecars with their SHA-256 digests.
4. Open the copied LMTR in the existing Desktop Linux reader. Compare its hash
   with the original, verify END or the explicit recovery scope, and record
   actual intake duration, acknowledged observations and gaps. Use
   **Temperature over time** to plot a point or ROI, seek an event and export
   CSV. Check the recorded intake times, unavailable cells, and original
   bytes. Close the camera normally and confirm release.

Use admission, duration, responsiveness, integrity and retained cadence as
separate outcomes. This procedure has not been executed for the consolidated
candidate and authorizes neither a Start nor a longer run.

## Integration boundary

Review the stacked field PR against `codex/r4-tiered-memory-diagnostic` at
`59b4e9a`. That delta contains the field recorder UI, private library and SAF
copy, this post-stop diagnostic fix and their tests/docs. The production
`:app` remains separate from the recorder and field modules. The wider
foundation history includes shared camera, acquisition and presentation
changes. Review that wider delta separately before any integration into main.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
