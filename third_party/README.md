# Native dependency provenance

Source snapshots, reviewed before adoption on 2026-10-02:

- libusb 1.0.29, tag `v1.0.29`, commit `15a7ebb4d426c5ce196684347d2b7cafad862626`.
  Source: https://github.com/libusb/libusb . LGPL-2.1-or-later. Included `libusb/`,
  `android/` and `COPYING`, unchanged. Android CMake compiles the upstream Android.mk source list.
- libuvc 0.0.8 development snapshot, commit `d07de4fa23905ee8cac06f5d24b2a03d42a3b363`.
  Source: https://github.com/libuvc/libuvc . BSD-3-Clause. Included `src/`, `include/`
  and `LICENSE.txt`, unchanged. We use the upstream `uvc_wrap` fd bridge and raw frame
  callback. No APK code or manufacturer binary is included.

Both compile as separate shared libraries. Their license texts are also included
in APK assets. Preserve notices and ship corresponding libusb source/build instructions
with binary distributions; this repository supplies the exact source and CMake recipe.
Recipients can rebuild/replace the shared libraries and re-sign the app. Do not impose
terms preventing reverse engineering of library modifications. Project-authored code's
release license remains an owner decision (the historical repository license was undecided).
This foundation debug APK is not a release/distribution policy decision.

AndroidX, Compose, Android Gradle Plugin: Apache-2.0. Kotlin and kotlinx.coroutines:
Apache-2.0. Gradle wrapper/runtime: Apache-2.0. JUnit 4.13.2: EPL-1.0 (test only).
Pinned versions are in the Gradle files. Android SDK/NDK build tools use Google's SDK
license and are local build tools, not vendored application source. The packaged shared
NDK C++ runtime is under the LLVM license; its notice is included in APK assets.

Upstream helper headers (e.g. libuvc's utlist) retain their embedded copyright/license
notices. libusb's per-file notices and android/ recipe are preserved as well.
