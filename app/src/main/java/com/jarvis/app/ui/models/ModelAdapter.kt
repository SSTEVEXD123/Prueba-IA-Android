package com.jarvis.app.ui.models

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.app.data.database.AiModelEntity
import com.jarvis.app.databinding.ItemModelBinding

/**
 * ModelAdapter — RecyclerView para listar modelos de IA
 * Muestra: nombre, descripción, tamaño, quantización, estado de descarga
 */
class ModelAdapter(
    private val onDownload: (AiModelEntity) -> Unit,
    private val onActivate: (AiModelEntity) -> Unit,
    private val onDelete: (AiModelEntity) -> Unit,
    private val getDownloadState: (String) -> DownloadState?
) : ListAdapter<AiModelEntity, ModelAdapter.ModelViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val binding = ItemModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ModelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        holder.bind(getItem(position), getDownloadState(getItem(position).modelId))
    }

    inner class ModelViewHolder(private val b: ItemModelBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(model: AiModelEntity, downloadState: DownloadState?) {
            b.textViewModelName.text = model.displayName
            b.textViewModelDesc.text = model.description
            b.textViewQuantization.text = model.quantization.ifEmpty { "?" }
            b.textViewContextLen.text = if (model.contextLength > 0)
                "${model.contextLength / 1000}K ctx" else ""

            val sizeMb = model.fileSize / (1024 * 1024)
            b.textViewSize.text = "${sizeMb} MB"

            // Tipo de modelo → chip de color
            b.chipModelType.text = when (model.type) {
                "llm" -> "🧠 LLM"
                "image" -> "🎨 Imagen"
                "vision" -> "👁️ Visión"
                else -> model.type
            }

            // ─── Estado según descarga ───
            when {
                downloadState is DownloadState.Downloading -> {
                    b.progressDownload.visibility = View.VISIBLE
                    b.progressDownload.progress = (downloadState.progress * 100).toInt()
                    b.textViewProgress.visibility = View.VISIBLE
                    b.textViewProgress.text = "${(downloadState.progress * 100).toInt()}%"
                    if (downloadState.totalBytes > 0) {
                        val dlMb = downloadState.downloadedBytes / (1024 * 1024)
                        val totMb = downloadState.totalBytes / (1024 * 1024)
                        b.textViewProgress.text = "$dlMb / $totMb MB"
                    }
                    b.buttonDownload.visibility = View.GONE
                    b.buttonActivate.visibility = View.GONE
                    b.buttonDelete.visibility = View.GONE
                    b.chipStatus.text = "Descargando…"
                    b.chipStatus.visibility = View.VISIBLE
                }

                downloadState is DownloadState.Error -> {
                    b.progressDownload.visibility = View.GONE
                    b.textViewProgress.visibility = View.GONE
                    b.buttonDownload.visibility = View.VISIBLE
                    b.buttonDownload.text = "Reintentar"
                    b.buttonActivate.visibility = View.GONE
                    b.buttonDelete.visibility = View.GONE
                    b.chipStatus.text = "❌ Error"
                    b.chipStatus.visibility = View.VISIBLE
                }

                model.isDownloaded -> {
                    b.progressDownload.visibility = View.GONE
                    b.textViewProgress.visibility = View.GONE
                    b.buttonDownload.visibility = View.GONE
                    b.buttonActivate.visibility = View.VISIBLE
                    b.buttonDelete.visibility = View.VISIBLE

                    if (model.isActive) {
                        b.buttonActivate.text = "✓ Activo"
                        b.buttonActivate.isEnabled = false
                        b.chipStatus.text = "Activo"
                        b.chipStatus.visibility = View.VISIBLE
                        b.root.strokeWidth = 2
                    } else {
                        b.buttonActivate.text = "Activar"
                        b.buttonActivate.isEnabled = true
                        b.chipStatus.visibility = View.GONE
                        b.root.strokeWidth = 0
                    }
                }

                else -> {
                    // No descargado
                    b.progressDownload.visibility = View.GONE
                    b.textViewProgress.visibility = View.GONE
                    b.buttonDownload.visibility = View.VISIBLE
                    b.buttonDownload.text = "Descargar"
                    b.buttonActivate.visibility = View.GONE
                    b.buttonDelete.visibility = View.GONE
                    b.chipStatus.visibility = View.GONE
                    b.root.strokeWidth = 0
                }
            }

            b.buttonDownload.setOnClickListener { onDownload(model) }
            b.buttonActivate.setOnClickListener { onActivate(model) }
            b.buttonDelete.setOnClickListener { onDelete(model) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AiModelEntity>() {
        override fun areItemsTheSame(a: AiModelEntity, b: AiModelEntity) = a.modelId == b.modelId
        override fun areContentsTheSame(a: AiModelEntity, b: AiModelEntity) = a == b
    }
}
