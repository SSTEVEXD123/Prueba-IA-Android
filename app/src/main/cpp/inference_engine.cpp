/**
 * inference_engine.cpp
 * Implementación del motor de inferencia llama.cpp para Android
 */

#include "inference_engine.h"
#include <android/log.h>
#include <sstream>
#include <stdexcept>

#define LOG_TAG "InferenceEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

InferenceEngine::InferenceEngine() : model_(nullptr), ctx_(nullptr) {
    llama_backend_init();
    LOGI("Backend llama.cpp inicializado");
}

InferenceEngine::~InferenceEngine() {
    cleanup();
    llama_backend_free();
}

bool InferenceEngine::initialize(const ModelParams& params) {
    params_ = params;

    // Parámetros del modelo
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = params.n_gpu_layers;
    model_params.use_mmap = params.use_mmap;
    model_params.use_mlock = params.use_mlock;

    // Cargar modelo GGUF
    model_ = llama_load_model_from_file(params.model_path.c_str(), model_params);
    if (!model_) {
        LOGE("No se pudo cargar el modelo desde: %s", params.model_path.c_str());
        return false;
    }

    // Parámetros del contexto
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = params.n_ctx;
    ctx_params.n_threads = params.n_threads;
    ctx_params.n_threads_batch = params.n_threads;
    ctx_params.seed = params.seed == -1 ? time(nullptr) : params.seed;

    // Crear contexto
    ctx_ = llama_new_context_with_model(model_, ctx_params);
    if (!ctx_) {
        LOGE("No se pudo crear el contexto del modelo");
        llama_free_model(model_);
        model_ = nullptr;
        return false;
    }

    LOGI("Modelo cargado: ctx=%d, threads=%d", params.n_ctx, params.n_threads);
    return true;
}

void InferenceEngine::cleanup() {
    if (ctx_) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_) {
        llama_free_model(model_);
        model_ = nullptr;
    }
}

std::vector<llama_token> InferenceEngine::tokenize(const std::string& text, bool add_bos) {
    int n_tokens = text.length() + (add_bos ? 1 : 0) + 1;
    std::vector<llama_token> tokens(n_tokens);
    n_tokens = llama_tokenize(model_, text.c_str(), text.length(),
                               tokens.data(), tokens.size(), add_bos, false);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        llama_tokenize(model_, text.c_str(), text.length(),
                      tokens.data(), tokens.size(), add_bos, false);
    } else {
        tokens.resize(n_tokens);
    }
    return tokens;
}

std::string InferenceEngine::token_to_str(llama_token token) {
    std::vector<char> buf(32);
    int n = llama_token_to_piece(model_, token, buf.data(), buf.size(), 0, false);
    if (n < 0) {
        buf.resize(-n);
        llama_token_to_piece(model_, token, buf.data(), buf.size(), 0, false);
        n = -n;
    }
    return std::string(buf.data(), n);
}

std::string InferenceEngine::generate(const GenerationParams& params) {
    std::string result;
    generate_streaming(params, [&](const std::string& token) {
        result += token;
    });
    return result;
}

void InferenceEngine::generate_streaming(
    const GenerationParams& params,
    std::function<void(const std::string&)> token_callback
) {
    if (!ctx_ || !model_) {
        token_callback("[ERROR] Modelo no cargado");
        return;
    }

    // Tokenizar el prompt
    std::vector<llama_token> tokens = tokenize(params.prompt, true);

    // Verificar longitud del contexto
    if ((int)tokens.size() >= params_.n_ctx) {
        LOGE("Prompt demasiado largo: %zu tokens, max=%d", tokens.size(), params_.n_ctx);
        token_callback("[ERROR] Prompt excede el contexto máximo");
        return;
    }

    // Limpiar KV cache
    llama_kv_cache_clear(ctx_);

    // Procesar el batch del prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size(), 0, 0);
    if (llama_decode(ctx_, batch)) {
        LOGE("Error al procesar el prompt");
        token_callback("[ERROR] Fallo al procesar el prompt");
        return;
    }

    // Configurar sampler
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(params.top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(params.temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
        llama_n_vocab(model_), llama_token_eos(model_),
        llama_token_nl(model_), 64, params.repeat_penalty, 0.0f, 0.0f, false, false
    ));
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    // Generación token por token
    int n_generated = 0;
    int n_cur = tokens.size();

    while (n_generated < params.max_new_tokens) {
        // Samplear siguiente token
        llama_token new_token = llama_sampler_sample(sampler, ctx_, -1);

        // Verificar token de fin
        if (llama_token_is_eog(model_, new_token)) {
            break;
        }

        // Convertir token a texto y emitir
        std::string token_str = token_to_str(new_token);
        token_callback(token_str);

        // Preparar siguiente iteración
        llama_batch next_batch = llama_batch_get_one(&new_token, 1, n_cur, 0);
        n_cur++;

        if (llama_decode(ctx_, next_batch)) {
            LOGE("Error al decodificar token %d", n_generated);
            break;
        }

        n_generated++;
    }

    llama_sampler_free(sampler);
    LOGI("Generación completada: %d tokens generados", n_generated);
}

int InferenceEngine::count_tokens(const std::string& text) {
    if (!model_) return -1;
    auto tokens = tokenize(text, false);
    return (int)tokens.size();
}

std::string InferenceEngine::get_model_info_json() {
    if (!model_) return "{}";
    std::ostringstream ss;
    ss << "{"
       << "\"n_vocab\":" << llama_n_vocab(model_) << ","
       << "\"n_ctx_train\":" << llama_n_ctx_train(model_) << ","
       << "\"n_embd\":" << llama_n_embd(model_) << ","
       << "\"n_layer\":" << llama_n_layer(model_) << ","
       << "\"model_type\":\"" << llama_model_desc(model_) << "\""
       << "}";
    return ss.str();
}
