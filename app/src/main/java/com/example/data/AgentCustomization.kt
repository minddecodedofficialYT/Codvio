package com.example.data

import com.example.model.AgentSkill
import com.example.model.PermissionRule
import com.example.model.PermissionRuleAction
import com.example.model.PermissionRuleType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object BuiltInSkills {
    val defaults: List<AgentSkill> = listOf(
        AgentSkill(
            id = "builtin_code_review",
            name = "code-review",
            isBuiltIn = true,
            instructions = """
                Perform a thorough, structured code review:
                1. Inspect modified or target files for correctness, edge cases, null safety, resource leaks, and security issues.
                2. Check adherence to existing project architecture, naming conventions, and error handling.
                3. Verify that tests cover critical paths and failure modes.
                4. Report findings grouped by severity (Critical, Warning, Suggestion) with concrete file/line references and minimal fixes.
            """.trimIndent()
        ),
        AgentSkill(
            id = "builtin_port_from_project",
            name = "port-from-project",
            isBuiltIn = true,
            instructions = """
                Port a feature or module cleanly from another project or reference file:
                1. Inspect both the source reference and the current project's dependencies, package names, and conventions.
                2. Adapt imports, state management, theming, and error handling to match the target project natively instead of blind copy-paste.
                3. Preserve behavior parity and edge-case handling, then verify compilation or tests after porting.
            """.trimIndent()
        ),
        AgentSkill(
            id = "builtin_skill_creator",
            name = "skill-creator",
            isBuiltIn = true,
            instructions = """
                Create a clear, reusable agent skill in .pocketcode/skills/<name>/SKILL.md:
                1. Choose a concise kebab-case skill name and clear trigger description.
                2. Write actionable, step-by-step workflow instructions, constraints, and verification steps.
                3. Include concrete examples or checklists only where they prevent mistakes, keeping the skill focused and concise.
            """.trimIndent()
        ),
        AgentSkill(
            id = "builtin_spike",
            name = "spike",
            isBuiltIn = true,
            instructions = """
                Run a time-boxed technical spike to evaluate feasibility before full implementation:
                1. Clarify the core unknown or architectural question first.
                2. Build the smallest possible working prototype or verification test to answer the question with real evidence.
                3. Summarize findings, trade-offs, risks, and a concrete recommendation before modifying production paths.
            """.trimIndent()
        ),
        AgentSkill(
            id = "builtin_systematic_debugging",
            name = "systematic-debugging",
            isBuiltIn = true,
            instructions = """
                Debug defects systematically rather than guessing:
                1. Reproduce or read the exact failure, stack trace, and triggering inputs first.
                2. Form a specific hypothesis about root cause and inspect relevant state/files to confirm or rule it out.
                3. Implement the minimal fix addressing the root cause (not just the symptom) and verify the fix with a test or build check.
            """.trimIndent()
        )
    )
}

object PermissionRuleEvaluator {

    fun evaluate(
        rules: List<PermissionRule>,
        type: PermissionRuleType,
        target: String
    ): PermissionRuleAction {
        var resolved = PermissionRuleAction.ASK
        for (rule in rules) {
            if (rule.type == type && matches(rule.pattern, type, target)) {
                resolved = rule.action
            }
        }
        return resolved
    }

    fun matches(patternRaw: String, type: PermissionRuleType, targetRaw: String): Boolean {
        val pattern = patternRaw.trim()
        val target = targetRaw.trim()
        if (pattern.isEmpty() || target.isEmpty()) return false

        val regex = globToRegex(pattern)
        if (type == PermissionRuleType.FILE) {
            val normalizedTarget = target.replace('\\', '/').trimStart('/')
            if (regex.matches(normalizedTarget)) return true
            if (!pattern.contains('/')) {
                val fileName = normalizedTarget.substringAfterLast('/')
                if (regex.matches(fileName)) return true
            }
            return false
        } else {
            return regex.matches(target)
        }
    }

    private fun globToRegex(pattern: String): Regex {
        val normalized = pattern.replace('\\', '/')
        val sb = StringBuilder("^")
        var i = 0
        while (i < normalized.length) {
            val ch = normalized[i]
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                else -> sb.append(Regex.escape(ch.toString()))
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    fun parsePocketCodeJsonRules(rawJson: String): List<PermissionRule> {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<PermissionRule>()

        // Format A: { "permissionRules": [ { "type": "file"|"command", "pattern": "*.md", "action": "allow"|"ask"|"deny" } ] }
        // Also supports "rules" array with same item format
        val array = root.optJSONArray("permissionRules") ?: root.optJSONArray("rules")
        if (array != null) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val typeStr = obj.optString("type").trim().lowercase()
                val pattern = obj.optString("pattern").trim()
                val actionStr = obj.optString("action").trim().lowercase()
                val type = when (typeStr) {
                    "command", "cmd", "bash", "shell" -> PermissionRuleType.COMMAND
                    "file", "path", "edit", "write" -> PermissionRuleType.FILE
                    else -> continue
                }
                val action = parseAction(actionStr) ?: continue
                if (pattern.isNotEmpty()) {
                    out.add(PermissionRule("proj_${UUID.randomUUID()}", type, pattern, action))
                }
            }
        }

        // Format B: { "permissions": { "allow": [...], "ask": [...], "deny": [...] } }
        val permObj = root.optJSONObject("permissions") ?: root.optJSONObject("permission")
        if (permObj != null) {
            parseBucket(permObj.optJSONArray("allow"), PermissionRuleAction.ALLOW, out)
            parseBucket(permObj.optJSONArray("ask"), PermissionRuleAction.ASK, out)
            parseBucket(permObj.optJSONArray("deny"), PermissionRuleAction.DENY, out)

            // Support object entries like { "file": { "*.md": "allow" }, "command": { "git *": "allow" } }
            listOf("file" to PermissionRuleType.FILE, "edit" to PermissionRuleType.FILE, "command" to PermissionRuleType.COMMAND, "bash" to PermissionRuleType.COMMAND)
                .forEach { (key, type) ->
                    val sub = permObj.optJSONObject(key)
                    if (sub != null) {
                        val keys = sub.keys()
                        while (keys.hasNext()) {
                            val pat = keys.next()
                            val act = parseAction(sub.optString(pat).lowercase()) ?: continue
                            out.add(PermissionRule("proj_${UUID.randomUUID()}", type, pat, act))
                        }
                    } else {
                        val directAction = parseAction(permObj.optString(key).lowercase())
                        if (directAction != null) {
                            out.add(PermissionRule("proj_${UUID.randomUUID()}", type, "*", directAction))
                        }
                    }
                }
        }

        return out
    }

    private fun parseBucket(
        arr: JSONArray?,
        action: PermissionRuleAction,
        out: MutableList<PermissionRule>
    ) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val raw = arr.optString(i).trim()
            if (raw.isEmpty()) continue
            val colonIdx = raw.indexOf(':')
            if (colonIdx > 0) {
                val prefix = raw.substring(0, colonIdx).trim().lowercase()
                val pat = raw.substring(colonIdx + 1).trim()
                val type = if (prefix in setOf("command", "cmd", "bash", "shell")) {
                    PermissionRuleType.COMMAND
                } else {
                    PermissionRuleType.FILE
                }
                if (pat.isNotEmpty()) {
                    out.add(PermissionRule("proj_${UUID.randomUUID()}", type, pat, action))
                }
            } else {
                out.add(PermissionRule("proj_${UUID.randomUUID()}", PermissionRuleType.FILE, raw, action))
            }
        }
    }

    private fun parseAction(raw: String): PermissionRuleAction? {
        return when (raw.trim().lowercase()) {
            "allow" -> PermissionRuleAction.ALLOW
            "ask" -> PermissionRuleAction.ASK
            "deny" -> PermissionRuleAction.DENY
            else -> null
        }
    }
}

object CustomCommandExpander {

    fun parseInvocation(rawMessage: String): Pair<String, String>? {
        val trimmed = rawMessage.trim()
        if (!trimmed.startsWith("/")) return null
        val withoutSlash = trimmed.substring(1)
        if (withoutSlash.isEmpty()) return null
        val firstSpace = withoutSlash.indexOfFirst { it.isWhitespace() }
        val cmdName = (if (firstSpace < 0) withoutSlash else withoutSlash.substring(0, firstSpace)).trim()
        if (cmdName.isEmpty()) return null
        val lower = cmdName.lowercase()
        if (lower == "init" || lower == "effort") return null
        val args = if (firstSpace < 0) "" else withoutSlash.substring(firstSpace).trim()
        return cmdName to args
    }

    fun tokenizeArguments(rawArgs: String): List<String> {
        if (rawArgs.isBlank()) return emptyList()
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quoteChar: Char? = null
        for (ch in rawArgs.trim()) {
            when {
                quoteChar != null -> {
                    if (ch == quoteChar) {
                        quoteChar = null
                    } else {
                        current.append(ch)
                    }
                }
                ch == '"' || ch == '\'' -> quoteChar = ch
                ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    fun expandPrompt(template: String, rawArgs: String): String {
        val cleanTemplate = stripMarkdownFrontmatter(template).trim()
        val tokens = tokenizeArguments(rawArgs)
        val hasArgumentsPlaceholder = cleanTemplate.contains("\$ARGUMENTS")
        val positionalRegex = Regex("""\$(\d+)""")
        val hasPositional = positionalRegex.containsMatchIn(cleanTemplate)

        var result = cleanTemplate.replace("\$ARGUMENTS", rawArgs)
        result = positionalRegex.replace(result) { match ->
            val index = match.groupValues[1].toIntOrNull() ?: 0
            if (index in 1..tokens.size) tokens[index - 1] else ""
        }

        if (!hasArgumentsPlaceholder && !hasPositional && rawArgs.isNotBlank()) {
            result = "$result\n\n$rawArgs"
        }
        return result.trim()
    }

    fun stripMarkdownFrontmatter(markdown: String): String {
        val trimmed = markdown.trimStart()
        if (!trimmed.startsWith("---")) return markdown
        val lines = trimmed.lines()
        if (lines.firstOrNull()?.trim() != "---") return markdown
        val endIdx = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (endIdx < 0) return markdown
        return lines.drop(endIdx + 2).joinToString("\n").trim()
    }
}
