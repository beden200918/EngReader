package com.example.engreader

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

// ---------- 分词 ----------

data class Word(val text: String, val start: Int, val end: Int)

object Tokenizer {
    // 只把字母词当可点单元；数字、标点、空格不可点
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

// ---------- 入口 ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private enum class Tab { Reader, Vocab }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
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

    // 更新词库：选一个新的 dictionary.db，校验后原地替换
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
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.Reader) "英文阅读词典" else "生词本") },
                actions = {
                    TextButton(onClick = { importDb.launch(arrayOf("application/octet-stream", "*/*")) }) {
                        Text("更新词库")
                    }
                    TextButton(onClick = { tab = if (tab == Tab.Reader) Tab.Vocab else Tab.Reader }) {
                        Text(if (tab == Tab.Reader) "生词本" else "阅读")
                    }
                }
            )
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
                else -> VocabScreen(store)
            }
        }
    }
}

// ---------- 阅读页 ----------

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

    val openTxt = rememberLauncherForActivityResult(Activ
