package com.lexiguid.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf("System") }
    var temperature by remember { mutableFloatStateOf(0.3f) }
    var maxTokens by remember { mutableIntStateOf(4096) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEach { theme ->
                    FilterChip(
                        selected = selectedTheme == theme,
                        onClick = { selectedTheme = theme },
                        label = { Text(theme) }
                    )
                }
            }

            HorizontalDivider()

            // Inference settings
            Text("Inference", style = MaterialTheme.typography.titleMedium)

            Text("Temperature: ${String.format("%.2f", temperature)}")
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0.1f..1.0f,
                steps = 8
            )
            Text(
                "Lower = more focused, Higher = more creative",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Text("Max tokens: $maxTokens")
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = { maxTokens = it.toInt() },
                valueRange = 512f..8192f,
                steps = 14
            )

            HorizontalDivider()

            // About
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text(
                "LexiGuid v1.0.0\nOffline AI Study Companion\nPowered by Gemma on-device",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
