/**
 * model_manager.h / model_manager.cpp
 * Gestor de modelos cargados en memoria.
 * Mantiene caché de modelos activos y gestiona recursos de la GPU/CPU.
 */

#pragma once
#include <string>
#include <memory>
#include <unordered_map>
#include <android/log.h>

#define LOG_TAG_MM "ModelManager"
#define LOGI_MM(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_MM, __VA_ARGS__)
#define LOGE_MM(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_MM, __VA_ARGS__)

/**
 * ModelType — Tipo de modelo
 */
enum class ModelType {
    LLM,        // Modelo de lenguaje (llama.cpp)
    IMAGE,      // Generación de imágenes (stable-diffusion.cpp)
    VISION      // Visión artificial (Moondream 2)
};

/**
 * ModelInfo — Metadatos de un modelo cargado
 */
struct LoadedModelInfo {
    std::string model_id;
    std::string model_path;
    ModelType type;
    size_t memory_usage_bytes;
    bool is_active;
    long loaded_at_ms;
};

/**
 * ModelManager — Singleton para gestión de modelos
 */
class ModelManager {
public:
    static ModelManager& getInstance() {
        static ModelManager instance;
        return instance;
    }

    ModelManager(const ModelManager&) = delete;
    ModelManager& operator=(const ModelManager&) = delete;

    /**
     * Registrar un modelo como cargado
     */
    void registerModel(
        const std::string& model_id,
        const std::string& model_path,
        ModelType type,
        size_t memory_usage
    ) {
        LoadedModelInfo info;
        info.model_id = model_id;
        info.model_path = model_path;
        info.type = type;
        info.memory_usage_bytes = memory_usage;
        info.is_active = true;
        info.loaded_at_ms = getCurrentTimeMs();

        loaded_models_[model_id] = info;
        LOGI_MM("Modelo registrado: %s (%.1f MB)",
            model_id.c_str(),
            memory_usage / (1024.0 * 1024.0));
    }

    /**
     * Desregistrar un modelo
     */
    void unregisterModel(const std::string& model_id) {
        auto it = loaded_models_.find(model_id);
        if (it != loaded_models_.end()) {
            LOGI_MM("Modelo liberado: %s", model_id.c_str());
            loaded_models_.erase(it);
        }
    }

    /**
     * Obtener uso total de memoria por todos los modelos
     */
    size_t getTotalMemoryUsage() const {
        size_t total = 0;
        for (const auto& pair : loaded_models_) {
            total += pair.second.memory_usage_bytes;
        }
        return total;
    }

    /**
     * Verificar si hay suficiente memoria para cargar un nuevo modelo
     * Estimación conservadora: modelo_size * 1.2 disponible
     */
    bool hasEnoughMemory(size_t required_bytes) const {
        // En Android, leer /proc/meminfo para memoria disponible
        FILE* f = fopen("/proc/meminfo", "r");
        if (!f) return true; // Asumir suficiente si no podemos leer

        char line[256];
        long available_kb = 0;
        while (fgets(line, sizeof(line), f)) {
            if (strncmp(line, "MemAvailable:", 13) == 0) {
                sscanf(line + 13, "%ld", &available_kb);
                break;
            }
        }
        fclose(f);

        size_t available_bytes = (size_t)available_kb * 1024;
        size_t needed = (size_t)(required_bytes * 1.3); // 30% overhead

        LOGI_MM("Memoria disponible: %.1f MB, Necesaria: %.1f MB",
            available_bytes / (1024.0 * 1024.0),
            needed / (1024.0 * 1024.0));

        return available_bytes >= needed;
    }

    int getLoadedModelCount() const { return (int)loaded_models_.size(); }

    bool isModelLoaded(const std::string& model_id) const {
        return loaded_models_.find(model_id) != loaded_models_.end();
    }

private:
    ModelManager() = default;
    std::unordered_map<std::string, LoadedModelInfo> loaded_models_;

    long getCurrentTimeMs() const {
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        return (long)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
    }
};
