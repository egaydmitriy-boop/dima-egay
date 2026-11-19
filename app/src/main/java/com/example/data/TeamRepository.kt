package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.KanbanTask
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.model.TeamChatMessage
import com.example.model.TeamMember
import com.example.model.UserProfile
import com.example.model.Workspace
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "TeamRepository"
const val DEFAULT_WORKSPACE_ID = "mossnode-core"

class TeamRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Необходим вход через Google.")
    }

    suspend fun ensureUserAndWorkspace(
        uid: String,
        displayName: String,
        email: String
    ): Result<String> {
        return try {
            val activeUid = requireUserId()
            check(activeUid == uid) { "UID mismatch" }

            val safeName = displayName.ifBlank { "Инженер команды" }.take(60)
            val safeEmail = email.take(120)

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                userRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "email" to safeEmail,
                        "role" to "AI Server Team",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val wsRef = db.collection("workspaces").document(DEFAULT_WORKSPACE_ID)
            val myWorkspaces = db.collection("workspaces")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()

            if (myWorkspaces.isEmpty) {
                try {
                    wsRef.update(
                        mapOf(
                            "memberUids" to FieldValue.arrayUnion(uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    ensureMemberRosterSlot(DEFAULT_WORKSPACE_ID, uid, safeName, "Участник команды")
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                        wsRef.set(
                            mapOf(
                                "ownerId" to uid,
                                "name" to "MossNode AI Server",
                                "mission" to "Построить локальный ИИ-сервер, который приносит деньги и пользу инвестору",
                                "memberUids" to listOf(uid),
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        seedInitialTeamPlan(DEFAULT_WORKSPACE_ID, uid, safeName)
                    } else {
                        throw e
                    }
                }
            }
            Result.success(DEFAULT_WORKSPACE_ID)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "workspaces/$DEFAULT_WORKSPACE_ID")
            Result.failure(e)
        }
    }

    private suspend fun ensureMemberRosterSlot(
        workspaceId: String,
        uid: String,
        name: String,
        specialty: String
    ) {
        val wsSnap = db.collection("workspaces").document(workspaceId).get().await()
        val memberUids = (wsSnap.get("memberUids") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: listOf(uid)
        val memberId = "user_${uid.take(24)}"
        val memberRef = db.collection("workspaces").document(workspaceId)
            .collection("members").document(memberId)
        val existing = memberRef.get().await()
        if (!existing.exists()) {
            memberRef.set(
                mapOf(
                    "workspaceId" to workspaceId,
                    "createdByUid" to uid,
                    "linkedUid" to uid,
                    "name" to name.take(50),
                    "specialty" to specialty.take(80),
                    "avatarHex" to "#C6F35C",
                    "memberUids" to memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private suspend fun seedInitialTeamPlan(
        workspaceId: String,
        ownerUid: String,
        ownerName: String
    ) {
        val membersCol = db.collection("workspaces").document(workspaceId).collection("members")
        val tasksCol = db.collection("workspaces").document(workspaceId).collection("tasks")
        val chatCol = db.collection("workspaces").document(workspaceId).collection("chat_messages")
        val memberUids = listOf(ownerUid)

        val mem1Id = "mem_infra"
        val mem2Id = "mem_mlops"
        val mem3Id = "mem_backend"
        val mem4Id = "mem_investor"

        val batch = db.batch()

        batch.set(
            membersCol.document(mem1Id),
            mapOf(
                "workspaceId" to workspaceId,
                "createdByUid" to ownerUid,
                "linkedUid" to ownerUid,
                "name" to ownerName.take(50),
                "specialty" to "GPU / Железо / Linux",
                "avatarHex" to "#C6F35C",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            membersCol.document(mem2Id),
            mapOf(
                "workspaceId" to workspaceId,
                "createdByUid" to ownerUid,
                "linkedUid" to "",
                "name" to "Артём (MLOps)",
                "specialty" to "vLLM / Квантизация / CUDA",
                "avatarHex" to "#A9DD3A",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            membersCol.document(mem3Id),
            mapOf(
                "workspaceId" to workspaceId,
                "createdByUid" to ownerUid,
                "linkedUid" to "",
                "name" to "Катя (Backend)",
                "specialty" to "FastAPI / Биллинг / Токены",
                "avatarHex" to "#68C79B",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            membersCol.document(mem4Id),
            mapOf(
                "workspaceId" to workspaceId,
                "createdByUid" to ownerUid,
                "linkedUid" to "",
                "name" to "Макс (Product)",
                "specialty" to "ROI-витрина / Инвестор",
                "avatarHex" to "#E8A317",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val t1Id = "task_1_rack"
        val t2Id = "task_2_gpu"
        val t3Id = "task_3_vllm"
        val t4Id = "task_4_billing"
        val t5Id = "task_5_demo"

        batch.set(
            tasksCol.document(t1Id),
            mapOf(
                "workspaceId" to workspaceId,
                "authorUid" to ownerUid,
                "title" to "Собрать серверное шасси и питание 2000W",
                "description" to "Подготовить стойку, охлаждение и Ubuntu Server 24.04 LTS с Docker.",
                "department" to "GPU & Железо",
                "status" to TaskStatus.DONE.name,
                "priority" to TaskPriority.HIGH.name,
                "assigneeId" to mem1Id,
                "assigneeName" to ownerName.take(60),
                "dueDateIso" to "2026-11-18",
                "dueTimeText" to "18:00",
                "estimatedHours" to 10L,
                "blockedByTaskIds" to emptyList<String>(),
                "sourceType" to "SEED",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            tasksCol.document(t2Id),
            mapOf(
                "workspaceId" to workspaceId,
                "authorUid" to ownerUid,
                "title" to "Прошить драйверы CUDA и прогнать 4× RTX 4090",
                "description" to "Настроить NVLink/PCIe пропускную способность и стресс-тест памяти 96GB VRAM.",
                "department" to "GPU & Железо",
                "status" to TaskStatus.IN_PROGRESS.name,
                "priority" to TaskPriority.CRITICAL.name,
                "assigneeId" to mem1Id,
                "assigneeName" to ownerName.take(60),
                "dueDateIso" to "2026-11-21",
                "dueTimeText" to "20:00",
                "estimatedHours" to 16L,
                "blockedByTaskIds" to listOf(t1Id),
                "sourceType" to "SEED",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            tasksCol.document(t3Id),
            mapOf(
                "workspaceId" to workspaceId,
                "authorUid" to ownerUid,
                "title" to "Поднять кластер vLLM (Qwen 72B AWQ + Whisper)",
                "description" to "Замер токенов/сек и настройка OpenAI-совместимого локального эндпоинта.",
                "department" to "Модели & vLLM",
                "status" to TaskStatus.TODO.name,
                "priority" to TaskPriority.CRITICAL.name,
                "assigneeId" to mem2Id,
                "assigneeName" to "Артём (MLOps)",
                "dueDateIso" to "2026-11-23",
                "dueTimeText" to "19:00",
                "estimatedHours" to 12L,
                "blockedByTaskIds" to listOf(t2Id),
                "sourceType" to "SEED",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            tasksCol.document(t4Id),
            mapOf(
                "workspaceId" to workspaceId,
                "authorUid" to ownerUid,
                "title" to "Сделать API-шлюз с учётом токенов и оплатой",
                "description" to "Лимиты клиентов, ключи доступа и подсчёт маржи на 1M токенов.",
                "department" to "API & Биллинг",
                "status" to TaskStatus.TESTING.name,
                "priority" to TaskPriority.HIGH.name,
                "assigneeId" to mem3Id,
                "assigneeName" to "Катя (Backend)",
                "dueDateIso" to "2026-11-25",
                "dueTimeText" to "17:00",
                "estimatedHours" to 14L,
                "blockedByTaskIds" to listOf(t3Id),
                "sourceType" to "SEED",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            tasksCol.document(t5Id),
            mapOf(
                "workspaceId" to workspaceId,
                "authorUid" to ownerUid,
                "title" to "Показать инвестору рабочий кластер и ROI-модель",
                "description" to "Живой запрос в локальную модель, экономика vs облако и план масштабирования.",
                "department" to "Инвестор-демо",
                "status" to TaskStatus.TODO.name,
                "priority" to TaskPriority.CRITICAL.name,
                "assigneeId" to mem4Id,
                "assigneeName" to "Макс (Product)",
                "dueDateIso" to "2026-11-27",
                "dueTimeText" to "15:00",
                "estimatedHours" to 8L,
                "blockedByTaskIds" to listOf(t3Id, t4Id),
                "sourceType" to "SEED",
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        batch.set(
            chatCol.document("msg_welcome"),
            mapOf(
                "workspaceId" to workspaceId,
                "senderUid" to ownerUid,
                "senderName" to "MossNode AI",
                "role" to "AI",
                "text" to "Привет, команда! Я ваш общий ИИ-ментор по запуску локального ИИ-сервера для инвестора. Я вижу всю Kanban-доску, дедлайны и цепочку зависимостей. Задавайте вопросы по железу, vLLM, монетизации или кидайте ссылки на YouTube-ролики для разбора.",
                "youtubeUrl" to "",
                "linkedTaskIds" to emptyList<String>(),
                "memberUids" to memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        batch.commit().await()
    }

    fun observeWorkspace(workspaceId: String, uid: String): Flow<Workspace?> =
        db.collection("workspaces")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Workspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .firstOrNull { it.id == workspaceId }
                    ?: snap.toObjects(Workspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE).firstOrNull()
            }

    fun observeMembers(workspaceId: String, uid: String): Flow<List<TeamMember>> =
        db.collection("workspaces").document(workspaceId).collection("members")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(TeamMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.seconds ?: 0L }
            }

    fun observeTasks(workspaceId: String, uid: String): Flow<List<KanbanTask>> =
        db.collection("workspaces").document(workspaceId).collection("tasks")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(KanbanTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<KanbanTask> { it.dueDateIso }.thenBy { it.dueTimeText })
            }

    fun observeChatMessages(workspaceId: String, uid: String): Flow<List<TeamChatMessage>> =
        db.collection("workspaces").document(workspaceId).collection("chat_messages")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(TeamChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }

    suspend fun syncMemberUidsAcrossCollections(workspaceId: String, memberUids: List<String>) {
        try {
            val uid = requireUserId()
            if (uid !in memberUids) return
            val batch = db.batch()
            var count = 0

            val tasks = db.collection("workspaces").document(workspaceId).collection("tasks")
                .whereArrayContains("memberUids", uid).get().await()
            for (doc in tasks.documents) {
                val cur = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                if (cur.toSet() != memberUids.toSet()) {
                    batch.update(doc.reference, mapOf("memberUids" to memberUids, "updatedAt" to FieldValue.serverTimestamp()))
                    count++
                }
            }
            val members = db.collection("workspaces").document(workspaceId).collection("members")
                .whereArrayContains("memberUids", uid).get().await()
            for (doc in members.documents) {
                val cur = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                if (cur.toSet() != memberUids.toSet()) {
                    batch.update(doc.reference, mapOf("memberUids" to memberUids, "updatedAt" to FieldValue.serverTimestamp()))
                    count++
                }
            }
            val msgs = db.collection("workspaces").document(workspaceId).collection("chat_messages")
                .whereArrayContains("memberUids", uid).get().await()
            for (doc in msgs.documents) {
                val cur = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                if (cur.toSet() != memberUids.toSet()) {
                    batch.update(doc.reference, mapOf("memberUids" to memberUids, "updatedAt" to FieldValue.serverTimestamp()))
                    count++
                }
            }
            if (count > 0) batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "syncMemberUidsAcrossCollections skipped: ${e.message}")
        }
    }

    suspend fun createTask(
        workspaceId: String,
        memberUids: List<String>,
        title: String,
        description: String,
        department: String,
        status: TaskStatus,
        priority: TaskPriority,
        assigneeId: String,
        assigneeName: String,
        dueDateIso: String,
        dueTimeText: String,
        estimatedHours: Long,
        blockedByTaskIds: List<String>,
        sourceType: String = "MANUAL"
    ): Result<String> {
        val path = "workspaces/$workspaceId/tasks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Название задачи не может быть пустым." }
            val cleanDate = dueDateIso.trim().take(10).padEnd(10, '0')
            val cleanHours = estimatedHours.coerceIn(1L, 500L)
            val safeMembers = if (uid in memberUids) memberUids else (memberUids + uid).distinct().take(20)

            val docRef = db.collection("workspaces").document(workspaceId).collection("tasks").document()
            val payload = mapOf(
                "workspaceId" to workspaceId,
                "authorUid" to uid,
                "title" to cleanTitle,
                "description" to description.trim().take(1000),
                "department" to department.trim().ifBlank { "GPU & Железо" }.take(40),
                "status" to status.name,
                "priority" to priority.name,
                "assigneeId" to assigneeId.take(64),
                "assigneeName" to assigneeName.trim().take(60),
                "dueDateIso" to cleanDate,
                "dueTimeText" to dueTimeText.trim().ifBlank { "18:00" }.take(16),
                "estimatedHours" to cleanHours,
                "blockedByTaskIds" to blockedByTaskIds.distinct().take(10),
                "sourceType" to sourceType,
                "memberUids" to safeMembers,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        workspaceId: String,
        taskId: String,
        title: String,
        description: String,
        department: String,
        status: TaskStatus,
        priority: TaskPriority,
        assigneeId: String,
        assigneeName: String,
        dueDateIso: String,
        dueTimeText: String,
        estimatedHours: Long,
        blockedByTaskIds: List<String>
    ): Result<Unit> {
        val path = "workspaces/$workspaceId/tasks/$taskId"
        return try {
            requireUserId()
            require(taskId.isNotBlank()) { "Task ID is required" }
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Название задачи обязательно" }
            val docRef = db.collection("workspaces").document(workspaceId).collection("tasks").document(taskId)
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "description" to description.trim().take(1000),
                    "department" to department.trim().ifBlank { "GPU & Железо" }.take(40),
                    "status" to status.name,
                    "priority" to priority.name,
                    "assigneeId" to assigneeId.take(64),
                    "assigneeName" to assigneeName.trim().take(60),
                    "dueDateIso" to dueDateIso.trim().take(10),
                    "dueTimeText" to dueTimeText.trim().ifBlank { "18:00" }.take(16),
                    "estimatedHours" to estimatedHours.coerceIn(1L, 500L),
                    "blockedByTaskIds" to blockedByTaskIds.filter { it != taskId }.distinct().take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTaskStatus(
        workspaceId: String,
        taskId: String,
        newStatus: TaskStatus
    ): Result<Unit> {
        val path = "workspaces/$workspaceId/tasks/$taskId"
        return try {
            requireUserId()
            db.collection("workspaces").document(workspaceId).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "status" to newStatus.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(workspaceId: String, taskId: String): Result<Unit> {
        val path = "workspaces/$workspaceId/tasks/$taskId"
        return try {
            requireUserId()
            db.collection("workspaces").document(workspaceId).collection("tasks").document(taskId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addTeamMember(
        workspaceId: String,
        memberUids: List<String>,
        name: String,
        specialty: String,
        avatarHex: String = "#C6F35C"
    ): Result<String> {
        val path = "workspaces/$workspaceId/members"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(50)
            require(cleanName.isNotEmpty()) { "Имя участника обязательно" }
            val safeMembers = if (uid in memberUids) memberUids else (memberUids + uid).distinct().take(20)
            val docRef = db.collection("workspaces").document(workspaceId).collection("members").document()
            docRef.set(
                mapOf(
                    "workspaceId" to workspaceId,
                    "createdByUid" to uid,
                    "linkedUid" to "",
                    "name" to cleanName,
                    "specialty" to specialty.trim().ifBlank { "Инженер" }.take(80),
                    "avatarHex" to avatarHex.take(12),
                    "memberUids" to safeMembers,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun postChatMessage(
        workspaceId: String,
        memberUids: List<String>,
        senderName: String,
        role: String,
        text: String,
        youtubeUrl: String = "",
        linkedTaskIds: List<String> = emptyList()
    ): Result<String> {
        val path = "workspaces/$workspaceId/chat_messages"
        return try {
            val uid = requireUserId()
            val cleanText = text.trim().take(8000)
            require(cleanText.isNotEmpty()) { "Сообщение не может быть пустым" }
            val safeMembers = if (uid in memberUids) memberUids else (memberUids + uid).distinct().take(20)
            val docRef = db.collection("workspaces").document(workspaceId).collection("chat_messages").document()
            docRef.set(
                mapOf(
                    "workspaceId" to workspaceId,
                    "senderUid" to uid,
                    "senderName" to senderName.trim().ifBlank { "Команда" }.take(60),
                    "role" to role,
                    "text" to cleanText,
                    "youtubeUrl" to youtubeUrl.trim().take(300),
                    "linkedTaskIds" to linkedTaskIds.take(10),
                    "memberUids" to safeMembers,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
