package tw.avianjay.netshift.priv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import tw.avianjay.netshift.data.AppSettings
import tw.avianjay.netshift.data.SettingsRepository

/** 目前實際生效的 backend 與其身分 */
data class ActiveBackend(val label: String, val uid: Int)

data class PrivStatus(
    val mode: PrivMode,
    val rootFallback: Boolean,
    val shizuku: ShizukuState,
    val root: RootState,
    val active: ActiveBackend?,
)

/** 依「權限模式」設定挑出要用的 backend。 */
class BackendManager(
    scope: CoroutineScope,
    private val settings: SettingsRepository,
    val shizuku: ShizukuBackend,
    val root: RootBackend,
) {
    val status: StateFlow<PrivStatus> =
        combine(settings.settings, shizuku.state, root.state, ::buildStatus)
            .distinctUntilChanged()
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                buildStatus(settings.current, shizuku.state.value, root.state.value),
            )

    /**
     * 取得要用的 backend；沒有可用的就回傳 null。
     * 自動模式只有在使用者打開「退回 root」時才會碰 su，所以預設不會跳出 Magisk 授權視窗。
     */
    suspend fun resolve(): PrivBackend? {
        val s = settings.current
        if (s.privMode != PrivMode.ROOT && shizuku.isAvailable()) return shizuku
        val wantRoot = s.privMode == PrivMode.ROOT || (s.privMode == PrivMode.AUTO && s.rootFallback)
        return if (wantRoot && root.ensure()) root else null
    }

    suspend fun requestRoot(): Boolean = root.request()

    /** 給 UI 用的「為什麼現在不能套用」 */
    fun unavailableReason(): String = when (settings.current.privMode) {
        PrivMode.ROOT -> "未取得 root 權限"
        else -> when (val s = shizuku.state.value) {
            ShizukuState.NotRunning -> "Sui / Shizuku 服務未執行"
            ShizukuState.Unsupported -> "Shizuku 版本過舊"
            is ShizukuState.NoPermission -> "尚未授權本 App 使用 Sui / Shizuku"
            is ShizukuState.Ready -> "${s.identity} 暫時無法使用"
        }
    }

    private fun buildStatus(s: AppSettings, sz: ShizukuState, rt: RootState): PrivStatus {
        val viaShizuku = (sz as? ShizukuState.Ready)?.let { ActiveBackend(it.identity, it.uid) }
        val viaRoot = if (rt == RootState.GRANTED) ActiveBackend(root.label, ROOT_UID) else null
        val active = when (s.privMode) {
            PrivMode.SHIZUKU -> viaShizuku
            PrivMode.ROOT -> viaRoot
            PrivMode.AUTO -> viaShizuku ?: viaRoot?.takeIf { s.rootFallback }
        }
        return PrivStatus(s.privMode, s.rootFallback, sz, rt, active)
    }
}
