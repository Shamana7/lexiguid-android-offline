package com.lexiguid.app.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet for configuring subject/chapter context.
 * Mirrors the web app's context configuration modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextConfigSheet(
    subjects: List<String>,
    chapters: List<String>,
    currentSubject: String?,
    currentChapter: String?,
    onSubjectChange: (String) -> Unit,
    onChapterChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Configure Study Context",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Subject selector
            Text(
                text = "Subject",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (subjects.isEmpty()) {
                Text(
                    text = "No knowledge bases loaded. Add them via Model Manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                var expanded by remember { mutableStateOf(false) }
                var selectedSubject by remember { mutableStateOf(currentSubject ?: "") }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSubject.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        placeholder = { Text("Select a subject") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        subjects.forEach { subject ->
                            DropdownMenuItem(
                                text = { Text(subject.replace("_", " ")) },
                                onClick = {
                                    selectedSubject = subject
                                    expanded = false
                                    onSubjectChange(subject)
                                },
                                trailingIcon = {
                                    if (subject == currentSubject) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Chapter selector (chips)
            if (chapters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Chapter",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chapters) { chapter ->
                        FilterChip(
                            selected = chapter == currentChapter?.replace("_", " "),
                            onClick = { onChapterChange(chapter) },
                            label = { Text(chapter) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Context")
            }
        }
    }
}
