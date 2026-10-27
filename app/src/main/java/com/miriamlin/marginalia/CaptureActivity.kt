package com.miriamlin.marginalia

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textKey = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> Intent.EXTRA_PROCESS_TEXT
            Intent.ACTION_SEND -> Intent.EXTRA_TEXT
            else -> null
        }
        val text = runCatching {
            textKey?.let { intent.getCharSequenceExtra(it)?.toString() }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            finish()
            return
        }

        window.setGravity(Gravity.BOTTOM)
        window.setWindowAnimations(0)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setFinishOnTouchOutside(true)
        val store = NoteStore(this)
        setContent { CaptureSheet(text, store, onSaved = { finish() }) }
        updateWindowSize()
    }

    private fun updateWindowSize() {
        val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.heightPixels
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, screenHeight / 4)
    }
}

@Composable
private fun CaptureSheet(text: String, store: NoteStore, onSaved: () -> Unit) {
    var showExisting by rememberSaveable { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<Note>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val loadError = stringResource(R.string.load_notes_error)
    val saveError = stringResource(R.string.save_note_error)
    val textStyle = TextStyle(color = Color.Black, fontSize = 16.sp, lineHeight = 22.sp)

    BackHandler(enabled = showExisting && !saving) {
        showExisting = false
        errorMessage = null
    }
    LaunchedEffect(showExisting) {
        if (showExisting) {
            notes = null
            errorMessage = null
            try {
                notes = withContext(Dispatchers.IO) { store.load() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = loadError
            }
        }
    }

    fun save(noteId: String?) {
        if (saving) return
        saving = true
        errorMessage = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (noteId == null) store.create(text) else store.append(noteId, text)
                }
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = saveError
            } finally {
                saving = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color.White).border(1.dp, Color.Black)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (showExisting) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    stringResource(if (saving) R.string.saving else R.string.add_to_existing_note),
                    modifier = Modifier.weight(1f),
                    style = textStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                )
                CaptureButton(stringResource(R.string.back), enabled = !saving) {
                    showExisting = false
                    errorMessage = null
                }
            }
            Spacer(Modifier.height(8.dp))
            val currentNotes = notes
            val currentError = errorMessage
            when {
                currentError != null -> BasicText(currentError, style = textStyle)
                currentNotes == null -> BasicText(stringResource(R.string.loading_notes), style = textStyle)
                currentNotes.isEmpty() -> BasicText(stringResource(R.string.no_notes), style = textStyle)
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), overscrollEffect = null) {
                    itemsIndexed(currentNotes, key = { _, note -> note.id }) { index, note ->
                        if (index > 0) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val preview = note.excerpts.firstOrNull().orEmpty()
                            BasicText(
                                preview, modifier = Modifier.weight(1f), style = textStyle,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            CaptureButton(
                                stringResource(R.string.add_symbol),
                                modifier = Modifier.size(48.dp), enabled = !saving,
                                description = stringResource(R.string.add_to_note, preview)
                            ) { save(note.id) }
                        }
                    }
                }
            }
        } else {
            BasicText(
                stringResource(if (saving) R.string.saving else R.string.save_text),
                style = textStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                errorMessage ?: text, modifier = Modifier.weight(1f), style = textStyle,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CaptureButton(
                    stringResource(R.string.create_new_note),
                    modifier = Modifier.weight(1f).height(56.dp), enabled = !saving
                ) { save(null) }
                CaptureButton(
                    stringResource(R.string.add_to_existing_note),
                    modifier = Modifier.weight(1f).height(56.dp), enabled = !saving
                ) { showExisting = true }
            }
        }
    }
}

@Composable
private fun CaptureButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier.heightIn(min = 48.dp).border(1.dp, Color.Black)
            .semantics { if (description != null) contentDescription = description }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, enabled = enabled, role = Role.Button, onClick = onClick
            ).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            label,
            style = TextStyle(color = Color.Black, fontSize = 16.sp, textAlign = TextAlign.Center)
        )
    }
}
