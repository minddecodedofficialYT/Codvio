package com.example.data

import android.system.Os
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque
import java.util.zip.GZIPInputStream

data class TarExtractionSummary(
    val filesExtracted: Int,
    val directoriesCreated: Int,
    val symlinksCreated: Int,
    val hardlinksCreated: Int
)

object TarGzExtractor {

    private const val BLOCK_SIZE = 512

    fun extract(tarGzFile: File, targetDir: File): TarExtractionSummary {
        return tarGzFile.inputStream().buffered().use { fis ->
            extractStream(fis, targetDir)
        }
    }

    fun extractStream(compressedStream: InputStream, targetDir: File): TarExtractionSummary {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            error("Unable to create extraction directory: ${targetDir.absolutePath}")
        }
        val rootPath = targetDir.canonicalFile

        var filesExtracted = 0
        var directoriesCreated = 0
        var symlinksCreated = 0
        var hardlinksCreated = 0

        var pendingLongName: String? = null
        var pendingLongLinkName: String? = null
        var nextPaxOverrides: MutableMap<String, String> = mutableMapOf()
        val globalPaxOverrides: MutableMap<String, String> = mutableMapOf()

        val deferredDirModes = mutableListOf<Pair<File, Int>>()

        GZIPInputStream(BufferedInputStream(compressedStream, 32 * 1024)).use { tarIn ->
            val header = ByteArray(BLOCK_SIZE)

            while (true) {
                val read = readFully(tarIn, header, 0, BLOCK_SIZE)
                if (read == 0) break
                if (read < BLOCK_SIZE) {
                    error("Truncated tar header ($read bytes)")
                }
                if (isAllZero(header)) {
                    // End-of-archive marker
                    break
                }

                val typeFlagByte = header[156].toInt() and 0xFF
                val typeFlag = if (typeFlagByte == 0) '0' else typeFlagByte.toChar()

                val ustarName = parseNullTerminatedString(header, 0, 100)
                val ustarPrefix = parseNullTerminatedString(header, 345, 155)
                val baseName = if (ustarPrefix.isNotEmpty()) "$ustarPrefix/$ustarName" else ustarName
                val baseLinkName = parseNullTerminatedString(header, 157, 100)
                val mode = parseOctal(header, 100, 8).toInt()
                val rawSize = parseSize(header, 124, 12)

                when (typeFlag) {
                    'L' -> {
                        pendingLongName = readEntryString(tarIn, rawSize).trimEnd('\u0000', '\n')
                        skipPadding(tarIn, rawSize)
                        continue
                    }
                    'K' -> {
                        pendingLongLinkName = readEntryString(tarIn, rawSize).trimEnd('\u0000', '\n')
                        skipPadding(tarIn, rawSize)
                        continue
                    }
                    'x', 'X' -> {
                        val paxText = readEntryString(tarIn, rawSize)
                        skipPadding(tarIn, rawSize)
                        parsePaxRecords(paxText, nextPaxOverrides)
                        continue
                    }
                    'g' -> {
                        val paxText = readEntryString(tarIn, rawSize)
                        skipPadding(tarIn, rawSize)
                        parsePaxRecords(paxText, globalPaxOverrides)
                        continue
                    }
                }

                val entryPathRaw = nextPaxOverrides["path"]
                    ?: pendingLongName
                    ?: globalPaxOverrides["path"]
                    ?: baseName
                val entryLinkRaw = nextPaxOverrides["linkpath"]
                    ?: pendingLongLinkName
                    ?: globalPaxOverrides["linkpath"]
                    ?: baseLinkName
                val entrySize = nextPaxOverrides["size"]?.toLongOrNull()
                    ?: rawSize

                pendingLongName = null
                pendingLongLinkName = null
                nextPaxOverrides = mutableMapOf()

                val cleanRelative = sanitizeArchiveRelativePath(entryPathRaw)
                if (cleanRelative == null || cleanRelative.isEmpty()) {
                    skipFully(tarIn, paddedSize(entrySize))
                    continue
                }

                val outFile = resolveSafeInsideRoot(rootPath, cleanRelative)

                when (typeFlag) {
                    '5' -> {
                        ensureDirectoryHierarchy(outFile)
                        if (mode > 0) {
                            deferredDirModes.add(outFile to (mode and 0x1FF))
                        }
                        directoriesCreated++
                        skipFully(tarIn, paddedSize(entrySize))
                    }
                    '2' -> {
                        outFile.parentFile?.let { ensureDirectoryHierarchy(it) }
                        deleteExistingEntry(outFile)
                        createSymbolicLinkCompat(outFile, entryLinkRaw)
                        symlinksCreated++
                        skipFully(tarIn, paddedSize(entrySize))
                    }
                    '1' -> {
                        val targetRel = sanitizeArchiveRelativePath(entryLinkRaw)
                            ?: error("Unsafe hardlink target in tar: $entryLinkRaw")
                        val linkTarget = resolveSafeInsideRoot(rootPath, targetRel)
                        outFile.parentFile?.let { ensureDirectoryHierarchy(it) }
                        deleteExistingEntry(outFile)
                        createHardLinkOrCopyCompat(outFile, linkTarget)
                        hardlinksCreated++
                        skipFully(tarIn, paddedSize(entrySize))
                    }
                    '0', '7' -> {
                        outFile.parentFile?.let { ensureDirectoryHierarchy(it) }
                        deleteExistingEntry(outFile)
                        FileOutputStream(outFile).use { fos ->
                            copyExactBytes(tarIn, fos, entrySize)
                        }
                        skipPadding(tarIn, entrySize)
                        applyFileModeCompat(outFile, mode and 0x1FF)
                        filesExtracted++
                    }
                    else -> {
                        // Device nodes, FIFOs, etc. cannot be created without root in app sandbox; skip payload
                        skipFully(tarIn, paddedSize(entrySize))
                    }
                }
            }
        }

        // Apply directory modes deepest-first so read-only dirs don't block child creation
        deferredDirModes
            .sortedByDescending { it.first.absolutePath.length }
            .forEach { (dir, perm) ->
                if (dir.exists()) {
                    // Keep owner rwx so app can traverse & maintain its rootfs
                    applyFileModeCompat(dir, perm or 0b111_000_000)
                }
            }

        return TarExtractionSummary(
            filesExtracted = filesExtracted,
            directoriesCreated = directoriesCreated,
            symlinksCreated = symlinksCreated,
            hardlinksCreated = hardlinksCreated
        )
    }

    internal fun sanitizeArchiveRelativePath(raw: String): String? {
        val normalized = raw.replace('\\', '/').trim()
        if (normalized.isEmpty() || normalized == "." || normalized == "./") return ""
        val segments = ArrayDeque<String>()
        for (part in normalized.split('/')) {
            when {
                part.isEmpty() || part == "." -> Unit
                part == ".." -> {
                    if (segments.isEmpty()) return null
                    segments.removeLast()
                }
                else -> segments.addLast(part)
            }
        }
        return segments.joinToString("/")
    }

    private fun resolveSafeInsideRoot(rootPath: File, cleanRelative: String): File {
        var current = rootPath
        val parts = cleanRelative.split('/').filter { it.isNotEmpty() }
        for (index in parts.indices) {
            val part = parts[index]
            val candidate = File(current, part)
            val isLast = index == parts.lastIndex
            if (!isLast && isSymlink(candidate)) {
                val target = readSymlinkTarget(candidate)
                if (target != null) {
                    current = resolveSymlinkDestinationWithinRoot(rootPath, current, target)
                    continue
                }
            }
            current = candidate
        }
        val rootCanon = rootPath.canonicalPath
        val lexCheck = File(rootCanon, parts.joinToString(File.separator)).canonicalPath
        if (lexCheck != rootCanon && !lexCheck.startsWith(rootCanon + File.separator)) {
            error("Archive entry escapes root directory: $cleanRelative")
        }
        return current
    }

    private fun resolveSymlinkDestinationWithinRoot(
        rootPath: File,
        linkParent: File,
        linkTarget: String
    ): File {
        val rawSegments = if (linkTarget.startsWith("/")) {
            sanitizeArchiveRelativePath(linkTarget)
        } else {
            val parentRel = linkParent.absolutePath
                .removePrefix(rootPath.absolutePath)
                .trimStart(File.separatorChar)
            val combined = if (parentRel.isEmpty()) linkTarget else "$parentRel/$linkTarget"
            sanitizeArchiveRelativePath(combined)
        }
        if (rawSegments.isNullOrEmpty()) return rootPath
        return File(rootPath, rawSegments.replace('/', File.separatorChar))
    }

    private fun ensureDirectoryHierarchy(dir: File) {
        if (dir.isDirectory) return
        if (isSymlink(dir) || dir.isFile) {
            dir.delete()
        }
        if (!dir.mkdirs() && !dir.isDirectory) {
            error("Unable to create directory: ${dir.absolutePath}")
        }
    }

    private fun deleteExistingEntry(target: File) {
        if (isSymlink(target) || target.isFile) {
            target.delete()
        } else if (target.isDirectory) {
            target.deleteRecursively()
        }
    }

    private fun isSymlink(file: File): Boolean {
        return runCatching { Files.isSymbolicLink(file.toPath()) }.getOrDefault(false)
    }

    private fun readSymlinkTarget(file: File): String? {
        return runCatching { Os.readlink(file.absolutePath) }.getOrElse {
            runCatching { Files.readSymbolicLink(file.toPath()).toString() }.getOrNull()
        }
    }

    private fun createSymbolicLinkCompat(linkFile: File, targetPath: String) {
        val osSucceeded = runCatching {
            Os.symlink(targetPath, linkFile.absolutePath)
        }.isSuccess
        if (!osSucceeded || !isSymlink(linkFile)) {
            if (linkFile.exists()) linkFile.delete()
            Files.createSymbolicLink(linkFile.toPath(), Path.of(targetPath))
        }
    }

    private fun createHardLinkOrCopyCompat(newLink: File, existingTarget: File) {
        if (!existingTarget.exists() && !isSymlink(existingTarget)) {
            return
        }
        if (isSymlink(existingTarget)) {
            val symTarget = readSymlinkTarget(existingTarget)
            if (symTarget != null) {
                createSymbolicLinkCompat(newLink, symTarget)
                return
            }
        }
        val osLinked = runCatching {
            Os.link(existingTarget.absolutePath, newLink.absolutePath)
        }.isSuccess
        if (!osLinked || !newLink.exists()) {
            val nioLinked = runCatching {
                Files.createLink(newLink.toPath(), existingTarget.toPath())
            }.isSuccess
            if ((!nioLinked || !newLink.exists()) && existingTarget.isFile) {
                existingTarget.copyTo(newLink, overwrite = true)
            }
        }
    }

    private fun applyFileModeCompat(file: File, mode: Int) {
        if (mode <= 0) return
        val chmodOk = runCatching {
            Os.chmod(file.absolutePath, mode)
        }.isSuccess
        if (!chmodOk || ((mode and 0b001_000_000) != 0 && !file.canExecute())) {
            if ((mode and 0b001_000_000) != 0) {
                file.setExecutable(true, (mode and 0b000_001_001) == 0)
            }
            if ((mode and 0b010_000_000) != 0) {
                file.setWritable(true, true)
            }
            file.setReadable(true, false)
        }
    }

    private fun readEntryString(tarIn: InputStream, size: Long): String {
        val clamped = size.coerceAtMost(1024L * 1024L).toInt()
        val out = ByteArrayOutputStream(clamped)
        copyExactBytes(tarIn, out, size)
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun parsePaxRecords(paxText: String, target: MutableMap<String, String>) {
        var idx = 0
        while (idx < paxText.length) {
            val spaceIdx = paxText.indexOf(' ', idx)
            if (spaceIdx <= idx) break
            val recordLen = paxText.substring(idx, spaceIdx).toIntOrNull() ?: break
            val eqIdx = paxText.indexOf('=', spaceIdx + 1)
            if (eqIdx < 0) break
            val key = paxText.substring(spaceIdx + 1, eqIdx)
            val recordEnd = (idx + recordLen).coerceAtMost(paxText.length)
            val value = paxText.substring(eqIdx + 1, recordEnd).trimEnd('\n')
            target[key] = value
            idx += recordLen.coerceAtLeast(1)
        }
    }

    private fun copyExactBytes(input: InputStream, output: java.io.OutputStream, totalBytes: Long) {
        var remaining = totalBytes
        val buffer = ByteArray(16 * 1024)
        while (remaining > 0L) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val n = input.read(buffer, 0, toRead)
            if (n < 0) error("Unexpected EOF while reading tar entry ($remaining bytes remaining)")
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val rem = (size % BLOCK_SIZE).toInt()
        if (rem != 0) {
            skipFully(input, (BLOCK_SIZE - rem).toLong())
        }
    }

    private fun paddedSize(size: Long): Long {
        val rem = size % BLOCK_SIZE
        return if (rem == 0L) size else size + (BLOCK_SIZE - rem)
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(4096)
        while (remaining > 0L) {
            val n = input.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
            if (n < 0) error("Unexpected EOF while skipping tar padding")
            remaining -= n
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val n = input.read(buffer, offset + total, length - total)
            if (n < 0) return total
            total += n
        }
        return total
    }

    private fun isAllZero(block: ByteArray): Boolean {
        for (b in block) {
            if (b.toInt() != 0) return false
        }
        return true
    }

    private fun parseNullTerminatedString(buf: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val max = (offset + length).coerceAtMost(buf.size)
        while (end < max && buf[end].toInt() != 0) {
            end++
        }
        return String(buf, offset, end - offset, StandardCharsets.UTF_8)
    }

    private fun parseOctal(buf: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        var seenDigit = false
        val max = (offset + length).coerceAtMost(buf.size)
        for (i in offset until max) {
            val b = buf[i].toInt() and 0xFF
            if (b == 0) break
            if (b == ' '.code) {
                if (seenDigit) break else continue
            }
            if (b in '0'.code..'7'.code) {
                seenDigit = true
                result = (result shl 3) + (b - '0'.code)
            } else {
                break
            }
        }
        return result
    }

    private fun parseSize(buf: ByteArray, offset: Int, length: Int): Long {
        val first = buf[offset].toInt() and 0xFF
        if ((first and 0x80) != 0) {
            var value = 0L
            for (i in 1 until length) {
                value = (value shl 8) or (buf[offset + i].toLong() and 0xFFL)
            }
            return value
        }
        return parseOctal(buf, offset, length)
    }
}
