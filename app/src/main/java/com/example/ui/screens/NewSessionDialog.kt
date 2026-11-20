package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.SessionItem
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueContainer
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.StorageUtils

@Composable
fun NewSessionDialog(
    existingSessions: List<SessionItem>,
    defaultProjectFolder: String,
    onDismiss: () -> Unit,
    onStart: (projectName: String, projectFolder: String, folderUri: String?) -> Unit
) {
    val context = LocalContext.current
    var projectFolder by remember { mutableStateOf(defaultProjectFolder) }
    var projectName by remember {
        mutableStateOf(
            defaultProjectFolder
                .trimEnd('/')
                .substringAfterLast('/')
                .takeIf { it.isNotBlank() }
                ?: ""
        )
    }
    var selectedFolderUri by remember { mutableStateOf<String?>(null) }
    var showCopyFromProject by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val info = StorageUtils.extractFolderDisplayInfo(uri)
            projectFolder = info.displayPath
            projectName = info.folderName
            selectedFolderUri = uri.toString()
        }
    }

    val copyFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val info = StorageUtils.extractFolderDisplayInfo(uri)
            projectFolder = info.displayPath
            projectName = "${info.folderName}-copy"
            selectedFolderUri = uri.toString()
            showCopyFromProject = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            borderColor = BorderActive
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "New session",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("choose_project_folder_button"),
                    cornerRadius = 20.dp,
                    containerColor = BlueContainer.copy(alpha = 0.65f),
                    borderColor = BorderActive,
                    onClick = { folderPickerLauncher.launch(null) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = BlueLightAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Choose project folder",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }

                OutlinedTextField(
                    value = projectFolder,
                    onValueChange = { projectFolder = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_session_project_folder_input"),
                    label = { Text("Project folder (optional)") },
                    placeholder = { Text("/storage/emulated/0/Projects/app") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_session_project_name_input"),
                    label = { Text("Project name") },
                    placeholder = { Text("My project") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = pocketTextFieldColors()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCopyFromProject = !showCopyFromProject }
                        .testTag("copy_from_another_project_link")
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = BlueLightAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy from another project (optional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = BlueLightAccent
                    )
                }

                AnimatedVisibility(visible = showCopyFromProject) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (existingSessions.isNotEmpty()) {
                                Text(
                                    text = "Recent projects",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                                existingSessions.take(3).forEach { source ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                projectFolder = source.projectFolder
                                                projectName = "${source.projectName}-copy"
                                                selectedFolderUri = source.folderUri
                                                showCopyFromProject = false
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = SurfaceCard,
                                        border = BorderStroke(1.dp, BorderActive)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = source.projectName,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (source.projectFolder.isNotBlank()) {
                                                Text(
                                                    text = source.projectFolder,
                                                    style = MonoTextStyle.copy(color = TextMuted),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "No previous sessions yet. Pick an existing folder to copy its path and name.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            TextButton(
                                onClick = { copyFolderPickerLauncher.launch(null) },
                                modifier = Modifier.testTag("copy_pick_folder_button")
                            ) {
                                Text(
                                    text = "Browse folder to copy",
                                    color = BlueLightAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        testTag = "new_session_cancel_button",
                        modifier = Modifier.weight(1f),
                        contentColor = TextSecondary,
                        borderColor = BorderActive
                    )

                    PocketPrimaryButton(
                        text = "Start",
                        onClick = { onStart(projectName, projectFolder, selectedFolderUri) },
                        testTag = "new_session_start_button",
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )
                }
            }
        }
    }
}
