# Native Windows R3 gate — pending

This is a reproducible camera-free protocol, **not a Windows result**. Fedora and
WSL evidence do not satisfy this gate. Use the exact pushed draft PR #14 commit,
a supported JDK 17, Python 3 and the normal Gradle dependencies already available
on the Windows host. No Android APK, USB camera, ADB, CMake or live recorder is needed
for the isolated module. Android build/core regression was performed on Fedora.

## Exact corpus and independent readers

From a native PowerShell checkout, with `JAVA_HOME` pointing to JDK 17:

```powershell
$env:PYTHONDONTWRITEBYTECODE = '1'
.\gradlew.bat :lmtr-contract:clean :lmtr-contract:test :lmtr-contract:writeRuntimeClasspath
python -W error -m unittest tools.lmtr_v1.tests.test_reference -v
python tools/check_lmtr_conformance.py --report windows-lmtr-r3-validation.json
git diff --check
```

Use `--offline` for Gradle only when its dependencies are already cached. Do not
substitute fixture-only self-read for independent Kotlin-write/Python-read testing.
The interoperability tool compares all existing canonical files and classifiers,
logical bytes/summaries, exact canonical JSON, feature/version negotiation and
codec/recovery/scan outcomes. It preserves sources and generates only owned temporary
output. `.gitattributes` protects binary files, exact plan/inventory bytes and source
provenance from CRLF conversion. This is preventive configuration, not proof of
native Windows conformance.

Record the tested branch/commit, native Windows version, JDK/Python/zlib versions,
JVM and Python test totals, cross-language results, fixture-manifest SHA-256 and
source immutability. Retain failed runs and exact errors. Do not regenerate the pinned
corpus with a different zlib runtime just to remove a provenance/determinism failure;
reader support does not require identical independently compressed bytes.

## Separate stress/platform checks

`tools/check_lmtr_sparse.py` currently measures Fedora `st_blocks`/`resource` and
creates Unix sparse files. It is **Linux-only** tooling; running it under WSL does
not demonstrate native NTFS sparse offsets. Native Windows >4GiB seek/resource
validation needs a reviewed Windows-specific stress adapter with the same valid
contiguous framing and source-integrity assertions. Do not commit huge files.

ART execution, monotonic/stream-filter seek, exhaustive index-depth/fast-recovery
limits, arbitrary damaged-framing discovery and disposable rebuilt-index cache,
async recorder admission/timers/Stop and backend durability/export are separate
remaining coverage/runtime gates. None is established by a green host suite.
No generic-camera, indefinite-runtime, physical power-loss or calibrated temperature
guarantee follows from this protocol. R3 #8 stays open and PR #14 stays draft.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
