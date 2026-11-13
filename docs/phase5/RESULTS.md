# Phase 5 qualification results — 2026-11-13

## Status matrix

| Area | Completed evidence | Still pending |
| --- | --- | --- |
| Android product UI | 234 core + 39 app JVM/Robolectric tests; English portrait/French landscape SDK32/36, source failure/no-camera/native ROI controls | ART, actual touch/TalkBack, visible responsiveness and physical camera |
| Android build/release | Debug, instrumentation and unsigned release APK/AAB; debug/release lint zero errors; localization/boundary/ELF/ZIP checks | Signed delivery, actual 16 KiB runtime/Play generation, owner privacy/licensing/signing decisions |
| Desktop software | 249 tests on CPython 3.15.2 and isolated CPython 3.13.18; eleven offline UI cases per frozen candidate | Complete unlocked native/operator acceptance, actual HT-301 acquisition |
| LMTR/LMTX integrity | 51 canonical files unchanged; 102 source/frozen strict/recovery outcomes per candidate; cross-Python reports identical; PNG/source invariance | New physical Android-to-Desktop capture on the qualified product builds |
| Linux packaging | Clean-source receipts, 510/452 file hashes, contained links, native ELF audits, private install/retraction, no external Python in PATH | Broad distro ABI qualification: both candidates still include GLIBC2.44 through GTK/tinysparql/SQLite |
| Windows | Native setup/build/installer procedure and reusable offline checks prepared/reviewed | Actual Windows execution; no CI budget available |

All Android tests are host tests, including the SDK36 sandbox under Java21. App/core
compilation remains Java17 and minSdk26. Actual ThermalScreen navigation never opens or
initializes the counted synthetic module; only explicit Connect opens it. Palette/range
preserve its matrix/ROI, and errors/transients/Close clear readings. The future instrumented
locale recipe now enters Measurements/Settings before their controls. It compiles, not runs.

The Desktop fixes preserve refused-finalization ownership/retry and make LMTR controls fit
a 1,000-pixel window instead of the observed 1,179-pixel minimum. No recorder safety,
wire, acknowledgement, memory margin, queue capacity, thermometry or USB sequence changes.
There is no new physical acquisition FPS, Pixel heap/GC or visible latency claim.

## Native and physical boundary

An initial Fedora Home window was genuinely exposed. Subsequent X11 widget tests ran,
but strict whole-case native exposure checks did not pass. Read-only diagnosis identified
hidden windows and `LockedHint=yes` on the current active Wayland session. No unlocking,
security/display setting changes or capture of other applications occurred. Offscreen
success is recorded separately; it is not substituted for the native/operator gate.
The HT-301 VID1514/PID0001 was absent in the host inventory. The unrelated webcam was
not opened. **No ADB or Pixel access occurred**; the owner's explicit availability gate
remains in force. See [the prepared physical procedure](ANDROID_PHYSICAL_PLAN.md).

## Artifact and history provenance

The Desktop code/artifacts are pinned at
`c9a0854743df488b38042e4de77ac0920aa962c5`; later evidence/documentation commits don't
change that tested code. Both private runtimes preserve their original archives with
measured pre/post SHA-256. Their source inventories are stable, not atomic snapshots.
The installed-file rehearsal keeps a user-added sentinel and external local logs while
removing only owned files. This is not a signed public installer or Windows validation.

The original phase4 manifest (`34ae7d832019c32c4ad32c826cf23778aaaa64abc83f1da7fa2722e87b332733`)
and all 1,615 inventoried files were verified before work. Failed harness setup/exposure
attempts and the negative interpreter-portability experiment remain preserved separately.
No canonical corpus is regenerated. No new physical image is acquired; legacy UI inputs
are explicitly fixture-derived with simulated gaps/drops, never presented as a new capture.

The new Android source contains no production Java/Kotlin/camera/core change relative
to the Play tip; additions are host-test launcher/tests, instrumented-recipe routing
and documentation. APKs are compiled, not installed. The unsigned AAB is 7,900,309 bytes,
SHA-256 `3c331a8ed27cd7f4be564ca1e924cfb0819ca67060c1dcee44dedcd3baf24c15`.
All four arm64 native libraries pass static 16 KiB alignment, the APK passes zipalign,
and the AAB config parsed with the official bundletool classes requests PAGE_ALIGNMENT_16K.
No release key, account or store submission is created. Runtime and legal gates remain open.

## Review and integration

See [the exact dependency/conflict proposal](INTEGRATION_PLAN.md). Android UX and Play
contain the same final source fix but different commit ancestry; a dry merge reports a
CHANGELOG conflict that must preserve both histories. No actual merge/rebase occurs.
Desktop packaging includes the workspace and later R5 review ancestry. Main and old PRs
stay unchanged; production R4/R6 is not activated. New branches may be published only
when workflow filters/secrets are checked and no hosted run is triggered.

Owner choices remain: licensing route/copyright authority, supported Linux ABI/plugin
policy and clean-system campaign, native Windows budget/acceptance, product branding/
version/support/signing/privacy details, plus explicit future Pixel availability.
[The license worklist](../DISTRIBUTION_LICENSE_REVIEW.md) records the actual dependencies
without selecting a product license. No artifact is ready for public distribution.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
