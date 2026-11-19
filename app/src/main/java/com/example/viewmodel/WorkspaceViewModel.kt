package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DEFAULT_WORKSPACE_ID
import com.example.data.GeminiAiRepository
import com.example.data.TeamRepository
import com.example.model.Departments
import com.example.model.DependencyGraphSummary
import com.example.model.DependencyLink
import com.example.model.KanbanTask
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.TeamChatMessage
import com.example.model.TeamMember
import com.example.model.Workspace
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "WorkspaceViewModel"

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface VoiceProcessState {
    data object Idle : VoiceProcessState
    data object Recording : VoiceProcessState
    data object Analyzing : VoiceProcessState
    data class Done(val summary: String, val addedTitles: List<String>) : VoiceProcessState
    data class Error(val message: String) : VoiceProcessState
}

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data class Streaming(val partialText: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

class WorkspaceViewModel(
    private val teamRepository: TeamRepository,
    private val aiRepository: GeminiAiRepository,
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String
) : ViewModel() {

    private val _workspaceId = MutableStateFlow(DEFAULT_WORKSPACE_ID)
    private val _bootstrapError = MutableStateFlow<String?>(null)
    val bootstrapError: StateFlow<String?> = _bootstrapError.asStateFlow()

    private val _isBootstrapped = MutableStateFlow(false)
    val isBootstrapped: StateFlow<Boolean> = _isBootstrapped.asStateFlow()

    private val _selectedDepartmentFilter = MutableStateFlow<String?>(null)
    val selectedDepartmentFilter: StateFlow<String?> = _selectedDepartmentFilter.asStateFlow()

    private val _voiceState = MutableStateFlow<VoiceProcessState>(VoiceProcessState.Idle)
    val voiceState: StateFlow<VoiceProcessState> = _voiceState.asStateFlow()

    private val _chatStreamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val chatStreamState: StateFlow<ChatStreamState> = _chatStreamState.asStateFlow()

    private val _actionNotice = MutableStateFlow<String?>(null)
    val actionNotice: StateFlow<String?> = _actionNotice.asStateFlow()

    init {
        viewModelScope.launch {
            val result = teamRepository.ensureUserAndWorkspace(
                uid = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail
            )
            result.fold(
                onSuccess = { wsId ->
                    _workspaceId.value = wsId
                    _isBootstrapped.value = true
                },
                onFailure = { err ->
                    _bootstrapError.value = err.localizedMessage ?: "Не удалось подключиться к команде."
                }
            )
        }
    }

    val workspace: StateFlow<Workspace?> = _isBootstrapped
        .combine(_workspaceId) { ready, wsId -> ready to wsId }
        .let { flow ->
            teamRepository.observeWorkspace(DEFAULT_WORKSPACE_ID, currentUserId)
                .catch { error ->
                    val fsErr = unwrapFirestoreError(error)
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing workspace", fsErr ?: error)
                    emit(null)
                }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)
        }

    val membersState: StateFlow<DataUiState<List<TeamMember>>> =
        teamRepository.observeMembers(DEFAULT_WORKSPACE_ID, currentUserId)
            .map<List<TeamMember>, DataUiState<List<TeamMember>>> { DataUiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                emit(DataUiState.Error((fsErr ?: error).message ?: "Ошибка загрузки команды"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val tasksState: StateFlow<DataUiState<List<KanbanTask>>> =
        teamRepository.observeTasks(DEFAULT_WORKSPACE_ID, currentUserId)
            .map<List<KanbanTask>, DataUiState<List<KanbanTask>>> { DataUiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                emit(DataUiState.Error((fsErr ?: error).message ?: "Ошибка загрузки задач"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val chatMessagesState: StateFlow<DataUiState<List<TeamChatMessage>>> =
        teamRepository.observeChatMessages(DEFAULT_WORKSPACE_ID, currentUserId)
            .map<List<TeamChatMessage>, DataUiState<List<TeamChatMessage>>> { DataUiState.Success(it) }
            .catch { error ->
                val fsErr = unwrapFirestoreError(error)
                if (fsErr == null && error is CancellationException) throw error
                emit(DataUiState.Error((fsErr ?: error).message ?: "Ошибка загрузки чата"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DataUiState.Loading)

    val dependencyGraph: StateFlow<DependencyGraphSummary> = tasksState
        .map { state ->
            val tasks = (state as? DataUiState.Success)?.data ?: emptyList()
            computeDependencySummary(tasks)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            DependencyGraphSummary(emptyList(), emptySet(), emptyList(), 0)
        )

    init {
        viewModelScope.launch {
            workspace.collect { ws ->
                if (ws != null && ws.memberUids.size > 1) {
                    teamRepository.syncMemberUidsAcrossCollections(ws.id, ws.memberUids)
                }
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private fun currentMemberUids(): List<String> {
        val uids = workspace.value?.memberUids.orEmpty()
        return if (currentUserId in uids) uids else (uids + currentUserId).distinct()
    }

    private fun computeDependencySummary(tasks: List<KanbanTask>): DependencyGraphSummary {
        val byId = tasks.associateBy { it.id }
        val links = mutableListOf<DependencyLink>()
        val blockedIds = mutableSetOf<String>()
        val bottleneckMap = mutableMapOf<String, Long>()

        for (task in tasks) {
            for (blockerId in task.blockedByTaskIds) {
                val blocker = byId[blockerId] ?: continue
                if (blocker.taskStatus != TaskStatus.DONE) {
                    blockedIds.add(task.id)
                    links.add(
                        DependencyLink(
                            blocker = blocker,
                            blocked = task,
                            remainingBlockerHours = blocker.estimatedHours
                        )
                    )
                    val owner = blocker.assigneeName.ifBlank { "Не назначен" }
                    bottleneckMap[owner] = (bottleneckMap[owner] ?: 0L) + task.estimatedHours
                }
            }
        }
        val doneCount = tasks.count { it.taskStatus == TaskStatus.DONE }
        val completion = if (tasks.isEmpty()) 0 else ((doneCount * 100f) / tasks.size).toInt()
        val bottlenecks = bottleneckMap.entries.sortedByDescending { it.value }.map { it.key to it.value }
        return DependencyGraphSummary(
            links = links,
            blockedTaskIds = blockedIds,
            bottleneckMembers = bottlenecks,
            completionPercent = completion
        )
    }

    fun buildWebBridgeJson(): String {
        val tasks = (tasksState.value as? DataUiState.Success)?.data ?: emptyList()
        val graph = dependencyGraph.value
        val activeCount = tasks.count { it.taskStatus != TaskStatus.DONE }
        val doneCount = tasks.count { it.taskStatus == TaskStatus.DONE }

        val hotspots = JSONArray()
        for (dept in Departments.all) {
            val deptTasks = tasks.filter { it.department == dept }
            val hours = deptTasks.filter { it.taskStatus != TaskStatus.DONE }.sumOf { it.estimatedHours }
            val blocked = deptTasks.count { it.id in graph.blockedTaskIds }
            val label = if (blocked > 0) "$blocked блокер · ${hours}ч" else "${deptTasks.size} зад. · ${hours}ч"
            hotspots.put(JSONObject().apply {
                put("dept", dept)
                put("summary", label)
            })
        }

        val chains = JSONArray()
        for (link in graph.links.take(4)) {
            chains.put(JSONObject().apply {
                put("fromTitle", link.blocker.title.take(32))
                put("fromOwner", link.blocker.assigneeName.ifBlank { "Команда" })
                put("toTitle", link.blocked.title.take(32))
                put("toOwner", link.blocked.assigneeName.ifBlank { "Команда" })
                put("hours", link.remainingBlockerHours)
            })
        }

        return JSONObject().apply {
            put("totalTasks", tasks.size)
            put("doneTasks", doneCount)
            put("activeTasks", activeCount)
            put("blockerLinks", graph.links.size)
            put("completionPct", graph.completionPercent)
            put("hotspots", hotspots)
            put("chains", chains)
        }.toString()
    }

    fun setDepartmentFilter(dept: String?) {
        _selectedDepartmentFilter.value = dept
    }

    fun clearActionNotice() {
        _actionNotice.value = null
    }

    fun moveTaskStatus(task: KanbanTask, newStatus: TaskStatus) {
        viewModelScope.launch {
            teamRepository.updateTaskStatus(DEFAULT_WORKSPACE_ID, task.id, newStatus)
                .onFailure { _actionNotice.value = it.localizedMessage ?: "Не удалось обновить статус" }
        }
    }

    fun saveTask(
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
    ) {
        viewModelScope.launch {
            val res = if (existingTaskId == null) {
                teamRepository.createTask(
                    workspaceId = DEFAULT_WORKSPACE_ID,
                    memberUids = currentMemberUids(),
                    title = title,
                    description = description,
                    department = department,
                    status = status,
                    priority = priority,
                    assigneeId = assignee?.id.orEmpty(),
                    assigneeName = assignee?.name.orEmpty(),
                    dueDateIso = dueDateIso,
                    dueTimeText = dueTimeText,
                    estimatedHours = estimatedHours,
                    blockedByTaskIds = blockedByTaskIds,
                    sourceType = "MANUAL"
                ).map { Unit }
            } else {
                teamRepository.updateTask(
                    workspaceId = DEFAULT_WORKSPACE_ID,
                    taskId = existingTaskId,
                    title = title,
                    description = description,
                    department = department,
                    status = status,
                    priority = priority,
                    assigneeId = assignee?.id.orEmpty(),
                    assigneeName = assignee?.name.orEmpty(),
                    dueDateIso = dueDateIso,
                    dueTimeText = dueTimeText,
                    estimatedHours = estimatedHours,
                    blockedByTaskIds = blockedByTaskIds
                )
            }
            res.fold(
                onSuccess = { _actionNotice.value = "Задача сохранена в доску и календарь" },
                onFailure = { _actionNotice.value = it.localizedMessage ?: "Ошибка сохранения задачи" }
            )
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            teamRepository.deleteTask(DEFAULT_WORKSPACE_ID, taskId)
                .onFailure { _actionNotice.value = it.localizedMessage ?: "Ошибка удаления" }
        }
    }

    fun addMember(name: String, specialty: String) {
        viewModelScope.launch {
            teamRepository.addTeamMember(
                workspaceId = DEFAULT_WORKSPACE_ID,
                memberUids = currentMemberUids(),
                name = name,
                specialty = specialty
            ).fold(
                onSuccess = { _actionNotice.value = "Участник $name добавлен" },
                onFailure = { _actionNotice.value = it.localizedMessage ?: "Не удалось добавить участника" }
            )
        }
    }

    fun sendTeamChatMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            val memberUids = currentMemberUids()
            val ytUrl = aiRepository.extractYoutubeUrl(clean).orEmpty()

            teamRepository.postChatMessage(
                workspaceId = DEFAULT_WORKSPACE_ID,
                memberUids = memberUids,
                senderName = currentUserName,
                role = "USER",
                text = clean,
                youtubeUrl = ytUrl
            ).onFailure {
                _chatStreamState.value = ChatStreamState.Error(it.localizedMessage ?: "Ошибка отправки")
                return@launch
            }

            _chatStreamState.value = ChatStreamState.Streaming("")
            try {
                val ytInfo = if (ytUrl.isNotEmpty()) aiRepository.fetchYoutubeMetadata(ytUrl) else null
                val recent = (chatMessagesState.value as? DataUiState.Success)?.data ?: emptyList()
                val tasks = (tasksState.value as? DataUiState.Success)?.data ?: emptyList()
                val members = (membersState.value as? DataUiState.Success)?.data ?: emptyList()

                var finalReply = ""
                aiRepository.streamTeamChatReply(
                    userPrompt = clean,
                    recentMessages = recent,
                    tasks = tasks,
                    members = members,
                    youtubeInfo = ytInfo
                ).collect { accumulated ->
                    finalReply = accumulated
                    _chatStreamState.value = ChatStreamState.Streaming(accumulated)
                }

                teamRepository.postChatMessage(
                    workspaceId = DEFAULT_WORKSPACE_ID,
                    memberUids = memberUids,
                    senderName = "MossNode AI",
                    role = "AI",
                    text = finalReply,
                    youtubeUrl = ytUrl
                )
                _chatStreamState.value = ChatStreamState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat error", e)
                _chatStreamState.value = ChatStreamState.Error(
                    e.localizedMessage ?: "Не удалось получить ответ от Gemini."
                )
            }
        }
    }

    fun setVoiceRecordingState() {
        _voiceState.value = VoiceProcessState.Recording
    }

    fun resetVoiceState() {
        _voiceState.value = VoiceProcessState.Idle
    }

    fun processVoicePayload(audioBytes: ByteArray?, fallbackText: String?) {
        viewModelScope.launch {
            _voiceState.value = VoiceProcessState.Analyzing
            try {
                val members = (membersState.value as? DataUiState.Success)?.data ?: emptyList()
                val existingTasks = (tasksState.value as? DataUiState.Success)?.data ?: emptyList()
                val parsed = aiRepository.parseVoiceToTasks(
                    audioBytes = audioBytes,
                    fallbackText = fallbackText,
                    members = members,
                    existingTasks = existingTasks
                )
                val memberUids = currentMemberUids()
                val createdIds = mutableListOf<String>()
                val createdTitles = mutableListOf<String>()

                for (item in parsed.tasks) {
                    val matchedMember = members.firstOrNull { m ->
                        item.assigneeName.isNotBlank() &&
                            (m.name.contains(item.assigneeName, ignoreCase = true) ||
                                item.assigneeName.contains(m.name.substringBefore(" "), ignoreCase = true))
                    }
                    val matchedBlocker = existingTasks.firstOrNull { t ->
                        item.dependsOnTaskTitle.isNotBlank() &&
                            t.title.contains(item.dependsOnTaskTitle.take(18), ignoreCase = true)
                    }
                    val priority = TaskPriority.fromRaw(item.priority)
                    val res = teamRepository.createTask(
                        workspaceId = DEFAULT_WORKSPACE_ID,
                        memberUids = memberUids,
                        title = item.title,
                        description = item.description,
                        department = item.department,
                        status = TaskStatus.TODO,
                        priority = priority,
                        assigneeId = matchedMember?.id.orEmpty(),
                        assigneeName = matchedMember?.name ?: item.assigneeName,
                        dueDateIso = item.dueDateIso,
                        dueTimeText = item.dueTimeText,
                        estimatedHours = item.estimatedHours,
                        blockedByTaskIds = listOfNotNull(matchedBlocker?.id),
                        sourceType = "VOICE_AI"
                    )
                    res.onSuccess { id ->
                        createdIds.add(id)
                        createdTitles.add("${item.title} (${item.dueDateIso})")
                    }
                }

                if (createdIds.isNotEmpty()) {
                    teamRepository.postChatMessage(
                        workspaceId = DEFAULT_WORKSPACE_ID,
                        memberUids = memberUids,
                        senderName = currentUserName,
                        role = "VOICE_LOG",
                        text = "Голосовой разбор (${parsed.transcriptSummary}) -> добавлено в календарь и Kanban:\n" +
                            createdTitles.joinToString("\n") { "• $it" },
                        linkedTaskIds = createdIds
                    )
                }

                _voiceState.value = VoiceProcessState.Done(
                    summary = parsed.transcriptSummary,
                    addedTitles = createdTitles
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice AI processing error", e)
                _voiceState.value = VoiceProcessState.Error(
                    e.localizedMessage ?: "Ошибка голосового разбора Gemini"
                )
            }
        }
    }
}
