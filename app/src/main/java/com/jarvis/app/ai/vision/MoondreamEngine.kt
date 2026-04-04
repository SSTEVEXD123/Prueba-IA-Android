package com.jarvis.app.ai.vision

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * MoondreamEngine
 * Motor de visión con Moondream 2
 * Capacidades: descripción de imágenes, VQA (Visual Question Answering),
 * detección de objetos, lectura de texto en imágenes (OCR básico)
 */
class MoondreamEngine private constructor() {

    companion object {
        private const val TAG = "MoondreamEngine"
        private var INSTANCE: MoondreamEngine? = null

        fun getInstance(): MoondreamEngine {
            return INSTANCE ?: synchronized(this) {
                MoondreamEngine().also { INSTANCE = it }
            }
        }

        init {
            try {
                System.loadLibrary("jarvis_jni") // Compartida con llama
                Log.i(TAG, "Motor de visión inicializado")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Error al cargar librería de visión: ${e.message}")
            }
        }
    }

    private var modelHandle: Long = 0

    // =====================================================================
    // JNI Declarations
    // =====================================================================

    private external fun nativeVisionInit(modelPath: String, nThreads: Int): Long
    private external fun nativeVisionQuery(
        handle: Long,
        imageBase64: String,
        query: String,
        maxTokens: Int
    ): String
    private external fun nativeVisionRelease(handle: Long)

    // =====================================================================
    // Interfaz pública
    // =====================================================================

    suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (modelHandle != 0L) {
                nativeVisionRelease(modelHandle)
                modelHandle = 0
            }

            modelHandle = nativeVisionInit(modelPath, 4)
            if (modelHandle == 0L) {
                Result.failure(Exception("No se pudo cargar Moondream 2"))
            } else {
                Log.i(TAG, "Moondream 2 cargado correctamente")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Describir una imagen en español
     */
    suspend fun describeImage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        query(bitmap, "Describe detalladamente todo lo que ves en esta imagen en español. " +
                "Incluye objetos, personas, colores, contexto y cualquier texto visible.")
    }

    /**
     * Responder una pregunta sobre una imagen (VQA)
     */
    suspend fun answerQuestion(bitmap: Bitmap, question: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = "Responde en español: $question"
        query(bitmap, prompt)
    }

    /**
     * Extraer texto visible en la imagen (OCR básico)
     */
    suspend fun extractText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        query(bitmap, "Lee y transcribe todo el texto visible en esta imagen. " +
                "Si no hay texto, indica que no se encontró texto.")
    }

    /**
     * Analizar imagen para uso en el chat de Jarvis
     */
    suspend fun analyzeForChat(bitmap: Bitmap, userQuestion: String): Result<String> {
        return if (userQuestion.isBlank()) {
            describeImage(bitmap)
        } else {
            answerQuestion(bitmap, userQuestion)
        }
    }

    private suspend fun query(bitmap: Bitmap, prompt: String): Result<String> {
        if (modelHandle == 0L) {
            return Result.failure(Exception("Moondream 2 no está cargado"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val imageBase64 = bitmapToBase64(bitmap)
                val response = nativeVisionQuery(modelHandle, imageBase64, prompt, 512)
                if (response.startsWith("[ERROR]")) {
                    Result.failure(Exception(response))
                } else {
                    Result.success(response.trim())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en visión: ${e.message}")
                Result.failure(e)
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Redimensionar si es muy grande
        val maxSize = 512
        val resized = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newW = (bitmap.width * scale).toInt()
            val newH = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else bitmap

        resized.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    fun isLoaded(): Boolean = modelHandle != 0L

    fun release() {
        if (modelHandle != 0L) {
            nativeVisionRelease(modelHandle)
            modelHandle = 0
        }
    }
}
