# Android build configuration

Install JDK 17 and Android SDK packages:

- `platforms;android-35`
- `build-tools;35.0.0`
- `ndk;28.0.13004108`
- `cmake;3.22.1`

Set `JAVA_HOME` to the JDK, and set `ANDROID_HOME` or create ignored `local.properties`
with `sdk.dir=/absolute/path/to/sdk`. The Gradle wrapper downloads checksum-pinned
Gradle 8.11.1. Build dependencies come from Google Maven/Maven Central/plugin portal.
No system Gradle install is required. Native sources are vendored and build offline
after SDK/Gradle/Maven dependencies are cached.

For this development run the toolchain was provisioned under ignored `.local-tools/`
because no Android SDK/JDK 17 was available. It is not a committed machine requirement.
Example for that local installation:

```bash
export JAVA_HOME="$PWD/.local-tools/jdk-17.0.20.1+1"
export GRADLE_USER_HOME="$PWD/.local-tools/gradle-home"
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Do not commit local SDK paths, signing keys, debug keystores, ADB pairing keys,
unsanitized debug payloads or build outputs. Debug signing uses normal Android tooling.
The feature branch contains source/tests and wrapper, not a signed release artifact.

## Thermometry parity instrumentation

AndroidX Test runner 1.6.2 and JUnit extension 1.2.1 are `androidTestImplementation`
dependencies only. `AndroidJUnitRunner` executes on the real paired phone; test APK
assets reuse `core/src/test/resources` and are excluded from the product APK.
The parity tests never open USB or change camera/session state.

```bash
./gradlew :app:assembleDebugAndroidTest
adb -s <paired-wireless-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <paired-wireless-serial> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <paired-wireless-serial> shell am instrument -w org.lmthermal.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use the JDK/SDK configuration above. Kotlin compiler `.kotlin/` state is ignored.
Goldens can be regenerated with the sibling Desktop virtual environment as documented
in ANDROID_THERMOMETRY.md; Python remains a development oracle, not an app dependency.
