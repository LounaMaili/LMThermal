# R4 autonomous Pixel live memory diagnostic

Status: **physical wireless access, explicit radiometric acquisition and one bounded
normal-render memory window completed; safe recorder admission remains unproven.**
No Full LMTR was produced and no recorder Start was attempted. PR16 remains DRAFT,
issue9 OPEN. Frozen wire/corpus, product camera/thermometry and UI ownership are unchanged.
[Aggregate receipt](pixel-live-memory-validation.json) separates actual Pixel, host-only
metadata allocation and isolated ART results. Original files/screenshots remain private.

## Exact route and provenance

The already-authorized mDNS Pixel connection was reused; `adb devices -l` listed it as
`device` while `adb mdns services` returned no additional endpoint. No `adb connect`,
server reset, pairing, security setting, reboot or unrelated application operation was
needed. Pixel8 reports **Android17 /API37**, not the API35 CI emulator runtime.
It was awake/unlocked; initial battery80%, thermal status0, about7.1 GB app-files storage
available and no low-memory signal. Android enumerated Infiray T3-317-13,1514:0001.
Normal CAMERA permission was already granted. Explicit Connect succeeded without a new
USB prompt; an empty-looking permission dump was not treated as definitive failure.

The installed21e1965f APK was historical. The separate field package was updated normally
without uninstalling/deleting its private originals. Tested source is
`145ee46152e46882d7cae794f1eb1d04cfc50e87`; recorder runtime remains
`530a45831a748f7723b34836cc5e7b2416ecdeed`. Actual installed field-build artifact SHA-256:
`301d8bc4e5f5aa8e8796a86a802c3ba1ccd11a5a0386c94471335f194d487d4e`.
No production APK or LMarchery was changed.

Read-only opening showed DISPLAY at about25 FPS, words approximately32774–33014.
The private preview showed a uniform surface/edge, without a visible person or identifying
content, consistent with the owner's authorized non-sensitive scene. This is not a hand,
motion-latency or independently measured temperature-target test.

## One explicit initialization and normal release

Exactly one supported sequence32772 →32800 →32768 ran. Each SET transferred exactly2
bytes; no speculative Zoom GET/readback, other vendor control or second initialization
was sent. Readiness took **6,274 ms** from the actual request. It required75 shutter
settling frames and5 changing valid frames. Raw image words were genuinely below0x4000;
after ready, an observed frame range was5464–5994 (no masking/manufactured indices).

At the first ready event, cumulative session counters were105 discarded,85 rejected,
30 held and5 malformed; native counters reported11 malformed and6 replacements.
These are mixed cumulative diagnostic domains, **not additive losses in the later
memory window**. A later held/unsettled interval recovered without new controls before
that measured window. All original events are retained rather than hidden by the pass.

Acquisition also ran outside the measured window for baseline, initialization and private
evidence/Close inspection; none of the window rates are extrapolated across that time.
Normal Close released the single owner once, in89 ms; active owners became0 and the
closed screen cleared measurement. No physical detach/replug/fault test or reopen followed.

## One bounded normal-render window

`SAMPLE_LIVE_MEMORY` selects Auto/full native ROI, leaves rendering enabled, warms3 s,
then collects20 samples. The completed measured interval is about21.116 s, including
sampling overhead and the final delay. It is not a20-second recording. No suppressed
phase, forced GC, favorable-GC retry, profile change or recording engine is used.

| Actual observation | Result |
|---|---|
| Authoritative gate |528 delivered,528 valid;25.00098 FPS |
| Source issues in this measured window |0 held /0 invalid /0 unavailable /0 known or unknown loss signals |
| Preview publications |528; age p50/p95/max53.82 /69.45 /79.77 ms |
| Render wall p50/p95/max |30.32 /42.36 /53.54 ms |
| Full ROI wall p50/p95/max |7.20 /16.18 /20.72 ms |
| Gate thermometry wall p50/p95/max |16.53 /20.10 /29.26 ms |
| Health minimum Java headroom |86,589,248 B, against unchanged115,343,360 B (110 MiB) requirement |
| Observational policy failure |4 of20 rows: `java_headroom_low`;16 pass observations are not permission for a lucky Start |
| Natural GC |8 collections between first/last counters;0 forced/blocking collections |
| Allocation/free counters over about20.055 s |1,456,558,080 allocated /1,371,103,232 freed bytes; process-wide, not recorder attribution |
| Debug memory sample peaks |Java185,249,984 B; native52,000,224 B; RSS475,676,672 B; PSS408,331,264 B |
| Explicit visible roles |1–3 distinct underlying owners per sample; camera/rendered/ROI only |
| Weak sample census |2–63 alive among at most128 recent observed measurements in rows; final summary differs in time |

Health Runtime and Debug/PSS snapshots are **not atomic** and can straddle GC; do not
subtract/add their peaks to manufacture a simultaneous-live bound. The role properties
are read sequentially, and the diagnostic briefly retains their references while reporting.
It does not enumerate controller/flow/combine/coroutine/Compose roots. Weak owners can
also be uncollected garbage; history truncation does not prove old-owner release.

No request replacement or rejected completion was counted in the528 normal render events.
Preview age is publication/collection age from host receipt, not LCD/photon latency.
The UI was scrolled to reach existing controls, then back toward the image late in the
window. Render/ROI work stayed enabled throughout, but this is **not** an operator-confirmed
fully visible viewport/movement test or a complete graphics-memory bound. No new scene
or calibrated-temperature claim follows.

## Metadata object/allocation follow-up on Fedora only

The new [host-only probe](../../tools/probe_lmtr_metadata_cost.py) runs the unchanged
Kotlin parser/canonical encoder with a256 MiB Java cap,4 warmups/8 samples, no forced GC.
It counts parsed structural owners after timing. Its5 synthetic JSON inputs exercise
item/cardinality, long strings, exact1024-byte decimal tokens and depth32. They are not
complete CHUNKs or canonical R3 fixtures; results do not claim that each fits the rest
of a real CHUNK's schema/remaining item budget.

| Parser stress input | Serialized bytes | Parse + canonical encode allocated p50 |
|---|---:|---:|
|65,535 scalar array values |131,084|42,699,480 B|
|60,000 optional properties |840,001|15,290,384 B|
|63 maximum-length strings |1,032,395|10,890,752 B|
|900 exact long decimals |922,514|80,152,456 B|
|32 object levels |16,736|189,912 B|

A first1025-byte decimal spelling correctly failed the frozen limit. Its generation
failure/partial outputs remain private; corrected inputs use a new directory, not an
overwrite. Both bounded repetitions and source/input hashes remain recorded.

Allocated volume is **not simultaneously retained bytes** or an ART size estimate. The
large amplification nevertheless prevents deriving a universal16× metadata live/scratch
bound from serialized length alone. Existing16 MiB control/object allowance is still an
engineering assumption; it is not certified or lowered by this probe. Unknown optional
values keep exact precision, and no accepted limit/semantics is changed.

Reproduce into a new private directory after `:lmtr-contract:writeRuntimeClasspath`:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/probe_lmtr_metadata_cost.py \
  --java-home "$JAVA_HOME" --output /new/private/metadata-probe
```

## Admission decision and remaining gates

Keep **78 MiB reservation +32 MiB margin**, normal preview, Full evidence, queues and
both full CHUNK validations unchanged. No recorder Start, actual refusal attempt, LMTR,
original-source recording oracle or physical Python LMTR readback occurred. The policy
rows are explicitly observations; no repeated Start/admission retry was attempted.

Safe admission needs a defensible simultaneous source/controller/UI root bound,
metadata object/allocator and GC allowance, native/graphics/system-pressure accounting,
then one authorized20-second Full smoke and independent original-source byte/mask/context/
clock/gap/readback. The physical diagnostic narrows facts; it does not complete R4.
A narrow future allocation option remains direct Float32 packing into a new private
payload owner; no such source/representation change is implemented here.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Regression and failed ART gate retained

The current host suite passes **440 JVM tests**, including3 new visible-owner cases;
build/lint pass. API26 in [run39782301329](https://github.com/LounaMaili/LMThermal/actions/runs/39782301329)
passes118/118. API35 passes117/118, including all3 new role-census tests, but the existing
`fullSizeFiniteRecorderReplayReportsCadenceAndBothCodecs` fails. The run remains **failed**;
no retry, weakened assertion or green relabeling was used.

API26's fixture throughput is still `retained_failure`:97/200 STORED and85/200 DEFLATE,
with103/115 explicit writer drops. API35 stops the first STORED configuration after65
accepted offers;20 observations are acknowledged,5 preparation and44 writer drops
recorded, and finalization is refused with the literal failure
`Cannot certify unjournaled delivered/admitted loss`. The next codec is skipped. Terminal
cleanup overwrites the initiating failure reason, so a more specific journal/OOM/root
cause is **not certified** from this receipt. Queues did not grow. This is separate
from the successful real-camera **no-recorder** diagnostic.

Independent Python validates the immutable20-observation committed prefix with unknown
tail and no END (`full_verified_prefix`); it does not certify the missing/uncommitted
tail or a successful recording. All other available engine/source/profile/memory
readbacks pass. The original incomplete file, oracle and failed logs remain private.
Future engineering should retain the first failure witness and isolate writer-service/
control-journal behavior before another finite replay. The earlier green530a458 ART
receipt remains historical evidence, not a replacement for this failure.
