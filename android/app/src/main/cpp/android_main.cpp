#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <string>
#include <cstdlib>

#include "android_touch_input.h"
#include "android_disc_extractor.h"

#define LOG_TAG "WiiCompiled_NDK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ANativeWindow* g_nativeWindow = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeInit(JNIEnv* env, jobject thiz, jstring internalPath) {
    const char* pathStr = env->GetStringUTFChars(internalPath, nullptr);
    LOGI("Native init with data directory: %s", pathStr);
    setenv("INTERNAL_STORAGE", pathStr, 1);
    env->ReleaseStringUTFChars(internalPath, pathStr);
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSurfaceCreated(JNIEnv* env, jobject thiz, jobject surface) {
    g_nativeWindow = ANativeWindow_fromSurface(env, surface);
    LOGI("Native window acquired: %p", g_nativeWindow);
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSurfaceDestroyed(JNIEnv* env, jobject thiz) {
    LOGI("Native window releasing: %p", g_nativeWindow);
    if (g_nativeWindow) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeTouchEvent(JNIEnv* env, jobject thiz, jint action, jfloat x, jfloat y, jint pointerId) {
    bool isDown = (action == 0 || action == 2);
    
    // Virtual Accelerate (Bottom-right zone)
    if (x > 1400.0f && y > 700.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnA, isDown);
    }
    // Virtual Drift / Hop (Upper-right zone)
    else if (x > 1400.0f && y <= 700.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnB, isDown);
    }
    // Virtual Item (Upper-left zone)
    else if (x < 600.0f && y < 400.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnL, isDown);
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeTiltEvent(JNIEnv* env, jobject thiz, jfloat angle) {
    AndroidInput::SetTilt(angle);
    int8_t steer = static_cast<int8_t>(std::clamp(angle * 128.0f, -128.0f, 127.0f));
    AndroidInput::SetStick(steer, 0);
}

JNIEXPORT jboolean JNICALL
Java_com_wiicompiled_mkw_MainActivity_nativeExtractDisc(JNIEnv* env, jobject thiz, jstring discPath, jstring destDir) {
    const char* cDiscPath = env->GetStringUTFChars(discPath, nullptr);
    const char* cDestDir = env->GetStringUTFChars(destDir, nullptr);

    LOGI("Native extracting disc %s to %s", cDiscPath, cDestDir);

    auto result = AndroidDiscIO::ExtractDisc(cDiscPath, cDestDir, [](const std::string& status, float prog) {
        LOGI("Extracting: %s (%.1f%%)", status.c_str(), prog * 100.0f);
    });

    env->ReleaseStringUTFChars(discPath, cDiscPath);
    env->ReleaseStringUTFChars(destDir, cDestDir);

    if (!result.success) {
        LOGE("Extraction failed: %s", result.errorMessage.c_str());
    }
    return result.success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
