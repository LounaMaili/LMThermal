# Android LMTX v1 still export

The immutable [Accepted v1.0 contract](LMTX_FORMAT_V1.md) is normative. This guide
explains the Android producer, not a replacement schema. Issue #4 retains decision
history; Android [#5](https://github.com/LounaMaili/LMThermal/issues/5) remains open
until Desktop [#1](https://github.com/LounaMaili/LMThermal-Desktop/issues/1) can verify
cross-platform interoperability. No Desktop importer or recording format is added.

## Operator flow

1. Open a source. Initialize HT-301 radiometry explicitly when temperatures are wanted.
2. Choose Point/ROI, palette and Auto/Locked range as usual.
3. **Save capture** freezes the current source and choices and privately prepares/checks
   one complete `.lmtx`. The live viewer continues during preparation.
4. **Choose destination** opens Android's create-document picker. Finish or cancel it.
   Success appears only after the destination stream closes successfully.
5. **Share capture** is available only after successful publication. Android grants
   temporary read access to the finalized private cache file through FileProvider.

Destination/share UI can background the Activity. Rotation also recreates it.
The existing unconditional `MainActivity.onStop → leaveForeground → owner.background`
camera policy releases USB in all of these cases; returning requires explicit Connect/Open and never
initializes automatically. The frozen capture survives this release and Activity/
language recreation. Neither rotation nor SAF requires USB release technically:
the coordinator/connection uses application context. This inherited conservative
policy remains in this milestone; a separate lifecycle change could distinguish
configuration/picker transitions from genuine background/detach and test permission/
ownership races. There is no automatic reconnect/initialization workaround here.
Final ViewModel disposal cancels unfinished work; backgrounding alone preserves
the prepared capture while releasing the camera. Process death cannot resume an
in-progress publication, and incomplete destination bytes are never a valid capture.
A closed/detached/error source cannot create a capture. A streaming display,
unsettled or transient preview can create a truthful preview-only capture: no last
ready matrix, temperature range, statistics or HT measurement evidence is reused.
ROI geometry may persist without readings.

The source coordinate system stays native: HT-301 384×288; simulator 160×120.
No raw orientation, ROI, sample index or temperature is changed for phone orientation.

## Ownership and module boundary

`CaptureFreeze` consumes one retained immutable `CameraSessionState` plus choices
read at the Save gesture. A separate worker copies the published owned source into
`LmtxCapture` before opening destination UI. Modules must obey the existing immutable
publication contract; a later frame, close, ROI or palette change cannot mutate the
retained request. The completed capture retains no controller, live Bitmap or
mutable array alias.

`ExportEvidenceProvider`, implemented by an immutable module measurement adapter,
returns `SourceExportEvidence`: owned repeatable member streams, generic descriptors,
namespace/version metadata, actual settings references and provenance. The common
writer never casts to HT-301 or serializes diagnostic/UI maps. A module without
richer evidence omits it truthfully. Simulator output is visual-only, simulated,
160×120, with no Celsius plane, native raw14, calibration or HT extension.

HT-301's `org.lmthermal.camera.ht301` extension schema 1.0 retains current original
raw14 words and full 224256-byte transport, parsed environmental/calibration inputs,
actual host range/lens/shutter assumptions, lookup trace, distinct trailer/literal
center observations and trailer high/low. Core extrema and ROI statistics instead
come from the saved matrix. No new lookup, UVC control, high-bit mask or camera
setting is used by export. Algorithm/module versions not exposed by the source
are omitted rather than invented.

**Native-equivalent temperatures; absolute physical accuracy not yet independently validated.**

## Binary, presentation and clocks

The authoritative current Float32 matrix is copied bit-for-bit and streamed as
little-endian row-major `[height,width]`, unit `Cel`. The existing ROI engine supplies
finite-valid-row-major-f64-v1 extrema/means, first row-major ties and exact counts.
Optional 0/1 masks exclude invalid cells; only serialization canonicalizes their
filler to positive-zero bits. No-valid planes have counts, no readings/locations.
HT-301's fully valid plane omits the mask.

The PNG is generated from the frozen Celsius plane and frozen stable palette ID/
effective range, without overlays. Invalid cells have a distinct unavailable
checker pattern. An unavailable measurement uses the coherent source preview;
if coherence is absent it is omitted. Presentation does not supply numeric data.
Unknown optional JSON is retained with BigInteger/BigDecimal semantic precision,
including null values; known optional fields are omitted, not replaced with null.
This implements the owner's number-preservation clarification without requiring
original numeric spellings. Binary evidence retains exact bytes.

Acquisition UTC is unknown/not exposed. Receipt is the actual monotonic millisecond
receipt converted to nanoseconds (without claiming finer precision), in a fresh
process UUID clock domain; sequence is a decimal string. File creation records
actual host UTC as unreliable/host_clock_not_verified. It is not sensor acquisition
UTC or calibration evidence. Original acquisition captures have fresh UUIDs and
no parent lineage; derived capture editing is outside this implementation.

## Integrity, resource and publication behavior

Pure JVM `exchange/` code supplies model, JSON, streaming writer, physical ZIP/schema/
payload checker and storage-copy primitive. Payload SHA-256 and exact lengths are
computed over owned uncompressed streams, then DEFLATE level 3 writes directly to
an exclusively created private stage. There is no whole-ZIP memory buffer. The ZIP
is finalized, flushed, file-synced, closed and reopened before READY. Inventory,
CRC, SHA, typed sizes, clocks, statistics and PNG dimensions/scanlines are checked
before any measurements are returned. Local/central records, data descriptors,
paths, duplicate/colliding members, forbidden attributes and profile/resource
limits are independently checked. No archive member is extracted or executed.

Only one active export and one retained capture are accepted; no frame queue is
created. Hashing/compression/checking/PNG work runs on a dedicated bounded IO worker,
not Compose or acquisition callbacks. Snapshot/evidence copies and render/check
scratch allocations are bounded; no peak-memory claim is inferred from archive size.

Implementation-specific limits, reported as `resource_limit`: capture/check numeric
and image allocations at most **4,194,304 pixels**, encoded PNG at most **16 MiB**,
JSON number tokens at most **16,384 characters**. These are stricter local resource
refusals, not changes to v1.0's normative ceilings. The producer emits PNG; its
self-checker supports non-interlaced PNG without EXIF and original acquisition files.
JPEG/interlaced/orientation-metadata images and derivative lineage return explicit
`unsupported_required_feature`; this tool is not a full v1 Desktop consumer.

Publication uses a newly created SAF document, without broad storage permissions.
An 8 KiB buffer copies only the completed stage. Close/write/storage-full/cancel
failures never enable sharing; private staging is removed and partial destination
deletion is attempted. If the provider refuses deletion the UI asks for manual
removal. Private file sync does not promise parent-directory durability. SAF/cloud
atomic rename, power-loss durability and cloud transactions are provider dependent,
not promised. Successful cache shares are temporary, replaced on next capture and
old stages are pruned after a day on exporter creation; recipients should copy them.

Rich captures retain scene imagery, raw/native transport and available calibration;
the localized UI explains this. No location, device serial, USB/host path, LAN
identity, credentials or private diagnostics are added by default. Full opaque
transport preserves original device bytes and should be treated as sensitive scene/
protocol evidence. New live captures stay private and are not regression fixtures.

## Camera-free tools and shared fixtures

```bash
./gradlew :core:test
./gradlew :core:checkLmtx -PlmtxFile=/absolute/path/capture.lmtx
./gradlew :core:generateLmtxFixtures
```

The checker prints the verified manifest, never opens a camera, rebuilds thermometry
or extracts a member. The generator uses only synthetic inputs and a previously
sanitized room fixture, never current hardware. Shared fixtures and pinned archive
hashes/expected results are in
[`core/src/test/resources/lmtx/`](../core/src/test/resources/lmtx/README.md).
The corpus regression verifies every archive hash and positive/negative checker
outcome. Generator byte determinism requires a fixed timezone/JDK ZIP implementation;
committed archive bytes and corpus hashes are the interoperability authority.

Debug builds record bounded timing/size/FPS and keep one app-private source proof:
an independent ByteBuffer encoding of the retained original Celsius bits, ROI
statistics and presentation. This is validation data, never an exported member or
public image hash. Debug SAF readback compares the destination bytes with the checked
stage when the provider permits reading. Neither debug proof opens a camera or
reprocesses thermometry. See [ANDROID_VALIDATION.md](ANDROID_VALIDATION.md) for real
Pixel/HT-301 evidence and current acceptance status.
