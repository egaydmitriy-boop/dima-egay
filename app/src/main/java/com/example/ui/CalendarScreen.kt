package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DependencyGraphSummary
import com.example.model.KanbanTask
import com.example.model.TaskStatus
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
import java.util.Calendar
import java.util.Locale

@Composable
fun CalendarScreen(
    tasks: List<KanbanTask>,
    dependencyGraph: DependencyGraphSummary,
    onAddTaskForDate: (String) -> Unit,
    onTaskClick: (KanbanTask) -> Unit,
    onStatusChange: (KanbanTask, TaskStatus) -> Unit
) {
    val initialCal = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(initialCal.get(Calendar.YEAR)) }
    var monthZeroBased by remember { mutableIntStateOf(initialCal.get(Calendar.MONTH)) }
    var selectedDateIso by remember {
        mutableStateOf(
            String.format(
                Locale.US,
                "%04d-%02d-%02d",
                initialCal.get(Calendar.YEAR),
                initialCal.get(Calendar.MONTH) + 1,
                initialCal.get(Calendar.DAY_OF_MONTH)
            )
        )
    }

    val tasksByDate = remember(tasks) { tasks.groupBy { it.dueDateIso } }
    val selectedDayTasks = remember(tasksByDate, selectedDateIso) {
        tasksByDate[selectedDateIso].orEmpty().sortedBy { it.dueTimeText }
    }

    val monthNamesRu = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    val calGrid = remember(year, monthZeroBased) {
        val c = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        // Convert Sunday=1..Saturday=7 -> Monday=0..Sunday=6
        val dow = c.get(Calendar.DAY_OF_WEEK)
        val leadingEmpty = (dow + 5) % 7
        val cells = MutableList<Int?>(leadingEmpty) { null }
        for (d in 1..daysInMonth) cells.add(d)
        while (cells.size % 7 != 0) cells.add(null)
        cells.chunked(7)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MossMist),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Surface(color = MossLime, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "КАЛЕНДАРЬ ДЕДЛАЙНОВ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MossInk,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${monthNamesRu[monthZeroBased]} $year",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MossInk
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (monthZeroBased == 0) {
                                monthZeroBased = 11
                                year -= 1
                            } else {
                                monthZeroBased -= 1
                            }
                        },
                        modifier = Modifier.testTag("calendar_prev_month")
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Предыдущий месяц", tint = MossInk)
                    }
                    IconButton(
                        onClick = {
                            if (monthZeroBased == 11) {
                                monthZeroBased = 0
                                year += 1
                            } else {
                                monthZeroBased += 1
                            }
                        },
                        modifier = Modifier.testTag("calendar_next_month")
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Следующий месяц", tint = MossInk)
                    }
                }
            }
        }

        item {
            Surface(
                color = MossWhite,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MossLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { wd ->
                            Text(
                                text = wd,
                                style = MaterialTheme.typography.labelSmall,
                                color = MossMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    calGrid.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            week.forEach { day ->
                                if (day == null) {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    val iso = String.format(
                                        Locale.US,
                                        "%04d-%02d-%02d",
                                        year,
                                        monthZeroBased + 1,
                                        day
                                    )
                                    val dayTasks = tasksByDate[iso].orEmpty()
                                    val isSelected = iso == selectedDateIso
                                    val hasBlocked = dayTasks.any { it.id in dependencyGraph.blockedTaskIds }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> MossInk
                                                    dayTasks.isNotEmpty() -> MossPaper
                                                    else -> MossWhite
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 0.dp else 1.dp,
                                                color = if (dayTasks.isNotEmpty()) MossLime else MossLine.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedDateIso = iso },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$day",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (dayTasks.isNotEmpty() || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MossLime else MossInk
                                            )
                                            if (dayTasks.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(if (hasBlocked) MossCoral else MossLimeDark)
                                                    )
                                                    if (dayTasks.size > 1) {
                                                        Text(
                                                            text = "${dayTasks.size}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isSelected) MossPaper else MossInk2
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = MossInk, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Сроки на $selectedDateIso (${selectedDayTasks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MossInk
                    )
                }
                Surface(
                    color = MossLime,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddTaskForDate(selectedDateIso) }
                        .testTag("calendar_add_task_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Добавить на дату", tint = MossInk, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Задача", style = MaterialTheme.typography.labelMedium, color = MossInk)
                    }
                }
            }
        }

        if (selectedDayTasks.isEmpty()) {
            item {
                Surface(
                    color = MossPaper,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "На $selectedDateIso дедлайнов нет. Нажмите голосовую кнопку 🎙 внизу, чтобы надиктовать задачу на эту дату.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MossMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(selectedDayTasks, key = { it.id }) { task ->
                val isBlocked = task.id in dependencyGraph.blockedTaskIds
                Surface(
                    color = MossWhite,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isBlocked) MossCoral.copy(alpha = 0.5f) else MossLine
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTaskClick(task) }
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MossMuted, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "${task.dueTimeText} · ${task.estimatedHours}ч · ${task.department}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MossInk2
                                )
                            }
                            Surface(
                                color = if (task.taskStatus == TaskStatus.DONE) MossLime else MossMist,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = task.taskStatus.labelRu,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MossInk,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ответственный: ${task.assigneeName.ifBlank { "Команда" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MossMuted
                            )
                            if (isBlocked) {
                                Surface(color = MossCoralBg, shape = RoundedCornerShape(6.dp)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Link, contentDescription = null, tint = MossCoral, modifier = Modifier.size(13.dp))
                                        Text(
                                            text = "Упирается в блокер",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MossCoral
                                        )
                                    }
                                }
                            } else if (task.taskStatus != TaskStatus.DONE) {
                                Surface(
                                    color = MossInk,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onStatusChange(task, TaskStatus.DONE) }
                                ) {
                                    Text(
                                        text = "Завершить ✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MossLime,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
