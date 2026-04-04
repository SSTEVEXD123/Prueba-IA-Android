#!/bin/bash
# =============================================================================
# download_models.sh
# Descarga modelos GGUF para Jarvis App en tu PC
# y los transfiere al dispositivo Android vía ADB
# Ejecutar: bash scripts/download_models.sh
# =============================================================================

set -e

DEVICE_BASE="/sdcard/JarvisApp"
DEVICE_LLM="$DEVICE_BASE/models/llm"
DEVICE_IMAGE="$DEVICE_BASE/models/image"
DEVICE_VISION="$DEVICE_BASE/models/vision"
TEMP_DIR="/tmp/jarvis_models"

mkdir -p "$TEMP_DIR"

echo "=================================================="
echo "  Jarvis App - Descarga de Modelos"
echo "=================================================="
echo ""

# ─── Colores para terminal ───
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ─── Verificar ADB ───
if ! command -v adb &>/dev/null; then
    echo -e "${YELLOW}⚠️  ADB no encontrado. Los modelos se descargarán localmente en $TEMP_DIR${NC}"
    echo "   Cópialos manualmente a /JarvisApp/models/ en tu dispositivo."
    USE_ADB=false
else
    # Verificar dispositivo conectado
    DEVICE=$(adb devices | grep -v "List" | grep "device$" | head -1 | cut -f1)
    if [ -z "$DEVICE" ]; then
        echo -e "${YELLOW}⚠️  No hay dispositivo Android conectado. Descargando localmente…${NC}"
        USE_ADB=false
    else
        echo -e "${GREEN}✅ Dispositivo conectado: $DEVICE${NC}"
        USE_ADB=true
        # Crear directorios en el dispositivo
        adb shell "mkdir -p $DEVICE_LLM $DEVICE_IMAGE $DEVICE_VISION"
    fi
fi

echo ""

# ─── Función de descarga ───
download_model() {
    local name="$1"
    local url="$2"
    local dest_dir="$3"
    local filename=$(basename "$url")
    local local_path="$TEMP_DIR/$filename"

    echo -e "${BLUE}📥 Descargando: $name${NC}"
    echo "   URL: $url"
    echo "   Tamaño estimado: $4"
    echo ""

    if [ -f "$local_path" ]; then
        echo -e "${GREEN}   ✅ Ya existe localmente: $local_path${NC}"
    else
        if command -v wget &>/dev/null; then
            wget -q --show-progress -O "$local_path" "$url" || {
                echo -e "${RED}   ❌ Error al descargar. Intenta manualmente.${NC}"
                return 1
            }
        elif command -v curl &>/dev/null; then
            curl -L --progress-bar -o "$local_path" "$url" || {
                echo -e "${RED}   ❌ Error al descargar.${NC}"
                return 1
            }
        else
            echo -e "${RED}❌ Instala wget o curl primero.${NC}"
            return 1
        fi
    fi

    echo -e "${GREEN}   ✅ Descargado: $local_path${NC}"

    if [ "$USE_ADB" = true ]; then
        echo "   📲 Transfiriendo al dispositivo…"
        adb push "$local_path" "$dest_dir/$filename" && \
            echo -e "${GREEN}   ✅ Transferido a $dest_dir/$filename${NC}" || \
            echo -e "${RED}   ❌ Error al transferir${NC}"
    fi

    echo ""
}

# ─── Menú de selección ───
echo "Selecciona los modelos a descargar:"
echo ""
echo "  [1] Phi-3 Mini 3.8B Q4 (2.4 GB) - Recomendado para la mayoría"
echo "  [2] Gemma 2 2B Q5      (1.9 GB) - Compacto y preciso"
echo "  [3] Llama 3.2 3B Q4    (2.1 GB) - Meta, muy equilibrado"
echo "  [4] Qwen 2.5 1.5B Q6   (1.1 GB) - Ultra ligero"
echo "  [5] SmolLM2 360M Q8    (400 MB) - El más ligero"
echo "  [6] SD Turbo Q8        (1.6 GB) - Stable Diffusion"
echo "  [7] Moondream 2 Q4     (1.8 GB) - Visión"
echo "  [A] Todos los modelos"
echo "  [Q] Salir"
echo ""
read -r -p "Selección (ej: 1 2 3 o A): " SELECTION

echo ""

download_if_selected() {
    local num="$1"
    local name="$2"
    local url="$3"
    local dir="$4"
    local size="$5"

    if [[ "$SELECTION" == *"$num"* ]] || [[ "$SELECTION" == "A" ]] || [[ "$SELECTION" == "a" ]]; then
        download_model "$name" "$url" "$dir" "$size"
    fi
}

# LLM Models
download_if_selected "1" "Phi-3 Mini 3.8B Q4" \
    "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf" \
    "$DEVICE_LLM" "~2.4 GB"

download_if_selected "2" "Gemma 2 2B Q5" \
    "https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q5_K_M.gguf" \
    "$DEVICE_LLM" "~1.9 GB"

download_if_selected "3" "Llama 3.2 3B Q4" \
    "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf" \
    "$DEVICE_LLM" "~2.1 GB"

download_if_selected "4" "Qwen 2.5 1.5B Q6" \
    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q6_k.gguf" \
    "$DEVICE_LLM" "~1.1 GB"

download_if_selected "5" "SmolLM2 360M Q8" \
    "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf" \
    "$DEVICE_LLM" "~400 MB"

# Image Models
download_if_selected "6" "SD Turbo Q8" \
    "https://huggingface.co/rupeshs/sd-turbo-ncnn/resolve/main/sd-turbo-q8_0.gguf" \
    "$DEVICE_IMAGE" "~1.6 GB"

# Vision Models
download_if_selected "7" "Moondream 2 Q4" \
    "https://huggingface.co/vikhyatk/moondream2/resolve/main/moondream2-q4_0.gguf" \
    "$DEVICE_VISION" "~1.8 GB"

echo "=================================================="
echo -e "${GREEN}  ✅ Proceso completado${NC}"
echo "=================================================="
echo ""

if [ "$USE_ADB" = false ]; then
    echo "Los modelos están en: $TEMP_DIR"
    echo ""
    echo "Para transferirlos manualmente:"
    echo "  1. Conecta tu Android por USB"
    echo "  2. Activa la transferencia de archivos (MTP)"
    echo "  3. Copia los .gguf a:"
    echo "     - LLM → /JarvisApp/models/llm/"
    echo "     - Imagen → /JarvisApp/models/image/"
    echo "     - Visión → /JarvisApp/models/vision/"
    echo "  4. Abre Jarvis App → Modelos → Activar"
fi
echo ""
