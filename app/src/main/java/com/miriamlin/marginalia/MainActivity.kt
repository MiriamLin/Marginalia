package com.miriamlin.marginalia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var refreshCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setWindowAnimations(0)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        val store = NoteStore(this)

        setContent {
            var notes by remember { mutableStateOf<List<Note>?>(null) }
            var loadFailed by remember { mutableStateOf(false) }

            LaunchedEffect(refreshCount) {
                try {
                    notes = withContext(Dispatchers.IO) { store.load() }
                    loadFailed = false
                } catch (error: java.io.IOException) {
                    loadFailed = true
                } catch (error: org.json.JSONException) {
                    loadFailed = true
                }
            }
            NotesScreen(notes, loadFailed)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCount++
    }
}

@Composable
private fun NotesScreen(notes: List<Note>?, loadFailed: Boolean) {
    val bodyStyle = TextStyle(color = Color.Black, fontSize = 18.sp, lineHeight = 28.sp)
    Column(
        Modifier.fillMaxSize().background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        BasicText(
            stringResource(R.string.app_name),
            modifier = Modifier.padding(24.dp),
            style = bodyStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))

        when {
            loadFailed -> BasicText(
                stringResource(R.string.load_notes_error),
                modifier = Modifier.padding(24.dp), style = bodyStyle
            )
            notes == null -> BasicText(
                stringResource(R.string.loading_notes),
                modifier = Modifier.padding(24.dp), style = bodyStyle
            )
            notes.isEmpty() -> Column(Modifier.padding(24.dp)) {
                BasicText(stringResource(R.string.no_notes), style = bodyStyle)
                Spacer(Modifier.height(12.dp))
                BasicText(stringResource(R.string.no_notes_hint), style = bodyStyle)
            }
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), overscrollEffect = null) {
                itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
                    if (index > 0) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                    }
                    BasicText(
                        note.excerpts.joinToString("\n\n"),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        style = bodyStyle
                    )
                }
            }
        }
    }
}
