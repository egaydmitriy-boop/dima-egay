package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.Departments
import com.example.model.KanbanTask
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.TeamMember
import com.example.ui.theme.MossCoral
import com.example.ui.theme.MossInk
import com.example.ui.theme.MossLime
import com.example.ui.theme.MossMuted
import com.example.ui.theme.MossPaper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskEditorDialog(
    initialTask: KanbanTask?,
    presetDateIso: String?,
    allTasks: List<KanbanTask>,
    members: List<TeamMember>,
    onDismiss: () -> Unit,
    onSave: (
        existingTaskId: String?,
        title: String,
        description: String,
        department: String,
        status: TaskStatus,
        priority: TaskPriority,
        assignee: TeamMember?,
        dueDateIso: String,
        dueTimeText: String,
        estimatedHours: Long,
        blockedByTaskIds: List<String>
    ) -> Unit,
    onDelete: (String) -> Unit
) {
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var title by remember { mutableStateOf(initialTask?.title.orEmpty()) }
    var description by remember { mutableStateOf(initialTask?.description.orEmpty()) }
    var department by remember { mutableStateOf(initialTask?.department ?: Departments.all.first()) }
    var status by remember { mutableStateOf(initialTask?.taskStatus ?: TaskStatus.TODO) }
    var priority by remember { mutableStateOf(initialTask?.taskPriority ?: TaskPriority.HIGH) }
    var selectedMember by remember {
        mutableStateOf(
            members.firstOrNull { it.id == initialTask?.assigneeId || it.name == initialTask?.assigneeName }
                ?: members.firstOrNull()
        )
    }
    var dueDateIso by remember { mutableStateOf(initialTask?.dueDateIso ?: presetDateIso ?: todayIso) }
    var dueTimeText by remember { mutableStateOf(initialTask?.dueTimeText ?: "18:00") }
    var hoursText by remember { mutableStateOf((initialTask?.estimatedHours ?: 8L).toString()) }
    val selectedBlockers = remember {
        mutableStateListOf<String>().apply {
            addAll(initialTask?.blockedByTaskIds.orEmpty())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MossPaper,
        title = {
            Text(
                text = if (initialTask == null) "Новая задача" else "Редактировать задачу",
                style = MaterialTheme.typography.titleLarge,
                color = MossInk
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название задачи") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Детали / результат для инвестора") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Статус Kanban", style = MaterialTheme.typography.labelSmall, color = MossMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.labelRu, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Отдел", style = MaterialTheme.typography.labelSmall, color = MossMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Departments.all.forEach { dept ->
                        FilterChip(
                            selected = department == dept,
                            onClick = { department = dept },
                            label = { Text(dept, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Ответственный", style = MaterialTheme.typography.labelSmall, color = MossMuted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    members.forEach { m ->
                        FilterChip(
                            selected = selectedMember?.id == m.id,
                            onClick = { selectedMember = m },
                            label = { Text(m.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Приоритет", style = MaterialTheme.typography.labelSmall, color = MossMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.forEach { pr ->
                        FilterChip(
                            selected = priority == pr,
                            onClick = { priority = pr },
                            label = { Text(pr.labelRu, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dueDateIso,
                        onValueChange = { dueDateIso = it },
                        label = { Text("Дата YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = dueTimeText,
                        onValueChange = { dueTimeText = it },
                        label = { Text("Время") },
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                    OutlinedTextField(
                        value = hoursText,
                        onValueChange = { hoursText = it.filter { c -> c.isDigit() } },
                        label = { Text("Часы") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                val candidateBlockers = allTasks.filter { it.id != initialTask?.id }
                if (candidateBlockers.isNotEmpty()) {
                    Text(
                        text = "Зависит от задач (в кого упирается):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MossMuted
                    )
                    candidateBlockers.forEach { candidate ->
                        val checked = candidate.id in selectedBlockers
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (checked) MossLime.copy(alpha = 0.25f) else MossPaper,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (checked) selectedBlockers.remove(candidate.id)
                                    else selectedBlockers.add(candidate.id)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChk ->
                                        if (isChk) selectedBlockers.add(candidate.id)
                                        else selectedBlockers.remove(candidate.id)
                                    }
                                )
                                Column {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MossInk
                                    )
                                    Text(
                                        text = "${candidate.assigneeName} · ${candidate.dueDateIso}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MossMuted
                                    )
                                }
                            }
                        }
                    }
                }

                if (initialTask != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            onDelete(initialTask.id)
                            onDismiss()
                        }
                    ) {
                        Text("Удалить задачу", color = MossCoral)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeDate = if (Regex("""\d{4}-\d{2}-\d{2}""").matches(dueDateIso.trim())) {
                        dueDateIso.trim()
                    } else todayIso
                    onSave(
                        initialTask?.id,
                        title,
                        description,
                        department,
                        status,
                        priority,
                        selectedMember,
                        safeDate,
                        dueTimeText,
                        hoursText.toLongOrNull() ?: 8L,
                        selectedBlockers.toList()
                    )
                    onDismiss()
                },
                enabled = title.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime),
                modifier = Modifier.testTag("save_task_button")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = MossMuted)
            }
        }
    )
}

@Composable
fun AddMemberDialog(
    members: List<TeamMember>,
    onDismiss: () -> Unit,
    onAddMember: (name: String, specialty: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MossPaper,
        title = {
            Text("Команда проекта (${members.size})", style = MaterialTheme.typography.titleLarge, color = MossInk)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                members.forEach { m ->
                    Surface(
                        color = MossLime.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, color = MossInk)
                            Text(m.specialty, style = MaterialTheme.typography.labelSmall, color = MossMuted)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя нового участника") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Роль / зона ответственности") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddMember(name, specialty)
                    onDismiss()
                },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MossInk, contentColor = MossLime)
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = MossMuted)
            }
        }
    )
}
