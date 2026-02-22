#include <jni.h>
#include <vector>

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_cinerracam_nativewriter_NativeCompressionEngine_nativeCompress(
        JNIEnv *env,
        jobject,
        jbyteArray frameBytes,
        jbyteArray metadataBytes,
        jint mode) {
    const jsize frameLen = env->GetArrayLength(frameBytes);
    const jsize metadataLen = env->GetArrayLength(metadataBytes);

    std::vector<jbyte> output;
    output.reserve(static_cast<size_t>(metadataLen + frameLen + 8));

    output.push_back('C');
    output.push_back('C');
    output.push_back(static_cast<jbyte>(mode));
    output.push_back(0);

    output.push_back(static_cast<jbyte>((metadataLen >> 24) & 0xFF));
    output.push_back(static_cast<jbyte>((metadataLen >> 16) & 0xFF));
    output.push_back(static_cast<jbyte>((metadataLen >> 8) & 0xFF));
    output.push_back(static_cast<jbyte>(metadataLen & 0xFF));

    if (metadataLen > 0) {
        std::vector<jbyte> metadata(static_cast<size_t>(metadataLen));
        env->GetByteArrayRegion(metadataBytes, 0, metadataLen, metadata.data());
        output.insert(output.end(), metadata.begin(), metadata.end());
    }

    if (frameLen > 0) {
        std::vector<jbyte> frame(static_cast<size_t>(frameLen));
        env->GetByteArrayRegion(frameBytes, 0, frameLen, frame.data());
        output.insert(output.end(), frame.begin(), frame.end());
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(output.size()));
    if (result == nullptr) {
        return nullptr;
    }

    env->SetByteArrayRegion(result, 0, static_cast<jsize>(output.size()), output.data());
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_cinerracam_nativewriter_NativeCompressionEngine_nativeFlush(
        JNIEnv *,
        jobject) {
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cinerracam_nativewriter_NativeCompressionEngine_nativeClose(
        JNIEnv *,
        jobject) {
}
