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

Each field run owns its closure until sampling and recorder workers exit and
the read-only check, `private-report.json` and `inventory.json` are complete.
Background events during closure share the same task. Opening **Recordings**,
returning to the preview or destroying the ViewModel after closure leaves both
sidecars intact, including when the RSS Start lock remains set. A timed-out
close retains ownership for cleanup and keeps **Recordings** and `MEMORY_SAVE`
unavailable.

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
separate outcomes. The consolidated demonstration verified the copy and
Desktop analysis of a recording interrupted by the RSS guard. Ten-second
completion for that candidate remains unqualified. A new Start requires
separate authorization.

## HT-301 source memory

In the November 21 demonstration, the RSS guard ended intake after 3.271 s.
All 17 selected observations became durable, with no preparation or writer
loss. That run supplies an RSS and integrity observation. It does not isolate
memory use by the acquisition, presentation and recorder owners.

A ready HT-301 measurement now resolves each unchanged 16-bit transport word
directly through the validated LUT into the Celsius plane. The frame already
owns the original native words, so dropping the retained raw `IntArray` saves
442,368 array-data bytes per distinct ready measurement. Retained source
array data falls from 1,108,992 to 666,624 bytes. The measurement, preview and
recorder can alias that source owner. The saving applies once per source, even
when several consumers hold it. `raw14()` still returns a fresh array for
diagnostic callers. The recorder's maximum charge and resource policy remain
fixed. Analysis still saves Float32 temperatures with timing and context,
while Native and Full keep their existing native and transport evidence.

The host-only `Ht301SourceMemoryProbe` uses the same bytecode with the
`e146762` baseline core JAR and the candidate JAR. It alternates the existing
room and hand fixtures through the actual parser, measurement gate and
immutable source wrapper. Four fresh HotSpot 17.0.20.1+1 processes ran in
baseline/candidate/candidate/baseline order with a fixed 256 MiB heap, 96
warmup frames and six 16-frame batches per process. The caller-thread median
fell from 1,177,904 to 735,520 allocated bytes per frame, a reduction of
442,384 bytes including the array header. At 25 valid evaluations per second,
that would remove roughly 10.55 MiB/s of Java allocation. The probe excludes
JNI, Android rendering and the recorder's worker allocations. Its batch wall
medians were 1.881 and 1.605 ms per frame. Neither metric establishes an Android
RSS or recording-duration improvement.

For a local comparison, generate the runtime classpath with
`:lmtr-field-support:writeTestRuntimeClasspath`. Run
`org.lmthermal.field.Ht301SourceMemoryProbe` with that classpath and
`-Xms256m -Xmx256m`. Substitute the saved baseline `core.jar` entry for the
baseline arms. Passing a fresh output path runs one synthetic Analysis / 5 Hz /
DEFLATE-1 recording instead. A virtual 10-second source supplies 250 frames
and waits for the worker stages between selected frames. Both builds selected
and acknowledged the same 50 observations, without gaps. The independent
Python reader verified every receipt and slot, valid END and all 50 Float32
planes against the Desktop goldens. The complete files were 5,961,615 bytes
with the same SHA-256
`d8294a490ab7ed12f43b21be748a811bb9f6efbd90aebd85907c0adfbf6eb53e`.
Worker pacing makes this an integrity check with deterministic inputs. Live
preview latency, ART memory behavior and sustained recording remain physical
qualification gates.

## Integration boundary

Review the stacked field PR against `codex/r4-tiered-memory-diagnostic` at
`59b4e9a`. That delta contains the field recorder UI, private library and SAF
copy, this post-stop diagnostic fix and their tests/docs. The production
`:app` remains separate from the recorder and field modules. The wider
foundation history includes shared camera, acquisition and presentation
changes. Review that wider delta separately before any integration into main.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
