# Phase 5 Android field qualification — NOT RUN

## Availability and authorization gate

The owner explicitly deferred the Pixel. **Do not run ADB, discover/pair/install,
launch/stop apps, query phone files/configuration, or run connected tests until a new
explicit confirmation makes the Pixel available for LMThermal.** This includes a
phone that happens to appear online. LMarchery and its data must remain untouched.
No physical Android qualification is claimed by this document or the host tests.
The Linux HT-301 was absent during software qualification; no webcam fallback is allowed.

After future availability confirmation, coordinate exclusive ownership of phone/camera,
keep the existing pairing, use a non-sensitive authorized scene and obtain normal
Android permissions. Do not uninstall/clear data, downgrade, restart the phone, remove
pairing, force GC, trigger memory pressure, alter safety margins, or run a physical Full
LMTR recorder. Common LMTR recording remains absent from the product UI.

## Artifact and environment receipt

Before installation preserve the current user data and existing evidence normally;
never reset it. Record the exact reviewed Git SHA, clean status, debug APK/test APK
SHA-256, signing certificate identity, build commands and versions. Use a same-signer
non-destructive `adb -s <explicit-serial> install -r <apk>` only. A signer mismatch is a
stop requiring an owner decision, not permission to uninstall. Instrumentation replaces
synthetic diagnostic evidence: preserve live evidence before running it, camera closed.

Only after the availability gate use AGENTS.md's normal workflow: start server/list
already connected devices, then mDNS discovery and its advertised current connection
port if needed. Never hard-code a port or re-pair for a daemon restart. Do not affect
another project using the ADB server. Record model/Android SDK/version/ABI, page size,
USB VID/PID, battery/thermal/storage conditions and the matched app source privately.
A Pixel on a newer Android version does not qualify an API 36 or 16 KiB runtime by itself.

## Bounded manual checks

| Check | Procedure | Required observation / evidence |
| --- | --- | --- |
| No camera / denied permission | Owner prepares the normal condition; open app, visit all five panels | Explicit unavailable/denied state, no stale Celsius/legend, readable actions, no automatic controls |
| Read-only Connect | Attach only when free, Connect/Open, grant normal CAMERA/USB prompts | Display preview, responsive scene, no Celsius; discovery/open sends no initialization |
| Existing raw state | Observe current state before doing anything else | RAW14_UNSETTLED remains aiming-only; do not infer host readiness or automatically reinitialize |
| Explicit normal initialization | From a known display start, tap Initialize once | Validated 32772 → raw14 → 32800 → 32768, shutter/settling/liveness gates; readiness not just elapsed frames |
| Native measurement | Non-sensitive warm/cool target; Point and ROI, center/full-grid shortcuts | Native coordinates, warm/cool discrimination, extrema alignment, finite/masked validity and unchanged matrices across colors |
| Presentation | All palettes; Auto then Locked 25–45 °C and back | Only presentation changes; literal center is not assumed equal to trailer center |
| Small screen / accessibility | Portrait/landscape, en/fr, large font; operator may voluntarily use TalkBack | Fixed image while ROI changes, reachable controls, labels/targets and readings usable, no clipped essential actions |
| Capture | Freeze one current LMTX; local SAF save and cancellation; Share only if specifically chosen | Frozen bytes survive rotation/picker; no partial success or stale values; destination readback/independent Desktop parity |
| Lifetime | Normal Close/reopen, rotation, SAF return, Home/screen-off and app-language change | Rotation/SAF retain the owner; ordinary background/locale/screen-off release; explicit reopen does not restore stale readiness |
| Errors | Normal unavailable/permission-denied states only | Clear reason and recovery action; never inject USB faults or force detach |
| Shutdown | Normal Close after checks | Readings clear, single owner releases, no stale handles or automatic reopen |

Use existing [initialization](../RADIOMETRIC_INITIALIZATION.md),
[Android session](../ANDROID_RADIOMETRIC_SESSION.md),
[lifetime](../ANDROID_CAMERA_LIFETIME.md) and [presentation](../ANDROID_CELSIUS_PRESENTATION.md)
rules. Desktop Zoom readback rules are not Android GET_CUR equality requirements.
If a known display start requires a physical reconnect, ask the owner then; do not
reset repeatedly or invent a new transition. No experimental controls are authorized.

## Performance and evidence

Predeclare short matched windows (for example 30 seconds each) for display, ready Auto,
ready Locked, small/full ROI and palette switching. Keep scene, resolution, build,
orientation and conditions comparable. Record failures and don't choose a favorable
heap snapshot. No indefinite stability claim follows from finite windows.

Report acquisition/callback FPS separately from render preparation, publication/rejected
render counts and operator-visible freshness. Render duration is not total motion latency.
Collect bounded debug presentation/session/ownership logs and ordinary memory/GC evidence
without causing GC. True visible latency needs independent observation and an explicitly
consented measurement method; moving a hand only establishes qualitative responsiveness.
No physical temperature/calibration inference is allowed.

Store originals, raw logs, environment receipt, APK and LMTX hashes in a new private
persistent directory, never overwrite prior sessions. Keep images off Git; export a
sanitized numeric summary only. Independent Desktop validation must compare saved Float32,
validity, ROI, native/acquisition evidence and source immutability without recomputing
thermometry. A completed file, numerical parity and smooth preview are distinct outcomes.

## Completion record

Every physical case currently has status **NOT RUN / waiting for explicit Pixel availability**.
The reusable host checks compile the instrumented recipe; compilation is not ART execution.
Record pass/fail/not-run and exact source SHA for each future case. Unavailable hardware
is a deferred gate, not a reason to stop independent software qualification.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
