package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AgentChatClient
import com.example.data.AgentOrchestrator
import com.example.data.BuiltInSkills
import com.example.data.CustomCommandExpander
import com.example.data.EncryptedProviderStorage
import com.example.data.FetchModelsOutcome
import com.example.data.McpClient
import com.example.data.McpTestOutcome
import com.example.data.NotesStorage
import com.example.data.NotesTodosSnapshot
import com.example.data.ProjectWorkspace
import com.example.data.ProviderJsonCodec
import com.example.data.ProviderNetworkClient
import com.example.data.ProviderVaultSnapshot
import com.example.data.RateLimitSwitchOutcome
import com.example.data.RemoteChatOutcome
import com.example.data.RemoteConnectOutcome
import com.example.data.RemoteServerClient
import com.example.data.SessionStorage
import com.example.data.SessionsHistorySnapshot
import com.example.data.TestConnectionOutcome
import com.example.model.AgentConnectionMode
import com.example.model.AgentSkill
import com.example.model.AgentWorkMode
import com.example.model.AppThemeMode
import com.example.model.AppUiState
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.CustomCommand
import com.example.model.FallbackEntry
import com.example.model.LimitFlagEntry
import com.example.model.McpServerConfig
import com.example.model.NoteItem
import com.example.model.PermissionPromptState
import com.example.model.PermissionRule
import com.example.model.PermissionRuleAction
import com.example.model.PermissionRuleType
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderKeyEntry
import com.example.model.ProviderOperationState
import com.example.model.ReasoningEffort
import com.example.model.SessionItem
import com.example.model.TodoItem
import com.example.model.ToolExecutionItem
import com.example.model.ToolStatus
import com.example.model.VioMood
import com.example.service.AgentBackgroundCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import java.io.File
import java.util.UUID

class PocketCodeViewModel(
    private val appContext: Context? = null,
    private val storage: EncryptedProviderStorage? = null,
    private val sessionStorage: SessionStorage? = null,
    private val notesStorage: NotesStorage? = null,
    private val networkClient: ProviderNetworkClient = ProviderNetworkClient(),
    private val mcpClient: McpClient = McpClient(),
    private val orchestrator: AgentOrchestrator = AgentOrchestrator(AgentChatClient(), mcpClient),
    private val remoteServerClient: RemoteServerClient = RemoteServerClient(),
    private val backgroundCoordinator: AgentBackgroundCoordinator = AgentBackgroundCoordinator(appContext),
    private val todayDateProvider: () -> String = { ProviderJsonCodec.currentDateString() },
    private val vioCheerDurationMs: Long = 3_000L,
    private val vioSadDurationMs: Long = 2_600L
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppUiState(
            skills = BuiltInSkills.defaults,
            skillsInitialized = true,
            isVaultStatePending = (storage != null || sessionStorage != null)
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var activeRunJob: Job? = null
    private var activeHttpCall: Call? = null
    private var pendingPermissionDeferred: CompletableDeferred<Boolean>? = null
    private var vioMoodResetJob: Job? = null

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            backgroundCoordinator.taskCompletedEvents.collect {
                triggerTransientVioMood(VioMood.CHEER, vioCheerDurationMs)
            }
        }
        if (storage != null || sessionStorage != null || notesStorage != null) {
            viewModelScope.launch {
                try {
                    val vault = storage?.load() ?: ProviderVaultSnapshot()
                    val history = sessionStorage?.load() ?: SessionsHistorySnapshot()
                    val notesTodos = notesStorage?.load() ?: NotesTodosSnapshot()
                    val today = todayDateProvider()
                    _uiState.update { state ->
                        state.copy(
                            providers = vault.providers,
                            selectedModel = vault.selectedModel,
                            autoSwitchEnabled = vault.autoSwitchEnabled,
                            fallbackOrder = vault.fallbackOrder,
                            limitFlags = vault.limitFlags.filter { it.flaggedDate == today },
                            defaultReasoningEffort = vault.defaultReasoningEffort,
                            autoApproveAllPermissions = vault.autoApproveAllPermissions,
                            mcpServers = vault.mcpServers,
                            permissionRules = vault.permissionRules,
                            customCommands = vault.customCommands,
                            skills = vault.skills,
                            skillsInitialized = vault.skillsInitialized,
                            voiceAnnouncementEnabled = vault.voiceAnnouncementEnabled,
                            themeMode = vault.themeMode,
                            dynamicColorEnabled = vault.dynamicColorEnabled,
                            showVio = vault.showVio,
                            vioIntroShown = vault.vioIntroShown,
                            vioSideRight = vault.vioSideRight,
                            vioYFraction = vault.vioYFraction,
                            isVaultStatePending = false,
                            notes = notesTodos.notes.sortedByDescending { it.updatedAtMs },
                            todos = notesTodos.todos,
                            sessions = history.sessions.sortedByDescending { it.lastActivityAtMs },
                            sessionMessages = history.messagesBySession
                        )
                    }
                } catch (_: Exception) {
                    _uiState.update { it.copy(isVaultStatePending = false) }
                }
            }
        }
    }

    fun selectSetupTab(mode: AgentConnectionMode) {
        _uiState.update {
            it.copy(
                selectedSetupTab = mode,
                serverUrlError = null,
                remoteConnectionError = null
            )
        }
    }

    fun updateOnDeviceProjectFolder(folder: String) {
        _uiState.update { it.copy(onDeviceProjectFolder = folder) }
    }

    fun updateStorageAccess(granted: Boolean) {
        _uiState.update { it.copy(hasStorageAccess = granted) }
    }

    fun updateServerUrl(url: String) {
        _uiState.update {
            it.copy(
                serverUrl = url,
                serverUrlError = null,
                remoteConnectionError = null
            )
        }
    }

    fun updateServerPassword(password: String) {
        _uiState.update {
            it.copy(
                serverPassword = password,
                remoteConnectionError = null
            )
        }
    }

    fun startOnDevice() {
        _uiState.update {
            it.copy(
                activeConnectionMode = AgentConnectionMode.ON_DEVICE,
                isRemoteConnected = false,
                remoteConnectionError = null,
                serverUrlError = null
            )
        }
    }

    fun connectRemoteServer(onSuccess: () -> Unit = {}) {
        val current = _uiState.value
        if (current.isConnectingRemote) return

        val rawUrl = current.serverUrl.trim()
        val effectiveUrl = rawUrl.ifBlank { "http://127.0.0.1:4096" }
        val isValid = effectiveUrl.startsWith("http://") || effectiveUrl.startsWith("https://")
        if (!isValid) {
            _uiState.update {
                it.copy(
                    serverUrlError = "Enter a valid http:// or https:// URL",
                    remoteConnectionError = "Enter a valid http:// or https:// URL",
                    isRemoteConnected = false
                )
            }
            return
        }

        val passwordSnapshot = current.serverPassword
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    serverUrl = effectiveUrl,
                    serverUrlError = null,
                    remoteConnectionError = null,
                    isConnectingRemote = true
                )
            }

            when (val outcome = remoteServerClient.connect(effectiveUrl, passwordSnapshot)) {
                is RemoteConnectOutcome.Success -> {
                    _uiState.update { cur ->
                        val existingIds = cur.sessions.map { it.id }.toSet()
                        val mergedSessions = (cur.sessions + outcome.remoteSessions.filter { it.id !in existingIds })
                            .sortedByDescending { it.lastActivityAtMs }
                        cur.copy(
                            serverUrl = outcome.normalizedUrl,
                            serverUrlError = null,
                            remoteConnectionError = null,
                            isConnectingRemote = false,
                            isRemoteConnected = true,
                            activeConnectionMode = AgentConnectionMode.REMOTE_SERVER,
                            sessions = mergedSessions
                        )
                    }
                    persistSessions()
                    onSuccess()
                }

                is RemoteConnectOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isConnectingRemote = false,
                            isRemoteConnected = false,
                            remoteConnectionError = outcome.errorMessage
                        )
                    }
                }
            }
        }
    }

    fun disconnectConnection() {
        stopCurrentRun()
        _uiState.update { cur ->
            cur.copy(
                activeConnectionMode = AgentConnectionMode.ON_DEVICE,
                isRemoteConnected = false,
                isConnectingRemote = false,
                remoteConnectionError = null,
                serverUrlError = null,
                activeStreamingSessionId = null,
                workingStatusText = null,
                permissionPrompt = null
            )
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingSessions = true) }
            val loaded = sessionStorage?.load()
            if (loaded != null) {
                _uiState.update { state ->
                    state.copy(
                        sessions = loaded.sessions.sortedByDescending { it.lastActivityAtMs },
                        sessionMessages = loaded.messagesBySession
                    )
                }
            }
            val current = _uiState.value
            if (current.activeConnectionMode == AgentConnectionMode.REMOTE_SERVER && current.isRemoteConnected) {
                val remoteSessions = remoteServerClient.fetchSessions(
                    serverUrl = current.serverUrl,
                    password = current.serverPassword
                )
                if (remoteSessions.isNotEmpty()) {
                    _uiState.update { state ->
                        val existingIds = state.sessions.map { it.id }.toSet()
                        val merged = (state.sessions + remoteSessions.filter { it.id !in existingIds })
                            .sortedByDescending { it.lastActivityAtMs }
                        state.copy(sessions = merged)
                    }
                }
            }
            delay(250)
            _uiState.update { it.copy(isRefreshingSessions = false) }
        }
    }

    fun createSession(
        projectNameInput: String,
        projectFolderInput: String,
        folderUri: String?
    ): SessionItem {
        val trimmedFolder = projectFolderInput.trim().ifBlank {
            _uiState.value.onDeviceProjectFolder.trim()
        }
        val derivedName = trimmedFolder
            .trimEnd('/')
            .substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?: "Workspace"

        val finalName = projectNameInput.trim().ifBlank { derivedName }
        val now = System.currentTimeMillis()
        val newSession = SessionItem(
            id = UUID.randomUUID().toString(),
            projectName = finalName,
            projectFolder = trimmedFolder,
            folderUri = folderUri,
            mode = _uiState.value.selectedWorkMode,
            model = _uiState.value.selectedModel,
            lastActivityAtMs = now
        )
        _uiState.update { state ->
            state.copy(
                sessions = listOf(newSession) + state.sessions,
                draftMessage = "",
                pendingAttachment = null
            )
        }
        persistSessions()
        refreshWorkspaceMetadata(newSession.id)
        return newSession
    }

    fun getSession(sessionId: String): SessionItem? {
        return _uiState.value.sessions.find { it.id == sessionId }
    }

    fun workspaceForSession(sessionId: String): ProjectWorkspace? {
        val ctx = appContext ?: return null
        val session = getSession(sessionId) ?: return null
        return ProjectWorkspace.forSession(ctx, session)
    }

    fun refreshWorkspaceMetadata(sessionId: String) {
        val workspace = workspaceForSession(sessionId) ?: return
        viewModelScope.launch {
            val (projectType, hasIndex) = workspace.detectProjectInfo()
            _uiState.update { state ->
                val updated = state.sessions.map { s ->
                    if (s.id == sessionId) {
                        s.copy(projectType = projectType, hasIndexHtml = hasIndex)
                    } else {
                        s
                    }
                }
                state.copy(sessions = updated)
            }
            persistSessions()
        }
    }

    fun selectModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        persistVault()
    }

    fun selectWorkMode(mode: AgentWorkMode) {
        _uiState.update { it.copy(selectedWorkMode = mode) }
    }

    fun updateDraftMessage(message: String) {
        _uiState.update { it.copy(draftMessage = message) }
    }

    fun attachImageFromUri(uri: Uri, onError: (String) -> Unit) {
        val store = sessionStorage ?: return
        viewModelScope.launch {
            store.prepareImageAttachment(uri).fold(
                onSuccess = { attachment ->
                    _uiState.update { it.copy(pendingAttachment = attachment) }
                },
                onFailure = { err ->
                    onError(err.message ?: "Unable to attach image")
                }
            )
        }
    }

    fun clearPendingAttachment() {
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun sendMessage(
        sessionId: String,
        overrideText: String? = null
    ) {
        val state = _uiState.value
        val text = (overrideText ?: state.draftMessage).trim()

        val effortOverride = parseEffortCommand(text)
        if (effortOverride != null && (overrideText != null || state.pendingAttachment == null)) {
            applySessionReasoningEffort(
                sessionId = sessionId,
                effort = effortOverride,
                clearDraft = (overrideText == null)
            )
            return
        }

        if (_uiState.value.activeStreamingSessionId != null) return

        val attachment = if (overrideText == null) state.pendingAttachment else null
        if (text.isEmpty() && attachment == null) return

        val session = getSession(sessionId) ?: return

        if (state.activeConnectionMode == AgentConnectionMode.REMOTE_SERVER) {
            sendRemoteMessage(
                sessionId = sessionId,
                session = session,
                text = text,
                attachment = attachment,
                clearDraft = (overrideText == null)
            )
            return
        }

        val initialProvider = state.activeProvider ?: return
        val initialModel = state.selectedModel.trim()
        if (initialModel.isEmpty()) return

        val workspace = workspaceForSession(sessionId) ?: return

        val today = todayDateProvider()
        val cleanedFlags = state.limitFlags.filter { it.flaggedDate == today }
        var effectiveProvider = initialProvider
        var effectiveModel = initialModel
        var initialSwitchNotice: ChatMessageItem? = null

        if (state.autoSwitchEnabled && isKeyFlagged(cleanedFlags, initialProvider.id, initialProvider.activeKeyId.orEmpty(), today)) {
            val nextCandidate = findNextAutoSwitchCandidate(
                state = state.copy(limitFlags = cleanedFlags),
                failedProvider = initialProvider,
                failedModel = initialModel,
                today = today
            )
            if (nextCandidate == null) {
                val userMsg = ChatMessageItem(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.USER,
                    text = text,
                    timestampMs = System.currentTimeMillis(),
                    attachment = attachment
                )
                val limitErrMsg = ChatMessageItem(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ERROR,
                    text = "All saved keys and fallback providers have hit their limits. Reset limit flags in Settings or try again tomorrow.",
                    timestampMs = System.currentTimeMillis()
                )
                _uiState.update { cur ->
                    val updatedList = cur.sessionMessages[sessionId].orEmpty() + userMsg + limitErrMsg
                    cur.copy(
                        limitFlags = cleanedFlags,
                        sessionMessages = cur.sessionMessages + (sessionId to updatedList),
                        draftMessage = if (overrideText == null) "" else cur.draftMessage,
                        pendingAttachment = if (overrideText == null) null else cur.pendingAttachment
                    )
                }
                persistSessions()
                return
            } else {
                effectiveProvider = nextCandidate.first
                effectiveModel = nextCandidate.second
                initialSwitchNotice = ChatMessageItem(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.NOTICE,
                    text = "Switched to ${effectiveProvider.name} / $effectiveModel — the previous one hit its limit",
                    timestampMs = System.currentTimeMillis()
                )
            }
        }

        val commandInvocation = CustomCommandExpander.parseInvocation(text)
        val matchedSavedCommand = commandInvocation?.let { (cmdName, _) ->
            state.customCommands.firstOrNull { it.name.equals(cmdName, ignoreCase = true) }
        }
        val initialModelPromptText = if (commandInvocation != null && matchedSavedCommand != null) {
            CustomCommandExpander.expandPrompt(matchedSavedCommand.prompt, commandInvocation.second)
        } else {
            null
        }

        val userMessage = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = text,
            timestampMs = System.currentTimeMillis(),
            attachment = attachment,
            modelPromptText = initialModelPromptText
        )
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessageItem(
            id = assistantMessageId,
            role = ChatRole.ASSISTANT,
            text = "",
            timestampMs = System.currentTimeMillis(),
            isStreaming = true
        )

        val priorMessages = state.sessionMessages[sessionId].orEmpty()
        val initialDisplayedMessages = buildList {
            addAll(priorMessages)
            add(userMessage)
            if (initialSwitchNotice != null) add(initialSwitchNotice)
            add(assistantPlaceholder)
        }

        _uiState.update { cur ->
            val updatedProviders = applyActiveProviderAndKey(
                providers = cur.providers,
                targetProvider = effectiveProvider
            )
            val updatedSessions = cur.sessions.map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        lastActivityAtMs = System.currentTimeMillis(),
                        model = effectiveModel,
                        mode = cur.selectedWorkMode
                    )
                } else {
                    s
                }
            }.sortedByDescending { it.lastActivityAtMs }

            vioMoodResetJob?.cancel()
            vioMoodResetJob = null
            cur.copy(
                providers = updatedProviders,
                selectedModel = effectiveModel,
                limitFlags = cleanedFlags,
                sessions = updatedSessions,
                sessionMessages = cur.sessionMessages + (sessionId to initialDisplayedMessages),
                draftMessage = if (overrideText == null) "" else cur.draftMessage,
                pendingAttachment = if (overrideText == null) null else cur.pendingAttachment,
                activeStreamingSessionId = sessionId,
                workingStatusText = "Working...",
                vioMood = VioMood.THINKING
            )
        }
        persistVault()
        persistSessions()

        val effectiveReasoningEffort = session.reasoningEffortOverride ?: state.defaultReasoningEffort
        val snapshotAutoApprove = _uiState.value.autoApproveAllPermissions
        val snapshotMcpServers = _uiState.value.mcpServers
        val snapshotRules = _uiState.value.permissionRules
        val snapshotSkills = _uiState.value.skills

        backgroundCoordinator.onRunStarted()
        activeRunJob = viewModelScope.launch {
            val resolvedUserMessage = if (userMessage.modelPromptText == null && commandInvocation != null) {
                val projCmd = workspace.readProjectCustomCommands()
                    .firstOrNull { it.name.equals(commandInvocation.first, ignoreCase = true) }
                if (projCmd != null) {
                    val expanded = CustomCommandExpander.expandPrompt(projCmd.prompt, commandInvocation.second)
                    userMessage.copy(modelPromptText = expanded).also { updatedUserMsg ->
                        _uiState.update { cur ->
                            val updatedMsgs = cur.sessionMessages[sessionId].orEmpty().map { m ->
                                if (m.id == userMessage.id) updatedUserMsg else m
                            }
                            cur.copy(sessionMessages = cur.sessionMessages + (sessionId to updatedMsgs))
                        }
                    }
                } else {
                    userMessage
                }
            } else {
                userMessage
            }

            val historyForModel = priorMessages + resolvedUserMessage
            val (detectedType, _) = workspace.detectProjectInfo()

            val outcome = orchestrator.runAgentTurn(
                provider = effectiveProvider,
                model = effectiveModel,
                mode = _uiState.value.selectedWorkMode,
                projectType = if (session.projectType != detectedType) detectedType else session.projectType,
                workspace = workspace,
                historyMessages = historyForModel,
                assistantMessageId = assistantMessageId,
                reasoningEffort = effectiveReasoningEffort,
                autoApproveAllPermissions = snapshotAutoApprove,
                mcpServers = snapshotMcpServers,
                permissionRules = snapshotRules,
                skills = snapshotSkills,
                callbacks = object : AgentOrchestrator.Callbacks {
                    override fun onCallCreated(call: Call) {
                        activeHttpCall = call
                    }

                    override suspend fun onRateLimitHit(
                        failedProvider: ProviderConfig,
                        failedModel: String,
                        errorMessage: String
                    ): RateLimitSwitchOutcome {
                        return handleRateLimitSwitch(
                            sessionId = sessionId,
                            assistantMessageId = assistantMessageId,
                            failedProvider = failedProvider,
                            failedModel = failedModel
                        )
                    }

                    override suspend fun onStatusUpdate(status: String) {
                        _uiState.update { it.copy(workingStatusText = status) }
                    }

                    override suspend fun onTextDelta(assistantMessageId: String, delta: String) {
                        _uiState.update { cur ->
                            val list = cur.sessionMessages[sessionId].orEmpty()
                            val updated = list.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(text = msg.text + delta)
                                } else {
                                    msg
                                }
                            }
                            cur.copy(sessionMessages = cur.sessionMessages + (sessionId to updated))
                        }
                    }

                    override suspend fun onToolCardAdded(
                        assistantMessageId: String,
                        tool: ToolExecutionItem
                    ) {
                        _uiState.update { cur ->
                            val list = cur.sessionMessages[sessionId].orEmpty()
                            val updated = list.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    msg.copy(toolCalls = msg.toolCalls + tool)
                                } else {
                                    msg
                                }
                            }
                            cur.copy(sessionMessages = cur.sessionMessages + (sessionId to updated))
                        }
                    }

                    override suspend fun onToolCardUpdated(
                        assistantMessageId: String,
                        tool: ToolExecutionItem
                    ) {
                        _uiState.update { cur ->
                            val list = cur.sessionMessages[sessionId].orEmpty()
                            val updated = list.map { msg ->
                                if (msg.id == assistantMessageId) {
                                    val newTools = msg.toolCalls.map { t ->
                                        if (t.id == tool.id) tool else t
                                    }
                                    msg.copy(toolCalls = newTools)
                                } else {
                                    msg
                                }
                            }
                            cur.copy(sessionMessages = cur.sessionMessages + (sessionId to updated))
                        }
                        persistSessions()
                    }

                    override suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean {
                        val deferred = CompletableDeferred<Boolean>()
                        pendingPermissionDeferred = deferred
                        _uiState.update { it.copy(permissionPrompt = prompt) }
                        return try {
                            deferred.await()
                        } finally {
                            pendingPermissionDeferred = null
                            _uiState.update { it.copy(permissionPrompt = null) }
                        }
                    }

                    override suspend fun onWorkspaceMutated() {
                        refreshWorkspaceMetadata(sessionId)
                    }
                }
            )

            _uiState.update { cur ->
                val list = cur.sessionMessages[sessionId].orEmpty()
                val finalized = when (outcome) {
                    is AgentOrchestrator.RunOutcome.Completed -> {
                        list.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(isStreaming = false) else msg
                        }.filterNot { it.id == assistantMessageId && it.text.isBlank() && it.toolCalls.isEmpty() }
                    }
                    is AgentOrchestrator.RunOutcome.Cancelled -> {
                        list.map { msg ->
                            if (msg.id == assistantMessageId) {
                                val note = if (msg.text.isBlank()) "[Stopped by user]" else "${msg.text}\n\n[Stopped]"
                                msg.copy(text = note, isStreaming = false)
                            } else {
                                msg
                            }
                        }
                    }
                    is AgentOrchestrator.RunOutcome.Error -> {
                        val withoutEmptyPlaceholder = list.mapNotNull { msg ->
                            if (msg.id == assistantMessageId) {
                                if (msg.text.isBlank() && msg.toolCalls.isEmpty()) null
                                else msg.copy(isStreaming = false)
                            } else {
                                msg
                            }
                        }
                        withoutEmptyPlaceholder + ChatMessageItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ERROR,
                            text = outcome.message,
                            timestampMs = System.currentTimeMillis()
                        )
                    }
                }

                val updatedSessions = cur.sessions.map { s ->
                    if (s.id == sessionId) s.copy(lastActivityAtMs = System.currentTimeMillis()) else s
                }.sortedByDescending { it.lastActivityAtMs }

                cur.copy(
                    sessions = updatedSessions,
                    sessionMessages = cur.sessionMessages + (sessionId to finalized),
                    activeStreamingSessionId = null,
                    workingStatusText = null,
                    permissionPrompt = null
                )
            }
            activeHttpCall = null
            persistSessions()
            refreshWorkspaceMetadata(sessionId)
            when (outcome) {
                is AgentOrchestrator.RunOutcome.Error -> triggerTransientVioMood(VioMood.SAD, vioSadDurationMs)
                is AgentOrchestrator.RunOutcome.Cancelled -> resetVioToIdle()
                is AgentOrchestrator.RunOutcome.Completed -> Unit // Cheer triggered via taskCompletedEvents
            }
            backgroundCoordinator.onRunFinished(
                voiceEnabled = _uiState.value.voiceAnnouncementEnabled,
                wasCancelled = outcome is AgentOrchestrator.RunOutcome.Cancelled,
                sessionName = session.projectName,
                wasError = outcome is AgentOrchestrator.RunOutcome.Error
            )
        }
    }

    private fun sendRemoteMessage(
        sessionId: String,
        session: SessionItem,
        text: String,
        attachment: com.example.model.ChatAttachment?,
        clearDraft: Boolean
    ) {
        val state = _uiState.value
        val commandInvocation = CustomCommandExpander.parseInvocation(text)
        val matchedSavedCommand = commandInvocation?.let { (cmdName, _) ->
            state.customCommands.firstOrNull { it.name.equals(cmdName, ignoreCase = true) }
        }
        val initialModelPromptText = if (commandInvocation != null && matchedSavedCommand != null) {
            CustomCommandExpander.expandPrompt(matchedSavedCommand.prompt, commandInvocation.second)
        } else {
            null
        }

        val userMessage = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = text,
            timestampMs = System.currentTimeMillis(),
            attachment = attachment,
            modelPromptText = initialModelPromptText
        )
        val assistantMessageId = UUID.randomUUID().toString()
        val assistantPlaceholder = ChatMessageItem(
            id = assistantMessageId,
            role = ChatRole.ASSISTANT,
            text = "",
            timestampMs = System.currentTimeMillis(),
            isStreaming = true
        )

        val priorMessages = state.sessionMessages[sessionId].orEmpty()
        val initialDisplayedMessages = priorMessages + userMessage + assistantPlaceholder

        _uiState.update { cur ->
            val updatedSessions = cur.sessions.map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        lastActivityAtMs = System.currentTimeMillis(),
                        mode = cur.selectedWorkMode
                    )
                } else {
                    s
                }
            }.sortedByDescending { it.lastActivityAtMs }

            vioMoodResetJob?.cancel()
            vioMoodResetJob = null
            cur.copy(
                sessions = updatedSessions,
                sessionMessages = cur.sessionMessages + (sessionId to initialDisplayedMessages),
                draftMessage = if (clearDraft) "" else cur.draftMessage,
                pendingAttachment = if (clearDraft) null else cur.pendingAttachment,
                activeStreamingSessionId = sessionId,
                workingStatusText = "Remote server working...",
                vioMood = VioMood.THINKING
            )
        }
        persistSessions()

        val effectiveReasoningEffort = session.reasoningEffortOverride ?: state.defaultReasoningEffort
        val remoteUrl = state.serverUrl
        val remotePassword = state.serverPassword
        val mode = state.selectedWorkMode
        val model = state.selectedModel

        backgroundCoordinator.onRunStarted()
        activeRunJob = viewModelScope.launch {
            val outcome = remoteServerClient.sendChatTurn(
                serverUrl = remoteUrl,
                password = remotePassword,
                sessionId = sessionId,
                model = model,
                mode = mode,
                reasoningEffort = effectiveReasoningEffort,
                historyMessages = priorMessages + userMessage,
                onCallCreated = { call -> activeHttpCall = call },
                onTextDelta = { delta ->
                    _uiState.update { cur ->
                        val list = cur.sessionMessages[sessionId].orEmpty()
                        val updated = list.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(text = msg.text + delta) else msg
                        }
                        cur.copy(sessionMessages = cur.sessionMessages + (sessionId to updated))
                    }
                }
            )

            _uiState.update { cur ->
                val list = cur.sessionMessages[sessionId].orEmpty()
                val finalized = when (outcome) {
                    is RemoteChatOutcome.Success -> {
                        list.map { msg ->
                            if (msg.id == assistantMessageId) msg.copy(isStreaming = false) else msg
                        }.filterNot { it.id == assistantMessageId && it.text.isBlank() }
                    }
                    is RemoteChatOutcome.Cancelled -> {
                        list.map { msg ->
                            if (msg.id == assistantMessageId) {
                                val note = if (msg.text.isBlank()) "[Stopped by user]" else "${msg.text}\n\n[Stopped]"
                                msg.copy(text = note, isStreaming = false)
                            } else {
                                msg
                            }
                        }
                    }
                    is RemoteChatOutcome.Error -> {
                        val cleaned = list.mapNotNull { msg ->
                            if (msg.id == assistantMessageId) {
                                if (msg.text.isBlank()) null else msg.copy(isStreaming = false)
                            } else {
                                msg
                            }
                        }
                        cleaned + ChatMessageItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ERROR,
                            text = outcome.errorMessage,
                            timestampMs = System.currentTimeMillis()
                        )
                    }
                }
                val updatedSessions = cur.sessions.map { s ->
                    if (s.id == sessionId) s.copy(lastActivityAtMs = System.currentTimeMillis()) else s
                }.sortedByDescending { it.lastActivityAtMs }

                cur.copy(
                    sessions = updatedSessions,
                    sessionMessages = cur.sessionMessages + (sessionId to finalized),
                    activeStreamingSessionId = null,
                    workingStatusText = null,
                    permissionPrompt = null
                )
            }
            activeHttpCall = null
            persistSessions()
            when (outcome) {
                is RemoteChatOutcome.Error -> triggerTransientVioMood(VioMood.SAD, vioSadDurationMs)
                is RemoteChatOutcome.Cancelled -> resetVioToIdle()
                is RemoteChatOutcome.Success -> Unit // Cheer triggered via taskCompletedEvents
            }
            backgroundCoordinator.onRunFinished(
                voiceEnabled = _uiState.value.voiceAnnouncementEnabled,
                wasCancelled = outcome is RemoteChatOutcome.Cancelled,
                sessionName = session.projectName,
                wasError = outcome is RemoteChatOutcome.Error
            )
        }
    }

    fun resolvePermissionPrompt(allow: Boolean) {
        pendingPermissionDeferred?.complete(allow)
        _uiState.update { it.copy(permissionPrompt = null) }
    }

    fun stopCurrentRun() {
        pendingPermissionDeferred?.complete(false)
        pendingPermissionDeferred = null
        activeHttpCall?.cancel()
        activeHttpCall = null
        activeRunJob?.cancel()
        activeRunJob = null
        resetVioToIdle()

        val streamingId = _uiState.value.activeStreamingSessionId
        if (streamingId != null) {
            _uiState.update { cur ->
                val list = cur.sessionMessages[streamingId].orEmpty().map { msg ->
                    if (msg.isStreaming) {
                        val note = if (msg.text.isBlank()) "[Stopped by user]" else "${msg.text}\n\n[Stopped]"
                        msg.copy(text = note, isStreaming = false)
                    } else {
                        msg
                    }
                }
                cur.copy(
                    sessionMessages = cur.sessionMessages + (streamingId to list),
                    activeStreamingSessionId = null,
                    workingStatusText = null,
                    permissionPrompt = null
                )
            }
            persistSessions()
        }
        backgroundCoordinator.onRunFinished(
            voiceEnabled = false,
            wasCancelled = true,
            sessionName = null
        )
    }

    fun undoToolExecution(
        sessionId: String,
        messageId: String,
        toolId: String,
        onFeedback: (String) -> Unit
    ) {
        val workspace = workspaceForSession(sessionId) ?: return
        val messages = _uiState.value.sessionMessages[sessionId].orEmpty()
        val targetMsg = messages.firstOrNull { it.id == messageId } ?: return
        val targetTool = targetMsg.toolCalls.firstOrNull { it.id == toolId } ?: return
        val relPath = targetTool.backupRelativePath ?: return

        viewModelScope.launch {
            val result = workspace.undoFileMutation(
                relativePath = relPath,
                existedBefore = targetTool.existedBefore,
                previousContent = targetTool.previousContent
            )
            result.fold(
                onSuccess = { msg ->
                    _uiState.update { cur ->
                        val updatedMsgs = cur.sessionMessages[sessionId].orEmpty().map { m ->
                            if (m.id == messageId) {
                                val updatedTools = m.toolCalls.map { t ->
                                    if (t.id == toolId) {
                                        t.copy(
                                            status = ToolStatus.UNDONE,
                                            canUndo = false,
                                            summary = msg
                                        )
                                    } else {
                                        t
                                    }
                                }
                                m.copy(toolCalls = updatedTools)
                            } else {
                                m
                            }
                        }
                        cur.copy(sessionMessages = cur.sessionMessages + (sessionId to updatedMsgs))
                    }
                    persistSessions()
                    refreshWorkspaceMetadata(sessionId)
                    onFeedback(msg)
                },
                onFailure = { err ->
                    onFeedback("Undo failed: ${err.message}")
                }
            )
        }
    }

    fun saveProvider(
        editingProviderId: String?,
        name: String,
        subtitleLabel: String,
        baseUrl: String,
        apiStyle: ProviderApiStyle,
        isCustom: Boolean,
        apiKeyInput: String,
        customModelIds: List<String> = emptyList(),
        fetchedModels: List<String> = emptyList()
    ) {
        val trimmedName = name.trim()
        val trimmedUrl = baseUrl.trim().trimEnd('/')
        val trimmedKey = apiKeyInput.trim()

        _uiState.update { state ->
            val existing = editingProviderId?.let { id ->
                state.providers.firstOrNull { it.id == id }
            }

            val updatedProviders = if (existing == null) {
                val initialKeys = if (trimmedKey.isNotEmpty()) {
                    listOf(
                        ProviderKeyEntry(
                            id = UUID.randomUUID().toString(),
                            label = "Primary",
                            value = trimmedKey
                        )
                    )
                } else {
                    emptyList()
                }
                val shouldEnable = state.providers.none { it.isEnabled }
                val newProvider = ProviderConfig(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    subtitleLabel = subtitleLabel,
                    baseUrl = trimmedUrl,
                    apiStyle = apiStyle,
                    isCustom = isCustom,
                    isEnabled = shouldEnable,
                    keys = initialKeys,
                    activeKeyId = initialKeys.firstOrNull()?.id,
                    customModelIds = customModelIds.distinct(),
                    fetchedModels = fetchedModels.distinct()
                )
                state.providers + newProvider
            } else {
                val updatedKeys = when {
                    trimmedKey.isEmpty() -> existing.keys
                    existing.keys.isEmpty() -> listOf(
                        ProviderKeyEntry(
                            id = UUID.randomUUID().toString(),
                            label = "Primary",
                            value = trimmedKey
                        )
                    )
                    else -> {
                        val targetId = existing.activeKeyId ?: existing.keys.first().id
                        existing.keys.map { entry ->
                            if (entry.id == targetId) entry.copy(value = trimmedKey) else entry
                        }
                    }
                }
                val mergedFetched = if (fetchedModels.isNotEmpty()) {
                    fetchedModels.distinct()
                } else {
                    existing.fetchedModels
                }
                state.providers.map { provider ->
                    if (provider.id == existing.id) {
                        provider.copy(
                            name = trimmedName,
                            subtitleLabel = subtitleLabel,
                            baseUrl = trimmedUrl,
                            apiStyle = apiStyle,
                            keys = updatedKeys,
                            activeKeyId = provider.activeKeyId ?: updatedKeys.firstOrNull()?.id,
                            customModelIds = customModelIds.distinct(),
                            fetchedModels = mergedFetched
                        )
                    } else {
                        provider
                    }
                }
            }

            state.copy(providers = updatedProviders)
        }
        persistVault()
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        _uiState.update { state ->
            val updatedProviders = state.providers.map { provider ->
                if (provider.id == providerId) {
                    provider.copy(isEnabled = enabled)
                } else if (enabled) {
                    provider.copy(isEnabled = false)
                } else {
                    provider
                }
            }
            val newActive = updatedProviders.firstOrNull { it.isEnabled }
            val updatedSelectedModel = if (
                newActive != null && newActive.availableModels.contains(state.selectedModel)
            ) {
                state.selectedModel
            } else {
                ""
            }
            state.copy(
                providers = updatedProviders,
                selectedModel = updatedSelectedModel
            )
        }
        persistVault()
    }

    fun deleteProvider(providerId: String) {
        _uiState.update { state ->
            val removedWasActive = state.providers.firstOrNull { it.id == providerId }?.isEnabled == true
            val remaining = state.providers.filterNot { it.id == providerId }
            state.copy(
                providers = remaining,
                selectedModel = if (removedWasActive) "" else state.selectedModel,
                fallbackOrder = state.fallbackOrder.filterNot { it.providerId == providerId },
                limitFlags = state.limitFlags.filterNot { it.providerId == providerId }
            )
        }
        persistVault()
    }

    fun setAutoSwitchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(autoSwitchEnabled = enabled) }
        persistVault()
    }

    fun addFallbackEntry(providerId: String, model: String) {
        val cleanModel = model.trim()
        if (providerId.isBlank() || cleanModel.isEmpty()) return
        _uiState.update { state ->
            if (state.fallbackOrder.any { it.providerId == providerId && it.model == cleanModel }) {
                return@update state
            }
            val newEntry = FallbackEntry(
                id = UUID.randomUUID().toString(),
                providerId = providerId,
                model = cleanModel
            )
            state.copy(fallbackOrder = state.fallbackOrder + newEntry)
        }
        persistVault()
    }

    fun removeFallbackEntry(entryId: String) {
        _uiState.update { state ->
            state.copy(fallbackOrder = state.fallbackOrder.filterNot { it.id == entryId })
        }
        persistVault()
    }

    fun moveFallbackEntryUp(entryId: String) {
        _uiState.update { state ->
            val list = state.fallbackOrder.toMutableList()
            val idx = list.indexOfFirst { it.id == entryId }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                state.copy(fallbackOrder = list)
            } else {
                state
            }
        }
        persistVault()
    }

    fun moveFallbackEntryDown(entryId: String) {
        _uiState.update { state ->
            val list = state.fallbackOrder.toMutableList()
            val idx = list.indexOfFirst { it.id == entryId }
            if (idx >= 0 && idx < list.lastIndex) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                state.copy(fallbackOrder = list)
            } else {
                state
            }
        }
        persistVault()
    }

    fun resetLimitFlags() {
        _uiState.update { it.copy(limitFlags = emptyList()) }
        persistVault()
    }

    fun setDefaultReasoningEffort(effort: ReasoningEffort) {
        _uiState.update { it.copy(defaultReasoningEffort = effort) }
        persistVault()
    }

    fun setAutoApproveAllPermissions(enabled: Boolean) {
        _uiState.update { it.copy(autoApproveAllPermissions = enabled) }
        persistVault()
    }

    fun addMcpServer(name: String, url: String) {
        val cleanName = name.trim()
        val cleanUrl = url.trim()
        if (cleanName.isEmpty() || cleanUrl.isEmpty()) return
        val newServer = McpServerConfig(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            url = cleanUrl
        )
        _uiState.update { it.copy(mcpServers = it.mcpServers + newServer) }
        persistVault()
    }

    fun deleteMcpServer(serverId: String) {
        _uiState.update { state ->
            state.copy(mcpServers = state.mcpServers.filterNot { it.id == serverId })
        }
        persistVault()
    }

    fun testMcpServer(serverId: String) {
        val server = _uiState.value.mcpServers.firstOrNull { it.id == serverId } ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    mcpServers = state.mcpServers.map { s ->
                        if (s.id == serverId) s.copy(isTesting = true, lastTestError = null) else s
                    }
                )
            }

            when (val outcome = mcpClient.testAndDiscoverTools(server.url)) {
                is McpTestOutcome.Success -> {
                    val namesPreview = outcome.tools.take(6).joinToString(", ") { it.name } +
                        if (outcome.tools.size > 6) ", +${outcome.tools.size - 6} more" else ""
                    val statusLine = if (outcome.tools.isEmpty()) {
                        "Connected (${outcome.latencyMs} ms) · 0 tools"
                    } else {
                        "Connected · ${outcome.tools.size} tool(s): $namesPreview"
                    }
                    _uiState.update { state ->
                        state.copy(
                            mcpServers = state.mcpServers.map { s ->
                                if (s.id == serverId) {
                                    s.copy(
                                        isConnected = true,
                                        tools = outcome.tools,
                                        sessionId = outcome.sessionId,
                                        lastTestStatus = statusLine,
                                        lastTestError = null,
                                        isTesting = false
                                    )
                                } else {
                                    s
                                }
                            }
                        )
                    }
                }
                is McpTestOutcome.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            mcpServers = state.mcpServers.map { s ->
                                if (s.id == serverId) {
                                    s.copy(
                                        isConnected = false,
                                        tools = emptyList(),
                                        sessionId = null,
                                        lastTestStatus = null,
                                        lastTestError = outcome.errorMessage,
                                        isTesting = false
                                    )
                                } else {
                                    s
                                }
                            }
                        )
                    }
                }
            }
            persistVault()
        }
    }

    fun addPermissionRule(
        type: PermissionRuleType,
        pattern: String,
        action: PermissionRuleAction
    ) {
        val cleanPattern = pattern.trim()
        if (cleanPattern.isEmpty()) return
        val newRule = PermissionRule(
            id = UUID.randomUUID().toString(),
            type = type,
            pattern = cleanPattern,
            action = action
        )
        _uiState.update { it.copy(permissionRules = it.permissionRules + newRule) }
        persistVault()
    }

    fun deletePermissionRule(ruleId: String) {
        _uiState.update { state ->
            state.copy(permissionRules = state.permissionRules.filterNot { it.id == ruleId })
        }
        persistVault()
    }

    fun addCustomCommand(name: String, prompt: String) {
        val cleanName = name.trim().removePrefix("/").trim()
        val cleanPrompt = prompt.trim()
        if (cleanName.isEmpty() || cleanPrompt.isEmpty()) return
        val newCmd = CustomCommand(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            prompt = cleanPrompt
        )
        _uiState.update { it.copy(customCommands = it.customCommands + newCmd) }
        persistVault()
    }

    fun deleteCustomCommand(commandId: String) {
        _uiState.update { state ->
            state.copy(customCommands = state.customCommands.filterNot { it.id == commandId })
        }
        persistVault()
    }

    fun addSkill(name: String, instructions: String) {
        val cleanName = name.trim()
        val cleanInstructions = instructions.trim()
        if (cleanName.isEmpty() || cleanInstructions.isEmpty()) return
        val newSkill = AgentSkill(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            instructions = cleanInstructions,
            isBuiltIn = false
        )
        _uiState.update {
            it.copy(
                skills = it.skills + newSkill,
                skillsInitialized = true
            )
        }
        persistVault()
    }

    fun deleteSkill(skillId: String) {
        _uiState.update { state ->
            state.copy(
                skills = state.skills.filterNot { it.id == skillId },
                skillsInitialized = true
            )
        }
        persistVault()
    }

    fun setVoiceAnnouncementEnabled(enabled: Boolean) {
        _uiState.update { it.copy(voiceAnnouncementEnabled = enabled) }
        persistVault()
    }

    fun testVoice(onError: (String) -> Unit = {}) {
        backgroundCoordinator.testVoice(onError)
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        persistVault()
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        _uiState.update { it.copy(dynamicColorEnabled = enabled) }
        persistVault()
    }

    fun setShowVio(enabled: Boolean) {
        _uiState.update { it.copy(showVio = enabled) }
        persistVault()
    }

    fun dismissVioIntro() {
        _uiState.update { it.copy(vioIntroShown = true) }
        persistVault()
    }

    fun updateVioPosition(sideRight: Boolean, yFraction: Float) {
        val clampedY = yFraction.coerceIn(0.05f, 0.92f)
        _uiState.update {
            it.copy(
                vioSideRight = sideRight,
                vioYFraction = clampedY
            )
        }
        persistVault()
    }

    fun addNote(title: String, content: String) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() && cleanContent.isEmpty()) return
        val newNote = NoteItem(
            id = UUID.randomUUID().toString(),
            title = cleanTitle.ifEmpty { "Untitled note" },
            content = cleanContent,
            updatedAtMs = System.currentTimeMillis()
        )
        _uiState.update { state ->
            state.copy(notes = listOf(newNote) + state.notes)
        }
        persistNotes()
    }

    fun updateNote(noteId: String, title: String, content: String) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        if (cleanTitle.isEmpty() && cleanContent.isEmpty()) return
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            val updated = state.notes.map { note ->
                if (note.id == noteId) {
                    note.copy(
                        title = cleanTitle.ifEmpty { "Untitled note" },
                        content = cleanContent,
                        updatedAtMs = now
                    )
                } else {
                    note
                }
            }.sortedByDescending { it.updatedAtMs }
            state.copy(notes = updated)
        }
        persistNotes()
    }

    fun deleteNote(noteId: String) {
        _uiState.update { state ->
            state.copy(notes = state.notes.filterNot { it.id == noteId })
        }
        persistNotes()
    }

    fun addTodo(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val newTodo = TodoItem(
            id = UUID.randomUUID().toString(),
            text = cleanText,
            isDone = false,
            createdAtMs = System.currentTimeMillis()
        )
        _uiState.update { state ->
            state.copy(todos = listOf(newTodo) + state.todos)
        }
        persistNotes()
    }

    fun toggleTodo(todoId: String) {
        _uiState.update { state ->
            state.copy(
                todos = state.todos.map { todo ->
                    if (todo.id == todoId) todo.copy(isDone = !todo.isDone) else todo
                }
            )
        }
        persistNotes()
    }

    fun deleteTodo(todoId: String) {
        _uiState.update { state ->
            state.copy(todos = state.todos.filterNot { it.id == todoId })
        }
        persistNotes()
    }

    private fun triggerTransientVioMood(mood: VioMood, durationMs: Long) {
        vioMoodResetJob?.cancel()
        _uiState.update { it.copy(vioMood = mood) }
        vioMoodResetJob = viewModelScope.launch {
            delay(durationMs)
            _uiState.update { cur ->
                if (cur.activeStreamingSessionId == null && cur.vioMood == mood) {
                    cur.copy(vioMood = VioMood.IDLE)
                } else {
                    cur
                }
            }
        }
    }

    private fun resetVioToIdle() {
        vioMoodResetJob?.cancel()
        vioMoodResetJob = null
        _uiState.update { it.copy(vioMood = VioMood.IDLE) }
    }

    fun onAppForegrounded() {
        backgroundCoordinator.onAppForegrounded()
    }

    fun onAppBackgrounded() {
        backgroundCoordinator.onAppLeavingOrBackgrounded()
    }

    fun onActivityPaused() {
        backgroundCoordinator.onActivityPaused()
    }

    override fun onCleared() {
        super.onCleared()
        backgroundCoordinator.shutdown()
    }

    fun parseEffortCommand(rawText: String): ReasoningEffort? {
        return when (rawText.trim().lowercase()) {
            "/effort low" -> ReasoningEffort.LOW
            "/effort medium" -> ReasoningEffort.MEDIUM
            "/effort high" -> ReasoningEffort.HIGH
            "/effort auto" -> ReasoningEffort.AUTO
            else -> null
        }
    }

    private fun applySessionReasoningEffort(
        sessionId: String,
        effort: ReasoningEffort,
        clearDraft: Boolean
    ) {
        val confirmMsg = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = ChatRole.NOTICE,
            text = "Reasoning effort for this session: ${effort.label.lowercase()}",
            timestampMs = System.currentTimeMillis()
        )
        _uiState.update { state ->
            val updatedSessions = state.sessions.map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        reasoningEffortOverride = effort,
                        lastActivityAtMs = System.currentTimeMillis()
                    )
                } else {
                    s
                }
            }.sortedByDescending { it.lastActivityAtMs }

            val updatedMessages = state.sessionMessages[sessionId].orEmpty() + confirmMsg
            state.copy(
                sessions = updatedSessions,
                sessionMessages = state.sessionMessages + (sessionId to updatedMessages),
                draftMessage = if (clearDraft) "" else state.draftMessage
            )
        }
        persistSessions()
    }

    internal fun handleRateLimitSwitch(
        sessionId: String,
        assistantMessageId: String,
        failedProvider: ProviderConfig,
        failedModel: String
    ): RateLimitSwitchOutcome {
        val state = _uiState.value
        if (!state.autoSwitchEnabled) {
            return RateLimitSwitchOutcome.Disabled
        }

        val today = todayDateProvider()
        val failedKeyId = failedProvider.activeKeyId.orEmpty()
        val updatedFlags = (state.limitFlags.filter { it.flaggedDate == today } +
            LimitFlagEntry(
                providerId = failedProvider.id,
                keyId = failedKeyId,
                flaggedDate = today
            )).distinctBy { it.providerId to it.keyId }

        val stateWithFlag = state.copy(limitFlags = updatedFlags)
        val nextCandidate = findNextAutoSwitchCandidate(
            state = stateWithFlag,
            failedProvider = failedProvider,
            failedModel = failedModel,
            today = today
        )

        if (nextCandidate == null) {
            _uiState.update { it.copy(limitFlags = updatedFlags) }
            persistVault()
            return RateLimitSwitchOutcome.AllExhausted
        }

        val (nextProvider, nextModel) = nextCandidate
        val noticeMessage = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = ChatRole.NOTICE,
            text = "Switched to ${nextProvider.name} / $nextModel — the previous one hit its limit",
            timestampMs = System.currentTimeMillis()
        )

        _uiState.update { cur ->
            val updatedProviders = applyActiveProviderAndKey(
                providers = cur.providers,
                targetProvider = nextProvider
            )
            val currentList = cur.sessionMessages[sessionId].orEmpty().toMutableList()
            val assistantIndex = currentList.indexOfFirst { it.id == assistantMessageId }
            if (assistantIndex >= 0) {
                currentList.add(assistantIndex, noticeMessage)
            } else {
                currentList.add(noticeMessage)
            }
            val updatedSessions = cur.sessions.map { s ->
                if (s.id == sessionId) {
                    s.copy(model = nextModel, lastActivityAtMs = System.currentTimeMillis())
                } else {
                    s
                }
            }
            cur.copy(
                providers = updatedProviders,
                selectedModel = nextModel,
                limitFlags = updatedFlags,
                sessions = updatedSessions,
                sessionMessages = cur.sessionMessages + (sessionId to currentList)
            )
        }
        persistVault()
        persistSessions()
        return RateLimitSwitchOutcome.Switched(nextProvider, nextModel)
    }

    private fun findNextAutoSwitchCandidate(
        state: AppUiState,
        failedProvider: ProviderConfig,
        failedModel: String,
        today: String
    ): Pair<ProviderConfig, String>? {
        val currentKeyId = failedProvider.activeKeyId.orEmpty()

        // 1. First try other saved, unflagged keys of the same provider and model
        val liveCurrentProvider = state.providers.firstOrNull { it.id == failedProvider.id } ?: failedProvider
        for (key in liveCurrentProvider.keys) {
            if (key.id != currentKeyId && !isKeyFlagged(state.limitFlags, liveCurrentProvider.id, key.id, today)) {
                return liveCurrentProvider.copy(activeKeyId = key.id) to failedModel
            }
        }

        // 2. Then move through fallbackOrder entries in order
        for (entry in state.fallbackOrder) {
            val fbProvider = state.providers.firstOrNull { it.id == entry.providerId } ?: continue
            if (fbProvider.keys.isEmpty()) {
                val isSameFailed = fbProvider.id == failedProvider.id && currentKeyId.isEmpty() && entry.model == failedModel
                if (!isSameFailed && !isKeyFlagged(state.limitFlags, fbProvider.id, "", today)) {
                    return fbProvider to entry.model
                }
            } else {
                val orderedKeys = buildList {
                    fbProvider.keys.firstOrNull { it.id == fbProvider.activeKeyId }?.let { add(it) }
                    fbProvider.keys.filter { it.id != fbProvider.activeKeyId }.forEach { add(it) }
                }
                for (key in orderedKeys) {
                    val isSameFailed = fbProvider.id == failedProvider.id && key.id == currentKeyId && entry.model == failedModel
                    if (!isSameFailed && !isKeyFlagged(state.limitFlags, fbProvider.id, key.id, today)) {
                        return fbProvider.copy(activeKeyId = key.id) to entry.model
                    }
                }
            }
        }

        return null
    }

    private fun isKeyFlagged(
        flags: List<LimitFlagEntry>,
        providerId: String,
        keyId: String,
        today: String
    ): Boolean {
        return flags.any {
            it.providerId == providerId && it.keyId == keyId && it.flaggedDate == today
        }
    }

    private fun applyActiveProviderAndKey(
        providers: List<ProviderConfig>,
        targetProvider: ProviderConfig
    ): List<ProviderConfig> {
        return providers.map { p ->
            if (p.id == targetProvider.id) {
                p.copy(
                    isEnabled = true,
                    activeKeyId = targetProvider.activeKeyId ?: p.activeKeyId
                )
            } else {
                p.copy(isEnabled = false)
            }
        }
    }

    fun addProviderKey(providerId: String, label: String, keyValue: String) {
        val trimmedKey = keyValue.trim()
        if (trimmedKey.isEmpty()) return
        _uiState.update { state ->
            val updated = state.providers.map { provider ->
                if (provider.id == providerId) {
                    val newEntry = ProviderKeyEntry(
                        id = UUID.randomUUID().toString(),
                        label = label.trim().ifBlank { "Key ${provider.keys.size + 1}" },
                        value = trimmedKey
                    )
                    val newKeys = provider.keys + newEntry
                    provider.copy(
                        keys = newKeys,
                        activeKeyId = provider.activeKeyId ?: newEntry.id
                    )
                } else {
                    provider
                }
            }
            state.copy(providers = updated)
        }
        persistVault()
    }

    fun deleteProviderKey(providerId: String, keyId: String) {
        _uiState.update { state ->
            val updated = state.providers.map { provider ->
                if (provider.id == providerId) {
                    val remainingKeys = provider.keys.filterNot { it.id == keyId }
                    val nextActiveId = if (provider.activeKeyId == keyId) {
                        remainingKeys.firstOrNull()?.id
                    } else {
                        provider.activeKeyId
                    }
                    provider.copy(
                        keys = remainingKeys,
                        activeKeyId = nextActiveId
                    )
                } else {
                    provider
                }
            }
            state.copy(providers = updated)
        }
        persistVault()
    }

    fun selectActiveProviderKey(providerId: String, keyId: String) {
        _uiState.update { state ->
            val updated = state.providers.map { provider ->
                if (provider.id == providerId && provider.keys.any { it.id == keyId }) {
                    provider.copy(activeKeyId = keyId)
                } else {
                    provider
                }
            }
            state.copy(providers = updated)
        }
        persistVault()
    }

    fun fetchModelsForProvider(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeOperationProviderId = providerId,
                    providerOperationState = ProviderOperationState.Loading("Fetching models from ${provider.name}...")
                )
            }
            when (
                val result = networkClient.fetchModels(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.activeApiKey,
                    apiStyle = provider.apiStyle
                )
            ) {
                is FetchModelsOutcome.Success -> {
                    _uiState.update { state ->
                        val updatedProviders = state.providers.map { p ->
                            if (p.id == providerId) {
                                p.copy(fetchedModels = result.models)
                            } else {
                                p
                            }
                        }
                        state.copy(
                            providers = updatedProviders,
                            activeOperationProviderId = providerId,
                            providerOperationState = ProviderOperationState.Success(
                                message = "Fetched ${result.models.size} models (${result.latencyMs} ms)",
                                models = result.models
                            )
                        )
                    }
                    persistVault()
                }
                is FetchModelsOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            activeOperationProviderId = providerId,
                            providerOperationState = ProviderOperationState.Error(result.errorMessage)
                        )
                    }
                }
            }
        }
    }

    fun clearProviderOperationState() {
        _uiState.update {
            it.copy(
                activeOperationProviderId = null,
                providerOperationState = ProviderOperationState.Idle
            )
        }
    }

    suspend fun fetchModelsAdHoc(
        baseUrl: String,
        apiKey: String,
        apiStyle: ProviderApiStyle
    ): FetchModelsOutcome {
        return networkClient.fetchModels(
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiStyle = apiStyle
        )
    }

    suspend fun testConnectionAdHoc(
        baseUrl: String,
        apiKey: String,
        apiStyle: ProviderApiStyle,
        customModelIds: List<String> = emptyList()
    ): TestConnectionOutcome {
        return networkClient.testConnection(
            baseUrl = baseUrl,
            apiKey = apiKey,
            apiStyle = apiStyle,
            customModelIds = customModelIds
        )
    }

    suspend fun exportBackup(uri: Uri): Result<Int> {
        val repo = storage ?: return Result.failure(IllegalStateException("Storage unavailable"))
        val state = _uiState.value
        val snapshot = buildVaultSnapshot(state)
        return repo.exportToUri(uri, snapshot).map { state.providers.size }
    }

    suspend fun createShareBackupFile(): Result<File> {
        val repo = storage ?: return Result.failure(IllegalStateException("Storage unavailable"))
        val state = _uiState.value
        val snapshot = buildVaultSnapshot(state)
        return repo.writeShareBackupFile(snapshot)
    }

    suspend fun importBackup(uri: Uri): Result<Int> {
        val repo = storage ?: return Result.failure(IllegalStateException("Storage unavailable"))
        val importedResult = repo.importFromUri(uri)
        return importedResult.map { snapshot ->
            _uiState.update { state ->
                state.copy(
                    providers = snapshot.providers,
                    selectedModel = snapshot.selectedModel,
                    autoSwitchEnabled = snapshot.autoSwitchEnabled,
                    fallbackOrder = snapshot.fallbackOrder,
                    limitFlags = snapshot.limitFlags,
                    defaultReasoningEffort = snapshot.defaultReasoningEffort,
                    autoApproveAllPermissions = snapshot.autoApproveAllPermissions,
                    mcpServers = snapshot.mcpServers,
                    permissionRules = snapshot.permissionRules,
                    customCommands = snapshot.customCommands,
                    skills = snapshot.skills,
                    skillsInitialized = snapshot.skillsInitialized,
                    voiceAnnouncementEnabled = snapshot.voiceAnnouncementEnabled,
                    themeMode = snapshot.themeMode,
                    dynamicColorEnabled = snapshot.dynamicColorEnabled,
                    showVio = snapshot.showVio,
                    vioIntroShown = state.vioIntroShown || snapshot.vioIntroShown,
                    vioSideRight = snapshot.vioSideRight,
                    vioYFraction = snapshot.vioYFraction
                )
            }
            persistVault()
            snapshot.providers.size
        }
    }

    private fun buildVaultSnapshot(state: AppUiState): ProviderVaultSnapshot {
        return ProviderVaultSnapshot(
            providers = state.providers,
            selectedModel = state.selectedModel,
            autoSwitchEnabled = state.autoSwitchEnabled,
            fallbackOrder = state.fallbackOrder,
            limitFlags = state.limitFlags,
            defaultReasoningEffort = state.defaultReasoningEffort,
            autoApproveAllPermissions = state.autoApproveAllPermissions,
            mcpServers = state.mcpServers,
            permissionRules = state.permissionRules,
            customCommands = state.customCommands,
            skills = state.skills,
            skillsInitialized = state.skillsInitialized,
            voiceAnnouncementEnabled = state.voiceAnnouncementEnabled,
            themeMode = state.themeMode,
            dynamicColorEnabled = state.dynamicColorEnabled,
            showVio = state.showVio,
            vioIntroShown = state.vioIntroShown,
            vioSideRight = state.vioSideRight,
            vioYFraction = state.vioYFraction
        )
    }

    private fun persistVault() {
        val repo = storage ?: return
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                repo.save(buildVaultSnapshot(state))
            }
        }
    }

    private fun persistSessions() {
        val repo = sessionStorage ?: return
        val state = _uiState.value
        viewModelScope.launch {
            repo.save(
                SessionsHistorySnapshot(
                    sessions = state.sessions,
                    messagesBySession = state.sessionMessages
                )
            )
        }
    }

    private fun persistNotes() {
        val repo = notesStorage ?: return
        val state = _uiState.value
        viewModelScope.launch {
            repo.save(
                NotesTodosSnapshot(
                    notes = state.notes,
                    todos = state.todos
                )
            )
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appCtx = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val sharedMcpClient = McpClient()
                    return PocketCodeViewModel(
                        appContext = appCtx,
                        storage = EncryptedProviderStorage(appCtx),
                        sessionStorage = SessionStorage(appCtx),
                        notesStorage = NotesStorage(appCtx),
                        networkClient = ProviderNetworkClient(),
                        mcpClient = sharedMcpClient,
                        orchestrator = AgentOrchestrator(AgentChatClient(), sharedMcpClient),
                        remoteServerClient = RemoteServerClient(),
                        backgroundCoordinator = AgentBackgroundCoordinator(appCtx)
                    ) as T
                }
            }
        }
    }
}
