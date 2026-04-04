package com.jarvis.app.services

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.app.JarvisApplication
import com.jarvis.app.MainActivity
import com.jarvis.app.R
import com.jarvis.app.data.database.AppDatabase
import com.jarvis.app.utils.JarvisConfig
import kotlinx.coroutines.*
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * ModelDownloadService
 * Servicio en primer plano para descargar modelos de IA en background.
 * Muestra progreso en la barra de notificaciones y continúa
 * aunque el usuario salga de la app.
 */
class ModelDownloadService : Service() {

    companion object {
        const val TAG = "ModelDownloadService"
        const val NOTIF_ID = 1001
        const val ACTION_DOWNLOAD = "com.jarvis.app.ACTION_DOWNLOAD"
        const val ACTION_CANCEL = "com.jarvis.app.ACTION_CANCEL"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_DEST_PATH = "dest_path"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private val httpClient = OkHttpClient()

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val modelName = intent.getStringExtra(EXTRA_MODEL_NAME) ?: modelId
                val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: return START_NOT_STICKY
                val destPath = intent.getStringExtra(EXTRA_DEST_PATH) ?: return START_NOT_STICKY

                startForeground(NOTIF_ID, buildNotification(modelName, 0f))
                startDownload(modelId, modelName, downloadUrl, destPath)
            }
            ACTION_CANCEL -> {
                downloadJob?.cancel()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(
        modelId: String,
        modelName: String,
        url: String,
        destPath: String
    ) {
        downloadJob = serviceScope.launch {
            try {
                val destFile = File(destPath)
                destFile.parentFile?.mkdirs()

                Log.i(TAG, "Descargando modelo: $url → $destPath")

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "JarvisApp/1.0 Android")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

                    val body = response.body ?: throw IOException("Respuesta vacía")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L

                    FileOutputStream(destFile).use { fos ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            var lastNotifTime = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) {
                                    destFile.delete()
                                    return@launch
                                }

                                fos.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                // Actualizar notificación cada 500ms
                                val now = System.currentTimeMillis()
                                if (totalBytes > 0 && now - lastNotifTime > 500) {
                                    val progress = downloadedBytes.toFloat() / totalBytes
                                    val progressPct = (progress * 100).toInt()
                                    val downloadedMb = downloadedBytes / (1024 * 1024)
                                    val totalMb = totalBytes / (1024 * 1024)

                                    notificationManager.notify(
                                        NOTIF_ID,
                                        buildNotification(
                                            modelName, progress,
                                            "$downloadedMb MB / $totalMb MB"
                                        )
                                    )
                                    lastNotifTime = now
                                }
                            }
                        }
                    }

                    // Actualizar base de datos
                    val db = AppDatabase.getInstance(applicationContext)
                    db.aiModelDao().setDownloaded(modelId, true, destFile.absolutePath)

                    Log.i(TAG, "Descarga completada: $destPath (${destFile.length()} bytes)")

                    // Notificación de éxito
                    notificationManager.notify(NOTIF_ID, buildSuccessNotification(modelName))
                }

            } catch (e: CancellationException) {
                Log.i(TAG, "Descarga cancelada: $modelName")
                notificationManager.cancel(NOTIF_ID)
            } catch (e: Exception) {
                Log.e(TAG, "Error en descarga: ${e.message}")
                notificationManager.notify(NOTIF_ID, buildErrorNotification(modelName, e.message ?: "Error"))
            } finally {
                delay(3000) // Mantener notificación 3s
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // ─── Notificaciones ───

    private fun buildNotification(
        modelName: String,
        progress: Float,
        progressText: String = ""
    ): Notification {
        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, JarvisApplication.CHANNEL_DOWNLOAD_ID)
            .setContentTitle("Descargando: $modelName")
            .setContentText(if (progressText.isNotEmpty()) progressText else "Iniciando descarga…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_delete, "Cancelar", cancelPendingIntent)
            .build()
    }

    private fun buildSuccessNotification(modelName: String): Notification =
        NotificationCompat.Builder(this, JarvisApplication.CHANNEL_DOWNLOAD_ID)
            .setContentTitle("✅ Modelo listo")
            .setContentText("$modelName descargado correctamente")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()

    private fun buildErrorNotification(modelName: String, error: String): Notification =
        NotificationCompat.Builder(this, JarvisApplication.CHANNEL_DOWNLOAD_ID)
            .setContentTitle("❌ Error al descargar")
            .setContentText("$modelName: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        serviceScope.cancel()
    }
}
