package com.jarvis.app.ai.llm

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.jarvis.app.utils.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * LlamaEngine
 * Wrapper Kotlin para el motor de inferencia llama.cpp (JNI)
 * Gestiona carga de modelos, generación de texto y streaming
 */
class LlamaEngine private constructor() {

    companion object {
        private const val TAG = "LlamaEngine"
        private var INSTANCE: LlamaEngine? = null

        fun getInstance(): LlamaEngine {
            return INSTANCE ?: synchronized(this) {
                LlamaEngine().also { INSTANCE = it }
            }
        }

        // Cargar librería nativa
        init {
            try {
                System.loadLibrary("jarvis_jni")
                Log.i(TAG, "Librería nativa cargada correctamente")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Error al cargar librería nativa: ${e.message}")
            }
        }
    }

    // Handle del motor nativo
    private var engineHandle: Long = 0
    private var currentModelPath: String = ""
    private var isGenerating: Boolean = false

    // =====================================================================
    // JNI Declarations
    // =====================================================================

    private external fun nativeInit(
        modelPath: String,
        nCtx: Int,
        nThreads: Int,
        nGpuLayers: Int
    ): Long

    private external fun nativeGenerate(
        enginePtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        repeatPenalty: Float,
        callback: TokenCallback?
    ): String

    private external fun nativeRelease(enginePtr: Long)
    private external fun nativeGetModelInfo(enginePtr: Long): String
    private external fun nativeCountTokens(enginePtr: Long, text: String): Int

    // =====================================================================
    // Interfaz pública
    // =====================================================================

    /**
     * Cargar un modelo GGUF desde el sistema de archivos
     */
    suspend fun loadModel(
        modelPath: String,
        nCtx: Int = 4096,
        nThreads: Int = 4,
        nGpuLayers: Int = 0
    ): Result<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            // Liberar modelo anterior si hay uno cargado
            if (engineHandle != 0L) {
                Log.i(TAG, "Liberando modelo anterior...")
                nativeRelease(engineHandle)
                engineHandle = 0
            }

            Log.i(TAG, "Cargando modelo: $modelPath")
            engineHandle = nativeInit(modelPath, nCtx, nThreads, nGpuLayers)

            if (engineHandle == 0L) {
                return@withContext Result.failure(Exception("No se pudo cargar el modelo"))
            }

            currentModelPath = modelPath
            val infoJson = nativeGetModelInfo(engineHandle)
            val info = Gson().fromJson(infoJson, ModelInfo::class.java)
            Log.i(TAG, "Modelo cargado: $info")

            Result.success(info)
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar modelo: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Generar respuesta completa (sin streaming)
     */
    suspend fun generate(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        params: GenerationParams = GenerationParams()
    ): Result<String> = withContext(Dispatchers.IO) {
        if (engineHandle == 0L) {
            return@withContext Result.failure(Exception("Modelo no cargado"))
        }

        try {
            isGenerating = true
            val prompt = buildPrompt(userMessage, conversationHistory)
            val response = nativeGenerate(
                engineHandle, prompt,
                params.maxTokens, params.temperature,
                params.topP, params.repeatPenalty, null
            )
            isGenerating = false
            Result.success(response.trim())
        } catch (e: Exception) {
            isGenerating = false
            Log.e(TAG, "Error en generación: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Generar respuesta con streaming de tokens
     */
    fun generateStream(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList(),
        params: GenerationParams = GenerationParams()
    ): Flow<String> = flow {
        if (engineHandle == 0L) {
            emit("[ERROR] Modelo no cargado. Por favor instala un modelo primero.")
            return@flow
        }

        isGenerating = true
        val prompt = buildPrompt(userMessage, conversationHistory)
        val buffer = StringBuilder()

        try {
            val callback = object : TokenCallback {
                override fun onToken(token: String) {
                    // Los tokens se envían desde el hilo nativo
                }
                override fun onComplete() {
                    isGenerating = false
                }
            }

            // La generación real se hace aquí con streaming
            nativeGenerate(
                engineHandle, prompt,
                params.maxTokens, params.temperature,
                params.topP, params.repeatPenalty, callback
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error en streaming: ${e.message}")
            emit("\n[Error de generación: ${e.message}]")
        } finally {
            isGenerating = false
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Construir el prompt en formato de chat para los modelos
     */
    private fun buildPrompt(
        userMessage: String,
        history: List<ChatMessage>
    ): String {
        val systemPrompt = JarvisConfig.SYSTEM_PROMPT
        val sb = StringBuilder()

        // Formato compatible con Llama 3.2, Phi-3, Gemma 2, Qwen 2.5
        sb.append("<|system|>\n")
        sb.append(systemPrompt)
        sb.append("\n<|end|>\n")

        // Historial de conversación (últimos N mensajes para no exceder contexto)
        val recentHistory = history.takeLast(10)
        for (msg in recentHistory) {
            when (msg.role) {
                "user" -> {
                    sb.append("<|user|>\n")
                    sb.append(msg.content)
                    sb.append("\n<|end|>\n")
                }
                "assistant" -> {
                    sb.append("<|assistant|>\n")
                    sb.append(msg.content)
                    sb.append("\n<|end|>\n")
                }
            }
        }

        // Mensaje actual del usuario
        sb.append("<|user|>\n")
        sb.append(userMessage)
        sb.append("\n<|end|>\n")
        sb.append("<|assistant|>\n")

        return sb.toString()
    }

    fun countTokens(text: String): Int {
        return if (engineHandle != 0L) nativeCountTokens(engineHandle, text) else -1
    }

    fun isModelLoaded(): Boolean = engineHandle != 0L
    fun isCurrentlyGenerating(): Boolean = isGenerating
    fun getCurrentModelPath(): String = currentModelPath

    fun release() {
        if (engineHandle != 0L) {
            nativeRelease(engineHandle)
            engineHandle = 0
            currentModelPath = ""
        }
    }
}

// =====================================================================
// Data Classes
// =====================================================================

data class ModelInfo(
    val n_vocab: Int = 0,
    val n_ctx_train: Int = 0,
    val n_embd: Int = 0,
    val n_layer: Int = 0,
    val model_type: String = ""
)

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class GenerationParams(
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f
)

/**
 * Interfaz para recibir tokens en tiempo real
 */
interface TokenCallback {
    fun onToken(token: String)
    fun onComplete()
}
