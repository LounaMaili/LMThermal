# HT-301 application comparison and radiometric-mode experiment

Research date: 2026-09-26. The APKs and their extracted working trees are in
the separate `LMThermal-Research/apk/` folder. The ThermViewer extraction is
`apk/thermviewer_v2.0.23_extracted/`; no APK or binary is tracked here.
"Confirmed in code" below means Android DEX, decoded Java, ELF imports, or
binary resource data. It does not imply that the camera responded as intended.

The later full investigation is in
[RADIOMETRIC_INITIALIZATION.md](RADIOMETRIC_INITIALIZATION.md), including ARM
addresses, parameter bytes, host/device separation and staged Linux results.

## Identity and camera selection

| | Official HTI application | ThermViewer |
|---|---|---|
| APK | `HT-301ThermCameraViewerX-V-6.4.20241204-RELEASE.apk` | `ThermViewer_v2.0.23.apk` |
| SHA-256 | `9079796632d4dac0733e81594d7bacaeaa912395bb1e84f1b077323106f21c45` | `36e8c667754a17ddcc60042f364024f97ba732a6121ac41e9dc49d230ee6cf7c` |
| Package | `com.hti.Xtherm` | `com.bitera.ThermViewer` |
| Version | `6.4.20241204` / code `641204` | `2.0.23(ot)` / code `73` |
| English app label | `Hti Image` | `ThermViewer` |
| USB filter | USB class `0xef`, subclass `0x02`; no VID/PID in its filter | `0x1514:0x0001` and `0x1772:0x0002` |
| Camera families in code | HT-301 / Xtherm UVC path | Xtherm/HT-301 **and** separate ThermApp / IR PLUG paths |

The ThermViewer USB filter stores VID/PID values as decimal strings `5396/0001`
and `6002/0002`. Its `MainActivity.WellcomOnNext` selects `XthermAPI` for
vendor `0x1514`. `XthermAPI$2.onStartPreview` gates temperature startup on
vendor `0x1514` **and** product `0x0001`. These are confirmed DEX branches.
The official APK's USB filter is class-based; the observed HT-301 is
`0x1514:0x0001`. ThermViewer's additional ThermApp libraries and commands
must not be attributed to the HT-301 branch.

## Native paths and capabilities

| Topic | Official application | ThermViewer HT-301 branch | Evidence status |
|---|---|---|---|
| UVC bridge | `libUVCCamera.so` + `libuvc.so` | `libUVCCameraIR.so` + `libuvcIR.so` | Confirmed ELF dependencies |
| Thermometry library | Own `libthermometry.so` | Own `libthermometry.so`, different SHA-256 (`f7c16efd575848f431d0a48218fcdb285d0c292f13d4995ff6914337437603b0` for ARMv7) | Confirmed; **not** the same binary |
| Temperature functions | Imports `thermometryT4Line` and `thermometrySearch` | `libUVCCameraIR.so` imports the same two names | Confirmed API family; arithmetic equivalence unresolved |
| Additional builder | Not exported in official ARMv7 library | Exports `thermometryT4Line_auto_fix` | Confirmed; caller and meaning unresolved |
| `InitTempParam` | ARMv7 function is 48 bytes | ARMv7 function is byte-identical to official | Confirmed binary comparison; other stages not proven equal |
| Raw USB path | UVC/libusb stack | A separate ThermApp path also uses `libusbthermapp.so`, `libthermip.so`, and related libraries | Confirmed packaging; applicability to HT-301 is **not** established |
| UVC extension units / vendor control transfers | Device has an extension unit; no required HT-301 XU operation reconstructed | `libuvcIR.so` can parse extension units; no HT-301 XU or raw vendor request is identified in the traced startup path | Unresolved; do not replay legacy vendor commands |
| Frame format | Linux exposes YUYV 384 × 292; native callback uses four-row trailer | Java UVC bridge starts preview and native thermometry; no explicit alternate Y16 format request found in the traced DEX calls | Confirmed calls; raw words obtained without changing advertised YUYV; offsets traced below |
| Center/high/low | Official `thermometrySearch` maps trailer indices through a 16,384-entry lookup | Same high/low indices; overwrites Java center with spot-0 lookup from byte 221210; adds correction from 223514 | Confirmed ARM disassembly |

Only the ARMv7 ThermViewer APK contains the HT-301 UVC bridge and thermometry
libraries. Its `arm64-v8a`, `x86`, and `mips` directories contain only
RenderScript/support libraries. See [RESEARCH_INVENTORY.md](RESEARCH_INVENTORY.md)
for the complete list. The two ARMv7 `GetTempEvn` and `CalcFixRaw` functions
have equal sizes but different bytes; this alone does not establish different
math because branch addresses and literals can differ.

## Control order recovered from code

The numeric `setValue(512, value)` wrapper reaches the UVC `zoom_absolute`
setter (`0x009a090d`). The numeric values are application commands tunneled
through that standard control; their device-side semantics are **inferred**
from caller names, not decoded firmware behavior.

| Event | Official application | ThermViewer HT-301 branch |
|---|---|---|
| Open/preview | Open UVC and start preview | `MainActivity.WellcomOnNext` selects `XthermAPI`, chooses type `0` if `images_form_camera`, otherwise type `1`, opens UVC and starts preview |
| Temperature startup | `HomeActivity.startTemperaturing` calls `nativeStartStopTemp(1)` through `UVCCamera.startTemp` when measurement is selected | `XthermAPI$2.onStartPreview` checks `0x1514:0x0001`, then calls `startTemperaturing`; `CameraThread.handleStartPreview` also contains a conditional `startTemp` call |
| Output command | At +500 ms: `32772` | Output type `0` maps to `32773`; type `1` maps to `32772`. After preview, a runnable sends the selected value at about +20 ms |
| Default range | Native `setTempRange(120)` and `setShutterFix(1.5)`; at +1100 ms send `32800` | Host `setTempRange(120/400)` is separate. Optional `HighTempRange` sends `32800/32801` at +10 ms then refreshes; no range call found in HT-301 startup |
| Shutter/NUC | At +1600 ms send `32768`; `whenShutRefresh` scheduled +2500 ms. Measurement start schedules another refresh and `32768` at +400 ms | `Refresh` reaches `whenShutRefresh`, which sends `32768`; first startup posts a refresh at about +40 ms. Range change also calls refresh |
| Settings | UI/native correction, reflection, ambient, humidity, emissivity, distance | HT-301 startup posts emissivity `1.0` at about +30 ms and correction `0` at about +40 ms. Its `sendFloatCommand` splits float bytes into several `setValue` operations; exact byte encoding and overlapping scheduling are now decoded; see initialization document |
| Range change | 400: `setTempRange(400)`, shutter fix `1.2`, `32801`; 120: `setTempRange(120)`, shutter fix `1.5`, `32800`; then repeated `32768` and refresh | `HighTempRange` sets host 120/400, sends the corresponding device range at +10 ms, refreshes, then schedules another refresh +2500 ms |

ThermViewer's four post-preview runnables are posted at approximately 20, 30,
40, and 40 ms according to DEX integer additions and `postDelayed` calls.
These callbacks run serially on the same Looper; insertion order and nested
setter posting determine the order at shared deadlines. The decoded byte
commands do not wait for each setting to finish before the +40 ms refresh.

**Minimum observed transition:** `32772` after opening the default stream
produced true 14-bit words on the tested Linux camera. The full type-0 path
(`32773`, emissivity 1.0, correction 0.0 and refresh) retained `0x80YY` words.
This agrees with type 0's native Java array length of ten summaries and type
1's full temperature matrix. Both ARM searches reject high pixel bits rather
than masking them. See the staged evidence in
[RADIOMETRIC_INITIALIZATION.md](RADIOMETRIC_INITIALIZATION.md).

## Earlier single-command Linux experiment (historical)

Hypothesis: ThermViewer's HT-301 output-type-zero command `32773` alone might
change the 384 × 288 image words from `0x80YY` display values to 14-bit raw
indices. The desktop diagnostic opened the stable Infiray `video-index0`
link, captured three baseline frames, sent **one** V4L2
`zoom_absolute=32773` command, discarded 20 frames, and captured three more.
The command succeeded and a later V4L2 readback returned `32773`.

| Metric | Before (three frames) | After (three frames) |
|---|---|---|
| Image word minimum | 32778–32779 | 32778–32779 |
| Image word maximum | 33005–33007 | 33008–33010 |
| Image words `<= 0x3fff` | 0% in every frame | 0% in every frame |
| Center trailer index | 5306–5307 | 5307–5310 |
| High trailer index | 5766–5768 | 5770–5772 |
| Low trailer index | 5296–5297 | 5298 |
| Calibration fields | `0.2705`, `35.992`, `0.00004`, `0.0057`, `0.8234` | Same; copied fields still match |
| Image Y standard deviation | 52.24–52.25 | 52.40–52.48 |

The image still varied across pixels. A separate post-control luminance
preview showed a spatially structured thermal gradient, rather than a blank
or random-noise frame; it did not establish radiometric validity. The trailer
indices changed modestly with live frames. Those index changes cannot be
attributed to the command without a controlled scene. **Experimentally
observed:** `32773` alone did not yield true `0..16383` image words. No other
control combination was tried,
no raw vendor request was sent, and no fixture or native Celsius output was
generated. That earlier experiment left the full startup question open. The subsequent
staged replay above resolved the mode transition using `32772` and decoded
the remaining parameter writes; it required no raw vendor requests.

## Remaining evidence needed

An independently observed Android callback or calibrated target measurement
for the same scene is still needed for temperature accuracy. The official
x86_64 native arithmetic can now be reproduced offline against saved raw
frames, but that comparison does not validate physical calibration or prove
ThermViewer's extra correction and spot semantics equivalent.
