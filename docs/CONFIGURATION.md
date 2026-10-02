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
