package com.lexiguid.app.ui.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    isStreaming: Boolean,
    isEngineReady: Boolean,
    onStopStreaming: () -> Unit,
    onContextClick: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Context button
            IconButton(onClick = onContextClick) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = "Select subject",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Camera button (for image input)
            IconButton(onClick = { /* TODO: CameraX capture */ }) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Take photo",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Text input field
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (!isEngineReady) "Model not loaded..."
                        else "Ask a question...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                enabled = isEngineReady && !isStreaming,
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSend(inputText)
                            inputText = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send / Stop button
            AnimatedContent(
                targetState = isStreaming,
                label = "send_stop_button"
            ) { streaming ->
                if (streaming) {
                    FilledIconButton(
                        onClick = onStopStreaming,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop generation")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSend(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && isEngineReady
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
