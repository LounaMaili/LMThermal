# Android camera workspace V1

This is the principal application (`org.lmthermal.app`), based on `main` at
`8eb4aceaf8bb36a8061c932b3121f898b81b4af3`. It does not introduce an LMTR recorder
or depend on the experimental field application/R4 branches.

## Daily workflow

1. Attach a supported USB camera and use **Connect / Open**. Discovery and opening
   remain read-only. The status and model explain the current source state.
2. Use **Initialize radiometric** explicitly when the current streaming source
   offers it. During initialization the button is disabled and progress is shown;
   a ready source does not offer redundant initialization.
3. Inspect the image with **Point** or draw/replace a **ROI**. Point temperature and
   image minimum/maximum, or ROI mean/minimum/maximum, use the current authoritative
   measurement. Missing data shows `— °C`, never zero or the last good value.
4. Tap the palette/range summary to open **Image appearance**. Select a palette,
   Auto/Locked range, or edit the finite Celsius bounds (decimal comma is supported).
   These settings change colors only.
5. **Save capture** freezes a still through the existing LMTX exporter and opens a
   capture panel. **Choose destination** publishes it through the registered SAF
   transaction. Cancellation, completion, errors and sharing remain in that panel.
   Preview-only captures are explicitly identified using the frozen artifact’s content class, even if later live frames become measurable.
   Closing the panel does not discard a prepared capture; use Cancel export for that.
6. **Settings** contains the existing System/French/English language selector,
   source information, native sample and advanced module diagnostics. Language
   changes keep the existing release/explicit-reconnect policy.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
The full warning remains in the command area and Settings. No numerical/protocol
claim is established by this UI change or its synthetic screenshots.

## Layout and implementation boundaries

- A stable dark surround, readable system bars and text-labelled touch targets of
  at least 48 dp support phone use without additional runtime dependencies.
- The image never belongs to the command scroll container. Portrait places the
  workspace above a bounded control area; landscape places controls to the side.
- At ordinary text sizes, the temperature summary stays visible while commands
  scroll. Enlarged fonts or very short windows allow that summary to scroll with
  the controls so neither actions nor the image collapse to zero height.
- Viewport dimensions depend on available window space, not ROI results, palette,
  export phase or frame availability. Legend space and readout lines are reserved.
  Secondary panels overlay the workspace without resizing it.
- `ThermalViewport` keeps the existing centered `ContentScale.Fit` image and
  `ImageCoordinateMapper` for touches, point markers and native ROI cell edges.
  It does not crop, rotate, mirror or resample measurement matrices. Rounded
  background corners do not clip the camera image.
- `ThermalScreen` composes the layout; `ThermalReadouts`, `ThermalControls` and
  `ThermalRangeDialog` contain presentation-only components. Saveable panel/range
  edit state survives ordinary Activity recreation.
- The existing `CameraViewModel`, coordinator, permissions, `CameraUiPolicy`,
  presenters, export transaction and camera lifetime policy are reused unchanged.
  USB transport, thermometry, source ownership and frame handoff are unchanged.
- Debug builds include arm64-v8a and x86_64 so the same principal APK can run on
  the emulator; release ABI support remains arm64-v8a. No native code or dependency
  was changed to enable the emulator build.

## Candidate and camera-free checks

Build with the toolchain from [CONFIGURATION.md](CONFIGURATION.md):

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug \
  :app:assembleDebugAndroidTest :app:lintDebug
python3 tools/check_android_localization.py --self-test
python3 tools/check_camera_module_boundaries.py
```

Target an explicit emulator serial for every command. Do not use an unqualified
`connectedDebugAndroidTest` when the physical phone is also connected:

```bash
adb -s emulator-5554 shell getprop ro.build.version.sdk  # must be 36
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class org.lmthermal.app.ThermalWorkspaceDeviceTest,org.lmthermal.app.LocalizationLayoutDeviceTest \
  org.lmthermal.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am start -n org.lmthermal.app/.MainActivity
```

The available `medium_phone` API 36 AVD was started with its original data and
snapshot saving disabled. Its software graphics backend crashed before boot;
`-gpu host -feature -Vulkan` booted and completed the reported suites. Later
emulator-process SIGSEGVs interrupted screenshot retrieval, so the final recovery
tried the software renderer with Vulkan disabled (also failed before boot), then
recovered the screenshots in a short host-renderer run. These host emulator failures are
separate from the successful instrumentation receipts; emulator stability is not
claimed. No AVD replacement, data wipe,
phone connection or camera action was used as a workaround.

The layout suite injects a synthetic 160×120 two-temperature scene through the
existing test seam. It covers four locales × two orientations, held ROI gestures,
viewport/control stability, current-frame invalidation/recovery, capture preparation
and cancellation, range controls and access to secondary settings. The state suite
covers camera-free startup, settings recreation, explicit-only initialization,
disabled/hidden actions, transient measurement clearing and range validation.

See [the screenshot gallery and validation results](ux-v1/README.md). These are
emulator UI checks, not real HT-301 acceptance or physical temperature validation.
Real-camera UX and capture-provider ergonomics remain an explicitly authorized
follow-up. No R4 qualification campaign was run.
