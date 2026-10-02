#include <jni.h>
// libuvc exposes uvc_wrap only after the libusb API version macro is visible.
#include <libusb.h>
#include <libuvc/libuvc.h>
#include <libuvc/libuvc_internal.h>
#include <android/log.h>
#include <condition_variable>
#include <mutex>
#include <vector>
#include <chrono>
#include <cstdint>
#include <string>

namespace {
// Transport negotiation must retain the four trailer rows (docs/HARDWARE.md).
constexpr int kWidth = 384;
constexpr int kTransportHeight = 292;
constexpr int kFps = 25;
constexpr size_t kFrameBytes = kWidth * kTransportHeight * 2;
constexpr size_t kMaximumPayload = kFrameBytes * 2;
constexpr int kPollMilliseconds = 250;
// Upstream zoom helpers use an unlimited timeout and conflate a zero-byte transfer with success.
// Use their exact standard UVC request on the same handle, with bounded timeout/exact byte count.
constexpr unsigned int kControlTimeoutMs = 1000;
constexpr uint8_t kUvcGetRequest = LIBUSB_ENDPOINT_IN | LIBUSB_REQUEST_TYPE_CLASS | LIBUSB_RECIPIENT_INTERFACE;
constexpr uint8_t kUvcSetRequest = LIBUSB_ENDPOINT_OUT | LIBUSB_REQUEST_TYPE_CLASS | LIBUSB_RECIPIENT_INTERFACE;
constexpr uint16_t kSelectRaw14 = 32772;
constexpr uint16_t kSelectNormalRange = 32800;
constexpr uint16_t kShutterRefresh = 32768;
constexpr uint16_t kVendorId = 0x1514;
constexpr uint16_t kProductId = 0x0001;

/** One stream owns its UVC context. The Java-authorized fd stays open through close(). */
struct Stream {
    uvc_context_t* context = nullptr;
    uvc_device_handle_t* device = nullptr;
    std::mutex mutex;
    std::condition_variable available;
    std::vector<uint8_t> latest;
    uint64_t received = 0;
    uint64_t replaced = 0;
    uint64_t malformed = 0;
    uint64_t sequence = 0;
    uint64_t consumedSequence = 0;
};

/** Copy callback-owned payload before libuvc recycles it; at most one pending frame. */
void receivedFrame(uvc_frame_t* frame, void* user) {
    auto* stream = static_cast<Stream*>(user);
    std::lock_guard<std::mutex> guard(stream->mutex);
    ++stream->received;
    if (frame->width != kWidth || frame->height != kTransportHeight ||
        frame->frame_format != UVC_FRAME_FORMAT_YUYV) {
        ++stream->malformed;
        return;
    }
    if (frame->data_bytes != kFrameBytes) {
        ++stream->malformed;
    }
    if (stream->received == 1) {
        __android_log_print(ANDROID_LOG_INFO, "LMThermal", "native callback width=%u height=%u format=%d data_bytes=%zu sequence=%u",
                           frame->width, frame->height, frame->frame_format, frame->data_bytes, frame->sequence);
    }
    // A damaged payload cannot create unbounded memory use. Report it as malformed, never pad it.
    if (!frame->data || frame->data_bytes > kMaximumPayload) return;
    if (!stream->latest.empty()) ++stream->replaced;
    const auto* bytes = static_cast<const uint8_t*>(frame->data);
    stream->latest.assign(bytes, bytes + frame->data_bytes);
    stream->sequence = frame->sequence;
    stream->available.notify_one();
}

/** Error paths release the same resources as normal close, in reverse ownership order. */
void release(Stream* stream) {
    if (stream->device) uvc_close(stream->device); // joins streaming and event callbacks
    if (stream->context) uvc_exit(stream->context);
    delete stream;
}

/** Surface a transport failure to the Kotlin owner without permitting partial open handles. */
void fail(JNIEnv* env, Stream* stream, const char* stage, int code) {
    __android_log_print(ANDROID_LOG_ERROR, "LMThermal", "%s failed: %d", stage, code);
    release(stream);
    std::string message = std::string(stage) + ": " + std::to_string(code);
    env->ThrowNew(env->FindClass("java/io/IOException"), message.c_str());
}
}

/** Only UVC streaming negotiation occurs here. No camera terminal/vendor setting writes. */
extern "C" JNIEXPORT jlong JNICALL
Java_org_lmthermal_app_NativeUvc_open(JNIEnv* env, jobject, jint descriptor) {
    auto* stream = new Stream();
    // Android forbids global usbfs enumeration; UsbManager supplied the authorized descriptor.
    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    int result = uvc_init(&stream->context, nullptr);
    if (result < 0) { fail(env, stream, "uvc_init", result); return 0; }
    result = uvc_wrap(descriptor, stream->context, &stream->device);
    if (result < 0) { fail(env, stream, "uvc_wrap", result); return 0; }
    uvc_device_descriptor_t* identity = nullptr;
    result = uvc_get_device_descriptor(uvc_get_device(stream->device), &identity);
    bool matching = result == 0 && identity && identity->idVendor == kVendorId && identity->idProduct == kProductId;
    if (identity) uvc_free_device_descriptor(identity);
    if (!matching) { fail(env, stream, "HT301 identity", result ? result : -1); return 0; }
    uvc_stream_ctrl_t control{};
    result = uvc_get_stream_ctrl_format_size(stream->device, &control,
                                            UVC_FRAME_FORMAT_YUYV, kWidth, kTransportHeight, kFps);
    if (result < 0) { fail(env, stream, "YUYV 384x292@25 probe", result); return 0; }
    __android_log_print(ANDROID_LOG_INFO, "LMThermal", "probe frameBuffer=%u payload=%u interval=%u",
                       control.dwMaxVideoFrameSize, control.dwMaxPayloadTransferSize, control.dwFrameInterval);
    result = uvc_start_streaming(stream->device, &control, receivedFrame, stream, 0);
    if (result < 0) { fail(env, stream, "uvc_start_streaming", result); return 0; }
    return reinterpret_cast<jlong>(stream);
}

/** Sole Kotlin I/O consumer polls a bounded slot, never from the Android UI thread. */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_lmthermal_app_NativeUvc_read(JNIEnv* env, jobject, jlong handle) {
    auto* stream = reinterpret_cast<Stream*>(handle);
    std::unique_lock<std::mutex> guard(stream->mutex);
    if (!stream->available.wait_for(guard, std::chrono::milliseconds(kPollMilliseconds),
                                    [&] { return !stream->latest.empty(); })) return nullptr;
    auto result = env->NewByteArray(static_cast<jsize>(stream->latest.size()));
    if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(stream->latest.size()),
                                       reinterpret_cast<const jbyte*>(stream->latest.data()));
    stream->consumedSequence = stream->sequence;
    stream->latest.clear();
    return result;
}

/** Snapshot acquisition counts independently of presentation cadence. */
extern "C" JNIEXPORT jlongArray JNICALL
Java_org_lmthermal_app_NativeUvc_stats(JNIEnv* env, jobject, jlong handle) {
    auto* stream = reinterpret_cast<Stream*>(handle);
    std::lock_guard<std::mutex> guard(stream->mutex);
    jlong values[] = {static_cast<jlong>(stream->received), static_cast<jlong>(stream->replaced),
                      static_cast<jlong>(stream->malformed), static_cast<jlong>(stream->sequence), static_cast<jlong>(stream->consumedSequence)};
    auto result = env->NewLongArray(5);
    if (result) env->SetLongArrayRegion(result, 0, 5, values);
    return result;
}

/** Serialized by the Kotlin transport dispatcher; never races read/stats or callback lifetime. */
extern "C" JNIEXPORT void JNICALL
Java_org_lmthermal_app_NativeUvc_close(JNIEnv*, jobject, jlong handle) {
    release(reinterpret_cast<Stream*>(handle));
}

/** Restricted camera-terminal zoom operation, serialized with reads/close by the Kotlin owner.
 * libusb's event thread continues asynchronous streaming while this worker waits for a control.
 * Descriptors supply terminal/interface IDs; no second handle or guessed extension unit is opened.
 */
extern "C" JNIEXPORT jint JNICALL
Java_org_lmthermal_app_NativeUvc_zoom(JNIEnv* env, jobject, jlong handle, jint operation) {
    auto* stream = reinterpret_cast<Stream*>(handle);
    if (operation < 0 || operation > 2) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Unsupported HT301 operation");
        return -1;
    }
    const auto* terminal = uvc_get_camera_terminal(stream->device);
    if (!terminal) {
        env->ThrowNew(env->FindClass("java/io/IOException"), "Missing UVC camera terminal");
        return -1;
    }
    uint16_t value = operation == 0 ? kSelectRaw14 : operation == 1 ? kSelectNormalRange : kShutterRefresh;
    uint8_t data[2] = {static_cast<uint8_t>(value & 0xff), static_cast<uint8_t>(value >> 8)};
    int transferred = libusb_control_transfer(stream->device->usb_devh,
        kUvcSetRequest, UVC_SET_CUR,
        UVC_CT_ZOOM_ABSOLUTE_CONTROL << 8,
        terminal->bTerminalID << 8 | stream->device->info->ctrl_if.bInterfaceNumber,
        data, sizeof(data), kControlTimeoutMs);
    __android_log_print(ANDROID_LOG_INFO, "LMThermal", "zoom SET terminal=%u interface=%u selector=%u transferred=%d bytes=%02x,%02x",
        terminal->bTerminalID, stream->device->info->ctrl_if.bInterfaceNumber,
        UVC_CT_ZOOM_ABSOLUTE_CONTROL, transferred, data[0], data[1]);
    if (transferred == sizeof(data)) {
        // Drop a pending pre-control payload. Fifteen further receipts are still discarded by session.
        std::lock_guard<std::mutex> guard(stream->mutex);
        stream->latest.clear();
    }
    // SET reports transfer completion only, never a fabricated device acknowledgment.
    return transferred;
}

/** Read existing descriptors; bcdUVC lives in ctrl_if (the public device field is reserved/zero). */
extern "C" JNIEXPORT jlongArray JNICALL
Java_org_lmthermal_app_NativeUvc_zoomDescriptor(JNIEnv* env, jobject, jlong handle) {
    auto* stream = reinterpret_cast<Stream*>(handle);
    const auto* terminal = uvc_get_camera_terminal(stream->device);
    if (!terminal) {
        env->ThrowNew(env->FindClass("java/io/IOException"), "Missing camera terminal");
        return nullptr;
    }
    jlong values[] = {stream->device->info->ctrl_if.bcdUVC, terminal->bTerminalID,
        stream->device->info->ctrl_if.bInterfaceNumber, static_cast<jlong>(terminal->bmControls),
        terminal->wTerminalType, terminal->wObjectiveFocalLengthMin,
        terminal->wObjectiveFocalLengthMax, terminal->wOcularFocalLength};
    auto result = env->NewLongArray(8);
    if (result) env->SetLongArrayRegion(result, 0, 8, values);
    return result;
}

/** Seven fixed GET requests only. Preserve transfer error/short length instead of inventing a value. */
extern "C" JNIEXPORT jintArray JNICALL
Java_org_lmthermal_app_NativeUvc_zoomQuery(JNIEnv* env, jobject, jlong handle, jint query) {
    constexpr uint8_t kQueries[] = {UVC_GET_INFO, UVC_GET_LEN, UVC_GET_MIN, UVC_GET_MAX,
                                  UVC_GET_RES, UVC_GET_DEF, UVC_GET_CUR};
    constexpr uint16_t kLengths[] = {1, 2, 2, 2, 2, 2, 2};
    if (query < 0 || query >= 7) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Unsupported zoom query");
        return nullptr;
    }
    auto* stream = reinterpret_cast<Stream*>(handle);
    const auto* terminal = uvc_get_camera_terminal(stream->device);
    if (!terminal) {
        env->ThrowNew(env->FindClass("java/io/IOException"), "Missing camera terminal");
        return nullptr;
    }
    uint8_t data[2]{};
    int transferred = libusb_control_transfer(stream->device->usb_devh, kUvcGetRequest, kQueries[query],
        UVC_CT_ZOOM_ABSOLUTE_CONTROL << 8,
        terminal->bTerminalID << 8 | stream->device->info->ctrl_if.bInterfaceNumber,
        data, kLengths[query], kControlTimeoutMs);
    // First element is actual transfer length or libusb error, followed only by returned bytes.
    int returned = transferred > 0 ? std::min(transferred, 2) : 0;
    jint values[] = {transferred, data[0], data[1]};
    auto result = env->NewIntArray(1 + returned);
    if (result) env->SetIntArrayRegion(result, 0, 1 + returned, values);
    return result;
}
