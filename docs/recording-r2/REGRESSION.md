# R2 final regression and scope check

Final checks on 2026-10-07, using the repository JDK/Gradle environment:

| Check | Result |
|---|---|
| `:core:test` | 232 passed |
| `:app:testDebugUnitTest` | 10 passed |
| `:recording-r2:test` | 20 passed, including metadata budget and failed-close regressions |
| `:app:assembleDebug`, `:app:assembleDebugAndroidTest` | Passed |
| `:app:lintDebug` | 0 errors; 11 existing warnings |
| `:app:connectedDebugAndroidTest`, Pixel 8 Android 17 | 44 discovered: 40 passed, 4 explicit opt-in experiments skipped; 0 failures |
| Explicit Pixel R2 packet + metadata classes | 3 passed; same 15 file hashes as the published packet |
| Localization checker | 110 default / 110 French keys passed |
| Shared camera-boundary checker | 25 shared sources passed |
| Independent Python hostile-reader tests | 7 passed |
| Independent Linux Android-written packet | All 15 cases passed; exact bytes and immutable sources |
| Actual child-process kill matrix | All 8 cases passed |
| `git diff --check` | Passed |

The four skipped general-suite experiments are the live sustained test, two codec
packet tests and isolated thermometry cost test. They require explicit opt-in and
were executed separately. The live test methods finished, but both recording
matrices **failed the performance acceptance gate**; JUnit completion is not R2
acceptance. The final metadata admission correction has not passed a new live run.

Existing lint warnings: eight GradleDependency, one ChromeOsAbiSupport, one
DataExtractionRules and one MissingApplicationIcon. No new lint error.

Before connected-test uninstall/data cleanup, all private live evidence was copied
to ignored local storage: 30 files, 9,299,060,736 archive bytes. Archive bounds and
all three benchmark reports were verified readable. The private scene archive is
not committed. Sanitized SAF results were also preserved before cleanup.

Compared with base `5174b923bcc2011228d15d648f2766e941d8684e`, production
`app/src/main`, `core/src/main` and Accepted `docs/LMTX_FORMAT_V1.md` have no diff.
Disposable code is confined to `recording-r2`, tooling and Android test sources;
the app APK contains no R2/Zstd library. Desktop remained read-only and clean.

Real Windows is pending. No R1b acceptance, canonical R3 fixture, production
recorder, camera/session/thermometry change or independent calibration claim.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
