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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.VoiceRecorderManager
import com.example.ui.theme.MossCoral
import com.example.ui.theme.MossInk
import com.example.ui.theme.MossInk2
import com.example.ui.theme.MossLime
import com.example.ui.theme.MossLimeDark
import com.example.ui.theme.MossMuted
import com.example.ui.theme.MossPaper
import com.example.ui.theme.MossWhite
import com.example.viewmodel.VoiceProcessState
import kotlinx.coroutines.delay

@Composable
fun VoiceHotkeyFab(
    voiceState: VoiceProcessState,
    showDialog: Boolean,
    showOpenKanbanAction: Boolean,
    onOpenDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onOpenKanban: () -> Unit,
    onStartRecordingState: () -> Unit,
    onVoiceErrorState: (String) -> Unit,
    onResetVoiceState: () -> Unit,
    onSubmitAudio: (ByteArray?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceRecorderManager(context.applicationContext) }
    var permissionDeniedNote by remember { mutableStateOf(false) }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun beginRecording() {
        recorder.startRecording()
            .onSuccess {
                permissionDeniedNote = false
                onStartRecordingState()
            }
            .onFailure { err ->
                onVoiceErrorState(
                    err.localizedMessage ?: "Не удалось запустить микрофон. Проверьте разрешение или введите текст."
                )
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNote = false
            beginRecording()
        } else {
            permissionDeniedNote = true
            onResetVoiceState()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stopAndDiscard()
        }
    }

    // Automatically begin mic capture if the dialog was opened from another entry point (e.g., Kanban header)
    LaunchedEffect(showDialog) {
        if (showDialog && voiceState is VoiceProcessState.Idle) {
            if (hasMicPermission()) {
                beginRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
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
            if (isRecording) {
                onOpenDialog()
                recorder.stopAndReadBytes().fold(
                    onSuccess = { bytes -> onSubmitAudio(bytes, null) },
                    onFailure = { err ->
                        onVoiceErrorState(err.localizedMessage ?: "Ошибка остановки записи.")
                    }
                )
            } else {
                onResetVoiceState()
                onOpenDialog()
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
            contentDescription = "Голосовой ввод задачи в Gemini и Kanban",
            modifier = Modifier.size(28.dp)
        )
    }

    if (showDialog) {
        VoiceDispatchDialog(
            voiceState = voiceState,
            recorder = recorder,
            permissionDeniedNote = permissionDeniedNote,
            showOpenKanbanAction = showOpenKanbanAction,
            onStartMic = {
                if (hasMicPermission()) {
                    beginRecording()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onStopAndAnalyzeMic = {
                recorder.stopAndReadBytes().fold(
                    onSuccess = { bytes -> onSubmitAudio(bytes, null) },
                    onFailure = { err ->
                        onVoiceErrorState(err.localizedMessage ?: "Не удалось прочитать аудиозапись.")
                    }
                )
            },
            onSubmitFallbackText = { txt ->
                recorder.stopAndDiscard()
                onSubmitAudio(null, txt)
            },
            onOpenKanban = {
                onResetVoiceState()
                onOpenKanban()
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
    recorder: VoiceRecorderManager,
    permissionDeniedNote: Boolean,
    showOpenKanbanAction: Boolean,
    onStartMic: () -> Unit,
    onStopAndAnalyzeMic: () -> Unit,
    onSubmitFallbackText: (String) -> Unit,
    onOpenKanban: () -> Unit,
    onDismiss: () -> Unit
) {
    var fallbackText by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var currentAmp by remember { mutableFloatStateOf(0.1f) }
    val waveBars = remember { mutableStateListOf(0.15f, 0.25f, 0.4f, 0.2f, 0.35f, 0.5f, 0.3f, 0.45f, 0.2f, 0.35f, 0.25f, 0.15f) }

    LaunchedEffect(voiceState) {
        if (voiceState is VoiceProcessState.Recording) {
            elapsedSeconds = 0
            var tickCount = 0
            while (true) {
                val amp = recorder.getAmplitudeNormalized()
                currentAmp = amp
                waveBars.removeAt(0)
                waveBars.add(amp)
                delay(100L)
                tickCount++
                if (tickCount % 10 == 0) {
                    elapsedSeconds++
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MossPaper,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(color = if (voiceState is VoiceProcessState.Recording) MossCoral else MossLime, shape = CircleShape) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = if (voiceState is VoiceProcessState.Recording) MossPaper else MossInk,
                        modifier = Modifier.padding(6.dp).size(18.dp)
                    )
                }
                Text(
                    text = "Голосовая задача → Gemini → Kanban",
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
                        val mins = elapsedSeconds / 60
                        val secs = elapsedSeconds % 60
                        val timerLabel = "%02d:%02d".format(mins, secs)

                        Surface(
                            color = MossCoral.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MossCoral)
                                    )
                                    Text(
                                        text = "ЗАПИСЬ $timerLabel",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MossCoral
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    waveBars.forEach { barLevel ->
                                        val hDp = (6 + (barLevel * 24)).dp
                                        Box(
                                            modifier = Modifier
                                                .width(5.dp)
                                                .height(hDp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (currentAmp > 0.35f) MossCoral else MossInk)
                                        )
                                    }
                                }

                                Text(
                                    text = "Продиктуйте задачу, исполнителя, срок и от какой задачи она зависит. Затем нажмите «Разобрать ИИ».",
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
                                text = "Gemini расшифровывает аудио, извлекает поля задачи и сохраняет запись в Firestore Kanban…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossInk
                            )
                        }
                    }

                    is VoiceProcessState.Done -> {
                        Surface(
                            color = MossLime.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MossLimeDark)
                                    Text(
                                        text = "Создано в Kanban и Календаре!",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MossInk
                                    )
                                }
                                Text(
                                    text = "Транскрипция: «${voiceState.summary}»",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MossInk
                                )
                                if (voiceState.createdTasks.isNotEmpty()) {
                                    voiceState.createdTasks.forEach { task ->
                                        Surface(
                                            color = MossWhite,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = task.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MossInk
                                                )
                                                Text(
                                                    text = "${task.department} · ${task.assigneeName} · ${task.dueDateIso} ${task.dueTimeText}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MossInk2
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    voiceState.addedTitles.forEach { t ->
                                        Text("• $t", style = MaterialTheme.typography.labelMedium, color = MossInk)
                                    }
                                }
                            }
                        }
                    }

                    is VoiceProcessState.Error -> {
                        Surface(
                            color = MossCoral.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = voiceState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossCoral,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    VoiceProcessState.Idle -> {
                        if (permissionDeniedNote) {
                            Text(
                                text = "Доступ к микрофону отклонён. Разрешите запись или введите транскрипцию текстом ниже — Gemini создаст задачу в Firestore Kanban.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossMuted
                            )
                        } else {
                            Text(
                                text = "Нажмите «Записать голос» или введите текстовую расшифровку для разбора через Gemini API.",
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
                        label = { Text("Или введите текст / транскрипцию") },
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
                        Text("Разобрать ИИ → Kanban")
                    }
                }

                is VoiceProcessState.Analyzing -> {}

                is VoiceProcessState.Done -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showOpenKanbanAction) {
                            OutlinedButton(
                                onClick = onOpenKanban,
                                modifier = Modifier.testTag("voice_open_kanban_button")
                            ) {
                                Text("В Kanban", color = MossInk)
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime)
                        ) {
                            Text("Готово")
                        }
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
                                Text("Создать через ИИ")
                            }
                        } else {
                            Button(
                                onClick = onStartMic,
                                colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime),
                                modifier = Modifier.testTag("voice_start_record_button")
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
