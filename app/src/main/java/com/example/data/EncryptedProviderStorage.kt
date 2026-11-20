package com.example.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.model.AgentSkill
import com.example.model.AppThemeMode
import com.example.model.CustomCommand
import com.example.model.FallbackEntry
import com.example.model.LimitFlagEntry
import com.example.model.McpServerConfig
import com.example.model.McpToolInfo
import com.example.model.PermissionRule
import com.example.model.PermissionRuleAction
import com.example.model.PermissionRuleType
import com.example.model.ProviderApiStyle
import com.example.model.ProviderConfig
import com.example.model.ProviderKeyEntry
import com.example.model.ReasoningEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ProviderVaultSnapshot(
    val providers: List<ProviderConfig> = emptyList(),
    val selectedModel: String = "",
    val autoSwitchEnabled: Boolean = true,
    val fallbackOrder: List<FallbackEntry> = emptyList(),
    val limitFlags: List<LimitFlagEntry> = emptyList(),
    val defaultReasoningEffort: ReasoningEffort = ReasoningEffort.AUTO,
    val autoApproveAllPermissions: Boolean = false,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val permissionRules: List<PermissionRule> = emptyList(),
    val customCommands: List<CustomCommand> = emptyList(),
    val skills: List<AgentSkill> = BuiltInSkills.defaults,
    val skillsInitialized: Boolean = true,
    val voiceAnnouncementEnabled: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
    val showVio: Boolean = true,
    val vioIntroShown: Boolean = false,
    val vioSideRight: Boolean = true,
    val vioYFraction: Float = 0.72f
)

class EncryptedProviderStorage(private val context: Context) {

    private val vaultFile: File
        get() = File(context.filesDir, VAULT_FILE_NAME)

    suspend fun load(): ProviderVaultSnapshot = withContext(Dispatchers.IO) {
        val file = vaultFile
        if (!file.exists()) return@withContext ProviderVaultSnapshot()
        return@withContext try {
            val encryptedBytes = file.readBytes()
            val plainJson = decrypt(encryptedBytes)
            ProviderJsonCodec.decodeSnapshot(plainJson)
        } catch (_: Exception) {
            ProviderVaultSnapshot()
        }
    }

    suspend fun save(snapshot: ProviderVaultSnapshot) = withContext(Dispatchers.IO) {
        val json = ProviderJsonCodec.encodeSnapshot(snapshot)
        val encrypted = encrypt(json)
        vaultFile.writeBytes(encrypted)
    }

    suspend fun exportToUri(uri: Uri, snapshot: ProviderVaultSnapshot): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = ProviderJsonCodec.encodeSnapshot(snapshot, pretty = true)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(StandardCharsets.UTF_8))
                } ?: error("Unable to open destination file")
                Unit
            }
        }

    suspend fun writeShareBackupFile(snapshot: ProviderVaultSnapshot): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(exportDir, "pocketcode-providers.json")
                val json = ProviderJsonCodec.encodeSnapshot(snapshot, pretty = true)
                file.writeText(json, StandardCharsets.UTF_8)
                file
            }
        }

    suspend fun importFromUri(uri: Uri): Result<ProviderVaultSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawJson = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(StandardCharsets.UTF_8).readText()
                } ?: error("Unable to read selected file")
                ProviderJsonCodec.decodeSnapshot(rawJson)
            }
        }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return ByteArray(1 + iv.size + cipherBytes.size).apply {
            this[0] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(cipherBytes, 0, this, 1 + iv.size, cipherBytes.size)
        }
    }

    private fun decrypt(payload: ByteArray): String {
        require(payload.size > 13) { "Corrupted encrypted vault" }
        val ivLength = payload[0].toInt() and 0xFF
        require(ivLength in 12..16 && payload.size > 1 + ivLength) { "Invalid IV header" }
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val cipherBytes = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pocketcode_provider_vault_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VAULT_FILE_NAME = "providers_vault.enc"
    }
}

object ProviderJsonCodec {

    fun currentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun encodeSnapshot(
        snapshot: ProviderVaultSnapshot,
        pretty: Boolean = false
    ): String {
        val root = JSONObject().apply {
            put("version", 3)
            put("selectedModel", snapshot.selectedModel)
            put("autoSwitchEnabled", snapshot.autoSwitchEnabled)
            put("defaultReasoningEffort", snapshot.defaultReasoningEffort.name)
            put("autoApproveAllPermissions", snapshot.autoApproveAllPermissions)
            put("skillsInitialized", snapshot.skillsInitialized)
            put("voiceAnnouncementEnabled", snapshot.voiceAnnouncementEnabled)
            put("themeMode", snapshot.themeMode.name)
            put("dynamicColorEnabled", snapshot.dynamicColorEnabled)
            put("showVio", snapshot.showVio)
            put("vioIntroShown", snapshot.vioIntroShown)
            put("vioSideRight", snapshot.vioSideRight)
            put("vioYFraction", snapshot.vioYFraction.toDouble())

            val fallbackArray = JSONArray()
            snapshot.fallbackOrder.forEach { entry ->
                fallbackArray.put(JSONObject().apply {
                    put("id", entry.id)
                    put("providerId", entry.providerId)
                    put("model", entry.model)
                })
            }
            put("fallbackOrder", fallbackArray)

            val flagsArray = JSONArray()
            snapshot.limitFlags.forEach { flag ->
                flagsArray.put(JSONObject().apply {
                    put("providerId", flag.providerId)
                    put("keyId", flag.keyId)
                    put("flaggedDate", flag.flaggedDate)
                })
            }
            put("limitFlags", flagsArray)

            val mcpArray = JSONArray()
            snapshot.mcpServers.forEach { server ->
                mcpArray.put(JSONObject().apply {
                    put("id", server.id)
                    put("name", server.name)
                    put("url", server.url)
                    put("isConnected", server.isConnected)
                    put("sessionId", server.sessionId ?: JSONObject.NULL)
                    put("lastTestStatus", server.lastTestStatus ?: JSONObject.NULL)
                    put("lastTestError", server.lastTestError ?: JSONObject.NULL)
                    val toolsArr = JSONArray()
                    server.tools.forEach { tool ->
                        toolsArr.put(JSONObject().apply {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("inputSchemaJson", tool.inputSchemaJson)
                        })
                    }
                    put("tools", toolsArr)
                })
            }
            put("mcpServers", mcpArray)

            val rulesArray = JSONArray()
            snapshot.permissionRules.forEach { rule ->
                rulesArray.put(JSONObject().apply {
                    put("id", rule.id)
                    put("type", rule.type.name)
                    put("pattern", rule.pattern)
                    put("action", rule.action.name)
                })
            }
            put("permissionRules", rulesArray)

            val commandsArray = JSONArray()
            snapshot.customCommands.forEach { cmd ->
                commandsArray.put(JSONObject().apply {
                    put("id", cmd.id)
                    put("name", cmd.name)
                    put("prompt", cmd.prompt)
                })
            }
            put("customCommands", commandsArray)

            val skillsArray = JSONArray()
            snapshot.skills.forEach { skill ->
                skillsArray.put(JSONObject().apply {
                    put("id", skill.id)
                    put("name", skill.name)
                    put("instructions", skill.instructions)
                    put("isBuiltIn", skill.isBuiltIn)
                })
            }
            put("skills", skillsArray)

            val providersArray = JSONArray()
            snapshot.providers.forEach { provider ->
                providersArray.put(JSONObject().apply {
                    put("id", provider.id)
                    put("name", provider.name)
                    put("subtitleLabel", provider.subtitleLabel)
                    put("baseUrl", provider.baseUrl)
                    put("apiStyle", provider.apiStyle.name)
                    put("isCustom", provider.isCustom)
                    put("isEnabled", provider.isEnabled)
                    put("activeKeyId", provider.activeKeyId ?: JSONObject.NULL)

                    val keysArray = JSONArray()
                    provider.keys.forEach { key ->
                        keysArray.put(JSONObject().apply {
                            put("id", key.id)
                            put("label", key.label)
                            put("value", key.value)
                        })
                    }
                    put("keys", keysArray)

                    put("customModelIds", JSONArray(provider.customModelIds))
                    put("fetchedModels", JSONArray(provider.fetchedModels))
                })
            }
            put("providers", providersArray)
        }
        return if (pretty) root.toString(2) else root.toString()
    }

    fun decodeSnapshot(
        jsonString: String,
        todayDate: String = currentDateString()
    ): ProviderVaultSnapshot {
        val root = JSONObject(jsonString)
        val selectedModel = root.optString("selectedModel", "")
        val autoSwitchEnabled = root.optBoolean("autoSwitchEnabled", true)
        val defaultReasoningEffort = runCatching {
            ReasoningEffort.valueOf(root.optString("defaultReasoningEffort", ReasoningEffort.AUTO.name))
        }.getOrDefault(ReasoningEffort.AUTO)
        val autoApproveAllPermissions = root.optBoolean("autoApproveAllPermissions", false)
        val voiceAnnouncementEnabled = root.optBoolean("voiceAnnouncementEnabled", false)
        val themeMode = runCatching {
            AppThemeMode.valueOf(root.optString("themeMode", AppThemeMode.SYSTEM.name))
        }.getOrDefault(AppThemeMode.SYSTEM)
        val dynamicColorEnabled = root.optBoolean("dynamicColorEnabled", false)
        val showVio = root.optBoolean("showVio", true)
        val vioIntroShown = root.optBoolean("vioIntroShown", false)
        val vioSideRight = root.optBoolean("vioSideRight", true)
        val rawYFraction = root.optDouble("vioYFraction", 0.72).toFloat()
        val vioYFraction = if (rawYFraction.isNaN()) 0.72f else rawYFraction.coerceIn(0.05f, 0.92f)

        val fallbackOrder = mutableListOf<FallbackEntry>()
        val fallbackArray = root.optJSONArray("fallbackOrder")
        if (fallbackArray != null) {
            for (i in 0 until fallbackArray.length()) {
                val obj = fallbackArray.optJSONObject(i) ?: continue
                val pId = obj.optString("providerId").trim()
                val model = obj.optString("model").trim()
                if (pId.isNotEmpty() && model.isNotEmpty()) {
                    fallbackOrder.add(
                        FallbackEntry(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            providerId = pId,
                            model = model
                        )
                    )
                }
            }
        }

        val limitFlags = mutableListOf<LimitFlagEntry>()
        val flagsArray = root.optJSONArray("limitFlags")
        if (flagsArray != null) {
            for (i in 0 until flagsArray.length()) {
                val obj = flagsArray.optJSONObject(i) ?: continue
                val pId = obj.optString("providerId").trim()
                val keyId = obj.optString("keyId", "")
                val flaggedDate = obj.optString("flaggedDate").trim()
                // Flags automatically clear the next day
                if (pId.isNotEmpty() && flaggedDate == todayDate) {
                    limitFlags.add(LimitFlagEntry(providerId = pId, keyId = keyId, flaggedDate = flaggedDate))
                }
            }
        }

        val mcpServers = mutableListOf<McpServerConfig>()
        val mcpArray = root.optJSONArray("mcpServers")
        if (mcpArray != null) {
            for (i in 0 until mcpArray.length()) {
                val obj = mcpArray.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                val url = obj.optString("url").trim()
                if (name.isEmpty() || url.isEmpty()) continue
                val tools = mutableListOf<McpToolInfo>()
                val toolsArr = obj.optJSONArray("tools")
                if (toolsArr != null) {
                    for (t in 0 until toolsArr.length()) {
                        val tObj = toolsArr.optJSONObject(t) ?: continue
                        val tName = tObj.optString("name").trim()
                        if (tName.isNotEmpty()) {
                            tools.add(
                                McpToolInfo(
                                    name = tName,
                                    description = tObj.optString("description", ""),
                                    inputSchemaJson = tObj.optString(
                                        "inputSchemaJson",
                                        """{"type":"object","properties":{}}"""
                                    )
                                )
                            )
                        }
                    }
                }
                mcpServers.add(
                    McpServerConfig(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        url = url,
                        isConnected = obj.optBoolean("isConnected", false),
                        tools = tools,
                        sessionId = if (obj.isNull("sessionId")) null else obj.optString("sessionId"),
                        lastTestStatus = if (obj.isNull("lastTestStatus")) null else obj.optString("lastTestStatus"),
                        lastTestError = if (obj.isNull("lastTestError")) null else obj.optString("lastTestError")
                    )
                )
            }
        }

        val permissionRules = mutableListOf<PermissionRule>()
        val rulesArray = root.optJSONArray("permissionRules")
        if (rulesArray != null) {
            for (i in 0 until rulesArray.length()) {
                val obj = rulesArray.optJSONObject(i) ?: continue
                val pattern = obj.optString("pattern").trim()
                if (pattern.isEmpty()) continue
                val type = runCatching { PermissionRuleType.valueOf(obj.optString("type")) }
                    .getOrDefault(PermissionRuleType.FILE)
                val action = runCatching { PermissionRuleAction.valueOf(obj.optString("action")) }
                    .getOrDefault(PermissionRuleAction.ASK)
                permissionRules.add(
                    PermissionRule(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = type,
                        pattern = pattern,
                        action = action
                    )
                )
            }
        }

        val customCommands = mutableListOf<CustomCommand>()
        val commandsArray = root.optJSONArray("customCommands")
        if (commandsArray != null) {
            for (i in 0 until commandsArray.length()) {
                val obj = commandsArray.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim().removePrefix("/")
                val prompt = obj.optString("prompt")
                if (name.isEmpty() || prompt.isBlank()) continue
                customCommands.add(
                    CustomCommand(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        prompt = prompt
                    )
                )
            }
        }

        val hasSkillsInitialized = root.optBoolean("skillsInitialized", false)
        val decodedSkills = mutableListOf<AgentSkill>()
        if (hasSkillsInitialized) {
            val skillsArray = root.optJSONArray("skills")
            if (skillsArray != null) {
                for (i in 0 until skillsArray.length()) {
                    val obj = skillsArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val instructions = obj.optString("instructions")
                    if (name.isEmpty() || instructions.isBlank()) continue
                    decodedSkills.add(
                        AgentSkill(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = name,
                            instructions = instructions,
                            isBuiltIn = obj.optBoolean("isBuiltIn", false)
                        )
                    )
                }
            }
        } else {
            decodedSkills.addAll(BuiltInSkills.defaults)
        }

        val providersArray = root.optJSONArray("providers") ?: error("Missing 'providers' array in JSON")
        val providers = mutableListOf<ProviderConfig>()
        var sawEnabled = false

        for (i in 0 until providersArray.length()) {
            val obj = providersArray.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val baseUrl = obj.optString("baseUrl").trim()
            if (name.isEmpty() || baseUrl.isEmpty()) continue

            val styleName = obj.optString("apiStyle", ProviderApiStyle.OPENAI_COMPATIBLE.name)
            val apiStyle = runCatching { ProviderApiStyle.valueOf(styleName) }
                .getOrDefault(ProviderApiStyle.OPENAI_COMPATIBLE)

            val keys = mutableListOf<ProviderKeyEntry>()
            val keysArray = obj.optJSONArray("keys")
            if (keysArray != null) {
                for (k in 0 until keysArray.length()) {
                    val kObj = keysArray.optJSONObject(k) ?: continue
                    val value = kObj.optString("value")
                    if (value.isNotBlank()) {
                        keys.add(
                            ProviderKeyEntry(
                                id = kObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                                label = kObj.optString("label").ifBlank { "Key ${k + 1}" },
                                value = value
                            )
                        )
                    }
                }
            }

            val requestedEnabled = obj.optBoolean("isEnabled", false)
            val effectiveEnabled = requestedEnabled && !sawEnabled
            if (effectiveEnabled) sawEnabled = true

            val rawActiveKeyId = if (obj.isNull("activeKeyId")) null else obj.optString("activeKeyId")
            val validActiveKeyId = keys.firstOrNull { it.id == rawActiveKeyId }?.id ?: keys.firstOrNull()?.id

            providers.add(
                ProviderConfig(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    subtitleLabel = obj.optString("subtitleLabel").ifBlank {
                        if (obj.optBoolean("isCustom", false)) "Custom" else name
                    },
                    baseUrl = baseUrl,
                    apiStyle = apiStyle,
                    isCustom = obj.optBoolean("isCustom", false),
                    isEnabled = effectiveEnabled,
                    keys = keys,
                    activeKeyId = validActiveKeyId,
                    customModelIds = jsonArrayToStrings(obj.optJSONArray("customModelIds")),
                    fetchedModels = jsonArrayToStrings(obj.optJSONArray("fetchedModels"))
                )
            )
        }

        return ProviderVaultSnapshot(
            providers = providers,
            selectedModel = selectedModel,
            autoSwitchEnabled = autoSwitchEnabled,
            fallbackOrder = fallbackOrder,
            limitFlags = limitFlags.distinctBy { it.providerId to it.keyId },
            defaultReasoningEffort = defaultReasoningEffort,
            autoApproveAllPermissions = autoApproveAllPermissions,
            mcpServers = mcpServers,
            permissionRules = permissionRules,
            customCommands = customCommands,
            skills = decodedSkills,
            skillsInitialized = true,
            voiceAnnouncementEnabled = voiceAnnouncementEnabled,
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColorEnabled,
            showVio = showVio,
            vioIntroShown = vioIntroShown,
            vioSideRight = vioSideRight,
            vioYFraction = vioYFraction
        )
    }

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotEmpty()) list.add(value)
        }
        return list.distinct()
    }
}
