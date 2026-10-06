# Android camera lifetime and temporary UI transitions

## Ownership and root cause

`MainActivity.onStop()` previously called `AndroidCameraCoordinator.leaveForeground()`
unconditionally. Rotation follows `onPause → onStop → onDestroy → onCreate → onStart`;
SAF covers/stops the caller and returns through the Activity Result registry. Both therefore
closed a valid source even though `CameraViewModel`, its application-context coordinator,
presenter and immutable capture exporter already survived configuration recreation.
USB did not require those releases. Android documents these stop/recreation transitions in
[Activity state changes](https://developer.android.com/guide/components/activities/state-changes).

The same retained ViewModel now owns a generic `CameraUiLifetime` policy. It holds only UI
generations, a locale signature and an outstanding destination-transaction flag. It never
holds an Activity, View, launcher, driver or camera identity. `CameraSessionOwner` continues
to serialize one module session and await release before any replacement. There is no
singleton, foreground service, wake lock, automatic reopen or grace timer. MainActivity uses
`singleTask` entry so launcher/USB re-entry cannot allocate a second retained composition root.
Re-entry above SAF brings that root back and cancels the picker via the normal result path;
it does not create a new camera owner.

## Exact event policy

| Event | Camera ownership |
| --- | --- |
| Pause only: normal permission dialog / system overlay | Retain; no controls or state reset. |
| Stop with `isChangingConfigurations`, not finishing | Retain source; replacement UI binds a new generation. |
| SAF launch | Register destination transaction before launch; retain on stop. |
| SAF completion/cancellation | Resolve transaction in the current UI; publish/cancel only the immutable export. |
| Stop without configuration/picker exception | Clear preview/measurement synchronously; await background release. Home/app switching from the viewer uses this path. |
| `isFinishing` stop | Release, including Back/termination during a transaction. |
| `ACTION_SCREEN_OFF` | Release even during a picker transaction; explicit reopen after unlock. |
| Changed app-preference/effective locale signature | Release before replacement UI composition; explicit reopen, no initialization. |
| Close | Clear data immediately; await USER release exactly once. |
| Matching USB detach | Release; unrelated device detach has no effect. |
| Fatal module ERROR/action failure | Invalidate generation/data; await FAILED release outside its collector/mutex. |
| Final ViewModel disposal | Clear/dispose UI, export and presentation; await source close before cancelling owner scope and unregistering receiver. |
| Process death | Android closes process USB descriptors; a new process starts without a live owner or readiness. `onCleared` is not guaranteed on process kill. |

**Destination transaction exception:** while external SAF is outstanding, its lifetime
intentionally includes Home/app switching *inside the external picker*. Android does not
forward that external Activity's lifecycle to this app. Ownership ends on result,
screen-off, matching detach, failure or final disposal. Returning from SAF does not itself
close; the next ordinary viewer background stop does. This is a bounded-by-events transaction,
not a background acquisition service; it does not guarantee survival of Android process death.
Short interruptions that actually stop the Activity without a tracked exception release
conservatively. The Share chooser is not a destination transaction and retains that policy.

Old Activity stop/result callbacks fail the UI-generation check. Activity Result registry
redelivery after recreation targets the current instance. Source-generation gates and
latest-request presentation checks continue to prevent stale measurement publication.
Rotation preserves ROI geometry, Point/ROI mode, selected point, palette and Auto/Locked range
with the retained presenter. Layout/gesture mapping is rebuilt for the new viewport without
changing native coordinates. Locale release can retain geometry/choices but never readings.

## Protocol and export invariants

No lifecycle event opens a source or initializes it. Explicit initialization remains unchanged:
32772, frame-based genuine raw14 acceptance, 32800, 32768 and validated settling/liveness.
GET_CUR equality is not a readiness gate; raw words are never masked. An explicitly reopened
already-raw camera remains `RAW14_UNSETTLED`, not ready. Generic lifecycle code contains no
HT-301 test or USB command. Full acquisition/thermometry stays on the existing workers.

The prepared capture belongs to `CaptureExporter`, independently of the live source. It
survives these UI changes byte-for-byte until publication/cancellation/disposal; SAF export
still sends no camera controls. Cancellation deletes staging and disables Share; successful
publication requires destination close and grants only the finalized file. LMTX v1 semantics,
Float32 encoding and stale-Celsius exclusion are unchanged.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Diagnostics and regression checks

Debug `camera-lifetime.jsonl` / `LMThermalLifetime` records a random local owner ID,
session ordinal, cumulative opens/releases/actions, active-owner count, lifecycle events,
received-frame count, FPS and current session state. It records no USB path, hardware serial,
scene bytes or frame fingerprints. `radiometric-session.jsonl` separately records actual
control requests; compare its write events before/after transitions rather than treating
an explicit-action counter as USB write evidence. Files are bounded developer diagnostics.

`CameraUiLifetimeTest` covers picker return/cancel, changing frames, recreation/stale UI,
background, locale, screen-off, finishing, detach and fatal failure with a counted generic
module. `CameraActivityLifetimeTest` exercises actual Activity/ViewModel recreation with an
injected source, retained choices/artifact bytes, and ordinary-stop release.
`CameraEntryDeviceTest` checks that repeat root entry reuses the same Activity/ViewModel
on the Pixel without opening USB. Existing tests
cover serialized replacement, cancellation/failed release, thermometry, masks, ROI,
localization, coherent snapshots and publication/cancellation/Share.

Run with the repository JDK 17 and Gradle cache:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
python3 tools/check_android_localization.py
python3 tools/check_camera_module_boundaries.py
git diff --check
```

Physical results are recorded in [ANDROID_VALIDATION.md](ANDROID_VALIDATION.md).
