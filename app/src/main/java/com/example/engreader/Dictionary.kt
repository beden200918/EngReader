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
    fun suggest(prefix: String, limit: Int = 30): List<DictEntry> {
        val q = prefix.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return rawList(
            "SELECT word, phonetic, translation, definition, pos FROM dict " +
                "WHERE word LIKE ? COLLATE NOCASE ORDER BY frq DESC LIMIT ?",
            arrayOf("$q%", limit.toString())
        )
    }

    fun inTranslation(text: String, limit: Int = 40): List<DictEntry> {
        val q = text.trim()
        if (q.isEmpty()) return emptyList()
        return rawList(
            "SELECT word, phonetic, translation, definition, pos FROM dict " +
                "WHERE translation LIKE ? LIMIT ?",
            arrayOf("%$q%", limit.toString())
        )
    }

    fun phrasesOf(word: String, limit: Int = 12): List<DictEntry> {
        val q = word.trim().lowercase()
        if (q.isEmpty() || q.contains(' ')) return emptyList()
        return rawList(
            "SELECT word, phonetic, translation, definition, pos FROM dict " +
                "WHERE word LIKE ? COLLATE NOCASE LIMIT ?",
            arrayOf("$q %", limit.toString())
        )
    }

    fun lemmaOf(word: String): String? {
        val q = word.trim().lowercase()
        if (q.isEmpty() || q.contains(' ')) return null
        findLemma(q)?.let { if (it != q) return it }
        val rules = listOf(
            "ies" to "y", "ied" to "y", "ing" to "", "ed" to "",
            "es" to "", "s" to "", "ly" to "", "er" to "", "est" to ""
        )
        for ((suf, rep) in rules) {
            if (q.length > suf.length + 2 && q.endsWith(suf)) {
                val base = q.dropLast(suf.length) + rep
                if (base != q && find(base) != null) return base
                if (suf == "ing" || suf == "ed") {
                    if (find(base + "e") != null) return base + "e"
                    if (base.length > 2 && base[base.length - 1] == base[base.length - 2]) {
                        val one = base.dropLast(1)
                        if (find(one) != null) return one
                    }
                }
            }
        }
        return null
    }

    fun similar(word: String, limit: Int = 8): List<DictEntry> {
        val q = word.trim().lowercase()
        if (q.length < 3) return emptyList()
        val rows = rawList(
            "SELECT word, phonetic, translation, definition, pos FROM dict " +
                "WHERE word LIKE ? COLLATE NOCASE AND length(word) BETWEEN ? AND ? LIMIT 4000",
            arrayOf(q.take(1) + "%", (q.length - 2).toString(), (q.length + 2).toString())
        )
        return rows.map { it to distance(q, it.word.lowercase()) }
            .filter { it.second <= 2 }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun rawList(sql: String, args: Array<String>): List<DictEntry> =
        db.rawQuery(sql, args).use { c ->
            val out = mutableListOf<DictEntry>()
            while (c.moveToNext()) {
                out.add(
                    DictEntry(
                        c.getString(0).orEmpty(),
                        c.getString(1).orEmpty(),
                        c.getString(2).orEmpty(),
                        c.getString(3).orEmpty(),
                        c.getString(4).orEmpty()
                    )
                )
            }
            out
        }
}
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
