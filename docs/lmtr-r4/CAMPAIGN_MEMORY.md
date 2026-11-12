# Allocation and admission: controlled campaign update

This supplements, not replaces, the [full ownership ledger](HT301_FULL_ALLOCATION_LEDGER.md).
No buffer, queue, margin, maximum observation or negotiated admission value changes.

## Exact serialized-accounting bound

Current fixture configuration P=4MiB, Qp=Qw=4MiB, O=3MiB, K=1MiB gives:

`Qp + Qw + 2O + 12 max(P,O) + 16K = 4 + 4 + 6 + 48 + 16 = 78MiB`.

The additional required headroom margin is32MiB; keep110MiB. Queue bytes, in-flight
charges, conservatively counted pending closure, assembly/codec/validation allowance
and control/metadata allowance are distinct terms. A32-entry loss journal at32KiB per
reservation is not a request to grow it when the writer falls behind.

Binary lifetimes remain: deferred module owner; one preparation owner; prepared queue;
writer in-flight owner; pending entries; private encoded group(s); final body; first
complete validator; second independent complete validator. Handoffs may share immutable
owners and must not be added twice as distinct arrays. Conversely, dead first-pass
objects can still occupy heap during the second pass until natural GC. Sequential
validation does not prove sequential occupied heap or remove JSON trees/scratch.

## Measured allocation improvement and its limits

For one fixed2.687MB synthetic CHUNK,100 full host validations allocated397012688 bytes
before this campaign,338950216 with the seven-pattern cache and333331440 with the
metadata digest encoder. All are current-thread HotSpot allocation VOLUME, not live
retained objects, Android peak heap, native or RSS. Combined reduction16.0% does not
reduce the110MiB admission threshold or authorize a physical recording.

Seven fixed Pattern graphs plus an immutable map remain reachable for the class-loader
lifetime; exact ART retained bytes are unmeasured. Matcher instances are call-local.
The metadata hash still owns one canonical UTF-8 array, SHA-256 state and digest32 bytes;
its already established direct nibble encoder uses64 chars plus the returned String,
instead of32 Formatter graphs. No digest or payload remains cached between validations.

Fine diagnostic sample storage is separately524288 bytes plus fixed objects/reporting
scratch, as [previously measured](WRITER_API35_PREPARATION.md). OFF and ON common coarse
metrics are identical. Preallocation is outside runtime allocation-counter deltas;
process-wide GC overlaps elapsed work and cannot be summed as a pure pause/CPU total.
Native/PSS samples are roughly500ms and can miss nonsimultaneous peaks. None is a
conservative maximum for a real Compose/USB/native camera process.

## Admission conclusion

Keep the existing policy and refusal. Serialized metadata bounds (1MiB,65536 items,
depth32, exact numeric/string bounds) are finite, but bytes-per-String/HashMap/BigDecimal,
allocator garbage, simultaneous older UI generations, GPU/USB/native roots and actual
storage stalls remain unresolved. This campaign proves narrower host allocation and
byte-integrity facts only. The old physical refusal is not overridden by an emulator
heap sample or an empty UI. No forced GC, largeHeap, extra process or queue workaround.

Next architecture decision is still an attributed live-owner/transient census with
normal Compose preview and a reviewed object/allocator uncertainty allowance. Only
an explicitly authorized future physical session may supply that evidence; if it
cannot fit safely, review a representation/ownership redesign before implementation.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
