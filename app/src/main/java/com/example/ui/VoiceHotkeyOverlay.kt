package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.VoiceRecorderManager
import com.example.ui.theme.MossCoral
import com.example.ui.theme.MossInk
import com.example.ui.theme.MossLime
import com.example.ui.theme.MossLimeDark
import com.example.ui.theme.MossMuted
import com.example.ui.theme.MossPaper
import com.example.viewmodel.VoiceProcessState

@Composable
fun VoiceHotkeyFab(
    voiceState: VoiceProcessState,
    showDialog: Boolean,
    onOpenDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onStartRecordingState: () -> Unit,
    onResetVoiceState: () -> Unit,
    onSubmitAudio: (ByteArray?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceRecorderManager(context.applicationContext) }
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPermissionGranted = granted
        if (granted) {
            permissionDeniedNote = false
            recorder.startRecording().onSuccess {
                onStartRecordingState()
            }.onFailure {
                permissionDeniedNote = true
            }
        } else {
            permissionDeniedNote = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stopAndDiscard()
        }
    }

    val isRecording = voiceState is VoiceProcessState.Recording
    val pulseTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulseScale"
    )

    FloatingActionButton(
        onClick = {
            onOpenDialog()
            if (voiceState is VoiceProcessState.Idle || voiceState is VoiceProcessState.Done || voiceState is VoiceProcessState.Error) {
                onResetVoiceState()
                if (micPermissionGranted) {
                    recorder.startRecording().onSuccess {
                        onStartRecordingState()
                    }.onFailure {
                        permissionDeniedNote = true
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        containerColor = if (isRecording) MossCoral else MossLime,
        contentColor = if (isRecording) MossPaper else MossInk,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
        modifier = modifier
            .size(60.dp)
            .scale(pulseScale)
            .testTag("voice_hotkey_button")
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = "Горячая голосовая кнопка в календарь задач",
            modifier = Modifier.size(28.dp)
        )
    }

    if (showDialog) {
        VoiceDispatchDialog(
            voiceState = voiceState,
            permissionDeniedNote = permissionDeniedNote,
            onStartMic = {
                if (micPermissionGranted) {
                    recorder.startRecording().onSuccess {
                        permissionDeniedNote = false
                        onStartRecordingState()
                    }.onFailure {
                        permissionDeniedNote = true
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onStopAndAnalyzeMic = {
                val res = recorder.stopAndReadBytes()
                res.fold(
                    onSuccess = { bytes -> onSubmitAudio(bytes, null) },
                    onFailure = { err ->
                        permissionDeniedNote = true
                        onResetVoiceState()
                    }
                )
            },
            onSubmitFallbackText = { txt ->
                recorder.stopAndDiscard()
                onSubmitAudio(null, txt)
            },
            onDismiss = {
                recorder.stopAndDiscard()
                onResetVoiceState()
                onDismissDialog()
            }
        )
    }
}

@Composable
private fun VoiceDispatchDialog(
    voiceState: VoiceProcessState,
    permissionDeniedNote: Boolean,
    onStartMic: () -> Unit,
    onStopAndAnalyzeMic: () -> Unit,
    onSubmitFallbackText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var fallbackText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MossPaper,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = MossLime, shape = CircleShape) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = MossInk,
                        modifier = Modifier.padding(6.dp).size(18.dp)
                    )
                }
                Text(
                    text = "Голосовой разбор → Календарь",
                    style = MaterialTheme.typography.titleLarge,
                    color = MossInk
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (voiceState) {
                    is VoiceProcessState.Recording -> {
                        Surface(
                            color = MossCoral.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ИДЁТ ЗАПИСЬ ГОЛОСА…",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MossCoral
                                )
                                Text(
                                    text = "Продиктуйте задачу, кто отвечает, дедлайн и от какой задачи зависит. Нажмите «Разобрать ИИ», чтобы вставить в календарь.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MossInk
                                )
                            }
                        }
                    }

                    is VoiceProcessState.Analyzing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            CircularProgressIndicator(color = MossInk, modifier = Modifier.size(28.dp))
                            Text(
                                text = "Gemini расшифровывает речь и вставляет задачи в календарь и Kanban…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossInk
                            )
                        }
                    }

                    is VoiceProcessState.Done -> {
                        Surface(
                            color = MossLime.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MossLimeDark)
                                    Text("Добавлено в календарь и доску!", style = MaterialTheme.typography.titleMedium, color = MossInk)
                                }
                                Text(
                                    text = "Расшифровка: ${voiceState.summary}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MossInk
                                )
                                voiceState.addedTitles.forEach { t ->
                                    Text("• $t", style = MaterialTheme.typography.labelMedium, color = MossInk)
                                }
                            }
                        }
                    }

                    is VoiceProcessState.Error -> {
                        Text(
                            text = voiceState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MossCoral
                        )
                    }

                    VoiceProcessState.Idle -> {
                        if (permissionDeniedNote) {
                            Text(
                                text = "Микрофон недоступен или доступ отклонён. Вы можете ввести расшифровку текстом ниже — ИИ так же разберёт её и вставит в календарь.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossMuted
                            )
                        } else {
                            Text(
                                text = "Нажмите «Записать голос», чтобы надиктовать задачу в 1 клик, или введите текст поручения.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossInk
                            )
                        }
                    }
                }

                if (voiceState !is VoiceProcessState.Analyzing && voiceState !is VoiceProcessState.Recording) {
                    OutlinedTextField(
                        value = fallbackText,
                        onValueChange = { fallbackText = it },
                        label = { Text("Или вставьте текст голосовой заметки") },
                        placeholder = { Text("Напр.: Артёму завтра к 18:00 поднять vLLM после настройки CUDA, 6 часов") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_fallback_input")
                    )
                }
            }
        },
        confirmButton = {
            when (voiceState) {
                is VoiceProcessState.Recording -> {
                    Button(
                        onClick = onStopAndAnalyzeMic,
                        colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime),
                        modifier = Modifier.testTag("voice_stop_analyze_button")
                    ) {
                        Text("Разобрать ИИ → Календарь")
                    }
                }

                is VoiceProcessState.Analyzing -> {}

                is VoiceProcessState.Done -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime)
                    ) {
                        Text("Готово")
                    }
                }

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (fallbackText.isNotBlank()) {
                            Button(
                                onClick = { onSubmitFallbackText(fallbackText) },
                                colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime),
                                modifier = Modifier.testTag("voice_text_submit_button")
                            ) {
                                Text("Вставить ИИ")
                            }
                        } else {
                            Button(
                                onClick = onStartMic,
                                colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime)
                            ) {
                                Text("Записать голос")
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (voiceState !is VoiceProcessState.Analyzing) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", color = MossMuted)
                }
            }
        }
    )
}
