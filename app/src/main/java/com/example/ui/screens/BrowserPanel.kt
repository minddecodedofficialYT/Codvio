package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.ProjectWorkspace
import com.example.model.ProjectType
import com.example.model.SessionItem
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserPanel(
    session: SessionItem,
    workspace: ProjectWorkspace?,
    onClose: () -> Unit
) {
    val isWebProject = session.hasIndexHtml || session.projectType == ProjectType.WEB
    val directIndexFile = remember(workspace) { workspace?.resolveIndexFileForWebView() }
    val defaultUrl = remember(isWebProject, directIndexFile) {
        if (isWebProject) {
            if (directIndexFile != null) "file://${directIndexFile.absolutePath}" else WORKSPACE_PREVIEW_BASE_URL
        } else {
            "https://duckduckgo.com"
        }
    }

    var urlInput by remember(session.id) {
        mutableStateOf(if (isWebProject) WORKSPACE_PREVIEW_BASE_URL else "https://duckduckgo.com")
    }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableIntStateOf(100) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(session.id, isWebProject, workspace) {
        val view = webViewRef ?: return@LaunchedEffect
        if (isWebProject && workspace != null) {
            if (directIndexFile != null) {
                view.loadUrl("file://${directIndexFile.absolutePath}")
            } else {
                val html = workspace.readFile("index.html").getOrNull()
                if (html != null) {
                    view.loadDataWithBaseURL(
                        WORKSPACE_PREVIEW_BASE_URL,
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        }
    }

    fun navigateTo(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val normalized = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        urlInput = normalized
        webViewRef?.loadUrl(normalized)
    }

    fun loadDefaultPage() {
        urlInput = if (isWebProject) WORKSPACE_PREVIEW_BASE_URL else "https://duckduckgo.com"
        val view = webViewRef ?: return
        if (isWebProject && workspace != null) {
            if (directIndexFile != null) {
                view.loadUrl("file://${directIndexFile.absolutePath}")
            } else {
                view.loadUrl(WORKSPACE_PREVIEW_BASE_URL)
            }
        } else {
            view.loadUrl(defaultUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("browser_panel")
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = BlueLightAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isWebProject) "Browser · Live Preview" else "Browser",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(
                onClick = { loadDefaultPage() },
                modifier = Modifier.testTag("browser_home_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = if (isWebProject) "Live Preview Home" else "Home",
                    tint = BlueLightAccent,
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("browser_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close browser panel",
                    tint = TextPrimary
                )
            }
        }

        // Navigation + URL bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = { webViewRef?.goBack() },
                enabled = canGoBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("browser_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (canGoBack) BlueLightAccent else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = { webViewRef?.goForward() },
                enabled = canGoForward,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("browser_forward_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Forward",
                    tint = if (canGoForward) BlueLightAccent else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("browser_reload_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reload",
                    tint = BlueLightAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "Enter URL (https://...)",
                            style = MonoTextStyle.copy(color = TextMuted)
                        )
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        textStyle = MonoTextStyle.copy(color = TextPrimary),
                        cursorBrush = SolidColor(BlueLightAccent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { navigateTo(urlInput) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("browser_url_input")
                    )
                }
            }
        }

        if (pageProgress in 1..99) {
            LinearProgressIndicator(
                progress = { pageProgress / 100f },
                color = VioletPrimary,
                trackColor = SurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(horizontal = 10.dp)
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderActive),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress
                            }
                        }
                        webViewClient = buildWorkspaceWebViewClient(
                            workspace = workspace,
                            onPageStarted = { startedUrl ->
                                if (!startedUrl.isNullOrBlank()) {
                                    urlInput = if (startedUrl.startsWith("file://") && isWebProject) {
                                        WORKSPACE_PREVIEW_BASE_URL
                                    } else {
                                        startedUrl
                                    }
                                }
                            },
                            onPageFinished = { view, finishedUrl ->
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                if (!finishedUrl.isNullOrBlank()) {
                                    urlInput = if (finishedUrl.startsWith("file://") && isWebProject) {
                                        WORKSPACE_PREVIEW_BASE_URL
                                    } else {
                                        finishedUrl
                                    }
                                }
                            }
                        )
                        webViewRef = this
                        if (isWebProject && workspace != null) {
                            if (directIndexFile != null) {
                                loadUrl("file://${directIndexFile.absolutePath}")
                            } else {
                                loadUrl(WORKSPACE_PREVIEW_BASE_URL)
                            }
                        } else {
                            loadUrl(defaultUrl)
                        }
                    }
                },
                update = { view ->
                    webViewRef = view
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
            )
        }
    }
}
