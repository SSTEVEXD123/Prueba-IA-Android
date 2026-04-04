package com.jarvis.app.ui.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.app.databinding.ItemFileBinding
import com.jarvis.app.utils.FileInfo

class FileAdapter(
    private val onOpen: (FileInfo) -> Unit,
    private val onDelete: (FileInfo) -> Unit,
    private val onShare: (FileInfo) -> Unit
) : ListAdapter<FileInfo, FileAdapter.FileViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FileViewHolder(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class FileViewHolder(private val b: ItemFileBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(file: FileInfo) {
            b.textViewFileName.text = file.name
            b.textViewFileSize.text = file.formattedSize()
            b.textViewFileDate.text = file.lastModifiedStr
            b.textViewFileType.text = when (file.extension.lowercase()) {
                "txt" -> "📝"
                "md" -> "📋"
                "json" -> "{ }"
                "csv" -> "📊"
                "pdf" -> "📄"
                else -> "📎"
            }
            b.root.setOnClickListener { onOpen(file) }
            b.buttonDeleteFile.setOnClickListener { onDelete(file) }
            b.buttonShareFile.setOnClickListener { onShare(file) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FileInfo>() {
        override fun areItemsTheSame(a: FileInfo, b: FileInfo) = a.path == b.path
        override fun areContentsTheSame(a: FileInfo, b: FileInfo) = a == b
    }
}
