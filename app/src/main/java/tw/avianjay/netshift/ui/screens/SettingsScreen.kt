package tw.avianjay.netshift.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tw.avianjay.netshift.R
import tw.avianjay.netshift.priv.PrivMode
import tw.avianjay.netshift.priv.PrivStatus
import tw.avianjay.netshift.priv.RootState
import tw.avianjay.netshift.priv.ShizukuState
import tw.avianjay.netshift.ui.DeviceState
import tw.avianjay.netshift.ui.MainViewModel
import tw.avianjay.netshift.ui.UiState
import tw.avianjay.netshift.ui.components.InfoRow
import tw.avianjay.netshift.ui.components.SectionCard
import tw.avianjay.netshift.ui.components.SwitchRow
import tw.avianjay.netshift.util.SystemIntents

@Composable
fun SettingsScreen(state: UiState, device: DeviceState, vm: MainViewModel) {
    val s = state.settings

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Wi-Fi 模式", icon = R.drawable.ic_wifi) {
            SwitchRow(
                title = "連上 Wi-Fi 時套用",
                checked = s.wifi.enabled,
                onCheckedChange = { vm.setWifiRule(s.wifi.copy(enabled = it)) },
            )
            LimitSelector(s.wifi.limit, enabled = s.wifi.enabled) { vm.setWifiRule(s.wifi.copy(limit = it)) }
        }

        SectionCard("行動數據模式", icon = R.drawable.ic_cellular) {
            SwitchRow(
                title = "使用行動數據時套用",
                checked = s.cellular.enabled,
                onCheckedChange = { vm.setCellularRule(s.cellular.copy(enabled = it)) },
            )
            LimitSelector(s.cellular.limit, enabled = s.cellular.enabled) {
                vm.setCellularRule(s.cellular.copy(limit = it))
            }
            HorizontalDivider()
            SwitchRow(
                title = "同時開啟 Data Saver",
                checked = s.cellularDataSaver,
                onCheckedChange = vm::setCellularDataSaver,
                subtitle = "切回 Wi-Fi 時，若 Data Saver 是本 App 開的會自動關閉；你自己開的不會動",
                enabled = s.cellular.enabled,
            )
        }

        Text(
            "規則停用時不會改動該網路的設定；但若當下的限制是本 App 設的，會還原為無限制。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PrivModeCard(state.priv, vm)

        BackgroundCard(state, device, vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivModeCard(priv: PrivStatus, vm: MainViewModel) {
    SectionCard("權限模式") {
        PrivMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = priv.mode == mode, role = Role.RadioButton, onClick = { vm.setPrivMode(mode) })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = priv.mode == mode, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(mode.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        mode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (priv.mode == PrivMode.AUTO) {
            SwitchRow(
                title = "Sui / Shizuku 不可用時改用 Root",
                checked = priv.rootFallback,
                onCheckedChange = vm::setRootFallback,
                subtitle = "預設關閉。打開時會先請求一次 root 權限",
                enabled = priv.root != RootState.NOT_FOUND,
            )
        }

        HorizontalDivider()
        InfoRow(
            "目前生效",
            priv.active?.label ?: "無",
            if (priv.active == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        InfoRow("身分", priv.active?.let { uidLabel(it.uid) } ?: "—")
        InfoRow("Sui / Shizuku", describeShizuku(priv.shizuku))
        InfoRow("Root", priv.root.description)

        val canAskShizuku = priv.shizuku is ShizukuState.NoPermission && priv.mode != PrivMode.ROOT
        val canAskRoot = priv.root == RootState.UNKNOWN || priv.root == RootState.DENIED
        if (canAskShizuku || canAskRoot) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canAskShizuku) OutlinedButton(onClick = vm::requestShizukuPermission) { Text("請求 Sui / Shizuku 授權") }
                if (canAskRoot && (priv.mode == PrivMode.ROOT || priv.rootFallback)) {
                    OutlinedButton(onClick = vm::requestRoot) { Text("請求 Root 權限") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundCard(state: UiState, device: DeviceState, vm: MainViewModel) {
    val context = LocalContext.current
    val active = state.priv.active
    SectionCard("背景執行") {
        SwitchRow(
            title = "開機自動啟動",
            checked = state.settings.startOnBoot,
            onCheckedChange = vm::setStartOnBoot,
            subtitle = "自動切換服務啟用時，開機後自動恢復",
        )
        InfoRow(
            "電池最佳化",
            if (device.batteryOptimizationIgnored) "已加入白名單" else "受限制",
            if (device.batteryOptimizationIgnored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!device.batteryOptimizationIgnored) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { SystemIntents.requestIgnoreBatteryOptimizations(context) }) {
                    Text("加入白名單")
                }
                if (active != null) {
                    OutlinedButton(onClick = vm::whitelistViaBackend) { Text("用 ${active.label} 加入") }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InfoRow(
                "通知",
                if (device.notificationsAllowed) "已允許" else "未允許（常駐通知不會顯示）",
                if (device.notificationsAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!device.notificationsAllowed) {
                OutlinedButton(onClick = { SystemIntents.notificationSettings(context) }) { Text("開啟通知設定") }
            }
        }
    }
}
