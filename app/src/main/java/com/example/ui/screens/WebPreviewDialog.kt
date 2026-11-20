package com.example.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ProjectWorkspace
import com.example.ui.components.GlassCard
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.net.URLConnection

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewDialog(
    workspace: ProjectWorkspace,
    projectName: String,
    onDismiss: () -> Unit
) {
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val directIndexFile = remember(workspace) { workspace.resolveIndexFileForWebView() }

    LaunchedEffect(workspace, reloadTrigger) {
        if (directIndexFile != null) {
            errorMessage = null
            webViewRef?.loadUrl("file://${directIndexFile.absolutePath}")
        } else {
            val loaded = workspace.readFile("index.html")
            loaded.fold(
                onSuccess = { text ->
                    htmlContent = text
                    errorMessage = null
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "index.html not found in project folder"
                }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 22.dp)
                .testTag("web_preview_dialog"),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            isGlowingActive = true,
            borderColor = BorderNeonGlow
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        tint = BlueLightAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Web Preview",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "$projectName · index.html",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            reloadTrigger++
                            if (directIndexFile != null) {
                                webViewRef?.reload()
                            }
                        },
                        modifier = Modifier.testTag("web_preview_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh preview",
                            tint = BlueLightAccent
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("web_preview_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close preview",
                            tint = TextPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, BorderActive),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    when {
                        errorMessage != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage ?: "Unable to preview index.html",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }

                        directIndexFile == null && htmlContent == null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BlueLightAccent)
                            }
                        }

                        else -> {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.allowFileAccess = true
                                        settings.allowContentAccess = true
                                        webViewClient = buildWorkspaceWebViewClient(workspace)
                                        webViewRef = this
                                        if (directIndexFile != null) {
                                            loadUrl("file://${directIndexFile.absolutePath}")
                                        } else {
                                            loadDataWithBaseURL(
                                                WORKSPACE_PREVIEW_BASE_URL,
                                                htmlContent.orEmpty(),
                                                "text/html",
                                                "UTF-8",
                                                null
                                            )
                                        }
                                    }
                                },
                                update = { view ->
                                    webViewRef = view
                                    if (directIndexFile == null && htmlContent != null) {
                                        val tagKey = "$reloadTrigger:${htmlContent.hashCode()}"
                                        if (view.tag != tagKey) {
                                            view.tag = tagKey
                                            view.loadDataWithBaseURL(
                                                WORKSPACE_PREVIEW_BASE_URL,
                                                htmlContent.orEmpty(),
                                                "text/html",
                                                "UTF-8",
                                                null
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

const val WORKSPACE_PREVIEW_HOST = "pocketcode.local"
const val WORKSPACE_PREVIEW_BASE_URL = "https://$WORKSPACE_PREVIEW_HOST/"

fun buildWorkspaceWebViewClient(
    workspace: ProjectWorkspace?,
    onPageStarted: ((String?) -> Unit)? = null,
    onPageFinished: ((WebView?, String?) -> Unit)? = null
): WebViewClient {
    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            val url = request?.url ?: return null
            if (url.host == WORKSPACE_PREVIEW_HOST && workspace != null) {
                val rel = url.path?.trimStart('/').orEmpty().ifEmpty { "index.html" }
                val bytes = runBlocking {
                    workspace.readBytes(rel)
                }
                if (bytes != null) {
                    val mime = guessWebMime(rel)
                    return WebResourceResponse(
                        mime,
                        "UTF-8",
                        ByteArrayInputStream(bytes)
                    )
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onPageStarted?.invoke(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageFinished?.invoke(view, url)
        }
    }
}

fun guessWebMime(path: String): String {
    return when (Uri.parse(path).lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream"
    }
}
