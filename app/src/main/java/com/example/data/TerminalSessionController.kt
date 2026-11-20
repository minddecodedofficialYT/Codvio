package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class TerminalUiState(
    val isRunning: Boolean = false,
    val workingDirLabel: String = "/root",
    val lines: List<String> = emptyList(),
    val startupError: String? = null
)

class TerminalSessionController(
    private val envManager: TerminalEnvironmentManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TerminalUiState())
    val state: StateFlow<TerminalUiState> = _state.asStateFlow()

    private var process: Process? = null
    private var stdinStream: BufferedOutputStream? = null
    private var readerJob: Job? = null
    private var waitJob: Job? = null

    private val history = mutableListOf<String>()
    private var historyIndex: Int = -1
    private var currentBoundProjectDir: File? = null

    fun startIfNeeded(projectDir: File?) {
        if (process != null && _state.value.isRunning) return
        restart(projectDir)
    }

    fun restart(projectDir: File?) {
        stopShell()
        currentBoundProjectDir = projectDir?.takeIf { it.exists() && it.isDirectory && it.canRead() }
        if (!envManager.isInstalled()) {
            _state.value = TerminalUiState(
                isRunning = false,
                startupError = "Terminal environment is not installed."
            )
            return
        }

        val startDir = if (currentBoundProjectDir != null) "/workspace" else "/root"
        val initialLines = mutableListOf<String>()
        if (currentBoundProjectDir == null) {
            initialLines += "Note: Project folder cannot be bound directly (for example SAF URI); started in /root."
        } else {
            initialLines += "Workspace mounted at /workspace (${currentBoundProjectDir!!.name})."
        }

        val cmd = buildProotCommand(
            prootPath = envManager.prootBinary.absolutePath,
            rootfsPath = envManager.rootfsDir.absolutePath,
            projectDir = currentBoundProjectDir,
            startDir = startDir
        )

        val pb = ProcessBuilder(cmd).apply {
            redirectErrorStream(true)
            val env = environment()
            env["HOME"] = "/root"
            env["TERM"] = "xterm-256color"
            env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            env["PROOT_TMP_DIR"] = envManager.tmpDir.apply { mkdirs() }.absolutePath
            env["PROOT_NO_SECCOMP"] = "1"
            env["LANG"] = "C.UTF-8"
        }

        try {
            val started = pb.start()
            process = started
            stdinStream = BufferedOutputStream(started.outputStream)
            _state.value = TerminalUiState(
                isRunning = true,
                workingDirLabel = startDir,
                lines = initialLines,
                startupError = null
            )

            readerJob = scope.launch {
                streamProcessOutput(started.inputStream)
            }
            waitJob = scope.launch {
                val exitCode = runCatching { started.waitFor() }.getOrDefault(-1)
                appendOutput("\n[Shell exited with code $exitCode]\n")
                _state.update { it.copy(isRunning = false) }
            }
        } catch (t: Throwable) {
            val message = t.message ?: t.javaClass.simpleName
            _state.value = TerminalUiState(
                isRunning = false,
                workingDirLabel = startDir,
                lines = initialLines + "Failed to start PRoot/Alpine shell: $message",
                startupError = message
            )
        }
    }

    fun sendCommandLine(rawCommand: String) {
        val trimmed = rawCommand.trimEnd('\r', '\n')
        if (trimmed.isNotBlank()) {
            if (history.lastOrNull() != trimmed) {
                history.add(trimmed)
                if (history.size > 200) history.removeAt(0)
            }
        }
        historyIndex = -1
        appendOutput("${_state.value.workingDirLabel} $ $trimmed\n")
        writeRawBytes((trimmed + "\n").toByteArray(StandardCharsets.UTF_8))
    }

    fun sendControlChar(letter: Char) {
        val upper = letter.uppercaseChar()
        if (upper in 'A'..'Z') {
            val code = (upper.code - 'A'.code) + 1
            if (upper == 'L') {
                clearScreen()
                return
            }
            writeRawBytes(byteArrayOf(code.toByte()))
            if (upper == 'C') {
                appendOutput("^C\n")
            } else if (upper == 'D') {
                appendOutput("^D\n")
            }
        }
    }

    fun sendRawEscapeSequence(seq: String) {
        writeRawBytes(seq.toByteArray(StandardCharsets.UTF_8))
    }

    fun historyPrevious(): String? {
        if (history.isEmpty()) return null
        historyIndex = if (historyIndex < 0) history.lastIndex else (historyIndex - 1).coerceAtLeast(0)
        return history[historyIndex]
    }

    fun historyNext(): String? {
        if (history.isEmpty() || historyIndex < 0) return ""
        return if (historyIndex >= history.lastIndex) {
            historyIndex = -1
            ""
        } else {
            historyIndex++
            history[historyIndex]
        }
    }

    fun clearScreen() {
        _state.update { it.copy(lines = emptyList()) }
    }

    fun stopShell() {
        readerJob?.cancel()
        readerJob = null
        waitJob?.cancel()
        waitJob = null
        runCatching { stdinStream?.close() }
        stdinStream = null
        runCatching { process?.destroy() }
        process = null
        _state.update { it.copy(isRunning = false) }
    }

    fun dispose() {
        stopShell()
        scope.cancel()
    }

    private fun writeRawBytes(bytes: ByteArray) {
        val stream = stdinStream
        if (stream == null || !_state.value.isRunning) {
            appendOutput("[Shell is not running. Tap Restart to launch a new shell.]\n")
            return
        }
        scope.launch {
            runCatching {
                stream.write(bytes)
                stream.flush()
            }.onFailure { err ->
                appendOutput("[Write error: ${err.message ?: "stream closed"}]\n")
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    private suspend fun streamProcessOutput(input: InputStream) = withContext(Dispatchers.IO) {
        val buf = ByteArray(4096)
        while (true) {
            val n = runCatching { input.read(buf) }.getOrDefault(-1)
            if (n <= 0) break
            val chunk = String(buf, 0, n, StandardCharsets.UTF_8)
            val cleaned = ANSI_ESCAPE_REGEX.replace(chunk, "")
            if (cleaned.isNotEmpty()) {
                appendOutput(cleaned)
            }
        }
    }

    private fun appendOutput(text: String) {
        _state.update { cur ->
            val currentLines = cur.lines.toMutableList()
            val segments = text.replace("\r\n", "\n").split('\n')
            if (currentLines.isEmpty()) {
                segments.filterIndexed { idx, s -> idx < segments.lastIndex || s.isNotEmpty() }
                    .forEach { currentLines.add(it) }
            } else {
                segments.forEachIndexed { idx, part ->
                    if (idx == 0) {
                        val last = currentLines.removeAt(currentLines.lastIndex)
                        currentLines.add(last + part)
                    } else if (idx < segments.lastIndex || part.isNotEmpty()) {
                        currentLines.add(part)
                    }
                }
            }
            val capped = if (currentLines.size > MAX_TERMINAL_LINES) {
                currentLines.takeLast(MAX_TERMINAL_LINES)
            } else {
                currentLines
            }
            cur.copy(lines = capped)
        }
    }

    companion object {
        private const val MAX_TERMINAL_LINES = 600
        private val ANSI_ESCAPE_REGEX = Regex("\\u001B\\[[0-9;?]*[a-zA-Z]")

        internal fun buildProotCommand(
            prootPath: String,
            rootfsPath: String,
            projectDir: File?,
            startDir: String
        ): List<String> {
            return buildList {
                add(prootPath)
                add("-0")
                add("--link2symlink")
                add("-r")
                add(rootfsPath)
                add("-b")
                add("/dev")
                add("-b")
                add("/proc")
                add("-b")
                add("/sys")
                if (projectDir != null) {
                    add("-b")
                    add("${projectDir.absolutePath}:/workspace")
                }
                add("-w")
                add(startDir)
                add("/bin/sh")
            }
        }
    }
}
