package com.example.model

enum class AgentConnectionMode {
    ON_DEVICE,
    REMOTE_SERVER
}

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AgentWorkMode(val label: String) {
    BUILD("build"),
    PLAN("plan")
}

enum class VioMood {
    IDLE,
    THINKING,
    CHEER,
    SAD
}

enum class ProviderApiStyle(val label: String) {
    OPENAI_COMPATIBLE("OpenAI-compatible"),
    ANTHROPIC("Anthropic"),
    GEMINI("Gemini")
}

enum class ProjectType(val label: String) {
    WEB("Web"),
    NATIVE_ANDROID("Native Android"),
    GENERAL("Project")
}

enum class ChatRole {
    USER,
    ASSISTANT,
    ERROR,
    NOTICE
}

enum class ReasoningEffort(val label: String, val apiValue: String?) {
    AUTO("Auto", null),
    LOW("Low", "low"),
    MEDIUM("Medium", "medium"),
    HIGH("High", "high")
}

data class FallbackEntry(
    val id: String,
    val providerId: String,
    val model: String
)

data class LimitFlagEntry(
    val providerId: String,
    val keyId: String,
    val flaggedDate: String
)

enum class ToolKind(val displayName: String) {
    LIST_FILES("List files"),
    READ_FILE("Read file"),
    SEARCH_TEXT("Search text"),
    CREATE_FILE("Create file"),
    EDIT_FILE("Edit file"),
    RUN_COMMAND("Run command"),
    LOAD_SKILL("Load skill"),
    MCP_TOOL("MCP tool")
}

data class McpToolInfo(
    val name: String,
    val description: String = "",
    val inputSchemaJson: String = """{"type":"object","properties":{}}"""
)

data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val isConnected: Boolean = false,
    val tools: List<McpToolInfo> = emptyList(),
    val sessionId: String? = null,
    val lastTestStatus: String? = null,
    val lastTestError: String? = null,
    val isTesting: Boolean = false
)

enum class PermissionRuleType(val label: String) {
    FILE("File"),
    COMMAND("Command")
}

enum class PermissionRuleAction(val label: String) {
    ALLOW("Allow"),
    ASK("Ask"),
    DENY("Deny")
}

data class PermissionRule(
    val id: String,
    val type: PermissionRuleType,
    val pattern: String,
    val action: PermissionRuleAction
)

data class CustomCommand(
    val id: String,
    val name: String,
    val prompt: String,
    val isFromProject: Boolean = false
)

data class AgentSkill(
    val id: String,
    val name: String,
    val instructions: String,
    val isBuiltIn: Boolean = false,
    val isFromProject: Boolean = false
)

enum class ToolStatus {
    RUNNING,
    DONE,
    FAILED,
    DENIED,
    UNDONE
}

data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiStyle: ProviderApiStyle,
    val description: String
)

object ProviderPresets {
    val officialPresets = listOf(
        ProviderPreset(
            id = "anthropic",
            name = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1",
            apiStyle = ProviderApiStyle.ANTHROPIC,
            description = "Claude models · api.anthropic.com"
        ),
        ProviderPreset(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "GPT & o-series models · api.openai.com"
        ),
        ProviderPreset(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Unified multi-provider gateway · openrouter.ai"
        ),
        ProviderPreset(
            id = "gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiStyle = ProviderApiStyle.GEMINI,
            description = "Gemini models · generativelanguage.googleapis.com"
        ),
        ProviderPreset(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Ultra-fast LPU inference · api.groq.com"
        ),
        ProviderPreset(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "DeepSeek coding & reasoning · api.deepseek.com"
        ),
        ProviderPreset(
            id = "mistral",
            name = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Mistral & Codestral · api.mistral.ai"
        ),
        ProviderPreset(
            id = "xai",
            name = "xAI",
            baseUrl = "https://api.x.ai/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Grok models · api.x.ai"
        ),
        ProviderPreset(
            id = "together",
            name = "Together",
            baseUrl = "https://api.together.xyz/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Open-weights inference · api.together.xyz"
        ),
        ProviderPreset(
            id = "cerebras",
            name = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Wafer-scale fast inference · api.cerebras.ai"
        )
    )

    val customEndpointPresets = listOf(
        ProviderPreset(
            id = "ollama",
            name = "Ollama",
            baseUrl = "http://127.0.0.1:11434/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Local Ollama OpenAI-compatible server"
        ),
        ProviderPreset(
            id = "lm_studio",
            name = "LM Studio",
            baseUrl = "http://127.0.0.1:1234/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            description = "Local LM Studio server"
        )
    )
}

data class ProviderKeyEntry(
    val id: String,
    val label: String,
    val value: String
)

data class ProviderConfig(
    val id: String,
    val name: String,
    val subtitleLabel: String,
    val baseUrl: String,
    val apiStyle: ProviderApiStyle,
    val isCustom: Boolean,
    val isEnabled: Boolean,
    val keys: List<ProviderKeyEntry> = emptyList(),
    val activeKeyId: String? = null,
    val customModelIds: List<String> = emptyList(),
    val fetchedModels: List<String> = emptyList()
) {
    val activeKeyEntry: ProviderKeyEntry?
        get() = keys.firstOrNull { it.id == activeKeyId } ?: keys.firstOrNull()

    val activeApiKey: String
        get() = activeKeyEntry?.value.orEmpty()

    val availableModels: List<String>
        get() = (fetchedModels + customModelIds)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

data class ChatAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val base64Data: String,
    val localFilePath: String? = null
)

data class ToolExecutionItem(
    val id: String,
    val kind: ToolKind,
    val target: String,
    val summary: String = "",
    val fullOutput: String = "",
    val status: ToolStatus = ToolStatus.RUNNING,
    val canUndo: Boolean = false,
    val backupRelativePath: String? = null,
    val previousContent: String? = null,
    val existedBefore: Boolean = true,
    val titleOverride: String? = null
)

data class ChatMessageItem(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val attachment: ChatAttachment? = null,
    val toolCalls: List<ToolExecutionItem> = emptyList(),
    val isStreaming: Boolean = false,
    val modelPromptText: String? = null
)

data class PermissionPromptState(
    val requestId: String,
    val toolKind: ToolKind,
    val title: String,
    val target: String,
    val previewText: String
)

data class SessionItem(
    val id: String,
    val projectName: String,
    val projectFolder: String,
    val folderUri: String? = null,
    val mode: AgentWorkMode = AgentWorkMode.BUILD,
    val model: String = "",
    val createdAtLabel: String = "Just now",
    val lastActivityAtMs: Long = System.currentTimeMillis(),
    val projectType: ProjectType = ProjectType.GENERAL,
    val hasIndexHtml: Boolean = false,
    val reasoningEffortOverride: ReasoningEffort? = null
)

sealed interface ProviderOperationState {
    data object Idle : ProviderOperationState
    data class Loading(val message: String) : ProviderOperationState
    data class Success(val message: String, val models: List<String> = emptyList()) : ProviderOperationState
    data class Error(val message: String) : ProviderOperationState
}

data class AppUiState(
    val selectedSetupTab: AgentConnectionMode = AgentConnectionMode.ON_DEVICE,
    val onDeviceProjectFolder: String = "",
    val hasStorageAccess: Boolean = false,
    val serverUrl: String = "",
    val serverPassword: String = "",
    val serverUrlError: String? = null,
    val isConnectingRemote: Boolean = false,
    val remoteConnectionError: String? = null,
    val isRemoteConnected: Boolean = false,
    val activeConnectionMode: AgentConnectionMode = AgentConnectionMode.ON_DEVICE,
    val sessions: List<SessionItem> = emptyList(),
    val sessionMessages: Map<String, List<ChatMessageItem>> = emptyMap(),
    val isRefreshingSessions: Boolean = false,
    val providers: List<ProviderConfig> = emptyList(),
    val selectedModel: String = "",
    val selectedWorkMode: AgentWorkMode = AgentWorkMode.BUILD,
    val autoSwitchEnabled: Boolean = true,
    val fallbackOrder: List<FallbackEntry> = emptyList(),
    val limitFlags: List<LimitFlagEntry> = emptyList(),
    val defaultReasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
    val autoApproveAllPermissions: Boolean = false,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val permissionRules: List<PermissionRule> = emptyList(),
    val customCommands: List<CustomCommand> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val skillsInitialized: Boolean = false,
    val voiceAnnouncementEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val showVio: Boolean = true,
    val vioIntroShown: Boolean = false,
    val vioSideRight: Boolean = true,
    val vioYFraction: Float = 0.72f,
    val vioMood: VioMood = VioMood.IDLE,
    val isVaultStatePending: Boolean = false,
    val draftMessage: String = "",
    val pendingAttachment: ChatAttachment? = null,
    val activeStreamingSessionId: String? = null,
    val workingStatusText: String? = null,
    val permissionPrompt: PermissionPromptState? = null,
    val providerOperationState: ProviderOperationState = ProviderOperationState.Idle,
    val activeOperationProviderId: String? = null
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.isEnabled }

    val hasConnectedProvider: Boolean
        get() = activeProvider != null

    val activeProviderModels: List<String>
        get() = activeProvider?.availableModels.orEmpty()

    val effectiveVioMood: VioMood
        get() = if (activeStreamingSessionId != null) VioMood.THINKING else vioMood

    fun activeLimitFlagsCount(todayDate: String): Int =
        limitFlags.count { it.flaggedDate == todayDate }
}
