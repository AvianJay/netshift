package tw.avianjay.netshift.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.avianjay.netshift.NetShiftApp
import tw.avianjay.netshift.core.NetShiftController
import tw.avianjay.netshift.core.SystemCommands
import tw.avianjay.netshift.data.AppSettings
import tw.avianjay.netshift.data.LogEntry
import tw.avianjay.netshift.data.Rule
import tw.avianjay.netshift.net.NetType
import tw.avianjay.netshift.priv.PrivMode
import tw.avianjay.netshift.priv.PrivStatus
import tw.avianjay.netshift.priv.RootState
import tw.avianjay.netshift.priv.ShizukuState
import tw.avianjay.netshift.service.NetShiftService
import tw.avianjay.netshift.util.SystemIntents

data class UiState(
    val network: NetType,
    val settings: AppSettings,
    val priv: PrivStatus,
    val snapshot: NetShiftController.Snapshot,
    val serviceRunning: Boolean,
    val busy: Boolean,
)

/** 這些值沒有變更通知，在 onResume 時重新讀一次 */
data class DeviceState(
    val batteryOptimizationIgnored: Boolean = true,
    val notificationsAllowed: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as NetShiftApp).container

    private val initial = UiState(
        network = c.network.type.value,
        settings = c.settings.current,
        priv = c.backends.status.value,
        snapshot = c.controller.snapshot.value,
        serviceRunning = NetShiftService.running.value,
        busy = false,
    )

    val uiState: StateFlow<UiState> = combine(
        c.network.type,
        c.settings.settings,
        c.backends.status,
        c.controller.snapshot,
        NetShiftService.running,
    ) { net, settings, priv, snapshot, running ->
        UiState(net, settings, priv, snapshot, running, busy = false)
    }
        .combine(c.controller.busy) { state, busy -> state.copy(busy = busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val logs: StateFlow<List<LogEntry>> = c.logs.entries

    private val _device = MutableStateFlow(readDevice())
    val device: StateFlow<DeviceState> = _device.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun onResume() {
        c.shizuku.refresh()
        _device.value = readDevice()
        c.appScope.launch { c.controller.refresh() }
    }

    // ---- 服務 ----

    fun setServiceEnabled(on: Boolean) {
        c.settings.update { it.copy(serviceEnabled = on) }
        if (on) NetShiftService.start(getApplication()) else NetShiftService.stop(getApplication())
    }

    fun setStartOnBoot(on: Boolean) = c.settings.update { it.copy(startOnBoot = on) }

    // ---- 規則 ----

    fun setWifiRule(rule: Rule) = c.settings.update { it.copy(wifi = rule) }

    fun setCellularRule(rule: Rule) = c.settings.update { it.copy(cellular = rule) }

    fun setCellularDataSaver(on: Boolean) = c.settings.update { it.copy(cellularDataSaver = on) }

    fun applyNow() {
        c.appScope.launch { c.controller.applyNow("手動套用") }
    }

    fun refresh() {
        c.shizuku.refresh()
        c.appScope.launch { c.controller.refresh() }
    }

    // ---- 權限 ----

    fun setPrivMode(mode: PrivMode) {
        c.settings.update { it.copy(privMode = mode) }
        if (mode == PrivMode.ROOT && c.root.state.value != RootState.GRANTED) requestRoot()
    }

    /** 打開前先真的拿一次 root，拿不到就不打開，避免之後在背景默默失敗 */
    fun setRootFallback(on: Boolean) {
        if (!on) {
            c.settings.update { it.copy(rootFallback = false) }
            return
        }
        c.appScope.launch {
            if (c.backends.requestRoot()) {
                c.settings.update { it.copy(rootFallback = true) }
            } else {
                _messages.send("無法取得 root 權限（${c.root.state.value.description}）")
            }
        }
    }

    fun requestRoot() {
        c.appScope.launch {
            val ok = c.backends.requestRoot()
            _messages.send(if (ok) "已取得 root 權限" else "無法取得 root 權限（${c.root.state.value.description}）")
        }
    }

    fun requestShizukuPermission() {
        if (c.shizuku.requestPermission()) return
        val msg = when (val s = c.shizuku.state.value) {
            ShizukuState.NotRunning -> "Sui / Shizuku 服務未執行"
            ShizukuState.Unsupported -> "Shizuku 版本過舊，請更新"
            is ShizukuState.NoPermission -> "先前已拒絕且不再詢問，請到 Shizuku / Sui 管理介面手動允許"
            is ShizukuState.Ready -> "已授權（${s.identity}）"
        }
        viewModelScope.launch { _messages.send(msg) }
    }

    // ---- 背景執行 ----

    /** 用目前的特權 backend 直接把自己加進 deviceidle 白名單，免去系統對話框 */
    fun whitelistViaBackend() {
        c.appScope.launch {
            val backend = c.backends.resolve()
            if (backend == null) {
                _messages.send(c.backends.unavailableReason())
                return@launch
            }
            val r = backend.exec(SystemCommands.whitelistDeviceIdle(getApplication<Application>().packageName))
            _device.value = readDevice()
            _messages.send(if (r.ok) "已加入電池最佳化白名單" else "加入失敗：${r.message}")
        }
    }

    fun clearLogs() = c.logs.clear()

    private fun readDevice(): DeviceState {
        val app = getApplication<Application>()
        return DeviceState(
            batteryOptimizationIgnored = SystemIntents.isIgnoringBatteryOptimizations(app),
            notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
}
