#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <inttypes.h>
#include <android/log.h>

#define TAG "TastyDriveNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define TELEGRAM_MAX_BOT_FILE_SIZE (50LL * 1024LL * 1024LL) // 50MB
#define OPTIMAL_STREAM_CHUNK (64 * 1024) // 64KB

// 64-bit FNV-1a Hash Constants
#define FNV_OFFSET_BASIS 14695981039346656037ULL
#define FNV_PRIME 1099511628211ULL

JNIEXPORT jstring JNICALL
Java_com_tastydrive_app_util_NativeEngine_initNative(JNIEnv *env, jobject thiz) {
    LOGI("TastyDrive Native C Engine initialized successfully.");
#if defined(__arm64__) || defined(__aarch64__)
    const char *arch = "ARM64-v8a (64-bit Ultra Fast)";
#elif defined(__arm__)
    const char *arch = "ARMv7 (32-bit Fast)";
#elif defined(__x86_64__)
    const char *arch = "x86_64 (64-bit Ultra Fast)";
#else
    const char *arch = "Universal Native";
#endif
    char message[128];
    snprintf(message, sizeof(message), "TastyDrive Native C Core Active [%s]", arch);
    return (*env)->NewStringUTF(env, message);
}

JNIEXPORT jlong JNICALL
Java_com_tastydrive_app_util_NativeEngine_fastChecksum(JNIEnv *env, jobject thiz, jbyteArray data, jint length) {
    if (data == NULL || length <= 0) {
        return 0;
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (bytes == NULL) {
        return 0;
    }

    uint64_t hash = FNV_OFFSET_BASIS;
    for (int i = 0; i < length; ++i) {
        hash ^= (uint8_t)bytes[i];
        hash *= FNV_PRIME;
    }

    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    return (jlong)hash;
}

JNIEXPORT jboolean JNICALL
Java_com_tastydrive_app_util_NativeEngine_isOversized(JNIEnv *env, jobject thiz, jlong fileSize) {
    return (fileSize > TELEGRAM_MAX_BOT_FILE_SIZE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_tastydrive_app_util_NativeEngine_getOptimalBufferSize(JNIEnv *env, jobject thiz) {
    return OPTIMAL_STREAM_CHUNK;
}

JNIEXPORT jstring JNICALL
Java_com_tastydrive_app_util_NativeEngine_formatSpeed(JNIEnv *env, jobject thiz, jlong bytesPerSec) {
    char buffer[64];
    if (bytesPerSec <= 0) {
        snprintf(buffer, sizeof(buffer), "0 B/s");
    } else if (bytesPerSec < 1024) {
        snprintf(buffer, sizeof(buffer), "%" PRId64 " B/s", bytesPerSec);
    } else if (bytesPerSec < 1024 * 1024) {
        double kb = (double)bytesPerSec / 1024.0;
        snprintf(buffer, sizeof(buffer), "%.1f KB/s", kb);
    } else {
        double mb = (double)bytesPerSec / (1024.0 * 1024.0);
        snprintf(buffer, sizeof(buffer), "%.2f MB/s", mb);
    }
    return (*env)->NewStringUTF(env, buffer);
}

JNIEXPORT jstring JNICALL
Java_com_tastydrive_app_util_NativeEngine_formatFileSize(JNIEnv *env, jobject thiz, jlong bytes) {
    char buffer[64];
    if (bytes < 1024) {
        snprintf(buffer, sizeof(buffer), "%" PRId64 " B", bytes);
    } else if (bytes < 1024 * 1024) {
        double kb = (double)bytes / 1024.0;
        snprintf(buffer, sizeof(buffer), "%.1f KB", kb);
    } else if (bytes < 1024LL * 1024LL * 1024LL) {
        double mb = (double)bytes / (1024.0 * 1024.0);
        snprintf(buffer, sizeof(buffer), "%.1f MB", mb);
    } else {
        double gb = (double)bytes / (1024.0 * 1024.0 * 1024.0);
        snprintf(buffer, sizeof(buffer), "%.2f GB", gb);
    }
    return (*env)->NewStringUTF(env, buffer);
}

JNIEXPORT jboolean JNICALL
Java_com_tastydrive_app_util_NativeEngine_validateTelegramToken(JNIEnv *env, jobject thiz, jstring token) {
    if (token == NULL) return JNI_FALSE;
    const char *str = (*env)->GetStringUTFChars(env, token, NULL);
    if (str == NULL) return JNI_FALSE;

    int len = strlen(str);
    int colonPos = -1;
    jboolean valid = JNI_TRUE;

    // Telegram Bot token is formatted like: 123456789:ABCDefgh-ijklMNOP_qrstuvwxyz1234
    // Length is typically between 35 and 65 chars, with a colon separating numeric bot id and auth secret
    if (len < 30 || len > 70) {
        valid = JNI_FALSE;
    } else {
        for (int i = 0; i < len; i++) {
            if (str[i] == ':') {
                if (colonPos != -1 || i < 6 || i > 14) {
                    valid = JNI_FALSE;
                    break;
                }
                colonPos = i;
            } else if (colonPos == -1) {
                // Before colon must be digits
                if (str[i] < '0' || str[i] > '9') {
                    valid = JNI_FALSE;
                    break;
                }
            } else {
                // After colon can be alphanumeric or _ or -
                char c = str[i];
                if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                    valid = JNI_FALSE;
                    break;
                }
            }
        }
        if (colonPos == -1) valid = JNI_FALSE;
    }

    (*env)->ReleaseStringUTFChars(env, token, str);
    return valid;
}

