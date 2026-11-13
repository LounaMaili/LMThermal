# R4 host memory and writer candidates after the second physical smoke

Status: isolated Fedora engineering, based on `bacfd3a66cb7a63aa8f17d883c01c7ed047fdf6e`.
No ADB/discovery, phone/camera, APK installation, emulator, hosted ART or Actions is
permitted in this phase. Preserve the [second physical report](SECOND_PHYSICAL_ATTEMPT.md),
all original bytes and all historical failed experiments. Host gains are not Pixel RSS proof.

## First demonstrated attribution

The physical file has12 CHUNKs:11 mixed observation/loss and one observation-only;
none is gap-only. Eleven retain four Full observations each, and the terminal one
retains one. They carry60 explicit losses among45 retained observations. Thus the
[aged one-gap-CHUNK mechanism](https://github.com/LounaMaili/LMThermal/blob/facf86f121d68f796dff7a4e6957c8069d8dd546/docs/lmtr-r4/BACKLOG_DYNAMICS.md)
is a separately demonstrated possibility, not an observed explanation of this file.

## Ownership candidate

- `RecordingPayload.fromFloat32Le` packs original raw bits into one freshly allocated
  private owner. The public byte-array constructor remains defensive. No output array
  or writer callback escapes. HT still asks its immutable source for an independent
  FloatArray copy; it no longer builds an encoded array which the constructor copies
  again. Exactly442368 array bytes are avoided per available384×288 temperature plane.
- `FrozenJson.value` feeds its private bytes directly to the synchronous read-only
  parser. Fresh immutable maps/strings still result; there is no retained metadata tree
  or externally visible byte alias. Source JSON and returned nested maps remain protected.
- Payload role access retains only one immutable String with volatile publication,
  not a whole parsed descriptor or source owner. Competing initial reads may parse
  twice harmlessly; no recorder lock or admission behavior changes. Missing/invalid
  roles still fail on access rather than being defaulted.

The existing negotiated/queued/prepared/pending byte counts do not change: serialized
owners are identical. A small scalar String/object overhead is retained per payload;
it is not charged as another plane or used to lower78+32 MiB admission. There is no
new native/source/presentation ownership API, pool, borrowed public reader or thermometry.

## Host measurements and qualification

`HostWriterCostProbe` is a JVM-test-only tool. It reads one existing preserved file,
selects the first largest-decoded CHUNK, and reports fixed eight scalar samples after
four warmups. It measures current-thread allocation and inclusive wall/CPU time for:
fixture freezing+Full selection, complete physical metadata closure, and two separately
executed full physical CHUNK validations. Input reading/setup and SHA identity checks
are outside measured intervals. Results retain no payload history and write no payload.
HotSpot counters are not ART, native allocation, reachable heap or RSS measurements.

The initial **unpaired exploratory** ownership comparison measured preparation
2894776→2197008 allocated bytes/op, with an exact six-file byte match across
Analysis/Native/Full × STORED/DEFLATE. The independent Full readback also passes.
These numbers combine packing, input-copy and scalar-cache changes; they do not
attribute the whole difference to any one change. Wall-time or untouched-validation
variation in this initial pair is not a causal speedup claim. A predeclared fresh-process
reference/candidate/candidate/reference campaign is the next comparison gate.

New deterministic tests cover raw Float32 bits (signed zero/infinities/NaN payloads),
input/output mutation isolation, nested JSON immutability, exact optional decimal
values, invalid-role failures and concurrent role publication. Existing recorder and
adapter tests pass, including overloaded queues, timed Stop, sync/ACK and incomplete
recovery. All safety budgets, both complete validations and Full retained evidence
remain unchanged. No physical performance improvement is claimed at this checkpoint.

Native-equivalent temperatures; absolute physical accuracy not yet independently validated.
