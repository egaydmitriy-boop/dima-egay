package com.example.data

import android.util.Log
import com.example.model.Departments
import com.example.model.KanbanTask
import com.example.model.TeamChatMessage
import com.example.model.TeamMember
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "GeminiAiRepository"

data class ParsedVoiceTask(
    val title: String,
    val description: String,
    val department: String,
    val assigneeName: String,
    val dueDateIso: String,
    val dueTimeText: String,
    val estimatedHours: Long,
    val priority: String,
    val dependsOnTaskTitle: String
)

data class VoiceParseResult(
    val transcriptSummary: String,
    val tasks: List<ParsedVoiceTask>
)

data class YoutubeVideoInfo(
    val url: String,
    val title: String,
    val authorName: String
)

class GeminiAiRepository {
    private val httpClient by lazy { OkHttpClient() }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Ты — единый ИИ-архитектор и ментор небольшой инженерной команды MossNode AI.
                    Главная миссия команды: построить мощный локальный ИИ-сервер (GPU-кластер, vLLM/Ollama/TensorRT, OpenAI-совместимый API, биллинг и безопасность), который будет приносить деньги и измеримую пользу инвестору.
                    Отвечай на русском языке чётко, структурно и практически: железо, сети, VRAM, модели, монетизация, распределение задач и снятие блокеров.
                    Если команда присылает ссылку на YouTube-видео, разбери её по теме, архитектуре и применимости именно к нашему локальному ИИ-серверу и ROI инвестора, и предложи конкретные шаги для доски задач.
                    """.trimIndent()
                )
            }
        )
    }

    private val voiceTaskSchema = Schema.obj(
        properties = mapOf(
            "transcriptSummary" to Schema.string(description = "Краткая расшифровка голосового сообщения на русском языке"),
            "tasks" to Schema.array(
                description = "Список задач для вставки в календарь и Kanban-доску",
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Короткое название задачи до 90 символов"),
                        "description" to Schema.string(description = "Конкретные детали реализации"),
                        "department" to Schema.enumeration(
                            values = Departments.all,
                            description = "Отдел проекта"
                        ),
                        "assigneeName" to Schema.string(description = "Имя исполнителя из списка команды или пусто"),
                        "dueDateIso" to Schema.string(description = "Дата дедлайна строго в формате YYYY-MM-DD"),
                        "dueTimeText" to Schema.string(description = "Время дедлайна в формате HH:MM"),
                        "estimatedHours" to Schema.integer(description = "Оценка трудозатрат в часах (от 1 до 120)"),
                        "priority" to Schema.enumeration(
                            values = listOf("CRITICAL", "HIGH", "NORMAL"),
                            description = "Приоритет задачи"
                        ),
                        "dependsOnTaskTitle" to Schema.string(description = "Название задачи-блокера из текущего списка, если новая задача в неё упирается, иначе пустая строка")
                    )
                )
            )
        )
    )

    private val voiceParserModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceTaskSchema
            }
        )
    }

    fun extractYoutubeUrl(text: String): String? {
        val regex = Regex("""https?://(www\.)?(youtube\.com/watch\?[^\s]+|youtu\.be/[^\s]+|youtube\.com/shorts/[^\s]+)""")
        return regex.find(text)?.value
    }

    suspend fun fetchYoutubeMetadata(url: String): YoutubeVideoInfo? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                YoutubeVideoInfo(
                    url = url,
                    title = json.optString("title", ""),
                    authorName = json.optString("author_name", "")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube oEmbed lookup failed: ${e.message}")
            null
        }
    }

    fun streamTeamChatReply(
        userPrompt: String,
        recentMessages: List<TeamChatMessage>,
        tasks: List<KanbanTask>,
        members: List<TeamMember>,
        youtubeInfo: YoutubeVideoInfo?
    ): Flow<String> = flow {
        val taskContext = tasks.joinToString("\n") { t ->
            val deps = if (t.blockedByTaskIds.isEmpty()) "нет" else {
                t.blockedByTaskIds.mapNotNull { id -> tasks.find { it.id == id }?.title }.joinToString(", ")
            }
            "- [${t.taskStatus.labelRu}] ${t.title} | Отдел: ${t.department} | Исполнитель: ${t.assigneeName.ifBlank { "Не назначен" }} | Дедлайн: ${t.dueDateIso} ${t.dueTimeText} (${t.estimatedHours}ч) | Зависит от: $deps"
        }
        val rosterContext = members.joinToString(", ") { "${it.name} (${it.specialty})" }
        val historyContext = recentMessages.takeLast(6).joinToString("\n") { m ->
            "${m.senderName} [${m.role}]: ${m.text.take(400)}"
        }
        val ytContext = if (youtubeInfo != null) {
            "\nОбнаружена ссылка YouTube: ${youtubeInfo.url}\nНазвание видео: ${youtubeInfo.title}\nКанал: ${youtubeInfo.authorName}"
        } else {
            val rawUrl = extractYoutubeUrl(userPrompt)
            if (rawUrl != null) "\nОбнаружена ссылка YouTube: $rawUrl" else ""
        }

        val fullPrompt = """
            Текущий состав команды: $rosterContext
            
            Текущая Kanban-доска и дедлайны:
            $taskContext
            
            Недавние сообщения в общем чате команды:
            $historyContext
            $ytContext
            
            Запрос участника команды:
            $userPrompt
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chatModel.generateContentStream(fullPrompt).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Модель вернула пустой ответ.")
        }
    }

    suspend fun parseVoiceToTasks(
        audioBytes: ByteArray?,
        fallbackText: String?,
        members: List<TeamMember>,
        existingTasks: List<KanbanTask>
    ): VoiceParseResult {
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val memberList = members.joinToString(", ") { "${it.name} (${it.specialty})" }
        val existingList = existingTasks.joinToString("; ") { "${it.title} (${it.dueDateIso})" }

        val instruction = """
            Сегодняшняя дата: $todayIso.
            Команда строит локальный ИИ-сервер для получения дохода и пользы для инвестора.
            Участники команды: $memberList.
            Текущие задачи на доске: $existingList.
            
            Расшифруй голосовое сообщение (или текстовую диктовку) участника и извлеки от 1 до 4 конкретных задач для вставки в календарь и Kanban-доску.
            Определи подходящий отдел, исполнителя из команды (если упомянут по имени или по профилю), точную дату YYYY-MM-DD (если сказано "завтра", "в пятницу", "через 2 дня" — вычисли от $todayIso), время, оценку в часах и зависимость (если задача упирается в уже существующую на доске).
        """.trimIndent()

        val promptContent = content {
            if (audioBytes != null && audioBytes.isNotEmpty()) {
                inlineData(audioBytes, "audio/mp4")
            }
            if (!fallbackText.isNullOrBlank()) {
                text("Голосовая диктовка: $fallbackText")
            }
            text(instruction)
        }

        val response = withTimeout(60_000L) {
            voiceParserModel.generateContent(promptContent)
        }
        val rawJson = response.text ?: throw IllegalStateException("Не удалось разобрать голосовое сообщение.")
        val json = JSONObject(rawJson)
        val summary = json.optString("transcriptSummary", "Голосовая заметка обработана")
        val tasksArr = json.optJSONArray("tasks")
        val parsedList = mutableListOf<ParsedVoiceTask>()
        if (tasksArr != null) {
            for (i in 0 until tasksArr.length()) {
                val obj = tasksArr.getJSONObject(i)
                val rawDate = obj.optString("dueDateIso", todayIso).take(10)
                val safeDate = if (Regex("""\d{4}-\d{2}-\d{2}""").matches(rawDate)) rawDate else todayIso
                parsedList.add(
                    ParsedVoiceTask(
                        title = obj.optString("title", "Новая задача").take(120),
                        description = obj.optString("description", "").take(1000),
                        department = obj.optString("department", Departments.all.first()),
                        assigneeName = obj.optString("assigneeName", ""),
                        dueDateIso = safeDate,
                        dueTimeText = obj.optString("dueTimeText", "18:00").take(16),
                        estimatedHours = obj.optLong("estimatedHours", 4L).coerceIn(1L, 500L),
                        priority = obj.optString("priority", "HIGH"),
                        dependsOnTaskTitle = obj.optString("dependsOnTaskTitle", "")
                    )
                )
            }
        }
        return VoiceParseResult(transcriptSummary = summary, tasks = parsedList)
    }
}
