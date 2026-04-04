package com.jarvis.app.ui.models

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.app.data.database.AiModelEntity
import com.jarvis.app.data.database.AppDatabase
import com.jarvis.app.utils.JarvisConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * ModelManagerViewModel
 * Gestiona descarga, instalación, activación y eliminación de modelos de IA
 */
class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ModelManager"
    }

    private val db = AppDatabase.getInstance(application)
    private val modelDao = db.aiModelDao()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    val allModels: Flow<List<AiModelEntity>> = modelDao.getAllModels()

    private val activeDownloads = mutableMapOf<String, Job>()

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // =====================================================================
    // Descarga de modelos
    // =====================================================================

    fun downloadModel(model: AiModelEntity) {
        if (activeDownloads.containsKey(model.modelId)) {
            Log.w(TAG, "Ya existe una descarga en curso para ${model.modelId}")
            return
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Determinar directorio de destino por tipo
                val destDir = when (model.type) {
                    "llm" -> JarvisConfig.LLM_MODELS_DIR
                    "image" -> JarvisConfig.IMAGE_MODELS_DIR
                    "vision" -> JarvisConfig.VISION_MODELS_DIR
                    else -> JarvisConfig.MODELS_DIR
                }

                File(destDir).mkdirs()
                val fileName = model.downloadUrl.substringAfterLast("/")
                val destFile = File(destDir, fileName)

                Log.i(TAG, "Iniciando descarga: ${model.downloadUrl} → $destFile")

                updateDownloadState(model.modelId, DownloadState.Downloading(0f))

                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .addHeader("User-Agent", "JarvisApp/1.0 Android")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: throw IOException("Respuesta vacía")
                    val contentLength = body.contentLength()
                    var downloadedBytes = 0L

                    FileOutputStream(destFile).use { fos ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) {
                                    // Descarga cancelada
                                    destFile.delete()
                                    updateDownloadState(model.modelId, DownloadState.Cancelled)
                                    return@launch
                                }

                                fos.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                if (contentLength > 0) {
                                    val progress = downloadedBytes.toFloat() / contentLength
                                    updateDownloadState(
                                        model.modelId,
                                        DownloadState.Downloading(progress, downloadedBytes, contentLength)
                                    )
                                }
                            }
                        }
                    }

                    // Verificar tamaño del archivo
                    if (destFile.length() == 0L) {
                        destFile.delete()
                        throw IOException("Archivo descargado vacío")
                    }

                    // Actualizar base de datos
                    modelDao.setDownloaded(model.modelId, true, destFile.absolutePath)
                    updateDownloadState(model.modelId, DownloadState.Completed(destFile.absolutePath))

                    Log.i(TAG, "Descarga completada: ${destFile.absolutePath} (${destFile.length()} bytes)")
                }

            } catch (e: CancellationException) {
                Log.i(TAG, "Descarga cancelada: ${model.modelId}")
                updateDownloadState(model.modelId, DownloadState.Cancelled)
            } catch (e: Exception) {
                Log.e(TAG, "Error en descarga ${model.modelId}: ${e.message}")
                updateDownloadState(model.modelId, DownloadState.Error(e.message ?: "Error desconocido"))
            } finally {
                activeDownloads.remove(model.modelId)
            }
        }

        activeDownloads[model.modelId] = job
    }

    fun cancelDownload(modelId: String) {
        activeDownloads[modelId]?.cancel()
        activeDownloads.remove(modelId)
        updateDownloadState(modelId, DownloadState.Cancelled)
    }

    // =====================================================================
    // Activación de modelos
    // =====================================================================

    fun setActiveModel(modelId: String, type: String) {
        viewModelScope.launch {
            modelDao.deactivateAll(type)
            modelDao.setActive(modelId)
            Log.i(TAG, "Modelo activo: $modelId (tipo: $type)")
        }
    }

    // =====================================================================
    // Eliminación de modelos
    // =====================================================================

    fun deleteModel(model: AiModelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Eliminar archivo físico
                val file = File(model.filePath)
                if (file.exists()) {
                    file.delete()
                    Log.i(TAG, "Archivo eliminado: ${model.filePath}")
                }

                // Actualizar base de datos
                modelDao.setDownloaded(model.modelId, false, "")

                // Si era el modelo activo, desactivar
                if (model.isActive) {
                    modelDao.deactivateAll(model.type)
                }

                updateDownloadState(model.modelId, DownloadState.NotDownloaded)
            } catch (e: Exception) {
                Log.e(TAG, "Error al eliminar modelo: ${e.message}")
            }
        }
    }

    // =====================================================================
    // Importar modelo personalizado
    // =====================================================================

    fun importCustomModel(
        filePath: String,
        displayName: String,
        type: String = "llm"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = File(filePath)
                if (!sourceFile.exists()) {
                    Log.e(TAG, "Archivo no encontrado: $filePath")
                    return@launch
                }

                // Copiar al directorio de modelos de Jarvis
                val destDir = when (type) {
                    "llm" -> JarvisConfig.LLM_MODELS_DIR
                    "image" -> JarvisConfig.IMAGE_MODELS_DIR
                    "vision" -> JarvisConfig.VISION_MODELS_DIR
                    else -> JarvisConfig.MODELS_DIR
                }

                File(destDir).mkdirs()
                val destFile = File(destDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)

                // Registrar en la base de datos
                val modelId = "custom_${System.currentTimeMillis()}"
                modelDao.insertOrUpdate(
                    AiModelEntity(
                        modelId = modelId,
                        displayName = displayName,
                        description = "Modelo importado manualmente",
                        type = type,
                        filePath = destFile.absolutePath,
                        fileSize = destFile.length(),
                        isDownloaded = true,
                        quantization = detectQuantization(sourceFile.name),
                        installedAt = System.currentTimeMillis()
                    )
                )

                Log.i(TAG, "Modelo importado: $displayName → ${destFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error al importar modelo: ${e.message}")
            }
        }
    }

    private fun detectQuantization(fileName: String): String {
        val patterns = listOf("Q2_K", "Q3_K", "Q4_0", "Q4_K_M", "Q4_K_S",
            "Q5_0", "Q5_K_M", "Q6_K", "Q8_0", "F16", "F32")
        return patterns.firstOrNull { fileName.uppercase().contains(it) } ?: "UNKNOWN"
    }

    private fun updateDownloadState(modelId: String, state: DownloadState) {
        _downloadStates.update { current ->
            current.toMutableMap().apply { put(modelId, state) }
        }
    }
}

// =====================================================================
// Estados de descarga
// =====================================================================

sealed class DownloadState {
    object NotDownloaded : DownloadState()
    object Cancelled : DownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0
    ) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
