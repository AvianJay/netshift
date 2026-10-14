package tw.avianjay.netshift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tw.avianjay.netshift.core.formatTime
import tw.avianjay.netshift.data.LogEntry

@Composable
fun LogScreen(entries: List<LogEntry>, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "共 ${entries.size} 筆",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) { Text("清空") }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("尚無切換紀錄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { LogItem(it) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空紀錄？") },
            text = { Text("所有切換紀錄都會被刪除，無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun LogItem(entry: LogEntry) {
    val resultColor = if (entry.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(entry.time),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val result = when {
                    entry.from == null -> "略過"
                    entry.ok -> "成功"
                    else -> "失敗"
                }
                Text(result, style = MaterialTheme.typography.labelMedium, color = resultColor)
            }
            Text(entry.event, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val change = if (entry.from != null && entry.to != null) {
                "${entry.item}：${entry.from} → ${entry.to}"
            } else {
                entry.item
            }
            Text(change, style = MaterialTheme.typography.bodyMedium)
            entry.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = resultColor)
            }
        }
    }
}
