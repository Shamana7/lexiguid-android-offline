package com.lexiguid.app.data.model

import java.util.UUID

/**
 * A chat message displayed in the UI.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val thinking: String? = null,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String? = null
)

enum class MessageRole { USER, AI }

data class ToolCallInfo(
    val toolName: String,
    val query: String,
    val resultCount: Int = 0
)

/**
 * Student context for RAG pipeline and prompt building.
 */
data class StudentContext(
    val name: String = "",
    val grade: String = "",
    val subject: String = "",
    val chapter: String? = null,
    val chapters: List<String> = emptyList(),
    val country: String = "",
    val state: String = "",
    val board: String = "",
    val medium: String = "",
    val contentType: String? = null
) {
    fun hasActiveSubject(): Boolean = subject.isNotBlank()

    fun toContextHeader(): String = buildString {
        append("[Student Context]")
        if (name.isNotBlank()) append(" Name: $name |")
        if (grade.isNotBlank()) append(" Grade: $grade |")
        if (subject.isNotBlank()) append(" Subject: $subject |")
        if (!chapter.isNullOrBlank()) append(" Chapter: $chapter |")
        if (board.isNotBlank()) append(" Board: $board |")
        if (medium.isNotBlank()) append(" Medium: $medium |")
        if (country.isNotBlank()) append(" Country: $country |")
        if (state.isNotBlank()) append(" State: $state")
    }
}

/**
 * Result from the local vector search.
 */
data class SearchResult(
    val pageContent: String,
    val subject: String,
    val chapter: String,
    val classLevel: String,
    val score: Float,
    val contentType: String
)

/**
 * Inference config passed to GemmaEngine.
 */
data class InferenceConfig(
    val topK: Int = 64,
    val topP: Float = 0.95f,
    val temperature: Float = 0.3f,
    val maxTokens: Int = 4096,
    val systemInstruction: String = ""
)
