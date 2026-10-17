#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "zyx_qemu_api.h"

static pthread_mutex_t state_mutex = PTHREAD_MUTEX_INITIALIZER;
static void *qemu_handle = NULL;
static const ZyxQemuApi *qemu_api = NULL;
static int qemu_running = 0;
static int qemu_starting = 0;

static int fail_start(int code) {
    pthread_mutex_lock(&state_mutex);
    qemu_starting = 0;
    pthread_mutex_unlock(&state_mutex);
    return code;
}

static void release_args(JNIEnv *env, jobjectArray java_args, char **argv, jsize argc) {
    if (argv == NULL) return;
    for (jsize i = 0; i < argc; ++i) {
        if (argv[i] != NULL) {
            jstring item = (jstring)(*env)->GetObjectArrayElement(env, java_args, i);
            if (item != NULL) {
                (*env)->ReleaseStringUTFChars(env, item, argv[i]);
                (*env)->DeleteLocalRef(env, item);
            }
        }
    }
    free(argv);
}

JNIEXPORT jint JNICALL
Java_app_aemu_core_ZyxQemuSystemBridge_nativeRun(JNIEnv *env, jobject self,
                                                  jstring library_path,
                                                  jobjectArray java_args) {
    (void)self;
    if (library_path == NULL || java_args == NULL) return -1;

    pthread_mutex_lock(&state_mutex);
    if (qemu_running || qemu_starting || qemu_handle != NULL) {
        pthread_mutex_unlock(&state_mutex);
        return -2;
    }
    qemu_starting = 1;
    pthread_mutex_unlock(&state_mutex);

    const char *path = (*env)->GetStringUTFChars(env, library_path, NULL);
    if (path == NULL) return fail_start(-3);
    void *handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    (*env)->ReleaseStringUTFChars(env, library_path, path);
    if (handle == NULL) return fail_start(-4);

    dlerror();
    const ZyxQemuApi *(*get_api)(uint32_t) = (const ZyxQemuApi *(*)(uint32_t))dlsym(handle, "zyx_qemu_get_api");
    const char *symbol_error = dlerror();
    if (symbol_error != NULL || get_api == NULL) {
        dlclose(handle);
        return fail_start(-5);
    }
    const ZyxQemuApi *api = get_api(ZYX_QEMU_API_ABI);
    if (api == NULL || api->abi_version != ZYX_QEMU_API_ABI || api->run == NULL ||
        api->request_shutdown == NULL || api->request_reset == NULL || api->version == NULL) {
        dlclose(handle);
        return fail_start(-6);
    }

    jsize argc = (*env)->GetArrayLength(env, java_args);
    char **argv = (char **)calloc((size_t)argc + 1u, sizeof(char *));
    if (argv == NULL) {
        dlclose(handle);
        return fail_start(-7);
    }
    for (jsize i = 0; i < argc; ++i) {
        jstring item = (jstring)(*env)->GetObjectArrayElement(env, java_args, i);
        if (item == NULL) {
            release_args(env, java_args, argv, argc);
            dlclose(handle);
            return fail_start(-8);
        }
        const char *utf = (*env)->GetStringUTFChars(env, item, NULL);
        if (utf == NULL) {
            (*env)->DeleteLocalRef(env, item);
            release_args(env, java_args, argv, i);
            dlclose(handle);
            return fail_start(-9);
        }
        argv[i] = (char *)utf;
        (*env)->DeleteLocalRef(env, item);
    }

    pthread_mutex_lock(&state_mutex);
    qemu_handle = handle;
    qemu_api = api;
    qemu_starting = 0;
    qemu_running = 1;
    pthread_mutex_unlock(&state_mutex);

    int result = api->run((int)argc, (const char *const *)argv);

    pthread_mutex_lock(&state_mutex);
    qemu_running = 0;
    qemu_api = NULL;
    qemu_handle = NULL;
    pthread_mutex_unlock(&state_mutex);

    release_args(env, java_args, argv, argc);
    dlclose(handle);
    return (jint)result;
}

JNIEXPORT jint JNICALL
Java_app_aemu_core_ZyxQemuSystemBridge_nativeRequestStop(JNIEnv *env, jobject self) {
    (void)env; (void)self;
    pthread_mutex_lock(&state_mutex);
    const ZyxQemuApi *api = qemu_api;
    const int running = qemu_running;
    pthread_mutex_unlock(&state_mutex);
    return running && api != NULL ? (jint)api->request_shutdown() : -1;
}

JNIEXPORT jint JNICALL
Java_app_aemu_core_ZyxQemuSystemBridge_nativeRequestReset(JNIEnv *env, jobject self) {
    (void)env; (void)self;
    pthread_mutex_lock(&state_mutex);
    const ZyxQemuApi *api = qemu_api;
    const int running = qemu_running;
    pthread_mutex_unlock(&state_mutex);
    return running && api != NULL ? (jint)api->request_reset() : -1;
}

JNIEXPORT jboolean JNICALL
Java_app_aemu_core_ZyxQemuSystemBridge_nativeIsRunning(JNIEnv *env, jobject self) {
    (void)env; (void)self;
    pthread_mutex_lock(&state_mutex);
    const int running = qemu_running || qemu_starting;
    pthread_mutex_unlock(&state_mutex);
    return running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_app_aemu_core_ZyxQemuSystemBridge_nativeVersion(JNIEnv *env, jobject self) {
    (void)self;
    pthread_mutex_lock(&state_mutex);
    const ZyxQemuApi *api = qemu_api;
    const char *version = qemu_running && api != NULL ? api->version() : NULL;
    jstring result = version != NULL ? (*env)->NewStringUTF(env, version) : (*env)->NewStringUTF(env, "indisponível");
    pthread_mutex_unlock(&state_mutex);
    return result;
}
