package com.example.engreader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SearchResult(
    val exact: DictEntry?,
    val prefixList: List<DictEntry>,
    val related: List<DictEntry>,
    val similar: List<DictEntry>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(dict: Dictionary, store: VocabStore) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(Prefs.history(ctx)) }
    var selected by remember { mutableStateOf<DictEntry?>(null) }

    fun saveHistory(w: String) {
        Prefs.addHistory(ctx, w)
        history = Prefs.history(ctx)
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            result = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(220)
        result = withContext(Dispatchers.IO) {
            if (q.any { it.code in 0x4E00..0x9FFF }) {
                SearchResult(null, dict.inTranslation(q), emptyList(), emptyList())
            } else {
                val e = dict.lookup(q)
                val pre = dict.suggest(q)
                val rel = if (q.length >= 2) dict.phrasesOf(q) else emptyList()
                val sim = if (e == null && pre.isEmpty()) dict.similar(q) else emptyList()
                SearchResult(e, pre, rel, sim)
            }
        }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("查单词、短语，或输入中文反查") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (query.isNotBlank()) saveHistory(query.trim())
            }),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = { query = "" }) { Text("清除") }
                }
            }
        )

        if (query.isBlank()) {
            HistoryBlock(
                items = history,
                onClick = { query = it },
                onDelete = { Prefs.removeHistory(ctx, it); history = Prefs.history(ctx) },
                onClear = { Prefs.clearHistory(ctx); history = Prefs.history(ctx) }
            )
        } else {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val r = result
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                if (r != null) {
                    r.exact?.let { e ->
                        item { SectionTitle("词条") }
                        item { ResultRow(e) { saveHistory(e.word); selected = e } }
                    }
                    if (r.prefixList.isNotEmpty()) {
                        item { SectionTitle(if (r.exact == null) "搜索结果" else "相近词条") }
                        items(r.prefixList.filter { it.word.lowercase() != r.exact?.word?.lowercase() }) { e ->
                            ResultRow(e) { saveHistory(e.word); selected = e }
                        }
                    }
                    if (r.related.isNotEmpty()) {
                        item { SectionTitle("相关短语") }
                        items(r.related) { e -> ResultRow(e) { saveHistory(e.word); selected = e } }
                    }
                    if (r.similar.isNotEmpty()) {
                        item { SectionTitle("你是不是想找") }
                        items(r.similar) { e -> ResultRow(e) { saveHistory(e.word); selected = e } }
                    }
                    if (r.exact == null && r.prefixList.isEmpty() &&
                        r.related.isEmpty() && r.similar.isEmpty()
                    ) {
                        item {
                            Text(
                                "词库中没有找到「$query」",
                                Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    val sel = selected
    if (sel != null) {
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            WordSheet(
                entry = sel,
                lemma = dict.lemmaOf(sel.word),
                related = dict.phrasesOf(sel.word),
                onAdd = {
                    store.add(sel.word, sel, "", "搜索")
                    selected = null
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ResultRow(entry: DictEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.word, style = MaterialTheme.typography.titleMedium)
                if (entry.phonetic.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "/${entry.phonetic}/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (entry.translation.isNotBlank()) {
                Text(
                    entry.translation,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryBlock(
    items: List<String>,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("输入单词或中文开始查询", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "搜索历史",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("清空") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { w ->
                AssistChip(
                    onClick = { onClick(w) },
                    label = { Text(w) },
                    trailingIcon = {
                        Text(
                            "×",
                            modifier = Modifier.clickable { onDelete(w) }.padding(4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }
}
