#!/bin/bash
# =============================================================================
# setup_llama_cpp.sh
# Clona y prepara llama.cpp para compilar con Android NDK
# Ejecutar desde la raíz del proyecto: bash scripts/setup_llama_cpp.sh
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CPP_DIR="$PROJECT_DIR/app/src/main/cpp"

echo "=================================================="
echo "  Jarvis App - Configuración de llama.cpp"
echo "=================================================="
echo ""

# ─── 1. Verificar herramientas ───
check_tool() {
    if ! command -v "$1" &>/dev/null; then
        echo "❌ '$1' no encontrado. Instálalo primero."
        exit 1
    fi
    echo "✅ $1 encontrado"
}

check_tool git
check_tool cmake

echo ""

# ─── 2. Clonar llama.cpp dentro del proyecto ───
LLAMA_DIR="$CPP_DIR/llama.cpp"

if [ -d "$LLAMA_DIR" ]; then
    echo "📦 llama.cpp ya existe. Actualizando…"
    cd "$LLAMA_DIR"
    git pull origin master
else
    echo "📦 Clonando llama.cpp…"
    git clone --depth=1 https://github.com/ggerganov/llama.cpp.git "$LLAMA_DIR"
fi

echo ""
echo "✅ llama.cpp listo en: $LLAMA_DIR"

# ─── 3. Clonar stable-diffusion.cpp ───
SD_DIR="$CPP_DIR/stable-diffusion.cpp"

if [ -d "$SD_DIR" ]; then
    echo "📦 stable-diffusion.cpp ya existe. Actualizando…"
    cd "$SD_DIR"
    git pull origin master
else
    echo "📦 Clonando stable-diffusion.cpp…"
    git clone --depth=1 https://github.com/leejet/stable-diffusion.cpp.git "$SD_DIR"
fi

echo "✅ stable-diffusion.cpp listo en: $SD_DIR"

# ─── 4. Verificar NDK ───
echo ""
echo "🔍 Verificando Android NDK…"

if [ -n "$ANDROID_NDK_HOME" ]; then
    echo "✅ NDK encontrado: $ANDROID_NDK_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ]; then
    NDK_PATH=$(ls -d "$ANDROID_SDK_ROOT/ndk/"* 2>/dev/null | tail -1)
    if [ -n "$NDK_PATH" ]; then
        export ANDROID_NDK_HOME="$NDK_PATH"
        echo "✅ NDK encontrado: $NDK_PATH"
    else
        echo "⚠️  NDK no encontrado. Instálalo desde Android Studio:"
        echo "    SDK Manager → SDK Tools → NDK (Side by side)"
    fi
else
    echo "⚠️  NDK no encontrado. Configura ANDROID_NDK_HOME."
fi

# ─── 5. Actualizar CMakeLists con rutas correctas ───
echo ""
echo "📝 Generando includes necesarios…"

mkdir -p "$CPP_DIR/include"

cat > "$CPP_DIR/include/model_manager.h" << 'EOF'
#pragma once
#include <string>

struct ModelParams {
    std::string model_path;
    int n_ctx = 4096;
    int n_threads = 4;
    int n_gpu_layers = 0;
    bool use_mmap = true;
    bool use_mlock = false;
    int seed = -1;
};
EOF

echo ""
echo "=================================================="
echo "  ✅ Setup completo"
echo "=================================================="
echo ""
echo "Próximos pasos:"
echo "  1. Abre Android Studio"
echo "  2. File → Open → Selecciona la carpeta JarvisApp/"
echo "  3. Espera a que Gradle sincronice"
echo "  4. Build → Make Project"
echo "  5. Run en tu dispositivo Android (API 26+)"
echo ""
echo "Para descargar modelos, ejecuta:"
echo "  bash scripts/download_models.sh"
echo ""
