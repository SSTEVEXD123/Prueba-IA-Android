package com.jarvis.app.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.jarvis.app.ai.llm.ChatMessage
import com.jarvis.app.ai.llm.GenerationParams
import com.jarvis.app.ai.llm.LlamaEngine
import com.jarvis.app.data.database.AppDatabase
import com.jarvis.app.data.database.ConversationEntity
import com.jarvis.app.data.database.MessageEntity
import com.jarvis.app.utils.JarvisConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ChatViewModel
 * Gestiona el estado de la pantalla de chat, la comunicación con LlamaEngine
 * y la persistencia en Room
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val db = AppDatabase.getInstance(application)
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val aiModelDao = db.aiModelDao()
    private val engine = LlamaEngine.getInstance()

    // =====================================================================
    // Estado de la UI
    // =====================================================================

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    var currentConversationId: Long = -1
        private set

    private var generationJob: Job? = null

    // =====================================================================
    // Inicialización
    // =====================================================================

    init {
        viewModelScope.launch {
            checkModelStatus()
        }
    }

    private suspend fun checkModelStatus() {
        val activeModel = aiModelDao.getActiveModel("llm")
        if (activeModel != null && activeModel.isDownloaded) {
            _uiState.update { it.copy(
                modelName = activeModel.displayName,
                modelLoaded = engine.isModelLoaded()
            )}

            if (!engine.isModelLoaded()) {
                loadModel(activeModel.filePath)
            }
        } else {
            _uiState.update { it.copy(
                modelLoaded = false,
                statusMessage = "Sin modelo activo. Ve a Ajustes → Modelos para instalar uno."
            )}
        }
    }

    fun loadModel(modelPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoadingModel = true, statusMessage = "Cargando modelo...") }

            val result = engine.loadModel(
                modelPath = modelPath,
                nCtx = JarvisConfig.DEFAULT_N_CTX,
                nThreads = JarvisConfig.DEFAULT_N_THREADS
            )

            result.onSuccess { info ->
                Log.i(TAG, "Modelo cargado: $info")
                _uiState.update { it.copy(
                    isLoadingModel = false,
                    modelLoaded = true,
                    statusMessage = "Modelo listo ✓"
                )}
            }.onFailure { e ->
                Log.e(TAG, "Error al cargar modelo: ${e.message}")
                _uiState.update { it.copy(
                    isLoadingModel = false,
                    modelLoaded = false,
                    statusMessage = "Error al cargar modelo: ${e.message}"
                )}
            }
        }
    }

    // =====================================================================
    // Gestión de conversaciones
    // =====================================================================

    fun startNewConversation() {
        viewModelScope.launch {
            val activeModel = aiModelDao.getActiveModel("llm")
            val modelName = activeModel?.displayName ?: "Jarvis"

            val convId = conversationDao.insert(
                ConversationEntity(
                    title = "Nueva conversación",
                    modelName = modelName
                )
            )
            currentConversationId = convId
            _messages.value = emptyList()
            _uiState.update { it.copy(conversationId = convId) }
        }
    }

    fun loadConversation(conversationId: Long) {
        viewModelScope.launch {
            currentConversationId = conversationId

            messageDao.getMessagesForConversation(conversationId).collect { entities ->
                _messages.value = entities.map { it.toUiModel() }
            }
        }
    }

    // =====================================================================
    // Envío de mensajes y generación
    // =====================================================================

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (!engine.isModelLoaded()) {
            addErrorMessage("El modelo no está cargado. Por favor instala y activa un modelo en Ajustes.")
            return
        }

        viewModelScope.launch {
            // Crear conversación si no existe
            if (currentConversationId == -1L) {
                startNewConversation()
                delay(100) // Esperar a que se cree
            }

            // Guardar mensaje del usuario
            val userMsgId = messageDao.insert(
                MessageEntity(
                    conversationId = currentConversationId,
                    role = "user",
                    content = userInput
                )
            )

            // Actualizar UI con mensaje del usuario
            val currentMessages = _messages.value.toMutableList()
            currentMessages.add(ChatMessageUi(
                id = userMsgId,
                role = "user",
                content = userInput,
                timestamp = System.currentTimeMillis()
            ))

            // Placeholder para la respuesta del asistente
            val assistantPlaceholderId = System.currentTimeMillis()
            currentMessages.add(ChatMessageUi(
                id = assistantPlaceholderId,
                role = "assistant",
                content = "",
                isStreaming = true
            ))
            _messages.value = currentMessages

            _uiState.update { it.copy(isGenerating = true) }

            // Construir historial de chat para el contexto
            val history = currentMessages
                .filter { it.role != "assistant" || it.content.isNotBlank() }
                .dropLast(1) // Excluir el placeholder
                .takeLast(20)
                .map { ChatMessage(role = it.role, content = it.content) }

            val startTime = System.currentTimeMillis()
            val fullResponse = StringBuilder()

            // Generar respuesta con streaming
            generationJob = launch(Dispatchers.IO) {
                try {
                    val params = GenerationParams(
                        maxTokens = JarvisConfig.DEFAULT_MAX_TOKENS,
                        temperature = JarvisConfig.DEFAULT_TEMPERATURE,
                        topP = JarvisConfig.DEFAULT_TOP_P,
                        repeatPenalty = JarvisConfig.DEFAULT_REPEAT_PENALTY
                    )

                    // Simular streaming (el verdadero streaming viene del JNI callback)
                    val result = engine.generate(userInput, history, params)

                    result.onSuccess { response ->
                        fullResponse.append(response)

                        // Actualizar mensaje con respuesta completa
                        withContext(Dispatchers.Main) {
                            val updatedMessages = _messages.value.toMutableList()
                            val placeholderIndex = updatedMessages.indexOfFirst {
                                it.id == assistantPlaceholderId
                            }
                            if (placeholderIndex != -1) {
                                updatedMessages[placeholderIndex] = ChatMessageUi(
                                    id = assistantPlaceholderId,
                                    role = "assistant",
                                    content = response,
                                    isStreaming = false,
                                    generationTimeMs = System.currentTimeMillis() - startTime
                                )
                                _messages.value = updatedMessages
                            }
                        }

                        // Guardar en base de datos
                        val genTime = System.currentTimeMillis() - startTime
                        val assistantMsgId = messageDao.insert(
                            MessageEntity(
                                conversationId = currentConversationId,
                                role = "assistant",
                                content = response,
                                generationTimeMs = genTime,
                                tokensUsed = engine.countTokens(response).coerceAtLeast(0)
                            )
                        )

                        // Actualizar título de conversación si es el primer mensaje
                        if (currentMessages.size <= 2) {
                            updateConversationTitle(userInput)
                        }

                        conversationDao.updateTimestamp(currentConversationId)

                    }.onFailure { e ->
                        withContext(Dispatchers.Main) {
                            addErrorMessage("Error: ${e.message}")
                        }
                    }

                } catch (e: CancellationException) {
                    Log.i(TAG, "Generación cancelada por el usuario")
                } catch (e: Exception) {
                    Log.e(TAG, "Error inesperado: ${e.message}")
                    withContext(Dispatchers.Main) {
                        addErrorMessage("Error inesperado: ${e.message}")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isGenerating = false) }
                    }
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        _uiState.update { it.copy(isGenerating = false) }
    }

    private fun addErrorMessage(error: String) {
        val currentMessages = _messages.value.toMutableList()
        // Eliminar placeholder de streaming si existe
        currentMessages.removeIf { it.isStreaming }
        currentMessages.add(ChatMessageUi(
            id = System.currentTimeMillis(),
            role = "error",
            content = error,
            isStreaming = false
        ))
        _messages.value = currentMessages
    }

    private suspend fun updateConversationTitle(firstMessage: String) {
        val title = if (firstMessage.length > 50) {
            firstMessage.take(47) + "..."
        } else {
            firstMessage
        }
        val conversation = conversationDao.getById(currentConversationId) ?: return
        conversationDao.update(conversation.copy(title = title))
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            messageDao.delete(messageId)
            _messages.update { it.filter { msg -> msg.id != messageId } }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            if (currentConversationId != -1L) {
                messageDao.deleteForConversation(currentConversationId)
                _messages.value = emptyList()
            }
        }
    }

    // =====================================================================
    // Extensiones
    // =====================================================================

    private fun MessageEntity.toUiModel() = ChatMessageUi(
        id = id,
        role = role,
        content = content,
        timestamp = timestamp,
        generationTimeMs = generationTimeMs,
        isStreaming = false
    )
}

// =====================================================================
// Data Classes para UI
// =====================================================================

data class ChatUiState(
    val conversationId: Long = -1,
    val modelName: String = "Sin modelo",
    val modelLoaded: Boolean = false,
    val isLoadingModel: Boolean = false,
    val isGenerating: Boolean = false,
    val statusMessage: String = ""
)

data class ChatMessageUi(
    val id: Long,
    val role: String, // "user", "assistant", "error"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val generationTimeMs: Long = 0
)
