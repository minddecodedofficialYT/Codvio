package com.example.util

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat

object StorageUtils {

    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
        } else {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writeGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            readGranted && writeGranted
        }
    }

    fun launchAllFilesAccessSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    data class FolderDisplayInfo(
        val displayPath: String,
        val folderName: String
    )

    fun extractFolderDisplayInfo(treeUri: Uri): FolderDisplayInfo {
        val docId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: treeUri.lastPathSegment.orEmpty()

        val displayPath = when {
            docId.startsWith("primary:") -> {
                val relative = docId.removePrefix("primary:").trimStart('/')
                if (relative.isEmpty()) "/storage/emulated/0"
                else "/storage/emulated/0/$relative"
            }
            docId.contains(":") -> {
                val parts = docId.split(":", limit = 2)
                val volume = parts[0]
                val relative = parts.getOrNull(1).orEmpty().trimStart('/')
                if (relative.isEmpty()) "/storage/$volume"
                else "/storage/$volume/$relative"
            }
            docId.isNotBlank() -> docId
            else -> treeUri.toString()
        }

        val folderName = displayPath
            .trimEnd('/')
            .substringAfterLast('/')
            .ifBlank { "Workspace" }

        return FolderDisplayInfo(
            displayPath = displayPath,
            folderName = folderName
        )
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun pasteFromClipboard(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
