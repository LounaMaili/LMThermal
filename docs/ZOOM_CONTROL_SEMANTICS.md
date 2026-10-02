# Zoom Absolute control semantics investigation

Diagnostic child branch: `fix/android-zoom-control-semantics`, based on Android
session tip `21cf1551b22dd45bb9510096da9bdfe89f61194d`. This historical GET-only investigation did not change session gates; the subsequent
frame-acceptance update is documented at the end.
No Zoom SET has been sent by this investigation. Standard UVC streaming negotiation
remains the existing probe/commit path; its SET requests are not radiometric commands. Full radiometric acceptance remains pending.

## GET-only Android inventory

In a debug build, Connect/Open, then **Read zoom inventory**. Leave Initialize
radiometric untouched. This explicitly requested inventory runs on the existing
source worker and authorized streaming handle. It exposes only seven standard
GET request codes on camera-terminal Zoom Absolute, never arbitrary selectors or values.
GET_LEN is an exploratory observation here, not a control-width negotiation: standard
camera-terminal Zoom has a fixed two-byte payload, unlike extension-unit sizing.
`LMThermalZoom` logs descriptor fields, exact request metadata, actual lengths,
returned control bytes and unsigned little-endian decoding. Short/failed responses
remain observations and are never decoded as a successful zero. Connection ownership
is checked around each transfer. No private scene payload is logged or saved. Each explicit inventory also replaces
app-private `files/zoom-inventory.jsonl` with its bounded control-only event stream,
so a wireless ADB outage or logcat rotation does not lose the evidence. Retrieve it
from a debuggable build with:

```bash
adb shell run-as org.lmthermal.app cat files/zoom-inventory.jsonl
```

The first follow-up run was confirmed by the operator, but its entries had rotated
out of logcat before ADB reconnection. No result is inferred from that lost log.

The follow-up inventory samples CURRENT before and after each INFO/LEN/range query
as well as a final CURRENT. Query ordinals preserve order; a GET-only API does not
prove this unusual firmware's replies are independent of previous queries.

First inventory observed on Pixel 8, 2026-10-02:

| Query | Request | Requested / actual bytes | Response | Decoded |
|---|---|---|---|---:|
| INFO | 0x86 | 1 / 1 | 03 | 3 |
| LEN | 0x85 | 2 / 2 | 00 00 | 0 |
| MIN | 0x82 | 2 / 2 | 00 00 | 0 |
| MAX | 0x83 | 2 / 2 | ff ff | 65535 |
| RES | 0x84 | 2 / 2 | 00 00 | 0 |
| DEF | 0x87 | 2 / 2 | 00 00 | 0 |
| CUR | 0x81 | 2 / 2 | 00 00 | 0 |

All use `bmRequestType=0xa1`, `wValue=0x0b00`, `wIndex=0x0100`.
Descriptor: UVC 1.00, camera terminal 1, control interface 0, terminal type 0x0201,
`bmControls=0x220` (Zoom bit 9 advertised); all three focal-length fields zero.
The exploratory LEN=0 reply is not used as a valid size declaration. Earlier single direct CURRENT queries
returned `01 00`, including after reconnect. The saved follow-up contains two complete inventories on the same open stream.
The first CURRENT was 1 just after stream open; the second was 0. In **both**,
CURRENT immediately after INFO returned `03 00`, and CURRENT after MAX returned
`ff ff`; after the zero-valued queries it returned `00 00`. All transfers were
exact length. Therefore direct CURRENT depends on preceding GET traffic and cannot
serve as a reliable command-state acknowledgment on this tested Android path.
No Zoom SET was sent; the stream remained display. There were transient malformed/
mixed frames around the second inventory; a GET-only test does not establish their
cause or guarantee uninterrupted acquisition.

Pinned libuvc's stream probe uses `bmHint=1`, and performs probe GET_CUR before
commit. Its first two bytes are a **possible** source of the initial 1 if this
camera reuses endpoint-zero response contents; this is an inference, not a proven
firmware implementation. The observed INFO/MAX echo is direct evidence; the exact
firmware mechanism and corresponding Linux bus behavior still need comparison.

Compact numeric evidence: [control report](diagnostics/2026-10-02-zoom-control-semantics.json).
No conclusion about firmware-wide behavior follows from one inventory.

## Linux API audit: source expectation, not a bus trace

Host kernel: Fedora `7.2.7-200.fc44.x86_64`. The matching upstream stable
[uvc_ctrl.c](https://github.com/gregkh/linux/blob/v7.2.7/drivers/media/usb/uvc/uvc_ctrl.c)
was inspected; Fedora patches have not been audited.

The stock Zoom mapping is camera-terminal selector 0x0b, control bitmap index 9,
two-byte unsigned integer, mapped unchanged to V4L2_CID_ZOOM_ABSOLUTE.
`__uvc_ctrl_load_cur` returns already-loaded state without another physical GET.
`uvc_ctrl_get_flags` replaces the default auto-update flag according to GET_INFO;
INFO=3 advertises GET/SET but not auto-update. `__uvc_ctrl_commit` invalidates loaded
state for auto-updating or write-only controls. Thus a later G_CTRL may return
cached state, including after SET. A successful V4L2 SET/GET pair alone must not be
called a proven physical firmware readback.

The Desktop reference uses VIDIOC_G_CTRL/VIDIOC_S_CTRL, not direct libusb requests.
Existing recorded values remain valid **V4L2 control state** observations; frame
transitions separately establish observed camera effects. The subsequent trace below
found no physical Zoom GET for one repeated HT-301 G_CTRL on the already-loaded control.
UVCIOC_CTRL_QUERY is extension-unit-only and cannot substitute for this camera
terminal query. A Linux direct diagnostic must not detach uvcvideo or claim its
interface just to force access.

## Fresh Linux comparison

The operator moved the same camera from the phone to Fedora with a fresh connection.
The stable Infiray `video-index0` symlink was used (serial/path omitted from evidence).
Both `v4l2-ctl --get-ctrl=zoom_absolute` and Desktop `ZoomControl.get()` returned 0.
`--list-ctrls` reports min 0, max 65535, step 0, default 0, value 0. No stream or
radiometric initialization was started on Linux.

Unprivileged libusb could not open the root-owned USB node. The operator ran the
GET-only helper once with elevated diagnostic access. The request used the same
0xa1/0x81, 0x0b00/0x0100, requested length 2, but returned **libusb ERROR_IO (-1)**
with no bytes. It neither claimed an interface nor detached uvcvideo. This is
an access/transport failure, not a firmware GET_CUR value; it cannot decide Case A
or Case B. The follow-up libusb debug message identified USBFS submit **errno=16
(EBUSY)** while uvcvideo owns the interface. Root is not a product/runtime requirement.
The kernel driver is retained; no forced detach or alternative recipient was tried.

The operator also ran the control-only usbmon helper. The trace opened successfully;
one repeated `v4l2-ctl --get-ctrl=zoom_absolute` exited 0 and printed zero, with **no
Zoom control submit/completion observed**. This is consistent with the loaded driver
cache in the audited source. It applies to this repeated read, not the first read
at attachment and not a post-SET read. No new physical GET response was obtained.
Post-SET Desktop GETs are potentially host/cache confirmation according to the source;
this investigation did not send SET to trace that case.

## Outstanding boundary

Android request metadata matches descriptor-selected standard Zoom and the Linux
mapping, but matching physical replies across both stacks are not established.
The query-dependent CURRENT observation disproves its use as a dependable Android
command-state acknowledgment. It does not prove an exact firmware implementation
or the origin of the initial 1. V4L2 caching and libuvc probe bmHint=1 provide a
source-supported explanation to investigate, not a captured causal chain.
Only after the discrepancy has a defensible explanation may the optional single
32772 experiment proceed. No automatic range/shutter replay, baseline substitution,
thermometry or Celsius work is authorized by the inventory result alone.

## Historical disposition and checks

The 0-versus-1 comparison was comparing different abstractions: the observed repeated
Linux read used driver state, whereas Android CURRENT varied with GET history. The
initial 1 is not evidence of a different camera mode or of an incorrect selector.
The exact firmware mechanism behind the echo, and whether the 1 comes from stream
probe contents, are unresolved. No empirical 0→1 substitution is justified.

No session gate was relaxed in the GET-only task. The baseline-zero and post-write equality checks
remained in that inherited implementation pending a separately
controlled first-command experiment and portable acceptance-gate validation. They must
not be described as portable/proven physical acknowledgments. Full radiometric hardware
acceptance could not resume as a validated milestone on that audit branch alone. No Zoom SET,
32772 experiment, range/shutter replay, LUT or Celsius work occurred in this task.

Checks: **58 JVM tests passed** (51 inherited plus seven diagnostic regressions), debug
APK build passed, lint passed, `git diff --check` passed. The inventory checks fixed GETs,
descriptor-derived IDs, unsigned decoding, exact lengths, ownership cancellation and
preservation of query-dependent replies. Existing frame/stage gates and fixture tests
are unchanged. Read-only inventory was run on the Pixel 8; Linux reads/tracing were
performed on the same freshly attached camera. No private thermal fixture was created.

## Subsequent frame-acceptance confirmation

`fix/android-radiometric-frame-acceptance` continued from this audit with a restricted,
explicit single-32772 Pixel 8 test. An exact two-byte SET produced genuine raw14;
15 receipts and two distinct structurally valid images confirmed the transition.
No GET_CUR criterion or range/shutter command was used in that experiment.

The Android session now removes both baseline-zero and post-write GET equality gates.
It requires explicit ownership, three fresh DISPLAY frames, exact SET completion and
then the existing distinct-valid-frame/shutter/liveness evidence before progression.
The prior Disposition section records this audit's historical boundary; it no longer
describes the current Android gate implementation. The endpoint-zero echo mechanism
remains unresolved and is not needed as a command acknowledgment.
See [current session](ANDROID_RADIOMETRIC_SESSION.md) and
[numeric hardware report](diagnostics/2026-10-02-android-frame-acceptance.json).

The subsequent full Pixel 8 run also passed: 15/15/75 discards and five changing
summary-consistent frames reached RADIOMETRIC_READY in 6.312 s. Close/Open into raw14
stayed conservatively unsettled. The exact firmware echo mechanism remains unresolved;
no GET-based acknowledgment heuristic is used by the current Android session.
