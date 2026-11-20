package com.example.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TerminalArch(
    val androidAbi: String,
    val alpineArch: String,
    val prootArch: String
)

sealed interface TerminalArchResolution {
    data class Supported(val arch: TerminalArch) : TerminalArchResolution
    data class Unsupported(val abis: List<String>, val message: String) : TerminalArchResolution
}

data class TerminalDownloadPlan(
    val arch: TerminalArch,
    val alpineVersion: String,
    val rootfsFileName: String,
    val rootfsUrl: String,
    val rootfsSha256Url: String,
    val prootUrl: String,
    val rootfsSizeBytes: Long,
    val prootSizeBytes: Long
) {
    val totalSizeBytes: Long
        get() = rootfsSizeBytes + prootSizeBytes

    val totalSizeMbFormatted: String
        get() = formatMegabytes(totalSizeBytes)

    companion object {
        fun formatMegabytes(bytes: Long): String {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f", mb)
        }
    }
}

sealed interface TerminalMetadataOutcome {
    data class Ready(val plan: TerminalDownloadPlan) : TerminalMetadataOutcome
    data class Error(val message: String) : TerminalMetadataOutcome
}

sealed interface TerminalInstallStage {
    data object Idle : TerminalInstallStage
    data object FetchingSize : TerminalInstallStage
    data class ConfirmDownload(val plan: TerminalDownloadPlan) : TerminalInstallStage
    data class Downloading(
        val label: String,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : TerminalInstallStage
    data object Verifying : TerminalInstallStage
    data object SettingUp : TerminalInstallStage
    data object Installed : TerminalInstallStage
    data class Failed(val message: String) : TerminalInstallStage
}

sealed interface TerminalInstallOutcome {
    data class Success(val alpineVersion: String, val arch: TerminalArch) : TerminalInstallOutcome
    data object Cancelled : TerminalInstallOutcome
    data class Failure(val message: String) : TerminalInstallOutcome
}

data class AlpineMinirootfsRelease(
    val version: String,
    val file: String,
    val sha256FromYaml: String?
)

class TerminalEnvironmentManager(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build(),
    private val abiProvider: () -> Array<String> = { Build.SUPPORTED_ABIS },
    private val alpineLatestReleasesUrlProvider: (String) -> String = { alpineArch ->
        "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$alpineArch/latest-releases.yaml"
    },
    private val alpineFileUrlProvider: (String, String) -> String = { alpineArch, fileName ->
        "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$alpineArch/$fileName"
    },
    private val prootUrlProvider: (String) -> String = { prootArch ->
        "https://skirsten.github.io/proot-portable-android-binaries/$prootArch/proot"
    }
) {
    val terminalRootDir: File = File(context.filesDir, "terminal")
    val binDir: File = File(terminalRootDir, "bin")
    val rootfsDir: File = File(terminalRootDir, "rootfs")
    val tmpDir: File = File(terminalRootDir, "tmp")
    val prootBinary: File = File(binDir, "proot")
    val markerFile: File = File(terminalRootDir, ".installed_meta.json")

    fun isInstalled(): Boolean {
        return markerFile.exists() &&
            prootBinary.exists() &&
            prootBinary.length() > 0L &&
            File(rootfsDir, "bin/busybox").exists() &&
            File(rootfsDir, "etc/alpine-release").exists()
    }

    fun resolveArchitecture(): TerminalArchResolution {
        val abis = abiProvider().toList()
        for (abi in abis) {
            when (abi.trim().lowercase(Locale.US)) {
                "arm64-v8a" -> return TerminalArchResolution.Supported(
                    TerminalArch(androidAbi = "arm64-v8a", alpineArch = "aarch64", prootArch = "aarch64")
                )
                "armeabi-v7a" -> return TerminalArchResolution.Supported(
                    TerminalArch(androidAbi = "armeabi-v7a", alpineArch = "armv7", prootArch = "armv7")
                )
            }
        }
        val listed = if (abis.isEmpty()) "unknown" else abis.joinToString(", ")
        return TerminalArchResolution.Unsupported(
            abis = abis,
            message = "Unsupported CPU architecture ($listed). Terminal supports arm64-v8a (aarch64) and armeabi-v7a (armv7) devices."
        )
    }

    suspend fun queryDownloadPlan(): TerminalMetadataOutcome = withContext(Dispatchers.IO) {
        val arch = when (val resolved = resolveArchitecture()) {
            is TerminalArchResolution.Supported -> resolved.arch
            is TerminalArchResolution.Unsupported -> {
                return@withContext TerminalMetadataOutcome.Error(resolved.message)
            }
        }

        val yamlUrl = alpineLatestReleasesUrlProvider(arch.alpineArch)
        val yamlResult = fetchString(yamlUrl)
        val yamlText = yamlResult.getOrElse { err ->
            return@withContext TerminalMetadataOutcome.Error(
                "Unable to reach Alpine release server: ${err.message ?: "check your internet connection"}"
            )
        }

        val release = parseMinirootfsReleaseYaml(yamlText)
            ?: return@withContext TerminalMetadataOutcome.Error(
                "Could not find alpine-minirootfs release entry in latest-releases.yaml for ${arch.alpineArch}."
            )

        val rootfsUrl = alpineFileUrlProvider(arch.alpineArch, release.file)
        val rootfsShaUrl = "$rootfsUrl.sha256"
        val prootUrl = prootUrlProvider(arch.prootArch)

        val rootfsBytes = queryRemoteContentLength(rootfsUrl).getOrElse { err ->
            return@withContext TerminalMetadataOutcome.Error(
                "Unable to inspect Alpine download size from server headers: ${err.message ?: "network error"}"
            )
        }
        val prootBytes = queryRemoteContentLength(prootUrl).getOrElse { err ->
            return@withContext TerminalMetadataOutcome.Error(
                "Unable to inspect PRoot download size from server headers: ${err.message ?: "network error"}"
            )
        }

        TerminalMetadataOutcome.Ready(
            TerminalDownloadPlan(
                arch = arch,
                alpineVersion = release.version,
                rootfsFileName = release.file,
                rootfsUrl = rootfsUrl,
                rootfsSha256Url = rootfsShaUrl,
                prootUrl = prootUrl,
                rootfsSizeBytes = rootfsBytes,
                prootSizeBytes = prootBytes
            )
        )
    }

    suspend fun downloadVerifyAndInstall(
        plan: TerminalDownloadPlan,
        onProgress: (TerminalInstallStage) -> Unit,
        isCancelled: () -> Boolean
    ): TerminalInstallOutcome = withContext(Dispatchers.IO) {
        val downloadTempDir = File(terminalRootDir, "download_tmp")
        val partialProot = File(downloadTempDir, "proot.part")
        val partialRootfs = File(downloadTempDir, "${plan.rootfsFileName}.part")

        fun cleanupTempDownloads() {
            partialProot.delete()
            partialRootfs.delete()
            downloadTempDir.deleteRecursively()
        }

        try {
            if (isCancelled()) {
                cleanupTempDownloads()
                return@withContext TerminalInstallOutcome.Cancelled
            }
            if (!downloadTempDir.exists() && !downloadTempDir.mkdirs()) {
                return@withContext TerminalInstallOutcome.Failure(
                    "Unable to prepare temporary download folder."
                )
            }

            val totalBytes = plan.totalSizeBytes

            // 1. Download PRoot
            onProgress(
                TerminalInstallStage.Downloading(
                    label = "Downloading PRoot…",
                    downloadedBytes = 0L,
                    totalBytes = totalBytes
                )
            )
            downloadFileWithProgress(
                url = plan.prootUrl,
                destFile = partialProot,
                isCancelled = isCancelled,
                onBytes = { downloaded ->
                    onProgress(
                        TerminalInstallStage.Downloading(
                            label = "Downloading PRoot…",
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes
                        )
                    )
                }
            )

            // 2. Download Alpine Linux minirootfs
            val baseOffset = partialProot.length()
            onProgress(
                TerminalInstallStage.Downloading(
                    label = "Downloading Alpine Linux…",
                    downloadedBytes = baseOffset,
                    totalBytes = totalBytes
                )
            )
            downloadFileWithProgress(
                url = plan.rootfsUrl,
                destFile = partialRootfs,
                isCancelled = isCancelled,
                onBytes = { downloaded ->
                    onProgress(
                        TerminalInstallStage.Downloading(
                            label = "Downloading Alpine Linux…",
                            downloadedBytes = baseOffset + downloaded,
                            totalBytes = totalBytes
                        )
                    )
                }
            )

            if (isCancelled()) {
                cleanupTempDownloads()
                return@withContext TerminalInstallOutcome.Cancelled
            }

            // 3. Verifying…
            onProgress(TerminalInstallStage.Verifying)

            val shaText = fetchString(plan.rootfsSha256Url).getOrElse { err ->
                cleanupTempDownloads()
                return@withContext TerminalInstallOutcome.Failure(
                    "Verification failed: unable to download official SHA256 checksum (${err.message ?: "network error"})."
                )
            }
            val expectedSha256 = parseSha256Checksum(shaText)
            if (expectedSha256 == null) {
                cleanupTempDownloads()
                return@withContext TerminalInstallOutcome.Failure(
                    "Verification failed: official .sha256 file format was invalid."
                )
            }

            val actualSha256 = computeSha256Hex(partialRootfs)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                cleanupTempDownloads()
                return@withContext TerminalInstallOutcome.Failure(
                    "SHA256 verification failed! Expected $expectedSha256 but got $actualSha256. Downloaded files were deleted."
                )
            }

            if (!hasElfHeader(partialProot)) {
                cleanupTempDownloads()
                return@withContext TerminalInstallOutcome.Failure(
                    "Verification failed: downloaded PRoot binary is corrupted or not an ELF executable."
                )
            }

            if (isCancelled()) {
                cleanupTempDownloads()
                return@withContext TerminalInstallOutcome.Cancelled
            }

            // 4. Setting up…
            onProgress(TerminalInstallStage.SettingUp)

            if (rootfsDir.exists()) rootfsDir.deleteRecursively()
            if (binDir.exists()) binDir.deleteRecursively()
            markerFile.delete()

            binDir.mkdirs()
            rootfsDir.mkdirs()
            tmpDir.mkdirs()

            TarGzExtractor.extract(partialRootfs, rootfsDir)

            if (!partialProot.copyTo(prootBinary, overwrite = true).exists()) {
                error("Failed to install PRoot binary")
            }
            applyExecutableMode(prootBinary)

            val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
            File(etcDir, "resolv.conf").writeText(
                "nameserver 8.8.8.8\nnameserver 1.1.1.1\n",
                StandardCharsets.UTF_8
            )
            File(rootfsDir, "root").mkdirs()
            File(rootfsDir, "workspace").mkdirs()
            File(rootfsDir, "tmp").mkdirs()
            File(rootfsDir, "dev").mkdirs()
            File(rootfsDir, "proc").mkdirs()
            File(rootfsDir, "sys").mkdirs()

            val metaJson = JSONObject().apply {
                put("alpineVersion", plan.alpineVersion)
                put("alpineArch", plan.arch.alpineArch)
                put("androidAbi", plan.arch.androidAbi)
                put("sha256", actualSha256)
                put("installedAtMs", System.currentTimeMillis())
            }.toString()
            markerFile.writeText(metaJson, StandardCharsets.UTF_8)

            cleanupTempDownloads()
            onProgress(TerminalInstallStage.Installed)
            TerminalInstallOutcome.Success(alpineVersion = plan.alpineVersion, arch = plan.arch)
        } catch (cancelled: DownloadCancelledException) {
            cleanupTempDownloads()
            TerminalInstallOutcome.Cancelled
        } catch (ce: CancellationException) {
            cleanupTempDownloads()
            throw ce
        } catch (e: Exception) {
            cleanupTempDownloads()
            TerminalInstallOutcome.Failure(
                e.message ?: "Terminal setup failed"
            )
        }
    }

    suspend fun removeEnvironment(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (terminalRootDir.exists() && !terminalRootDir.deleteRecursively()) {
                error("Unable to delete terminal directory")
            }
            Unit
        }
    }

    private fun queryRemoteContentLength(url: String): Result<Long> {
        return runCatching {
            val headRequest = Request.Builder()
                .url(url)
                .head()
                .header("Accept-Encoding", "identity")
                .build()
            httpClient.newCall(headRequest).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code} from $url")
                }
                val contentLength = resp.header("Content-Length")?.toLongOrNull()
                    ?: resp.body?.contentLength()?.takeIf { it > 0L }
                if (contentLength != null && contentLength > 0L) {
                    return@runCatching contentLength
                }
            }

            // Fallback: 1-byte Range GET to read Content-Range total size from server header
            val rangeRequest = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-0")
                .header("Accept-Encoding", "identity")
                .build()
            httpClient.newCall(rangeRequest).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    error("HTTP ${resp.code} from $url")
                }
                val contentRange = resp.header("Content-Range")
                val totalFromRange = contentRange
                    ?.substringAfterLast('/', "")
                    ?.trim()
                    ?.toLongOrNull()
                if (totalFromRange != null && totalFromRange > 0L) {
                    return@runCatching totalFromRange
                }
                val directLen = resp.header("Content-Length")?.toLongOrNull()
                    ?: resp.body?.contentLength()
                if (directLen != null && directLen > 0L && resp.code == 200) {
                    return@runCatching directLen
                }
                error("Server did not return Content-Length header for $url")
            }
        }
    }

    private fun fetchString(url: String): Result<String> {
        return runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code} from $url")
                }
                resp.body?.string() ?: error("Empty response from $url")
            }
        }
    }

    private fun downloadFileWithProgress(
        url: String,
        destFile: File,
        isCancelled: () -> Boolean,
        onBytes: (Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept-Encoding", "identity")
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("Download failed (HTTP ${resp.code}) for $url")
            }
            val body = resp.body ?: error("Empty download body from $url")
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var totalRead = 0L
                    while (true) {
                        if (isCancelled()) throw DownloadCancelledException()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (isCancelled()) throw DownloadCancelledException()
                        output.write(buffer, 0, n)
                        totalRead += n
                        onBytes(totalRead)
                    }
                    output.flush()
                }
            }
        }
    }

    private fun hasElfHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                val n = input.read(magic)
                n == 4 &&
                    magic[0] == 0x7F.toByte() &&
                    magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun applyExecutableMode(file: File) {
        val osOk = runCatching {
            android.system.Os.chmod(file.absolutePath, 493) // 0755
        }.isSuccess
        if (!osOk || !file.canExecute()) {
            file.setExecutable(true, false)
            file.setReadable(true, false)
            file.setWritable(true, true)
        }
    }

    private class DownloadCancelledException : RuntimeException()

    companion object {
        internal fun parseMinirootfsReleaseYaml(yamlText: String): AlpineMinirootfsRelease? {
            // Parses entries inside Alpine's latest-releases.yaml list
            var currentArch = ""
            var currentFlavor = ""
            var currentFile = ""
            var currentVersion = ""
            var currentSha256: String? = null

            fun tryBuildEntry(): AlpineMinirootfsRelease? {
                if (currentFlavor == "alpine-minirootfs" &&
                    currentFile.isNotEmpty() &&
                    currentVersion.isNotEmpty()
                ) {
                    return AlpineMinirootfsRelease(
                        version = currentVersion,
                        file = currentFile,
                        sha256FromYaml = currentSha256
                    )
                }
                return null
            }

            for (rawLine in yamlText.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.startsWith("- ")) {
                    tryBuildEntry()?.let { return it }
                    currentArch = ""
                    currentFlavor = ""
                    currentFile = ""
                    currentVersion = ""
                    currentSha256 = null
                }
                val content = line.removePrefix("- ").trim()
                val colon = content.indexOf(':')
                if (colon <= 0) continue
                val key = content.substring(0, colon).trim()
                val value = content.substring(colon + 1).trim().trim('"', '\'')
                when (key) {
                    "arch" -> currentArch = value
                    "flavor" -> currentFlavor = value
                    "file" -> currentFile = value
                    "version" -> currentVersion = value
                    "sha256" -> currentSha256 = value.ifEmpty { null }
                }
            }
            return tryBuildEntry()
        }

        internal fun parseSha256Checksum(rawText: String): String? {
            val token = rawText.trim()
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?.lowercase(Locale.US)
                ?: return null
            return token.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' } }
        }

        fun computeSha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
