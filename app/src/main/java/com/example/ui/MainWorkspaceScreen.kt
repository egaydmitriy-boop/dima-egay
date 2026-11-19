package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.GeminiAiRepository
import com.example.data.TeamRepository
import com.example.model.KanbanTask
import com.example.ui.theme.MossInk
import com.example.ui.theme.MossLime
import com.example.ui.theme.MossMist
import com.example.ui.theme.MossPaper
import com.example.viewmodel.DataUiState
import com.example.viewmodel.WorkspaceViewModel
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_STORY = "story"
const val ROUTE_KANBAN = "kanban"
const val ROUTE_CALENDAR = "calendar"
const val ROUTE_CHAT = "chat"

private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val appTabs = listOf(
    NavTab(ROUTE_STORY, "Нода", Icons.Rounded.Explore),
    NavTab(ROUTE_KANBAN, "Kanban", Icons.Rounded.ViewKanban),
    NavTab(ROUTE_CALENDAR, "Календарь", Icons.Rounded.CalendarMonth),
    NavTab(ROUTE_CHAT, "ИИ-Чат", Icons.Rounded.AutoAwesome)
)

@Composable
fun MainWorkspaceScreen(
    currentUserId: String,
    currentUserName: String,
    currentUserEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: WorkspaceViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                WorkspaceViewModel(
                    teamRepository = TeamRepository(db),
                    aiRepository = GeminiAiRepository(),
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    currentUserEmail = currentUserEmail
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STORY

    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val membersState by viewModel.membersState.collectAsStateWithLifecycle()
    val chatState by viewModel.chatMessagesState.collectAsStateWithLifecycle()
    val dependencyGraph by viewModel.dependencyGraph.collectAsStateWithLifecycle()
    val selectedDept by viewModel.selectedDepartmentFilter.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val chatStreamState by viewModel.chatStreamState.collectAsStateWithLifecycle()
    val actionNotice by viewModel.actionNotice.collectAsStateWithLifecycle()
    val bootstrapError by viewModel.bootstrapError.collectAsStateWithLifecycle()

    val tasks = (tasksState as? DataUiState.Success)?.data ?: emptyList()
    val members = (membersState as? DataUiState.Success)?.data ?: emptyList()
    val chatMessages = (chatState as? DataUiState.Success)?.data ?: emptyList()

    val liveBridgeJson = remember(tasks, dependencyGraph) {
        viewModel.buildWebBridgeJson()
    }

    var showVoiceDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<KanbanTask?>(null) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var presetDateForTask by remember { mutableStateOf<String?>(null) }
    var showMemberDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionNotice, bootstrapError) {
        val msg = actionNotice ?: bootstrapError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionNotice()
        }
    }

    fun navigateToTab(route: String, deptFilter: String = "") {
        if (deptFilter.isNotBlank()) {
            viewModel.setDepartmentFilter(deptFilter)
        }
        val target = when (route) {
            ROUTE_KANBAN, ROUTE_CALENDAR, ROUTE_CHAT, ROUTE_STORY -> route
            else -> ROUTE_KANBAN
        }
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = MossMist,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar(
                        containerColor = MossPaper,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        appTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MossInk,
                                    selectedTextColor = MossInk,
                                    indicatorColor = MossLime
                                ),
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                VoiceHotkeyFab(
                    voiceState = voiceState,
                    showDialog = showVoiceDialog,
                    showOpenKanbanAction = currentRoute != ROUTE_KANBAN,
                    onOpenDialog = { showVoiceDialog = true },
                    onDismissDialog = { showVoiceDialog = false },
                    onOpenKanban = {
                        showVoiceDialog = false
                        navigateToTab(ROUTE_KANBAN)
                    },
                    onStartRecordingState = { viewModel.setVoiceRecordingState() },
                    onVoiceErrorState = { msg -> viewModel.setVoiceErrorState(msg) },
                    onResetVoiceState = { viewModel.resetVoiceState() },
                    onSubmitAudio = { bytes, txt -> viewModel.processVoicePayload(bytes, txt) }
                )
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpanded) {
                    NavigationRail(
                        containerColor = MossPaper,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        Spacer(modifier = Modifier.size(12.dp))
                        appTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MossInk,
                                    selectedTextColor = MossInk,
                                    indicatorColor = MossLime
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { signOutUser(credentialManager, scope) },
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Выйти",
                                tint = MossInk
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Compact status header on non-story screens with Sign-Out action
                    if (currentRoute != ROUTE_STORY && !isExpanded) {
                        Surface(
                            color = MossPaper,
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MossNode AI · $currentUserName",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MossInk,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { signOutUser(credentialManager, scope) },
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = "Выйти из аккаунта",
                                        tint = MossInk
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_STORY
                        ) {
                            composable(ROUTE_STORY) {
                                MossStoryWebView(
                                    liveJsonPayload = liveBridgeJson,
                                    onNavigate = { route, dept -> navigateToTab(route, dept) },
                                    onTriggerVoice = { showVoiceDialog = true }
                                )
                            }

                            composable(ROUTE_KANBAN) {
                                if (tasksState is DataUiState.Loading) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MossInk)
                                    }
                                } else {
                                    KanbanBoardScreen(
                                        tasks = tasks,
                                        members = members,
                                        dependencyGraph = dependencyGraph,
                                        selectedDepartment = selectedDept,
                                        onSelectDepartment = { viewModel.setDepartmentFilter(it) },
                                        onCreateTask = {
                                            editingTask = null
                                            presetDateForTask = null
                                            showTaskDialog = true
                                        },
                                        onEditTask = { task ->
                                            editingTask = task
                                            presetDateForTask = null
                                            showTaskDialog = true
                                        },
                                        onMoveStatus = { task, newSt -> viewModel.moveTaskStatus(task, newSt) },
                                        onAddMemberClick = { showMemberDialog = true },
                                        onOpenVoiceInput = {
                                            viewModel.resetVoiceState()
                                            showVoiceDialog = true
                                        }
                                    )
                                }
                            }

                            composable(ROUTE_CALENDAR) {
                                CalendarScreen(
                                    tasks = tasks,
                                    dependencyGraph = dependencyGraph,
                                    onAddTaskForDate = { dateIso ->
                                        editingTask = null
                                        presetDateForTask = dateIso
                                        showTaskDialog = true
                                    },
                                    onTaskClick = { task ->
                                        editingTask = task
                                        presetDateForTask = null
                                        showTaskDialog = true
                                    },
                                    onStatusChange = { task, st -> viewModel.moveTaskStatus(task, st) }
                                )
                            }

                            composable(ROUTE_CHAT) {
                                AiChatScreen(
                                    messages = chatMessages,
                                    streamState = chatStreamState,
                                    onSendMessage = { txt -> viewModel.sendTeamChatMessage(txt) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            initialTask = editingTask,
            presetDateIso = presetDateForTask,
            allTasks = tasks,
            members = members,
            onDismiss = { showTaskDialog = false },
            onSave = { id, title, desc, dept, st, prio, assignee, dateIso, timeTxt, hrs, blockers ->
                viewModel.saveTask(id, title, desc, dept, st, prio, assignee, dateIso, timeTxt, hrs, blockers)
            },
            onDelete = { id -> viewModel.deleteTask(id) }
        )
    }

    if (showMemberDialog) {
        AddMemberDialog(
            members = members,
            onDismiss = { showMemberDialog = false },
            onAddMember = { name, spec -> viewModel.addMember(name, spec) }
        )
    }
}
