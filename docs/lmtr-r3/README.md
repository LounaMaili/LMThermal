# Isolated LMTR v1 R3 contract work

This directory records the standalone implementation and synthetic conformance
work for [accepted LMTR v1](../LMTR_WIRE_V1.md), acceptance commit
`8c343cd6cf1ca11eff4f3bbaa5c2b10dfbbf645f`, integrated by PR #13 at
`fe029363062cdf54d6cefa9d4f24265e80188091`. R1 #6 is closed; R3 #8 remains open.
No production Android or Desktop recorder/importer is implemented here.

- [Progress and remaining gates](IMPLEMENTATION_PLAN.md)
- [Canonical synthetic corpus](CORPUS.md)
- [Independent Python tools](../../tools/lmtr_v1/README.md)
- `lmtr-contract/`: pure Kotlin/JVM library and structured CLI, no Android dependency.

## Reproduce on a host

Use JDK 17 and the repository's existing Gradle cache/toolchain. Set `JAVA_HOME`,
`GRADLE_USER_HOME` and `ANDROID_HOME` to your local configured paths; do not commit
machine paths. No APK installation, ADB, camera or thermometry calculation occurs.

```bash
./gradlew --offline :lmtr-contract:test :lmtr-contract:writeRuntimeClasspath
PYTHONDONTWRITEBYTECODE=1 python3 -W error -m unittest tools.lmtr_v1.tests.test_reference -v
PYTHONDONTWRITEBYTECODE=1 python3 tools/generate_lmtr_corpus.py --check
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_conformance.py \
  --report docs/lmtr-r3/linux-interop-validation.json
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_sparse.py \
  --java "$JAVA_HOME/bin/java" \
  --classpath "$(cat lmtr-contract/build/runtime-classpath.txt)" \
  --report docs/lmtr-r3/linux-sparse-validation.json
./gradlew --offline :core:test :app:assembleDebug :app:lintDebug
PYTHONDONTWRITEBYTECODE=1 python3 tools/check_lmtr_acceptance.py
git diff --check
```

The optional sparse `--full` scan independently verifies every Python payload; its
memory/result scope is separate from selected access. Huge stress sources are created
in owned temporary directories and removed; only small reports enter Git. Regeneration
pins Python/zlib versions and reference-source hashes in the corpus manifest.

Kotlin CLI (arguments separated by `|` for this Gradle task):

```bash
./gradlew --offline :lmtr-contract:lmtr -PlmtrArgs='check|example.lmtr|--full'
./gradlew --offline :lmtr-contract:lmtr -PlmtrArgs='check|example.lmtr|--entry|1'
./gradlew --offline :lmtr-contract:lmtr -PlmtrArgs='check|incomplete.lmtr|--recover|--full'
```

## Honest scope

`opened_records`, `selected_chunks`, `full` and `full_verified_prefix` are explicit
reader integrity scopes. Declared END status is not a producer close acknowledgement.
Source reads/recovery are immutable. Writers own synthetic non-wire plans and publish
new files exclusively; they do not advertise a production async recorder's timer,
queue, memory admission, filesystem/provider or Stop guarantees.

Required features/codecs unsupported by these tools reject, never turn into frame
gaps. Baseline STORED and bounded RFC1950 DEFLATE are supported; Zstd remains reserved.
Unknown optional JSON remains exact semantic data. Original binary payloads, valid
negative zero, invalid positive-zero filler and mask authority remain distinct.

General salvage/rebuilt-index scanning, full platform/backend conformance, real
native Windows and ART execution remain separate gates. Local reporting/clock/cache
budgets are disclosed implementation limits, not changes to accepted format limits.
R3 is not declared complete; R4/R5/R6/R7 are not started.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
