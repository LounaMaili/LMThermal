# Interactive synthetic Android UX acceptance

This is the **principal app**, `org.lmthermal.app`, on the UX V1 branch/PR #19.
The debug launcher **LMThermal · Demo** uses the actual MainActivity screen,
CameraViewModel, coordinator, Celsius/ROI presenters and LMTX exporter. It adds
no production feature, thermometry algorithm, USB command or LMTR recorder.

## Launch and interact

Use the already running **emulator-5554**, API 36. Do not use a physical phone or
camera, clear AVD data, restart ADB, or run unqualified connected tests.
From this worktree after building (toolchain: [CONFIGURATION.md](CONFIGURATION.md)):

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 shell getprop ro.build.version.sdk
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n org.lmthermal.app/.demo.SyntheticDemoActivity
```

1. Tap **Connect / Open** (French: **Connecter / Ouvrir**). The scene animates at
   a nominal four frames per second. No initialization is needed by this fixture.
2. In **Point**, touch the image. In **ROI**, drag from one corner to another;
   drag again to replace the rectangle, or use **Clear ROI**.
3. Tap the palette/range summary for Inferno, White hot, Black hot, Iron-like or
   Turbo and Auto/Locked range. Edit bounds, for example `20,5` and `40,5`.
4. **Settings → Diagnostics** (French: **Réglages → Diagnostics**) offers Play/Pause,
   Next frame and Reset. **Reset** pauses at scene frame 0; **Next frame** also pauses.
   The reference temperatures and rectangle are explained in this panel.
5. **Save capture → Choose destination → Save** uses Android's real document picker.
   The `.lmtx` is a still, with native Float32 values, ROI and `simulated` source
   provenance. Preparing freezes one frame even if the animation continues.
6. Rotate the emulator using its rotation controls. Native matrix/coordinates
   stay 384×288. Backgrounding, closing, or changing language follows the existing
   explicit-reconnect policy; rotation alone retains the source and selections.

Use the existing emulator window or Android Studio's **Running Devices** view to
manipulate the app. The launcher remains available after instrumentation ends;
it is not tied to a test process. The normal principal-app entry is unchanged:

```bash
adb -s emulator-5554 shell am start -n org.lmthermal.app/.MainActivity
```

The initial empty viewport retains the product's generic USB hint. In the demo,
the detected model reads **Synthetic demo · debug** and Connect opens only the
injected scene; no USB camera is required or selected.

## Numerical reference

Coordinates are native pixels, zero-based; bounds below are inclusive.

| Region / point | Expected Celsius |
| --- | --- |
| Left patch: x=32…159, y=32…111; point (64,64) | 10 |
| Right patch: x=224…351, y=32…111; point (288,64) | 40 |
| Background, including (192,64) | 22 |
| Moving target center: (64 + 4 × (frame mod 64), 208) | 35 |
| Moving target radius ≤48 pixels | Linear radial falloff from 35 to 22 |
| ROI x=128…255, y=48…95 | Mean 23.5; min 10; max 40; 6,144 valid pixels |

The ROI spans 32 cold + 64 background + 32 hot columns over 48 rows:
`(32×10 + 64×22 + 32×40) / 128 = 23.5 °C`. It is independent of the moving target.
At frame 0 the target is centered on (64,208); at frame 1 it is on (68,208).
The scene repeats after 64 frames. Full-image extrema stay 10/40 °C. These are
analytic fixtures, not camera measurements or physical calibration evidence.

## Reproduce the automated acceptance

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  :app:assembleRelease :app:lintDebug
python3 tools/check_android_localization.py --self-test
python3 tools/check_camera_module_boundaries.py
python3 tools/check_debug_demo_isolation.py
python3 tools/run_synthetic_ux.py
```

The runner hard-codes emulator-5554, checks API 36/emulator identity and an existing
local ADB server, installs with `-r` (no clearing), and invokes only
`SyntheticDemoDeviceTest`. `--locale en|fr|both` and `--output PATH` select receipts.
Tests restore the previous application locale/orientation. They create synthetic
LMTX documents in the current local SAF destination; existing documents are not
overwritten. The default picker on the tested AVD uses Downloads.

`build/ux-synthetic` contains raw instrumentation receipts, screenshots for every
palette and both orientations, measured-reference JSON, synthetic LMTX copies and
a hash inventory. Screenshots are checked for complete PNG chunks/CRC before
copying. Only the recipe's own private `files/ux-synthetic` files are collected.
The host runner does not change font scale or screen size.

The three instrumentation scenarios cover:

- Evolution, three fixed point readings, frame-zero peak, held ROI across live
  frames without viewport movement, exact native ROI statistics and displayed
  values, real orientation change with the same ViewModel/session, and one-frame step.
- Five actual palette buttons, color changes without measurement changes,
  decimal-comma manual range then automatic extrema, real capture preparation,
  Activity recreation, a newer live frame, system SAF Save, LMTX conformance and
  exact frozen Float32 matrix bits.
- Explicit Close and true background/foreground transitions: no stale measurement
  or capture action, and an explicit reconnect is required.

Two debug JVM tests independently cover all 64 scene frames, exact reference ROI
arithmetic, periodicity, target movement and rejection of a real USB identity.
Existing product UI state regressions are run separately; no R4 campaign is needed.

## Isolation and evidence limits

All fixture code, strings and its separate launcher manifest are in `app/src/debug`.
The Activity overrides only the ViewModel factory; its registration list contains
only the synthetic module and discovery only the existing `SimulatedCameraIdentity`
type. It neither registers the HT-301 module nor requests CAMERA permission.
The existing coordinator still owns explicit open, configuration retention,
background close and awaited disposal. A single background coroutine publishes
owned measurements through StateFlow; command delivery is conflated and bounded.

`check_debug_demo_isolation.py` verifies the built debug APK contains the class,
activity, identity and resource markers, and the built release APK contains none
of them. No runtime dependency was added, and the normal production composition
root, thermometry, USB, camera lifetime and source ownership code were not edited.

Results and representative images: [synthetic gallery](ux-synthetic/README.md).
Real Pixel 8/HT-301 behavior, physical accuracy, external/cloud document providers
and manual TalkBack navigation are outside this synthetic acceptance. No merge is
authorized by these results.
