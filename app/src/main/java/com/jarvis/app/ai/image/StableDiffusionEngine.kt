package com.jarvis.app.ai.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.jarvis.app.utils.JarvisConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * StableDiffusionEngine
 * Motor de generación de imágenes con Stable Diffusion local
 * Usa stable-diffusion.cpp via JNI
 *
 * Los modelos soportados son: SD Turbo, LCM DreamShaper (formato GGUF)
 */
class StableDiffusionEngine private constructor() {

    companion object {
        private const val TAG = "SDEngine"
        private var INSTANCE: StableDiffusionEngine? = null

        fun getInstance(): StableDiffusionEngine {
            return INSTANCE ?: synchronized(this) {
                StableDiffusionEngine().also { INSTANCE = it }
            }
        }

        init {
            try {
                System.loadLibrary("stable_diffusion")
                Log.i(TAG, "Librería SD cargada")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Librería SD no disponible: ${e.message}")
            }
        }
    }

    private var sdHandle: Long = 0

    // =====================================================================
    // JNI Declarations
    // =====================================================================

    private external fun nativeSDInit(
        modelPath: String,
        vaePath: String,
        nThreads: Int
    ): Long

    private external fun nativeSDGenerate(
        sdHandle: Long,
        prompt: String,
        negativePrompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        outputPath: String
    ): Boolean

    private external fun nativeSDRelease(sdHandle: Long)

    // =====================================================================
    // Interfaz pública
    // =====================================================================

    suspend fun loadModel(
        modelPath: String,
        vaePath: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (sdHandle != 0L) {
                nativeSDRelease(sdHandle)
                sdHandle = 0
            }

            sdHandle = nativeSDInit(modelPath, vaePath, 4)
            if (sdHandle == 0L) {
                Result.failure(Exception("No se pudo cargar el modelo SD"))
            } else {
                Log.i(TAG, "Modelo SD cargado correctamente")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar SD: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun generateImage(
        params: ImageGenerationParams
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (sdHandle == 0L) {
            return@withContext Result.failure(Exception("Modelo SD no cargado"))
        }

        try {
            // Crear directorio de salida
            val outputDir = File(JarvisConfig.GENERATED_IMAGES_DIR)
            outputDir.mkdirs()

            // Nombre de archivo único
            val timestamp = System.currentTimeMillis()
            val outputPath = "${JarvisConfig.GENERATED_IMAGES_DIR}/img_$timestamp.png"

            // Enriquecer el prompt
            val enrichedPrompt = JarvisConfig.buildImagePrompt(params.prompt)
            val negPrompt = params.negativePrompt.ifEmpty { JarvisConfig.SD_NEGATIVE_PROMPT }

            Log.i(TAG, "Generando imagen: $enrichedPrompt")

            val success = nativeSDGenerate(
                sdHandle,
                enrichedPrompt,
                negPrompt,
                params.width,
                params.height,
                params.steps,
                params.cfgScale,
                params.seed,
                outputPath
            )

            if (!success) {
                return@withContext Result.failure(Exception("Error al generar imagen"))
            }

            // Cargar bitmap generado
            val file = File(outputPath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Archivo de imagen no encontrado"))
            }

            val bitmap = BitmapFactory.decodeFile(outputPath)
                ?: return@withContext Result.failure(Exception("No se pudo decodificar la imagen"))

            Log.i(TAG, "Imagen generada: ${bitmap.width}x${bitmap.height}")
            Result.success(bitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Error en generación SD: ${e.message}")
            Result.failure(e)
        }
    }

    fun isLoaded(): Boolean = sdHandle != 0L

    fun release() {
        if (sdHandle != 0L) {
            nativeSDRelease(sdHandle)
            sdHandle = 0
        }
    }
}

data class ImageGenerationParams(
    val prompt: String,
    val negativePrompt: String = "",
    val width: Int = JarvisConfig.SD_DEFAULT_WIDTH,
    val height: Int = JarvisConfig.SD_DEFAULT_HEIGHT,
    val steps: Int = JarvisConfig.SD_DEFAULT_STEPS,
    val cfgScale: Float = JarvisConfig.SD_DEFAULT_CFG_SCALE,
    val seed: Long = -1L
)
