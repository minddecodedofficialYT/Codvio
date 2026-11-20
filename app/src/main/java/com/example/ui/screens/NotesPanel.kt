package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.NoteItem
import com.example.model.TodoItem
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassOutlinedButton
import com.example.ui.components.PillSegmentedSelector
import com.example.ui.components.PocketPrimaryButton
import com.example.ui.theme.BlueLightAccent
import com.example.ui.theme.BorderActive
import com.example.ui.theme.BorderNeonGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.SurfaceDialog
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary

private enum class NotesTab(val label: String) {
    NOTES("Notes"),
    TODOS("To-dos")
}

@Composable
fun NotesPanel(
    notes: List<NoteItem>,
    todos: List<TodoItem>,
    onAddNote: (title: String, content: String) -> Unit,
    onUpdateNote: (id: String, title: String, content: String) -> Unit,
    onDeleteNote: (id: String) -> Unit,
    onAddTodo: (text: String) -> Unit,
    onToggleTodo: (id: String) -> Unit,
    onDeleteTodo: (id: String) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(NotesTab.NOTES) }
    var editingNote by remember { mutableStateOf<NoteItem?>(null) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var newTodoText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("notes_panel")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = null,
                tint = BlueLightAccent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Notes & To-dos",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("notes_panel_close_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close notes panel",
                    tint = TextPrimary
                )
            }
        }

        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            PillSegmentedSelector(
                options = NotesTab.entries.map { it to it.label },
                selected = selectedTab,
                onSelect = { selectedTab = it },
                testTagPrefix = "notes_tab"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (selectedTab) {
            NotesTab.NOTES -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    GlassOutlinedButton(
                        text = "+ Add note",
                        onClick = { showCreateNoteDialog = true },
                        testTag = "notes_add_button",
                        height = 40.dp,
                        contentColor = BlueLightAccent,
                        borderColor = BorderActive
                    )
                }

                if (notes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No notes yet. Tap \"+ Add note\" to save reminders, snippets, or ideas offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.testTag("notes_empty_state")
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("notes_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(1.dp, BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("note_card_${note.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { editingNote = note },
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = note.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (note.content.isNotBlank()) {
                                            Text(
                                                text = note.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { editingNote = note },
                                        modifier = Modifier.testTag("note_edit_button_${note.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit ${note.title}",
                                            tint = BlueLightAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteNote(note.id) },
                                        modifier = Modifier.testTag("note_delete_button_${note.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete ${note.title}",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            NotesTab.TODOS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        placeholder = { Text("Add a to-do...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = notesTextFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (newTodoText.isNotBlank()) {
                                    onAddTodo(newTodoText)
                                    newTodoText = ""
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("todo_input")
                    )

                    GlassOutlinedButton(
                        text = "Add",
                        onClick = {
                            if (newTodoText.isNotBlank()) {
                                onAddTodo(newTodoText)
                                newTodoText = ""
                            }
                        },
                        enabled = newTodoText.isNotBlank(),
                        testTag = "todo_add_button",
                        height = 48.dp,
                        contentColor = BlueLightAccent,
                        borderColor = BorderActive
                    )
                }

                if (todos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No to-dos yet. Add your tasks above and tick them off as you build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.testTag("todos_empty_state")
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("todos_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(todos, key = { it.id }) { todo ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (todo.isDone) LiveGreen.copy(alpha = 0.45f) else BorderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("todo_row_${todo.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleTodo(todo.id) }
                                        .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onToggleTodo(todo.id) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("todo_toggle_${todo.id}")
                                    ) {
                                        Icon(
                                            imageVector = if (todo.isDone) {
                                                Icons.Filled.CheckBox
                                            } else {
                                                Icons.Filled.CheckBoxOutlineBlank
                                            },
                                            contentDescription = if (todo.isDone) "Mark incomplete" else "Mark complete",
                                            tint = if (todo.isDone) LiveGreen else BlueLightAccent
                                        )
                                    }

                                    Text(
                                        text = todo.text,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (todo.isDone) TextMuted else TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { onDeleteTodo(todo.id) },
                                        modifier = Modifier.testTag("todo_delete_${todo.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete to-do",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateNoteDialog || editingNote != null) {
        val current = editingNote
        NoteEditorDialog(
            initialTitle = current?.title.orEmpty(),
            initialContent = current?.content.orEmpty(),
            isEditing = current != null,
            onDismiss = {
                showCreateNoteDialog = false
                editingNote = null
            },
            onSave = { title, content ->
                if (current == null) {
                    onAddNote(title, content)
                } else {
                    onUpdateNote(current.id, title, content)
                }
                showCreateNoteDialog = false
                editingNote = null
            }
        )
    }
}

@Composable
private fun NoteEditorDialog(
    initialTitle: String,
    initialContent: String,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp)
                .testTag("note_editor_dialog"),
            cornerRadius = 24.dp,
            containerColor = SurfaceDialog,
            isGlowingActive = true,
            borderColor = BorderNeonGlow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEditing) "Edit note" else "New note",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Architecture notes") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = notesTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    placeholder = { Text("Write your note...") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    colors = notesTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        testTag = "note_dialog_cancel",
                        modifier = Modifier.weight(1f)
                    )
                    PocketPrimaryButton(
                        text = if (isEditing) "Save" else "Add",
                        onClick = { onSave(title, content) },
                        enabled = title.trim().isNotEmpty() || content.trim().isNotEmpty(),
                        testTag = "note_dialog_save",
                        modifier = Modifier.weight(1f),
                        height = 48.dp,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun notesTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = VioletPrimary,
    unfocusedBorderColor = BorderSubtle,
    focusedLabelColor = BlueLightAccent,
    unfocusedLabelColor = TextSecondary,
    cursorColor = BlueLightAccent
)
