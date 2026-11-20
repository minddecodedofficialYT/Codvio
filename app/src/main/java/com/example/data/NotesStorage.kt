package com.example.data

import android.content.Context
import com.example.model.NoteItem
import com.example.model.TodoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

data class NotesTodosSnapshot(
    val notes: List<NoteItem> = emptyList(),
    val todos: List<TodoItem> = emptyList()
)

class NotesStorage(private val context: Context) {

    private val storageFile: File
        get() = File(context.filesDir, FILE_NAME)

    suspend fun load(): NotesTodosSnapshot = withContext(Dispatchers.IO) {
        val file = storageFile
        if (!file.exists()) return@withContext NotesTodosSnapshot()
        runCatching {
            NotesTodosCodec.decode(file.readText(StandardCharsets.UTF_8))
        }.getOrDefault(NotesTodosSnapshot())
    }

    suspend fun save(snapshot: NotesTodosSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            storageFile.writeText(NotesTodosCodec.encode(snapshot), StandardCharsets.UTF_8)
        }
    }

    companion object {
        private const val FILE_NAME = "notes_todos.json"
    }
}

object NotesTodosCodec {

    fun encode(snapshot: NotesTodosSnapshot): String {
        val root = JSONObject()
        val notesArr = JSONArray()
        snapshot.notes.forEach { note ->
            notesArr.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("updatedAtMs", note.updatedAtMs)
                }
            )
        }
        root.put("notes", notesArr)

        val todosArr = JSONArray()
        snapshot.todos.forEach { todo ->
            todosArr.put(
                JSONObject().apply {
                    put("id", todo.id)
                    put("text", todo.text)
                    put("isDone", todo.isDone)
                    put("createdAtMs", todo.createdAtMs)
                }
            )
        }
        root.put("todos", todosArr)
        return root.toString()
    }

    fun decode(rawJson: String): NotesTodosSnapshot {
        val root = JSONObject(rawJson)
        val notes = mutableListOf<NoteItem>()
        val notesArr = root.optJSONArray("notes")
        if (notesArr != null) {
            for (i in 0 until notesArr.length()) {
                val obj = notesArr.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val title = obj.optString("title", "")
                val content = obj.optString("content", "")
                if (title.isBlank() && content.isBlank()) continue
                notes.add(
                    NoteItem(
                        id = id,
                        title = title,
                        content = content,
                        updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
                    )
                )
            }
        }

        val todos = mutableListOf<TodoItem>()
        val todosArr = root.optJSONArray("todos")
        if (todosArr != null) {
            for (i in 0 until todosArr.length()) {
                val obj = todosArr.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val text = obj.optString("text", "").trim()
                if (text.isEmpty()) continue
                todos.add(
                    TodoItem(
                        id = id,
                        text = text,
                        isDone = obj.optBoolean("isDone", false),
                        createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                    )
                )
            }
        }

        return NotesTodosSnapshot(notes = notes, todos = todos)
    }
}
