package com.miriamlin.marginalia

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

data class Note(val id: String, val excerpts: List<String>)

class NoteStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "notes.json"))

    fun load(): List<Note> = synchronized(lock) { readNotes() }

    fun create(text: String) = synchronized(lock) {
        require(text.isNotBlank())
        val notes = readNotes()
        val note = Note(UUID.randomUUID().toString(), listOf(text))
        writeNotes(listOf(note) + notes)
    }

    fun append(noteId: String, text: String) = synchronized(lock) {
        require(text.isNotBlank())
        val notes = readNotes()
        require(notes.any { it.id == noteId }) { "Note no longer exists" }
        writeNotes(notes.map { note ->
            if (note.id == noteId) note.copy(excerpts = note.excerpts + text) else note
        })
    }

    private fun readNotes(): List<Note> {
        val contents = try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: FileNotFoundException) {
            if (!file.baseFile.exists()) return emptyList()
            throw error
        }
        val notes = JSONArray(contents)
        return List(notes.length()) { index ->
            val note = notes.getJSONObject(index)
            val excerpts = note.getJSONArray("excerpts")
            Note(note.getString("id"), List(excerpts.length()) { excerpts.getString(it) })
        }
    }

    private fun writeNotes(notes: List<Note>) {
        val contents = JSONArray()
        notes.forEach { note ->
            contents.put(JSONObject().apply {
                put("id", note.id)
                put("excerpts", JSONArray(note.excerpts))
            })
        }
        val stream = file.startWrite()
        try {
            stream.write(contents.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private companion object {
        val lock = Any()
    }
}
