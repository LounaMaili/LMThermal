# R2 framing, recovery and chunk evidence

**Noncanonical test bytes; no LMTR wire freeze.** See the [exact annex](FRAMING_PROPOSAL.md)
and [predeclared plan](TEST_PLAN.md). The implementation is test/tool-only.

## Executed proofs

- Forward HEADER/CHUNK/INDEX_PAGE/CHECKPOINT/END; body sync, complete footer and
  footer sync precede commit counters. No complete-file rewrite on Stop.
- Chunk closure retains contexts/settings, masks, authoritative Celsius and explicit
  gaps. Seals at the earliest observed 1 s, 32 slots or preferred 16 MiB physical
  target; final short chunks, low-rate 2 s observations and mask/context changes pass.
- Full materializes Celsius plus transport; native is an independently hashed
  same-frame contiguous view. No physical duplicate raw plane.
- JVM lazy index test spans 600 chunks; one seek reads under 1 MiB. Fixed fanout 256,
  depth 8 and backward offset/ordinal checks prevent cycle traversal.
- Sparse tool-only 4,530,250,240-byte logical file with 180 larger chunks: Kotlin
  and independent Python seek beyond 4 GiB, reading 75,562,200 bytes for one large
  integrity-checked chunk. It is sparse test storage, not a SAF seeking claim.
  JVM peak used heap was 229,560,320 bytes. This is **not** an ordinary HT 64 MiB
  incremental-memory pass; R1b needs a separate large-geometry admission policy.

## Interruption and integrity

[Eight actual child-process kills](reports/process-kill.json) pass: metadata/payload
body writes, body synced before footer, footer written, footer synced, after committed
chunk before checkpoint and after checkpoint before END. Recovered counts match
independent synthetic byte expectations; no END or final count/time is invented.
A written but unacknowledged footer can survive forensic reopening, while the live
writer still truthfully reports failure/unacknowledged commit.

JVM tests separately cover truncated metadata/payload/footer, missing END, torn
checkpoint and corrupt final index. Cyclic/invalid final indexes fall back only to
verified prior chunks. A discovered test-reader bug retained the failed END root
when no earlier checkpoint existed; both readers now clear that root before fallback.
False leaf ranges and wrong leaf record types reject. Promised committed payload
corruption is an explicit integrity failure, never converted into a normal gap.

Deterministic ENOSPC and generic refusals at write/body-sync/footer-sync boundaries
preserve prior commits and do not advance saved counters. Source Stop and physical
USB detach are bounded; the [real detach report](reports/android-first-detach.json)
records a 1,037 ms finalization, no subsequent measurement and no reinitialization.
The acquisition lifecycle log independently recorded DETACHED (not merely background).

## Limits and assurance

Recovery suffix is bounded to 64 chunks/1,024 records; terminal scan to 65 MiB+72.
One damaged checkpoint plus the current interval is supported; more severe damage
is an explicit limit. Recovered files are opened read-only, never silently resumed.
Hashes/CRC are integrity checks, not authentication. Process kills and filesystem
syncs do not certify physical power-loss durability. Close/sync failure is not success.
No whole-index loading or multi-GB committed fixture is needed.

The final failed-close regression explicitly preserves a complete forensic END
while reporting unsuccessful live Stop and refusing further intake. Final byte
integrity and successfully acknowledged backend close are separate observations.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
