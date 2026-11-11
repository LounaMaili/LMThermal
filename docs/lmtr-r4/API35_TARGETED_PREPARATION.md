# R4 targeted API35 replay preparation

**LOCAL WORKFLOW CORRECTION ONLY — NOT COMMITTED OR EXECUTED.** The first
publication at `ec528c5609b4ae31e75aec3c8d6b9fbc967cfb0d` produced
[run 43052055058, attempt 1](https://github.com/LounaMaili/LMThermal/actions/runs/43052055058).
It remains outcome C: GitHub rejected `runner.temp` in job-level env before creating
any job. No APK build, emulator, instrumentation, codec output or Python readback
occurred. This correction is CI preparation, not new recorder validation.

The [initial static receipt](api35-targeted-preparation.json) remains historical and
unchanged. Its source hashes describe the initial preparation, including the rejected
workflow; YAML/AST checks did not validate GitHub context availability. The new
[context-validation receipt](api35-workflow-context-validation.json) applies only to
this local correction. Original C metadata, report and checksum manifest are preserved
privately without modification. The reviewed engine patch, scripts, workload, wire,
queues, margins, assertions and historical R4 workflow remain unchanged.

## Result path and expression scopes

`runner` is unavailable in `jobs.<job_id>.env`, but is permitted in step `env` and
step `with`. Each of the preflight, collector and upload steps now independently sets:

```yaml
env:
  LMTR_API35_REPORTS: ${{ runner.temp }}/lmtr-r4-api35-${{ github.run_id }}-${{ github.run_attempt }}
```

These three identical values do not use a preflight output or GITHUB_ENV side effect.
The upload retains `if: always()` and resolves its own step env even if preflight or
instrumentation fails. Its `with.path` uses that step's `env.LMTR_API35_REPORTS`.
The root remains outside the checkout on the specified Ubuntu hosted runner, with a
unique run/attempt suffix. The collector still receives its new `replay` subdirectory.
No output path is introduced into the checkout or inherited from a previous run.

Every current expression is checked at its exact workflow key against GitHub's
[context-availability table](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability):
workflow concurrency uses `github`; the job guard uses `github`; the three step env
values use `runner`/`github`; checkout ref and artifact name use `github`; upload path
uses step `env`; the upload guard uses `always()`. The receipt lists exact locations.
Actionlint and ShellCheck are unavailable on PATH and were not installed. Local checks
cover YAML, this finite expression/context inventory, shell syntax, unchanged action
pins and workload, independent path resolution and preservation of historical evidence.
They do not constitute a GitHub parser, action runtime, upload or ART execution result.

## Trigger history and next publication authorization

The separate workflow listens only for `push` to
`codex/r4-api35-first-failure-replay`, also checked by repository/ref/event guards.
It has no matrix, dispatch event, privileged token, pull_request_target or main change.
The historical R4 push filter selects only `feat/lmtr-r4-recorder-foundation`; Windows
selects its R3 branch. Neither selects this diagnostic branch. Actions remain pinned
to the same full commits, with exact-event-SHA checkout and contents:read only.
GitHub's [push](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#push)
and [dispatch](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch)
rules still apply. The original first-publication procedure is retained in
[the immutable ec528c5 document](https://github.com/LounaMaili/LMThermal/blob/ec528c5609b4ae31e75aec3c8d6b9fbc967cfb0d/docs/lmtr-r4/API35_TARGETED_PREPARATION.md);
its branch-creation commands must not be repeated.

After separate review and authorization only, use a normal child commit on the existing
branch, in this diagnostic worktree. Do not amend ec528c5, reset, rebase, recreate a
branch/worktree, force-push or push the original R4 branch. The proposed commands below
have NOT been executed:

```bash
test "$(git branch --show-current)" = codex/r4-api35-first-failure-replay
test "$(git rev-parse HEAD)" = ec528c5609b4ae31e75aec3c8d6b9fbc967cfb0d
sha256sum --check docs/lmtr-r4/api35-targeted-reviewed-sources.sha256
git diff --check
git add -- .github/workflows/lmtr-r4-api35-targeted.yml CHANGELOG.md \
  docs/lmtr-r4/API35_TARGETED_PREPARATION.md \
  docs/lmtr-r4/api35-workflow-context-validation.json
git diff --cached --check
git diff --cached --stat
# Review this exact four-path allowlist before creating the authorized child commit.
git commit -m "ci: Scope API35 diagnostic result paths to steps"
test "$(git rev-parse HEAD^)" = ec528c5609b4ae31e75aec3c8d6b9fbc967cfb0d
git show --stat --oneline HEAD
test -z "$(git status --porcelain)"
git push origin HEAD:refs/heads/codex/r4-api35-first-failure-replay
```

The eventual push itself starts a new run; no dispatch or rerun is needed. Remote Git
must use the authorized host context, preserving normal SSH configuration and host-key
checks. A later failed launch/run must also be preserved without a favorable retry.
Neither the failed C run nor its original commit/evidence is replaced by a new result.
No new commit, push, branch change, GitHub request to launch a run or device access
is authorized by this preparation. Repository policy or runner availability may still
prevent execution; do not bypass those boundaries.

After a separately authorized run, download available evidence to a new private
run/attempt directory. Retain original ZIP bytes, available GitHub digests/metadata and
logs before expiration, including negative inventories or download errors. Verify the
collected manifest before offline analysis into a new directory:

```bash
(cd "$LMTR_DOWNLOADED_REPLAY" && sha256sum --check SHA256SUMS)
PYTHONDONTWRITEBYTECODE=1 python3 tools/analyze_lmtr_r4_api35.py \
  "$LMTR_DOWNLOADED_REPLAY" "$LMTR_NEW_OFFLINE_ASSESSMENT"
```

The prepared collector also requests independent Python readback on the hosted runner.
Neither this later invocation nor a successful readback may replace raw instrumentation
failure, modify extracted source files or fabricate output for unexecuted codecs.

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

The next authorization needs to cover a reviewed child commit on the existing
diagnostic branch, one ordinary push and its single new automatic hosted run, bounded
emulator-only package cleanup, downloading and privately preserving all evidence even
if red, plus offline independent readback.
It does not cover the physical Pixel/HT-301, main, historical workflow changes, retries,
R5/R6 or removal/relaxation of assertions. Cadence acceptance remains an explicit review.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
