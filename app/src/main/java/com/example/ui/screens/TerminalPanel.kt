package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TerminalDownloadPlan
import com.example.data.TerminalEnvironmentManager
import com.example.data.TerminalInstallOutcome
import com.example.data.TerminalInstallStage
import com.example.data.TerminalMetadataOutcome
import com.example.data.TerminalSessionController
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceTerminal
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private enum class TerminalSpecialKey(val label: String, val tag: String) {
    ESC("ESC", "esc"),
    CTRL("CTRL", "ctrl"),
    ALT("ALT", "alt"),
    TAB("TAB", "tab"),
    HOME("HOME", "home"),
    END("END", "end"),
    PGUP("PGUP", "pgup"),
    PGDN("PGDN", "pgdn"),
    UP("↑", "up"),
    DOWN("↓", "down"),
    LEFT("←", "left"),
    RIGHT("→", "right")
}

@Composable
fun TerminalPanel(
    envManager: TerminalEnvironmentManager,
    controller: TerminalSessionController,
    projectDir: File?,
    onCloseToChat: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val shellState by controller.state.collectAsStateWithLifecycle()

    var isInstalled by remember { mutableStateOf(envManager.isInstalled()) }
    var installStage by remember {
        mutableStateOf<TerminalInstallStage>(
            if (envManager.isInstalled()) TerminalInstallStage.Installed else TerminalInstallStage.Idle
        )
    }
    var metadataRetryTrigger by remember { mutableIntStateOf(0) }
    var downloadCancelled by remember { mutableStateOf(false) }
    var activeInstallJob by remember { mutableStateOf<Job?>(null) }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var ctrlSticky by remember { mutableStateOf(false) }
    var altSticky by remember { mutableStateOf(false) }
    var commandField by remember { mutableStateOf(TextFieldValue("")) }

    val outputListState = rememberLazyListState()

    LaunchedEffect(isInstalled, metadataRetryTrigger) {
        if (isInstalled) {
            installStage = TerminalInstallStage.Installed
            controller.startIfNeeded(projectDir)
        } else {
            installStage = TerminalInstallStage.FetchingSize
            when (val outcome = envManager.queryDownloadPlan()) {
                is TerminalMetadataOutcome.Ready -> {
                    installStage = TerminalInstallStage.ConfirmDownload(outcome.plan)
                }
                is TerminalMetadataOutcome.Error -> {
                    installStage = TerminalInstallStage.Failed(outcome.message)
                }
            }
        }
    }

    LaunchedEffect(shellState.lines.size) {
        if (shellState.lines.isNotEmpty()) {
            outputListState.animateScrollToItem(shellState.lines.lastIndex)
        }
    }

    fun startInstall(plan: TerminalDownloadPlan) {
        downloadCancelled = false
        activeInstallJob?.cancel()
        activeInstallJob = scope.launch {
            val outcome = envManager.downloadVerifyAndInstall(
                plan = plan,
                onProgress = { stage -> installStage = stage },
                isCancelled = { downloadCancelled }
            )
            when (outcome) {
                is TerminalInstallOutcome.Success -> {
                    isInstalled = true
                    installStage = TerminalInstallStage.Installed
                }
                is TerminalInstallOutcome.Cancelled -> {
                    isInstalled = false
                    installStage = TerminalInstallStage.Idle
                    onCloseToChat()
                }
                is TerminalInstallOutcome.Failure -> {
                    isInstalled = false
                    installStage = TerminalInstallStage.Failed(outcome.message)
                }
            }
        }
    }

    fun submitCurrentInput() {
        val text = commandField.text
        if (ctrlSticky && text.length == 1 && text[0].isLetter()) {
            controller.sendControlChar(text[0])
            ctrlSticky = false
            commandField = TextFieldValue("")
            return
        }
        controller.sendCommandLine(text)
        commandField = TextFieldValue("")
        ctrlSticky = false
        altSticky = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("terminal_panel")
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Terminal,
                contentDescription = null,
                tint = BlueLightAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Surface(
                shape = CircleShape,
                color = SurfaceElevated,
                border = BorderStroke(
                    1.dp,
                    if (shellState.isRunning) LiveGreen.copy(alpha = 0.5f) else BorderSubtle
                )
            ) {
                Text(
                    text = if (isInstalled) shellState.workingDirLabel else "Setup required",
                    style = MonoTextStyle.copy(
                        fontSize = 11.sp,
                        color = if (shellState.isRunning) LiveGreen else TextSecondary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            if (isInstalled) {
                IconButton(
                    onClick = { controller.restart(projectDir) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("terminal_restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Restart shell",
                        tint = BlueLightAccent,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("terminal_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Terminal options",
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        containerColor = SurfaceDialog
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear output", color = TextPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                controller.clearScreen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restart shell", color = TextPrimary) },
                            onClick = {
                                showOverflowMenu = false
                                controller.restart(projectDir)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = ErrorRed
                                )
                            },
                            text = {
                                Text(
                                    text = "Remove terminal environment",
                                    color = ErrorRed
                                )
                            },
                            modifier = Modifier.testTag("remove_terminal_environment_item"),
                            onClick = {
                                showOverflowMenu = false
                                scope.launch {
                                    controller.stopShell()
                                    val res = envManager.removeEnvironment()
                                    res.fold(
                                        onSuccess = {
                                            isInstalled = false
                                            installStage = TerminalInstallStage.Idle
                                            onFeedback("Terminal environment removed")
                                            onCloseToChat()
                                        },
                                        onFailure = { err ->
                                            onFeedback("Failed to remove environment: ${err.message}")
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            IconButton(
                onClick = onCloseToChat,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("terminal_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close terminal panel",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Output console
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceTerminal,
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            if (!isInstalled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Alpine Linux terminal environment is not downloaded yet.",
                        style = MonoTextStyle.copy(color = TextSecondary)
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = outputListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("terminal_output_list"),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(shellState.lines) { _, line ->
                            Text(
                                text = line,
                                style = MonoTextStyle.copy(
                                    color = when {
                                        line.startsWith("Failed ") || line.startsWith("[Write error:") -> ErrorRed
                                        line.startsWith(shellState.workingDirLabel) -> BlueLightAccent
                                        line.startsWith("Note:") || line.startsWith("Workspace mounted") -> LiveGreen
                                        else -> TextPrimary
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Mobile Special-Keys Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("terminal_special_keys_row"),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(TerminalSpecialKey.entries.size) { index ->
                val key = TerminalSpecialKey.entries[index]
                val isStickyActive = (key == TerminalSpecialKey.CTRL && ctrlSticky) ||
                    (key == TerminalSpecialKey.ALT && altSticky)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isStickyActive) VioletPrimary else SurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (isStickyActive) BorderNeonGlow else BorderActive
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = isInstalled) {
                            when (key) {
                                TerminalSpecialKey.ESC -> {
                                    ctrlSticky = false
                                    altSticky = false
                                    controller.sendRawEscapeSequence("\u001B")
                                }
                                TerminalSpecialKey.CTRL -> ctrlSticky = !ctrlSticky
                                TerminalSpecialKey.ALT -> altSticky = !altSticky
                                TerminalSpecialKey.TAB -> {
                                    if (commandField.text.isEmpty()) {
                                        controller.sendRawEscapeSequence("\t")
                                    } else {
                                        val pos = commandField.selection.start
                                        val updated = commandField.text.replaceRange(pos, commandField.selection.end, "\t")
                                        commandField = TextFieldValue(updated, TextRange(pos + 1))
                                    }
                                }
                                TerminalSpecialKey.HOME -> {
                                    commandField = commandField.copy(selection = TextRange(0))
                                }
                                TerminalSpecialKey.END -> {
                                    commandField = commandField.copy(selection = TextRange(commandField.text.length))
                                }
                                TerminalSpecialKey.PGUP -> {
                                    scope.launch {
                                        val target = (outputListState.firstVisibleItemIndex - 8).coerceAtLeast(0)
                                        outputListState.animateScrollToItem(target)
                                    }
                                }
                                TerminalSpecialKey.PGDN -> {
                                    scope.launch {
                                        if (shellState.lines.isNotEmpty()) {
                                            val target = (outputListState.firstVisibleItemIndex + 8)
                                                .coerceAtMost(shellState.lines.lastIndex)
                                            outputListState.animateScrollToItem(target)
                                        }
                                    }
                                }
                                TerminalSpecialKey.UP -> {
                                    controller.historyPrevious()?.let { prev ->
                                        commandField = TextFieldValue(prev, TextRange(prev.length))
                                    }
                                }
                                TerminalSpecialKey.DOWN -> {
                                    controller.historyNext()?.let { next ->
                                        commandField = TextFieldValue(next, TextRange(next.length))
                                    }
                                }
                                TerminalSpecialKey.LEFT -> {
                                    val newPos = (commandField.selection.start - 1).coerceAtLeast(0)
                                    commandField = commandField.copy(selection = TextRange(newPos))
                                }
                                TerminalSpecialKey.RIGHT -> {
                                    val newPos = (commandField.selection.start + 1).coerceAtMost(commandField.text.length)
                                    commandField = commandField.copy(selection = TextRange(newPos))
                                }
                            }
                        }
                        .testTag("terminal_key_${key.tag}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key.label,
                            style = MonoTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isStickyActive) Color.White else BlueLightAccent
                            )
                        )
                    }
                }
            }
        }

        // Command input bar at bottom
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, BorderActive),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (ctrlSticky) "^" else "$",
                    style = MonoTextStyle.copy(
                        color = if (ctrlSticky) VioletPrimary else LiveGreen,
                        fontWeight = FontWeight.Bold
                    )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (commandField.text.isEmpty()) {
                        Text(
                            text = if (ctrlSticky) "Ctrl+key or command…" else "Enter shell command…",
                            style = MonoTextStyle.copy(color = TextMuted)
                        )
                    }
                    BasicTextField(
                        value = commandField,
                        onValueChange = { updated ->
                            if (ctrlSticky && updated.text.length == 1 && commandField.text.isEmpty() && updated.text[0].isLetter()) {
                                controller.sendControlChar(updated.text[0])
                                ctrlSticky = false
                                commandField = TextFieldValue("")
                            } else {
                                if (altSticky && updated.text.length == 1 && commandField.text.isEmpty()) {
                                    controller.sendRawEscapeSequence("\u001B${updated.text[0]}")
                                    altSticky = false
                                    commandField = TextFieldValue("")
                                } else {
                                    commandField = updated
                                }
                            }
                        },
                        enabled = isInstalled,
                        singleLine = true,
                        textStyle = MonoTextStyle.copy(color = TextPrimary),
                        cursorBrush = SolidColor(BlueLightAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = { submitCurrentInput() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terminal_command_input")
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(VioletPrimary)
                        .clickable(enabled = isInstalled) { submitCurrentInput() }
                        .testTag("terminal_send_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Run command",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // One-time environment download/verify/setup dialogs
    when (val stage = installStage) {
        TerminalInstallStage.FetchingSize -> {
            Dialog(
                onDismissRequest = onCloseToChat,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                TerminalSetupDialogCard(testTag = "terminal_fetching_size_dialog") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BlueLightAccent,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Checking Alpine Linux size…",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Reading official release metadata and download headers",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassOutlinedButton(
                        text = "Cancel",
                        onClick = onCloseToChat,
                        testTag = "terminal_size_cancel_button",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is TerminalInstallStage.ConfirmDownload -> {
            Dialog(
                onDismissRequest = onCloseToChat,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                TerminalSetupDialogCard(testTag = "terminal_download_confirm_dialog") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Terminal setup",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Terminal needs a one-time environment download (Alpine Linux). Size: ${stage.plan.totalSizeMbFormatted} MB. Download now?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.testTag("terminal_download_prompt_text")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Alpine ${stage.plan.alpineVersion} (${stage.plan.arch.alpineArch}) + PRoot",
                        style = MonoTextStyle.copy(color = TextSecondary, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassOutlinedButton(
                            text = "Not now",
                            onClick = onCloseToChat,
                            testTag = "terminal_not_now_button",
                            modifier = Modifier.weight(1f)
                        )
                        PocketPrimaryButton(
                            text = "Download",
                            onClick = { startInstall(stage.plan) },
                            testTag = "terminal_download_confirm_button",
                            modifier = Modifier.weight(1f),
                            height = 48.dp
                        )
                    }
                }
            }
        }

        is TerminalInstallStage.Downloading -> {
            val fraction = if (stage.totalBytes > 0L) {
                (stage.downloadedBytes.toFloat() / stage.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val downloadedMb = TerminalDownloadPlan.formatMegabytes(stage.downloadedBytes)
            val totalMb = TerminalDownloadPlan.formatMegabytes(stage.totalBytes)

            Dialog(
                onDismissRequest = { /* wait for explicit Cancel */ },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                TerminalSetupDialogCard(testTag = "terminal_downloading_dialog") {
                    Text(
                        text = stage.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = VioletPrimary,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$downloadedMb MB / $totalMb MB",
                        style = MonoTextStyle.copy(color = BlueLightAccent),
                        modifier = Modifier.testTag("terminal_download_progress_text")
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassOutlinedButton(
                        text = "Cancel",
                        onClick = {
                            downloadCancelled = true
                            activeInstallJob?.cancel()
                            installStage = TerminalInstallStage.Idle
                            onCloseToChat()
                        },
                        testTag = "terminal_download_cancel_button",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        TerminalInstallStage.Verifying -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                TerminalSetupDialogCard(testTag = "terminal_verifying_dialog") {
                    Text(
                        text = "Verifying…",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        color = VioletPrimary,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Checking official Alpine SHA256 signature…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        TerminalInstallStage.SettingUp -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                TerminalSetupDialogCard(testTag = "terminal_setting_up_dialog") {
                    Text(
                        text = "Setting up…",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        color = VioletPrimary,
                        trackColor = SurfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Extracting Alpine rootfs into app private storage…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        is TerminalInstallStage.Failed -> {
            Dialog(
                onDismissRequest = onCloseToChat,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                TerminalSetupDialogCard(testTag = "terminal_error_dialog") {
                    Text(
                        text = "Terminal setup failed",
                        style = MaterialTheme.typography.titleLarge,
                        color = ErrorRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stage.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.testTag("terminal_error_message")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassOutlinedButton(
                            text = "Close",
                            onClick = onCloseToChat,
                            testTag = "terminal_error_close_button",
                            modifier = Modifier.weight(1f)
                        )
                        PocketPrimaryButton(
                            text = "Retry",
                            onClick = { metadataRetryTrigger++ },
                            testTag = "terminal_error_retry_button",
                            modifier = Modifier.weight(1f),
                            height = 48.dp
                        )
                    }
                }
            }
        }

        TerminalInstallStage.Idle, TerminalInstallStage.Installed -> Unit
    }
}

@Composable
private fun TerminalSetupDialogCard(
    testTag: String,
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .widthIn(max = 500.dp)
            .testTag(testTag),
        cornerRadius = 24.dp,
        containerColor = SurfaceDialog,
        isGlowingActive = true,
        borderColor = BorderNeonGlow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            content()
        }
    }
}
