package com.jarvis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.jarvis.app.utils.JarvisConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * JarvisApplication
 * Clase Application personalizada.
 * Inicializa: carpetas, canales de notificación, base de datos.
 */
class JarvisApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_DOWNLOAD_ID = "jarvis_download"
        const val CHANNEL_GENERATION_ID = "jarvis_generation"
        private const val TAG = "JarvisApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Iniciando Jarvis Application...")

        createNotificationChannels()

        applicationScope.launch {
            try {
                JarvisConfig.createDirectories()
                Log.i(TAG, "Directorios de Jarvis inicializados: ${JarvisConfig.ROOT_DIR}")
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar directorios: ${e.message}")
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Canal para descarga de modelos
            NotificationChannel(
                CHANNEL_DOWNLOAD_ID,
                "Descarga de Modelos",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de progreso al descargar modelos de IA"
                notificationManager.createNotificationChannel(this)
            }

            // Canal para notificaciones de generación
            NotificationChannel(
                CHANNEL_GENERATION_ID,
                "Generación de IA",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Notificaciones de la generación en curso"
                notificationManager.createNotificationChannel(this)
            }
        }
    }
}
