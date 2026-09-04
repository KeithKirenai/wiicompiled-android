#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <string>
#include <cstdlib>
#include <thread>
#include <atomic>
#include <chrono>
#include <cmath>
#include <algorithm>

#include "android_touch_input.h"
#include "android_disc_extractor.h"

#define LOG_TAG "WiiCompiled_NDK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static ANativeWindow* g_nativeWindow = nullptr;
static std::thread g_renderThread;
static std::atomic<bool> g_renderRunning{false};

static void GameRenderWorker() {
    LOGI("GameRenderWorker started");
    if (!g_nativeWindow) {
        LOGE("GameRenderWorker started without native window");
        return;
    }

    constexpr int kRenderW = 1280;
    constexpr int kRenderH = 720;
    ANativeWindow_setBuffersGeometry(g_nativeWindow, kRenderW, kRenderH, WINDOW_FORMAT_RGBA_8888);

    uint32_t frameCount = 0;
    while (g_renderRunning.load(std::memory_order_relaxed) && g_nativeWindow != nullptr) {
        ANativeWindow_Buffer buffer;
        if (ANativeWindow_lock(g_nativeWindow, &buffer, nullptr) == 0) {
            uint32_t* line = static_cast<uint32_t*>(buffer.bits);
            float t = frameCount * 0.03f;
            
            // Generate vivid animated race-track background gradient
            uint8_t r = static_cast<uint8_t>(20 + 15 * std::sin(t));
            uint8_t g = static_cast<uint8_t>(35 + 25 * std::sin(t + 2.0f));
            uint8_t b = static_cast<uint8_t>(70 + 35 * std::cos(t));
            uint32_t bgColor = 0xFF000000 | (b << 16) | (g << 8) | r;

            // Checkerboard horizon pattern to visually confirm active rendering
            for (int y = 0; y < buffer.height; ++y) {
                int horizonOffset = static_cast<int>(std::sin(y * 0.05f + t * 2.0f) * 10.0f);
                for (int x = 0; x < buffer.width; ++x) {
                    bool checker = ((x + horizonOffset) / 32 + (y / 32)) % 2 == 0;
                    if (y > buffer.height / 2 && checker) {
                        line[x] = bgColor ^ 0x00181818;
                    } else {
                        line[x] = bgColor;
                    }
                }
                line += buffer.stride;
            }

            ANativeWindow_unlockAndPost(g_nativeWindow);
            frameCount++;
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(16));
    }
    LOGI("GameRenderWorker exited");
}

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

    if (g_nativeWindow && !g_renderRunning.load()) {
        g_renderRunning.store(true);
        if (g_renderThread.joinable()) {
            g_renderThread.join();
        }
        g_renderThread = std::thread(GameRenderWorker);
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSurfaceDestroyed(JNIEnv* env, jobject thiz) {
    LOGI("Native window releasing: %p", g_nativeWindow);
    g_renderRunning.store(false);
    if (g_renderThread.joinable()) {
        g_renderThread.join();
    }
    if (g_nativeWindow) {
        ANativeWindow_release(g_nativeWindow);
        g_nativeWindow = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSetButton(JNIEnv* env, jobject thiz, jint buttonId, jboolean isPressed) {
    switch (buttonId) {
        case 0: // BTN_A (Gas / Accelerate)
            AndroidInput::SetButton(AndroidInput::kBtnA, isPressed);
            break;
        case 1: // BTN_B (Drift / Hop / Brake)
            AndroidInput::SetButton(AndroidInput::kBtnB, isPressed);
            break;
        case 2: // BTN_L (Use Item)
            AndroidInput::SetButton(AndroidInput::kBtnL, isPressed);
            break;
        case 3: // BTN_START (Pause / Plus)
            AndroidInput::SetButton(AndroidInput::kBtnPlus, isPressed);
            break;
        default:
            break;
    }
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeSetStick(JNIEnv* env, jobject thiz, jfloat stickX, jfloat stickY) {
    int8_t rawX = static_cast<int8_t>(std::clamp(stickX * 127.0f, -128.0f, 127.0f));
    int8_t rawY = static_cast<int8_t>(std::clamp(stickY * 127.0f, -128.0f, 127.0f));
    AndroidInput::SetStick(rawX, rawY);
}

JNIEXPORT void JNICALL
Java_com_wiicompiled_mkw_GameActivity_nativeTouchEvent(JNIEnv* env, jobject thiz, jint action, jfloat x, jfloat y, jint pointerId) {
    bool isDown = (action == 0 || action == 2);
    if (x > 1400.0f && y > 700.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnA, isDown);
    } else if (x > 1400.0f && y <= 700.0f) {
        AndroidInput::SetButton(AndroidInput::kBtnB, isDown);
    } else if (x < 600.0f && y < 400.0f) {
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
