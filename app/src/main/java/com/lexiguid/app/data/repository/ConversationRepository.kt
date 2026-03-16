package com.lexiguid.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lexiguid.app.data.local.ConversationDao
import com.lexiguid.app.data.local.ConversationEntity
import com.lexiguid.app.data.local.MessageDao
import com.lexiguid.app.data.local.MessageEntity
import com.lexiguid.app.data.model.ChatMessage
import com.lexiguid.app.data.model.MessageRole
import com.lexiguid.app.data.model.ToolCallInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val gson: Gson
) {

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    suspend fun getConversation(id: String): ConversationEntity? =
        conversationDao.getById(id)

    fun getMessages(conversationId: String): Flow<List<ChatMessage>> =
        messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toChatMessage() }
        }

    suspend fun createConversation(
        title: String,
        subject: String? = null,
        chapter: String? = null
    ): ConversationEntity {
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            subject = subject,
            chapter = chapter
        )
        conversationDao.upsert(conversation)
        return conversation
    }

    suspend fun saveMessage(conversationId: String, message: ChatMessage) {
        val toolCallsJson = if (message.toolCalls.isNotEmpty()) {
            gson.toJson(message.toolCalls)
        } else null

        val entity = MessageEntity(
            id = message.id,
            conversationId = conversationId,
            role = message.role.name,
            text = message.text,
            thinking = message.thinking,
            toolCalls = toolCallsJson,
            imagePath = message.imagePath,
            timestamp = message.timestamp
        )
        messageDao.insert(entity)
        conversationDao.incrementMessageCount(conversationId)
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.deleteById(id)
    }

    suspend fun updateTitle(id: String, title: String) {
        conversationDao.getById(id)?.let { conv ->
            conversationDao.upsert(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun MessageEntity.toChatMessage(): ChatMessage {
        val toolCallList: List<ToolCallInfo> = if (toolCalls != null) {
            try {
                val type = object : TypeToken<List<ToolCallInfo>>() {}.type
                gson.fromJson(toolCalls, type)
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        return ChatMessage(
            id = id,
            role = if (role == MessageRole.USER.name) MessageRole.USER else MessageRole.AI,
            text = text,
            thinking = thinking,
            toolCalls = toolCallList,
            timestamp = timestamp,
            imagePath = imagePath
        )
    }
}
