package com.jarvis.app.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * FileManager
 * Gestión completa de archivos del usuario dentro de JarvisApp/files/
 * CRUD: crear, leer, actualizar, eliminar
 * Soporte para txt, json, md, csv
 */
object FileManager {

    private const val TAG = "FileManager"

    // =====================================================================
    // CREAR
    // =====================================================================

    suspend fun createTextFile(
        name: String,
        content: String,
        subfolder: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            JarvisConfig.createDirectories()
            val dir = if (subfolder.isNotEmpty()) {
                File("${JarvisConfig.USER_FILES_DIR}/$subfolder").also { it.mkdirs() }
            } else {
                File(JarvisConfig.USER_FILES_DIR)
            }

            val safeName = sanitizeFileName(name)
            val file = File(dir, safeName)
            file.writeText(content, Charsets.UTF_8)
            Log.i(TAG, "Archivo creado: ${file.absolutePath}")
            Result.success(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear archivo: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun createJsonFile(
        name: String,
        jsonContent: String
    ): Result<File> = createTextFile(
        name = if (name.endsWith(".json")) name else "$name.json",
        content = jsonContent
    )

    // =====================================================================
    // LEER
    // =====================================================================

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Archivo no encontrado: $path"))
            }
            Result.success(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFileBytes(path: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Archivo no encontrado"))
            }
            Result.success(file.readBytes())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(
        directory: String = JarvisConfig.USER_FILES_DIR,
        extension: String? = null
    ): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        try {
            val dir = File(directory)
            if (!dir.exists()) {
                dir.mkdirs()
                return@withContext Result.success(emptyList())
            }

            val files = dir.listFiles()?.filter { file ->
                file.isFile && (extension == null || file.extension == extension)
            }?.map { it.toFileInfo() } ?: emptyList()

            Result.success(files.sortedByDescending { it.lastModified })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listAllJarvisFiles(): Result<Map<String, List<FileInfo>>> = withContext(Dispatchers.IO) {
        try {
            val result = mutableMapOf<String, List<FileInfo>>()

            mapOf(
                "Documentos" to JarvisConfig.USER_FILES_DIR,
                "Imágenes generadas" to JarvisConfig.GENERATED_IMAGES_DIR,
                "PDFs" to JarvisConfig.GENERATED_PDFS_DIR,
                "Modelos LLM" to JarvisConfig.LLM_MODELS_DIR,
                "Modelos Imagen" to JarvisConfig.IMAGE_MODELS_DIR
            ).forEach { (label, path) ->
                val files = File(path).listFiles()
                    ?.filter { it.isFile }
                    ?.map { it.toFileInfo() }
                    ?: emptyList()
                result[label] = files
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================================
    // ACTUALIZAR
    // =====================================================================

    suspend fun updateFile(
        path: String,
        newContent: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Archivo no encontrado: $path"))
            }

            // Backup antes de modificar
            val backupPath = "$path.bak"
            file.copyTo(File(backupPath), overwrite = true)

            file.writeText(newContent, Charsets.UTF_8)
            File(backupPath).delete()

            Log.i(TAG, "Archivo actualizado: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun appendToFile(
        path: String,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) file.createNewFile()
            file.appendText(content, Charsets.UTF_8)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFile(
        path: String,
        newName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Archivo no encontrado"))
            }

            val newFile = File(file.parent, sanitizeFileName(newName))
            if (file.renameTo(newFile)) {
                Result.success(newFile.absolutePath)
            } else {
                Result.failure(Exception("No se pudo renombrar el archivo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================================
    // ELIMINAR
    // =====================================================================

    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Archivo no encontrado: $path"))
            }
            if (file.delete()) {
                Log.i(TAG, "Archivo eliminado: $path")
                Result.success(Unit)
            } else {
                Result.failure(Exception("No se pudo eliminar el archivo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMultiple(paths: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        paths.associate { path ->
            path to (deleteFile(path).isSuccess)
        }
    }

    // =====================================================================
    // COPIAR Y MOVER
    // =====================================================================

    suspend fun copyFile(
        sourcePath: String,
        destinationPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val dest = File(destinationPath)
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================================
    // UTILIDADES
    // =====================================================================

    fun getFileSize(path: String): Long = File(path).length()

    fun fileExists(path: String): Boolean = File(path).exists()

    fun getExtension(path: String): String = File(path).extension

    fun getTotalJarvisSize(): Long {
        return File(JarvisConfig.ROOT_DIR).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun sanitizeFileName(name: String): String {
        var clean = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        if (!clean.contains('.')) clean += ".txt"
        return clean
    }

    private fun File.toFileInfo() = FileInfo(
        name = name,
        path = absolutePath,
        size = length(),
        extension = extension,
        lastModified = lastModified(),
        lastModifiedStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es")).format(Date(lastModified()))
    )
}

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val extension: String,
    val lastModified: Long,
    val lastModifiedStr: String
) {
    fun formattedSize(): String = when {
        size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
        size >= 1024 -> String.format("%.1f KB", size / 1024.0)
        else -> "$size B"
    }
}
