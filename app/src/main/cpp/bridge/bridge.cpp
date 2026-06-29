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

// Global functions to notify Kotlin of states from any thread
void notify_native_state(int state) {
    if (!g_jvm || !g_callbacks_obj) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        attached = true;
    }
    
    if (env) {
        jclass clazz = env->GetObjectClass(g_callbacks_obj);
        jmethodID method = env->GetMethodID(clazz, "jniOnNativeStateChanged", "(I)V");
        if (method) {
            env->CallVoidMethod(g_callbacks_obj, method, state);
        }
        env->DeleteLocalRef(clazz);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
}

void notify_native_error(int code, const char* msg) {
    if (!g_jvm || !g_callbacks_obj) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        attached = true;
    }
    
    if (env) {
        jclass clazz = env->GetObjectClass(g_callbacks_obj);
        jmethodID method = env->GetMethodID(clazz, "jniOnNativeError", "(ILjava/lang/String;)V");
        if (method) {
            jstring jmsg = env->NewStringUTF(msg);
            env->CallVoidMethod(g_callbacks_obj, method, code, jmsg);
            env->DeleteLocalRef(jmsg);
        }
        env->DeleteLocalRef(clazz);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
}

void notify_native_metrics(long pkts_rx, long pkts_rej, long pbuf_allocs, long pbuf_fails, long q_depth, long avg_mbox_lat, long avg_proc_lat) {
    if (!g_jvm || !g_callbacks_obj) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        attached = true;
    }
    
    if (env) {
        jclass clazz = env->GetObjectClass(g_callbacks_obj);
        jmethodID method = env->GetMethodID(clazz, "jniOnNativeMetrics", "(JJJJJJJ)V");
        if (method) {
            env->CallVoidMethod(g_callbacks_obj, method, pkts_rx, pkts_rej, pbuf_allocs, pbuf_fails, q_depth, avg_mbox_lat, avg_proc_lat);
        }
        env->DeleteLocalRef(clazz);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
}

void notify_downlink_packet(const uint8_t* data, size_t length) {
    if (!g_jvm || !g_callbacks_obj) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        attached = true;
    }
    
    if (env) {
        jbyteArray arr = env->NewByteArray(length);
        if (arr) {
            env->SetByteArrayRegion(arr, 0, length, reinterpret_cast<const jbyte*>(data));
            jclass clazz = env->GetObjectClass(g_callbacks_obj);
            jmethodID method = env->GetMethodID(clazz, "jniOnDownlinkPacketReceived", "([BI)V");
            if (method) {
                env->CallVoidMethod(g_callbacks_obj, method, arr, static_cast<jint>(length));
            }
            env->DeleteLocalRef(clazz);
            env->DeleteLocalRef(arr);
        }
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
}

bool jni_protect_socket(int fd) {
    if (!g_jvm || !g_callbacks_obj) return false;
    JNIEnv* env = nullptr;
    bool attached = false;
    
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        attached = true;
    }
    
    bool result = false;
    if (env) {
        jclass clazz = env->GetObjectClass(g_callbacks_obj);
        jmethodID method = env->GetMethodID(clazz, "jniProtectSocket", "(I)Z");
        if (method) {
            result = env->CallBooleanMethod(g_callbacks_obj, method, fd);
        }
        env->DeleteLocalRef(clazz);
        if (attached) {
            g_jvm->DetachCurrentThread();
        }
    }
    return result;
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
    if (!g_backend) return;
    if (packet_data == nullptr || length <= 0) return;
    
    jsize arr_len = env->GetArrayLength(packet_data);
    if (length > arr_len || length > 1500) {
        return; // Malformed / Oversized
    }
    
    jbyte* buffer = env->GetByteArrayElements(packet_data, nullptr);
    if (buffer != nullptr) {
        g_backend->injectUplinkPacket(reinterpret_cast<const uint8_t*>(buffer), static_cast<size_t>(length));
        // Release the elements without copying back (JNI_ABORT) since it's read-only
        env->ReleaseByteArrayElements(packet_data, buffer, JNI_ABORT);
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
