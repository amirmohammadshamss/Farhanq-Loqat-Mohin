package ir.iliya.farhanglogat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ir.iliya.farhanglogat.data.Collection
import ir.iliya.farhanglogat.data.DictionaryRepository
import ir.iliya.farhanglogat.data.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DictionaryState(
    val collection: Collection = Collection.WORDS,
    val query: String = "",
    val entries: List<Entry> = emptyList(),
    val selected: Entry? = null,
    val favorite: Boolean = false,
    val loading: Boolean = false,
    val more: Boolean = true,
    val error: Boolean = false,
    val textSize: Float = 18f,
)

class DictionaryViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private val databaseDispatcher = databaseExecutor.asCoroutineDispatcher()
    private val repository = DictionaryRepository(application)
    private val preferences = application.getSharedPreferences("compose_settings", 0)
    private val mutableState = MutableStateFlow(DictionaryState(
        collection = Collection.valueOf(savedState["collection"] ?: Collection.WORDS.name),
        query = savedState["query"] ?: "",
        textSize = preferences.getFloat("text_size", 18f),
    ))
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        addCloseable(java.io.Closeable {
            databaseExecutor.execute { repository.close() }
            databaseExecutor.shutdown()
        })
        reload()
        val id = savedState.get<Long>("selected_id")
        val section = savedState.get<Boolean>("selected_section") ?: false
        if (id != null) runAction {
            withContext(databaseDispatcher) { repository.entry(id, section) }?.let {
                mutableState.value = mutableState.value.copy(selected = it, favorite = withContext(databaseDispatcher) { repository.isSaved(it) })
            }
        }
    }

    fun selectCollection(collection: Collection) {
        savedState["collection"] = collection.name
        savedState["query"] = ""
        dismissDetail()
        mutableState.value = mutableState.value.copy(collection = collection, query = "", entries = emptyList())
        reload()
    }

    fun search(query: String) {
        savedState["query"] = query
        mutableState.value = mutableState.value.copy(query = query, entries = emptyList())
        reload(debounce = true)
    }

    fun reload(debounce: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = false)
            try {
                if (debounce) delay(250)
                val snapshot = mutableState.value
                val rows = withContext(databaseDispatcher) { repository.list(snapshot.collection, snapshot.query, 0) }
                mutableState.value = mutableState.value.copy(entries = rows, loading = false, more = rows.size == 100)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(loading = false, error = true) }
        }
    }

    fun loadMore() {
        val snapshot = mutableState.value
        if (snapshot.loading || !snapshot.more) return
        loadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true)
            try {
                val rows = withContext(databaseDispatcher) { repository.list(snapshot.collection, snapshot.query, snapshot.entries.size) }
                mutableState.value = mutableState.value.copy(entries = snapshot.entries + rows, loading = false, more = rows.size == 100)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(loading = false, error = true) }
        }
    }

    fun open(entry: Entry) {
        savedState["selected_id"] = entry.id
        savedState["selected_section"] = entry.section
        mutableState.value = mutableState.value.copy(selected = entry, favorite = false)
        runAction {
            val favorite = withContext(databaseDispatcher) {
                repository.recordHistory(entry)
                repository.isSaved(entry)
            }
            if (mutableState.value.selected == entry) mutableState.value = mutableState.value.copy(favorite = favorite)
        }
    }

    fun dismissDetail() {
        savedState.remove<Long>("selected_id")
        mutableState.value = mutableState.value.copy(selected = null, favorite = false)
        if (mutableState.value.collection == Collection.HISTORY) reload()
    }

    fun toggleFavorite() {
        val entry = mutableState.value.selected ?: return
        runAction {
            val favorite = withContext(databaseDispatcher) {
                repository.toggleSaved(entry)
                repository.isSaved(entry)
            }
            if (mutableState.value.selected == entry) mutableState.value = mutableState.value.copy(favorite = favorite)
            reload()
        }
    }

    fun clear() {
        val collection = mutableState.value.collection
        runAction { withContext(databaseDispatcher) { repository.clear(collection) }; reload() }
    }

    fun textSize(size: Float) {
        preferences.edit().putFloat("text_size", size).apply()
        mutableState.value = mutableState.value.copy(textSize = size)
    }

    private fun runAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableState.value = mutableState.value.copy(error = true) }
    }
}
