package com.lexiguid.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.lexiguid.app.data.model.ChatMessage
import com.lexiguid.app.ui.theme.*

/**
 * User message bubble — right-aligned, primary color.
 */
@Composable
fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isSystemInDarkTheme()) UserBubbleDark else UserBubbleLight,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = LexiOnPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * AI message bubble — left-aligned, surface variant color, with markdown rendering.
 */
@Composable
fun AiMessageBubble(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Thinking block (collapsible)
        if (message.thinking != null) {
            ThinkingBlock(text = message.thinking)
        }

        // Tool call chips
        message.toolCalls.forEach { tool ->
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        "Searched: ${tool.query.take(30)}${if (tool.query.length > 30) "..." else ""} (${tool.resultCount} results)",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Main content
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isSystemInDarkTheme()) AiBubbleDark else AiBubbleLight,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            RichText(
                modifier = Modifier.padding(12.dp)
            ) {
                Markdown(content = message.text)
            }
        }
    }
}

/**
 * Streaming AI response — shows in-progress text with blinking cursor.
 */
@Composable
fun StreamingBubble(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isSystemInDarkTheme()) AiBubbleDark else AiBubbleLight,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                RichText(modifier = Modifier.weight(1f, fill = false)) {
                    Markdown(content = text)
                }
                // Blinking cursor
                Text(
                    text = "│",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * Collapsible thinking block — shows AI's chain-of-thought reasoning.
 */
@Composable
fun ThinkingBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDark) ThinkingBgDark else ThinkingBgLight,
        modifier = Modifier
            .widthIn(max = 340.dp)
            .padding(bottom = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
