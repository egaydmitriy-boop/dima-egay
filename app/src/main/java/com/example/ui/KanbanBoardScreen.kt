package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Departments
import com.example.model.DependencyGraphSummary
import com.example.model.KanbanTask
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.TeamMember
import com.example.ui.theme.MossAmber
import com.example.ui.theme.MossAmberBg
import com.example.ui.theme.MossCoral
import com.example.ui.theme.MossCoralBg
import com.example.ui.theme.MossInk
import com.example.ui.theme.MossInk2
import com.example.ui.theme.MossLime
import com.example.ui.theme.MossLimeDark
import com.example.ui.theme.MossLine
import com.example.ui.theme.MossMist
import com.example.ui.theme.MossMuted
import com.example.ui.theme.MossPaper
import com.example.ui.theme.MossWhite

@Composable
fun KanbanBoardScreen(
    tasks: List<KanbanTask>,
    members: List<TeamMember>,
    dependencyGraph: DependencyGraphSummary,
    selectedDepartment: String?,
    onSelectDepartment: (String?) -> Unit,
    onCreateTask: () -> Unit,
    onEditTask: (KanbanTask) -> Unit,
    onMoveStatus: (KanbanTask, TaskStatus) -> Unit,
    onAddMemberClick: () -> Unit
) {
    var showDependencyGraph by remember { mutableStateOf(false) }

    val filteredTasks = remember(tasks, selectedDepartment) {
        if (selectedDepartment.isNullOrBlank()) tasks
        else tasks.filter { it.department == selectedDepartment }
    }
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MossMist)
    ) {
        // Header + Mode switcher (Kanban Columns vs Visual Dependency Map)
        Surface(color = MossPaper, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Surface(color = MossLime, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "KANBAN И ЗАВИСИМОСТИ · ${dependencyGraph.completionPercent}% ГОТОВО",
                                style = MaterialTheme.typography.labelSmall,
                                color = MossInk,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Дедлайн-доска команды",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MossInk
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = MossWhite,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MossLine),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onAddMemberClick() }
                                .testTag("add_member_button")
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.GroupAdd, contentDescription = "Участники", tint = MossInk)
                            }
                        }

                        Surface(
                            color = MossInk,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCreateTask() }
                                .testTag("create_task_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Новая задача", tint = MossLime, modifier = Modifier.size(18.dp))
                                Text("Задача", style = MaterialTheme.typography.labelMedium, color = MossLime)
                            }
                        }
                    }
                }

                // Mode toggle + Department filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !showDependencyGraph,
                        onClick = { showDependencyGraph = false },
                        label = { Text("Доска (${filteredTasks.size})", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Rounded.ViewColumn, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MossInk,
                            selectedLabelColor = MossLime,
                            selectedLeadingIconColor = MossLime
                        ),
                        modifier = Modifier.testTag("kanban_mode_chip")
                    )

                    FilterChip(
                        selected = showDependencyGraph,
                        onClick = { showDependencyGraph = true },
                        label = {
                            Text(
                                "Кто в кого упирается (${dependencyGraph.links.size})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MossInk,
                            selectedLabelColor = MossLime,
                            selectedLeadingIconColor = MossLime
                        ),
                        modifier = Modifier.testTag("dependency_mode_chip")
                    )

                    FilterChip(
                        selected = selectedDepartment == null,
                        onClick = { onSelectDepartment(null) },
                        label = { Text("Все отделы", style = MaterialTheme.typography.labelSmall) }
                    )

                    Departments.all.forEach { dept ->
                        FilterChip(
                            selected = selectedDepartment == dept,
                            onClick = { onSelectDepartment(if (selectedDepartment == dept) null else dept) },
                            label = { Text(dept, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        if (showDependencyGraph) {
            DependencyMapPanel(
                dependencyGraph = dependencyGraph,
                allTasks = tasks,
                members = members,
                onEditTask = onEditTask
            )
        } else {
            // 4-Column Kanban Board
            LazyRow(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(TaskStatus.entries, key = { it.name }) { status ->
                    val columnTasks = filteredTasks.filter { it.taskStatus == status }
                    KanbanColumn(
                        status = status,
                        tasks = columnTasks,
                        allTasksById = tasksById,
                        blockedTaskIds = dependencyGraph.blockedTaskIds,
                        onEditTask = onEditTask,
                        onMoveStatus = onMoveStatus
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanColumn(
    status: TaskStatus,
    tasks: List<KanbanTask>,
    allTasksById: Map<String, KanbanTask>,
    blockedTaskIds: Set<String>,
    onEditTask: (KanbanTask) -> Unit,
    onMoveStatus: (KanbanTask, TaskStatus) -> Unit
) {
    val headerBadgeColor = when (status) {
        TaskStatus.TODO -> MossMist
        TaskStatus.IN_PROGRESS -> MossAmberBg
        TaskStatus.TESTING -> MossLime.copy(alpha = 0.4f)
        TaskStatus.DONE -> MossLime
    }

    Surface(
        color = MossPaper,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MossLine),
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBadgeColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.labelRu,
                    style = MaterialTheme.typography.titleMedium,
                    color = MossInk,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(color = MossInk, shape = CircleShape) {
                    Text(
                        text = "${tasks.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MossLime,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    KanbanTaskCard(
                        task = task,
                        allTasksById = allTasksById,
                        isBlocked = task.id in blockedTaskIds,
                        onClick = { onEditTask(task) },
                        onMoveStatus = { newSt -> onMoveStatus(task, newSt) }
                    )
                }
                if (tasks.isEmpty()) {
                    item {
                        Text(
                            text = "Пусто — перенесите задачу сюда или создайте голосом",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MossMuted,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanTaskCard(
    task: KanbanTask,
    allTasksById: Map<String, KanbanTask>,
    isBlocked: Boolean,
    onClick: () -> Unit,
    onMoveStatus: (TaskStatus) -> Unit
) {
    val activeBlockers = task.blockedByTaskIds
        .mapNotNull { allTasksById[it] }
        .filter { it.taskStatus != TaskStatus.DONE }
    val blockingOthers = allTasksById.values.filter { task.id in it.blockedByTaskIds && task.taskStatus != TaskStatus.DONE }

    val priorityColor = when (task.taskPriority) {
        TaskPriority.CRITICAL -> MossCoral
        TaskPriority.HIGH -> MossAmber
        TaskPriority.NORMAL -> MossLimeDark
    }

    val statuses = TaskStatus.entries
    val curIdx = statuses.indexOf(task.taskStatus)
    val prevStatus = if (curIdx > 0) statuses[curIdx - 1] else null
    val nextStatus = if (curIdx < statuses.lastIndex) statuses[curIdx + 1] else null

    Surface(
        color = MossWhite,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isBlocked) 1.5.dp else 1.dp,
            color = if (isBlocked) MossCoral.copy(alpha = 0.65f) else MossLine
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("kanban_card_${task.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MossMist,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = task.department,
                        style = MaterialTheme.typography.labelSmall,
                        color = MossInk2,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(priorityColor)
                    )
                    Text(
                        text = task.taskPriority.labelRu,
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor
                    )
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MossInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (activeBlockers.isNotEmpty()) {
                Surface(color = MossCoralBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = MossCoral, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Упирается в (${activeBlockers.sumOf { it.estimatedHours }}ч):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MossCoral
                            )
                        }
                        activeBlockers.take(2).forEach { b ->
                            Text(
                                text = "• ${b.title} (${b.assigneeName.ifBlank { "Команда" }})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (blockingOthers.isNotEmpty()) {
                Surface(color = MossAmberBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "⏳ Ждут эту задачу: ${blockingOthers.size} (${blockingOthers.joinToString { it.assigneeName.ifBlank { "Команда" } }})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MossAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = MossMuted, modifier = Modifier.size(14.dp))
                    Text(
                        text = task.assigneeName.ifBlank { "Не назначен" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MossInk2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MossMuted, modifier = Modifier.size(14.dp))
                    Text(
                        text = "${task.dueDateIso.takeLast(5)} · ${task.estimatedHours}ч",
                        style = MaterialTheme.typography.labelSmall,
                        color = MossMuted
                    )
                }
            }

            // 1-tap status shift arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prevStatus != null) {
                    IconButton(
                        onClick = { onMoveStatus(prevStatus) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад в ${prevStatus.shortRu}",
                            tint = MossMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                Text(
                    text = if (task.sourceType == "VOICE_AI") "🎙 Из голоса" else "Нажмите для ред.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MossMuted
                )

                if (nextStatus != null) {
                    Surface(
                        color = MossInk,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMoveStatus(nextStatus) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = nextStatus.shortRu,
                                style = MaterialTheme.typography.labelSmall,
                                color = MossLime
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Вперёд",
                                tint = MossLime,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

@Composable
private fun DependencyMapPanel(
    dependencyGraph: DependencyGraphSummary,
    allTasks: List<KanbanTask>,
    members: List<TeamMember>,
    onEditTask: (KanbanTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = MossInk,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "КРИТИЧЕСКИЙ ПУТЬ И БЛОКЕРЫ ПО ВРЕМЕНИ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MossLime
                    )
                    if (dependencyGraph.bottleneckMembers.isEmpty()) {
                        Text(
                            text = "Все зависимости между задачами закрыты — путь к запуску свободен!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MossPaper
                        )
                    } else {
                        Text(
                            text = "Узкие места команды (кто задерживает других):",
                            style = MaterialTheme.typography.titleMedium,
                            color = MossWhite
                        )
                        dependencyGraph.bottleneckMembers.forEach { (owner, blockedHours) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• $owner", style = MaterialTheme.typography.bodyMedium, color = MossPaper)
                                Text(
                                    "держит ${blockedHours}ч чужих задач",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MossLime
                                )
                            }
                        }
                    }
                }
            }
        }

        items(dependencyGraph.links) { link ->
            Surface(
                color = MossWhite,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MossLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ЗАДЕРЖКА +${link.remainingBlockerHours}ч · ${link.blocker.dueDateIso} → ${link.blocked.dueDateIso}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MossCoral
                        )
                    }

                    // Blocker Node
                    Surface(
                        color = MossAmberBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEditTask(link.blocker) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "БЛОКИРУЕТ (${link.blocker.taskStatus.labelRu}) · ${link.blocker.assigneeName.ifBlank { "Команда" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MossAmber
                            )
                            Text(
                                text = link.blocker.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MossInk
                            )
                        }
                    }

                    // Visual connector line + arrowhead drawn on Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    ) {
                        val cx = size.width / 2f
                        drawLine(
                            color = MossCoral,
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height - 6f),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                        val arrow = Path().apply {
                            moveTo(cx - 12f, size.height - 14f)
                            lineTo(cx, size.height)
                            lineTo(cx + 12f, size.height - 14f)
                        }
                        drawPath(arrow, color = MossCoral, style = Stroke(width = 5f, cap = StrokeCap.Round))
                    }

                    // Blocked Dependent Node
                    Surface(
                        color = MossCoralBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onEditTask(link.blocked) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "ЖДЁТ РАЗБЛОКИРОВКИ · ${link.blocked.assigneeName.ifBlank { "Команда" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MossCoral
                            )
                            Text(
                                text = link.blocked.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MossInk
                            )
                        }
                    }
                }
            }
        }
    }
}
