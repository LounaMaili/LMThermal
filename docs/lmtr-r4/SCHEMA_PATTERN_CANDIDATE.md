# Bounded fixed-schema pattern candidate

Status: controlled candidate, not a lossless-throughput or physical acceptance.
Reference:27f09249bd3867b20ddd83eafd81ec121ee09d4f, runtime snapshot69ea559.

## One change and retained invariants

`LmtrSchema` precompiles the seven unique regex patterns in the immutable pinned
registry once. No value derived from file/source/optional metadata becomes a cache
key. Each independent validation still parses its own data, checks every shape,
pattern, body/block/logical hash, numerical payload and closure. Matchers remain
call-local; Regex/Pattern objects are immutable. Lazy initialization is synchronized,
publication is thread-safe, later lookup has no growing map or new recorder lock.
No digest is shared across the two complete validations. No wire/schema/corpus,
queue/admission/memory78+32MiB, clocks, ACK, losses, END or USB change occurs.

The steady cost is seven retained pattern graphs and a small immutable map for the
class-loader lifetime. Exact ART retained bytes remain unmeasured. Initialization
temporaries and call-local matchers still allocate. No payload reference is retained.
This is not a reduction of reserved memory or proof that physical admission is safe.

## Predeclared host measurements

Same existing synthetic Full-STORED CHUNK body,2686996 bytes,
SHA256 `7a08c0ecb9954b51cda3ad61ffff88a01ac4f9bfbc975de0f6c26e73ff8c5fbf`.
One fresh HotSpot17 process per revision,256MiB heap,20 warmups,100 COMPLETE
synchronous validations, no forced GC, fixed operation boundaries. Thread-local
allocated bytes are not retained heap/RSS or whole-process allocations.

| Metric | Reference | Candidate |
|---|---:|---:|
| Sum of100 validations ns |1556820622|1740550461|
| p50 ns |14562980|15923394|
| p95 ns |18897367|22414919|
| Current-thread allocated bytes |397012688|338950216|

Allocation volume fell14.6%; elapsed time increased11.8% in these unpaired host
samples. Do not call this a demonstrated speedup or choose a favorable rerun.
A fixed hosted comparison is justified because prior ART complete validation costs
and allocation/GC pressure differ substantially from HotSpot. Retain both outcomes.

## Predeclared hosted run: reference / candidate / candidate / reference

One Ubuntu24.04 runner and API35 x86_64 google_apis emulator with cameras disabled.
Both versions use fine metrics ON, identical selected test/workload sources,50 baseline
and200 offered Full observations per codec,40ms schedule and unchanged assertions.
The push trigger additionally requires a change to the explicit run-request JSON;
documentation-only follow-up pushes cannot start another comparison.
Each arm gets a fresh cleared disposable target process and unique evidence. The
reference checkout is pinned27f0924; the event SHA is always the candidate SHA and
is recorded separately from the actual tested checkout SHA. Every tracked source and
APK gets a digest. A runtime/load source drift guard aborts rather than comparing
changed loads. All four arms run once, failures retained, no retry/favorable selection.

This is a finite matched-environment order-balanced comparison, not randomized large-N
causal proof. Host/emulator/filesystem/JIT/GC drift and fixed STORED→DEFLATE order remain.
Retained measurements, represented preparation/writer losses, producer baseline,
latency, validation/backend/Stop, heap/GC and byte integrity are analyzed separately.
Nested phases are not added; coarse and fine clocks are not blindly subtracted.

## Host verification before publication

445 JVM test executions pass (74 contract,67 recorder,20 adapter,50 field,234 core;
the gap-only test is also compiled by the field test source set), plus64
independent Python conformance and9 CI report/orchestration tests. Both targeted APKs
build. Fourteen actual-engine fault/race/profile files and six full-size fixture
profile/codec files are byte-identical to the clean reference checkout. The canonical
51 fixture hashes and both complete validation boundaries are unchanged.

## Fatal and admission boundaries

The historical full-suite API35 fatal remains unexplained. A new fixed-clock gap-only
test independently commits repeated zero-payload CHUNKs in both codecs, retains exact
known losses, has zero acknowledged observations and permits honest END with no first
failure. This does not reproduce or disprove process-order/control-reserve exhaustion.
Existing deterministic first-failure/reserve/Stop/OOM/torn-write assertions stay intact.
No safety criterion is relaxed to make the current candidate pass.

Physical Pixel/HT-301 access is prohibited. The diagnostic allocation tables512KiB plus
objects, class-loader pattern cache, body/decoded/JSON scratch, module/source matrices,
prepared/writer/pending owners and UI/graphics must be charged as simultaneous owners
when applicable. Allocations/GC are not live-set measurements. The78+32MiB conservative
policy remains unchanged; real Compose/USB admission and long-run safety stay unverified.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
