package com.jarvis.app.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvis.app.databinding.FragmentChatBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.Intent
import java.util.Locale

/**
 * ChatFragment
 * Fragmento principal de la interfaz de chat estilo Jarvis/ChatGPT
 * Maneja la UI de mensajes, entrada de texto, reconocimiento de voz
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        private const val REQUEST_RECORD_AUDIO = 100
        fun newInstance() = ChatFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupInputBar()
        setupVoiceInput()
        observeViewModel()
        viewModel.startNewConversation()
    }

    // =====================================================================
    // Configuración de RecyclerView
    // =====================================================================

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            onCopyClick = { message -> copyToClipboard(message.content) },
            onDeleteClick = { message -> viewModel.deleteMessage(message.id) },
            onRegenerateClick = { /* regenerar último mensaje */ }
        )

        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
            itemAnimator = null // Desactivar animaciones para mejor performance
        }
    }

    // =====================================================================
    // Barra de entrada de texto
    // =====================================================================

    private fun setupInputBar() {
        binding.editTextInput.apply {
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage()
                    true
                } else false
            }
        }

        binding.buttonSend.setOnClickListener { sendMessage() }

        binding.buttonStop.setOnClickListener {
            viewModel.cancelGeneration()
        }

        binding.buttonNewChat.setOnClickListener {
            viewModel.startNewConversation()
        }
    }

    private fun sendMessage() {
        val input = binding.editTextInput.text?.toString()?.trim() ?: return
        if (input.isEmpty()) return

        binding.editTextInput.text?.clear()
        viewModel.sendMessage(input)
    }

    // =====================================================================
    // Reconocimiento de voz
    // =====================================================================

    private fun setupVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            binding.buttonVoice.isVisible = false
            return
        }

        binding.buttonVoice.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext()).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    updateVoiceButton(true)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        binding.editTextInput.setText(matches[0])
                        binding.editTextInput.setSelection(matches[0].length)
                    }
                    stopListening()
                }

                override fun onError(error: Int) {
                    stopListening()
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió el audio"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                        else -> "Error de reconocimiento"
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) {
                        binding.editTextInput.setText(partial[0])
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "ES"))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        isListening = false
        updateVoiceButton(false)
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun updateVoiceButton(listening: Boolean) {
        binding.buttonVoice.apply {
            if (listening) {
                setIconResource(android.R.drawable.ic_btn_speak_now)
                contentDescription = "Detener grabación"
            } else {
                setIconResource(android.R.drawable.ic_lock_silent_mode_off)
                contentDescription = "Activar micrófono"
            }
        }
    }

    // =====================================================================
    // Observar ViewModel
    // =====================================================================

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                chatAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                }

                // Mostrar estado vacío si no hay mensajes
                binding.layoutEmpty.isVisible = messages.isEmpty()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Botón enviar / detener
                binding.buttonSend.isVisible = !state.isGenerating
                binding.buttonStop.isVisible = state.isGenerating
                binding.editTextInput.isEnabled = !state.isGenerating

                // Estado del modelo
                binding.textViewModelName.text = state.modelName

                // Indicador de carga del modelo
                binding.progressModelLoading.isVisible = state.isLoadingModel

                // Indicador de generación
                binding.progressGenerating.isVisible = state.isGenerating

                // Mensaje de estado
                if (state.statusMessage.isNotEmpty()) {
                    binding.textViewStatus.text = state.statusMessage
                    binding.textViewStatus.isVisible = true
                } else {
                    binding.textViewStatus.isVisible = false
                }

                // Bloquear input si el modelo no está cargado
                binding.buttonSend.isEnabled = state.modelLoaded && !state.isGenerating
                binding.buttonVoice.isEnabled = state.modelLoaded && !state.isGenerating
            }
        }
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Jarvis Response", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopListening()
        _binding = null
    }
}
