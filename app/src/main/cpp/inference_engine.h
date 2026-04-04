/**
 * inference_engine.h
 * Motor de inferencia para llama.cpp en Android
 */

#pragma once
#include <string>
#include <functional>
#include <vector>
#include "llama.h"

struct ModelParams {
    std::string model_path;
    int n_ctx = 4096;
    int n_threads = 4;
    int n_gpu_layers = 0;
    bool use_mmap = true;
    bool use_mlock = false;
    int seed = -1;
};

struct GenerationParams {
    std::string prompt;
    int max_new_tokens = 2048;
    float temperature = 0.7f;
    float top_p = 0.9f;
    float top_k = 40.0f;
    float repeat_penalty = 1.1f;
    bool stream = true;
};

class InferenceEngine {
public:
    InferenceEngine();
    ~InferenceEngine();

    bool initialize(const ModelParams& params);
    void cleanup();

    // Generación completa (sin streaming)
    std::string generate(const GenerationParams& params);

    // Generación con streaming token a token
    void generate_streaming(
        const GenerationParams& params,
        std::function<void(const std::string&)> token_callback
    );

    // Utilidades
    int count_tokens(const std::string& text);
    std::string get_model_info_json();
    bool is_loaded() const { return model_ != nullptr; }

private:
    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
    ModelParams params_;

    std::vector<llama_token> tokenize(const std::string& text, bool add_bos);
    std::string token_to_str(llama_token token);
};
