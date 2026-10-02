#include <jni.h>
// libuvc exposes uvc_wrap only after the libusb API version macro is visible.
#include <libusb.h>
#include <libuvc/libuvc.h>
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
    stream->latest.clear();
    return result;
}

/** Snapshot acquisition counts independently of presentation cadence. */
extern "C" JNIEXPORT jlongArray JNICALL
Java_org_lmthermal_app_NativeUvc_stats(JNIEnv* env, jobject, jlong handle) {
    auto* stream = reinterpret_cast<Stream*>(handle);
    std::lock_guard<std::mutex> guard(stream->mutex);
    jlong values[] = {static_cast<jlong>(stream->received), static_cast<jlong>(stream->replaced),
                      static_cast<jlong>(stream->malformed), static_cast<jlong>(stream->sequence)};
    auto result = env->NewLongArray(4);
    if (result) env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

/** Serialized by the Kotlin transport dispatcher; never races read/stats or callback lifetime. */
extern "C" JNIEXPORT void JNICALL
Java_org_lmthermal_app_NativeUvc_close(JNIEnv*, jobject, jlong handle) {
    release(reinterpret_cast<Stream*>(handle));
}
