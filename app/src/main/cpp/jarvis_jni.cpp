/**
 * jarvis_jni.cpp
 * Puente JNI entre Kotlin/Java y llama.cpp
 * Maneja: carga de modelos, inferencia, streaming de tokens
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"
#include "model_manager.h"
#include "inference_engine.h"

#define LOG_TAG "JarvisJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Instancia global del motor de inferencia
static InferenceEngine* g_engine = nullptr;
static JavaVM* g_jvm = nullptr;

// Callback global para streaming
static jobject g_callback_object = nullptr;
static jmethodID g_callback_method = nullptr;

extern "C" {

/**
 * Inicializar el motor de inferencia
 */
JNIEXPORT jlong JNICALL
Java_com_jarvis_app_ai_llm_LlamaEngine_nativeInit(
        JNIEnv* env,
        jobject /* this */,
        jstring model_path,
        jint n_ctx,
        jint n_threads,
        jint n_gpu_layers
) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Inicializando modelo: %s", path);

    // Parámetros del modelo
    ModelParams params;
    params.model_path = std::string(path);
    params.n_ctx = n_ctx > 0 ? n_ctx : 4096;
    params.n_threads = n_threads > 0 ? n_threads : 4;
    params.n_gpu_layers = n_gpu_layers;
    params.use_mmap = true;
    params.use_mlock = false;

    env->ReleaseStringUTFChars(model_path, path);

    // Crear motor
    InferenceEngine* engine = new InferenceEngine();
    if (!engine->initialize(params)) {
        LOGE("Error al cargar el modelo");
        delete engine;
        return 0;
    }

    LOGI("Modelo cargado exitosamente");
    return reinterpret_cast<jlong>(engine);
}

/**
 * Ejecutar inferencia con streaming de tokens
 */
JNIEXPORT jstring JNICALL
Java_com_jarvis_app_ai_llm_LlamaEngine_nativeGenerate(
        JNIEnv* env,
        jobject /* this */,
        jlong engine_ptr,
        jstring prompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jfloat repeat_penalty,
        jobject callback
) {
    if (engine_ptr == 0) {
        return env->NewStringUTF("[ERROR] Motor no inicializado");
    }

    InferenceEngine* engine = reinterpret_cast<InferenceEngine*>(engine_ptr);
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);

    // Parámetros de generación
    GenerationParams gen_params;
    gen_params.prompt = std::string(prompt_str);
    gen_params.max_new_tokens = max_tokens > 0 ? max_tokens : 2048;
    gen_params.temperature = temperature > 0 ? temperature : 0.7f;
    gen_params.top_p = top_p > 0 ? top_p : 0.9f;
    gen_params.repeat_penalty = repeat_penalty > 0 ? repeat_penalty : 1.1f;

    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Configurar callback de streaming si se proporcionó
    std::string full_response;
    if (callback != nullptr) {
        jclass callback_class = env->GetObjectClass(callback);
        jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        jmethodID on_done = env->GetMethodID(callback_class, "onComplete", "()V");

        // Streaming token por token
        engine->generate_streaming(gen_params, [&](const std::string& token) {
            JNIEnv* jni_env;
            bool attached = false;
            if (g_jvm->GetEnv((void**)&jni_env, JNI_VERSION_1_6) != JNI_OK) {
                g_jvm->AttachCurrentThread(&jni_env, nullptr);
                attached = true;
            }

            jstring jtoken = jni_env->NewStringUTF(token.c_str());
            jni_env->CallVoidMethod(callback, on_token, jtoken);
            jni_env->DeleteLocalRef(jtoken);

            if (attached) g_jvm->DetachCurrentThread();

            full_response += token;
        });

        JNIEnv* jni_env;
        if (g_jvm->GetEnv((void**)&jni_env, JNI_VERSION_1_6) == JNI_OK) {
            jni_env->CallVoidMethod(callback, on_done);
        }
    } else {
        full_response = engine->generate(gen_params);
    }

    return env->NewStringUTF(full_response.c_str());
}

/**
 * Liberar recursos del modelo
 */
JNIEXPORT void JNICALL
Java_com_jarvis_app_ai_llm_LlamaEngine_nativeRelease(
        JNIEnv* /* env */,
        jobject /* this */,
        jlong engine_ptr
) {
    if (engine_ptr != 0) {
        InferenceEngine* engine = reinterpret_cast<InferenceEngine*>(engine_ptr);
        engine->cleanup();
        delete engine;
        LOGI("Motor liberado correctamente");
    }
}

/**
 * Obtener información del modelo cargado
 */
JNIEXPORT jstring JNICALL
Java_com_jarvis_app_ai_llm_LlamaEngine_nativeGetModelInfo(
        JNIEnv* env,
        jobject /* this */,
        jlong engine_ptr
) {
    if (engine_ptr == 0) {
        return env->NewStringUTF("{}");
    }

    InferenceEngine* engine = reinterpret_cast<InferenceEngine*>(engine_ptr);
    std::string info = engine->get_model_info_json();
    return env->NewStringUTF(info.c_str());
}

/**
 * Tokenizar texto (para contar tokens)
 */
JNIEXPORT jint JNICALL
Java_com_jarvis_app_ai_llm_LlamaEngine_nativeCountTokens(
        JNIEnv* env,
        jobject /* this */,
        jlong engine_ptr,
        jstring text
) {
    if (engine_ptr == 0) return -1;

    InferenceEngine* engine = reinterpret_cast<InferenceEngine*>(engine_ptr);
    const char* text_str = env->GetStringUTFChars(text, nullptr);
    int count = engine->count_tokens(std::string(text_str));
    env->ReleaseStringUTFChars(text, text_str);
    return count;
}

/**
 * Inicialización de la JVM para callbacks
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    g_jvm = vm;
    LOGI("JarvisJNI cargado, versión llama.cpp inicializada");
    return JNI_VERSION_1_6;
}

} // extern "C"
