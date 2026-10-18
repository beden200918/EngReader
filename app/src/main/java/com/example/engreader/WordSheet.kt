package com.example.engreader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WordSheet(
    entry: DictEntry,
    lemma: String?,
    related: List<DictEntry>,
    onAdd: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.word, style = MaterialTheme.typography.headlineSmall)
            if (entry.phonetic.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "/${entry.phonetic}/",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!lemma.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "原形：$lemma",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (entry.pos.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(entry.pos, style = MaterialTheme.typography.labelMedium)
        }

        if (entry.translation.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(entry.translation, style = MaterialTheme.typography.bodyLarge)
        }

        if (entry.definition.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "EN: ${entry.definition}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (related.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "相关短语",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            related.forEach { r ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(r.word, style = MaterialTheme.typography.titleSmall)
                    if (r.translation.isNotBlank()) {
                        Text(
                            r.translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("加入生词本")
        }
        Spacer(Modifier.height(24.dp))
    }
}
