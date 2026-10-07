# R2 predeclared test plan — 2026-10-07

**Disposable feasibility work; all bytes are noncanonical R2 artifacts.**
Tracking: [R2 #7](https://github.com/LounaMaili/LMThermal/issues/7), feeding
[R1 #6](https://github.com/LounaMaili/LMThermal/issues/6) for R1b. The authoritative
[gates](../RECORDING_R2_VALIDATION.md) and [R1a invariants](../LMTR_FORMAT_CANDIDATE.md)
remain unchanged. This plan is committed before substantial measurements.

## Revision and environment

- Plan/implementation base: `5174b923bcc2011228d15d648f2766e941d8684e`.
- Branch: `test/recording-r2-feasibility`. No executable prototype exists at this
  first plan commit. Commit the executable revision before substantial runs and
  put its exact Git SHA and plan revision in every aggregate report.
- Verified device: Pixel 8, Android 17 / API 37, arm64-v8a. Wireless ADB uses the
  existing pairing and the currently discovered endpoint; no endpoint/serial is
  committed. SDK/target and the established application remain at API 35.
- Real source: HT-301 USB 1514:0001, complete 224256-byte transport, unchanged
  native 384×288 coordinates and validated explicit radiometric session. Camera
  permission/physical connection and a ready current measurement are prerequisites.
- Host: existing repository JDK 17/SDK/NDK/Gradle and independent Python validator.
  Desktop repository is read-only. Real Windows availability is checked separately;
  prepared commands/artifacts alone cannot pass Windows parity.

## Isolation and invariant checks

Prototype Kotlin code is a separate test/tool module, used only by Android
instrumentation. Codec JNI, if used, belongs only to the test APK. No recording
action or production dependency is added. The existing application, transport,
session, thermometry and still serializers remain unchanged.

Use coherent owned current full Celsius/mask/native/context evidence, never
recompute temperatures to serialize them. State/observation loss, source-invalid
or held frames and writer overload are separately reported; none acquire previous
Celsius. A latest-state observation harness must disclose any conflation loss and
cannot claim every native callback was retained merely from its FPS counter.

## Workloads and duration

| Test | Predeclared workload |
|---|---|
| Real ready baseline | 20 s warmup, 120 s observed ready stream, no prototype writer |
| Sustained profile/codec | 20 s warmup then 120 s for each Analysis, Native, Full / Research × STORED, DEFLATE-1, and Zstd-3 if deployed |
| Scene | Operator-confirmed moving hand/object against cooler background where practical; ordinary source variation also counted. No assumed physical hand temperature |
| Exact codec comparison | Same bounded owned source-byte sample for all codecs, one-frame and up-to-25-frame role-major groups; one warmup and five measured passes |
| Thermal/power | Record run order, battery/current/temperature and thermal status when accessible; baseline/profile measurements use the same instrumentation. Lack of power evidence is explicit |
| Chunk edge cases | Low-rate synthetic source, short Stop chunk, explicit gaps, partial/zero validity, context changes and large singleton under hard limits |

Callbacks around 25 FPS do not promise 25 valid measurements. If a source becomes
unready, stop or report that interval rather than hiding it. An interrupted planned
duration is incomplete and may be repeated with its reason recorded, not relabeled
as a sustained pass. No artificial unsafe USB fault or physical power cycling.

## Candidate resources, chunking and framing

Engineering targets, **not frozen contract constants**:

- Seal at earliest observed 1 s, 32 entries or 16 MiB uncompressed physical data
  including metadata; metadata closure can seal earlier. A bounded larger frame
  forms a singleton; no chunk over the draft 64 MiB aggregate hard limit.
- Record ceiling 65 MiB; metadata 1 MiB/depth 32/65536 values; page/checkpoint/END
  512 KiB; index fanout 256/depth 8; checked signed-Long-range offsets/counters.
- Handoff at most 4 MiB, bounded chunks and codec scratch; ordinary HT writer
  aims at at most 64 MiB incremental memory over its matched ready baseline.
- Forward-only HEADER, CHUNK, INDEX_PAGE, CHECKPOINT and END; bounded header/footer,
  CRC framing checks, SHA-256 stored/logical integrity, explicit commit footer and
  sync barriers. Exact prototype byte definitions are recorded in a proposal annex.
- At most 32 chunks between checkpoints; paged random access with bounded cache,
  no whole-recording rewrite or whole-index allocation on ordinary opening.

Profiles physically retain:

- Analysis: exact full Float32 Celsius, mask when present, interpretation context,
  truthful timeline/provenance/gaps.
- Native: Analysis plus independently owned exact native samples.
- HT Full / Research: Analysis plus 224256-byte acquisition, with a same-frame,
  same-chunk native view at offset 0/length 221184, u16le `[288,384]`. No second
  physical native plane. Compare the view to separately owned native bytes.

STORED and zlib-wrapped DEFLATE-1 are baseline candidates. If Zstd-3 is deployed,
pin the reference source/version/license/hash, test-only ABI/APK cost, independent
frames, no dictionaries and decoder window at most 8 MiB. Adoption remains an R1b
decision and requires its actual Android and Linux/Windows evidence.

## Metrics and predeclared pass/refusal rules

Per run collect acquisition callbacks/interval distribution, delivered source
states, accepted measurements, source-held/invalid/transient counts, conflation/
replacement losses, writer drops, uncompressed/stored/framing bytes, codec wall/CPU,
writer throughput, queue maximum/depth/backlog trend, copies/allocations where
measurable, Java heap/native/RSS, main-thread heartbeat/response, thermal/power and
existing thermometry wall diagnostics. CPU attribution limitations are stated;
whole-process CPU is not mislabeled as isolated thermometry CPU.

- Acquisition rate must remain within 5% of the matched measured ready baseline;
  all variation/loss remains visible. Report actual sustained rates, not nominal FPS.
- No unbounded queue/memory growth; enforce queue/chunk/record/codec bounds. Zero
  writer overload in controlled steady-input runs is required to recommend that
  configuration at its tested source rate. A lower sustainable rate is a measured
  limitation, never a hidden profile downgrade.
- Ordinary HT incremental recorder memory target is 64 MiB; report peak and steady
  values and distinguish managed heap/native/RSS and measurement uncertainty.
- Main-thread heartbeat target p99 ≤100 ms with no writer-caused >500 ms stall;
  operator confirms usable preview. Instrumentation overhead is disclosed.
- Every materialized/logical byte hash and original Float32 bit must agree after
  roundtrip; contexts, masks, view geometry and gaps agree across validators.
- No pending/incomplete-tail frame is counted committed/durable. Index damage may
  recover prior valid chunks; promised payload corruption is an integrity error.

Threshold revisions need a committed explanation and new plan revision before
affected reruns; failed old results are retained. Gates cannot be passed by hiding
source loss, dropping evidence, relaxing bounds or changing thermometry.

## Fault/adversarial matrix

Exercise at multiple metadata/payload/footer boundaries: actual prototype process
kill during body, before footer, after commit/before checkpoint and checkpoint/
before END; truncated body/footer/checkpoint/index; missing END; damaged final index;
committed payload/hash corruption; deterministic ENOSPC versus generic refusal;
short write, sync/close/readback failure; cancellation; graceful Stop and normal
operator USB detach. Preserve source hashes and compare recovered commits to the
test journal. No invented tail count/time or automatic in-place resume.

Reject negative/overflowing lengths, excessive dimensions, expansion/windows,
unknown required codec/feature, invalid ordinals/types/context references, malformed
UTF-8/JSON/duplicate keys/nonfinite values/depth, bad index ranges/cycles, all illegal
view forms, parent/logical hash corruption and shape/dtype/endian disagreement.
Also test a synthetic alternate geometry. Bounds precede allocation/exposure.

Synthetic sparse/generated large-offset tests exercise >4 GiB and many pages
without committing a multi-GB fixture. Kotlin and Python checked arithmetic,
lazy seeks and actual bounded allocations are measured, not inferred from limits.

## Backend/publication matrix

| Backend/test | Required proof |
|---|---|
| App-private local file | Forward append, actual StatFs capacity, commit barriers, sync/close, read-only reopen/recovery and access without a second full copy |
| Read-only no-copy access | Existing narrow provider/test-only access where possible; no mutation, lifetime/permissions and uninstall/data-clear/backup limits |
| Optional independent export | Source unchanged; success only after suitable close/readback; cancel/refusal/short write/failed close/mismatch fail truthfully |
| Selected local SAF | Provider class, descriptor mode, capacity knowledge, seek/append/reopen/sync/close/readback/permission-loss/cancel/failure; no all-provider inference |
| Storage policy state | Known insufficient startup+reserve blocks with reason; unknown capacity warns and permits Start; compact normal status, low warning, refusal stops and preserves prior commits |

Software interruption tests do not certify physical storage power-loss durability.
An app-owned URI is access, not a backup. Remote/pipe-only providers get no untested
durable-recording claim. A bounded spool cannot restore bytes lost by a provider.

## Evidence and exit

Keep private live bytes, method traces, raw ADB/log output, hardware serials and
paths in ignored local validation storage. Commit aggregate sanitized JSON/reports
with exact plan/prototype revisions, metric definitions, pass/fail/pending status
and limitations. Compact deterministic synthetic Android-written packets are
clearly **noncanonical R2 test artifacts**, not R3 or LMTR v1 release fixtures.
Record hashes, generation provenance and source immutability for Linux/real Windows.

Return throughput/codec, framing/recovery, view/hostile, backend/SAF and R1b proposal
reports; update the candidate with evidence links without freezing it. R2 #7 stays
open for any incomplete required baseline gate, especially real Windows parity.
R1 #6 stays open for owner R1b review; R3 is not started.

Regression commands: `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`,
`:app:lintDebug`, `:app:connectedDebugAndroidTest`, every separate R2 module test,
Python localization/boundary checks and `git diff --check`. Record existing lint
warnings separately. Native-equivalent temperatures; absolute physical accuracy
not yet independently validated.

## Instrumentation revision before the second sustained attempt

The first sustained prototype uses existing still-export freeze/JSON code for every
recorded frame. It reported writer overload and the operator reported severe image
latency while controls remained responsive. Preserve that attempt, including all
completed runs and partial tails; it does not pass the throughput/responsiveness gate.

Before rerunning, the disposable harness now copies exact already-owned matrix/native/
transport directly and constructs the **same complete existing HT metadata mapping**.
A test compares semantic JSON equality with the existing export evidence. The R2-only
bounded JSON fork caches its numeric token grammar; the production still serializer
is unchanged. Fixed ASCII hash spelling avoids per-byte Formatter allocations; an
already completed validation scan is not repeated, and native-view equality is
checked without allocating another copied plane. No checks, values/calibration/trace
fields, profiles, frames or gaps are removed.
The standalone fair codec study moves after the sustained profiles to avoid its
native allocator caches confounding memory against the ready baseline.

Warmup/duration, chunk policy, 4 MiB queue, 64 MiB incremental-memory target, 5% source
FPS threshold, zero-overload requirement and operator responsiveness gate are
**unchanged**. Commit this executable/plan revision before reruns. Record both attempts.
The first attempt's good UI heartbeat cannot override the operator's image-latency
failure; render timings and completed-frame age must be considered separately.
The repeat also samples completed presentation age and counts observed completed
renders independently of the main-thread heartbeat.

## Baseline interruption handling

The second attempt's initial baseline included a normal BACKGROUND release and
explicit reopen/initialize. It is retained as interrupted and excluded from matched
whole-run throughput comparison. Keep the subsequent complete profile observations;
repeat the 20-second warmup/120-second baseline alone after the matrix, using a fresh
explicit ready session. This post-matrix baseline is labeled as such (run order,
thermal/memory differences remain disclosed), never relabeled as the original
pre-profile baseline. No pass threshold or profile duration changes.
