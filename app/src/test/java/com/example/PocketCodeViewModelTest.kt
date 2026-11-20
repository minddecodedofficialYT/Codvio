package com.example

import com.example.data.ProviderJsonCodec
import com.example.data.ProviderNetworkClient
import com.example.data.ProviderVaultSnapshot
import com.example.model.AgentConnectionMode
import com.example.model.AgentWorkMode
import com.example.model.ProviderApiStyle
import com.example.model.ProviderPresets
import com.example.viewmodel.PocketCodeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PocketCodeViewModelTest {

    @Test
    fun `creating session derives project name from folder when name is blank`() {
        val viewModel = PocketCodeViewModel()
        val session = viewModel.createSession(
            projectNameInput = "",
            projectFolderInput = "/storage/emulated/0/Projects/MyApp",
            folderUri = null
        )

        assertEquals("MyApp", session.projectName)
        assertEquals("/storage/emulated/0/Projects/MyApp", session.projectFolder)
        assertEquals(1, viewModel.uiState.value.sessions.size)
    }

    @Test
    fun `connect remote validates scheme and requires real server response`() {
        val viewModel = PocketCodeViewModel()
        var succeeded = false
        viewModel.updateServerUrl("invalid-url")
        viewModel.connectRemoteServer { succeeded = true }
        assertFalse(succeeded)
        assertTrue(viewModel.uiState.value.serverUrlError != null)

        val serverSocket = java.net.ServerSocket(0)
        val port = serverSocket.localPort
        val thread = kotlin.concurrent.thread {
            repeat(2) {
                val socket = serverSocket.accept()
                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val body = """{"status":"ok"}"""
                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                    s.getOutputStream().write(resp.toByteArray())
                    s.getOutputStream().flush()
                }
            }
            serverSocket.close()
        }

        viewModel.updateServerUrl("http://127.0.0.1:$port")
        viewModel.connectRemoteServer { succeeded = true }
        val deadline = System.currentTimeMillis() + 5000
        while (!succeeded && System.currentTimeMillis() < deadline) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        thread.join(2000)

        assertTrue(succeeded)
        assertEquals("http://127.0.0.1:$port", viewModel.uiState.value.serverUrl)
        assertTrue(viewModel.uiState.value.isRemoteConnected)
        assertEquals(AgentConnectionMode.REMOTE_SERVER, viewModel.uiState.value.activeConnectionMode)
    }

    @Test
    fun `work mode and model selection update state`() {
        val viewModel = PocketCodeViewModel()
        assertEquals(AgentWorkMode.BUILD, viewModel.uiState.value.selectedWorkMode)

        viewModel.selectWorkMode(AgentWorkMode.PLAN)
        viewModel.selectModel("google/gemini-2.5-pro")

        assertEquals(AgentWorkMode.PLAN, viewModel.uiState.value.selectedWorkMode)
        assertEquals("google/gemini-2.5-pro", viewModel.uiState.value.selectedModel)
    }

    @Test
    fun `enabling one provider disables others while keeping keys saved`() {
        val viewModel = PocketCodeViewModel()
        val openai = ProviderPresets.officialPresets.first { it.id == "openai" }
        val anthropic = ProviderPresets.officialPresets.first { it.id == "anthropic" }

        viewModel.saveProvider(
            editingProviderId = null,
            name = openai.name,
            subtitleLabel = openai.name,
            baseUrl = openai.baseUrl,
            apiStyle = openai.apiStyle,
            isCustom = false,
            apiKeyInput = "sk-openai-1",
            fetchedModels = listOf("gpt-4o", "gpt-4o-mini")
        )
        viewModel.saveProvider(
            editingProviderId = null,
            name = anthropic.name,
            subtitleLabel = anthropic.name,
            baseUrl = anthropic.baseUrl,
            apiStyle = anthropic.apiStyle,
            isCustom = false,
            apiKeyInput = "sk-ant-1",
            fetchedModels = listOf("claude-sonnet-4")
        )

        val providersAfterAdd = viewModel.uiState.value.providers
        assertEquals(2, providersAfterAdd.size)
        assertTrue(providersAfterAdd[0].isEnabled)
        assertFalse(providersAfterAdd[1].isEnabled)

        // Switch second provider ON -> first turns OFF, key stays saved
        viewModel.setProviderEnabled(providersAfterAdd[1].id, true)
        val updated = viewModel.uiState.value.providers
        assertFalse(updated[0].isEnabled)
        assertEquals("sk-openai-1", updated[0].activeApiKey)
        assertTrue(updated[1].isEnabled)
        assertEquals("Anthropic", viewModel.uiState.value.activeProvider?.name)
    }

    @Test
    fun `multi-key management adds switches and deletes keys per provider`() {
        val viewModel = PocketCodeViewModel()
        viewModel.saveProvider(
            editingProviderId = null,
            name = "Ollama",
            subtitleLabel = "Custom",
            baseUrl = "http://127.0.0.1:11434/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = true,
            apiKeyInput = "",
            customModelIds = listOf("qwen2.5-coder:7b")
        )

        val providerId = viewModel.uiState.value.providers.first().id
        viewModel.addProviderKey(providerId, "Primary", "key-one")
        viewModel.addProviderKey(providerId, "Backup", "key-two")

        val keys = viewModel.uiState.value.providers.first().keys
        assertEquals(2, keys.size)
        assertEquals("key-one", viewModel.uiState.value.providers.first().activeApiKey)

        viewModel.selectActiveProviderKey(providerId, keys[1].id)
        assertEquals("key-two", viewModel.uiState.value.providers.first().activeApiKey)

        viewModel.deleteProviderKey(providerId, keys[0].id)
        assertEquals(1, viewModel.uiState.value.providers.first().keys.size)
    }

    @Test
    fun `json export and import round-trip preserves providers keys and selected model`() {
        val viewModel = PocketCodeViewModel()
        viewModel.saveProvider(
            editingProviderId = null,
            name = "Groq",
            subtitleLabel = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiStyle = ProviderApiStyle.OPENAI_COMPATIBLE,
            isCustom = false,
            apiKeyInput = "gsk_123456789",
            fetchedModels = listOf("llama-3.3-70b-versatile")
        )
        val id = viewModel.uiState.value.providers.first().id
        viewModel.addProviderKey(id, "Secondary", "gsk_987654321")
        viewModel.selectModel("llama-3.3-70b-versatile")

        val snapshot = ProviderVaultSnapshot(
            providers = viewModel.uiState.value.providers,
            selectedModel = viewModel.uiState.value.selectedModel
        )
        val json = ProviderJsonCodec.encodeSnapshot(snapshot, pretty = true)
        val decoded = ProviderJsonCodec.decodeSnapshot(json)

        assertEquals(1, decoded.providers.size)
        assertEquals("llama-3.3-70b-versatile", decoded.selectedModel)
        assertEquals(2, decoded.providers.first().keys.size)
        assertEquals("gsk_123456789", decoded.providers.first().activeApiKey)
        assertEquals(listOf("llama-3.3-70b-versatile"), decoded.providers.first().fetchedModels)
    }

    @Test
    fun `network client parses OpenAI and Gemini models responses and HTTP errors`() {
        val client = ProviderNetworkClient()

        val openAiJson = """{"data":[{"id":"gpt-4o"},{"id":"o3-mini"}]}"""
        assertEquals(
            listOf("gpt-4o", "o3-mini"),
            client.parseModelsResponse(openAiJson, ProviderApiStyle.OPENAI_COMPATIBLE)
        )

        val geminiJson = """
            {
              "models": [
                {"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]}
              ]
            }
        """.trimIndent()
        assertEquals(
            listOf("gemini-2.5-flash"),
            client.parseModelsResponse(geminiJson, ProviderApiStyle.GEMINI)
        )

        val errJson = """{"error":{"message":"Incorrect API key provided"}}"""
        assertEquals(
            "HTTP 401: Incorrect API key provided",
            client.formatHttpError(401, "Unauthorized", errJson)
        )
        assertNull(ProviderPresets.customEndpointPresets.first().baseUrl.takeIf { it.isBlank() })
    }
}
