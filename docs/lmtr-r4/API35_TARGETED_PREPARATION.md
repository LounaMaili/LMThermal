# R4 targeted API35 replay preparation

**LOCAL PREPARATION ONLY.** No branch creation, commit, push, Actions run, emulator,
ADB operation, APK build/install, instrumentation or new recording was performed.
The first-failure engine patch remains a separate reviewed input. This preparation
does not change the engine, workload tests, historical workflow/script, wire, source
qualification, queues, memory margins or assertions. See the [static preparation
receipt](api35-targeted-preparation.json), not a runtime validation certificate.

## Trigger strategy and future authorization

The new `.github/workflows/lmtr-r4-api35-targeted.yml` listens only for `push` to
`codex/r4-api35-first-failure-replay`, also checked by repository/ref/event job guards.
It has no matrix, dispatch event, privileged token, pull_request_target or main change.
Actions are pinned to the same full commits as the historical R4 workflow; checkout
uses the exact event SHA with persisted credentials disabled and contents:read only.

GitHub documents that push workflows can run before being merged into the default
branch, whereas a new workflow_dispatch file must exist there to trigger normally:
[push semantics](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#push),
[dispatch semantics](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch).
The historical R4 push filter selects only `feat/lmtr-r4-recorder-foundation` and the
Windows workflow only its R3 branch. Neither selects the proposed diagnostic branch.
Repository Actions policy, runner availability and branch protection may still prevent
execution; that is a reported external boundary, not permission to bypass controls.

**Do not push the current R4 branch.** The following is a proposed later sequence,
not authorization or a command executed during preparation:

1. Review the engine patch and preparation patch independently, including the source
   checksum list and this fresh-process/cadence acceptance boundary.
2. Explicitly authorize branch creation, a reviewed commit, and exactly one push/run.
3. In this same existing worktree, create `codex/r4-api35-first-failure-replay` from
   its unchanged base `fc5fae1fb5ae3783efbd3300dae4cdd40c6e1ff1`, carrying the reviewed
   local changes. Do not reset, recreate worktrees, merge main or touch R2.
4. Stage only the reviewed engine/preparation file allowlist, not `.local-tools` or raw
   evidence. One atomic diagnostic commit is sufficient; the two review patches remain
   separate. Verify `git show --stat HEAD`, no unexpected paths and a clean tree.
5. Push only `HEAD:refs/heads/codex/r4-api35-first-failure-replay`. The push itself
   starts the single API35 job; do not dispatch or rerun it a second time.
6. Record that commit and the Actions run/attempt, download the uniquely named artifact
   and retain original ZIP/log hashes privately before its 30-day expiration. No merge,
   issue closure, production recorder, hardware access or favorable retry is included.

Every later push/rerun could start a new experiment and needs its own authorization.

After that authorization only, the exact local publication commands are:

```bash
# Start in this reviewed worktree; abort on any unexpected path, hash or base.
test "$(git rev-parse HEAD)" = fc5fae1fb5ae3783efbd3300dae4cdd40c6e1ff1
sha256sum --check docs/lmtr-r4/api35-targeted-reviewed-sources.sha256
git diff --check
git switch -c codex/r4-api35-first-failure-replay
git add -- CHANGELOG.md docs/SPECIFICATION.md docs/lmtr-r4/README.md \
  docs/lmtr-r4/FIRST_FAILURE.md docs/lmtr-r4/first-failure-validation.json \
  lmtr-field-support/src/main/kotlin/org/lmthermal/field/FieldPreflight.kt \
  lmtr-field-support/src/test/kotlin/org/lmthermal/field/FieldFailureDiagnosticTest.kt \
  lmtr-recorder/src/main/kotlin/org/lmthermal/recorder/RecorderEngine.kt \
  lmtr-recorder/src/main/kotlin/org/lmthermal/recorder/RecorderFirstFailure.kt \
  lmtr-recorder/src/test/kotlin/org/lmthermal/recorder/RecorderEngineTest.kt \
  .github/workflows/lmtr-r4-api35-targeted.yml \
  tools/validate_lmtr_r4_api35.py tools/analyze_lmtr_r4_api35.py \
  docs/lmtr-r4/api35-targeted-reviewed-sources.sha256 \
  docs/lmtr-r4/API35_TARGETED_PREPARATION.md \
  docs/lmtr-r4/api35-targeted-preparation.json
git diff --cached --check
git diff --cached --stat
git status --short
# Inspect the exact 16-path allowlist above before committing.
git commit -m "test: Isolate the R4 API35 first-failure diagnostic replay"
git show --stat --oneline HEAD
test -z "$(git status --porcelain)"
git rev-parse HEAD
git push origin HEAD:refs/heads/codex/r4-api35-first-failure-replay
```

These are separate commands with review gates, not a shell script to run unchecked.
Do not reuse an existing diagnostic branch silently; if its name already exists,
stop and review its provenance instead of resetting it. Record the printed commit,
then identify the one push run by that exact `headSha`, branch, workflow and event.
No subsequent dispatch is needed. A nonzero result still requires artifact/log
retrieval, not a retry. Download to a new private directory with the run ID and
attempt in its name; retain original ZIP SHA-256 and GitHub metadata. Then verify
the downloaded `replay/SHA256SUMS` and invoke the offline analyzer into another new
directory, preserving the original CI assessment:

```bash
# Values below designate the newly downloaded evidence, not an old result tree.
(cd "$LMTR_DOWNLOADED_REPLAY" && sha256sum --check SHA256SUMS)
PYTHONDONTWRITEBYTECODE=1 python3 tools/analyze_lmtr_r4_api35.py \
  "$LMTR_DOWNLOADED_REPLAY" "$LMTR_NEW_OFFLINE_ASSESSMENT"
```

The collector already performs this independent Python readback on the hosted
runner. This later invocation verifies the preserved evidence again; it never
writes into the original extracted recording or invents skipped codec results.

## Exact environment and selector

The isolated Ubuntu24.04 hosted job uses JDK17 and the existing API35 x86_64
`google_apis` runner, with the unchanged camera-disabled options and host Gradle
memory settings. No local host infrastructure is provisioned in this preparation.
The script refuses a non-GitHub environment, other repository/ref, dirty/mismatched
checkout or changed reviewed sources before any emulator command. Discovery is only
`adb -e get-serialno`; all subsequent commands use `adb -s <verified emulator serial>`.
It checks qemu=1, SDK35, x86_64 and a nonempty fingerprint. There is no physical/default
ADB fallback, connect, pairing, daemon restart or hardware permission path.

Only APK packaging tasks run:

```bash
./gradlew -p tools/lmtr-r4-art :lmtr-art:assembleDebug :lmtr-art:assembleDebugAndroidTest --console=plain
```

The one instrumentation command is equivalent to:

```bash
adb -s "$LMTR_EMULATOR_SERIAL" shell am instrument -w -r \
  -e class 'org.lmthermal.art.HtMemoryRuntimeTest#fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs' \
  org.lmthermal.art.test/androidx.test.runner.AndroidJUnitRunner
```

There is no connectedDebugAndroidTest (which would uninstall the evidence owner),
other test invocation, suppression of assertions, queue increase or forced GC.
The method still supplies 50 baseline observations and 200 offers/codec. Existing Stop
wait 30s, worker exit wait 5s, heap guard 32 MiB and logical 78 MiB reservation remain.
STORED precedes DEFLATE. The existing method skips subsequent codecs on memory stop
or nonfinalization; a finalizable run with represented losses can still proceed.
That distinction corrects the shorthand in the earlier FIRST_FAILURE proposal without
changing the actual workload or its assertions.

## Source identity and immutable evidence

The 13-entry `api35-targeted-reviewed-sources.sha256` pins all five reviewed modified
code/test inputs plus the unchanged load method/probe, safety/validation sources, wire,
corpus manifest and saved room fixture. Both workflow and runner check it. The runner
also records SHA-256 for every tracked source, commit SHA, raw command argv/exits,
APK hashes, Java/Python/host versions, Android model/release/SDK/ABI/fingerprint,
image package revision and GitHub run/attempt/job metadata. Environment is allowlisted;
no token or whole environment dump is collected.

Each attempt creates a new 700 result root and a new-only `replay` subdirectory.
Files use umask 077; no previous directory is reused or removed. Each test invocation
also retains its existing UUID stress root. The collector retains original tar bytes,
separates binary tar stderr, rejects extraction traversal/links/duplicates/special files,
and verifies extracted inputs against the original archive during analysis. Its host
2 GiB/10000-member extraction and 1 GiB disk-headroom guards are collector resource
limits, not recorder limits; refusal is explicit C and the original tar is retained.

Collection is in a finally path, independently of JUnit success. It attempts full
logcat before/after post-test process freeze, raw private test files, result/stress JSON,
first witness/current sticky loss, terminal reason, original-owner oracle and available
partial LMTR. It does not clear logcat, uninstall, repair or invent END. The existing
runtime errors (including preparation/writer/Stop) remain in raw instrumentation/logs
and the first/terminal fields; no new in-process logger changes measurement load.

The 600-second host instrumentation watchdog is an outer campaign bound, not a change
to recorder Stop/memory/cadence logic. After instrumentation returns, the collector
force-stops only the isolated test package to quiesce files. After a host timeout this
is explicitly a forced process cutoff, never successful Stop; the outcome is C. If
freeze/archive/logcat collection fails, preserve available bytes and explicit errors.
Preparation failure/absent run-result.json does not skip the collector/final assessment.
The Actions upload step uses always(), unique run/attempt naming, no overwrite and
30-day retention. The emulator step has a 70-minute outer bound inside an 80-minute
job, leaving room for the existing per-command watchdogs (including build and analysis)
and upload. These host bounds do not extend the 50/200-observation workload.
Runner loss, job cancellation/disk exhaustion or an upload failure
can still prevent complete retention; no physical-power-loss durability is claimed.

## Independent readback and A/B/C

`tools/analyze_lmtr_r4_api35.py` never uses ADB. It selects only observed codec directories,
never requires output from unexecuted tests/codecs, and calls the unchanged
`check_lmtr_r4_field.check` implementation. It performs full reader checks and original
Float32/mask/native/transport hash comparison where present, context/sequence/view
cross-checks, source immutability, actual verified block codec and qualified gap accounting. Adapter context remains
a cross-check, not an independent calibration interpretation oracle.

`finalized=false` or missing terminal JSON requests recovery, while the unknown terminal
state stays unknown. Missing oracle/capture or failed readback is C, not silently skipped.
Acknowledgments, accepted owners, prepared oracle rows, verified prefix observations
and wire gaps are reported separately. Arithmetic residuals are labeled as residuals,
not invented observations or loss records; a prefix need not include every accepted item.

- **A:** both codecs, 200 accepted/acknowledged each, no preparation/writer drop or refusal,
  status completed, first_failure null, sticky loss false, unchanged workload limits,
  full independent integrity/oracle parity and explicit cadence comparability approval.
- **B:** complete enough collection and successful independent readback characterize a
  first failure, represented overload or recorded memory-guard stop. It remains a failed
  replay gate; B is not a success or a correction of the original API35 failure.
- **C:** bad environment/filter, preparation/collection/readback errors, missing chronology,
  host timeout, unexplained result or unreviewed cadence. Raw instrumentation verdict
  remains separately visible. Exit codes are A=0, B=10, C=20.

The existing criteria require cadence comparable to the matched baseline but specify
**no numeric tolerance for this finite replay**. The analyzer adds none. A clean run is
therefore reported C with `eligible_for_A_after_cadence_review=true` until an explicit
review of its baseline/intervals/lateness/elapsed metrics. Neither JUnit OK nor finalized
nor the probe's completed_no_drops string alone approves A.

An optional later OFFLINE review can qualify A without rerunning the emulator. Supply
`--cadence-review REVIEW.json` to the analyzer, with a fresh output directory and:

```json
{
  "identity": {"source_sha": "EXACT_COMMIT", "run_id": "EXACT_RUN", "run_attempt": "EXACT_ATTEMPT", "archive_sha256": "EXACT_TAR_HASH"},
  "decision": "comparable_to_measured_baseline",
  "reviewer": "owner",
  "rationale": "Explicit assessment of these recorded matched-baseline cadence metrics"
}
```

All other gates must already pass. A review cannot override drops, errors or integrity
failure. No such review or A acceptance has been generated during preparation.

## Reproducibility boundary and next authorization

This is a fresh-process isolated method. The original API35 run followed other tests;
its prior allocations/order/GC/image revision may differ. Captured versions and metrics
allow comparison but a clean targeted pass cannot retroactively explain the historical
failure. New scripts are statically reviewed only: no ART compatibility, runtime capture,
CLI/parser/ADB integration, collection timeout behavior or classifier execution is
claimed validated yet.

The next authorization needs to cover the reviewed diagnostic branch/commit/push and
its single automatic hosted run, bounded emulator-only package cleanup, downloading
and privately preserving all evidence even if red, plus offline independent readback.
It does not cover the physical Pixel/HT-301, main, historical workflow changes, retries,
R5/R6 or removal/relaxation of assertions. Cadence acceptance remains an explicit review.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
