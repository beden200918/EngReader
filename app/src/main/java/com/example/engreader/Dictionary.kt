package com.example.engreader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream

data class DictEntry(
    val word: String,
    val phonetic: String,
    val translation: String,
    val definition: String,
    val pos: String
)

/** 只读词典库：首次启动从 assets 解压到私有目录，之后可整包替换 */
class Dictionary(context: Context) {
    private val db: SQLiteDatabase

    init {
        val f = File(context.filesDir, "dictionary.db")
        if (!f.exists() || f.length() < 4096) {
            context.assets.open("dictionary.db").use { input ->
                FileOutputStream(f).use { out -> input.copyTo(out, 1 shl 20) }
            }
        }
        db = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun lookup(raw: String): DictEntry? {
        val q = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        if (q.isEmpty()) return null
        find(q)?.let { return it }
        if (!q.contains(' ')) {
            val lemma = findLemma(q)
            if (lemma != null && lemma != q) find(lemma)?.let { return it }
        }
        return null
    }

    private fun find(word: String): DictEntry? =
        db.rawQuery(
            "SELECT word, phonetic, translation, definition, pos FROM dict " +
                "WHERE word = ? COLLATE NOCASE LIMIT 1",
            arrayOf(word)
        ).use { c ->
            if (c.moveToFirst()) DictEntry(
                c.getString(0) ?: word,
                c.getString(1).orEmpty(),
                c.getString(2).orEmpty(),
                c.getString(3).orEmpty(),
                c.getString(4).orEmpty()
            ) else null
        }

    private fun findLemma(form: String): String? =
        db.rawQuery("SELECT lemma FROM forms WHERE form = ? LIMIT 1", arrayOf(form)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}

/** 用户数据：生词本，与词典库彻底分开，换词库不丢数据 */
class VocabStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "user.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE vocab(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                term TEXT NOT NULL,
                phonetic TEXT,
                translation TEXT,
                definition TEXT,
                context TEXT,
                source TEXT,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE UNIQUE INDEX idx_term ON vocab(term)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 以后加字段/复习功能，在这里做迁移，用户数据原地保留
    }

    data class Item(
        val id: Long,
        val term: String,
        val phonetic: String,
        val translation: String,
        val definition: String,
        val context: String
    )

    fun add(term: String, e: DictEntry?, context: String, source: String): Boolean {
        val cv = ContentValues().apply {
            put("term", term)
            put("phonetic", e?.phonetic ?: "")
            put("translation", e?.translation ?: "")
            put("definition", e?.definition ?: "")
            put("context", context)
            put("source", source)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict(
            "vocab", null, cv, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun list(): List<Item> {
        val out = mutableListOf<Item>()
        readableDatabase.rawQuery(
            "SELECT id,term,phonetic,translation,definition,context FROM vocab ORDER BY id DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Item(
                        c.getLong(0),
                        c.getString(1).orEmpty(),
                        c.getString(2).orEmpty(),
                        c.getString(3).orEmpty(),
                        c.getString(4).orEmpty(),
                        c.getString(5).orEmpty()
                    )
                )
            }
        }
        return out
    }

    fun delete(id: Long) {
        writableDatabase.delete("vocab", "id = ?", arrayOf(id.toString()))
    }

    fun exportCsv(): String {
        val sb = StringBuilder("term,phonetic,translation,definition,context\n")
        readableDatabase.rawQuery(
            "SELECT term,phonetic,translation,definition,context FROM vocab ORDER BY id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val cells = (0..4).map { esc(c.getString(it).orEmpty()) }
                sb.append(cells.joinToString(",")).append("\n")
            }
        }
        return sb.toString()
    }

    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
}
