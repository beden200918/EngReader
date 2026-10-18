package com.example.engreader

import android.content.Context

object Prefs {
    private const val NAME = "settings"
    private const val KEY_HISTORY = "search_history"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_FONT = "font_level"
    private const val KEY_ONLINE = "online_enhance"

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun themeMode(c: Context): Int = sp(c).getInt(KEY_THEME, 0)
    fun setThemeMode(c: Context, v: Int) = sp(c).edit().putInt(KEY_THEME, v).apply()

    fun fontLevel(c: Context): Int = sp(c).getInt(KEY_FONT, 1)
    fun setFontLevel(c: Context, v: Int) = sp(c).edit().putInt(KEY_FONT, v).apply()

    fun fontScale(c: Context): Float = when (fontLevel(c)) {
        0 -> 0.85f
        2 -> 1.15f
        3 -> 1.30f
        else -> 1.0f
    }

    fun onlineEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_ONLINE, false)
    fun setOnline(c: Context, v: Boolean) = sp(c).edit().putBoolean(KEY_ONLINE, v).apply()

    fun history(c: Context): List<String> =
        sp(c).getString(KEY_HISTORY, "")!!.split("\n").filter { it.isNotBlank() }

    fun addHistory(c: Context, word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        val list = history(c).toMutableList()
        list.remove(w)
        list.add(0, w)
        while (list.size > 100) list.removeAt(list.size - 1)
        sp(c).edit().putString(KEY_HISTORY, list.joinToString("\n")).apply()
    }

    fun removeHistory(c: Context, word: String) {
        val list = history(c).filter { it != word }
        sp(c).edit().putString(KEY_HISTORY, list.joinToString("\n")).apply()
    }

    fun clearHistory(c: Context) = sp(c).edit().remove(KEY_HISTORY).apply()
}
