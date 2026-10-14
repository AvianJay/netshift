package tw.avianjay.netshift.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 一筆切換紀錄。[from] / [to] 為 null 表示這筆不是數值變更（例如「略過」）。
 */
data class LogEntry(
    val time: Long,
    /** 觸發事件，例如「網路變更 Wi-Fi → 行動數據」 */
    val event: String,
    /** 變更項目，例如「下載限速」、「Data Saver」 */
    val item: String,
    val from: String?,
    val to: String?,
    val ok: Boolean,
    val note: String? = null,
)

/** 切換紀錄，新的在前，存成 filesDir 裡的一個 JSON 檔。 */
class LogRepository(context: Context, private val scope: CoroutineScope) {

    private val file = File(context.filesDir, "switch_log.json")
    private val writeLock = Mutex()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val stored = read()
            // 讀檔期間可能已經有新紀錄進來，舊的接在後面
            _entries.update { (it + stored).take(MAX_ENTRIES) }
        }
    }

    fun add(entry: LogEntry) {
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        persist()
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                val json = JSONArray()
                _entries.value.forEach { json.put(it.toJson()) }
                runCatching { file.writeText(json.toString()) }
                    .onFailure { Log.w(TAG, "write log failed", it) }
            }
        }
    }

    private fun read(): List<LogEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val json = JSONArray(file.readText())
        List(json.length()) { json.getJSONObject(it).toEntry() }
    }.getOrElse {
        Log.w(TAG, "read log failed", it)
        emptyList()
    }

    private fun LogEntry.toJson() = JSONObject()
        .put("time", time)
        .put("event", event)
        .put("item", item)
        .put("from", from ?: JSONObject.NULL)
        .put("to", to ?: JSONObject.NULL)
        .put("ok", ok)
        .put("note", note ?: JSONObject.NULL)

    private fun JSONObject.toEntry() = LogEntry(
        time = getLong("time"),
        event = getString("event"),
        item = getString("item"),
        from = stringOrNull("from"),
        to = stringOrNull("to"),
        ok = getBoolean("ok"),
        note = stringOrNull("note"),
    )

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else getString(key)

    private companion object {
        const val TAG = "LogRepository"
        const val MAX_ENTRIES = 300
    }
}
