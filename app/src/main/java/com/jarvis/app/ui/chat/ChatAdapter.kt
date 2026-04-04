package com.jarvis.app.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.app.databinding.ItemMessageAssistantBinding
import com.jarvis.app.databinding.ItemMessageUserBinding
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChatAdapter
 * Adaptador para la lista de mensajes en el chat
 * Soporta mensajes del usuario, asistente y errores
 * Renderiza Markdown en mensajes del asistente
 */
class ChatAdapter(
    private val onCopyClick: (ChatMessageUi) -> Unit,
    private val onDeleteClick: (ChatMessageUi) -> Unit,
    private val onRegenerateClick: (ChatMessageUi) -> Unit
) : ListAdapter<ChatMessageUi, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_ASSISTANT = 1
        private const val VIEW_TYPE_ERROR = 2
    }

    private lateinit var markwon: Markwon
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("es"))

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).role) {
            "user" -> VIEW_TYPE_USER
            "assistant" -> VIEW_TYPE_ASSISTANT
            else -> VIEW_TYPE_ERROR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        // Inicializar Markwon con soporte HTML
        if (!::markwon.isInitialized) {
            markwon = Markwon.builder(parent.context)
                .usePlugin(HtmlPlugin.create())
                .build()
        }

        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemMessageUserBinding.inflate(inflater, parent, false)
                UserMessageViewHolder(binding)
            }
            VIEW_TYPE_ASSISTANT -> {
                val binding = ItemMessageAssistantBinding.inflate(inflater, parent, false)
                AssistantMessageViewHolder(binding)
            }
            else -> {
                val binding = ItemMessageAssistantBinding.inflate(inflater, parent, false)
                ErrorMessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message)
            is AssistantMessageViewHolder -> holder.bind(message)
            is ErrorMessageViewHolder -> holder.bind(message)
        }
    }

    // =====================================================================
    // ViewHolders
    // =====================================================================

    inner class UserMessageViewHolder(
        private val binding: ItemMessageUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessageUi) {
            binding.textViewMessage.text = message.content
            binding.textViewTime.text = timeFormat.format(Date(message.timestamp))

            binding.buttonCopy.setOnClickListener { onCopyClick(message) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(message) }
        }
    }

    inner class AssistantMessageViewHolder(
        private val binding: ItemMessageAssistantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessageUi) {
            if (message.isStreaming) {
                // Mostrar indicador de carga mientras genera
                binding.textViewMessage.text = ""
                binding.layoutStreaming.visibility = View.VISIBLE
                binding.layoutActions.visibility = View.GONE
            } else {
                binding.layoutStreaming.visibility = View.GONE
                binding.layoutActions.visibility = View.VISIBLE

                // Renderizar Markdown
                if (message.content.isNotBlank()) {
                    markwon.setMarkdown(binding.textViewMessage, message.content)
                } else {
                    binding.textViewMessage.text = ""
                }

                // Tiempo de generación
                if (message.generationTimeMs > 0) {
                    val seconds = message.generationTimeMs / 1000.0
                    binding.textViewGenTime.text = String.format("%.1fs", seconds)
                    binding.textViewGenTime.visibility = View.VISIBLE
                }

                binding.textViewTime.text = timeFormat.format(Date(message.timestamp))
            }

            binding.buttonCopy.setOnClickListener { onCopyClick(message) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(message) }
            binding.buttonRegenerate.setOnClickListener { onRegenerateClick(message) }
        }
    }

    inner class ErrorMessageViewHolder(
        private val binding: ItemMessageAssistantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessageUi) {
            binding.textViewMessage.text = "⚠️ ${message.content}"
            binding.layoutStreaming.visibility = View.GONE
            binding.layoutActions.visibility = View.GONE
            binding.root.setBackgroundColor(
                binding.root.context.getColor(android.R.color.holo_red_dark)
            )
        }
    }

    // =====================================================================
    // DiffCallback
    // =====================================================================

    class DiffCallback : DiffUtil.ItemCallback<ChatMessageUi>() {
        override fun areItemsTheSame(oldItem: ChatMessageUi, newItem: ChatMessageUi) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessageUi, newItem: ChatMessageUi) =
            oldItem == newItem
    }
}
