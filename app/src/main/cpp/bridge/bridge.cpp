#include <jni.h>
#include <memory>
#include "common/Logger.h"
#include "backend/LwipBackend.h"

using dev::kartik::accessmanager::backend::NetworkBackend;
using dev::kartik::accessmanager::backend::LwipBackend;

// Global state for the backend instance
static std::unique_ptr<NetworkBackend> g_backend = nullptr;

// Cache JVM reference for callbacks
static JavaVM* g_jvm = nullptr;
static jobject g_callbacks_obj = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGD("JNI_OnLoad called.");
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGD("JNI_OnUnload called.");
    if (g_callbacks_obj != nullptr) {
        JNIEnv* env;
        if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(g_callbacks_obj);
            g_callbacks_obj = nullptr;
        }
    }
    g_jvm = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kartik_accessmanager_vpn_relay_jni_JniNativeBridge_nativeInit(JNIEnv *env, jobject thiz) {
    LOGI("nativeInit called.");
    if (g_callbacks_obj != nullptr) {
        env->DeleteGlobalRef(g_callbacks_obj);
    }
    g_callbacks_obj = env->NewGlobalRef(thiz);

    if (!g_backend) {
        g_backend = std::make_unique<LwipBackend>();
    }
    
    g_backend->initialize();
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kartik_accessmanager_vpn_relay_jni_JniNativeBridge_nativeStart(JNIEnv *env, jobject thiz) {
    LOGI("nativeStart called.");
    if (g_backend) {
        g_backend->start();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kartik_accessmanager_vpn_relay_jni_JniNativeBridge_nativeInjectUplink(JNIEnv *env, jobject thiz, jbyteArray packet_data, jint length) {
    if (g_backend && packet_data != nullptr) {
        // We obtain a pointer to the JVM array.
        // GetByteArrayElements may copy the array, or pin it in place.
        jbyte* buffer = env->GetByteArrayElements(packet_data, nullptr);
        if (buffer != nullptr) {
            g_backend->injectUplinkPacket(reinterpret_cast<const uint8_t*>(buffer), static_cast<size_t>(length));
            // Release the elements without copying back (JNI_ABORT) since it's read-only
            env->ReleaseByteArrayElements(packet_data, buffer, JNI_ABORT);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kartik_accessmanager_vpn_relay_jni_JniNativeBridge_nativeStop(JNIEnv *env, jobject thiz) {
    LOGI("nativeStop called.");
    if (g_backend) {
        g_backend->stop();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kartik_accessmanager_vpn_relay_jni_JniNativeBridge_nativeDestroy(JNIEnv *env, jobject thiz) {
    LOGI("nativeDestroy called.");
    if (g_backend) {
        g_backend->destroy();
        g_backend.reset();
    }
    if (g_callbacks_obj != nullptr) {
        env->DeleteGlobalRef(g_callbacks_obj);
        g_callbacks_obj = nullptr;
    }
}
