# Second bounded physical R4 smoke — safety interruption

## Scope and immutable provenance

On 2026-11-13 the owner explicitly confirmed exclusive Pixel 8 / HT-301 availability,
an unlocked phone and a non-sensitive scene, and authorized exactly one new ten-second
attempt. Existing paired wireless ADB worked directly. No pairing/security changes,
other application access, hosted CI, merge or longer recording were performed.

The clean corrected Android source was
`bd2cfa614d64021078e0a5d16646e00cca6f08f8`, on
`codex/r4-first-physical-attempt`. All 46 field-support JVM tests, APK build and lint
were rerun. A new build was retained with SHA-256
`6f21c24b4e6620f61cef00e76f164ac8bf292bf24ebeed50e496b8be7d83cc4d`
(11640682 bytes). Signing identity matched; `install -r` preserved data. Pulling
back the installed package confirmed that exact hash and the corrected diagnostic
projection was present in its DEX. This is the physically used APK, **not** the
previously retained, never-installed build with hash `1cf4107c…`.
Different rebuild bytes do not imply bit-reproducible Android packaging.

The original first-attempt file and all 717 private manifest members were verified
unchanged before starting. Historical worktrees and pending diagnostics were not
imported. The original first attempt remains [a separate failed observation](FIRST_PHYSICAL_ATTEMPT.md).
This follow-up changes documentation only; no recorder, memory, queue, wire,
thermometry, USB, presentation or assertion change was used to obtain a result.

## Fresh camera state and one actual Start

The first read-only open observed raw14 words 5134–5721 and
`RAW14_UNSETTLED` from the previous session, not READY. No controls or recording
were sent. Normal Close released that connection. The operator then replugged only
the HT-301, providing the required genuine DISPLAY baseline; normal Connect was
explicit and required no new permission prompt.

Pixel 8 / Android 17 / API 37 / arm64 and Infiray T3-317-13 VID `1514`, PID `0001`
were verified. DISPLAY frames were exact 224256-byte transports. A single explicit
initialization requalified three display frames (32778–33011), completed two-byte
SETs 32772 → 32800 → 32768 and reached structurally qualified READY in 6.344 seconds,
with 75 shutter-discard frames and five subsequent live frames. Current measurement
and the normal Inferno/Auto preview were visible before Start. No prior READY was reused.

One guarded UI request used the existing Full/DEFLATE-1 ten-second button. There was
no second Start, fallback, forced GC, preview suppression, cadence change or unsafe
camera fault. The normal safety mechanism stopped before the ten-second timer;
no external Stop was needed. After preservation, normal Close released the current
session once; cumulative two opens/two releases include the initial read-only
unsettled session, with zero active owners left.

## Exact admission witness

| Quantity | Actual value |
|---|---:|
| Full profile / codec / requested seconds | Full / 1 (DEFLATE-1) / 10 |
| Java maximum / occupied / available headroom | 268435456 / 38792192 / 229643264 bytes |
| Logical reservation / additional Java and system margin | 81788928 (78 MiB) / 33554432 (32 MiB) bytes |
| System available / low-memory flag | 1437626368 bytes / false |
| Process RSS at admission | 297185280 bytes |
| Battery / Android thermal status | 67% / 0 |
| Available storage / required free | 90887905280 / 525072384 bytes |
| File/oracle/trace finite cap | 256636928 bytes |
| Preparation / writer queue / preferred CHUNK | 4 / 4 / 4 MiB |
| Maximum observation / control reserve | 3 / 1 MiB |

Admission passed once with the actual in-app Runtime witness. The policy was not
weakened and no favorable repeat snapshot was chosen. Logical reservation is not
a measured process RSS bound or an ART steady-live-memory proof.

## Result and counter boundaries

**Diagnostic correction passed this short physical execution, but the requested
smoke failed the RSS safety gate and did not establish loss-free Full at 25 Hz.**

| Witness | Value |
|---|---:|
| Stop request elapsed from field run origin | 4308975303 ns (4.308975303 s) |
| Final report elapsed including drain/close/full reopen | 7581784834 ns |
| Field authoritative gate deliveries / valid measurements | 108 / 108 |
| Represented eligible offer positions | 105 |
| Engine accepted / finally acknowledged | 103 / 45 |
| Preparation / writer drops | 2 / 58 |
| Known source losses / unknown source-loss signals | 0 / 0 |
| Held / invalid gate observations in this window | 0 / 0 |
| Retained observations / gap events / known missing | 45 / 60 / 60 |
| CHUNKs / timeline entries | 12 / 107 |
| Terminal status / reason | END `interrupted` / `field_safety_rss_increment_exceeded` |
| Diagnostic exception / engine error | null / null |
| Trace samples written | 5 |

`103 accepted + 2 preparation losses = 105 journal positions`, and
`45 retained + 58 writer losses + 2 preparation losses = 105`.
The independent readback verifies each retained original-owner hash and each
prepared-but-dropped oracle row against represented writer loss. The 105 count is
the eligible/journaled population, **not a new direct counter of every refused or
post-Stop call**. The 108 diagnostic callbacks include setup before engine
subscription; do not label the three-callback boundary difference as hidden loss.

Gate acquisition was 25.0143790833 FPS. Final retained observations divided by the
4.308975303-second field intake window are 10.4433181524/s (45/105 = 42.857% of
the eligible positions). This includes observations acknowledged during drain;
it is not a precise acknowledgement-latency rate or a ten-second throughput pass.
Preview collection-age p50/p95/max was 52.545/65.402/72.770 ms over 108 publications,
not optical/display-scanout latency or manual operator usability acceptance.

No immutable first-fatal engine diagnostic was added to this tested published
foundation. The exposed error is null; the first sticky *safety* reason is RSS.
Do not claim an unavailable internal witness or import a pending diagnostic patch
into the tested source retrospectively.

## Memory, queues and writer observations

The RSS policy uses the admission witness plus exactly 128 MiB:

- Baseline 297185280 + increment 134217728 = threshold **431403008 bytes**.
- Triggering `FieldHealth.rssBytes`: **440557568 bytes**, an increment of 143372288
  bytes, exceeding the unchanged limit. Minimum sampled health Java headroom was
  **121750528 bytes**; battery stayed 67%, thermal status 0, low-memory false and
  storage ample. This was not a Java-headroom/storage/thermal refusal.
- The later diagnostic sample reports an RSS peak **448307200 bytes** and occupied
  Java heap peak 152173568 bytes. Health and metrics are different sampling instants;
  do not substitute that later RSS for the actual guard predicate.
- Five samples span 4.224516156 seconds. Whole-process ART counters increased by
  960851968 allocated bytes, four GC collections and 365 ms GC time, with zero new
  blocking-GC count. These include camera, thermometry, normal Compose, recorder and
  diagnostics; there is no matched no-recorder baseline or writer-only attribution.
  No plateau, complete peak or indefinite-memory bound is demonstrated.
- Maximum prep queued/in-flight charge was 3145728/3145728 bytes; writer queued/
  in-flight 3577068/894269 bytes; pending 3599177 bytes. Observed counters stay within
  their unchanged limits; allocation volume and process RSS are different evidence.
- CHUNK seal+append p50/p95/max: 348.613/390.316/611.553 ms. Encode plus full validation
  p95 was 249.873 ms; append/sync p95 173.720 ms. Codec-call p95 was 11.692 ms over
  24 calls. These are inclusive/distribution observations; percentile differences
  cannot be subtracted to invent exclusive stage costs or prove a unique root cause.

The measured writer service and explicit overloads justify further software-only
allocation/backlog investigation. They do not authorize raising RSS limits, queues,
changing evidence roles or reducing requested cadence in a new physical run.

## Preserved originals and independent consumers

The new device run and a new mode-0700 private Fedora directory retain LMTR,
oracle, preflight, five samples, terminal report, inventory, APKs, initialization,
lifetime and own-process logs. All six original file hashes match the extracted
copies after readback. Neither the first run nor any original was repaired or overwritten.
This is persistent same-filesystem preservation, not an independent backup.

- `capture.lmtr`: **10051773 bytes**, SHA-256
  `0ca64fc9a9a9507a241f20c1b358d176c476972603f088bd64cafcea9cb76a3f`.
- `oracle.jsonl`: 504938 bytes, SHA-256
  `968297036a5d57c03d6b4b486933ac65c1860c79dcc7d1fa2a520aae309b1cfa`.
- Python strict full readback passes: 45 available planes, 45 same-observation raw
  views, exact pre-transform Float32/native/transport hashes/lengths and original
  timing/context evidence; END interrupted, all 60 losses represented, no unknown tail.
- Retained temperature bytes 19906560, transport bytes 10091520; native-view logical
  bytes 9953280. Physical decoded payload is 29998080 bytes. Masks are absent and
  all 45 × 110592 temperature values are finite; no mask/value is fabricated.
- Desktop reference `969420ec49e5570599648a186db4617658f0911e` (unchanged runtime
  `85dd25fa4747a2f79b9486702318ab2b9a76b4cc`): installed Linux full check and exposed
  native-window checks pass normal opening, points, ROI, palette/range independence,
  resize, metadata, separate PNG, explicit blank events and cooperative Close.
  Automatic widget tests do not replace operator interaction or physical motion evidence.
- Full Desktop sequence traversal also verifies all 107 events, all 45 original-owner
  Float32/native/transport hashes, all 60 gaps and exact time seek. All 45 native
  planes have distinct hashes (not a controlled target-motion claim). Raw range
  5177–5876; retained matrix range 20.920644760–36.332649231 °C.
  Representative first/middle/last and gap UI navigation preserves point/ROI/palette
  numbers and clears stale readings; play/pause/step and normal worker Close pass.

Only sanitized numerical conclusions and hashes are Git-tracked. Real images,
recordings, private paths, device identifiers, per-frame oracles and logs stay private.
The [sanitized receipt](second-physical-attempt-validation.json) distinguishes the
physically tested source from later documentation-only commits.

## Next gate — not ready for 30–60 seconds

Do not run a longer recording. First review this RSS refusal and the 2+58 explicit
overloads, investigate simultaneous heap/native/graphics lifetimes and bounded
writer service off-device, and qualify any proposed change without weakening safety
or integrity. A future physical short retry needs separate owner authorization and
a separately hashed APK; only a coherent verified short run can justify a 30–60-second
protocol. No optimization or further hardware experiment is part of this report.
Issue #9 stays open; no R4 completion, calibration or durability certification is claimed.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**
