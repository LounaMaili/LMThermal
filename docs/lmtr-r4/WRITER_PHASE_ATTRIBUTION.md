# Host writer boundaries after the first physical sequences

Status: software attribution only, from `db0de1ddd1d92370829937b6ef3cf6feb2d6489c`.
No ADB discovery, phone/camera, APK installation, emulator/ART or Actions occurs.
The [previous ownership/canonical-size candidates](HOST_MEMORY_WRITER.md) remain the
production-source checkpoint. **All 89 tracked `src/main` files are byte-identical
to it**: this phase adds a host-only test source set/probe and documentation, not
another speculative writer optimization. [Sanitized receipt](writer-phase-attribution-validation.json).

## Questions and measured boundaries

The physical run acquired near 25 FPS but retained 45/105 positions, journaled 60
losses and stopped on RSS after 4.309 s. Prior seal/codec/append distributions were
inclusive; subtracting percentiles could not identify exclusive components.

`HostWriterPhaseProbe` reads the unchanged private file, selects the first largest
decoded CHUNK (four Full observations, 2,666,496 physical decoded bytes), reconstructs
all exact immutable source payloads and recomputes statistics through the ordinary
encoder. It re-encodes both codecs without reducing evidence. For DEFLATE, all twelve
generated bodies (four warmups/eight measured) are **exactly the original body**.
Python independently checks all 24 newly written prefixes against original logical
Float32/native/transport hashes. These intentionally lack END, have an unknown tail
and are recovery inputs, **not** recorder Stop/failure/throughput outcomes.

The single predeclared host process uses JDK 17.0.20.1+1 HotSpot, `-Xms64m -Xmx256m`,
four warmups/eight samples per codec, STORED then DEFLATE. No GC, queue change or
favorable retry occurs. Existing preparation/select costs remain separately measured
in the prior paired campaign; reconstruction here is setup, not HT acquisition/Compose.

| Boundary, median ms | STORED | DEFLATE-1 | Scope |
|---|---:|---:|---|
| Encode, including its first full validation | 23.770 | 58.046 | Metadata/cache/hash/grouping/codec/body and full validation, inclusive |
| Standalone full validation A | 15.821 | 22.148 | Independent complete check of the same body |
| Standalone full validation B | 15.959 | 22.517 | Another complete check; no cached validation result |
| AppendSession entire call | 26.789 | 28.549 | Actual second validation, journal, footer-sync backend, ACK and index work |
| Append start → backend entry | 16.257 | 22.385 | Actual second validation plus journal checks and measurement overhead |
| Backend entry → synced return | 10.358 | 5.962 | Framing/hash/write/footer synchronization; not filesystem-only |
| Synced return → ACK callback | 0.137 | 0.083 | Summary publication before source ACK |
| ACK callback → append return | 0.057 | 0.043 | Index-frontier bookkeeping; not checkpoint/END finalization |
| Sum of the two encoder codec-call times | 0.002 | 29.382 | Nested inside encode, excludes concatenation and later validations |

Append subintervals are sequential on the one calling thread and their raw row
timestamps can be compared. **Medians are not additive**. The two standalone checks
are extra diagnostics: a probe iteration executes four full validations overall
(encode +two standalone +append); ordinary R4 still executes two. Do not sum all four
as a pipeline cost, or subtract a separately timed check from encode as an exact
construction cost. No internal first-validation-only timer was added to production.
The physical codec p95 was per block, whereas the last table row sums two calls;
different percentile/unit/platform values cannot be directly compared.

The backend callback checks that footer-synchronized return precedes ACK in every
case. It wraps the existing `LocalRecorderBackend`, changes no ACK condition and
does not append a repair END. The current ACK phase is small on this filesystem;
it does not prove Android F2FS/ART/normal-preview behavior or power-loss certification.
There is no concurrent producer/backlog or capture-rate claim in this standalone test.
Historical actual losses, aged-gap tests and 32/33 reserve-boundary tests stay separate.

## Allocations, retained owners and RSS

| Current-thread allocated B per operation | STORED | DEFLATE-1 |
|---|---:|---:|
| Encoder, first validation included | 10,029,840 | 12,677,144 |
| Each standalone full validation | 4,087,096 | 6,745,512 |
| Actual complete append call | 4,272,864 | 6,927,968 |
| Backend framing/sync interval | 46,416 | 41,952 |

The decoded DEFLATE groups require independent output arrays in each complete
boundary; STORED checks private input windows directly. The approximately 2.66 MB
per-validation allocation difference is consistent with those decoded owners,
not proof of an equal retained heap or RSS increase. Reusing decoded blocks/results
between boundaries would change independence/lifetime assumptions and is **not done**.

Host point samples range around 238–246 MB RSS while occupied Java varies about
14–44 MB; setup owners, temporary garbage, committed/touched heap, JVM/native/code
and allocator state coexist. These are not interval peaks, a retained-root census,
Android category attribution or a Pixel bound. The probe deliberately retains its
one setup source/CHUNK and prepared payloads across cases; its lifetime graph is not
the real acquisition/presentation pipeline. Natural GC and sample order confound
before/after points. OS reads, source/file hashes, metadata reports and setup are
outside measured phase windows but still affect the whole process.

The empty clock/counter closure median is 210 ns. That quantifies only this host's
empty harness call, not all callback overhead. Timed rows also include a bounded
number of allocation/CPU/monotonic reads and small wrapper objects; the probe retains
24 scalar reports, not payload history. Four warmups/eight measured output files
per codec are finite and private. There is no new 512 KiB production aggregator,
cross-thread synchronization, Throwable history, source clock or deadline.

## Engineering decision

The previous measured copy/JSON changes remain justified and byte-preserving. The
new attribution does **not** justify weakening validation, changing cadence, enlarging
queues/journal or reducing 78+32 MiB. Host append service and physical p95 differ
substantially, but ART scheduling/GC, actual file sync, Compose and source preparation
cannot be separated retrospectively from five physical samples.

No further production micro-optimization is adopted merely because a host median is
lower. Next useful runtime evidence is matched normal-preview versus recording with
bounded existing phase/GC/owner counters and actual backend timing. The previously
reviewed fine instrumentation remains separate; its memory/observer cost must be
accounted for before any future use. Native/graphics/root attribution is still missing.
Profile-aware preparation remains a possible separately tested design: the current
adapter freezes available roles before selecting Analysis/Native/Full. Native saves
only 3,072 physical bytes/frame against a valid same-observation Full view; changing
profile is neither implicit fallback nor a proven RSS fix.

## Validation and reproduction

428 JVM module executions pass (core 234, contract 74, recorder 53, bridge 20,
field support 47), with shared test-source overlap. Existing overload, delayed ACK,
Stop races, corruption, allocation refusal and sticky-loss recovery assertions stay.
64 Python unit tests and 29 hostile cross-language combinations pass. Python verifies
all 24 prefix files, original binary hashes and source immutability. The accepted
wire and 51 canonical bytes are unchanged. No app source or installed APK is touched.

```sh
./gradlew --offline -p tools/lmtr-r3-host :lmtr-recorder:writeTestRuntimeClasspath
java -Xms64m -Xmx256m -cp "$(cat lmtr-recorder/build/test-runtime-classpath.txt)" \
  org.lmthermal.recorder.HostWriterPhaseProbe PRIVATE_EXISTING_LMTR NEW_PRIVATE_DIRECTORY
```

Freeze/hash the compiled classpath first, retain the plan/tool/source hashes and
all warmup/failed outputs. New files are host engineering evidence, never canonical
R3 bytes. Current report/classpath/originals remain in a private persistent directory.

## Proposed next physical gate — preparation only

Candidate branch **`codex/r4-host-memory-writer`**, exact production-source commit
**`db0de1ddd1d92370829937b6ef3cf6feb2d6489c`**. It includes direct Float32 ownership,
fresh immutable JSON parsing/scalar role cache and per-pass canonical-size reuse;
the new host phase probe adds no phone code. Desktop uses the separately qualified
offline candidate for independent readback, never thermometry recomputation.

Before any install, obtain fresh explicit Pixel/HT availability and scene permission,
then rerun the relevant source/build/lint checks and pin the built/installed APK SHA.
Confirm normal permissions, one actual changing READY session and normal Compose
presentation. Record exact battery/thermal/storage/system/heap/RSS admission once,
with unchanged 78+32 MiB, 4 MiB queues/preferred CHUNK and 1 MiB journal.

One Start only, exactly ten requested seconds, explicit Full/DEFLATE-1. No forced GC,
favorable snapshot, preview suppression, rate change, replug retry or profile downgrade.
Keep all existing safety stops. Record acquisition and saved cadence separately,
eligible/admitted/ACKed observations, prep/writer/source losses, gap ranges, queue
peaks, codec/seal/append/Stop/drain and GC/memory samples. Preserve every original
and error/terminal field, including unavailable first-fatal diagnostics as unknown.

Compare with the first one-observation diagnostic failure and second 45-observation/
60-gap RSS interruption without erasing either. Independently assess (1) requested
duration, (2) safety/memory behavior, (3) loss-free throughput, (4) honest loss accounting,
(5) finalized/recoverable integrity, and (6) useful normal preview. A valid interrupted
END can pass integrity while failing duration/throughput. Python/Desktop must verify
all original roles, timeline/point/ROI/palettes and unchanged source hashes.

Separate Analysis/Native experiments require explicit profile/run authorization and
disclosure of absent original data; use the [profile table](HOST_MEMORY_WRITER.md).
**No 30–60 s run is ready or authorized.** First resolve the short-run duration/RSS
boundary with exact accounting and matched resource evidence; a host allocation or
sync result cannot qualify a longer physical recording. Nothing here authorizes
future hardware access by merely making the phone available.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
