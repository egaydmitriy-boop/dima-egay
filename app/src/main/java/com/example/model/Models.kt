package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskStatus(val labelRu: String, val shortRu: String) {
    TODO("К выполнению", "К вып."),
    IN_PROGRESS("В работе", "В работе"),
    TESTING("Тестирование", "Тест"),
    DONE("Готово", "Готово");

    companion object {
        fun fromRaw(raw: String): TaskStatus =
            entries.firstOrNull { it.name == raw } ?: TODO
    }
}

enum class TaskPriority(val labelRu: String) {
    CRITICAL("Критично"),
    HIGH("Высокий"),
    NORMAL("Обычный");

    companion object {
        fun fromRaw(raw: String): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "Участник команды",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Workspace(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "MossNode AI",
    val mission: String = "Построить сервер для локальных ИИ, который приносит деньги и пользу инвестору",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TeamMember(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val createdByUid: String = "",
    val linkedUid: String = "",
    val name: String = "",
    val specialty: String = "",
    val avatarHex: String = "#C6F35C",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class KanbanTask(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val authorUid: String = "",
    val title: String = "",
    val description: String = "",
    val department: String = "GPU & Железо",
    val status: String = TaskStatus.TODO.name,
    val priority: String = TaskPriority.NORMAL.name,
    val assigneeId: String = "",
    val assigneeName: String = "",
    val dueDateIso: String = "2026-11-22",
    val dueTimeText: String = "18:00",
    val estimatedHours: Long = 8L,
    val blockedByTaskIds: List<String> = emptyList(),
    val sourceType: String = "MANUAL",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val taskStatus: TaskStatus get() = TaskStatus.fromRaw(status)
    val taskPriority: TaskPriority get() = TaskPriority.fromRaw(priority)
}

@Keep
@IgnoreExtraProperties
data class TeamChatMessage(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val role: String = "USER",
    val text: String = "",
    val youtubeUrl: String = "",
    val linkedTaskIds: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DependencyLink(
    val blocker: KanbanTask,
    val blocked: KanbanTask,
    val remainingBlockerHours: Long
)

data class DependencyGraphSummary(
    val links: List<DependencyLink>,
    val blockedTaskIds: Set<String>,
    val bottleneckMembers: List<Pair<String, Long>>,
    val completionPercent: Int
)

object Departments {
    val all = listOf(
        "GPU & Железо",
        "Модели & vLLM",
        "API & Биллинг",
        "Инвестор-демо"
    )
}
