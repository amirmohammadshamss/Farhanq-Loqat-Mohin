package ir.iliya.farhanglogat.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DictionaryRepositoryTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var repository: DictionaryRepository

    @Before fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(target.cacheDir, "dictionary-test-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(target) {
            override fun getDatabasePath(name: String) = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        }
        repository = DictionaryRepository(context)
    }

    @After fun cleanup() { repository.close(); directory.deleteRecursively() }

    @Test fun bundledDictionaryIsSearchableAndPaged() {
        val first = repository.list(Collection.WORDS, "", 0)
        val second = repository.list(Collection.WORDS, "", 100)
        assertEquals(100, first.size)
        assertTrue(first.map { it.id }.intersect(second.map { it.id }.toSet()).isEmpty())
        val result = repository.list(Collection.WORDS, "اب", 0)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { "اب" in it.title })
        assertTrue(repository.list(Collection.WORDS, "%' OR 1=1 --", 0).isEmpty())
        assertEquals(25, repository.list(Collection.SECTIONS, "", 0).size)
    }

    @Test fun savedWordsHistoryAndBookmarksPersist() {
        val entry = repository.list(Collection.WORDS, "", 0).first()
        repository.toggleSaved(entry)
        repository.recordHistory(entry)
        repository.recordHistory(entry)
        val section = repository.list(Collection.SECTIONS, "", 0).first()
        repository.toggleSaved(section)
        repository.close()
        repository = DictionaryRepository(context)
        assertTrue(repository.isSaved(entry))
        assertEquals(entry.id, repository.list(Collection.FAVORITES, "", 0).single().id)
        assertEquals(entry.id, repository.list(Collection.HISTORY, "", 0).single().id)
        assertTrue(repository.isSaved(section))
        repository.toggleSaved(entry)
        assertFalse(repository.isSaved(entry))
        repository.clear(Collection.HISTORY)
        assertTrue(repository.list(Collection.HISTORY, "", 0).isEmpty())
        assertTrue(repository.isSaved(section))
    }

    @Test fun upgradesLegacyFavoritesWithoutLosingWords() {
        // Version 2 saved only the text. The migration must resolve its dictionary ID.
        context.openOrCreateDatabase("AppData.db", 0, null).use { db ->
            db.execSQL("CREATE TABLE wordbook_favorites (_id INTEGER PRIMARY KEY, word TEXT)")
            db.execSQL("INSERT INTO wordbook_favorites (word) VALUES ('اب')")
            db.version = 2
        }
        val favorite = repository.list(Collection.FAVORITES, "", 0).single()
        assertEquals("اب", favorite.title)
        assertTrue(repository.isSaved(favorite))
    }
}
