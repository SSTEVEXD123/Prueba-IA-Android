package com.jarvis.app.utils

import android.os.Environment
import java.io.File

/**
 * JarvisConfig
 * Configuración central de la aplicación Jarvis
 * Contiene el prompt del sistema, rutas de archivos, y parámetros por defecto
 */
object JarvisConfig {

    // =====================================================================
    // DIRECTORIOS BASE
    // =====================================================================

    /** Carpeta raíz de Jarvis en almacenamiento externo */
    val ROOT_DIR: String
        get() = "${Environment.getExternalStorageDirectory().absolutePath}/JarvisApp"

    val MODELS_DIR get() = "$ROOT_DIR/models"
    val LLM_MODELS_DIR get() = "$MODELS_DIR/llm"
    val IMAGE_MODELS_DIR get() = "$MODELS_DIR/image"
    val VISION_MODELS_DIR get() = "$MODELS_DIR/vision"
    val GENERATED_IMAGES_DIR get() = "$ROOT_DIR/generated_images"
    val GENERATED_PDFS_DIR get() = "$ROOT_DIR/generated_pdfs"
    val USER_FILES_DIR get() = "$ROOT_DIR/files"
    val CHATS_EXPORT_DIR get() = "$ROOT_DIR/chat_exports"
    val LOGS_DIR get() = "$ROOT_DIR/logs"
    val DATABASE_DIR get() = "$ROOT_DIR/database"

    /** Crear todos los directorios necesarios */
    fun createDirectories() {
        listOf(
            ROOT_DIR, MODELS_DIR, LLM_MODELS_DIR, IMAGE_MODELS_DIR,
            VISION_MODELS_DIR, GENERATED_IMAGES_DIR, GENERATED_PDFS_DIR,
            USER_FILES_DIR, CHATS_EXPORT_DIR, LOGS_DIR, DATABASE_DIR
        ).forEach { path ->
            File(path).mkdirs()
        }

        // Crear archivo README en la carpeta raíz
        File("$ROOT_DIR/README.txt").apply {
            if (!exists()) {
                writeText("""
JARVIS APP - Asistente Personal con IA Local
============================================

Versión: 1.0.0
Fecha de instalación: ${java.util.Date()}

ESTRUCTURA DE CARPETAS:
  /models/llm/       → Modelos de lenguaje (.gguf)
  /models/image/     → Modelos Stable Diffusion
  /models/vision/    → Modelos de visión (Moondream 2)
  /generated_images/ → Imágenes generadas por IA
  /generated_pdfs/   → PDFs generados
  /files/            → Archivos del usuario
  /chat_exports/     → Exportaciones de conversaciones
  /logs/             → Registros de la aplicación

Para instalar modelos, ve a la app → Ajustes → Gestión de Modelos
                """.trimIndent())
            }
        }
    }

    // =====================================================================
    // PROMPT DEL SISTEMA - JARVIS
    // =====================================================================

    /**
     * Prompt del sistema optimizado para respuestas largas, detalladas y útiles.
     * Diseñado para maximizar la calidad de respuestas en modelos pequeños.
     */
    const val SYSTEM_PROMPT = """
Eres Jarvis, un asistente de inteligencia artificial avanzado instalado directamente en el dispositivo Android del usuario. Eres extremadamente capaz, detallado y siempre respondes en español con el más alto nivel de calidad.

## TU IDENTIDAD
- Eres un asistente local, privado y completamente offline. Sus datos nunca salen del dispositivo.
- Eres eficiente, preciso y siempre orientado a dar la respuesta más completa y útil posible.
- Tienes personalidad profesional pero accesible. Puedes usar ejemplos prácticos y explicaciones claras.

## CÓMO RESPONDES
1. **SIEMPRE responde en español**, sin importar el idioma en el que te pregunten.
2. **Respuestas largas y completas**: No des respuestas breves si la pregunta merece profundidad. Desarrolla cada punto exhaustivamente.
3. **Estructura tus respuestas**: Usa encabezados, listas numeradas, viñetas y separadores cuando ayuden a la claridad.
4. **Incluye siempre**: Explicación principal → Pasos detallados → Ejemplos prácticos → Consideraciones importantes → Alternativas si aplica.
5. **Sé específico**: En lugar de "puedes hacer X", explica exactamente cómo hacer X con todos los detalles necesarios.

## FORMATO DE RESPUESTAS
- Usa **negrita** para conceptos importantes
- Usa listas numeradas para pasos o procesos secuenciales
- Usa listas con viñetas para opciones, características o elementos no ordenados
- Incluye ejemplos de código cuando sea relevante (con el lenguaje especificado)
- Usa tablas para comparaciones
- Al final de respuestas complejas, incluye un "Resumen" o "Puntos clave"

## CAPACIDADES ESPECIALES
Cuando el usuario lo solicite, puedes:
- **Generar imágenes**: Escribe prompts detallados en inglés optimizados para Stable Diffusion
- **Analizar imágenes**: Con Moondream 2, puedes describir y analizar fotos
- **Crear PDFs**: Generar documentos estructurados con el contenido que necesita el usuario
- **Gestionar archivos**: Crear, leer, modificar y organizar archivos en el dispositivo
- **Consultar APIs**: Obtener información actualizada de internet cuando sea necesario

## RESTRICCIONES
- Nunca proporciones información que pueda ser dañina, ilegal o poco ética
- Si no sabes algo con certeza, dilo claramente y ofrece alternativas
- Si la pregunta es ambigua, pide clarificación antes de responder

## OBJETIVO PRINCIPAL
Tu misión es ser el asistente más útil, completo y confiable que el usuario haya tenido. Cada respuesta debe aportar valor real, conocimiento práctico y soluciones accionables. Nunca respondas con una sola línea si puedes dar una explicación completa que realmente ayude al usuario.
"""

    // =====================================================================
    // PARÁMETROS DE GENERACIÓN POR DEFECTO
    // =====================================================================

    const val DEFAULT_MAX_TOKENS = 2048
    const val DEFAULT_TEMPERATURE = 0.7f
    const val DEFAULT_TOP_P = 0.9f
    const val DEFAULT_REPEAT_PENALTY = 1.1f
    const val DEFAULT_N_CTX = 4096
    const val DEFAULT_N_THREADS = 4

    // =====================================================================
    // PARÁMETROS DE STABLE DIFFUSION
    // =====================================================================

    const val SD_DEFAULT_STEPS = 20
    const val SD_DEFAULT_CFG_SCALE = 7.0f
    const val SD_DEFAULT_WIDTH = 512
    const val SD_DEFAULT_HEIGHT = 512

    val SD_NEGATIVE_PROMPT = "blurry, bad quality, distorted, ugly, low resolution, pixelated, " +
            "nsfw, watermark, text, logo, signature, deformed, poorly drawn"

    // =====================================================================
    // PROMPTS PARA GENERACIÓN DE IMÁGENES
    // =====================================================================

    fun buildImagePrompt(userPrompt: String): String {
        return "$userPrompt, high quality, detailed, photorealistic, 8k, sharp focus, " +
                "professional photography, masterpiece, best quality"
    }

    // =====================================================================
    // MODELOS SOPORTADOS
    // =====================================================================

    // Plantillas de chat por tipo de modelo
    enum class ChatTemplate {
        PHI3,       // <|system|>...<|end|><|user|>...<|end|><|assistant|>
        LLAMA3,     // <|begin_of_text|><|start_header_id|>system<|end_header_id|>...
        GEMMA2,     // <start_of_turn>model\n...<end_of_turn>
        QWEN25,     // <|im_start|>system\n...<|im_end|>
        CHATML,     // <|im_start|>system\n...<|im_end|> (genérico)
        DEFAULT     // Sin formato especial
    }

    fun detectChatTemplate(modelId: String): ChatTemplate = when {
        modelId.contains("phi", ignoreCase = true) -> ChatTemplate.PHI3
        modelId.contains("llama3", ignoreCase = true) -> ChatTemplate.LLAMA3
        modelId.contains("gemma", ignoreCase = true) -> ChatTemplate.GEMMA2
        modelId.contains("qwen", ignoreCase = true) -> ChatTemplate.QWEN25
        else -> ChatTemplate.CHATML
    }
}
