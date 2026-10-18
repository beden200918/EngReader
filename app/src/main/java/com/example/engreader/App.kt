package com.example.engreader

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class Word(val text: String, val start: Int, val end: Int)

object Tokenizer {
    private val wordRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

    fun wordTokens(text: String): List<Word> =
        wordRegex.findAll(text).map { Word(it.value, it.range.first, it.range.last + 1) }.toList()

    fun splitParagraphs(text: String): List<String> =
        text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .map { it.trim() }.filter { it.isNotEmpty() }

    fun sentenceAround(text: String, start: Int, end: Int): String {
        var s = start.coerceIn(0, text.length)
        while (s > 0 && text[s - 1] !in ".!?\n") s--
        var e = end.coerceIn(0, text.length)
        while (e < text.length && text[e] !in ".!?\n") e++
        if (e < text.length) e++
        return text.substring(s, e).trim()
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("crash", MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            prefs.edit().putString("last_crash", Log.getStackTraceString(error)).commit()
            previous?.uncaughtException(thread, error)
        }
        super.onCreate(savedInstanceState)
        setContent {
            val crash = prefs.getString("last_crash", null)
            if (crash != null) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        CrashScreen(crash) {
                            prefs.edit().remove("last_crash").apply()
                        }
                    }
                }
            } else {
                App()
            }
        }
    }
}

@Composable
private fun CrashScreen(trace: String, onClear: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(
            "上次启动崩溃了",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
        Text(trace, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", trace))
            }) { Text("复制错误信息") }
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
}
private enum class Tab { Reader, Search, Vocab, Settings }

private fun scaleOf(level: Int): Float = when (level) {
    0 -> 0.85f
    2 -> 1.15f
    3 -> 1.30f
    else -> 1.0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var themeMode by remember { mutableStateOf(Prefs.themeMode(ctx)) }
    var fontLevel by remember { mutableStateOf(Prefs.fontLevel(ctx)) }
    var online by remember { mutableStateOf(Prefs.onlineEnabled(ctx)) }

    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    EngReaderTheme(darkTheme = dark, fontScale = scaleOf(fontLevel)) {
        val scope = rememberCoroutineScope()
        val store = remember { VocabStore(ctx) }
        var tab by remember { mutableStateOf(Tab.Reader) }
        var dict by remember { mutableStateOf<Dictionary?>(null) }
        var loading by remember { mutableStateOf(true) }
        var reloading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            dict = withContext(Dispatchers.IO) { Dictionary(ctx) }
            loading = false
        }

        val importDb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                reloading = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val tmp = File(ctx.filesDir, "dictionary_new.db")
                            ctx.contentResolver.openInputStream(uri)!!.use { i ->
                                tmp.outputStream().use { o -> i.copyTo(o) }
                            }
                            val probe = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY)
                            val valid = probe.rawQuery(
                                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='dict'", null
                            ).use { it.moveToFirst() && it.getInt(0) > 0 }
                            probe.close()
                            if (valid) {
                                val target = File(ctx.filesDir, "dictionary.db")
                                if (target.exists()) target.delete()
                                tmp.renameTo(target)
                            } else tmp.delete()
                            valid
                        }.getOrDefault(false)
                    }
                    if (ok) dict = withContext(Dispatchers.IO) { Dictionary(ctx) }
                    reloading = false
                }
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Reader,
                        onClick = { tab = Tab.Reader },
                        icon = { Text("📖") },
                        label = { Text("阅读") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Search,
                        onClick = { tab = Tab.Search },
                        icon = { Text("🔍") },
                        label = { Text("搜索") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Vocab,
                        onClick = { tab = Tab.Vocab },
                        icon = { Text("📒") },
                        label = { Text("生词本") }
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Settings,
                        onClick = { tab = Tab.Settings },
                        icon = { Text("⚙") },
                        label = { Text("设置") }
                    )
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when {
                    loading -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("首次启动正在解压词库，请稍候…")
                    }
                    reloading -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("正在更新词库…")
                    }
                    tab == Tab.Reader -> ReaderScreen(dict!!, store)
                    tab == Tab.Search -> SearchScreen(dict!!, store)
                    tab == Tab.Vocab -> VocabScreen(store)
                    else -> SettingsScreen(
                        themeMode = themeMode,
                        fontLevel = fontLevel,
                        online = online,
                        onTheme = {
                            themeMode = it
                            Prefs.setThemeMode(ctx, it)
                        },
                        onFont = {
                            fontLevel = it
                            Prefs.setFontLevel(ctx, it)
                        },
                        onOnline = {
                            online = it
                            Prefs.setOnline(ctx, it)
                        },
                        onUpdateDict = {
                            importDb.launch(arrayOf("application/octet-stream", "*/*"))
                        }
                    )
                }
            }
        }
    }
}
@Composable
private fun ReaderScreen(dict: Dictionary, store: VocabStore) {
    val ctx = LocalContext.current

    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var source by remember { mutableStateOf("") }
    var showPaste by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    var selPara by remember { mutableStateOf(-1) }
    var anchor by remember { mutableStateOf(-1) }
    var selFrom by remember { mutableStateOf(-1) }
    var selTo by remember { mutableStateOf(-1) }

    var entry by remember { mutableStateOf<DictEntry?>(null) }
    var hint by remember { mutableStateOf("") }

    val openTxt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val t = runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!t.isNullOrBlank()) {
                paragraphs = Tokenizer.splitParagraphs(t)
                source = uri.lastPathSegment ?: "导入文本"
                selPara = -1; anchor = -1; selFrom = -1; selTo = -1
            }
        }
    }

    val allWords = remember(paragraphs) { paragraphs.map { Tokenizer.wordTokens(it) } }
    val words = if (selPara in paragraphs.indices) allWords[selPara] else emptyList()
    val term: String? =
        if (selPara in paragraphs.indices && selFrom in words.indices && selTo in words.indices)
            words.subList(selFrom, selTo + 1).joinToString(" ") { it.text }
        else null

    LaunchedEffect(term) {
        if (term == null) {
            entry = null; hint = ""
        } else {
            entry = withContext(Dispatchers.IO) { dict.lookup(term) }
            hint = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { openTxt.launch(arrayOf("text/plain")) }) { Text("打开 txt") }
            OutlinedButton(onClick = { showPaste = true }) { Text("粘贴文本") }
            if (paragraphs.isNotEmpty()) {
                TextButton(onClick = {
                    paragraphs = emptyList()
                    selPara = -1; anchor = -1; selFrom = -1; selTo = -1
                }) { Text("清空") }
            }
        }

        Box(Modifier.weight(1f)) {
            if (paragraphs.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("使用说明", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. 导入或粘贴一段英文文本。\n2. 点一下某个词 = 选中它。\n3. 再点相邻的词 = 自动扩成词组。\n4. 长按任意词 = 以它为起点重新选。\n5. 卡片里点“加入生词本”收录，附带原句。",
                        lineHeight = 26.sp
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(paragraphs) { idx, p ->
                        SelectableParagraph(
                            text = p,
                            selFrom = if (selPara == idx) selFrom else null,
                            selTo = if (selPara == idx) selTo else null,
                            onTapWord = { w ->
                                if (selPara != idx) {
                                    selPara = idx; anchor = w; selFrom = w; selTo = w
                                } else if (anchor == w) {
                                    selFrom = w; selTo = w
                                } else {
                                    selFrom = minOf(anchor, w)
                                    selTo = maxOf(anchor, w)
                                }
                            },
                            onLongPressWord = { w ->
                                selPara = idx; anchor = w; selFrom = w; selTo = w
                            }
                        )
                    }
                }
            }
        }
        if (term != null) {
            LookupCard(
                term = term,
                entry = entry,
                hint = hint,
                onAdd = {
                    val sent =
                        if (selPara in paragraphs.indices && selFrom in words.indices && selTo in words.indices)
                            Tokenizer.sentenceAround(
                                paragraphs[selPara],
                                words[selFrom].start,
                                words[selTo].end
                            )
                        else ""
                    val ok = store.add(term, entry, sent, source)
                    hint = if (ok) "✓ 已加入生词本" else "这个词已经在生词本里了"
                },
                onClear = { selPara = -1; anchor = -1; selFrom = -1; selTo = -1 }
            )
        }
    }

    if (showPaste) {
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("粘贴英文文本") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 320.dp),
                    placeholder = { Text("把文章粘贴到这里…") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pasteText.isNotBlank()) {
                        paragraphs = Tokenizer.splitParagraphs(pasteText)
                        source = "粘贴文本"
                        selPara = -1; anchor = -1; selFrom = -1; selTo = -1
                    }
                    showPaste = false
                    pasteText = ""
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showPaste = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SelectableParagraph(
    text: String,
    selFrom: Int?,
    selTo: Int?,
    onTapWord: (Int) -> Unit,
    onLongPressWord: (Int) -> Unit
) {
    val words = remember(text) { Tokenizer.wordTokens(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotated = remember(text, selFrom, selTo) {
        buildAnnotatedString {
            append(text)
            if (selFrom != null && selTo != null &&
                selFrom in words.indices && selTo in words.indices && selFrom <= selTo
            ) {
                addStyle(SpanStyle(background = Color(0xFFFFE082)), words[selFrom].start, words[selTo].end)
            }
        }
    }

    Text(
        text = annotated,
        style = TextStyle(fontSize = 17.sp, lineHeight = 28.sp),
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(words) {
                fun hit(pos: Offset): Int? {
                    val l = layout ?: return null
                    val off = l.getOffsetForPosition(pos)
                    val exact = words.indexOfFirst { off >= it.start && off < it.end }
                    if (exact >= 0) return exact
                    val near = words.minByOrNull {
                        minOf(abs(it.start - off), abs(it.end - off))
                    } ?: return null
                    return words.indexOf(near)
                }
                detectTapGestures(
                    onTap = { hit(it)?.let(onTapWord) },
                    onLongPress = { hit(it)?.let(onLongPressWord) }
                )
            }
    )
}
@Composable
private fun LookupCard(
    term: String,
    entry: DictEntry?,
    hint: String,
    onAdd: () -> Unit,
    onClear: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(8.dp)) {
        Column(
            Modifier
                .padding(16.dp)
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(term, style = MaterialTheme.typography.titleLarge)
                if (!entry?.phonetic.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text("/${entry!!.phonetic}/", color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (entry == null) {
                Text("词库未收录该词条", color = MaterialTheme.colorScheme.error)
            } else {
                if (entry.pos.isNotBlank()) {
                    Text(entry.pos, style = MaterialTheme.typography.labelMedium)
                }
                if (entry.translation.isNotBlank()) {
                    Text(entry.translation)
                }
                if (entry.definition.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "EN: ${entry.definition}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text("加入生词本") }
                TextButton(onClick = onClear) { Text("取消选择") }
            }
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(hint, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun VocabScreen(store: VocabStore) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf(store.list()) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(store.exportCsv().toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("共 ${rows.size} 条", Modifier.weight(1f))
            TextButton(onClick = { export.launch("vocab.csv") }) { Text("导出 CSV") }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("生词本还是空的")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.term,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    store.delete(item.id)
                                    rows = store.list()
                                }) { Text("删除") }
                            }
                            if (item.phonetic.isNotBlank()) {
                                Text("/${item.phonetic}/", style = MaterialTheme.typography.bodySmall)
                            }
                            if (item.translation.isNotBlank()) Text(item.translation)
                            if (item.context.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.context,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
