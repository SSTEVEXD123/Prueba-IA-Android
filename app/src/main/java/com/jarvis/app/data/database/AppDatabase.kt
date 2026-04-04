package com.jarvis.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AiModelEntity::class,
        GeneratedImageEntity::class,
        GeneratedPdfEntity::class,
        ManagedFileEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun generatedImageDao(): GeneratedImageDao
    abstract fun generatedPdfDao(): GeneratedPdfDao
    abstract fun managedFileDao(): ManagedFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jarvis_database.db"
                )
                    // FIXED: pasamos scope al callback para evitar race condition con INSTANCE
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Callback para poblar la base de datos con modelos predefinidos.
     * FIXED: recibe la corrutina scope y accede a INSTANCE que ya está
     * garantizado no-null porque Room llama onCreate antes de devolver build().
     */
    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch {
                // INSTANCE está asignado porque Room invoca onCreate
                // dentro del mismo bloque synchronized de getInstance()
                INSTANCE?.aiModelDao()?.let { populateInitialModels(it) }
            }
        }

        private suspend fun populateInitialModels(dao: AiModelDao) {
            // Modelos LLM disponibles para descarga
            val llmModels = listOf(
                AiModelEntity(
                    modelId = "phi3_mini_q4",
                    displayName = "Phi-3 Mini (3.8B Q4)",
                    description = "Modelo ultra-ligero de Microsoft. Ideal para dispositivos con 4GB RAM. Respuestas rápidas y precisas.",
                    type = "llm",
                    filePath = "",
                    fileSize = 2_400_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
                    quantization = "Q4_K_M",
                    contextLength = 4096,
                    parameters = "{\"n_threads\":4,\"n_ctx\":4096}"
                ),
                AiModelEntity(
                    modelId = "gemma2_2b_q5",
                    displayName = "Gemma 2 (2B Q5)",
                    description = "Modelo compacto de Google. Excelente relación calidad-velocidad en dispositivos Android.",
                    type = "llm",
                    filePath = "",
                    fileSize = 1_900_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q5_K_M.gguf",
                    quantization = "Q5_K_M",
                    contextLength = 8192,
                    parameters = "{\"n_threads\":4,\"n_ctx\":8192}"
                ),
                AiModelEntity(
                    modelId = "llama3_2_3b_q4",
                    displayName = "Llama 3.2 (3B Q4)",
                    description = "Meta Llama 3.2 ultra-compacto. Muy eficiente para conversaciones largas y tareas complejas.",
                    type = "llm",
                    filePath = "",
                    fileSize = 2_100_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                    quantization = "Q4_K_M",
                    contextLength = 8192,
                    parameters = "{\"n_threads\":4,\"n_ctx\":8192}"
                ),
                AiModelEntity(
                    modelId = "qwen2_5_1b_q6",
                    displayName = "Qwen 2.5 (1.5B Q6)",
                    description = "Modelo de Alibaba. Excelente para razonamiento y código. Muy ligero.",
                    type = "llm",
                    filePath = "",
                    fileSize = 1_100_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q6_k.gguf",
                    quantization = "Q6_K",
                    contextLength = 32768,
                    parameters = "{\"n_threads\":4,\"n_ctx\":8192}"
                ),
                AiModelEntity(
                    modelId = "smollm2_360m_q8",
                    displayName = "SmolLM2 (360M Q8)",
                    description = "El modelo más ligero. Para dispositivos con recursos muy limitados. Rápido y eficiente.",
                    type = "llm",
                    filePath = "",
                    fileSize = 400_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
                    quantization = "Q8_0",
                    contextLength = 8192,
                    parameters = "{\"n_threads\":2,\"n_ctx\":4096}"
                )
            )

            // Modelos de imagen (Stable Diffusion)
            val imageModels = listOf(
                AiModelEntity(
                    modelId = "sd_turbo_q8",
                    displayName = "SD Turbo (Q8)",
                    description = "Stable Diffusion Turbo optimizado. Generación rápida de imágenes 512x512 en Android.",
                    type = "image",
                    filePath = "",
                    fileSize = 1_600_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/rupeshs/sd-turbo-ncnn/resolve/main/sd-turbo-q8_0.gguf",
                    quantization = "Q8_0",
                    contextLength = 0,
                    parameters = "{\"steps\":4,\"width\":512,\"height\":512}"
                ),
                AiModelEntity(
                    modelId = "lcm_dreamshaper_q4",
                    displayName = "LCM DreamShaper (Q4)",
                    description = "Modelo artístico con LCM. Genera imágenes creativas en pocos pasos.",
                    type = "image",
                    filePath = "",
                    fileSize = 2_000_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/rupeshs/LCM-dreamshaper-v7-ncnn/resolve/main/lcm-dreamshaper-v7-q4_0.gguf",
                    quantization = "Q4_0",
                    contextLength = 0,
                    parameters = "{\"steps\":8,\"width\":512,\"height\":512}"
                )
            )

            // Modelos de visión
            val visionModels = listOf(
                AiModelEntity(
                    modelId = "moondream2_q4",
                    displayName = "Moondream 2 (Q4)",
                    description = "Modelo de visión compacto. Análisis de imágenes, descripción y respuesta a preguntas visuales.",
                    type = "vision",
                    filePath = "",
                    fileSize = 1_800_000_000L,
                    isDownloaded = false,
                    downloadUrl = "https://huggingface.co/vikhyatk/moondream2/resolve/main/moondream2-q4_0.gguf",
                    quantization = "Q4_0",
                    contextLength = 2048,
                    parameters = "{\"n_threads\":4}"
                )
            )

            (llmModels + imageModels + visionModels).forEach { dao.insertOrUpdate(it) }
        }
    }
}
