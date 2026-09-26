# Local HT-301 research inventory (2026-09-26)

This is an inventory of the read-only `LMThermal-Research` folder. No research
file was moved, edited, or deleted. The APK at
`apk/HT-301ThermCameraViewerX-V-6.4.20241204-RELEASE.apk` is the only APK
found. `com.hti.Xtherm.BuildConfig` identifies package `com.hti.Xtherm`,
version name `6.4.20241204`, version code `641204`. APK SHA-256:
`9079796632d4dac0733e81594d7bacaeaa912395bb1e84f1b077323106f21c45`.
No second camera application was available for comparison.

## Native libraries

`apk/extracted/lib` contains 52 ELF shared objects. ABI directories and ELF
machine types are `arm64-v8a` (AArch64, 11 files), `armeabi` (ARM, 8),
`armeabi-v7a` (ARM, 11), `x86` (Intel 80386, 11), and `x86_64` (AMD x86-64,
11). Every checked library lacks a regular `.symtab`; dynamic symbols remain
available through `.dynsym`. The three RenderScript libraries (`libRSSupport`,
`librsjni`, `librsjni_androidx`) are absent from `armeabi`. Other library
names present in every ABI are `libBugly`, `libBugly_Native`, `libUVCCamera`,
`libjpeg-turbo1500`, `libsimplePictureProcessing`, `libthermometry`,
`libusb100`, and `libuvc` (each with `.so` suffix).
The inventory was checked with `file`, `readelf -h/-d/-Ws`, `nm -D`, and
`sha256sum` against files in `apk/extracted/lib/<ABI>/`.

| x86_64 library | SHA-256 | Dynamic exports | Relevant dependencies |
|---|---|---:|---|
| `libUVCCamera.so` | `fd86345f99fd3a922c020a6f5eed4464b1b7f890713ee43ef035f933fcdbdf81` | 405 | `libusb100`, `libuvc`, `libthermometry`, `libsimplePictureProcessing`, Android/standard libs |
| `libthermometry.so` | `00fc62661ec6d407ecc8b2dd14a1d136701a386bd8f4b9e1ee342f8b42e7addd` | 14 | `liblog`, `libstdc++`, `libm`, `libc`, `libdl` |
| `libuvc.so` | `f68fdd954b063b39645f60d1cf89cc06c84b5bed97fffd5ff2200dc1165a7bc6` | 205 | `libjpeg-turbo1500`, `libusb100`, Android/standard libs |
| `libsimplePictureProcessing.so` | `fce659b444168e83a57c3be013a66395ac21f75c105de0759d46ed0072622711` | 276 | `liblog`, `libstdc++`, `libm`, `libc`, `libdl` |

`libUVCCamera.so` has undefined imports of `thermometryT4Line`,
`thermometrySearch`, `distanceFix`, and `thermFix`. It does not import
`thermometryT`, `CalcFixRaw`, `GetTempEvn`, or `InitTempParam` directly. The
latter three are called inside `thermometryT4Line`. Relevant exported
`libthermometry.so` functions are `GetTempEvn`, `GetFix`, `InitTempParam`,
`CalcFixRaw`, `thermometryT`, `thermometryT4Line`, `thermometrySearch`,
`thermometrySearchSingle`, `thermometrySearchCMM`, `distanceFix`, and
`thermFix`. Their addresses and call relationships are in
[NATIVE_CALL_CHAIN.md](NATIVE_CALL_CHAIN.md) and
[THERMOMETRY_LIB.md](THERMOMETRY_LIB.md).

SHA-256 values for the four thermometry-path libraries in every ABI:

| ABI | `libUVCCamera.so` | `libuvc.so` | `libsimplePictureProcessing.so` | `libthermometry.so` |
|---|---|---|---|---|
| arm64-v8a | `ad8afd536239ec961e053c79a49aec98f2caa363f183ac80f3ee120251134e94` | `213056a071e39c6be177c8c390962eb601986a330197a084eb384e0389c3bb68` | `58250823b4e21bdf97907b29403549a6f40e2edd08c7d86011c2dbee9c07b18d` | `eff72ec5a90ad741958b0053e1654130e2907964f4e7a19c3025cb7ca374ec14` |
| armeabi | `dd2a3eb37b02d4c4644fdf7cc5c4b3083fd9fb9d7fe734e896a6df3b3db248d1` | `14acace94877ee4d87efa68c0069c287b7053097a5e58455f0224474cc62b286` | `5dbe115010e7d12812ded2ee19385940e1454ea62e07587253bf630b0367f0b6` | `f18c293df69bc706e61cc225c40ebf3ae8abae4dfa674019f86cad28f9718cc5` |
| armeabi-v7a | `5030ae2573f22ed6710b7770869286b1cdd135d006dd708700dc04548457c7bb` | `d11a48b027a704038b564309fdcf41a24c8c36adf98ac297a74c0672433962b8` | `748e1e2bc756c21c87ab6c4345b3db927da2f895fed3180e4a14d1d064ac1688` | `fcfc64a6aa1a5a5f10781e1ce3adffcb5ac4b83d3b6e793192ae819c3a91cdf8` |
| x86 | `48401027f3dd8e039490fa89e8cdbfbe0b26e638205b9d63403ec6b7e23a08cd` | `e98d6835cfcef830bf42005f6bd8357813ddd730416aa5f182457de308c8fb9b` | `44292498680488ca1bb3cf03e4ab030eefdf5dc2727249f737980b4b0285e03a` | `d79d3a04b61dd52269d25d3a5dda16fc531c513d4fd8a75c8ce384170f70b577` |
| x86_64 | `fd86345f99fd3a922c020a6f5eed4464b1b7f890713ee43ef035f933fcdbdf81` | `f68fdd954b063b39645f60d1cf89cc06c84b5bed97fffd5ff2200dc1165a7bc6` | `fce659b444168e83a57c3be013a66395ac21f75c105de0759d46ed0072622711` | `00fc62661ec6d407ecc8b2dd14a1d136701a386bd8f4b9e1ee342f8b42e7addd` |

The decoded Java tree contains 1,387 source files. The app-specific path is
`com/hti/Xtherm/ui/HomeActivity.java`; temperature/UVC bridge files are
`com/serenegiant/usb/UVCCamera.java`, `ITemperatureCallback.java`,
`com/serenegiant/usbcameracommon/AbstractUVCCameraHandler.java`, and
`com/serenegiant/widget/UVCCameraTextureView.java`. The extracted APK also
contains the binary manifest and `classes.dex`.

## Legacy script disposition

These classifications describe evidence value, not permission to delete.
Every file remains in its original location.

| Script in `old-scripts/` | Classification | Unique value / limitation |
|---|---|---|
| `decode_rodata.py` | Still useful | Captures `.rodata` constants from x86_64 thermometry; verify values against current binary before use. |
| `test_frame_tail.py` | Still useful | Finds 514-byte tail and dumps its structure; older image-boundary assumption needs correction. |
| `test_params_full.py` | Still useful | Broad dump of mixed float/int parameter fields. |
| `test_decode_params.py` | Superseded, unique offsets | Probes tail offsets; P2 Pro temperature calculation is invalid here. |
| `test_full_decode.py` | Superseded, unique observations | Compares Y and metadata values; temperature labels and raw reinterpretations are hypotheses. |
| `test_raw.py` | Superseded | Early OpenCV capture and Y view; hard-codes `/dev/video2`. |
| `test_live.py` | Superseded | Checks OpenCV converted versus raw frame shapes. |
| `test_decode.py` | Superseded | Explores bottom rows and Y histogram; row labels predate corrected 288-row boundary. |
| `test_calibrate.py` | Invalidated assumption | Brightness/colored-bar visual calibration is not Celsius thermometry. |
| `test_palette.py` | Superseded | Documents color/row inspection only. |
| `test_thermal.py` | Invalidated assumption | Tries to split the transport into picture and thermal halves and apply an unverified conversion. |
| `test_thermal2.py` | Invalidated assumption | Tests P2 Pro, `/100`, and `/10` uint16 conversions. |
| `test_thermal3.py` | Invalidated assumption | Tests a direct uint16 temperature formula. |
| `test_thermal4.py` | Invalidated assumption | Tests top/bottom uint16 interpretations; preserves observed shapes. |
| `test_thermal5.py` | Invalidated assumption | Tests two linear Y-to-Celsius mappings; Y histogram remains descriptive. |
| `test_load_so.py` | Duplicate, invalidated fallback | Byte-identical to `apk/test_load_so.py`; failed native load leads to a speculative two-point Y calibration. Preserve one copy for history. |
| `test_usb.py` | Still useful, unverified protocol | Records attempted vendor control requests and detach behavior; do not run casually. |
| `test_vendor.py` | Still useful, unverified protocol | Records additional USB control request probes and timeout handling. |
| `test_y16.py` | Requires further investigation | Attempts P2 Pro-style Y16 command with kernel-driver detach; HT-301 compatibility unproven. |
| `test_y16_2.py` | Requires further investigation | Attempts the same Y16 command without detach; the resulting `/64-273.15` conversion is invalid. |

No legacy script is a validated production thermometry implementation. The
USB command bytes and early capture observations should be retained as
historical research. Preserve originals; migrate only confirmed logic into
maintained code after a controlled camera/app comparison.
