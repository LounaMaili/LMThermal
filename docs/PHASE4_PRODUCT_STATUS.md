# Phase 4 product and distribution checkpoint

## Recovered phase 3 state

The previous campaign was preserved, not assumed integrated:

- Desktop R5 integration review `63d8076c6297189ab6ec46747b78b4a32cd05407`
  is the basis of the new companion workspace (232 baseline Linux tests).
- Android R4 loss-run prototype `61e8f265319ff846a182cbe7600ac32f5bff3104`
  remains an isolated experiment. Its grouped-loss append can lose the entire final group
  when append fails; its optional witness/default-off semantics are NOT a production recorder.
  No recorder/memory-admission code is imported into this product work.
- Android product work starts at main `8eb4aceaf8bb36a8061c932b3121f898b81b4af3`.
  Desktop main `b0afdf3b42b6f55c2ae3d926f0d8c9df09255746` and old PR heads are untouched.
  Historical R4 worktrees, pending diffs and private evidence remain separate and preserved.

## Developed and host-tested

| Branch | Work | Evidence / limit |
| --- | --- | --- |
| LMThermal `codex/android-product-workspace` | Fixed image stage, Live/Measure/Capture/Settings/Diagnostics; en/fr; existing module/export/readiness APIs | 234 core + 27 local app tests; APK/instrumentation compilation; debug lint; no ART |
| LMThermal `codex/android-play-preparation` | Child of workspace; API 36, unsigned AAB/APK, optional external signing config, icon/backup rules, static ELF/ZIP audit | Same 261 host tests; release lint/build; four aligned libraries; no runtime/Play submission |
| Desktop `codex/desktop-product-workspace` | Owned Home/document tabs, explicit live Connect, legacy/LMTX/LMTR engines, local logs, cooperative Close | 242 Linux offscreen tests; no working native display/operator or Windows validation |
| Desktop `codex/desktop-packaging-preview` | Child of workspace; PyInstaller portable directory, private tarball, pinned headless Qt6 environment, Windows templates and Codex guide | 242 tests and 102 source/frozen canonical comparisons; build-host-only prototype |

The Android display handoff avoids one 442,368-byte IntArray copy per HT-size render by
ownership analysis, not by a new Pixel measurement. Celsius Float32/source/ROI semantics,
latest-pending render guards and USB initialization remain unchanged. Callback FPS,
render/publish timing and perceptible latency are still distinct. No allocation/performance
claim is extrapolated to a physical device.

Source/raw/mask/Float32/opaque evidence, accepted LMTX/LMTR v1 and all 51 canonical LMTR
fixtures remain unchanged. No R6/common LMTR recording control is enabled. Existing legacy
Desktop recording keeps its experimental limitations; it is not the common recorder.
No hosted CI budget, ADB, Pixel, HT-301, account or store access is used.

## Review links and decisions

- [Android UX and allocation audit](ANDROID_PRODUCT_WORKSPACE.md).
- [API 36, signing, native packaging, privacy and Play gates](PLAY_PREPARATION.md).
- [Privacy content draft for owner review](PRIVACY_POLICY_DRAFT.md).
- [Desktop workspace](https://github.com/LounaMaili/LMThermal-Desktop/blob/codex/desktop-product-workspace/docs/PRODUCT_WORKSPACE.md).
- [Packaging choices, Windows templates and native Codex setup](https://github.com/LounaMaili/LMThermal-Desktop/blob/codex/desktop-packaging-preview/docs/PACKAGING.md).

Before release the owner must select licensing (including PyQt GPL/commercial implications),
branding/version/support policy, supported platforms/devices, signing custody and privacy
policy identity/contact/URL. A Fedora-built portable runtime does not establish broad
Linux ABI compatibility. Windows installer and latest UI need native qualification; the
Android UI/API 36/16 KiB behavior needs authorized ART/device/operator qualification.
No release is ready to distribute publicly. Branch publication is allowed only after
checking that none of its workflows match the new branch; no existing PR is changed.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
