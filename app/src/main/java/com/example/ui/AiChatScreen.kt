package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.TeamChatMessage
import com.example.ui.theme.MossInk
import com.example.ui.theme.MossInk2
import com.example.ui.theme.MossLime
import com.example.ui.theme.MossLimeDark
import com.example.ui.theme.MossLine
import com.example.ui.theme.MossMist
import com.example.ui.theme.MossMuted
import com.example.ui.theme.MossPaper
import com.example.ui.theme.MossWhite
import com.example.viewmodel.ChatStreamState

@Composable
fun AiChatScreen(
    messages: List<TeamChatMessage>,
    streamState: ChatStreamState,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamState) {
        val target = messages.size + if (streamState is ChatStreamState.Streaming) 1 else 0
        if (target > 0) {
            listState.animateScrollToItem((target - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MossMist)
    ) {
        // Header banner
        Surface(
            color = MossInk,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(color = MossLime, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "ЕДИНЫЙ ИИ-МЕНТОР КОМАНДЫ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MossInk,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "Gemini 3.8",
                        style = MaterialTheme.typography.labelSmall,
                        color = MossPaper.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = "Сервер локальных ИИ для инвестора — вопросы, YouTube-разборы и снятие блокеров",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MossPaper
                )
            }
        }

        // Quick prompts including YouTube link analysis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                "Где главное узкое место по времени?",
                "Какой стек выбрать: vLLM или Ollama для 4× 4090?",
                "Как посчитать ROI сервера для инвестора?",
                "Разбери YouTube: https://www.youtube.com/watch?v="
            )
            presets.forEach { preset ->
                Surface(
                    color = MossWhite,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MossLine),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (preset.endsWith("v=")) {
                                inputText = preset
                            } else {
                                onSendMessage(preset)
                            }
                        }
                ) {
                    Text(
                        text = preset.take(36) + if (preset.length > 36) "…" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MossInk,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg = msg)
            }

            if (streamState is ChatStreamState.Streaming) {
                item(key = "streaming_bubble") {
                    ChatBubble(
                        msg = TeamChatMessage(
                            id = "streaming",
                            senderName = "MossNode AI",
                            role = "AI",
                            text = streamState.partialText.ifBlank { "Анализирую доску, зависимости и вопрос…" }
                        ),
                        isStreaming = true
                    )
                }
            }

            if (streamState is ChatStreamState.Error) {
                item(key = "chat_error") {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = streamState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Composer row (with generous right margin so it doesn't collide with Voice Hotkey FAB)
        Surface(
            color = MossPaper,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 84.dp, top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Спроси ИИ или вставь ссылку на YouTube…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MossInk,
                        unfocusedBorderColor = MossLine,
                        focusedContainerColor = MossWhite,
                        unfocusedContainerColor = MossWhite
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_chat_input")
                )

                val sending = streamState is ChatStreamState.Streaming
                Surface(
                    color = MossInk,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    IconButton(
                        onClick = {
                            val txt = inputText.trim()
                            if (txt.isNotEmpty() && !sending) {
                                inputText = ""
                                onSendMessage(txt)
                            }
                        },
                        enabled = !sending,
                        modifier = Modifier.testTag("ai_chat_send_button")
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MossLime,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Отправить в ИИ-чат",
                                tint = MossLime
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    msg: TeamChatMessage,
    isStreaming: Boolean = false
) {
    val isUser = msg.role == "USER"
    val isVoiceLog = msg.role == "VOICE_LOG"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = when {
                isUser -> MossInk
                isVoiceLog -> MossLime.copy(alpha = 0.35f)
                else -> MossWhite
            },
            shape = RoundedCornerShape(16.dp),
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, MossLine) else null,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isUser) {
                        Icon(
                            imageVector = if (isVoiceLog) Icons.Rounded.Mic else Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (isVoiceLog) MossInk else MossLimeDark,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = if (isStreaming) "${msg.senderName} · печатает…" else msg.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MossLime else MossInk2
                    )
                }

                if (msg.youtubeUrl.isNotBlank()) {
                    Surface(
                        color = if (isUser) MossInk2 else MossPaper,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircleOutline,
                                contentDescription = "YouTube",
                                tint = if (isUser) MossLime else MossInk,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = msg.youtubeUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) MossPaper else MossInk2,
                                maxLines = 1
                            )
                        }
                    }
                }

                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MossPaper else MossInk
                )
            }
        }
    }
}
