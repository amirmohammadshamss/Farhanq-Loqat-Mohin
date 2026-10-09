package ir.iliya.farhanglogat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable
import java.util.zip.ZipInputStream

enum class Collection { WORDS, FAVORITES, HISTORY, SECTIONS, BOOKMARKS }

data class Entry(val id: Long, val title: String, val body: String, val section: Boolean = false)

/** Owns the bundled read-only dictionaries and the original writable AppData.db. */
class DictionaryRepository(private val context: Context) : Closeable {
    private val wordConnection = lazy { openAssetDatabase("wordbook") }
    private val sectionConnection = lazy { openAssetDatabase("subdict") }
    private val words by wordConnection
    private val sections by sectionConnection
    private val saved = SavedData(context)
    private var reconciled = false

    private fun reconcileLegacyFavorites() {
        if (reconciled) return
        val db = saved.writableDatabase
        db.query("wordbook_favorites", arrayOf("_id", "word"), "wordbookID IS NULL", null, null, null, null).use { rows ->
            while (rows.moveToNext()) {
                words.query("wordbook", arrayOf("_id"), "langFullWord = ?", arrayOf(rows.getString(1)), null, null, null, "1").use { match ->
                    if (match.moveToFirst()) db.update("wordbook_favorites",
                        ContentValues().apply { put("wordbookID", match.getLong(0)) }, "_id = ?", arrayOf(rows.getLong(0).toString()))
                }
            }
        }
        reconciled = true
    }

    private fun openAssetDatabase(name: String): SQLiteDatabase {
        // A new filename avoids overwriting a dictionary from an earlier app installation.
        val destination = context.getDatabasePath("${name}-bundled-v1.db")
        if (!destination.exists()) {
            destination.parentFile!!.mkdirs()
            val temporary = java.io.File(destination.parentFile, "${destination.name}.tmp")
            try {
                ZipInputStream(context.assets.open("databases/$name.zip")).use { zip ->
                    check(zip.nextEntry?.name == "$name.db") { "Invalid dictionary archive" }
                    temporary.outputStream().use { zip.copyTo(it) }
                }
                check(temporary.renameTo(destination)) { "Cannot install dictionary" }
            } finally {
                temporary.delete()
            }
        }
        return SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun list(collection: Collection, query: String, offset: Int, limit: Int = 100): List<Entry> {
        reconcileLegacyFavorites()
        val isSection = collection == Collection.SECTIONS || collection == Collection.BOOKMARKS
        val db = if (isSection) sections else words
        val table = if (isSection) "subdict" else "wordbook"
        val title = if (isSection) "section" else "langFullWord"
        val body = if (isSection) "xml" else "entry"
        val savedTable = savedTable(collection)
        val ids = savedTable?.let { (tableName, column) ->
            saved.readableDatabase.rawQuery("SELECT $column FROM $tableName ORDER BY _id DESC", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0)) }
            }
        }
        if (ids != null && ids.isEmpty()) return emptyList()
        val conditions = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        if (ids != null) {
            // IDs are integers read from the local database, never user-provided SQL.
            conditions += "_id IN (${ids.joinToString(",")})"
        }
        if (query.isNotBlank()) {
            conditions += "$title LIKE ? ESCAPE '\\'"
            arguments += "%${query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        }
        val order = if (collection == Collection.HISTORY && ids != null) {
            "CASE _id " + ids.mapIndexed { index, id -> "WHEN $id THEN $index" }.joinToString(" ") + " END"
        } else "$title COLLATE LOCALIZED, _id"
        return db.query(table, arrayOf("_id", title, body),
            conditions.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            arguments.toTypedArray(), null, null, order, "$offset,$limit").use { c ->
            buildList {
                while (c.moveToNext()) add(Entry(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), isSection))
            }
        }
    }

    fun entry(id: Long, section: Boolean): Entry? {
        val db = if (section) sections else words
        return db.query(if (section) "subdict" else "wordbook",
            if (section) arrayOf("_id", "section", "xml") else arrayOf("_id", "langFullWord", "entry"),
            "_id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (c.moveToFirst()) Entry(c.getLong(0), c.getString(1), c.getString(2).orEmpty(), section) else null
        }
    }

    fun isSaved(entry: Entry): Boolean {
        reconcileLegacyFavorites()
        val (table, column) = savedTable(if (entry.section) Collection.BOOKMARKS else Collection.FAVORITES)!!
        return saved.readableDatabase.query(table, arrayOf("_id"), "$column = ?",
            arrayOf(entry.id.toString()), null, null, null, "1").use { it.moveToFirst() }
    }

    fun toggleSaved(entry: Entry) {
        val collection = if (entry.section) Collection.BOOKMARKS else Collection.FAVORITES
        val (table, column) = savedTable(collection)!!
        val db = saved.writableDatabase
        db.beginTransaction()
        try {
            if (isSaved(entry)) db.delete(table, "$column = ?", arrayOf(entry.id.toString()))
            else insert(db, table, column, if (entry.section) "subdict_section" else "word", entry)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun recordHistory(entry: Entry) {
        if (entry.section) return
        val db = saved.writableDatabase
        db.beginTransaction()
        try {
            db.delete("wordbook_history", "wordbookID = ?", arrayOf(entry.id.toString()))
            insert(db, "wordbook_history", "wordbookID", "word", entry)
            db.execSQL("DELETE FROM wordbook_history WHERE _id NOT IN (SELECT _id FROM wordbook_history ORDER BY _id DESC LIMIT 500)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun clear(collection: Collection) {
        savedTable(collection)?.let { saved.writableDatabase.delete(it.first, null, null) }
    }

    private fun insert(db: SQLiteDatabase, table: String, column: String, title: String, entry: Entry) {
        db.insertOrThrow(table, null, ContentValues().apply { put(column, entry.id); put(title, entry.title) })
    }

    private fun savedTable(collection: Collection): Pair<String, String>? = when (collection) {
        Collection.FAVORITES -> "wordbook_favorites" to "wordbookID"
        Collection.HISTORY -> "wordbook_history" to "wordbookID"
        Collection.BOOKMARKS -> "subdict_bookmarks" to "subdict_id"
        else -> null
    }

    override fun close() {
        if (wordConnection.isInitialized()) words.close()
        if (sectionConnection.isInitialized()) sections.close()
        saved.close()
    }
}

private class SavedData(context: Context) : SQLiteOpenHelper(context, "AppData.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE wordbook_favorites (_id INTEGER PRIMARY KEY, wordbookID INTEGER, word TEXT)")
        db.execSQL("CREATE TABLE wordbook_history (_id INTEGER PRIMARY KEY, wordbookID INTEGER, word TEXT)")
        db.execSQL("CREATE TABLE subdict_bookmarks (_id INTEGER PRIMARY KEY, subdict_id INTEGER, subdict_section TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 3 already has the required schema. Preserve every existing saved row.
        if (oldVersion < 3) {
            val columns = db.rawQuery("PRAGMA table_info(wordbook_favorites)", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(1)) }
            }
            if ("wordbookID" !in columns) db.execSQL("ALTER TABLE wordbook_favorites ADD COLUMN wordbookID INTEGER")
            db.execSQL("CREATE TABLE IF NOT EXISTS wordbook_history (_id INTEGER PRIMARY KEY, wordbookID INTEGER, word TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS subdict_bookmarks (_id INTEGER PRIMARY KEY, subdict_id INTEGER, subdict_section TEXT)")
        }
    }
}
