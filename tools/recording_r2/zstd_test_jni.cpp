// Disposable test APK codec; bounded independent frames, no external dictionaries.
#define ZSTD_STATIC_LINKING_ONLY
#include <zstd.h>
#include <jni.h>
#include <vector>
#include <cstdint>
static constexpr size_t CHUNK_LIMIT = 64 * 1048576;
static constexpr size_t WINDOW_LIMIT = 8 * 1048576;
static void reject(JNIEnv* env) { env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "bounded_zstd_rejection"); }
static jbyteArray result(JNIEnv* env, const std::vector<uint8_t>& bytes, size_t length) {
    auto array = env->NewByteArray(static_cast<jsize>(length));
    if (array) env->SetByteArrayRegion(array, 0, static_cast<jsize>(length), reinterpret_cast<const jbyte*>(bytes.data()));
    return array;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_org_lmthermal_app_r2_NativeZstd_encode(JNIEnv* env, jobject, jbyteArray input) {
    auto length = env->GetArrayLength(input);
    if (length < 0 || static_cast<size_t>(length) > CHUNK_LIMIT) { reject(env); return nullptr; }
    std::vector<uint8_t> source(length), output(ZSTD_compressBound(length));
    env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(source.data()));
    auto context = ZSTD_createCCtx();
    if (!context) { reject(env); return nullptr; }
    ZSTD_CCtx_setParameter(context, ZSTD_c_compressionLevel, 3);
    ZSTD_CCtx_setParameter(context, ZSTD_c_windowLog, 23);
    ZSTD_CCtx_setParameter(context, ZSTD_c_checksumFlag, 1);
    auto size = ZSTD_compress2(context, output.data(), output.size(), source.data(), source.size());
    ZSTD_freeCCtx(context);
    if (ZSTD_isError(size)) { reject(env); return nullptr; }
    return result(env, output, size);
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_org_lmthermal_app_r2_NativeZstd_decode(JNIEnv* env, jobject, jbyteArray input, jint expected) {
    auto length = env->GetArrayLength(input);
    if (expected < 0 || static_cast<size_t>(expected) > CHUNK_LIMIT || length < 0 || length > 65 * 1048576) { reject(env); return nullptr; }
    std::vector<uint8_t> source(length);
    env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(source.data()));
    ZSTD_FrameHeader header{};
    auto status = ZSTD_getFrameHeader(&header, source.data(), source.size());
    if (ZSTD_isError(status) || status != 0 || header.frameType != ZSTD_frame || header.dictID != 0 ||
        header.windowSize > WINDOW_LIMIT || header.frameContentSize != static_cast<uint64_t>(expected) ||
        ZSTD_findFrameCompressedSize(source.data(), source.size()) != source.size()) { reject(env); return nullptr; }
    auto context = ZSTD_createDCtx();
    if (!context) { reject(env); return nullptr; }
    ZSTD_DCtx_setParameter(context, ZSTD_d_windowLogMax, 23);
    std::vector<uint8_t> output(expected);
    auto size = ZSTD_decompressDCtx(context, output.data(), output.size(), source.data(), source.size());
    ZSTD_freeDCtx(context);
    if (ZSTD_isError(size) || size != static_cast<size_t>(expected)) { reject(env); return nullptr; }
    return result(env, output, size);
}
