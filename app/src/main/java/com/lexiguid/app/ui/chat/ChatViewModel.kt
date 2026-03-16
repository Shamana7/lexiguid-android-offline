package com.lexiguid.app.ui.chat

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lexiguid.app.data.local.UserProfileDao
import com.lexiguid.app.data.model.ChatMessage
import com.lexiguid.app.data.model.MessageRole
import com.lexiguid.app.data.model.ModelState
import com.lexiguid.app.data.model.StudentContext
import com.lexiguid.app.data.model.ToolCallInfo
import com.lexiguid.app.data.repository.ConversationRepository
import com.lexiguid.app.domain.engine.GemmaInferenceEngine
import com.lexiguid.app.domain.pipeline.LocalSearchTool
import com.lexiguid.app.domain.pipeline.PipelineEvent
import com.lexiguid.app.domain.pipeline.RAGPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val ragPipeline: RAGPipeline,
    private val gemmaEngine: GemmaInferenceEngine,
    private val searchTool: LocalSearchTool,
    private val conversationRepo: ConversationRepository,
    private val profileDao: UserProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversationId: String? = null
    private var streamingJob: Job? = null

    fun loadConversation(id: String?) {
        viewModelScope.launch {
            // Load profile for context
            val profile = profileDao.getProfileOnce()
            val studentContext = StudentContext(
                name = profile?.firstName ?: "",
                grade = profile?.grade ?: "",
                country = profile?.country ?: "",
                state = profile?.state ?: "",
                board = profile?.board ?: "",
                medium = profile?.medium ?: ""
            )
            _uiState.update { it.copy(studentContext = studentContext) }

            // Load available subjects
            val subjects = searchTool.getAvailableSubjects()
            _uiState.update { it.copy(availableSubjects = subjects) }

            // Load existing conversation or start new
            if (id != null && id != "new") {
                conversationId = id
                val messages = conversationRepo.getMessages(id).first()
                _uiState.update { it.copy(messages = messages) }

                // Restore subject/chapter from conversation
                val conv = conversationRepo.getConversation(id)
                if (conv?.subject != null) {
                    updateContext(conv.subject, conv.chapter)
                }
            }

            // Check engine readiness
            _uiState.update {
                it.copy(isEngineReady = gemmaEngine.state.value is ModelState.Ready)
            }
        }
    }

    fun updateContext(subject: String, chapter: String? = null) {
        viewModelScope.launch {
            val chapters = if (subject.isNotBlank()) {
                val grade = _uiState.value.studentContext.grade
                searchTool.getAvailableChapters(subject, grade)
            } else emptyList()

            _uiState.update { state ->
                state.copy(
                    studentContext = state.studentContext.copy(
                        subject = subject,
                        chapter = chapter,
                        chapters = emptyList()
                    ),
                    availableChapters = chapters
                )
            }
        }
    }

    fun sendMessage(text: String, image: Bitmap? = null) {
        if (text.isBlank() && image == null) return
        if (_uiState.value.isStreaming) return

        viewModelScope.launch {
            // Create conversation if new
            if (conversationId == null) {
                val title = text.take(50)
                val conv = conversationRepo.createConversation(
                    title = title,
                    subject = _uiState.value.studentContext.subject.ifBlank { null },
                    chapter = _uiState.value.studentContext.chapter
                )
                conversationId = conv.id
            }

            // Add user message
            val userMessage = ChatMessage(
                role = MessageRole.USER,
                text = text,
                imagePath = null // TODO: save image to storage
            )
            _uiState.update { it.copy(messages = it.messages + userMessage) }
            conversationRepo.saveMessage(conversationId!!, userMessage)

            // Start streaming AI response
            _uiState.update { it.copy(isStreaming = true, streamingText = "") }

            streamingJob = launch {
                val responseBuilder = StringBuilder()
                var toolCalls = emptyList<ToolCallInfo>()

                try {
                    ragPipeline.processMessage(
                        userMessage = text,
                        context = _uiState.value.studentContext,
                        hasConversationHistory = _uiState.value.messages.size > 1,
                        image = image
                    ).collect { event ->
                        when (event) {
                            is PipelineEvent.Searching -> {
                                _uiState.update {
                                    it.copy(statusMessage = "Searching knowledge base...")
                                }
                            }
                            is PipelineEvent.SearchComplete -> {
                                _uiState.update {
                                    it.copy(statusMessage = "Found ${event.resultCount} sources")
                                }
                            }
                            is PipelineEvent.Generating -> {
                                _uiState.update { it.copy(statusMessage = null) }
                            }
                            is PipelineEvent.Token -> {
                                responseBuilder.append(event.text)
                                _uiState.update {
                                    it.copy(streamingText = responseBuilder.toString())
                                }
                            }
                            is PipelineEvent.Complete -> {
                                toolCalls = event.toolCalls
                            }
                            is PipelineEvent.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isStreaming = false,
                                        statusMessage = null,
                                        errorMessage = event.message
                                    )
                                }
                                return@collect
                            }
                        }
                    }

                    // Save completed AI message
                    val aiMessage = ChatMessage(
                        role = MessageRole.AI,
                        text = responseBuilder.toString(),
                        toolCalls = toolCalls
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + aiMessage,
                            isStreaming = false,
                            streamingText = "",
                            statusMessage = null
                        )
                    }
                    conversationRepo.saveMessage(conversationId!!, aiMessage)

                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            streamingText = "",
                            statusMessage = null,
                            errorMessage = e.message ?: "An error occurred"
                        )
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update {
            // Keep whatever was streamed so far
            val partialMessage = if (it.streamingText.isNotBlank()) {
                ChatMessage(role = MessageRole.AI, text = it.streamingText)
            } else null

            it.copy(
                messages = if (partialMessage != null) it.messages + partialMessage else it.messages,
                isStreaming = false,
                streamingText = "",
                statusMessage = null
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun showContextSheet() {
        _uiState.update { it.copy(showContextSheet = true) }
    }

    fun hideContextSheet() {
        _uiState.update { it.copy(showContextSheet = false) }
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isEngineReady: Boolean = false,
    val studentContext: StudentContext = StudentContext(),
    val availableSubjects: List<String> = emptyList(),
    val availableChapters: List<String> = emptyList(),
    val showContextSheet: Boolean = false
)
