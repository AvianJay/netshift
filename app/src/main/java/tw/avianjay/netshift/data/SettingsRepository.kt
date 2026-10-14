package tw.avianjay.netshift.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import tw.avianjay.netshift.core.Limits
import tw.avianjay.netshift.priv.PrivMode

/** 單一網路類型的規則：[limit] 單位為 bytes/s，-1 = 無限制 */
data class Rule(val enabled: Boolean, val limit: Long)

data class AppSettings(
    val wifi: Rule = Rule(enabled = true, limit = Limits.UNLIMITED),
    val cellular: Rule = Rule(enabled = true, limit = 125_000L),
    /** 切到行動數據時順便開 Data Saver */
    val cellularDataSaver: Boolean = false,
    val privMode: PrivMode = PrivMode.AUTO,
    /** 自動模式下 Sui / Shizuku 不可用時改用 root；預設關閉，要使用者自己打開 */
    val rootFallback: Boolean = false,
    /** 自動切換服務是否啟用（也決定開機要不要自動啟動） */
    val serviceEnabled: Boolean = false,
    val startOnBoot: Boolean = true,
)

/** SharedPreferences 版的設定。BootReceiver 需要同步讀取，所以不用 DataStore。 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        save(_settings.updateAndGet(transform))
    }

    /** 目前生效的下載限制是本 App 設的（且不是 -1）；規則停用時靠它決定要不要還原 */
    var limitManaged: Boolean
        get() = prefs.getBoolean(K_LIMIT_MANAGED, false)
        set(value) = prefs.edit { putBoolean(K_LIMIT_MANAGED, value) }

    /** Data Saver 是本 App 打開的；離開行動數據時只關掉自己開的，不動使用者手動開的 */
    var dataSaverManaged: Boolean
        get() = prefs.getBoolean(K_DS_MANAGED, false)
        set(value) = prefs.edit { putBoolean(K_DS_MANAGED, value) }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            wifi = Rule(
                prefs.getBoolean(K_WIFI_ON, d.wifi.enabled),
                prefs.getLong(K_WIFI_LIMIT, d.wifi.limit),
            ),
            cellular = Rule(
                prefs.getBoolean(K_CELL_ON, d.cellular.enabled),
                prefs.getLong(K_CELL_LIMIT, d.cellular.limit),
            ),
            cellularDataSaver = prefs.getBoolean(K_CELL_DS, d.cellularDataSaver),
            privMode = prefs.getString(K_PRIV_MODE, null)
                ?.let { name -> PrivMode.entries.firstOrNull { it.name == name } }
                ?: d.privMode,
            rootFallback = prefs.getBoolean(K_ROOT_FALLBACK, d.rootFallback),
            serviceEnabled = prefs.getBoolean(K_SERVICE, d.serviceEnabled),
            startOnBoot = prefs.getBoolean(K_BOOT, d.startOnBoot),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit {
            putBoolean(K_WIFI_ON, s.wifi.enabled)
            putLong(K_WIFI_LIMIT, s.wifi.limit)
            putBoolean(K_CELL_ON, s.cellular.enabled)
            putLong(K_CELL_LIMIT, s.cellular.limit)
            putBoolean(K_CELL_DS, s.cellularDataSaver)
            putString(K_PRIV_MODE, s.privMode.name)
            putBoolean(K_ROOT_FALLBACK, s.rootFallback)
            putBoolean(K_SERVICE, s.serviceEnabled)
            putBoolean(K_BOOT, s.startOnBoot)
        }
    }

    private companion object {
        const val K_WIFI_ON = "wifi_enabled"
        const val K_WIFI_LIMIT = "wifi_limit"
        const val K_CELL_ON = "cellular_enabled"
        const val K_CELL_LIMIT = "cellular_limit"
        const val K_CELL_DS = "cellular_data_saver"
        const val K_PRIV_MODE = "priv_mode"
        const val K_ROOT_FALLBACK = "root_fallback"
        const val K_SERVICE = "service_enabled"
        const val K_BOOT = "start_on_boot"
        const val K_LIMIT_MANAGED = "limit_managed"
        const val K_DS_MANAGED = "data_saver_managed"
    }
}
