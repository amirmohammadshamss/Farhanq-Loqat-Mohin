package ir.iliya.farhanglogat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.iliya.farhanglogat.data.Collection
import ir.iliya.farhanglogat.data.Entry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF91D5B4))
                else lightColorScheme(primary = Color(0xFF236B4E), secondary = Color(0xFF526459))
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { DictionaryApp() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryApp(model: DictionaryViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    var settings by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.selected != null) { model.dismissDetail() }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(state.selected?.title ?: stringResource(R.string.app_name), maxLines = 1) },
                navigationIcon = {
                    if (state.selected != null) TextButton(onClick = model::dismissDetail) { Text(stringResource(R.string.back)) }
                },
                actions = { TextButton(onClick = { settings = true }) { Text(stringResource(R.string.settings)) } })
        },
        bottomBar = {
            if (state.selected == null) NavigationBar {
                listOf(Collection.WORDS, Collection.FAVORITES, Collection.HISTORY).forEach { collection ->
                    NavigationBarItem(selected = state.collection == collection,
                        onClick = { model.selectCollection(collection) },
                        icon = { Text(when (collection) { Collection.WORDS -> "آ"; Collection.FAVORITES -> "♡"; else -> "◷" }) },
                        label = { Text(stringResource(collection.label())) })
                }
            }
        },
    ) { padding ->
        if (state.selected != null) {
            EntryDetail(state.selected!!, state.favorite, state.textSize, model::toggleFavorite,
                Modifier.padding(padding))
        } else {
            Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Collection.SECTIONS, Collection.BOOKMARKS).forEach { collection ->
                        FilterChip(selected = state.collection == collection,
                            onClick = { model.selectCollection(collection) }, label = { Text(stringResource(collection.label())) })
                    }
                }
                OutlinedTextField(value = state.query, onValueChange = model::search,
                    label = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp))
                if (state.collection in listOf(Collection.FAVORITES, Collection.HISTORY, Collection.BOOKMARKS)) {
                    TextButton(onClick = { confirmClear = true }, enabled = state.entries.isNotEmpty()) { Text(stringResource(R.string.clear)) }
                }
                when {
                    state.error -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.error))
                        TextButton(onClick = { model.reload() }) { Text(stringResource(R.string.retry)) }
                    }
                    state.loading && state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.empty))
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.entries, key = { it.id }) { entry ->
                            Surface(onClick = { model.open(entry) }, modifier = Modifier.fillMaxWidth()) {
                                Text(entry.title, Modifier.padding(horizontal = 24.dp, vertical = 18.dp), fontSize = state.textSize.sp)
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                        }
                        if (state.more) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (state.loading) CircularProgressIndicator()
                                else TextButton(onClick = model::loadMore) { Text(stringResource(R.string.load_more)) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false },
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column {
                Text(stringResource(R.string.text_size))
                Slider(value = state.textSize, onValueChange = model::textSize, valueRange = 14f..30f, steps = 7)
                Text(stringResource(R.string.about))
            }
        }, confirmButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.done)) } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        text = { Text(stringResource(R.string.clear_confirm)) },
        confirmButton = { TextButton(onClick = { model.clear(); confirmClear = false }) { Text(stringResource(R.string.clear)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun EntryDetail(entry: Entry, saved: Boolean, textSize: Float, onSave: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val body = remember(entry.body) {
        // Bundled entries are plain text, some with literal escaped newlines.
        entry.body.replace("\\r\\n", "\n").replace("\\n", "\n")
    }
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onSave) { Text(stringResource(if (saved) R.string.remove_saved else R.string.save)) }
            TextButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${entry.title}\n\n$body")
                }, null))
            }) { Text(stringResource(R.string.share)) }
        }
        SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(body, Modifier.fillMaxWidth().padding(vertical = 20.dp), fontSize = textSize.sp,
                lineHeight = (textSize * 1.8f).sp,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
        }
    }
}

private fun Collection.label(): Int = when (this) {
    Collection.WORDS -> R.string.words
    Collection.FAVORITES -> R.string.favorites
    Collection.HISTORY -> R.string.history
    Collection.SECTIONS -> R.string.sections
    Collection.BOOKMARKS -> R.string.bookmarks
}
