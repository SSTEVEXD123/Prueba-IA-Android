package com.jarvis.app.ui.dashboard

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.ai.image.StableDiffusionEngine
import com.jarvis.app.ai.llm.LlamaEngine
import com.jarvis.app.ai.vision.MoondreamEngine
import com.jarvis.app.data.database.AppDatabase
import com.jarvis.app.databinding.FragmentDashboardBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.RandomAccessFile
import java.text.DecimalFormat

/**
 * DashboardFragment
 * Panel de control con:
 * - CPU, RAM, Almacenamiento en tiempo real
 * - Estado de modelos cargados
 * - Estadísticas de uso
 * - Acciones rápidas
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private var statsJob: Job? = null
    private val df = DecimalFormat("#.##")

    companion object {
        fun newInstance() = DashboardFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDeviceInfo()
        setupModelStatus()
        loadStats()
        startRealTimeMonitoring()
    }

    // =====================================================================
    // Información del dispositivo
    // =====================================================================

    private fun setupDeviceInfo() {
        binding.apply {
            textViewDeviceModel.text = "${Build.MANUFACTURER} ${Build.MODEL}"
            textViewAndroidVersion.text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            textViewCpuArch.text = Build.SUPPORTED_ABIS.firstOrNull() ?: "Desconocido"
            textViewJarvisVersion.text = "Jarvis App v1.0.0"
        }
    }

    // =====================================================================
    // Estado de modelos IA
    // =====================================================================

    private fun setupModelStatus() {
        val llamaLoaded = LlamaEngine.getInstance().isModelLoaded()
        val sdLoaded = StableDiffusionEngine.getInstance().isLoaded()
        val moonLoaded = MoondreamEngine.getInstance().isLoaded()

        binding.apply {
            chipLlmStatus.apply {
                text = if (llamaLoaded) "LLM ✓ Activo" else "LLM ✗ Inactivo"
                isChecked = llamaLoaded
            }
            chipSdStatus.apply {
                text = if (sdLoaded) "SD ✓ Activo" else "SD ✗ Inactivo"
                isChecked = sdLoaded
            }
            chipMoondreamStatus.apply {
                text = if (moonLoaded) "Moondream ✓" else "Moondream ✗"
                isChecked = moonLoaded
            }
        }

        // Estadísticas de conversaciones
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val convCount = db.conversationDao().getCount()
            val imgCount = db.generatedImageDao().getCount()
            val pdfCount = 0 // Se puede añadir

            binding.textViewConvCount.text = convCount.toString()
            binding.textViewImgCount.text = imgCount.toString()
        }
    }

    // =====================================================================
    // Estadísticas en tiempo real
    // =====================================================================

    private fun startRealTimeMonitoring() {
        statsJob = lifecycleScope.launch {
            while (isActive) {
                updateStats()
                delay(2000) // Actualizar cada 2 segundos
            }
        }
    }

    private fun updateStats() {
        val context = requireContext()

        // RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamGB = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val usedRamGB = (memInfo.totalMem - memInfo.availMem).toDouble() / (1024 * 1024 * 1024)
        val ramPercent = ((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem * 100).toInt()

        // Almacenamiento interno
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val internalTotal = internalStat.blockCountLong * internalStat.blockSizeLong
        val internalFree = internalStat.availableBlocksLong * internalStat.blockSizeLong
        val internalUsed = internalTotal - internalFree
        val storagePercent = (internalUsed.toDouble() / internalTotal * 100).toInt()

        // Almacenamiento de Jarvis
        val jarvisDir = File(com.jarvis.app.utils.JarvisConfig.ROOT_DIR)
        val jarvisSize = getFolderSize(jarvisDir)

        // CPU (aproximado con /proc/stat)
        val cpuPercent = getCpuUsage()

        // Actualizar UI
        binding.apply {
            // RAM
            textViewRamUsed.text = "${df.format(usedRamGB)} GB / ${df.format(totalRamGB)} GB"
            progressBarRam.progress = ramPercent
            textViewRamPercent.text = "$ramPercent%"

            // Almacenamiento
            textViewStorageUsed.text = "${formatBytes(internalUsed)} / ${formatBytes(internalTotal)}"
            progressBarStorage.progress = storagePercent
            textViewStoragePercent.text = "$storagePercent%"

            // Jarvis folder size
            textViewJarvisSize.text = "JarvisApp: ${formatBytes(jarvisSize)}"

            // CPU
            textViewCpuPercent.text = "$cpuPercent%"
            progressBarCpu.progress = cpuPercent

            // Memoria de Jarvis
            val rt = Runtime.getRuntime()
            val appUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val appMaxMb = rt.maxMemory() / (1024 * 1024)
            textViewAppMemory.text = "App: ${appUsedMb}MB / ${appMaxMb}MB"
        }
    }

    private fun loadStats() {
        updateStats()
    }

    private fun getCpuUsage(): Int {
        return try {
            val reader1 = RandomAccessFile("/proc/stat", "r")
            val load1 = reader1.readLine()
            reader1.close()

            Thread.sleep(200)

            val reader2 = RandomAccessFile("/proc/stat", "r")
            val load2 = reader2.readLine()
            reader2.close()

            val fields1 = load1.replace("cpu  ", "").trim().split(" ")
            val fields2 = load2.replace("cpu  ", "").trim().split(" ")

            val idle1 = fields1[3].toLong()
            val idle2 = fields2[3].toLong()
            val total1 = fields1.sumOf { it.toLong() }
            val total2 = fields2.sumOf { it.toLong() }

            val totalDiff = total2 - total1
            val idleDiff = idle2 - idle1

            if (totalDiff == 0L) 0
            else ((totalDiff - idleDiff) * 100 / totalDiff).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            0
        }
    }

    private fun getFolderSize(folder: File): Long {
        if (!folder.exists()) return 0
        return folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
            bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
            bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
            else -> "$bytes B"
        }
    }

    override fun onPause() {
        super.onPause()
        statsJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        if (statsJob?.isActive != true) {
            startRealTimeMonitoring()
        }
        setupModelStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        statsJob?.cancel()
        _binding = null
    }
}
