package com.lexiguid.app.ui.modelmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lexiguid.app.data.model.GemmaModel
import com.lexiguid.app.data.model.ModelState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device info
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Device Info", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "RAM: ${uiState.deviceRamGb} GB | Free storage: ${uiState.freeStorageGb} GB",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Inference models section
            item {
                Text(
                    "Inference Models",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Select one model for chat. Switch anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            items(GemmaModel.entries.toList()) { model ->
                InferenceModelCard(
                    model = model,
                    state = uiState.modelStates[model] ?: ModelState.NotDownloaded,
                    isActive = model == uiState.activeModel,
                    onDownload = { viewModel.downloadModel(model) },
                    onDelete = { viewModel.deleteModel(model) },
                    onActivate = { viewModel.activateModel(model) }
                )
            }

            // Embedding model section
            item {
                Text(
                    "Embedding Model (Required)",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                EmbeddingModelCard(
                    state = uiState.embeddingModelState,
                    onDownload = { viewModel.downloadEmbeddingModel() },
                    onDelete = { viewModel.deleteEmbeddingModel() }
                )
            }

            // Knowledge base section
            item {
                Text(
                    "Knowledge Bases",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    "Pre-built databases on device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            if (uiState.knowledgeBases.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.FolderOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No knowledge bases found.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Copy ObjectBox DB folders to:\n${uiState.kbBasePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                items(uiState.knowledgeBases) { kb ->
                    KnowledgeBaseCard(
                        subject = kb.subject,
                        grade = kb.grade,
                        sizeMb = kb.sizeBytes / (1024 * 1024),
                        onDelete = { viewModel.deleteKnowledgeBase(kb.subject, kb.grade) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InferenceModelCard(
    model: GemmaModel,
    state: ModelState,
    isActive: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(model.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Size: ${model.sizeBytes / (1024 * 1024)} MB | Min RAM: ${model.minRamGb} GB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (model.supportsVision) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Vision + Audio") },
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                if (isActive) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                is ModelState.NotDownloaded -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }
                is ModelState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${(state.progress * 100).toInt()}% downloaded",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                is ModelState.Downloaded, is ModelState.Ready -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isActive) {
                            Button(
                                onClick = onActivate,
                                modifier = Modifier.weight(1f)
                            ) { Text("Activate") }
                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            ) { Text("Active") }
                        }
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
                is ModelState.Initializing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Initializing...", style = MaterialTheme.typography.labelSmall)
                }
                is ModelState.Error -> {
                    Text(
                        "Error: ${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddingModelCard(
    state: ModelState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("EmbeddingGemma (308M)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Required for RAG search. <200 MB RAM, <50ms per query.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                is ModelState.NotDownloaded -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text("Download (~200 MB)")
                    }
                }
                is ModelState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ModelState.Ready, is ModelState.Downloaded -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ready", style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun KnowledgeBaseCard(
    subject: String,
    grade: String,
    sizeMb: Long,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(subject.replace("_", " "), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${grade.replace("_", " ")} · ${sizeMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete KB")
            }
        }
    }
}
