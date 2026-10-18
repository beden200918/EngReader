package com.example.engreader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val ctx = LocalContext.current
    var appId by remember { mutableStateOf(Prefs.baiduAppId(ctx)) }
    var apiKey by remember { mutableStateOf(Prefs.baiduApiKey(ctx)) }
    var secret by remember { mutableStateOf(Prefs.baiduSecret(ctx)) }
    var saved by remember { mutableStateOf(false) }

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
                            "开启后可联网翻译例句。查词内容会发送到百度翻译服务器，关闭时完全离线。",
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
                Text("百度翻译密钥", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "仅保存在本机，不会上传。在百度智能云「文本翻译-通用版」应用中获取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it; saved = false },
                    label = { Text("APP ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it; saved = false },
                    label = { Text("Secret Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        Prefs.setBaiduAppId(ctx, appId.trim())
                        Prefs.setBaiduApiKey(ctx, apiKey.trim())
                        Prefs.setBaiduSecret(ctx, secret.trim())
                        saved = true
                    }) { Text("保存密钥") }
                    if (saved) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "已保存",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
