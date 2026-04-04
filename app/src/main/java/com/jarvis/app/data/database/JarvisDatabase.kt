package com.jarvis.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// =====================================================================
// ENTIDADES DE BASE DE DATOS
// =====================================================================

/**
 * Conversación de chat
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isArchived: Boolean = false
)

/**
 * Mensaje individual en una conversación
 */
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensUsed: Int = 0,
    val modelName: String = "",
    val generationTimeMs: Long = 0,
    val hasImage: Boolean = false,
    val imagePath: String = ""
)

/**
 * Modelo de IA instalado
 */
@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey
    val modelId: String, // nombre único del modelo
    val displayName: String,
    val description: String,
    val type: String, // "llm", "image", "vision"
    val filePath: String,
    val fileSize: Long,
    val isDownloaded: Boolean = false,
    val isActive: Boolean = false,
    val downloadUrl: String = "",
    val quantization: String = "", // "Q4_K_M", "Q5_K_M", etc.
    val contextLength: Int = 4096,
    val parameters: String = "", // JSON con params extra
    val installedAt: Long = 0
)

/**
 * Imagen generada por Stable Diffusion
 */
@Entity(tableName = "generated_images")
data class GeneratedImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prompt: String,
    val negativePrompt: String = "",
    val imagePath: String,
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val width: Int = 512,
    val height: Int = 512,
    val seed: Long = -1,
    val modelUsed: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * PDF generado por la app
 */
@Entity(tableName = "generated_pdfs")
data class GeneratedPdfEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val fileSize: Long = 0,
    val pageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val conversationId: Long = 0
)

/**
 * Archivo gestionado por la app
 */
@Entity(tableName = "managed_files")
data class ManagedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val type: String, // "txt", "pdf", "image", "json", etc.
    val size: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val tags: String = "" // CSV de etiquetas
)

// =====================================================================
// DAOs
// =====================================================================

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET updatedAt = :time, messageCount = messageCount + 1 WHERE id = :id")
    suspend fun updateTimestamp(id: Long, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE conversations SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE isArchived = 0")
    suspend fun getCount(): Int
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessagesForConversation(convId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    suspend fun getMessagesSync(convId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteForConversation(convId: Long)

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    suspend fun search(query: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :convId")
    suspend fun getCountForConversation(convId: Long): Int
}

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models ORDER BY type, displayName")
    fun getAllModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE type = :type")
    suspend fun getModelsByType(type: String): List<AiModelEntity>

    @Query("SELECT * FROM ai_models WHERE isActive = 1 AND type = :type LIMIT 1")
    suspend fun getActiveModel(type: String): AiModelEntity?

    @Query("SELECT * FROM ai_models WHERE modelId = :id")
    suspend fun getById(id: String): AiModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(model: AiModelEntity)

    @Query("UPDATE ai_models SET isActive = 0 WHERE type = :type")
    suspend fun deactivateAll(type: String)

    @Query("UPDATE ai_models SET isActive = 1 WHERE modelId = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE ai_models SET isDownloaded = :downloaded, filePath = :path, installedAt = :time WHERE modelId = :id")
    suspend fun setDownloaded(id: String, downloaded: Boolean, path: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_models WHERE modelId = :id")
    suspend fun delete(id: String)
}

@Dao
interface GeneratedImageDao {
    @Query("SELECT * FROM generated_images ORDER BY createdAt DESC")
    fun getAllImages(): Flow<List<GeneratedImageEntity>>

    @Insert
    suspend fun insert(image: GeneratedImageEntity): Long

    @Query("DELETE FROM generated_images WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM generated_images")
    suspend fun getCount(): Int
}

@Dao
interface GeneratedPdfDao {
    @Query("SELECT * FROM generated_pdfs ORDER BY createdAt DESC")
    fun getAllPdfs(): Flow<List<GeneratedPdfEntity>>

    @Insert
    suspend fun insert(pdf: GeneratedPdfEntity): Long

    @Update
    suspend fun update(pdf: GeneratedPdfEntity)

    @Query("DELETE FROM generated_pdfs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ManagedFileDao {
    @Query("SELECT * FROM managed_files ORDER BY modifiedAt DESC")
    fun getAllFiles(): Flow<List<ManagedFileEntity>>

    @Query("SELECT * FROM managed_files WHERE type = :type ORDER BY modifiedAt DESC")
    fun getFilesByType(type: String): Flow<List<ManagedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: ManagedFileEntity): Long

    @Update
    suspend fun update(file: ManagedFileEntity)

    @Query("DELETE FROM managed_files WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM managed_files WHERE name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<ManagedFileEntity>
}
