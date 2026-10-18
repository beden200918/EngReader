package com.example.engreader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: Int,
    fontLevel: Int,
    online: Boolean,
    onTheme: (Int) -> Unit,
    onFont: (Int) -> Unit,
    onOnline: (Boolean) -> Unit,
    onUpdateDict: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("使用说明", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "例句翻译说明：开启「联网增强」时，例句会联网翻译成中文；关闭时，使用词库内置的中文译文。两种模式都只翻译你主动查询或收藏的内容。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("外观", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = themeMode == 0, onClick = { onTheme(0) }, label = { Text("跟随系统") })
                    FilterChip(selected = themeMode == 1, onClick = { onTheme(1) }, label = { Text("浅色") })
                    FilterChip(selected = themeMode == 2, onClick = { onTheme(2) }, label = { Text("深色") })
                }
                Spacer(Modifier.height(12.dp))
                Text("字号", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = fontLevel == 0, onClick = { onFont(0) }, label = { Text("小") })
                    FilterChip(selected = fontLevel == 1, onClick = { onFont(1) }, label = { Text("标准") })
                    FilterChip(selected = fontLevel == 2, onClick = { onFont(2) }, label = { Text("大") })
                    FilterChip(selected = fontLevel == 3, onClick = { onFont(3) }, label = { Text("特大") })
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("联网增强", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后可联网翻译例句、补充生僻词搭配",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = online, onCheckedChange = onOnline)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("词库", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "当前词库：全量 ECDICT（约 77 万词条）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onUpdateDict) { Text("导入新的 dictionary.db") }
            }
        }

        Text(
            "EngReader 1.1 · 数据全部存储在本机",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
