package tw.avianjay.netshift.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tw.avianjay.netshift.R
import tw.avianjay.netshift.core.Limits
import tw.avianjay.netshift.core.formatDataSaver
import tw.avianjay.netshift.core.formatTime
import tw.avianjay.netshift.data.AppSettings
import tw.avianjay.netshift.net.NetType
import tw.avianjay.netshift.priv.PrivMode
import tw.avianjay.netshift.priv.PrivStatus
import tw.avianjay.netshift.priv.ROOT_UID
import tw.avianjay.netshift.priv.RootState
import tw.avianjay.netshift.priv.SHELL_UID
import tw.avianjay.netshift.priv.ShizukuState
import tw.avianjay.netshift.ui.DeviceState
import tw.avianjay.netshift.ui.MainViewModel
import tw.avianjay.netshift.ui.UiState
import tw.avianjay.netshift.ui.components.InfoRow
import tw.avianjay.netshift.ui.components.NoticeCard
import tw.avianjay.netshift.ui.components.SectionCard
import tw.avianjay.netshift.ui.components.SwitchRow
import tw.avianjay.netshift.util.SystemIntents

@Composable
fun StatusScreen(state: UiState, device: DeviceState, vm: MainViewModel) {
    val context = LocalContext.current
    val priv = state.priv

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("自動切換", icon = R.drawable.ic_tab_status) {
            SwitchRow(
                title = "啟用自動切換服務",
                checked = state.settings.serviceEnabled,
                onCheckedChange = vm::setServiceEnabled,
                subtitle = if (state.serviceRunning) "前景服務執行中" else "已停止",
            )
        }

        CurrentStateCard(state, onRefresh = vm::refresh, onApply = vm::applyNow)

        PrivilegeCard(priv)

        if (priv.active == null) {
            if (priv.mode == PrivMode.ROOT) {
                NoticeCard(
                    "目前是 Root 模式，但尚未取得 root（${priv.root.description}）。",
                    error = true,
                ) {
                    if (priv.root != RootState.NOT_FOUND) {
                        TextButton(onClick = vm::requestRoot) { Text("請求 Root 權限") }
                    }
                }
            } else {
                ShizukuGuideCard(priv.shizuku, vm::requestShizukuPermission, vm::refresh)
            }
        }

        RulesSummaryCard(state.settings)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            NoticeCard(
                "此裝置為 Android ${Build.VERSION.RELEASE}。ingress_rate_limit_bytes_per_second 是 Android 13 才加入的設定，" +
                    "在這個版本上下載限速多半不會生效（Data Saver 不受影響）。",
            )
        }

        if (!device.batteryOptimizationIgnored) {
            NoticeCard("尚未加入電池最佳化白名單，常駐服務可能被系統終止。") {
                Row {
                    TextButton(onClick = { SystemIntents.requestIgnoreBatteryOptimizations(context) }) {
                        Text("前往設定")
                    }
                    if (priv.active != null) {
                        TextButton(onClick = vm::whitelistViaBackend) { Text("用 ${priv.active.label} 加入") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentStateCard(state: UiState, onRefresh: () -> Unit, onApply: () -> Unit) {
    val snap = state.snapshot
    val netIcon = when (state.network) {
        NetType.WIFI -> R.drawable.ic_wifi
        NetType.CELLULAR -> R.drawable.ic_cellular
        NetType.OTHER -> R.drawable.ic_tab_status
        NetType.NONE -> R.drawable.ic_no_network
    }
    SectionCard("目前狀態", icon = netIcon) {
        InfoRow("網路類型", state.network.label)
        InfoRow("下載限制", Limits.format(snap.limit))
        InfoRow("Data Saver", formatDataSaver(snap.dataSaver))
        if (snap.readAt > 0) {
            Text(
                "讀取時間 ${formatTime(snap.readAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        snap.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onRefresh, enabled = !state.busy) { Text("重新讀取") }
            Button(onClick = onApply, enabled = !state.busy) { Text("立即套用規則") }
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun PrivilegeCard(priv: PrivStatus) {
    val active = priv.active
    SectionCard("權限") {
        InfoRow(
            "生效中",
            active?.label ?: "無",
            if (active == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        InfoRow("身分", active?.let { uidLabel(it.uid) } ?: "—")
        InfoRow("權限模式", priv.mode.title)
        InfoRow("Sui / Shizuku", describeShizuku(priv.shizuku))
        InfoRow("Root", priv.root.description)
    }
}

@Composable
private fun RulesSummaryCard(s: AppSettings) {
    SectionCard("規則") {
        InfoRow("Wi-Fi", if (s.wifi.enabled) Limits.format(s.wifi.limit) else "停用")
        InfoRow(
            "行動數據",
            if (!s.cellular.enabled) "停用"
            else Limits.format(s.cellular.limit) + if (s.cellularDataSaver) "＋Data Saver" else "",
        )
    }
}

fun uidLabel(uid: Int): String = when (uid) {
    ROOT_UID -> "uid 0（root）"
    SHELL_UID -> "uid 2000（adb shell）"
    else -> "uid $uid"
}

fun describeShizuku(state: ShizukuState): String = when (state) {
    ShizukuState.NotRunning -> "服務未執行"
    ShizukuState.Unsupported -> "版本過舊"
    is ShizukuState.NoPermission -> if (state.permanentlyDenied) "已拒絕（需手動允許）" else "未授權"
    is ShizukuState.Ready -> "${state.identity}・API v${state.apiVersion}"
}
