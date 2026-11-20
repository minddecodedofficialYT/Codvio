package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.AgentSkill
import com.example.model.CustomCommand
import com.example.model.PermissionRule
import com.example.model.ProjectType
import com.example.model.SessionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class FileMutationResult(
    val success: Boolean,
    val summary: String,
    val detail: String,
    val existedBefore: Boolean = true,
    val previousContent: String? = null
)

data class CommandRunResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String
)

class ProjectWorkspace private constructor(
    private val context: Context,
    val rootDisplayPath: String,
    private val fileRoot: File?,
    private val safTreeUri: Uri?
) {
    val directDirectory: File?
        get() = fileRoot?.takeIf { it.exists() && it.isDirectory && it.canRead() }

    suspend fun detectProjectInfo(): Pair<ProjectType, Boolean> = withContext(Dispatchers.IO) {
        val topEntries = listTopLevelNames()
        val hasIndexHtml = topEntries.any { it.equals("index.html", ignoreCase = true) }
        val isGradleAndroid = topEntries.any {
            it.equals("build.gradle.kts", ignoreCase = true) ||
                it.equals("build.gradle", ignoreCase = true) ||
                it.equals("settings.gradle.kts", ignoreCase = true) ||
                it.equals("settings.gradle", ignoreCase = true)
        }
        val projectType = when {
            hasIndexHtml -> ProjectType.WEB
            isGradleAndroid -> ProjectType.NATIVE_ANDROID
            else -> ProjectType.GENERAL
        }
        projectType to hasIndexHtml
    }

    suspend fun hasIndexHtml(): Boolean = withContext(Dispatchers.IO) {
        listTopLevelNames().any { it.equals("index.html", ignoreCase = true) }
    }

    suspend fun listFiles(relativePath: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRel = sanitizeRelativePath(relativePath)
                ?: throw SecurityException("Access denied: path '$relativePath' is outside the project folder.")

            if (fileRoot != null) {
                val targetDir = resolveSandboxedFile(fileRoot, cleanRel)
                if (!targetDir.exists()) error("Directory not found: ${cleanRel.ifEmpty { "." }}")
                if (!targetDir.isDirectory) error("Not a directory: $cleanRel")

                val results = mutableListOf<String>()
                collectFilesRecursive(fileRoot, targetDir, 0, MAX_LIST_DEPTH, results)
                results
            } else if (safTreeUri != null) {
                val targetDocId = resolveSafDocumentId(safTreeUri, cleanRel)
                    ?: error("Directory not found: ${cleanRel.ifEmpty { "." }}")
                val results = mutableListOf<String>()
                collectSafRecursive(safTreeUri, targetDocId, cleanRel, 0, MAX_LIST_DEPTH, results)
                results
            } else {
                error("No workspace available")
            }
        }
    }

    suspend fun readFile(relativePath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRel = sanitizeRelativePath(relativePath)
                ?: throw SecurityException("Access denied: path '$relativePath' is outside the project folder.")
            if (cleanRel.isEmpty()) error("Provide a file path to read.")

            if (fileRoot != null) {
                val target = resolveSandboxedFile(fileRoot, cleanRel)
                if (!target.exists() || !target.isFile) error("File not found: $cleanRel")
                if (target.length() > MAX_READ_BYTES) {
                    val bytes = target.inputStream().use { it.readNBytes(MAX_READ_BYTES) }
                    String(bytes, StandardCharsets.UTF_8) + "\n...[truncated after $MAX_READ_BYTES bytes]"
                } else {
                    target.readText(StandardCharsets.UTF_8)
                }
            } else if (safTreeUri != null) {
                val docId = resolveSafDocumentId(safTreeUri, cleanRel)
                    ?: error("File not found: $cleanRel")
                val docUri = DocumentsContract.buildDocumentUriUsingTree(safTreeUri, docId)
                context.contentResolver.openInputStream(docUri)?.use { input ->
                    val bytes = input.readNBytes(MAX_READ_BYTES + 1)
                    if (bytes.size > MAX_READ_BYTES) {
                        String(bytes, 0, MAX_READ_BYTES, StandardCharsets.UTF_8) +
                            "\n...[truncated after $MAX_READ_BYTES bytes]"
                    } else {
                        String(bytes, StandardCharsets.UTF_8)
                    }
                } ?: error("Unable to open file: $cleanRel")
            } else {
                error("No workspace available")
            }
        }
    }

    suspend fun readBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val cleanRel = sanitizeRelativePath(relativePath) ?: return@withContext null
        if (cleanRel.isEmpty()) return@withContext null

        return@withContext runCatching {
            if (fileRoot != null) {
                val target = resolveSandboxedFile(fileRoot, cleanRel)
                if (target.exists() && target.isFile) target.readBytes() else null
            } else if (safTreeUri != null) {
                val docId = resolveSafDocumentId(safTreeUri, cleanRel) ?: return@runCatching null
                val docUri = DocumentsContract.buildDocumentUriUsingTree(safTreeUri, docId)
                context.contentResolver.openInputStream(docUri)?.use { it.readBytes() }
            } else {
                null
            }
        }.getOrNull()
    }

    fun resolveIndexFileForWebView(): File? {
        val root = fileRoot ?: return null
        val index = File(root, "index.html")
        return if (index.exists() && index.isFile) index else null
    }

    suspend fun searchText(query: String, relativePath: String = ""): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (query.isBlank()) error("Search query cannot be empty.")
                val files = listFiles(relativePath).getOrThrow()
                    .filterNot { it.endsWith("/") }
                    .take(150)

                val matches = mutableListOf<String>()
                for (filePath in files) {
                    if (matches.size >= 80) break
                    val text = readFile(filePath).getOrNull() ?: continue
                    if (text.indexOf('\u0000') >= 0) continue // Skip binary files
                    val lines = text.lines()
                    lines.forEachIndexed { index, line ->
                        if (matches.size < 80 && line.contains(query, ignoreCase = true)) {
                            matches.add("$filePath:${index + 1}: ${line.trim().take(160)}")
                        }
                    }
                }
                matches
            }
        }

    suspend fun createFile(relativePath: String, content: String): FileMutationResult =
        withContext(Dispatchers.IO) {
            val cleanRel = sanitizeRelativePath(relativePath)
                ?: return@withContext FileMutationResult(
                    success = false,
                    summary = "Access denied",
                    detail = "Path '$relativePath' is outside the project folder."
                )
            if (cleanRel.isEmpty()) {
                return@withContext FileMutationResult(false, "Invalid path", "File path cannot be empty.")
            }

            try {
                if (fileRoot != null) {
                    val target = resolveSandboxedFile(fileRoot, cleanRel)
                    val existed = target.exists()
                    val previous = if (existed && target.isFile) target.readText(StandardCharsets.UTF_8) else null
                    target.parentFile?.mkdirs()
                    target.writeText(content, StandardCharsets.UTF_8)
                    FileMutationResult(
                        success = true,
                        summary = if (existed) "Overwrote $cleanRel (${content.lines().size} lines)" else "Created $cleanRel (${content.lines().size} lines)",
                        detail = "Saved ${content.length} chars to $cleanRel",
                        existedBefore = existed,
                        previousContent = previous
                    )
                } else if (safTreeUri != null) {
                    val existingDocId = resolveSafDocumentId(safTreeUri, cleanRel)
                    val previous = if (existingDocId != null) readFile(cleanRel).getOrNull() else null
                    val docUri = if (existingDocId != null) {
                        DocumentsContract.buildDocumentUriUsingTree(safTreeUri, existingDocId)
                    } else {
                        createSafFile(safTreeUri, cleanRel)
                    }
                    context.contentResolver.openOutputStream(docUri, "wt")?.use { out ->
                        out.write(content.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("Unable to write to $cleanRel")
                    FileMutationResult(
                        success = true,
                        summary = "Created $cleanRel (${content.lines().size} lines)",
                        detail = "Saved ${content.length} chars to $cleanRel",
                        existedBefore = existingDocId != null,
                        previousContent = previous
                    )
                } else {
                    FileMutationResult(false, "No workspace", "No writable project folder available.")
                }
            } catch (e: Exception) {
                FileMutationResult(false, "Create failed", e.message ?: "Unable to create $cleanRel")
            }
        }

    suspend fun editFile(
        relativePath: String,
        oldText: String?,
        newText: String?,
        fullContent: String?
    ): FileMutationResult = withContext(Dispatchers.IO) {
        val cleanRel = sanitizeRelativePath(relativePath)
            ?: return@withContext FileMutationResult(
                success = false,
                summary = "Access denied",
                detail = "Path '$relativePath' is outside the project folder."
            )
        if (cleanRel.isEmpty()) {
            return@withContext FileMutationResult(false, "Invalid path", "File path cannot be empty.")
        }

        try {
            val previous = readFile(cleanRel).getOrNull()
                ?: return@withContext FileMutationResult(false, "File not found", "Cannot edit missing file: $cleanRel")

            val updatedContent = when {
                !oldText.isNullOrEmpty() && newText != null -> {
                    if (!previous.contains(oldText)) {
                        return@withContext FileMutationResult(
                            success = false,
                            summary = "Patch target not found",
                            detail = "Could not find matching text block in $cleanRel."
                        )
                    }
                    previous.replaceFirst(oldText, newText)
                }
                fullContent != null -> fullContent
                newText != null -> newText
                else -> return@withContext FileMutationResult(
                    success = false,
                    summary = "Missing edit content",
                    detail = "Provide either old_text + new_text or content."
                )
            }

            if (fileRoot != null) {
                val target = resolveSandboxedFile(fileRoot, cleanRel)
                target.writeText(updatedContent, StandardCharsets.UTF_8)
            } else if (safTreeUri != null) {
                val docId = resolveSafDocumentId(safTreeUri, cleanRel)
                    ?: error("File not found: $cleanRel")
                val docUri = DocumentsContract.buildDocumentUriUsingTree(safTreeUri, docId)
                context.contentResolver.openOutputStream(docUri, "wt")?.use { out ->
                    out.write(updatedContent.toByteArray(StandardCharsets.UTF_8))
                } ?: error("Unable to open $cleanRel for writing")
            }

            FileMutationResult(
                success = true,
                summary = "Edited $cleanRel",
                detail = "Updated $cleanRel (${updatedContent.lines().size} lines)",
                existedBefore = true,
                previousContent = previous
            )
        } catch (e: Exception) {
            FileMutationResult(false, "Edit failed", e.message ?: "Unable to edit $cleanRel")
        }
    }

    suspend fun undoFileMutation(
        relativePath: String,
        existedBefore: Boolean,
        previousContent: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanRel = sanitizeRelativePath(relativePath)
                ?: throw SecurityException("Access denied for undo path: $relativePath")

            if (!existedBefore) {
                if (fileRoot != null) {
                    val target = resolveSandboxedFile(fileRoot, cleanRel)
                    if (target.exists()) target.delete()
                } else if (safTreeUri != null) {
                    val docId = resolveSafDocumentId(safTreeUri, cleanRel)
                    if (docId != null) {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(safTreeUri, docId)
                        DocumentsContract.deleteDocument(context.contentResolver, docUri)
                    }
                }
                "Removed created file $cleanRel"
            } else {
                val restoreText = previousContent ?: ""
                if (fileRoot != null) {
                    val target = resolveSandboxedFile(fileRoot, cleanRel)
                    target.parentFile?.mkdirs()
                    target.writeText(restoreText, StandardCharsets.UTF_8)
                } else if (safTreeUri != null) {
                    val docId = resolveSafDocumentId(safTreeUri, cleanRel)
                        ?: error("File no longer exists: $cleanRel")
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(safTreeUri, docId)
                    context.contentResolver.openOutputStream(docUri, "wt")?.use { out ->
                        out.write(restoreText.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("Unable to restore $cleanRel")
                }
                "Restored previous version of $cleanRel"
            }
        }
    }

    suspend fun runCommand(command: String): CommandRunResult = withContext(Dispatchers.IO) {
        val workDir = fileRoot
        if (workDir == null || !workDir.exists() || !workDir.canRead()) {
            return@withContext CommandRunResult(
                success = false,
                exitCode = -1,
                output = "Shell commands require direct filesystem access to the project folder (grant Storage access in Setup, or use the app's private workspace)."
            )
        }

        return@withContext try {
            val shellPath = listOf("/system/bin/sh", "/bin/sh")
                .firstOrNull { File(it).canExecute() }
                ?: "sh"
            val process = ProcessBuilder(shellPath, "-c", command)
                .directory(workDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = workDir.absolutePath
                    environment()["PWD"] = workDir.absolutePath
                }
                .start()

            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                process.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(1024)
                    var read: Int
                    while (reader.read(buffer).also { read = it } != -1) {
                        if (outputBuilder.length < MAX_COMMAND_OUTPUT_CHARS) {
                            val remaining = MAX_COMMAND_OUTPUT_CHARS - outputBuilder.length
                            outputBuilder.append(buffer, 0, minOf(read, remaining))
                        }
                    }
                }
            }
            readerThread.start()

            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                readerThread.join(500)
                return@withContext CommandRunResult(
                    success = false,
                    exitCode = -1,
                    output = outputBuilder.toString() + "\n[Timed out after ${COMMAND_TIMEOUT_SECONDS}s]"
                )
            }
            readerThread.join(1000)
            val exitCode = process.exitValue()
            val text = outputBuilder.toString().ifBlank { "(no output)" }
            CommandRunResult(
                success = exitCode == 0,
                exitCode = exitCode,
                output = text
            )
        } catch (e: Exception) {
            CommandRunResult(
                success = false,
                exitCode = -1,
                output = e.message ?: "Failed to run command"
            )
        }
    }

    suspend fun readProjectPermissionRules(): List<PermissionRule> = withContext(Dispatchers.IO) {
        val raw = readFile("pocketcode.json").getOrNull() ?: return@withContext emptyList()
        PermissionRuleEvaluator.parsePocketCodeJsonRules(raw)
    }

    suspend fun readProjectCustomCommands(): List<CustomCommand> = withContext(Dispatchers.IO) {
        val entries = listFiles(".pocketcode/commands").getOrNull() ?: return@withContext emptyList()
        val commands = mutableListOf<CustomCommand>()
        for (relPath in entries) {
            if (relPath.endsWith("/") || !relPath.endsWith(".md", ignoreCase = true)) continue
            val fileName = relPath.substringAfterLast('/')
            val cmdName = fileName.substringBeforeLast('.').trim()
            if (cmdName.isEmpty()) continue
            val content = readFile(relPath).getOrNull()?.trim() ?: continue
            val stripped = CustomCommandExpander.stripMarkdownFrontmatter(content)
            if (stripped.isNotBlank()) {
                commands.add(
                    CustomCommand(
                        id = "proj_cmd_$cmdName",
                        name = cmdName,
                        prompt = stripped,
                        isFromProject = true
                    )
                )
            }
        }
        commands
    }

    suspend fun readProjectSkills(): List<AgentSkill> = withContext(Dispatchers.IO) {
        val entries = listFiles(".pocketcode/skills").getOrNull() ?: return@withContext emptyList()
        val skills = mutableListOf<AgentSkill>()
        for (relPath in entries) {
            if (!relPath.endsWith("/SKILL.md", ignoreCase = true)) continue
            val parentFolder = relPath.substringBeforeLast('/').substringAfterLast('/')
            if (parentFolder.isBlank()) continue
            val raw = readFile(relPath).getOrNull()?.trim() ?: continue
            val body = CustomCommandExpander.stripMarkdownFrontmatter(raw)
            if (body.isNotBlank()) {
                skills.add(
                    AgentSkill(
                        id = "proj_skill_$parentFolder",
                        name = parentFolder,
                        instructions = body,
                        isBuiltIn = false,
                        isFromProject = true
                    )
                )
            }
        }
        skills
    }

    private fun listTopLevelNames(): List<String> {
        return if (fileRoot != null && fileRoot.exists() && fileRoot.isDirectory) {
            fileRoot.listFiles()?.map { it.name }.orEmpty()
        } else if (safTreeUri != null) {
            val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(safTreeUri) }.getOrNull()
                ?: return emptyList()
            listSafChildren(safTreeUri, treeDocId).map { it.name }
        } else {
            emptyList()
        }
    }

    internal fun sanitizeRelativePath(rawPath: String): String? {
        var normalized = rawPath.trim().replace('\\', '/')
        if (normalized == "." || normalized == "./" || normalized.isEmpty()) return ""

        // If the model passed an absolute path inside our project root, make it relative; otherwise reject.
        if (normalized.startsWith("/")) {
            val rootCanonical = fileRoot?.canonicalPath?.trimEnd('/')
            if (rootCanonical != null && (normalized == rootCanonical || normalized.startsWith("$rootCanonical/"))) {
                normalized = normalized.removePrefix(rootCanonical).trimStart('/')
            } else if (normalized.startsWith(rootDisplayPath.trimEnd('/') + "/")) {
                normalized = normalized.removePrefix(rootDisplayPath.trimEnd('/')).trimStart('/')
            } else {
                return null
            }
        }

        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        val resolved = mutableListOf<String>()
        for (segment in segments) {
            if (segment == "..") {
                return null // Block any parent traversal
            }
            resolved.add(segment)
        }
        return resolved.joinToString("/")
    }

    private fun resolveSandboxedFile(root: File, cleanRelative: String): File {
        val rootCanonical = root.canonicalFile
        val target = if (cleanRelative.isEmpty()) rootCanonical else File(rootCanonical, cleanRelative).canonicalFile
        val rootPath = rootCanonical.path
        val targetPath = target.path
        if (targetPath != rootPath && !targetPath.startsWith(rootPath + File.separator)) {
            throw SecurityException("Access denied: path escapes project root.")
        }
        return target
    }

    private fun collectFilesRecursive(
        root: File,
        current: File,
        depth: Int,
        maxDepth: Int,
        out: MutableList<String>
    ) {
        if (out.size >= MAX_LIST_ENTRIES) return
        val children = current.listFiles()
            ?.filter { it.name != ".git" && it.name != "node_modules" && it.name != ".gradle" && it.name != "build" }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: return

        for (child in children) {
            if (out.size >= MAX_LIST_ENTRIES) break
            val rel = child.canonicalFile.relativeTo(root.canonicalFile).path.replace(File.separatorChar, '/')
            if (child.isDirectory) {
                out.add("$rel/")
                if (depth < maxDepth) {
                    collectFilesRecursive(root, child, depth + 1, maxDepth, out)
                }
            } else {
                out.add(rel)
            }
        }
    }

    private data class SafEntry(val docId: String, val name: String, val isDirectory: Boolean)

    private fun listSafChildren(treeUri: Uri, parentDocId: String): List<SafEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val entries = mutableListOf<SafEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIdx) ?: continue
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: ""
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                entries.add(SafEntry(docId, name, isDir))
            }
        }
        return entries.sortedWith(compareBy<SafEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun resolveSafDocumentId(treeUri: Uri, cleanRelative: String): String? {
        var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        if (cleanRelative.isEmpty()) return currentDocId
        val segments = cleanRelative.split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            val match = listSafChildren(treeUri, currentDocId).firstOrNull { it.name == segment }
                ?: return null
            currentDocId = match.docId
        }
        return currentDocId
    }

    private fun createSafFile(treeUri: Uri, cleanRelative: String): Uri {
        var currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val segments = cleanRelative.split('/').filter { it.isNotEmpty() }
        for (i in 0 until segments.size - 1) {
            val dirName = segments[i]
            val existing = listSafChildren(treeUri, currentDocId).firstOrNull { it.name == dirName && it.isDirectory }
            currentDocId = if (existing != null) {
                existing.docId
            } else {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    dirName
                ) ?: error("Unable to create folder $dirName")
                DocumentsContract.getDocumentId(createdUri)
            }
        }
        val fileName = segments.last()
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
        val mime = guessMimeType(fileName)
        return DocumentsContract.createDocument(context.contentResolver, parentUri, mime, fileName)
            ?: error("Unable to create file $fileName")
    }

    private fun collectSafRecursive(
        treeUri: Uri,
        parentDocId: String,
        prefix: String,
        depth: Int,
        maxDepth: Int,
        out: MutableList<String>
    ) {
        if (out.size >= MAX_LIST_ENTRIES) return
        val children = listSafChildren(treeUri, parentDocId)
            .filter { it.name != ".git" && it.name != "node_modules" && it.name != ".gradle" && it.name != "build" }

        for (child in children) {
            if (out.size >= MAX_LIST_ENTRIES) break
            val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) {
                out.add("$rel/")
                if (depth < maxDepth) {
                    collectSafRecursive(treeUri, child.docId, rel, depth + 1, maxDepth, out)
                }
            } else {
                out.add(rel)
            }
        }
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "md" -> "text/markdown"
            "kt", "kts", "java", "xml", "txt", "py", "sh" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private const val MAX_LIST_DEPTH = 4
        private const val MAX_LIST_ENTRIES = 250
        private const val MAX_READ_BYTES = 64 * 1024
        private const val MAX_COMMAND_OUTPUT_CHARS = 32_000
        private const val COMMAND_TIMEOUT_SECONDS = 30L

        fun forSession(context: Context, session: SessionItem): ProjectWorkspace {
            val folderPath = session.projectFolder.trim()
            val treeUri = session.folderUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

            if (folderPath.isBlank() && treeUri == null) {
                val privateDir = File(context.filesDir, "workspaces/${session.id}").apply { mkdirs() }
                return ProjectWorkspace(
                    context = context,
                    rootDisplayPath = privateDir.absolutePath,
                    fileRoot = privateDir,
                    safTreeUri = null
                )
            }

            val directFile = folderPath.takeIf { it.isNotBlank() }?.let { File(it) }
            val canUseDirectFile = directFile != null &&
                directFile.exists() &&
                directFile.isDirectory &&
                directFile.canRead() &&
                directFile.canWrite()

            return if (canUseDirectFile) {
                ProjectWorkspace(
                    context = context,
                    rootDisplayPath = directFile!!.absolutePath,
                    fileRoot = directFile,
                    safTreeUri = treeUri
                )
            } else if (treeUri != null) {
                ProjectWorkspace(
                    context = context,
                    rootDisplayPath = folderPath.ifBlank { treeUri.toString() },
                    fileRoot = null,
                    safTreeUri = treeUri
                )
            } else {
                // Typed path without SAF URI; attempt to use/create if accessible, else fallback cleanly
                val target = directFile ?: File(context.filesDir, "workspaces/${session.id}")
                if (!target.exists()) runCatching { target.mkdirs() }
                ProjectWorkspace(
                    context = context,
                    rootDisplayPath = target.absolutePath,
                    fileRoot = target,
                    safTreeUri = null
                )
            }
        }
    }
}
