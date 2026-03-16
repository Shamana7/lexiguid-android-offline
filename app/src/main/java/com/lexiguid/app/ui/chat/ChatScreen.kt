package com.lexiguid.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexiguid.app.data.model.MessageRole
import com.lexiguid.app.ui.chat.components.AiMessageBubble
import com.lexiguid.app.ui.chat.components.ChatInputBar
import com.lexiguid.app.ui.chat.components.ContextConfigSheet
import com.lexiguid.app.ui.chat.components.StreamingBubble
import com.lexiguid.app.ui.chat.components.UserMessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Load conversation on first composition
    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Context configuration bottom sheet
    if (uiState.showContextSheet) {
        ContextConfigSheet(
            subjects = uiState.availableSubjects,
            chapters = uiState.availableChapters,
            currentSubject = uiState.studentContext.subject,
            currentChapter = uiState.studentContext.chapter,
            onSubjectChange = { viewModel.updateContext(it) },
            onChapterChange = { viewModel.updateContext(uiState.studentContext.subject, it) },
            onDismiss = { viewModel.hideContextSheet() }
        )
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                subject = uiState.studentContext.subject,
                chapter = uiState.studentContext.chapter,
                isStreaming = uiState.isStreaming,
                onNavigateBack = onNavigateBack,
                onContextChange = { viewModel.showContextSheet() }
            )
        },
        bottomBar = {
            ChatInputBar(
                onSend = { text -> viewModel.sendMessage(text) },
                isStreaming = uiState.isStreaming,
                isEngineReady = uiState.isEngineReady,
                onStopStreaming = { viewModel.stopGeneration() },
                onContextClick = { viewModel.showContextSheet() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status bar (searching, etc.)
            AnimatedVisibility(visible = uiState.statusMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.statusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Engine not ready warning
            if (!uiState.isEngineReady) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Model not loaded. Go to Model Manager to download and initialize.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Message list
            if (uiState.messages.isEmpty() && !uiState.isStreaming) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Ask me anything about your studies!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (uiState.studentContext.subject.isBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.showContextSheet() }) {
                                Icon(Icons.Default.MenuBook, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select a subject to get started")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        when (message.role) {
                            MessageRole.USER -> UserMessageBubble(message = message)
                            MessageRole.AI -> AiMessageBubble(message = message)
                        }
                    }

                    // Streaming bubble (in-progress AI response)
                    if (uiState.isStreaming && uiState.streamingText.isNotBlank()) {
                        item(key = "streaming") {
                            StreamingBubble(text = uiState.streamingText)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    subject: String,
    chapter: String?,
    isStreaming: Boolean,
    onNavigateBack: () -> Unit,
    onContextChange: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text("LexiGuid", style = MaterialTheme.typography.titleMedium)
                if (subject.isNotBlank()) {
                    Text(
                        text = buildString {
                            append(subject.replace("_", " "))
                            if (!chapter.isNullOrBlank()) {
                                append(" > ${chapter.replace("_", " ")}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            // Context filter button
            IconButton(onClick = onContextChange) {
                Icon(Icons.Default.Tune, contentDescription = "Configure context")
            }
        }
    )
}
