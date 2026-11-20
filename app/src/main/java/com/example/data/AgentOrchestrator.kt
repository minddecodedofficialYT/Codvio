package com.example.data

import com.example.model.AgentSkill
import com.example.model.AgentWorkMode
import com.example.model.ChatMessageItem
import com.example.model.ChatRole
import com.example.model.McpServerConfig
import com.example.model.McpToolInfo
import com.example.model.PermissionPromptState
import com.example.model.PermissionRule
import com.example.model.PermissionRuleAction
import com.example.model.PermissionRuleType
import com.example.model.ProjectType
import com.example.model.ProviderConfig
import com.example.model.ReasoningEffort
import com.example.model.ToolExecutionItem
import com.example.model.ToolKind
import com.example.model.ToolStatus
import okhttp3.Call
import org.json.JSONObject
import java.util.UUID

sealed interface RateLimitSwitchOutcome {
    data class Switched(val provider: ProviderConfig, val model: String) : RateLimitSwitchOutcome
    data object AllExhausted : RateLimitSwitchOutcome
    data object Disabled : RateLimitSwitchOutcome
}

private data class BoundMcpTool(
    val llmToolName: String,
    val server: McpServerConfig,
    val tool: McpToolInfo
)

private enum class PermissionCheckDecision {
    ALLOW,
    DENIED_BY_USER,
    DENIED_BY_RULE
}

class AgentOrchestrator(
    private val chatClient: AgentChatClient,
    private val mcpClient: McpClient = McpClient()
) {

    interface Callbacks {
        fun onCallCreated(call: Call)
        suspend fun onStatusUpdate(status: String)
        suspend fun onTextDelta(assistantMessageId: String, delta: String)
        suspend fun onToolCardAdded(assistantMessageId: String, tool: ToolExecutionItem)
        suspend fun onToolCardUpdated(assistantMessageId: String, tool: ToolExecutionItem)
        suspend fun requestUserPermission(prompt: PermissionPromptState): Boolean
        suspend fun onWorkspaceMutated()
        suspend fun onRateLimitHit(
            failedProvider: ProviderConfig,
            failedModel: String,
            errorMessage: String
        ): RateLimitSwitchOutcome = RateLimitSwitchOutcome.Disabled
    }

    suspend fun runAgentTurn(
        provider: ProviderConfig,
        model: String,
        mode: AgentWorkMode,
        projectType: ProjectType,
        workspace: ProjectWorkspace,
        historyMessages: List<ChatMessageItem>,
        assistantMessageId: String,
        reasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
        autoApproveAllPermissions: Boolean = false,
        mcpServers: List<McpServerConfig> = emptyList(),
        permissionRules: List<PermissionRule> = emptyList(),
        skills: List<AgentSkill> = emptyList(),
        callbacks: Callbacks
    ): RunOutcome {
        val topLevelFiles = workspace.listFiles("").getOrNull()?.take(40).orEmpty()
        val projectRules = workspace.readProjectPermissionRules()
        val mergedRules = permissionRules + projectRules

        val projectSkills = workspace.readProjectSkills()
        val mergedSkillsMap = linkedMapOf<String, AgentSkill>()
        skills.forEach { mergedSkillsMap[it.name.lowercase()] = it }
        projectSkills.forEach { mergedSkillsMap[it.name.lowercase()] = it }
        val mergedSkills = mergedSkillsMap.values.toList()

        val mcpLookup = linkedMapOf<String, BoundMcpTool>()
        val dynamicTools = mutableListOf<DynamicToolSpec>()

        if (mergedSkills.isNotEmpty()) {
            val skillNames = mergedSkills.joinToString(", ") { it.name }
            dynamicTools.add(
                DynamicToolSpec(
                    name = LOAD_SKILL_TOOL_NAME,
                    description = "Load detailed instructions for one of the available skills when the user's request matches it. Available skills: $skillNames.",
                    schema = JSONObject(
                        """{"type":"object","properties":{"name":{"type":"string","description":"Exact skill name to load"}},"required":["name"]}"""
                    )
                )
            )
        }

        mcpServers.filter { it.isConnected && it.tools.isNotEmpty() }
            .forEachIndexed { serverIndex, server ->
                server.tools.forEachIndexed { toolIndex, mcpTool ->
                    val sanitizedTool = mcpTool.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_')
                        .ifEmpty { "tool_$toolIndex" }
                    var candidateName = "mcp_${serverIndex}_$sanitizedTool".take(64)
                    if (mcpLookup.containsKey(candidateName)) {
                        candidateName = "mcp_${serverIndex}_${toolIndex}_$sanitizedTool".take(64)
                    }
                    val schemaObj = runCatching { JSONObject(mcpTool.inputSchemaJson) }
                        .getOrElse { JSONObject("""{"type":"object","properties":{}}""") }
                    val bound = BoundMcpTool(
                        llmToolName = candidateName,
                        server = server,
                        tool = mcpTool
                    )
                    mcpLookup[candidateName] = bound
                    dynamicTools.add(
                        DynamicToolSpec(
                            name = candidateName,
                            description = "[MCP: ${server.name}] ${mcpTool.description.ifBlank { mcpTool.name }}",
                            schema = schemaObj
                        )
                    )
                }
            }

        val systemPrompt = buildSystemPrompt(
            mode = mode,
            projectType = projectType,
            workspacePath = workspace.rootDisplayPath,
            topLevelFiles = topLevelFiles,
            isInitCommand = historyMessages.lastOrNull()?.text?.trim() == "/init",
            skills = mergedSkills,
            mcpTools = mcpLookup.values.toList()
        )

        val turns = buildInitialTurns(historyMessages)
        val includeTools = (mode == AgentWorkMode.BUILD)
        val effectiveExtraTools = if (includeTools) dynamicTools else emptyList()
        var currentProvider = provider
        var currentModel = model

        for (step in 0 until MAX_AGENT_STEPS) {
            callbacks.onStatusUpdate(
                if (step == 0) "Thinking..." else "Continuing with tool results..."
            )

            var stepOutcome: StreamTurnOutcome.Success? = null
            while (stepOutcome == null) {
                when (
                    val outcome = chatClient.streamTurn(
                        provider = currentProvider,
                        model = currentModel,
                        systemPrompt = systemPrompt,
                        turns = turns,
                        includeTools = includeTools,
                        reasoningEffort = reasoningEffort,
                        extraTools = effectiveExtraTools,
                        onCallCreated = callbacks::onCallCreated,
                        onTextDelta = { delta ->
                            callbacks.onTextDelta(assistantMessageId, delta)
                        }
                    )
                ) {
                    is StreamTurnOutcome.Cancelled -> return RunOutcome.Cancelled
                    is StreamTurnOutcome.Error -> {
                        if (outcome.isRateLimit) {
                            when (val sw = callbacks.onRateLimitHit(currentProvider, currentModel, outcome.errorMessage)) {
                                is RateLimitSwitchOutcome.Switched -> {
                                    currentProvider = sw.provider
                                    currentModel = sw.model
                                    callbacks.onStatusUpdate("Retrying with ${sw.provider.name} / ${sw.model}...")
                                }
                                is RateLimitSwitchOutcome.AllExhausted -> {
                                    return RunOutcome.Error(
                                        message = "All saved keys and fallback providers have hit their limits. Reset limit flags in Settings or try again tomorrow.",
                                        isRateLimit = true
                                    )
                                }
                                is RateLimitSwitchOutcome.Disabled -> {
                                    return RunOutcome.Error(
                                        message = outcome.errorMessage,
                                        isRateLimit = true
                                    )
                                }
                            }
                        } else {
                            return RunOutcome.Error(
                                message = outcome.errorMessage,
                                isRateLimit = false
                            )
                        }
                    }
                    is StreamTurnOutcome.Success -> {
                        stepOutcome = outcome
                    }
                }
            }

            if (stepOutcome.toolCalls.isEmpty() || mode == AgentWorkMode.PLAN) {
                return RunOutcome.Completed
            }

            turns.add(LlmTurn.Assistant(text = stepOutcome.text, toolCalls = stepOutcome.toolCalls))

            for (toolCall in stepOutcome.toolCalls) {
                val toolResultOutput = executeSingleTool(
                    toolCall = toolCall,
                    workspace = workspace,
                    assistantMessageId = assistantMessageId,
                    autoApproveAllPermissions = autoApproveAllPermissions,
                    mergedRules = mergedRules,
                    skills = mergedSkills,
                    mcpLookup = mcpLookup,
                    callbacks = callbacks
                )
                turns.add(
                    LlmTurn.ToolResult(
                        toolCallId = toolCall.id,
                        toolName = toolCall.name,
                        output = toolResultOutput
                    )
                )
            }
        }

        return RunOutcome.Completed
    }

    private suspend fun evaluateAndRequestPermission(
        toolId: String,
        toolKind: ToolKind,
        ruleType: PermissionRuleType,
        ruleTarget: String,
        title: String,
        previewText: String,
        autoApproveAllPermissions: Boolean,
        mergedRules: List<PermissionRule>,
        callbacks: Callbacks
    ): PermissionCheckDecision {
        if (autoApproveAllPermissions) {
            return PermissionCheckDecision.ALLOW
        }
        return when (PermissionRuleEvaluator.evaluate(mergedRules, ruleType, ruleTarget)) {
            PermissionRuleAction.ALLOW -> PermissionCheckDecision.ALLOW
            PermissionRuleAction.DENY -> PermissionCheckDecision.DENIED_BY_RULE
            PermissionRuleAction.ASK -> {
                val allowed = callbacks.requestUserPermission(
                    PermissionPromptState(
                        requestId = toolId,
                        toolKind = toolKind,
                        title = title,
                        target = ruleTarget,
                        previewText = previewText
                    )
                )
                if (allowed) PermissionCheckDecision.ALLOW else PermissionCheckDecision.DENIED_BY_USER
            }
        }
    }

    private suspend fun executeSingleTool(
        toolCall: LlmToolCall,
        workspace: ProjectWorkspace,
        assistantMessageId: String,
        autoApproveAllPermissions: Boolean,
        mergedRules: List<PermissionRule>,
        skills: List<AgentSkill>,
        mcpLookup: Map<String, BoundMcpTool>,
        callbacks: Callbacks
    ): String {
        val args = runCatching { JSONObject(toolCall.argumentsJson) }.getOrDefault(JSONObject())
        val toolId = UUID.randomUUID().toString()

        if (toolCall.name == LOAD_SKILL_TOOL_NAME) {
            return executeLoadSkillTool(
                toolId = toolId,
                args = args,
                skills = skills,
                assistantMessageId = assistantMessageId,
                callbacks = callbacks
            )
        }

        val boundMcp = mcpLookup[toolCall.name]
        if (boundMcp != null) {
            return executeMcpTool(
                toolId = toolId,
                boundMcp = boundMcp,
                argumentsJson = toolCall.argumentsJson,
                args = args,
                autoApproveAllPermissions = autoApproveAllPermissions,
                mergedRules = mergedRules,
                assistantMessageId = assistantMessageId,
                callbacks = callbacks
            )
        }

        val kind = when (toolCall.name) {
            "list_files" -> ToolKind.LIST_FILES
            "read_file" -> ToolKind.READ_FILE
            "search_text" -> ToolKind.SEARCH_TEXT
            "create_file" -> ToolKind.CREATE_FILE
            "edit_file" -> ToolKind.EDIT_FILE
            "run_command" -> ToolKind.RUN_COMMAND
            else -> return "Unknown tool '${toolCall.name}'."
        }

        val target = when (kind) {
            ToolKind.LIST_FILES -> args.optString("path").ifBlank { "." }
            ToolKind.READ_FILE -> args.optString("path")
            ToolKind.SEARCH_TEXT -> args.optString("query")
            ToolKind.CREATE_FILE, ToolKind.EDIT_FILE -> args.optString("path")
            ToolKind.RUN_COMMAND -> args.optString("command")
            ToolKind.LOAD_SKILL, ToolKind.MCP_TOOL -> toolCall.name
        }

        val runningCard = ToolExecutionItem(
            id = toolId,
            kind = kind,
            target = target,
            summary = "Running...",
            status = ToolStatus.RUNNING
        )
        callbacks.onToolCardAdded(assistantMessageId, runningCard)
        callbacks.onStatusUpdate("${kind.displayName}: $target")

        return when (kind) {
            ToolKind.LIST_FILES -> {
                val rel = args.optString("path")
                val res = workspace.listFiles(rel)
                res.fold(
                    onSuccess = { entries ->
                        val output = if (entries.isEmpty()) "(empty folder)" else entries.joinToString("\n")
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DONE,
                                summary = "${entries.size} item(s)",
                                fullOutput = output
                            )
                        )
                        output
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "Failed to list files"
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.FAILED,
                                summary = msg,
                                fullOutput = msg
                            )
                        )
                        "Error: $msg"
                    }
                )
            }

            ToolKind.READ_FILE -> {
                val rel = args.optString("path")
                val res = workspace.readFile(rel)
                res.fold(
                    onSuccess = { text ->
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DONE,
                                summary = "${text.lines().size} lines read",
                                fullOutput = text.take(4000)
                            )
                        )
                        text
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "Failed to read file"
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.FAILED,
                                summary = msg,
                                fullOutput = msg
                            )
                        )
                        "Error: $msg"
                    }
                )
            }

            ToolKind.SEARCH_TEXT -> {
                val query = args.optString("query")
                val rel = args.optString("path")
                val res = workspace.searchText(query, rel)
                res.fold(
                    onSuccess = { matches ->
                        val output = if (matches.isEmpty()) "No matches for '$query'" else matches.joinToString("\n")
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DONE,
                                summary = "${matches.size} match(es)",
                                fullOutput = output
                            )
                        )
                        output
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "Search failed"
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.FAILED,
                                summary = msg,
                                fullOutput = msg
                            )
                        )
                        "Error: $msg"
                    }
                )
            }

            ToolKind.CREATE_FILE -> {
                val rel = args.optString("path")
                val content = args.optString("content")
                val preview = content.lines().take(12).joinToString("\n")

                when (
                    evaluateAndRequestPermission(
                        toolId = toolId,
                        toolKind = ToolKind.CREATE_FILE,
                        ruleType = PermissionRuleType.FILE,
                        ruleTarget = rel,
                        title = "Allow creating file?",
                        previewText = preview.ifBlank { "(empty file)" },
                        autoApproveAllPermissions = autoApproveAllPermissions,
                        mergedRules = mergedRules,
                        callbacks = callbacks
                    )
                ) {
                    PermissionCheckDecision.DENIED_BY_RULE -> {
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DENIED,
                                summary = "Blocked by permission rule",
                                fullOutput = "Blocked by permission rule for file '$rel'."
                            )
                        )
                        return "Action blocked by permission rule for file '$rel'. Continue without creating this file."
                    }
                    PermissionCheckDecision.DENIED_BY_USER -> {
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DENIED,
                                summary = "Denied by user",
                                fullOutput = "User refused permission to create $rel."
                            )
                        )
                        return "User denied permission to create file '$rel'. Continue without creating this file."
                    }
                    PermissionCheckDecision.ALLOW -> Unit
                }

                val mutation = workspace.createFile(rel, content)
                if (mutation.success) {
                    callbacks.onWorkspaceMutated()
                    callbacks.onToolCardUpdated(
                        assistantMessageId,
                        runningCard.copy(
                            status = ToolStatus.DONE,
                            summary = mutation.summary,
                            fullOutput = mutation.detail + "\n\n" + preview,
                            canUndo = true,
                            backupRelativePath = rel,
                            previousContent = mutation.previousContent,
                            existedBefore = mutation.existedBefore
                        )
                    )
                    mutation.summary
                } else {
                    callbacks.onToolCardUpdated(
                        assistantMessageId,
                        runningCard.copy(
                            status = ToolStatus.FAILED,
                            summary = mutation.summary,
                            fullOutput = mutation.detail
                        )
                    )
                    "Error: ${mutation.detail}"
                }
            }

            ToolKind.EDIT_FILE -> {
                val rel = args.optString("path")
                val oldText = if (args.has("old_text")) args.optString("old_text") else null
                val newText = if (args.has("new_text")) args.optString("new_text") else null
                val content = if (args.has("content")) args.optString("content") else null

                val preview = buildString {
                    if (!oldText.isNullOrEmpty() && newText != null) {
                        appendLine("- ${oldText.lines().take(5).joinToString("\n- ")}")
                        append("+ ${newText.lines().take(5).joinToString("\n+ ")}")
                    } else {
                        append((content ?: newText.orEmpty()).lines().take(10).joinToString("\n"))
                    }
                }

                when (
                    evaluateAndRequestPermission(
                        toolId = toolId,
                        toolKind = ToolKind.EDIT_FILE,
                        ruleType = PermissionRuleType.FILE,
                        ruleTarget = rel,
                        title = "Allow editing file?",
                        previewText = preview.ifBlank { "Update $rel" },
                        autoApproveAllPermissions = autoApproveAllPermissions,
                        mergedRules = mergedRules,
                        callbacks = callbacks
                    )
                ) {
                    PermissionCheckDecision.DENIED_BY_RULE -> {
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DENIED,
                                summary = "Blocked by permission rule",
                                fullOutput = "Blocked by permission rule for file '$rel'."
                            )
                        )
                        return "Action blocked by permission rule for file '$rel'. Continue without modifying this file."
                    }
                    PermissionCheckDecision.DENIED_BY_USER -> {
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DENIED,
                                summary = "Denied by user",
                                fullOutput = "User refused permission to edit $rel."
                            )
                        )
                        return "User denied permission to edit file '$rel'. Continue without modifying this file."
                    }
                    PermissionCheckDecision.ALLOW -> Unit
                }

                val mutation = workspace.editFile(rel, oldText, newText, content)
                if (mutation.success) {
                    callbacks.onWorkspaceMutated()
                    callbacks.onToolCardUpdated(
                        assistantMessageId,
                        runningCard.copy(
                            status = ToolStatus.DONE,
                            summary = mutation.summary,
                            fullOutput = mutation.detail + "\n\n" + preview,
                            canUndo = true,
                            backupRelativePath = rel,
                            previousContent = mutation.previousContent,
                            existedBefore = mutation.existedBefore
                        )
                    )
                    mutation.summary
                } else {
                    callbacks.onToolCardUpdated(
                        assistantMessageId,
                        runningCard.copy(
                            status = ToolStatus.FAILED,
                            summary = mutation.summary,
                            fullOutput = mutation.detail
                        )
                    )
                    "Error: ${mutation.detail}"
                }
            }

            ToolKind.RUN_COMMAND -> {
                val cmd = args.optString("command")
                when (
                    evaluateAndRequestPermission(
                        toolId = toolId,
                        toolKind = ToolKind.RUN_COMMAND,
                        ruleType = PermissionRuleType.COMMAND,
                        ruleTarget = cmd,
                        title = "Allow running shell command?",
                        previewText = "Working folder: ${workspace.rootDisplayPath}",
                        autoApproveAllPermissions = autoApproveAllPermissions,
                        mergedRules = mergedRules,
                        callbacks = callbacks
                    )
                ) {
                    PermissionCheckDecision.DENIED_BY_RULE -> {
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DENIED,
                                summary = "Blocked by permission rule",
                                fullOutput = "Blocked by permission rule for command: $cmd"
                            )
                        )
                        return "Action blocked by permission rule for command '$cmd'. Continue without running this command."
                    }
                    PermissionCheckDecision.DENIED_BY_USER -> {
                        callbacks.onToolCardUpdated(
                            assistantMessageId,
                            runningCard.copy(
                                status = ToolStatus.DENIED,
                                summary = "Denied by user",
                                fullOutput = "User refused permission to run: $cmd"
                            )
                        )
                        return "User denied permission to run command '$cmd'. Continue without running this command."
                    }
                    PermissionCheckDecision.ALLOW -> Unit
                }

                val result = workspace.runCommand(cmd)
                callbacks.onWorkspaceMutated()
                callbacks.onToolCardUpdated(
                    assistantMessageId,
                    runningCard.copy(
                        status = if (result.success) ToolStatus.DONE else ToolStatus.FAILED,
                        summary = "exit ${result.exitCode}",
                        fullOutput = result.output
                    )
                )
                "Exit code: ${result.exitCode}\n${result.output}"
            }

            ToolKind.LOAD_SKILL, ToolKind.MCP_TOOL -> ""
        }
    }

    private suspend fun executeLoadSkillTool(
        toolId: String,
        args: JSONObject,
        skills: List<AgentSkill>,
        assistantMessageId: String,
        callbacks: Callbacks
    ): String {
        val requestedName = args.optString("name").trim()
        val matched = skills.firstOrNull { it.name.equals(requestedName, ignoreCase = true) }
        val displayTitle = "Skill loaded: ${matched?.name ?: requestedName}"

        val card = ToolExecutionItem(
            id = toolId,
            kind = ToolKind.LOAD_SKILL,
            target = matched?.name ?: requestedName,
            summary = if (matched != null) "${matched.instructions.lines().size} lines" else "Not found",
            fullOutput = matched?.instructions ?: "No skill named '$requestedName' found.",
            status = if (matched != null) ToolStatus.DONE else ToolStatus.FAILED,
            titleOverride = displayTitle
        )
        callbacks.onToolCardAdded(assistantMessageId, card)
        callbacks.onStatusUpdate(displayTitle)
        return matched?.instructions
            ?: "Skill '$requestedName' not found. Available skills: ${skills.joinToString { it.name }}"
    }

    private suspend fun executeMcpTool(
        toolId: String,
        boundMcp: BoundMcpTool,
        argumentsJson: String,
        args: JSONObject,
        autoApproveAllPermissions: Boolean,
        mergedRules: List<PermissionRule>,
        assistantMessageId: String,
        callbacks: Callbacks
    ): String {
        val displayTitle = "${boundMcp.server.name}: ${boundMcp.tool.name}"
        val targetDisplay = argumentsJson.takeIf { it.isNotBlank() && it != "{}" } ?: boundMcp.tool.name

        val runningCard = ToolExecutionItem(
            id = toolId,
            kind = ToolKind.MCP_TOOL,
            target = targetDisplay,
            summary = "Running MCP tool...",
            status = ToolStatus.RUNNING,
            titleOverride = displayTitle
        )
        callbacks.onToolCardAdded(assistantMessageId, runningCard)
        callbacks.onStatusUpdate(displayTitle)

        if (isMutatingMcpTool(boundMcp.tool, args)) {
            val inferredCommand = args.optString("command").ifBlank { boundMcp.tool.name }
            val inferredPath = args.optString("path").ifBlank { args.optString("file") }
            val (ruleType, ruleTarget) = if (inferredPath.isNotBlank()) {
                PermissionRuleType.FILE to inferredPath
            } else {
                PermissionRuleType.COMMAND to inferredCommand
            }
            when (
                evaluateAndRequestPermission(
                    toolId = toolId,
                    toolKind = ToolKind.MCP_TOOL,
                    ruleType = ruleType,
                    ruleTarget = ruleTarget,
                    title = "Allow MCP tool '${boundMcp.tool.name}'?",
                    previewText = "Server: ${boundMcp.server.name}\nArgs: ${argumentsJson.take(400)}",
                    autoApproveAllPermissions = autoApproveAllPermissions,
                    mergedRules = mergedRules,
                    callbacks = callbacks
                )
            ) {
                PermissionCheckDecision.DENIED_BY_RULE -> {
                    callbacks.onToolCardUpdated(
                        assistantMessageId,
                        runningCard.copy(
                            status = ToolStatus.DENIED,
                            summary = "Blocked by permission rule",
                            fullOutput = "Blocked by permission rule for MCP tool '$ruleTarget'."
                        )
                    )
                    return "Action blocked by permission rule for '${boundMcp.tool.name}'. Continue without it."
                }
                PermissionCheckDecision.DENIED_BY_USER -> {
                    callbacks.onToolCardUpdated(
                        assistantMessageId,
                        runningCard.copy(
                            status = ToolStatus.DENIED,
                            summary = "Denied by user",
                            fullOutput = "User refused permission to run MCP tool ${boundMcp.tool.name}."
                        )
                    )
                    return "User denied permission to run MCP tool '${boundMcp.tool.name}'."
                }
                PermissionCheckDecision.ALLOW -> Unit
            }
        }

        val outcome = mcpClient.callTool(
            serverUrl = boundMcp.server.url,
            sessionId = boundMcp.server.sessionId,
            toolName = boundMcp.tool.name,
            argumentsJson = argumentsJson
        )
        callbacks.onToolCardUpdated(
            assistantMessageId,
            runningCard.copy(
                status = if (outcome.success) ToolStatus.DONE else ToolStatus.FAILED,
                summary = if (outcome.success) "${outcome.output.lines().size} line(s)" else "MCP error",
                fullOutput = outcome.output.take(6000)
            )
        )
        return outcome.output
    }

    private fun isMutatingMcpTool(tool: McpToolInfo, args: JSONObject): Boolean {
        val lower = "${tool.name} ${tool.description}".lowercase()
        val readPrefixes = listOf("resolve-", "query-", "get-", "list-", "read-", "search-", "fetch-", "find-", "resolve_", "query_", "get_", "list_", "read_", "search_")
        if (readPrefixes.any { tool.name.lowercase().startsWith(it) } &&
            !lower.contains("delete") && !lower.contains("write") && !lower.contains("execute")
        ) {
            return false
        }
        val mutatingKeywords = listOf(
            "write", "edit", "create", "delete", "remove", "move", "patch",
            "run", "exec", "shell", "bash", "command", "install", "apply", "commit", "push"
        )
        return args.has("command") || mutatingKeywords.any { lower.contains(it) }
    }

    private fun buildInitialTurns(historyMessages: List<ChatMessageItem>): MutableList<LlmTurn> {
        val turns = mutableListOf<LlmTurn>()
        historyMessages.forEach { msg ->
            when (msg.role) {
                ChatRole.USER -> {
                    val userText = when {
                        !msg.modelPromptText.isNullOrBlank() -> msg.modelPromptText
                        msg.text.trim() == "/init" ->
                            "/init — Inspect the project folder's files first using tools and provide a clear, concise overview of this project."
                        else -> msg.text
                    }
                    turns.add(LlmTurn.User(text = userText, attachment = msg.attachment))
                }
                ChatRole.ASSISTANT -> {
                    if (msg.text.isNotBlank()) {
                        turns.add(LlmTurn.Assistant(text = msg.text))
                    }
                }
                ChatRole.ERROR, ChatRole.NOTICE -> Unit
            }
        }
        return turns
    }

    private fun buildSystemPrompt(
        mode: AgentWorkMode,
        projectType: ProjectType,
        workspacePath: String,
        topLevelFiles: List<String>,
        isInitCommand: Boolean,
        skills: List<AgentSkill>,
        mcpTools: List<BoundMcpTool>
    ): String {
        val typeHint = when (projectType) {
            ProjectType.WEB -> "Detected project type: Web project (index.html found). Write clean HTML, CSS, and JavaScript."
            ProjectType.NATIVE_ANDROID -> "Detected project type: Native Android project (Gradle/Kotlin files found). Work in Kotlin and Android Gradle structure."
            ProjectType.GENERAL -> "Detected project type: General workspace."
        }

        val modeInstruction = if (mode == AgentWorkMode.PLAN) {
            "MODE: PLAN MODE. You MUST reply only with a clear step-by-step plan in text. Do NOT create, edit, or delete any file and do NOT run any shell command."
        } else {
            "MODE: BUILD MODE. You can use tools (list_files, read_file, search_text, create_file, edit_file, run_command${if (skills.isNotEmpty()) ", load_skill" else ""}) strictly inside the project folder '$workspacePath'. Never attempt to read or modify anything outside the project folder."
        }

        val topFilesSummary = if (topLevelFiles.isEmpty()) {
            "Current project folder is empty."
        } else {
            "Top-level files in project folder:\n" + topLevelFiles.joinToString("\n") { "- $it" }
        }

        val skillsHint = if (skills.isNotEmpty() && mode == AgentWorkMode.BUILD) {
            "\nAvailable skills (call `load_skill` when the task matches one): " +
                skills.joinToString(", ") { it.name }
        } else {
            ""
        }

        val mcpHint = if (mcpTools.isNotEmpty() && mode == AgentWorkMode.BUILD) {
            "\nConnected MCP tools available: " +
                mcpTools.joinToString(", ") { "${it.llmToolName} (${it.server.name}:${it.tool.name})" }
        } else {
            ""
        }

        val initHint = if (isInitCommand) {
            "\nThe user sent /init: inspect the project files first and summarize the project structure, stack, and purpose concisely."
        } else {
            ""
        }

        return """
            You are Codvio, an expert mobile AI coding assistant.
            $modeInstruction
            $typeHint
            $topFilesSummary$skillsHint$mcpHint$initHint
            Be concise, practical, and accurate.
        """.trimIndent()
    }

    sealed interface RunOutcome {
        data object Completed : RunOutcome
        data object Cancelled : RunOutcome
        data class Error(val message: String, val isRateLimit: Boolean = false) : RunOutcome
    }

    companion object {
        private const val MAX_AGENT_STEPS = 12
        const val LOAD_SKILL_TOOL_NAME = "load_skill"
    }
}

