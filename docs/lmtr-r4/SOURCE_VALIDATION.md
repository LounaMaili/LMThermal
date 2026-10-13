# R4 finite source/lifecycle validation

## Scope and provenance

The final host probe is pinned to `abea098e69332f0a16ee779f6181c3af9040947c` and runs 120 seconds
at nominal 25 Hz with a **13 × 7 non-USB synthetic module**. It uses the existing
RecorderEngine, the optional authoritative source hub, guarded lifecycle binding and
the original synthetic producer. This is not real HT-301/Pixel8 throughput, preview
latency, ART/RSS or indefinitely bounded memory evidence. No camera or physical ADB
was used. Recorded matrix/raw/transport bytes are synthetic and remain outside Git.
Only sanitized receipts are committed; this does not extend the 51 canonical fixtures.

[Final stress receipt](source-stress-final.json), [source readback](source-readback-final.json)
and [HT copy-cost receipt](ht-copy-cost-final.json) retain exact verdicts. Absolute host
uptime is omitted from public receipts; relative acknowledgment/timeline intervals
are unchanged. Original local reports and source files are preserved.

## Actual host results

| Measurement | Observed |
|---|---:|
| Acquired/admitted/CHUNK-acknowledged observations | 3000 / 3000 / 3000 |
| Temperature planes / explicitly unavailable observations | 2962 / 38 |
| Preparation / writer / qualified source-loss gaps | 0 / 0 / 0 |
| Separate preview emissions / palette / ROI changes | 1500 / 177 / 231 |
| Physical chunks / checkpoints | 118 / 23 |
| Highest charged preparation queue / writer queue | 65,536 / 262,144 bytes |
| Highest preparation / writer in-flight charge | 65,536 / 65,536 bytes |
| Highest accounted pending CHUNK bytes | 50,272 bytes |
| Admission p50 / p95 / max | 35.450 / 59.953 / 2769.785 µs |
| Stop cutoff/drain/finalization + engine terminal reopen | 198.535 ms |
| Final file bytes | 9,981,123 |
| Original file SHA-256 | `e1a0a230fea25009e2c4b6f0a944b0bab33f08651b47bb7985eb19d8c4285887` |

Fake presentation state changes are deliberately independent of acquisition and
recording. They are not a measured UI FPS or responsiveness assertion. The source
retains original Float32 bits/masks, native/acquisition evidence, context transitions,
session/stream/source sequence and genuine receipt stamps. The JSONL source oracle hash
is `91b8572aa45fa2fa985159425e72f8ab2b31ae0c9edd629e1f6c4380a5ea082b`.
Python compares original receipt/acquisition/device/schedule/source facts and checks
common intake against pre/post callback witnesses. Both witnesses use System.nanoTime
in this synthetic run; that correlation is not inferred between real Android
elapsedRealtimeNanos source receipt and the recorder clock. Future physical factories
must declare/configure those clock domains truthfully. Clock units have known
scale, but actual resolution is explicitly unknown; sensor time/UTC is not inferred.
Queues retain their original 4 MiB capacities. Bounds shown here are charged-queue
counters, not process memory or a proof of full-size HT memory behavior.

The independent Python reader fully checks all 3000 observations against original
payload hash/length/role, context, session and sequence expectations, verifies complete
chronology/integrity and confirms source SHA remains unchanged. The shared engine
probe now passes 14 cases: ten completed, one honestly finalized failed END with a known
source-loss gap, and three recovered prefixes. The mixed-loss prefix retains its actual
CHUNK acknowledgment, original bits and unknown tail without END. All three recovered
cases compare original payload bytes. [Current engine receipt](resumed-engine-interop.json);
[earlier 12-case receipt](continuation-engine-interop.json) remains history.

## HT evidence copy cost

The adapter benchmark uses the **existing sanitized room fixture**; no new HT frame
or fixture output is captured/exported. Thermometry is evaluated once before timing;
`Ht301RecordingAdapter` itself never calls it. Fifty samples after warmup show:

- retained existing source owner: 1,108,992 bytes; conservative source/prepared charge: 3 MiB;
- small deferred offer p50/p95/max: 10.460 / 12.180 / 19.140 µs;
- off-callback evidence freezing/copy/metadata p50/p95/max: 0.770 / 1.124 / 2.760 ms.

This excludes hashing/compression/I/O and is a host cost observation, not Pixel8
performance. No image copy is added to normal complete acquisition; malformed input
is owned/copy-retained only for an active consumer. Expensive evidence work runs on
`lmtr-preparation`, and rejected overload never invokes its materializer.

The exceptional callback-side wrapper copy is measured separately using 224255
**synthetic malformed** transport bytes. Fifty escaping owners verify isolation from
source mutation; p50/p95/max are 85.081 /
90.880 / 240.290 µs.
[Ownership receipt](ownership-copy-cost-final.json). This includes no parser, thermometry,
hash, codec or I/O and is not a physical-device callback measurement.

## Deterministic lifecycle/fault coverage

17 camera-recording JVM tests cover exact HT matrix/native/transport/settings/calibration,
unavailable/malformed frames, source owner immutability, no implicit Start, independent
presentation, partial/temperature-only/preview-only modules, source losses, every close
reason, rotation retention/screen-off cutoff, same-device stale callbacks, actual owner
replacement/Close, nonblocking repeated Stop with delayed preparation/footer, and refused
overlapping reservations. Two latches also prove Close racing a late open cannot revive
recording intake and a broken optional lifetime observer cannot prevent camera release.

The 39 recorder regressions preserve the original assertions and cover: idle/late-preparation sealing,
checkpoint/Stop/admission races, both overload classes, loss-reserve exhaustion, injected
storage exhaustion/uncertain sync/close failures, refusal/memory limits and bounded
read-only prefix recovery. Host tests are finite schedules, not exhaustive race modeling
or backend power-loss certification. A sticky no-END condition survives both failure
orderings, Stop/detach/source error/memory pressure, repeated termination and worker
errors. Only abandoned current/queued evidence is unrepresented: an idle worker
interruption or fully journaled per-item failure can honestly finalize failed END.
Actual ART coverage and failures are in
[ART integration](ART_INTEGRATION.md); these are runtime tests, not physical acquisition.

## Preserved earlier attempts

The first stress finalized its file, then the diagnostic reporter tried to encode a
large expectation inventory within the wire JSON 1 MiB budget and failed `resource_limit`.
The limit was retained; engineering expectations use JSONL outside the wire grammar.
[Independent first-file readback](source-first-readback.json) proves valid full integrity
for 3000 observations and source immutability, but cannot establish original payload
parity because that first source oracle was not written. No lost-observation success
claim is inferred from the reporter failure.

The [earlier repeat](source-stress-prior.json) and its
[Python parity receipt](source-readback-prior.json) remain historical. Its code snapshot
had no exact build SHA receipt and preceded the clock/preview correction; the final
repeat above provides pinned evidence. The earlier run overlapped build work for its
first portion. Reports are preserved rather than replaced with new success numbers.

The pinned owner-era stress/readback/copy reports are preserved as
[source stress](source-stress-owner-checkpoint.json),
[source readback](source-readback-owner-checkpoint.json) and
[copy cost](ht-copy-cost-owner-checkpoint.json), separately from the current final run.
The three-second clock probe had a checker attribute-name error before readback; its
unchanged source was re-read successfully after correcting the helper, not recaptured.
Compilation/reporting/infrastructure failures remain in local logs and Git history.

## Reproduction

```bash
./gradlew -p tools/lmtr-r3-host :camera-recording:writeTestRuntimeClasspath
mkdir -p /tmp/lmtr-r4-source-validation
git rev-parse HEAD > /tmp/lmtr-r4-source-validation/source-sha.txt
java -Xmx256m -cp "$(cat camera-recording/build/test-runtime-classpath.txt)" \
  org.lmthermal.recording.CameraRecordingStressProbe /tmp/lmtr-r4-source-validation/generated 120
java -Xmx256m -cp "$(cat camera-recording/build/test-runtime-classpath.txt)" \
  org.lmthermal.recording.AcquisitionOwnershipCostProbe /tmp/lmtr-r4-source-validation/generated/ownership-copy-cost.json
python3 tools/check_lmtr_r4.py --existing /tmp/lmtr-r4-source-validation/generated \
  --report /tmp/lmtr-r4-source-validation/python-readback.json
```

Use JDK17 and a fresh output directory; do not replace an earlier run. The JSONL oracle
is separate from wire JSON limits; no generated `.lmtr` binaries or multi-megabyte
oracle files are committed. Actual backend acknowledgment times, checkpoints, queue,
in-flight and pending counters are in the private original/sanitized public reports.
The 256 MiB test-JVM heap cap is not a measured heap/RSS plateau.

## Remaining physical integration gates

The source/lifetime hooks compile into the product but have no product recorder
subscriber. Adapter/engine integration is test-only, with explicit Start/Stop and no
recording UI/background service. Full-size real Android bytes, qualified UVC source
loss accounting, HT/Pixel acquisition/FPS versus baseline, preview freshness, clean-process
memory, repeated lifecycle/Stop/error/recovery behavior require explicit field tests.
Native Windows recorder and applicable backend durability gates are separate; R5/R6,
SAF/remote publication and physical power-loss certification are not implemented here.
R4 is incomplete; #9 and draft PR #16 stay open.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
