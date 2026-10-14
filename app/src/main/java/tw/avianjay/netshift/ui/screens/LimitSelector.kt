package tw.avianjay.netshift.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tw.avianjay.netshift.core.Limits

private const val MAX_KBPS = 10_000_000L // 10 Gbps，再大也沒意義

/** 預設值 chips + 自訂（以 kbps 輸入，存成 bytes/s） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitSelector(value: Long, enabled: Boolean, onChange: (Long) -> Unit) {
    var showCustom by remember { mutableStateOf(false) }
    val isPreset = value in Limits.PRESETS

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Limits.PRESETS.forEach { preset ->
            FilterChip(
                selected = value == preset,
                onClick = { onChange(preset) },
                label = { Text(Limits.format(preset)) },
                enabled = enabled,
            )
        }
        FilterChip(
            selected = !isPreset,
            onClick = { showCustom = true },
            label = { Text(if (isPreset) "自訂…" else "自訂：${Limits.format(value)}") },
            enabled = enabled,
        )
    }
    Text(
        if (value < 0) "目前：無限制（-1）" else "目前：${Limits.format(value)}（$value B/s）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (showCustom) {
        CustomLimitDialog(
            initialKbps = if (value > 0) value / Limits.BYTES_PER_KBPS else 1_000L,
            onDismiss = { showCustom = false },
            onConfirm = {
                onChange(Limits.fromKbps(it))
                showCustom = false
            },
        )
    }
}

@Composable
private fun CustomLimitDialog(initialKbps: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var text by remember { mutableStateOf(initialKbps.toString()) }
    val kbps = text.toLongOrNull()?.takeIf { it in 1..MAX_KBPS }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自訂下載限制") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter(Char::isDigit).take(8) },
                    label = { Text("速度（kbps）") },
                    singleLine = true,
                    isError = kbps == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    if (kbps == null) "請輸入 1 ~ $MAX_KBPS 之間的整數"
                    else "= ${Limits.format(Limits.fromKbps(kbps))}（${Limits.fromKbps(kbps)} B/s）",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { kbps?.let(onConfirm) }, enabled = kbps != null) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
