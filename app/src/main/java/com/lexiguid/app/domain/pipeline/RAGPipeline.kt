package com.lexiguid.app.domain.pipeline

import android.graphics.Bitmap
import com.lexiguid.app.data.model.ChatMessage
import com.lexiguid.app.data.model.MessageRole
import com.lexiguid.app.data.model.SearchResult
import com.lexiguid.app.data.model.StudentContext
import com.lexiguid.app.data.model.ToolCallInfo
import com.lexiguid.app.domain.engine.GemmaInferenceEngine
import com.lexiguid.app.domain.engine.StreamToken
import com.lexiguid.app.domain.router.AgentModeRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Complete on-device RAG pipeline.
 * Replaces the backend's orchestrator + sub-agents + tools.
 *
 * Flow:
 *   User message
 *     → AgentModeRouter (detect greeting/rag/knowledge)
 *     → [if RAG] EmbeddingGemma → ObjectBox search → augmented prompt
 *     → GemmaEngine.streamChat()
 *     → streaming tokens to UI
 */
@Singleton
class RAGPipeline @Inject constructor(
    private val gemmaEngine: GemmaInferenceEngine,
    private val searchTool: LocalSearchTool,
    private val promptManager: PromptManager,
    private val modeRouter: AgentModeRouter
) {
    private var lastMode: AgentModeRouter.Mode? = null

    /**
     * Process a user message through the full pipeline.
     * Returns a Flow of PipelineEvent for the UI to consume.
     */
    fun processMessage(
        userMessage: String,
        context: StudentContext,
        hasConversationHistory: Boolean,
        image: Bitmap? = null
    ): Flow<PipelineEvent> = flow {

        // Step 1: Detect agent mode
        val mode = modeRouter.detectMode(
            userMessage = userMessage,
            hasActiveSubject = context.hasActiveSubject(),
            hasConversationHistory = hasConversationHistory
        )

        // Switch system prompt if mode changed
        if (mode != lastMode) {
            val systemPrompt = promptManager.getSystemPrompt(mode)
            gemmaEngine.resetConversation(systemPrompt)
            lastMode = mode
        }

        // Step 2: Build the prompt based on mode
        val (finalPrompt, searchResults) = when (mode) {
            AgentModeRouter.Mode.RAG -> {
                emit(PipelineEvent.Searching(userMessage))

                val results = searchTool.searchKb(
                    query = userMessage,
                    subject = context.subject,
                    grade = context.grade,
                    chapter = context.chapter,
                    k = 8
                )

                emit(PipelineEvent.SearchComplete(results.size))

                val augmented = buildRAGPrompt(userMessage, results, context)
                Pair(augmented, results)
            }

            AgentModeRouter.Mode.GREETING -> {
                val prompt = "${context.toContextHeader()}\n\n${userMessage}"
                Pair(prompt, emptyList())
            }

            AgentModeRouter.Mode.KNOWLEDGE -> {
                val prompt = "${context.toContextHeader()}\n\n${userMessage}"
                Pair(prompt, emptyList())
            }
        }

        // Step 3: Stream response from Gemma
        emit(PipelineEvent.Generating)

        val responseBuilder = StringBuilder()
        gemmaEngine.streamChat(finalPrompt, image).collect { token ->
            when (token) {
                is StreamToken.Delta -> {
                    responseBuilder.append(token.text)
                    emit(PipelineEvent.Token(token.text))
                }
                is StreamToken.Done -> {
                    val toolCalls = if (searchResults.isNotEmpty()) {
                        listOf(ToolCallInfo(
                            toolName = "search_kb",
                            query = userMessage,
                            resultCount = searchResults.size
                        ))
                    } else emptyList()

                    emit(PipelineEvent.Complete(
                        fullText = responseBuilder.toString(),
                        toolCalls = toolCalls
                    ))
                }
                is StreamToken.Error -> {
                    emit(PipelineEvent.Error(token.message))
                }
            }
        }
    }

    private fun buildRAGPrompt(
        query: String,
        results: List<SearchResult>,
        context: StudentContext
    ): String {
        val contextHeader = context.toContextHeader()

        if (results.isEmpty()) {
            return """
                |$contextHeader
                |
                |[No relevant textbook content found for this query.]
                |The student may be asking about a topic not in the current textbook selection.
                |Answer based on your general knowledge, but mention that no textbook source was found.
                |
                |[Student Question]
                |$query
            """.trimMargin()
        }

        val retrievedDocs = results.mapIndexed { i, r ->
            "[Source ${i + 1}] (${r.subject.replace("_", " ")} > ${r.chapter.replace("_", " ")})\n${r.pageContent}"
        }.joinToString("\n\n")

        return """
            |$contextHeader
            |
            |[Retrieved Knowledge Base Results]
            |$retrievedDocs
            |
            |[Student Question]
            |$query
        """.trimMargin()
    }
}

/**
 * Events emitted by the RAG pipeline for UI consumption.
 */
sealed class PipelineEvent {
    data class Searching(val query: String) : PipelineEvent()
    data class SearchComplete(val resultCount: Int) : PipelineEvent()
    data object Generating : PipelineEvent()
    data class Token(val text: String) : PipelineEvent()
    data class Complete(
        val fullText: String,
        val toolCalls: List<ToolCallInfo> = emptyList()
    ) : PipelineEvent()
    data class Error(val message: String) : PipelineEvent()
}
