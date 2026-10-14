package tw.avianjay.netshift.core

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.avianjay.netshift.data.AppSettings
import tw.avianjay.netshift.data.LogEntry
import tw.avianjay.netshift.data.LogRepository
import tw.avianjay.netshift.data.SettingsRepository
import tw.avianjay.netshift.net.NetType
import tw.avianjay.netshift.net.NetworkMonitor
import tw.avianjay.netshift.priv.BackendManager
import tw.avianjay.netshift.priv.PrivBackend

/**
 * 依「目前網路 + 使用者規則」決定下載限制與 Data Saver，並透過特權 backend 寫入系統。
 *
 * 規則：
 * - Wi-Fi / 行動數據規則啟用 → 套用該規則的限制值
 * - 規則停用（或其他網路）→ 不動；但若目前的限制是本 App 設的，還原為 -1
 * - 行動數據 + 自動 Data Saver → 開；離開行動數據時只關掉本 App 自己開的
 * - 無網路 → 什麼都不做，等下一個網路
 */
class NetShiftController(
    private val settings: SettingsRepository,
    private val network: NetworkMonitor,
    private val backends: BackendManager,
    private val logs: LogRepository,
) {
    /** 最後一次從系統讀到的值；null = 讀不到 */
    data class Snapshot(
        val limit: Long? = null,
        val dataSaver: Boolean? = null,
        val readAt: Long = 0L,
        val error: String? = null,
    )

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val mutex = Mutex()

    private data class Inputs(val net: NetType, val rules: AppSettings, val backend: String?)

    /** 由前景服務呼叫，持續監聽直到呼叫端的 coroutine 被取消。 */
    @OptIn(FlowPreview::class)
    suspend fun runAutoSwitch() {
        var prev: Inputs? = null
        combine(
            network.type,
            // 服務開關本身不影響套用結果，排除掉免得多觸發一次
            settings.settings.map { it.copy(serviceEnabled = false, startOnBoot = false) },
            backends.status.map { it.active?.label },
        ) { net, rules, backend -> Inputs(net, rules, backend) }
            .distinctUntilChanged()
            // Wi-Fi ↔ 行動數據交接時常會短暫出現「無網路」，等穩定再套用
            .debounce(SETTLE_MS)
            .collect { cur ->
                val trigger = describe(prev, cur)
                prev = cur
                applyNow(trigger)
            }
    }

    suspend fun applyNow(trigger: String) = locked {
        val backend = backends.resolve()
        if (backend == null) {
            val reason = backends.unavailableReason()
            _snapshot.update { it.copy(error = reason) }
            logs.add(LogEntry(now(), trigger, "略過", null, null, ok = false, note = reason))
            return@locked
        }

        val net = network.type.value
        val s = settings.current
        var limit = readLimit(backend)
        var dataSaver = readDataSaver(backend)
        var error: String? = null

        if (net != NetType.NONE) {
            val targetLimit = planLimit(net, s)
            if (targetLimit != null && targetLimit != limit) {
                val r = backend.exec(SystemCommands.setIngressLimit(targetLimit))
                val after = readLimit(backend)
                val ok = r.ok && after == targetLimit
                val note = when {
                    ok -> null
                    !r.ok -> r.message
                    else -> "寫入後讀回 ${Limits.format(after)}，與預期不符"
                }
                if (ok) settings.limitManaged = targetLimit != Limits.UNLIMITED else error = "下載限速：$note"
                logs.add(LogEntry(now(), trigger, ITEM_LIMIT, Limits.format(limit), Limits.format(targetLimit), ok, note))
                limit = after
            }

            val targetDs = planDataSaver(net, s)
            if (targetDs != null && dataSaver == null) {
                error = error ?: "無法讀取 Data Saver 狀態"
            } else if (targetDs != null && targetDs != dataSaver) {
                val before = dataSaver
                val r = backend.exec(SystemCommands.setDataSaver(targetDs))
                val after = readDataSaver(backend)
                val ok = r.ok && after == targetDs
                val note = when {
                    ok -> null
                    !r.ok -> r.message
                    else -> "寫入後讀回 ${formatDataSaver(after)}，與預期不符"
                }
                // 只有「本來關、被我們打開」才算我們管的；關掉後就交還給使用者
                if (ok) settings.dataSaverManaged = targetDs && before == false else error = error ?: "Data Saver：$note"
                logs.add(LogEntry(now(), trigger, ITEM_DATA_SAVER, formatDataSaver(before), formatDataSaver(targetDs), ok, note))
                dataSaver = after
            }
        }

        _snapshot.value = Snapshot(limit, dataSaver, now(), error)
    }

    /** 只讀取系統目前的值，不寫入。 */
    suspend fun refresh() = locked {
        val backend = backends.resolve()
        if (backend == null) {
            _snapshot.update { it.copy(error = backends.unavailableReason()) }
            return@locked
        }
        _snapshot.value = Snapshot(readLimit(backend), readDataSaver(backend), now(), null)
    }

    private fun planLimit(net: NetType, s: AppSettings): Long? {
        val rule = when (net) {
            NetType.WIFI -> s.wifi
            NetType.CELLULAR -> s.cellular
            else -> null
        }
        return when {
            rule != null && rule.enabled -> rule.limit
            settings.limitManaged -> Limits.UNLIMITED
            else -> null
        }
    }

    private fun planDataSaver(net: NetType, s: AppSettings): Boolean? = when {
        net == NetType.CELLULAR && s.cellular.enabled && s.cellularDataSaver -> true
        settings.dataSaverManaged -> false
        else -> null
    }

    private suspend fun readLimit(b: PrivBackend): Long? =
        b.exec(SystemCommands.GET_INGRESS_LIMIT).takeIf { it.ok }?.let { SystemCommands.parseIngressLimit(it.stdout) }

    private suspend fun readDataSaver(b: PrivBackend): Boolean? =
        b.exec(SystemCommands.GET_DATA_SAVER).takeIf { it.ok }?.let { SystemCommands.parseDataSaver(it.stdout) }

    private fun describe(prev: Inputs?, cur: Inputs): String = when {
        prev == null -> "服務啟動"
        prev.net != cur.net -> "網路變更 ${prev.net.label} → ${cur.net.label}"
        prev.backend != cur.backend -> "權限變更：${cur.backend ?: "無可用 backend"}"
        prev.rules != cur.rules -> "設定變更"
        else -> "重新確認"
    }

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
        _busy.value = true
        try {
            block()
        } finally {
            _busy.value = false
        }
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val SETTLE_MS = 1_000L
        const val ITEM_LIMIT = "下載限速"
        const val ITEM_DATA_SAVER = "Data Saver"
    }
}
