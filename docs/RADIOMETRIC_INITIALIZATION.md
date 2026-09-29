# HT-301 initialization and native word handling

Research date: 2026-09-26. Scope: USB `1514:0001`, 384 × 292 transport,
first 288 image rows. APK identities/hashes are in
[APPLICATION_COMPARISON.md](APPLICATION_COMPARISON.md). No GUI conversion is
introduced by this experiment.

## Evidence classes and outcome

- **Static ARM disassembly:** ThermViewer passes copied UVC bytes unchanged
  into `thermometrySearch`. Both applications reject full uint16 pixel words
  at or above `0x4000`; neither clears flag bits to make them fit.
- **DEX/Java:** output type 1 sends `32772`; type 0 sends `32773`.
  Parameter byte encoding, Handler delays and host/device distinctions below
  come from the APKs, not guessed firmware commands.
- **Linux hardware:** from a physical reconnect, `32772` alone changed the
  sampled image from `0x80YY` to genuine 14-bit values. The subsequent
  `32800` and `32768` retained that representation. A separate reconnect and
  full ThermViewer type-0 startup retained display words, despite successfully
  changing emissivity to 1.0.
- **Executed native reference:** the reconstructed official lookup matches
  the original official x86_64 instructions on sanitized captures. This is
  arithmetic parity, not independently measured temperature accuracy.
- **Inference:** the default/type-0 `0x80` high byte is consistent with
  neutral U/V in advertised YUYV. It is not a flag that either traced native
  search recognizes. No claim is made about every firmware or other modes.

The minimum **observed** transition is: start unconverted capture after
reconnect, then send standard UVC zoom absolute `0x8004` (32772). For the
replayed official range-120 startup, also send `0x8020` and `0x8000`, with
settling time. No XU, raw vendor request, Y16 negotiation, or image mask was
needed on this camera. Control readback confirms API state; the captured
word distribution provides the separate evidence of device effect.

## ARM frame path: no hidden pixel conversion

Addresses are virtual addresses, with the Thumb mode bit removed.

| Binary | SHA-256 |
|---|---|
| Official ARMv7 `libUVCCamera.so` | `5030ae2573f22ed6710b7770869286b1cdd135d006dd708700dc04548457c7bb` |
| Official ARMv7 `libthermometry.so` | `fcfc64a6aa1a5a5f10781e1ce3adffcb5ac4b83d3b6e793192ae819c3a91cdf8` |
| ThermViewer ARMv7 `libUVCCameraIR.so` | `90760f39106d7e568be62a695690facc455c133f04b40808fa7497fc816fea8c` |
| ThermViewer ARMv7 `libthermometry.so` | `f7c16efd575848f431d0a48218fcdb285d0c292f13d4995ff6914337437603b0` |

ThermViewer `UVCPreviewIR::uvc_preview_frame_callback` at `0xccc8` checks
frame availability/size, copies `width*height*2` bytes with `memcpy` at
`0xccec`, swaps host buffers at `this+0x54/+0x58`, and signals the temperature
thread. `do_temperature` (`0xd39c`) supplies that buffer to
`do_temperature_callback` (`0xd0ec`). The callback retains the pointer in
`r4`. `cpyParaVariation` (`0xd0b4`) only copies trailer coordinates to host
fields. A separate image cache is compared/copied, but does not replace the
`r4` input used for thermometry. When refresh is needed, the callback passes
the trailer at `raw + 2*w*(h-4)` to `thermometryT4Line` (`0xd2d6` call).
It then passes the unmodified `r4` full frame and host LUT at `this+0x120`
to `thermometrySearch` at `0xd1f8`, with image mode 4.

No image AND mask, shift, byte swap, YUYV conversion, or flag stripping occurs
on this traced path. Pointer shifts multiply dimensions/indices by their
byte sizes; they do not transform pixel values. Relevant search loops are:

```text
Official ARM:    0x2500 ldrh word; 0x2504 cmp word,#0x4000
                0x2514 bge error; 0x2518 LUT + word*4
ThermViewer ARM: 0x3f34 ldrh word; 0x3f38 cmp word,#0x4000
                0x3f48 bge error; 0x3f4c LUT + word*4
```

`ldrh` zero-extends. A bit-15-set word is therefore >=32768 and takes the
error path; it is not interpreted as a signed sample or masked to 15/14 bits.
The error path prints and returns. Trailer summaries are written before the
pixel loop; at an invalid pixel, earlier pixel outputs may already have been
written and later output storage is not completed. A valid summary does not
prove that a full native temperature matrix was produced.

ThermViewer's native zoom wrapper (`0xb98c`) sends the low 16 bits through
the UVC setter and, for `0x8004/0x8005`, also calls host `setOutputType`
(`0xc2e4`). Type `0x8005` makes the Java callback array length **10**;
otherwise it is `10+w*(h-4)`. Search still receives mode 4. After search,
`0xd20a..0xd210` replaces output[0] with output[7], which comes from trailer
byte **221210** (spot 0). Official center uses **221208**. ThermViewer also
adds a correction float at byte **223514** to the parameter-block correction
at **223742** before lookup output. These differences prevent assuming its
visible center is identical to the official app's center.

## Exact parameter commands

For ThermViewer `XthermAPI.handler.sendCommand.send(position,float)`:

```text
bits = Float.floatToIntBits(value)
byte[i] = (bits >> (8*i)) & 255              # IEEE-754 float32, little endian
command[i] = ((position+i) << 8) | byte[i]   # no 0x8000 OR
setValue(512, command[i]) -> UVCCamera.setZoom -> nativeSetZoom
 -> UVCCamera::setZoom -> internalSetCtrlValue -> UVC zoom absolute
```

`sendFloatCommand` posts four runnables on the calling Looper, at **0, 10,
20, 20 ms** relative to setter invocation. Each runnable emits one command;
there is no additional packet or checksum. The underlying standard control
is `V4L2_CID_ZOOM_ABSOLUTE = 0x009a090d` on Linux. `512` is the Java bridge's
control selector, not a USB request number. The `send(position,int)` overload
also sends four little-endian bytes, but the exposed distance setter is the
**float** overload.

Examples below are decoded values; only the two startup setters were sent
in the ThermViewer hardware experiment.

| Setting | Offset | Example input | Commands in byte order (hex) | Device or host |
|---|---:|---:|---|---|
| Correction | 0 | 0.0 | `0000 0100 0200 0300` | Device |
| Reflected temperature | 4 | 25.0 | `0400 0500 06c8 0741` | Device |
| Ambient temperature | 8 | 25.0 | `0800 0900 0ac8 0b41` | Device |
| Humidity | 12 | 0.45 | `0c66 0d66 0ee6 0f3e` | Device |
| Emissivity | 16 | 1.0 | `1000 1100 1280 133f` | Device |
| ThermViewer distance | 20 | 1.0 | `1400 1500 1680 173f` | Device; float32, not the official uint16 encoding |
| Official distance | 20 | 1 | `1401 1500` | Device; uint16, two writes |

The distance ABI discrepancy is real code evidence, not an instruction to
write the ThermViewer float format to firmware expecting the official layout.
Neither distance form was tested here. An additional ThermViewer
`setDeviceTempCorrection` clamps to approximately ±12.7, converts `10*value`
to an integer then a byte, and sends `(149<<8)|(byte&255)`; this was decoded
but is not part of the replay. It also updates a Java cached float.

Official `HomeActivity.SendRunnable` uses the same byte encoding, but floats
are posted at **20/40/60/80 ms**, with host LUT refresh at **120 ms**.
Integer distance posts two bytes at **20/40 ms**, with host refresh at
**60 ms**. The settings UI additionally debounces the operation. These are
separate scheduling layers, not transport delays added between every byte.

## Host-only state versus device effects

| Operation | Host effect | Device write |
|---|---|---|
| Official `setTempRange(120/400)` | Stores native range used to build the LUT | None from this function |
| Official `setShutterFix(1.5/1.2)` | Stores native correction float | None |
| Official `whenShutRefresh(delay)` | Posts native refresh flag; rebuild LUT on a frame | None |
| Native ThermViewer `whenShutRefresh` | Sets host LUT refresh flag | None |
| ThermViewer SDK private `whenShutRefresh()` / `Refresh()` | Optional Java callback and timer handling | `32768` |
| `32772` / `32773` | ThermViewer additionally stores output type/Java result length | Both send UVC zoom; observed raw/display distinction |
| `32800` / `32801` | Calling Java also selects native range | UVC zoom range command |
| `32768` | Host refresh is a separate action where scheduled | UVC zoom shutter/refresh command |
| Parameter setters | Native later reads settings from returned trailer | Byte commands above |
| `startTemperaturing` | Starts native processing thread/callback | No mode command in that call |

Official ARM stores: `setTempRange` `0xe0c8`, `setShutterFix` `0xe0d4`;
`whenShutRefresh` `0xe1a8` sets a flag under a mutex. ThermViewer equivalents:
`setTempRange` `0xc2f8`, `whenShutRefresh` `0xc3d4`. Naming alone would confuse
the SDK device command with the native host method of the same name.

## Replayed sequences and timing limits

### Official

APK `HomeActivity.startDevicePreview`: preview, host range 120 and shutter
fix 1.5; device `32772` at +500 ms, `32800` at +1100 ms, `32768` at +1600 ms;
host LUT refresh at +2500 ms. Palette at +1000 ms is a host rendering action.
Range-change UI selects host 120/1.5 or 400/1.2 and schedules `32800/32801`
at +100 ms, `32768` at +500 ms, host refresh at +1000 ms, and `32768` at
+1500 ms. High range was decoded but not tested.

Linux records baseline first, then uses at least 500 ms before the first
command, 600 ms before the second, and 500 ms before the third. The first two
controls are followed by 15 discarded frames. Following `32768`, the current
diagnostic discards at least **75 frames** (about three seconds at 25 fps)
before selecting a post-shutter frame. Those captures
extend the APK delays; actual times are in the JSON reports. Linux host LUT
state is supplied explicitly to the offline experimental module; no fake
camera writes substitute for host-only functions.

### ThermViewer output type 0

`MainActivity.WellcomOnNext` chooses type 0 when `images_form_camera` is true,
and type 1 otherwise. Type 0 is the user-requested comparison here, not an
unconditional app default. HT-301 `onStartPreview` posts:

1. Selected output command at +20 ms (`32773` here).
2. Emissivity setter 1.0 at +30 ms; its four byte callbacks span +30..50 ms.
3. Correction setter 0.0 at +40 ms; its callbacks span +40..60 ms.
4. Refresh at +40 ms, **before all parameter bytes have completed**.

The same Looper executes these posted tasks serially. Same-time order depends
on insertion order and actual setter invocation time; it is not a pair of
parallel 40 ms threads. The diagnostic reproduces nested posting and records
every actual control time/readback. No `HighTempRange` call was found on this
startup path, so no extra range command was inserted. That optional SDK
method sets host 120/400 immediately, then sends `32800/32801` at +10 ms,
calls `Refresh`, and schedules another refresh after 2500 ms.

At 25 fps a frame takes about 40 ms. Linux cannot obtain a distinct new frame
between every 10 ms byte write while preserving that schedule. Continuous
acquisition therefore records receipt intervals for every command stage;
empty stage arrays explicitly mean no frame arrived. Receipt times are not
exposure timestamps, and frames arriving during a write are not proof of
that write's completed device effect. The settled stage discards 20 frames
before collecting three observations.

## Hardware observations

The official run started after a user-confirmed physical reconnect, USB
connection number 24, zoom readback 0. ThermViewer used another confirmed
reconnect, connection number 25, readback 0. Reports omit serials and paths.

| Official stage | Readback | Image min–max over three frames | Words <=0x3fff | Bit 15 / bit 14 | Trailer center / high / low |
|---|---:|---:|---:|---|---|
| A baseline | 0 | 32768–32993 | 0% | 100% / 0% | 8224 / not retained / not retained |
| B output 1 | 32772 | 5213–5266 | 100% | 0% / 0% | 5235–5236 / not retained / not retained |
| C range 120 | 32800 | 4715–4864 | 100% | 0% / 0% | 4844 / not retained / not retained |
| D refresh | 32768 | 4715–4864 | 100% | 0% / 0% | 4844 / 4864 / 4715 |

ThermViewer's actual command order (all readbacks equal sent values):
`32773, 4096, 32768, 4352, 0, 4736, 4927, 256, 512, 768`.
Relative command starts were approximately
`20.09, 30.15, 40.14, 40.66, 41.15, 50.13, 50.64, 51.34, 60.13, 60.67 ms`.
One complete frame arrived during the emissivity-byte0 interval:
32768–33013, bit15 100%, bit14 0%, emissivity about 0.9799957. All other
short write intervals had no received frame. After settling, min–max was
32877–32907, 0% 14-bit, bit15 100%, bit14 0%; emissivity was 1.0 and correction
0.0. The trailer center was 5998 in all three frames. Thus all decoded
startup parameter writes worked without changing the selected display mode.

`word & 0x3fff` and `word & 0x7fff` min/max are retained in the reports as
**diagnostics only**. For display frames these reduce to the low-byte Y range;
for compatible raw frames they equal the full words. No masked value is fed
to the experimental LUT. Top-two-bit distribution is entirely `10` in the
sampled display frames and `00` in the raw frames.

The original September 26 JSON reports were temporary and did not survive the
interruption. The measurements above were retained in session output and these
notes; a labeled transcript summary is in `LMThermal-Desktop/docs/diagnostics/`.
The first sanitized raw fixture and full native reference table survived in
`tests/fixtures/`. New captures use persistent research storage. Images are spatially
scrambled outside a center patch and known identifier spans are cleared.
This preserves word statistics and trailer numerics, not extrema coordinates.

## Lookup reconstruction and remaining boundary

See [NATIVE_CALL_CHAIN.md](NATIVE_CALL_CHAIN.md) for corrected source offsets
and [THERMOMETRY_LIB.md](THERMOMETRY_LIB.md) for arithmetic. The previous note
incorrectly used byte 223490 in the FPA transform. The actual sources are:

- `221186`: `20 - (word-7800)/36`, used in GetFix and the linear coefficient.
- `223490`: `word/10 - 273.15`, used with host shutter fix in the constant.
- Official native lens default **68** multiplies stored distance by **3**.

The desktop `experimental_thermometry.py` supports the observed official
384-wide, range-120, lens-68, shutter-fix-1.5 branch. The optional
`tools/native_lookup_reference.py` executes the hash-pinned official x86_64
ELF's arithmetic with host libm imports, without Android constructors.
It does not execute camera I/O. Reference tables are saved for ordinary tests,
so tests need neither APK binaries nor RE tools. Low indices producing NaNs
are retained as native undefined entries; observed image/trailer indices must
select finite values.

The first raw fixture yields center/high/low **16.282669 / 16.800127 /
12.868019** from both implementations. All 16,384 entries match exactly on
this Linux host, including the NaN pattern (13,264 finite entries). These
numbers are not a calibration claim. There is no independently observed
same-scene Android UI callback or calibrated temperature target in this run.
Cross-ABI numerical parity, equilibrium/shutter behavior, other ranges/lenses,
and independent accuracy remain to be checked before GUI measurement work.

## Subsequent shutter and spatial checks (2026-09-27)

A second reconnect and official replay again changed the first image after
`32772` from display words 32768–32980 to raw14 values 5128–5411. Range
`32800` produced 4623–5008, and refresh `32768` retained raw14 at
4621–5007. A post-refresh run of 75 frames included a held/repeated interval
of roughly **1.3 seconds**; its first frames are transient evidence and cannot
serve as steady-state temperature fixtures. Discarding 15 frames was
insufficient. The 75-frame settling recommendation is specific to this
experimental camera and setup, not a universal firmware guarantee.

A later read-only 75-frame window contained 75 distinct raw14 images, with
center/high/low output standard deviations of approximately 0.029/0.017/0.039
°C over three seconds. A subsequent calibration update was also observed, so
low short-term variation does not prove long-term equilibrium. Trailer high
and low coordinates matched their indexed image words and image extrema in
all 75 later frames. The trailer center index matched the literal pixel at
`(192,144)` in only 13 of 75 frames; its region semantics remain open.
In a room/ceiling scene, a warmer bright strip averaged about 27.336 °C and
a cooler wall region about 22.213 °C under the reconstructed lookup. These
were relative scene observations without an independent reference. Persistent
capture reports and sanitized fixtures are listed in the desktop repository.

The next physical-accuracy experiment should compare at least two, preferably
three, matte high-emissivity targets against a contact/reference instrument.
Record surface readings and uncertainty, emissivity, ambient/reflected
temperature, distance, the raw14 frame and the reconstructed temperature;
avoid fitting a correction to a single scene.
